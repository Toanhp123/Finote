# Transfer Pairing Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A debit and a credit of the same amount on two of the user's accounts, both from notifications, within 30 minutes, become one `TRANSFER`. A wrong pair can be split back from the edit sheet.

**Architecture:** Pairing runs inside `NotificationSettlementRepositoryImpl.book`, in the same DB transaction that books the second row. Pure domain functions pick the partner and build the transfer; `LedgerWriter` gains one method that writes the three rows (two soft-deleted originals with `pairedIntoId`, one live `TRANSFER`). The receiver's balance anchor is checked at read time in `findBalanceAnchor`. Unpair is a use case that restores the originals.

**Tech Stack:** Kotlin, Room 3, Hilt, Compose (Material 3), kotlinx-coroutines-test, Turbine, Robolectric, Konsist, detekt.

**Spec:** `docs/superpowers/specs/2026-11-17-transfer-pairing-design.md`. Read it before any task.

Paths below: `main/` = `app/src/main/java/io/github/toanhp123/finote/`, `test/` = `app/src/test/java/io/github/toanhp123/finote/`.

## Global Constraints

- Branch `feature/transfer-pairing` from `dev`. Follow `docs/git-workflow.md`. Commit scope `pairing`, `balance`, `db` or `ui`. No AI attribution. `coder` never commits; the main session commits each task after its review.
- Money is `Long` VND. Never `Double`. Soft delete only. Every balance change runs inside one write transaction.
- Never log notification content, balances, account numbers, notes.
- One ledger write path: every row insert goes through `LedgerWriter` (N1).
- Layers and limits: `docs/architecture.md`. UI: `docs/design-system.md`; dialogs in their own file under the feature `component/`.
- Strings in `values` (`vi`), `values-en`, `values-zh-rCN`, `values-ja`.
- Constants: `TRANSFER_PAIR_WINDOW_MILLIS = 1_800_000L` (30 min, two-sided). Existing `PAIR_WINDOW_MILLIS = 30_000L` (bank + wallet dedup) does not change.
- DB version 7 → 8, still `fallbackToDestructiveMigration`.
- Skills for the implementer: `ponytail:ponytail`, `kotlin-concurrency-and-flow`, `compose-state-and-effects`, `testing-coroutines-with-runtest`, `testing-flows-with-turbine`, `using-robolectric-correctly`, `gradle-run`.
- Run Gradle only through `gradle-run`: `'.\gradlew.bat' testDebugUnitTest --tests '<pattern>'` from Bash. After Task 8, `qualityGate` passes.

## Review Focus

1. **A late row of the receiver lands between the two times after the pair was written** (Xiaomi holds a 10:02 MB expense until 10:12). The receiver's drift must not show a false difference. Test in Task 2 (anchor) and Task 4 (end to end).
2. **The debit is held back and booked after its credit.** The partner is later than the new row; it must still pair, with the debit side as source. Test in Task 3.
3. **The user edits `T` (date, account, or type to EXPENSE), then unpairs.** All four accounts end correct; the button is still there. Test in Task 6 and Task 7.
4. **The same matching pair notification is re-posted after unpair.** The restored rows never pair again, even with a clock that did not move. Test in Task 6.
5. **A merged-away account on one side of an existing pair.** Unpair restores into the merge target and balances stay right. Test in Task 6.

---

### Task 1: Schema, model, and the edit rule

**Files:**
- Modify: `main/domain/transaction/model/Transaction.kt`, `main/data/transaction/local/entity/TransactionEntity.kt`, `main/data/transaction/mapper/TransactionMapper.kt`, `main/data/database/FinoteDatabase.kt` (version 8), `main/domain/transaction/usecase/UpdateTransactionUseCase.kt`
- Test: `test/domain/transaction/model/TransactionTest.kt` (create if missing), `test/domain/transaction/usecase/UpdateTransactionUseCaseTest.kt`

**Interfaces:**
- Produces on `Transaction` and `TransactionEntity` (defaults `null`):
  ```kotlin
  val toPostBalance: Long? = null   // bank balance the receiver printed
  val toTimestamp: Long? = null     // the receiver's own notification time
  val pairedIntoId: String? = null  // on a soft-deleted original: the TRANSFER that replaced it
  ```
  `TransactionEntity` adds `Index(value = ["pairedIntoId"])`.
