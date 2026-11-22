# Double booking (roadmap step 11): design

Date: 2026-11-23. Status: approved 2026-11-23, implemented on `fix/double-booking`.
Branch: `fix/double-booking`. Source: `docs/roadmap.md` section 5 step 11, money bugs L3 and L4, foundation N3, design debt T4. Uses N1 (one ledger write path) and N4 (real migrations).

## Why

Two paths can book one real payment twice. Neither has shown up in dogfood yet (the user has never seen a double booking), so this branch is a guard before v0.2, not a reaction to a seen bug.

- **L3, repost.** A log is a repost only when package, title and content are identical (`SettlementPlanner.sameText`), within 10 min when it has a balance, 3 s when not. A bank that updates its notification (adds a line, shows a balance that was cut by "…", re-posts without a balance after 3 s) produces a log with new text. It passes `sameText` and is booked again. A real Techcombank sample had its balance cut by an ellipsis, so the shape is plausible.
- **L4, late log after an adjustment.** The user presses Adjust on a drift banner (or reconciles by hand); an `ADJUSTMENT` row covers the missing money. If the missing log later gets booked (reparse of a `FAILED` log after a rule update, or a ROM that holds notifications for hours), the money is counted twice. `detectBalanceDrift` stays silent, because an adjustment sits after the anchor.

## Goals

1. An updated or re-posted notification books once, whatever changed in its text, when the platform says it is the same notification.
2. A log whose account already has an adjustment later than the log is not booked. It ends `FAILED` with a reason the user can see in the log screen (and in failed-inbox, step 13).
3. Existing logs survive: migration 9 → 10.
4. The check in goal 2 is the "candidate vs ledger" function of N3 that steps 13, 24 and 25 reuse: one function that says "the ledger of this account was settled by hand after time T".

## Non-goals

- Copying a later post balance onto the original transaction when the update carries a balance the original lacked. The original keeps its own fields.
- Booking the late log and shrinking the adjustment instead (option b, rejected below).
- Any UI. The log screen already shows `failureReason`.
- Reading the bank's own time from the text (N-20).

## Anti-goals

- Two different real payments merged into one when they carry different balances. Different non-null balances always mean two payments.
- Losing a booking silently: every log that is not booked ends in a visible status (`DEDUPLICATED` with a link, or `FAILED` with a reason).
- Notification text, balance, mask or the notification key in Logcat or `toString`.
- A balance change outside the one write transaction (CLAUDE.md money rules).

## Approaches

### L3

- **1. Platform key (picked).** Store `StatusBarNotification.key` on the log. Same key plus same money fields within 10 min is a repost. Catches every update shape, with or without a balance. Cost: one nullable column and a migration. Risk: an app that reuses one key for every notification, with two real payments of equal amount, type and mask, at most one of them with a balance, within 10 min, merges them. Accepted (see Risks).
- **2. Parsed fields only, no migration.** Same amount, type, mask, balances equal or one missing. Smaller, but a no-balance repost after 3 s still books twice: the split-bill risk forbids a long window without a key.
- **3. Do nothing, add a test.** Cheapest; v0.2 ships with the hole.

### L4

- **a. Do not book; fail with a reason (picked).** Safe: the balance does not move. The user decides later (failed-inbox).
- **b. Book, then shrink the adjustment by the amount.** The transaction appears with a category, but one adjustment can cover several payments and the sign is easy to get wrong. Higher money risk.

The user picked 1 + a on 2026-11-23.

## Design

### Capture: keep the key

