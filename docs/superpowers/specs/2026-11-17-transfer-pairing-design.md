# Transfer pairing (roadmap step 5, P2d): design

Date: 2026-11-17. Status: draft, waiting for approval.
Branch: `feature/transfer-pairing`. Source: spec section 2.2 Module 7, `docs/roadmap.md` section 5 step 5, debts T1, T5, N-18, open decision D12. Uses foundation N1 (one ledger write path) and starts N3 (matching).

## Why

The user moves money between their own accounts: VCB to MB, VCB to a MoMo top-up. The phone gets two notifications, one debit and one credit. Today the app books one EXPENSE and one INCOME, so monthly spending and income are both inflated by every internal move. Module 7 turns such a pair into one `TRANSFER`.

Notification delay is not predictable. Phones like Xiaomi hold notifications back for minutes. A fixed wait before booking would either miss late partners or delay every captured row. So rows are booked as today and paired when the partner shows up, even if it is late.

## Goals

1. A debit on account A and a credit of the same amount on account B, both from notifications, within 30 minutes of each other, become one `TRANSFER` A → B. Both logs get `PAIRED_TRANSFER`.
2. The same rule runs whether the partner arrives in the same worker run or 25 minutes later. One code path.
3. A newly booked row never shows in the list and then vanishes: pairing happens in the same DB transaction that books the second row.
4. The receiver's bank balance is kept (`toPostBalance`, debt T1) and feeds the balance anchor, cold start and drift report, when it is safe (see Balance rules).
5. A wrong pair can be undone from the edit sheet: "Not a transfer" splits it back into the two original rows, and those two rows never pair again.
6. N-18: a notification whose post time is outside the `TransactionRules` time range fails at planning with its own reason, not after an hour of retries.

## Non-goals

- Reading the bank's own time from the notification text (`(?<time>)`, approach C below). Comes after dogfood, when real samples exist. This branch only keeps the "which time do we compare" choice in one function.
- Pairing amounts that differ by a fee. Only exact amounts pair; a fee is entered by hand.
- Suggesting a pair for rows the user already edited. No suggestion UI.
- A snackbar or system notification on pairing (no app notifications until N7, step 18).
- Pairing a notification row with a manual row (cash). Cash has no notifications; manual transfers already exist.
- Trash screen behaviour for rows hidden by pairing (step 26; see Debts).
- Real DB migrations (step 6). The DB is still destructive; bump the version.

## Anti-goals

- A row that appears in the list and disappears a moment later.
- Any pairing or unpairing write outside one DB transaction with the balance rebuild of both accounts.
- Losing the original rows: unpairing must restore exactly what was booked.
- A `toPostBalance` that anchors the receiver at a moment its bank never printed it for.
- Changing the existing bank + wallet same-direction dedup (stays 30 s; debt T5 closes with no change).

## Approaches

1. **Pair inside `SettlementPlanner`.** Pair two pending logs before booking; for a late partner, feed recent booked rows into the planner and emit "merge into row X". Never has two temporary rows, but two code paths for one rule, in an already complex planner.
2. **Pair when booking the second row (picked).** The planner stays as it is (plus N-18). `book` inserts the row, then in the same transaction searches ±30 minutes around it for a partner and, if found, replaces the two with one `TRANSFER`. Early and late partners take the same path. Unpairing is a reversal.
3. **Read the bank's time from the text.** Not a replacement: needs a `time` group in every rule, per-bank date formats, a log column, and real samples. Planned as an add-on to 2, through one "pairing time" function.

The user picked 2 after the "same transaction" refinement, plus a "Tự ghép" label and `animateItem()` on the list.

## Decisions

### Who pairs with whom