- `Transaction.init`: `toPostBalance` and `toTimestamp` must be null unless `type == TRANSFER`; `pairedIntoId` must be null unless `deletedAt != null`.

- [ ] **Step 0: Branch.** `git switch -c feature/transfer-pairing` from `dev`. Commit the spec and this plan: `docs(pairing): transfer pairing design and plan`.
- [ ] **Step 1: Failing tests.**
  - `TransactionTest`: `EXPENSE` with `toPostBalance = 1` throws `IllegalArgumentException`; `EXPENSE` with `toTimestamp = 1` throws; `TRANSFER` with both set builds; live row with `pairedIntoId = "t"` throws; soft-deleted row with it builds.
  - `UpdateTransactionUseCaseTest`:
    - `edit keeps receiver balance when date and both accounts stay` → `toPostBalance`, `toTimestamp` unchanged.
    - `edit clears receiver balance when destination changes` → both null; same for a date change; same for a type change to `EXPENSE` (no exception from `init`).
    - `edit with a clock that has not moved still marks the row touched` → with clock at `createdAt`, saved `updatedAt == createdAt + 1`.
- [ ] **Step 2: Run, expect FAIL.** `--tests '*TransactionTest' --tests '*UpdateTransactionUseCaseTest'`
- [ ] **Step 3: Implement.** Add the three fields end to end (model, entity, mapper both ways). In `withEdit`: keep `toPostBalance`/`toTimestamp` only when `isSameDate && fields.accountId == existing.accountId && fields.toAccountId == existing.toAccountId`; `updatedAt = maxOf(clock.millis(), existing.createdAt + 1)`. Bump DB version to 8.
- [ ] **Step 4: Run all unit tests, expect PASS.** `testDebugUnitTest`
- [ ] **Step 5: Commit** `feat(db): receiver balance and pair link columns`

### Task 2: Receiver anchor at read time

**Files:**
- Modify: `main/domain/account/service/BalanceAnchor.kt`
- Test: `test/domain/account/service/BalanceAnchorTest.kt`, `test/domain/account/service/BalanceDriftTest.kt`

**Interfaces:**
- Consumes: Task 1 fields.
- Produces: `findBalanceAnchor(account, transactions)` signature unchanged. Candidates for account `X`, newest first in `LEDGER_ORDER`:
  - `accountId == X && postBalance != null` (as today), with balance `postBalance`;
  - `type == TRANSFER && toAccountId == X && toPostBalance != null && toTimestamp != null`, with balance `toPostBalance`.
  The first candidate that passes the order check wins; none → null. Order check (private, same file):
  ```kotlin
  /** Time the bank applied [row] to account [x]: the receiver time of a paired transfer into x, else its timestamp. */
  private fun effectiveTime(row: Transaction, x: String): Long
  ```
  Candidate `A` passes when, for every other row `R` of `mine` (live rows touching `X`): if `effectiveTime(R) == effectiveTime(A)` and either uses a `toTimestamp` → fail; else `(LEDGER_ORDER.compare(R, A) < 0) == (effectiveTime(R) < effectiveTime(A))`.
  `// shortcut: O(n) per candidate, worst case O(n²) when many candidates fail; fine per account until step 30.`

- [ ] **Step 1: Failing tests** in `BalanceAnchorTest` (replace `received transfer never anchors`):
  - `received transfer anchors receiver when order agrees` → anchor `postBalance == toPostBalance`.
  - `received transfer without toPostBalance never anchors` (manual transfer).
  - `late receiver row between the two times disqualifies` (T 10:00/10:05, MB row 10:02) → older anchor used; with no older one, null.
  - `credit earlier than debit with a row between disqualifies` (toTimestamp 10:00, timestamp 10:05, MB row 10:02).
  - `row at exactly toTimestamp disqualifies`.
  - `another paired transfer whose receiver time crosses disqualifies`: T 10:00/10:05; T1 into MB with timestamp 10:10, toTimestamp 10:03 → T invalid. Also T1 timestamp 09:58, toTimestamp 10:03 → T valid (ledger and real order agree).
  - `normal anchor after a crossing transfer`: MB EXPENSE 10:04 with `postBalance`, T1 into MB timestamp 09:58, toTimestamp 10:06 → that EXPENSE is invalid.
  - `sender rows do not count`: a row of the sender account between the two times does not disqualify the receiver anchor.
  - Existing tests stay green (rows without `toTimestamp` behave as today).
  In `BalanceDriftTest`: `receiver with rows before transfer has no drift when bank agrees` → receiver initial 1_000_000, an EXPENSE 200_000 before, transfer in 500_000 with `toPostBalance = 1_300_000` → `detectBalanceDrift == null`; with `toPostBalance = 1_400_000` → drift `100_000`.
