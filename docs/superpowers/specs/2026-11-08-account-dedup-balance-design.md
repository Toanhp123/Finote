# Account mapping, dedup and cold-start balance design (P2c, `feature/account-dedup-balance`)

Spec modules 5, 6, 8 (L44–51), tables 3.1 (L69–70), 3.3 (L110–118), 3.7 (L166–179), acceptance 6 (L410).
Roadmap P2c and P2a debt #3.

## Scope

In this branch:

- Turn `PENDING` notification logs into transactions on the right account, with no human step.
- Create an account on the fly when no account matches (bank name plus last four digits).
- Merge the wallet and bank notifications of one payment into one transaction (30 s window).
- Set the initial balance of an auto-created account from the first `postBalance`.
- Show a banner when the bank's balance and the app's balance differ, with an "Adjust" action.
- P2a debt #3: a re-posted notification does not create a second transaction.

Out of scope:

- Merge accounts and ignore accounts: `feature/account-merge-ignore`, right after this branch.
- Transfer pairing between two own accounts: P2d (`feature/transfer-pairing`). It reuses the settle window built here.
- Auto category: P3 (`feature/merchant-normalize`). Notification transactions have no category for now.
- Failed Inbox UI: P2b-3 (`feature/failed-inbox`). The new `FAILED` reasons below show there later.

## Decisions (brainstorm 2026-11-08)

| # | Question | Decision |
|---|---|---|
| 1 | No account matches | Create one. Users fix wrong guesses later (merge, ignore), as PennyWise does. |
| 2 | Dedup key | Different package, one side a wallet, same amount, same direction, ≤ 30 s. Merchant is not compared: wallet and bank never word it the same. |
| 3 | Cold start | Set `initialBalance` automatically, no prompt. Only auto-created accounts start uninitialized. |
| 4 | Balance drift | Banner plus "Adjust" (existing `ReconcileBalanceUseCase`). Never adjust silently. |
| 5 | When to book | Wait for the window, then decide once ("settle"). No booking-then-fixing. |
| 6 | Repost window | 10 min when the log has `parsedPostBalance`, 3 s when it has none (split-bill risk, see risks). |

Why settle and not book at once: when the wallet notification comes first, booking at once puts the payment on the
wallet account, and the bank notification would then have to move it and rebuild two balances. P2d needs the same
waiting state ("do not create two lopsided transactions"). Cost: a transaction shows 30–60 s late.

## Data (DB version 5 → 6)

Before v1.0 the DB is recreated (`fallbackToDestructiveMigration(dropAllTables = true)` is already set).

### `accounts`

- `bankPackageName: String?`, `accountNumberMask: String?`. One non-unique index on
  `(bankPackageName, accountNumberMask)`. Not unique: SQLite treats NULLs as distinct, and a soft-deleted account may
  share a key with a live one.
- Null for manual accounts. An auto-created account has both (mask may be null, see mapping).

### `transactions`

- `postBalance: Long?`: the balance the bank reported right after this transaction.
- `notificationLogId: String?`: foreign key to `notification_logs.id`, `onDelete = SET_NULL`, indexed.

A soft-deleted notification transaction is never booked again: its log is `CONFIRMED` and settle books only `PENDING`.

### `notification_logs`

The existing `linkedTransactionId` is filled. It stays a plain column with no foreign key, so the two tables have no
circular constraint. Status after settle:

| Status | Meaning |
|---|---|
| `CONFIRMED` | A transaction was created from this log. |
| `DEDUPLICATED` | Same event as another log; `linkedTransactionId` is the kept transaction (null when that log was not booked). |
| `FAILED` | New reasons: `ambiguous_account`, `account_archived`, `invalid_amount`. |

### `parser_rules`

- `accountType: String?` (`AccountType.name`). Null means `BANK`.
- System rules: every MoMo rule is `WALLET`; `mb_card` and `tcb_card` are `CREDIT_CARD`.
- Feed payload gets the same optional field. Old apps ignore it (`RuleFeedCodec` uses `ignoreUnknownKeys = true`).
  Re-sign the feed after the change.
- Used for the type of an auto-created account and for the wallet side of dedup.

## Flow

### Trigger

- After `CaptureNotificationUseCase` stores a `PENDING` row, it asks `SettleScheduler` to enqueue the unique one-time
  work `settle-notifications`, initial delay 35 s, `ExistingWorkPolicy.REPLACE`.
  - Not `KEEP`: a notification stored while the worker is `RUNNING`, after the worker read the DB, would be dropped
    and stay `PENDING` until the next notification or app start.
  - `REPLACE` on a running worker cancels it. That is safe: each decision is its own write transaction, a cancelled one
    rolls back, and settle is idempotent. A burst of notifications just pushes the run back (debounce).