- `RawNotification` and `NotificationLog` get `notificationKey: String?`. The listener fills it from `sbn.key`, blank stored as null. Null for logs captured before this branch.
- `NotificationLogEntity` gets a nullable `notificationKey` column. Migration 9 → 10 adds it; old rows stay null. `DATABASE_VERSION = 10`, new schema JSON, migration test as `MigrationPolicyTest` does for 8 → 9.
- `contentHash` does not change. The key is not part of it: a rescan in `onListenerConnected` must still hit the unique hash.
- `toString` of the three types keeps leaving the key out (it can contain the posting app's tag).
- `ReparseFailedLogsUseCase` builds its `RawNotification` from the log; it passes the stored key through so nothing is lost.

### Planner: key repost

In `findReposts` (`SettlementPlanner.kt`) the text rule stays as it is: an earlier log with `sameText`, within 10 min with a balance or 3 s without; the newest match wins; a repost of a repost points at the first original.

The key rule is new and works on **clusters**. A cluster is one root (a log that is not a repost) plus the logs already matched to it. A log joins a cluster when all hold:

- both keys non-null and equal (the listener stores a blank `sbn.key` as null), same `packageName`;
- both the log and the root parsed (`parsed != null`);
- same `amount`, `type`, `accountMask` as the root (exact; null equals only null);
- its balance does not conflict with **any** non-null balance already in the cluster (root or member);
- `|log.postTime − root.postTime| <= REPOST_WINDOW_WITH_BALANCE_MILLIS` (10 min), measured from the root, never from a member. No window creep.

Which roots a log may join:

- a **settled** root on either side of it in time. A reparsed or late log whose update was already booked (`#1` at 10:00:00 reparsed after `#2` at 10:00:02 was booked) becomes a repost of `#2`. The `abs` above is the only place a later log can be an original.
- a **pending** root only earlier in plan order (`LOG_ORDER`), as today.

When several clusters fit, the one whose root is nearest in time wins, then the earlier root. The text rule runs first; the key rule only looks at logs the text rule left alone.

An unparsed log (a `FAILED` first version) is never a root, so the parsed update books once.

`SettlePendingLogsUseCase` loads settled logs from `min(pending postTime) − 10 min` with no upper bound, so later settled logs are already in the input. Its filter for the text rule (`LOG_ORDER.compare(it, log) < 0`) stays.

Reparse touches only `FAILED` logs with `no_rule_matched` (`NotificationLogDao.getFailed`), so an `adjusted_after` log is never reparsed.

### Write path: adjusted-after check

**When an adjustment covers a log.** An adjustment after a log does not always cover it. Example: an old drift banner from 09:00; a real payment posts at 10:00:00 and waits its 30 s; the user taps Adjust at 10:00:10. The adjustment fixed the gap up to the 09:00 balance, not the 10:00 payment. So a log at time `t` on account `x` is covered when both exist among the live rows of `x`:

- an `ADJUSTMENT` row `A` of `x` with `A.timestamp > t` (strict: equal time is not covered);
- a row `R` that carries a bank balance for `x` (its own `postBalance`, or `toPostBalance` of a paired transfer into `x`, at `toTimestamp`) with `t < time(R) <= A.timestamp`.

That is: the bank had already reported a balance that includes the log, and the user then adjusted. Drift Adjust always has such an `R` (it is the anchor of the drift).

- One pure domain function holds this rule, e.g. `isCoveredByAdjustment(accountId, time, rows): Boolean` in `domain/account/service`, next to `BalanceAnchor`. N3's "candidate vs ledger" for steps 13, 24, 25 calls the same function. No SQL copy of the rule.
- Inside `NotificationSettlementRepositoryImpl.book`, in the same write transaction, after the account is resolved: load the account's live rows (as `LedgerWriter.coldStart` already does), run the function; if covered, return a new `SettlementError.AdjustedAfter` before any insert. In the write transaction, not the planner, so an adjustment saved between plan and write is still seen. A new account has no rows and is never covered.
- Rollback: `bookInTransaction` turns any `SettlementError` into `Outcome.Err`, and `RoomTransactionRunner.write` rolls back on `Err`. So an identifier attached or an account created by `accountFor` before the check is rolled back. A test proves it.
- `SettlePendingLogsUseCase.execute`: on `AdjustedAfter`, after the rollback, write `Fail(bankSide, walletSide, FAIL_ADJUSTED_AFTER)` with `FAIL_ADJUSTED_AFTER = "adjusted_after"`. Not retried, not left `PENDING`. Every other `SettlementError` keeps today's retry and stuck handling.
- Both sides of a bank + wallet pair become `FAILED`, as every pair `Fail` does today. Step 13 must show a pair as one item (both logs share the reason and are partners), or the user could enter it twice by hand. Noted for step 13.
- A paired transfer (`findPair`) runs after the check, so a late log is never paired.
- Both `ADJUSTMENT` sources count: Adjust on a drift banner and manual reconcile. Both write `timestamp = now`.

### Failure reason display

The log screen (`NotificationLogsScreen`, `LogDetailSheet`) shows the raw `failureReason` code, like every other reason. No string change.

## Tests

- Planner (`SettlementPlannerTest`):
  - key repost with an extra line; first without balance, second with; repost after 3 s without a balance;
  - different balances, same key → two bookings; same key after 10 min → two bookings;
  - different keys, identical text → text rule still applies; null keys (old logs) → text rule only; blank keys never match;
  - first version unparsed → second books;
  - reparse: `#1` (10:00:00) pending, `#2` (10:00:02, same key) settled and booked → `#1` is a repost of `#2`;
  - settled log with the same key one day later → no match;
  - chain `#1` bal 1,000k → `#2` bal null → `#3` bal 950k → `#3` books;
  - creep `#1` t=0 → `#2` t=8 min → `#3` t=16 min, no balances → `#3` books.
- Domain (`isCoveredByAdjustment`): anchor between log and adjustment → covered; adjustment after log but no anchor between (the 30 s race) → not covered; `A.timestamp == t` → not covered; deleted adjustment or deleted anchor → not covered; adjustment of another account → not covered; anchor through a paired transfer into the account → covered.
- Repository (Room, `NotificationSettlementRepositoryImplTest`): covered log → `AdjustedAfter`, no transaction row, no new account, no new identifier (rollback through the runner); covered log with a pair partner in 30 min → no `TRANSFER`, partner untouched; uncovered → booked.
- Use case: `AdjustedAfter` → log `FAILED` with `adjusted_after`, wallet side too, not retried.
- Capture: key stored; blank key stored as null; rescan of the same notification stays one row; `ReparseFailedLogsUseCase` keeps the key; `toString` of `RawNotification`, `NotificationLog`, `NotificationLogEntity` has no key.
- Migration 9 → 10 keeps every log, key null.

## Risks

- **Key reuse.** An app that posts every alert under one fixed key, two real payments with equal amount, type and mask, at most one of them with a balance, within 10 min: merged, one payment lost. Techcombank alerts carry a balance, so different balances keep them apart. Mark it in code as `shortcut:` with the upgrade path (per-rule opt-out of the key rule).
- **Sibling with a null link.** A settled sibling with a null link adds no balance to the cluster (root failed, e.g. `ambiguous_account`); a different payment with the same key and amount within 10 min of that root can join it. Rare; accepted.
- **Mask cut in the first version.** The key rule needs the same mask; a first version with the mask cut and a second with it books twice. Accepted: in the Techcombank shape the `Account:` line comes before the balance, so the mask survives a cut. Loosening it (null matches any) widens the key-reuse risk.
- **Reconcile without any balance row.** Manual reconcile on an account that never had a bank balance, then a late log from before it: not covered, booked, counted twice. Rare: accounts fed by notifications carry balances.
- **Adjustment that was not for this log.** A late log, a bank balance after it, then an Adjust for an unrelated reason: the log fails instead of booking. Visible in the log screen; failed-inbox turns it into a manual transaction.
- **Not tested on a real device.** The update shape is inferred from one sample. Dogfood keeps watching the log screen.

## Left to the builder

- `AutoMigration` or a hand-written `MIGRATION_9_10`, following `MigrationPolicy.kt`. Hand-written: `ADD COLUMN notificationKey TEXT` with no `DEFAULT`, so it matches the entity's schema.
- The exact name and home of the adjusted-after function, within `docs/architecture.md` rules.

## Calls made by Claude (check while reading)

- Key window is 10 min for both cases, with or without a balance, measured from the cluster root.
- Covered = adjustment after the log **and** a bank balance between them (not just any later adjustment).
- Mask must match exactly in the key rule.
- The adjusted-after check runs in the write transaction, not the planner.
- `adjusted_after` is final: not retried, not `PENDING`.
- A repost whose original failed `adjusted_after` is `DEDUPLICATED` with no link (today's behavior for a failed original).
- No UI string; the log screen shows raw reason codes.