- [ ] **Step 2: Run, expect FAIL.** `--tests '*BalanceAnchorTest' --tests '*BalanceDriftTest'`
- [ ] **Step 3: Implement** in `findBalanceAnchor`. Update the KDoc that says a received transfer never anchors.
- [ ] **Step 4: Run all unit tests, expect PASS.**
- [ ] **Step 5: Commit** `feat(balance): receiver anchor from a paired transfer`

### Task 3: Partner finder and transfer builder (pure)

**Files:**
- Create: `main/domain/transaction/service/TransferPairing.kt`
- Test: `test/domain/transaction/service/TransferPairingTest.kt`

**Interfaces:**
- Consumes: Task 1 fields; `Account`, `AccountType`, `Transaction`.
- Produces:
  ```kotlin
  const val TRANSFER_PAIR_WINDOW_MILLIS = 1_800_000L

  /** The single time used to compare two rows. Approach C (bank time) changes only this. */
  fun pairingTime(row: Transaction): Long

  /** True when the row may still be auto-paired: notification source, live, never touched, not yet paired. */
  fun isPairable(row: Transaction): Boolean

  /**
   * Best partner of [row] among [candidates], or null. Rules: both pairable, one EXPENSE one INCOME, same amount,
   * different accounts, both accounts in [usableAccounts] and not CREDIT_CARD, |pairingTime| diff <= window.
   * Nearest pairingTime wins, then createdAt ascending, then id ascending.
   */
  fun findTransferPartner(row: Transaction, candidates: List<Transaction>, usableAccounts: Map<String, Account>): Transaction?

  /** The TRANSFER that replaces [a] and [b] (either order). Sender = the EXPENSE side. */
  fun buildPairedTransfer(a: Transaction, b: Transaction, id: String, now: Long): Transaction
  ```
  `isPairable`: `source == NOTIFICATION_PARSER && deletedAt == null && pairedIntoId == null && updatedAt == createdAt && type in {EXPENSE, INCOME}`.
  `buildPairedTransfer` fields: `type = TRANSFER`, `accountId`/`timestamp`/`postBalance` from the EXPENSE side; `toAccountId`, `toPostBalance = income.postBalance`, `toTimestamp = income.timestamp` from the INCOME side; `amount`; `feeAmount = 0`; `note` = expense note, or income note when blank; `merchantName` = expense merchant, or income merchant when null or blank; `categoryId = null`; `source = NOTIFICATION_PARSER`; `notificationLogId = null`; `createdAt = updatedAt = now`.

- [ ] **Step 1: Failing tests**, one per line:
  - pairs EXPENSE 500_000 on A with INCOME 500_000 on B, 5 min apart.
  - pairs when the partner is later than `row` (debit booked after its credit): `row` = INCOME at 10:02, candidate EXPENSE at 10:00 → found; `buildPairedTransfer` gives `accountId = A`, `timestamp = 10:00`.
  - window: 29 min 59 s pairs; 30 min 0 ms pairs; 30 min 1 ms does not.
  - no pair: different amount; both EXPENSE; same account; candidate `updatedAt != createdAt`; candidate `source = MANUAL`; candidate `pairedIntoId != null`; an account `CREDIT_CARD`; an account missing from `usableAccounts`.
  - two candidates 3 min and 1 min away → the 1 min one; equal distance → lower `createdAt`, then lower `id`.
  - `buildPairedTransfer` note falls back to the income note when the expense note is blank; merchant likewise; `notificationLogId == null`.