- When a run leaves `PENDING` logs that are not due yet, the worker returns `Result.retry()`; the work request uses
  `BackoffPolicy.LINEAR` with a 30 s backoff.
- App start runs settle once (covers a killed process). It books only due logs, so a notification from 3 s ago still
  waits; app start then also calls `SettleScheduler` so the rest is not left to chance.

### Settle (`SettlePendingLogsUseCase`)

Runs under one mutex. Reads:

- every `PENDING` log;
- every non-`PENDING`, non-`IGNORED_SPAM` log with `postTime ≥ min(pending postTime) − 10 min` (repost history);
- every non-deleted account with a `bankPackageName`;
- the `accountType` of each rule id.

Calls the pure planner, then hands the decisions to the repository in order.

A log is **due** when `postTime ≤ now − 30 s`. At that time every log that can pair with it (≤ 30 s apart) has
arrived, so the planner sees all candidates, due or not. A not-due log is only used as a partner, never booked alone.

### Planner (`planSettlement`, `domain/notification`, no `android.*`)

Input: pending logs, recent settled logs, mappable accounts, rule types, `now`. Output, in write order:

- `Repost(log, original)`: `original` is a settled log or a log booked or failed earlier in this plan. When the
  original is still pending and not due, the repost waits for a later run, so its link is never lost.
- `Book(bankSide, walletSide?, account: Existing(id) | New(package, mask, bankName, type), merchant, note, postBalance)`.
  `bankSide` is always the non-wallet log when there is a pair: it gives `notificationLogId`, `timestamp`, the
  `CONFIRMED` status, account and `postBalance`. `walletSide` becomes `DEDUPLICATED`.
- `Fail(log, partner?, reason)`: the partner fails with the same reason, so a wallet log is never left alone to be
  booked on the wallet account in the next run.

Each log is used in at most one decision. Steps:

1. **Repost pre-pass, over all pending logs (due or not), oldest first.** A log is a repost of an earlier log (settled
   or pending) with the same package, title and content when `postTime` is ≤ 10 min apart if the log has
   `parsedPostBalance`, or ≤ 3 s apart if it has none. It is removed from the pool before pairing, so a not-due repost
   cannot steal a partner.
2. **Pair, nearest first.** Build every candidate pair: other package, exactly one side `WALLET`, same `parsedAmount`,
   same `parsedType`, `|Δ postTime| ≤ 30 s`, at least one side due. Sort by `|Δ postTime|`, then by the older
   `postTime`; take pairs greedily, skipping a log already taken. Nearest-first stops an earlier bank charge of the
   same amount from stealing the wallet log of a later one.
3. **Singles.** Every remaining due log is booked alone.
4. **Map the account** of the bank side (or the single):
   - Mask present: the account with the same `(bankPackageName, accountNumberMask)`. None → `New`.
   - Mask null: the package's only account → it. No account → `New` with null mask. Several →
     `Fail(ambiguous_account)`.
   - Matched account archived → `Fail(account_archived)` (fixed by merge/ignore later, not by a new account).

Note on step 2: a pair is booked as soon as one side is due; the other side has arrived by definition. A third log that
would be even nearer to the not-due side can still arrive later; that case is accepted (see risks).

### Write (`NotificationSettlementRepository`, data layer)

Decisions run in plan order. The repository keeps a map `logId → transactionId` for this run, so a `Repost` of a log
booked in the same run links to its new transaction.

One `withWriteTransaction` per `Book`:

1. Resolve the account. For `New`, look up `(bankPackageName, accountNumberMask)` again inside the transaction
   (`IS` for a null mask) and reuse a match; only insert when there is none. Two `New` decisions for one key in one
   run therefore create one account. Insert: name `"<bankName> ••<mask>"` (or `bankName` alone), type from the rule,
   `initialBalance = 0`, `isInitialized = false`, VND, default icon and color for the type.
2. Insert the transaction: `source = NOTIFICATION_PARSER`, `type` from `parsedType`, `timestamp = bankSide.postTime`,
   `categoryId = null`, merchant, note, `postBalance`, `notificationLogId = bankSide.id`.
3. Cold start (below), then `AccountBalanceRebuilder.rebuild(accountId)`.
4. Move `bankSide` to `CONFIRMED` and `walletSide` to `DEDUPLICATED`, both linked to the transaction, in one
   `UPDATE … WHERE id IN (…) AND status = 'PENDING'`. When the row count is not the number of logs, throw: the whole
   transaction rolls back (another run took one of them).

`Fail` and `Repost` are conditional updates on the same `status = 'PENDING'` guard (`Fail` covers both logs).

### Cold start

When the account has `isInitialized = false`, is not `CREDIT_CARD`, and the booked transaction has `postBalance`:

```
initialBalance = postBalance − effect(transactions of the account with timestamp ≤ this one, from 0)
isInitialized  = true
```

`effect` is `BalanceCalculator.balance(accountId, 0, …)`. A transaction without `postBalance` leaves the account
uninitialized; a later one sets it.

A user edit wins: `UpdateAccountUseCase` already sets `isInitialized = true`, so a balance the user typed is never
overwritten by a later notification.

Credit cards are skipped on purpose: their alerts show the remaining limit, not a balance. Today the `mb_card` and
`tcb_card` rules have no `balance` group; this guard keeps a future OTA rule that reads the limit from breaking it.

## Balance drift banner

For each non-`CREDIT_CARD` account, take its newest live transaction with `postBalance` (`P`, by `timestamp`, then
`createdAt`):

- `expected` = the account's running balance right after `P` (`BalanceCalculator` over the transactions up to `P`).
- The account **drifts** when `P.postBalance ≠ expected` and there is no `ADJUSTMENT` after `P`.
- Difference `d = P.postBalance − expected`.

Compared with "the newest transaction must be `P`": a later notification without a balance no longer hides a real
drift, and a later manual transaction is not mistaken for one, because the check is made at `P`, not against
`currentBalance`.

- `ObserveBalanceDriftUseCase` emits the drifting accounts with `d`.
- Transaction list shows one banner per drifting account: "<account> differs from the bank by <d>", button "Adjust" →
  `ReconcileBalanceUseCase(accountId, actualBalance = currentBalance + d)`. The `ADJUSTMENT` it creates is after `P`,
  so the banner goes away.
- Strings in all four locales (`values`, `values-en`, `values-zh-rCN`, `values-ja`).

## Error handling

- A decision that throws rolls back its own transaction; its logs stay `PENDING`; the next decisions still run. The
  next run retries.
- `Transaction` rejects `amount ≤ 0`. The parser should never give that, but if it does the log becomes
  `FAILED(invalid_amount)` so it does not retry forever.
- No notification text, amount, balance or account number in Logcat. Log ids and status codes only.

## Testing (JVM, Robolectric for Room)

- `planSettlement`:
  - wallet + bank pair, wallet first and bank first; `bankSide` is always the bank log
  - two equal charges from the same package stay two transactions
  - 31 s apart → no pair; bank + bank → no pair; opposite directions → no pair
  - nearest pair wins: bank 0 s, bank 20 s, wallet 21 s → the 20 s bank pairs with the wallet
  - a not-due log is used as a partner but never booked alone
  - repost pre-pass: with balance within 10 min → repost, after → booked; without balance within 3 s → repost,
    4 s → booked; original settled in an earlier run; original booked in the same run; not-due repost cannot pair
  - pair whose bank side cannot be mapped → `Fail` covers both logs
- Account mapping: mask present (found / new), mask null (0 / 1 / many accounts), archived account. Target 100%
  (acceptance 6).
- Cold start: correct `initialBalance`; no `postBalance` waits; earlier manual transactions counted; credit card
  skipped; a user-edited balance is not overwritten.
- Repository: transaction and account created, balance and snapshots right; two `New` for one key in a run → one
  account; repost in the same run links to the new transaction; pair update that hits 1 of 2 rows rolls back;
  rollback mid-way leaves logs `PENDING`; running settle twice books once.
- Drift: shows on mismatch; stays after a later no-balance notification; gone after adjust; a later manual transaction
  does not create a false drift; credit card never drifts.
- Worker and scheduler: runs the use case; `retry()` when logs are not due; capture enqueues with `REPLACE`.
- Konsist and `qualityGate` pass.

## Known risks

- **Repost vs split bills.** Two real no-balance notifications with the same text (MoMo "Bạn đã nhận được 50.000đ từ A",
  friends paying back a split bill one by one) would be merged by a long repost window and real money would be lost
  from the books, silently. Hence the 3 s window for logs without a balance: a system re-post lands within a fraction
  of a second, two real payments from one person almost never within 3 s. A no-balance notification that an app
  re-posts later than 3 s is booked twice; a duplicate is visible and can be deleted, a lost payment is not.
- **Wallet + bank false merge.** Paying the same amount by bank at shop A and by wallet at shop B within 30 s merges
  them into one. Rare; accepted.
- **Late better partner.** A pair is fixed once one side is due; a log arriving later that is nearer to the not-due
  side is not considered. Needs three same-amount notifications within a minute. Accepted.
- **Card mask is not account mask.** A VCB debit card notification (`…5678`) creates its own account next to the
  account `…1234`. Fixed by merge in `feature/account-merge-ignore`; until then the user can archive it.
- **Late display.** A transaction appears 30–60 s after the notification; longer if WorkManager is deferred by Doze or
  a burst keeps replacing the work. Opening the app settles every due log at once.