A candidate partner for a newly booked notification row `N` is a row `C` where:
- both have `source = NOTIFICATION_PARSER`, are live (`deletedAt == null`), and are not yet paired;
- both are untouched by the user: `updatedAt == createdAt` (book sets both to `now`; any edit or an unpair changes `updatedAt`);
- one is `EXPENSE`, the other `INCOME`, with the same `amount`;
- different `accountId`s (two accounts in the same bank app pair fine);
- neither account is a `CREDIT_CARD` (card payments are step 23's call; a card never anchors anyway);
- `|pairingTime(N) - pairingTime(C)| <= 30 min`, where `pairingTime(row) = row.timestamp` today. This function is the single place approach C will change. The window is two-sided: a debit held back 15 minutes by the phone is booked after its credit, so the partner can be later than `N`. The DAO query reads `[t - 30 min, t + 30 min]`;
- both accounts are usable (`isUsable`) at write time.

Several candidates: the nearest `pairingTime` wins, ties by `createdAt` ascending, then `id` ascending. Pure domain function (e.g. `findTransferPartner` in `domain/notification/service` or a new `domain/transaction/service`), the repository only queries rows in the window and calls it.

The bank + wallet same-direction dedup is untouched: it pairs logs of the same type before booking, transfer pairing needs opposite types after booking, so they never compete for a row.

### What a pair writes (three rows, lossless)

In the `book` write transaction, after inserting `N` and finding `C`:
- Insert a new `TRANSFER` row `T`: `accountId` = the EXPENSE side's account, `toAccountId` = the INCOME side's account, `amount`, `feeAmount = 0`, `timestamp` = the EXPENSE side's timestamp, `postBalance` = the EXPENSE side's, `toPostBalance` and `toTimestamp` = the INCOME side's balance and timestamp (see Balance rules), `note` and `merchantName` from the EXPENSE side, each falling back to the INCOME side when blank (a credit notice often names the sender better), no category, `source = NOTIFICATION_PARSER`, `notificationLogId = null` (the logs belong to the originals; this keeps one live-or-dead row per log id lookup simple; the index on `notificationLogId` is not unique, so this is about clarity, not a crash).
- Soft-delete `N` and `C` and set a new column `pairedIntoId = T.id` on both. Their other fields stay as booked, so unpairing restores them exactly.
- Logs keep their `linkedTransactionId` to their own original row (`N` or `C`); `T` is reached through `pairedIntoId`. The two bank-side logs (`notificationLogId` of `N` and `C`) change status to `PAIRED_TRANSFER`. A wallet log already `DEDUPLICATED` into `N` or `C` is not touched. Builder's call (keeps unpair lossless without remembering which log belonged where).
- Run `TransactionRules` on `T` before writing. If it refuses `T`, there is no pair: `N` stays booked as a single row, logs `CONFIRMED`, and `book` succeeds. Builder's call.
- Rebuild both accounts; run cold start for each account independently from its own anchor (`LedgerWriter.coldStart` today only covers `accountId`).

What the user sees: `C` was in the list; in one emission it is replaced by `T`, at the same place when `C` was the sender. `animateItem()` on the list turns this into a fade instead of a jump. `N` never shows on its own. When both logs settle in the same worker run, each `book` is its own transaction, so the first row is visible for a moment before the second one's commit replaces it: same look as a late partner, accepted. (In chat this was described as "the row changes its content"; with three rows it is a replace in place. Called out in the handoff list.)

### Balance rules

- `T.timestamp` is the sender's time, so `postBalance` stays exact for the sender.
- `T` always stores `toPostBalance` and `toTimestamp` (the receiver's own time). Whether the receiver may use it is decided **at read time**, not at pairing time: a notification held back by the phone can land between the two times minutes after the pair was written (example: pair at 10:00/10:05, then a 10:02 MB expense arrives at 10:12). A pairing-time check would miss it and leave a permanent drift.
- A received transfer with `toPostBalance != null` is an anchor candidate for the receiver. Today's rule "a transfer received never anchors" changes to this.
- **Order check, for every anchor candidate `A` of account `X`** (not only received transfers): the effective time of a row `R` on `X` is `R.toTimestamp` when `R` is a transfer into `X` with `toTimestamp != null`, else `R.timestamp`. `A` is valid only when, for every other live row `R` touching `X`, "R comes before A in `LEDGER_ORDER`" equals "eff(R) < eff(A)"; and when eff(R) == eff(A) while either of them uses a `toTimestamp`, `A` is invalid. In words: the bank printed the balance at the real time; the anchor is usable only if the ledger order agrees with the real order up to that point. It covers a late row between the two times, the credit arriving before the debit, and another paired transfer whose receiver time falls on the other side (Gemini review, round 2). For rows that never use a `toTimestamp`, both orders are the timestamp order, so today's anchors do not change.
- An invalid candidate is skipped and the next older one is tried. `detectBalanceDrift` and `anchoredInitialBalance` follow through it.
- On the receiver side the transfer counts `+amount` (sender: `-(amount + fee)`), as `BalanceCalculator` already does for manual transfers. Tests cover anchor and drift on a receiver that has rows before `T`.
- A manual transfer has `toPostBalance = null`, `toTimestamp = null`. Editing `T` follows the existing edit rule: changing account or date clears `postBalance`, `toPostBalance`, `toTimestamp`. `toPostBalance` and `toTimestamp` are also cleared whenever the destination changes, which includes switching the type away from `TRANSFER` (the destination becomes null), so `Transaction.init` never sees them on another type.

### Unpair ("Không phải chuyển khoản")

- Shown in the edit sheet for any row that has original rows pointing at it (`pairedIntoId = T.id`), whatever its type now. The edit sheet lets the user switch `T` to EXPENSE; the button must stay reachable then, or the originals and logs are stuck. The list row of such a row carries a small "Tự ghép" label.
- The list reads the "has originals" flag in its own query (`EXISTS` on `pairedIntoId`, indexed), not one DAO call per row.
- Confirm dialog (own file under the feature's `component/`, per house rule). It says edits made to the transfer are lost.
- One write transaction: soft-delete `T`; restore `N` and `C` (`deletedAt = null`, `pairedIntoId = null`, `updatedAt = maxOf(now, createdAt + 1)`, so "touched" holds even when the clock has not moved); the two `PAIRED_TRANSFER` logs back to `CONFIRMED` (their links never changed); rebuild every account involved: `T.accountId`, `T.toAccountId`, `N.accountId`, `C.accountId` (the user may have moved `T` to another account before unpairing). `UpdateTransactionUseCase` uses the same `maxOf` stamp.
- Only a live `T` can be unpaired. A plain Delete on `T` soft-deletes `T` only: the originals stay soft-deleted with `pairedIntoId`, logs stay `PAIRED_TRANSFER` (the user wanted the money move gone).
- Refused (typed error, snackbar) when either original's account is not usable now (archived, ignored, deleted). Account merge already rewrites `accountId` on soft-deleted rows too, so after a merge the originals follow the target and unpair still works. `updatedAt = now` makes both rows "touched", so they never pair again; no extra flag.
- A new use case (e.g. `UnpairTransferUseCase`) behind the existing edit ViewModel.

### N-18

`planSettlement` checks the post time through the same pure function `TransactionRules` uses for its time bounds (one source, no second copy of the limits) and emits `Fail` with a new reason (e.g. `FAIL_INVALID_TIME`); the existing `Fail` path already writes `FAILED`. The check in `book` stays as the last guard.

### Schema

`TransactionEntity`: add `toPostBalance: Long?`, `toTimestamp: Long?` and `pairedIntoId: String?` (index on `pairedIntoId`). Domain `Transaction` gains all three; `init` allows `toPostBalance` and `toTimestamp` only on a `TRANSFER`, and `pairedIntoId` only on a soft-deleted row. DB version 7 → 8, still destructive.

### Roadmap and spec text

In this branch: roadmap step 5 text (drop "log stays PENDING until the window ends, never two rows then fix"), close D12 (30 min look-back, exact amount, fee by hand), close T1, T5, N-18; add the trash debt below. `docs/architecture.md` only if a new service location needs it.

## Debts this branch adds

- Trash screen and purge (step 26): hide rows with `pairedIntoId != null` and transfers dissolved by unpair; never purge an original while its `T` is live; purge originals together with their `T`.
- Account merge bumps `updatedAt` on every moved row, so untouched rows of a merged account stop being pair candidates. Accepted; revisit at step 10.
- Cold start runs once. If a late notification lands before an anchor that already set an uninitialized account's start, the start stays as set and the drift banner shows the gap (user adjusts). Same as today's out-of-order case (N-03); pairing does not make it new, since the receiver's own original row already ran cold start at its own time.
- The version bump wipes dev-device data including `notification_logs`. Accepted: no real data before step 6.
- Approach C (bank time) after dogfood: rule `time` groups, per-bank formats, a log column, change `pairingTime`.

## Tests (minimum)

- Pairs: VCB debit + MB credit, same amount, 5 min apart → one `TRANSFER`, two soft-deleted originals, both logs `PAIRED_TRANSFER`.
- Same worker run (both logs pending together) → same result; the second booked row is never written live on its own.
- Credit card account on either side → no pair. `TransactionRules` refusing `T` → `N` booked alone, no error.
- Unpair with an archived account → typed error, nothing changes.
- Late partner at 29 min pairs; at 31 min does not; a partner later than `N` (debit held back, booked after its credit) pairs.
- Receiver anchor: used when nothing lies in the closed range; ignored when a row lies inside, including one booked after the pair was written and one at the same millisecond.
- Unpair after the user moved `T` to a third account: all four accounts end correct. Unpair after switching `T` to EXPENSE still works.
- Different amount, same type, same account, an edited row, a manual row → no pair.
- Two candidates → the nearest wins.
- MoMo top-up from VCB (bank debit + wallet credit) → `TRANSFER`. MoMo paid from VCB (both debits) → still dedup, no transfer.
- Balance: both accounts correct after pair and after unpair; receiver anchor used when nothing lies between, ignored when a row lies between; cold start of an uninitialized receiver works from `toPostBalance`.
- Unpair restores both rows exactly (all fields but `updatedAt`), logs back to `CONFIRMED`, and a new matching notification does not pair them again.
- Rollback: a failure during pairing leaves neither `N` nor the pair written (existing rollback test style).
- N-18: out-of-range post time → `Fail(FAIL_INVALID_TIME)` at planning.

## Left to the builder

- Exact names, file locations within the architecture rules, DAO query shape for the window.
- Whether the partner search lives next to `SettlementPlanner` or under `domain/transaction/service`.
- Label and dialog styling within `docs/design-system.md`; strings in all four languages.