- [ ] **Step 2: Run, expect FAIL.** `--tests '*TransferPairingTest'`
- [ ] **Step 3: Implement** the four functions in `TransferPairing.kt`.
- [ ] **Step 4: Run, expect PASS.**
- [ ] **Step 5: Commit** `feat(pairing): pure transfer partner rules`

### Task 4: Pair while booking

**Files:**
- Modify: `main/data/transaction/repository/LedgerWriter.kt`, `main/data/transaction/local/dao/TransactionDao.kt`, `main/data/notification/local/dao/NotificationLogDao.kt`, `main/data/notification/repository/NotificationSettlementRepositoryImpl.kt`
- Test: `test/data/notification/repository/NotificationSettlementRepositoryImplTest.kt`

**Interfaces:**
- Consumes: Task 3 functions; `TransactionRules.check`.
- Produces:
  ```kotlin
  // TransactionDao: live NOTIFICATION_PARSER rows of this amount in [from, to], not paired, untouched
  suspend fun getPairCandidates(amount: Long, from: Long, to: Long): List<TransactionEntity>
  // NotificationLogDao: CONFIRMED -> PAIRED_TRANSFER, link unchanged; returns rows changed
  suspend fun markPairedTransfer(id: String): Int
  // LedgerWriter
  /** Inserts [newOriginal] soft-deleted, soft-deletes [partner], both with pairedIntoId = transfer.id; inserts
   *  [transfer]; cold start for both of its accounts; rebuilds every account of the three rows. */
  suspend fun insertPaired(transfer: Transaction, newOriginal: Transaction, partner: Transaction, now: Long)
  ```
  `LedgerWriter.insert(coldStart = true)` runs cold start for `accountId` and, when set, `toAccountId`.

- [ ] **Step 1: Failing tests** (Robolectric Room, existing setup), one per line:
  - `debit then credit 5 min later becomes one transfer`: after the second `book`, live rows = one `TRANSFER` A→B 500_000; two soft-deleted rows with `pairedIntoId = T.id`; both logs `PAIRED_TRANSFER`, each `linkedTransactionId` = its own original; A and B `currentBalance` match an equivalent manual transfer.
  - `second row is never live`: the second booked row id has `deletedAt != null` right after `book` returns.
  - `same worker run pairs`: two pending logs, one `SettlePendingLogsUseCase` run (use the existing fake clock pattern) → same result.
  - `MoMo top-up from VCB pairs` (bank EXPENSE + wallet INCOME) → `TRANSFER`. `MoMo paid from VCB` (both EXPENSE within 30 s) → still one EXPENSE + `DEDUPLICATED`, no transfer.
  - `wallet log deduplicated into a paired row keeps its link` → status `DEDUPLICATED`, link = the original row.
  - `partner account archived after its booking books the new row alone`: book A debit, archive A, book B credit → B row live as INCOME, no transfer, no error, its log `CONFIRMED`.
  - `uninitialized receiver gets its start from toPostBalance`.
  - `late receiver row after pairing does not cause drift`: pair (10:00 debit, 10:05 credit with `toPostBalance`), then book a B EXPENSE at 10:02 → `detectBalanceDrift` on B is null (anchor falls back) and `currentBalance` is right.
  - `failure while pairing rolls back the new row` (existing rollback style: throw inside the write) → no new row, logs still `PENDING`.
- [ ] **Step 2: Run, expect FAIL.** `--tests '*NotificationSettlementRepositoryImplTest'`
- [ ] **Step 3: Implement.** In `book`, after building the row and its `TransactionRules` check: query candidates in `[pairingTime - window, pairingTime + window]`, call `findTransferPartner` with the usable accounts. No partner → today's path. Partner → `buildPairedTransfer`, `TransactionRules.check(transfer, now, usable ids of both, null)`; refused → today's path; else `ledger.insertPaired(...)`, `markSettled(bank log, PAIRED_TRANSFER, newRow.id)`, wallet side `DEDUPLICATED` to `newRow.id`, `markPairedTransfer(partner.notificationLogId)` when not null.
- [ ] **Step 4: Run all unit tests, expect PASS.**
- [ ] **Step 5: Commit** `feat(pairing): pair a transfer when booking its second side`

### Task 5: N-18, invalid post time fails at planning

**Files:**
- Modify: `main/domain/transaction/service/TransactionRules.kt`, `main/domain/notification/service/SettlementPlanner.kt`
- Test: `test/domain/notification/service/SettlementPlannerTest.kt`, `test/domain/transaction/service/TransactionRulesTest.kt`

**Interfaces:**
- Produces: `fun TransactionRules.isValidTimestamp(timestamp: Long, now: Instant): Boolean` (used by `check` too); `const val FAIL_INVALID_TIME = "invalid_time"` next to the other reasons.

- [ ] **Step 1: Failing tests:** a pending log with `postTime` 400 days ahead → `Fail(log, null, FAIL_INVALID_TIME)` in the same run (not waiting to be due); `postTime` before 2000-01-01 → same; such a log never pairs with a valid one. `TransactionRulesTest`: `isValidTimestamp` at both bounds.
- [ ] **Step 2: Run, expect FAIL.**
- [ ] **Step 3: Implement.** In `Planner.settle`, logs with an invalid time are removed from the pool before pairing and emit `Fail` at once.
- [ ] **Step 4: Run all unit tests, expect PASS.**
- [ ] **Step 5: Commit** `fix(notif): fail a notification with an impossible time at planning`

### Task 6: Unpair

**Files:**
- Create: `main/domain/transaction/usecase/UnpairTransferUseCase.kt`
- Modify: `main/domain/transaction/repository/TransactionRepository.kt`, `main/data/transaction/repository/TransactionRepositoryImpl.kt`, `main/data/transaction/local/dao/TransactionDao.kt`, `main/data/notification/local/dao/NotificationLogDao.kt`, `test/domain/transaction/usecase/FakeTransactionRepository.kt`
- Test: `test/domain/transaction/usecase/UnpairTransferUseCaseTest.kt`, `test/data/transaction/repository/TransactionRepositoryImplTest.kt`

**Interfaces:**
- Produces:
  ```kotlin
  // TransactionRepository
  suspend fun getPairedOriginals(transferId: String): List<Transaction>   // rows with pairedIntoId = transferId
  fun observeAutoPairedIds(): Flow<Set<String>>                          // DISTINCT pairedIntoId, not null
  /** Soft-deletes the transfer, restores [originals] (deletedAt = null, pairedIntoId = null,
   *  updatedAt = maxOf(now, createdAt + 1)), their PAIRED_TRANSFER logs back to CONFIRMED,
   *  rebuilds every account of the transfer and the originals. */
  suspend fun unpair(transferId: String, now: Long)
  // NotificationLogDao: PAIRED_TRANSFER -> CONFIRMED for these ids
  suspend fun markUnpaired(ids: List<String>): Int
  // UnpairTransferUseCase
  suspend operator fun invoke(transferId: String): Outcome<Unit, TransactionError>
  ```
  Use case inside `tx.write`: `NotFound` when the transfer is missing or deleted or has no originals; `AccountUnusable` when an original's account is not usable; `AmountOverflow` on `ArithmeticException`; else `unpair`.

- [ ] **Step 1: Failing tests:**
  - Repository: `unpair restores both rows exactly` (all fields but `deletedAt`, `pairedIntoId`, `updatedAt` equal to before pairing); logs `CONFIRMED`; balances equal to two separate rows.
  - `unpair after the user moved T to a third account rebuilds all four`.
  - `unpair after T was switched to EXPENSE` works.
  - `restored rows never pair again`: with the clock not moved, book the same matching notification again → no transfer.
  - `unpair after merging B into D`: originals now on D; unpair succeeds; D balance right.
  - Use case: archived original account → `Err(AccountUnusable)`, nothing changed; unknown id → `Err(NotFound)`; a manual transfer → `Err(NotFound)`; a soft-deleted `T` → `Err(NotFound)`.
  - `plain delete of T keeps originals hidden`: `DeleteTransactionUseCase(T.id)` → originals still soft-deleted with `pairedIntoId`, logs still `PAIRED_TRANSFER`.
  - `observeAutoPairedIds` emits `{T.id}` after pairing, `{}` after unpair.
- [ ] **Step 2: Run, expect FAIL.** `--tests '*UnpairTransferUseCaseTest' --tests '*TransactionRepositoryImplTest'`
- [ ] **Step 3: Implement.** Extend `FakeTransactionRepository` with the three methods.
- [ ] **Step 4: Run all unit tests, expect PASS.**
- [ ] **Step 5: Commit** `feat(pairing): split an auto-paired transfer back`

### Task 7: UI, label, animation, unpair button

**Files:**
- Create: `main/ui/cashentry/component/UnpairConfirmDialog.kt`
- Modify: `main/ui/transactions/TransactionListContract.kt`, `TransactionListViewModel.kt`, `TransactionListScreen.kt`, `main/ui/transactions/logic/TransactionRowText.kt`, `main/ui/cashentry/CashEntryContract.kt`, `CashEntryViewModel.kt`, `CashEntryScreen.kt`, strings in 4 `values*`
- Test: `test/ui/transactions/TransactionListViewModelTest.kt`, `test/ui/transactions/logic/TransactionRowTextTest.kt`, `test/ui/cashentry/CashEntryEditViewModelTest.kt`

**Interfaces:**
- Consumes: `UnpairTransferUseCase`; two new one-line use cases in `main/domain/transaction/usecase/` (the UI never reaches a repository): `ObserveAutoPairedIdsUseCase(): Flow<Set<String>>` and `IsAutoPairedUseCase(id: String): Boolean` (`getPairedOriginals(id).isNotEmpty()`).
- Produces:
  - `TransactionListState.autoPairedIds: Set<String> = emptySet()`.
  - `rowSubtitle(..., autoPairedLabel: String?)`: appended after the accounts, before the reimbursement label.
  - `CashEntryState.isAutoPaired: Boolean`, `isUnpairConfirmOpen: Boolean`; intents `UnpairClicked`, `UnpairConfirmed`, `UnpairDismissed`.
  - Strings: `transactions_auto_paired` (vi "Tự ghép"), `cash_entry_unpair` (vi "Không phải chuyển khoản"), `cash_entry_unpair_confirm_title`, `cash_entry_unpair_confirm_body` (vi "Tách lại thành hai giao dịch Chi và Thu ban đầu. Những thay đổi bạn đã sửa trên dòng này sẽ mất.").

- [ ] **Step 1: Failing tests:**
  - `rowSubtitle` with `autoPairedLabel = "Tự ghép"` → `"VCB → MB · Tự ghép · note"`; null → unchanged output.
  - List VM: a paired id in the repository flow → state `autoPairedIds` contains it.
  - Edit VM: opening an auto-paired row → `isAutoPaired = true`; also after the row was switched to `EXPENSE`; a manual transfer → false. `UnpairConfirmed` calls the use case once and hides the sheet on `Ok`; `Err` → `hasSaveError = true`. `UnpairClicked` ignored while saving or in create mode.
- [ ] **Step 2: Run, expect FAIL.**
- [ ] **Step 3: Implement.** Edit VM reads `isAutoPaired` once at start through `IsAutoPairedUseCase`. Add `Modifier.animateItem()` to the transaction `items` in `TransactionListScreen`. Button in the edit sheet next to Delete, same style as Delete; dialog mirrors `DeleteConfirmDialog`.
- [ ] **Step 4: Run all unit tests, expect PASS.**
- [ ] **Step 5: Commit** `feat(ui): auto-paired label and split back action`

### Task 8: Docs and the gate

**Files:**
- Modify: `docs/roadmap.md`

- [ ] **Step 1: Roadmap.** Step 5 → ✅ when squashed; rewrite its bullet list to match the spec (book then pair, 30 min two-sided, exact amount). Close D12 (move to Đã chốt: "30 phút hai chiều; chỉ ghép khi bằng tiền; phí nhập tay"), remove T1, T5, N-18. Add to Nợ nhỏ: trash/purge rule for `pairedIntoId` rows (step 26); approach C bank time (after step 9); account merge marks rows touched (step 10).
- [ ] **Step 2: Gate.** `qualityGate` through `gradle-run`. Expected `exit_status` 0.
- [ ] **Step 3: Commit** `docs(pairing): roadmap after transfer pairing`
- [ ] **Step 4: Manual device check (user).** Not run by the agent. Transfer VCB → MB: one transfer row, "Tự ghép" label; unpair restores two rows.
