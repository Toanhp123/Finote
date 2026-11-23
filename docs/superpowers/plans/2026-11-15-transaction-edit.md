# Transaction Edit Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Tap a row in the transaction list to edit or soft-delete it in the existing cash-entry sheet.

**Architecture:** `CashEntryActivity` takes an optional transaction id; `CashEntryViewModel` runs the same form in edit mode. `UpdateTransactionUseCase` changes shape: it takes the id and the form fields and does read, copy, check and write inside one `tx.write`. A pure field check in `TransactionRules` refuses bad combinations before any `Transaction` is built.

**Tech Stack:** Kotlin, Compose (Material 3), Hilt, Room 3, kotlinx-coroutines-test, Turbine, Robolectric, Konsist, detekt.

**Spec:** `docs/superpowers/specs/2026-11-15-transaction-edit-design.md`. Read it before any task.

## Global Constraints

- Branch `feature/transaction-edit` from `dev`. Follow `docs/git-workflow.md`. No AI attribution in commits. `coder` never commits; the main session commits a task after its review.
- Money is `Long` VND. Never `Double`.
- Soft delete only (`deletedAt`). Every balance change runs inside one write transaction.
- Never log notification content, balances, account numbers, notes.
- Layers, naming, limits: `docs/architecture.md` (400 lines per file, 60 per function, 80 per `@Composable`). UI tokens and components: `docs/design-system.md`. No hard-coded UI values.
- Strings in `values` (`vi`), `values-en`, `values-zh-rCN`, `values-ja`.
- Skills for the implementer: `ponytail:ponytail`, `kotlin-concurrency-and-flow`, `compose-state-and-effects`, `testing-coroutines-with-runtest`, `testing-flows-with-turbine`, `using-robolectric-correctly`, `gradle-run`.
- Run Gradle only through `gradle-run`: `'.\gradlew.bat' testDebugUnitTest --tests '<pattern>'` from Bash. After each task the named tests pass; after Task 6, `qualityGate` passes.
- Kept on every edit: `id`, `source`, `notificationLogId`, `merchantName`, `createdAt`, `adjustmentDirection`.

## Review Focus

1. **Process death with the edit sheet open.** The id survives in `SavedStateHandle`; the sheet reloads the row and must not crash or fall back to create mode. Test in Task 3.
2. **A new date whose old time of day does not exist** (DST gap in the clock's zone). The save must succeed with a valid timestamp on that date. Test in Task 2.
3. **A transfer whose destination was archived.** The form opens, the destination is empty, Save is disabled until the user picks one; nothing is written meanwhile. Test in Task 3.
4. **A notification amount longer than `AmountExpression.MAX_DIGITS` (13).** Prefill must show it and save it unchanged when the user edits only the note. Test in Task 3.
5. **Two fast taps on one row.** The user expects one editor. Manual check in Task 6; if two open, set `launchMode="singleTop"` on `CashEntryActivity` and re-check the tile and the shortcut.

---

### Task 1: Field check before a `Transaction` is built

**Files:**
- Create: `app/src/main/java/io/github/toanhp123/finote/domain/transaction/model/TransactionFields.kt`
- Modify: `domain/transaction/error/TransactionError.kt`, `domain/transaction/service/TransactionRules.kt`, `domain/transaction/usecase/AddManualTransactionUseCase.kt`
- Test: `app/src/test/java/.../domain/transaction/service/TransactionRulesTest.kt`, the existing `AddManualTransactionUseCase` test

**Interfaces:**
- Produces:
  ```kotlin
  data class TransactionFields(
      val amount: Long, val feeAmount: Long, val type: TransactionType,
      val accountId: String, val toAccountId: String?, val categoryId: String?,
  )
  // TransactionError: two new objects
  data object InvalidAmount : TransactionError      // amount <= 0
  data object UnsupportedType : TransactionError    // a type the form cannot write (ADJUSTMENT)
  // TransactionRules
  fun checkFields(fields: TransactionFields): TransactionError?
  ```

- [ ] **Step 0: Branch.** Create `feature/transaction-edit` from `dev`. Commit the spec and this plan there as `docs: transaction edit design and plan`.
- [ ] **Step 1: Write failing tests** in `TransactionRulesTest`, one per line:
  - `amount 0` and `amount -1` → `InvalidAmount`
  - `type ADJUSTMENT` → `UnsupportedType`
  - `feeAmount -1` → `InvalidTransfer`
  - EXPENSE with `toAccountId` set → `InvalidTransfer`; EXPENSE with `feeAmount 1` → `InvalidTransfer`
  - TRANSFER with `toAccountId null` → `InvalidTransfer`; with `toAccountId == accountId` → `InvalidTransfer`; with `categoryId` set → `InvalidTransfer`
  - TRANSFER with `amount = Long.MAX_VALUE`, `feeAmount = 1` → `AmountOverflow`
  - valid EXPENSE, INCOME, TRANSFER (fee 0 and fee 3_300) → `null`
  - Order when several are wrong: `InvalidAmount` first, then `UnsupportedType`, then the transfer checks.
- [ ] **Step 2: Run** `--tests '*TransactionRulesTest*'`. Expected: compile failure (`checkFields` missing).
- [ ] **Step 3: Implement** `TransactionFields`, the two errors, `checkFields`. Move the body of `AddManualTransactionUseCase.refusalOf` into it; delete `refusalOf`; the use case calls `TransactionRules.checkFields(...)` before it builds the `Transaction`.
- [ ] **Step 4: Add to the add use case test:** `amount = 0` returns `Outcome.Err(InvalidAmount)` and `type = ADJUSTMENT` returns `Outcome.Err(UnsupportedType)`; neither throws.
- [ ] **Step 5: Run** `--tests '*TransactionRulesTest*' --tests '*AddManualTransactionUseCase*'`. Expected: PASS.
- [ ] **Step 6: Commit** `refactor: transaction field check in the rules` (scope per `docs/git-workflow.md`).

---

### Task 2: `UpdateTransactionUseCase` takes the id and the form fields

**Files:**
- Create: `domain/transaction/model/TransactionEdit.kt`, `domain/transaction/usecase/GetTransactionUseCase.kt`
- Modify: `domain/transaction/usecase/UpdateTransactionUseCase.kt`
- Modify (caller removal): `ui/transactions/TransactionListViewModel.kt`, `TransactionListContract.kt`, `TransactionListScreen.kt`, `ui/transactions/component/ReimbursementSheet.kt`
- Test: `UpdateTransactionUseCaseTest.kt`, `TransactionUseCaseTransactionTest.kt`, `TransactionListViewModelTest.kt`, `app/src/screenshotTest/.../TransactionListScreenshots.kt`

**Interfaces:**
- Consumes: `TransactionFields`, `TransactionRules.checkFields` (Task 1).
- Produces:
  ```kotlin
  data class TransactionEdit(
      val fields: TransactionFields, val date: LocalDate, val note: String,
      val isExcludedFromAnalytics: Boolean, val reimbursementStatus: ReimbursementStatus,
  )
  class UpdateTransactionUseCase { suspend operator fun invoke(id: String, edit: TransactionEdit): Outcome<Unit, TransactionError> }
  /** The live row, or null when it is missing or soft-deleted. */
  class GetTransactionUseCase { suspend operator fun invoke(id: String): Transaction? }
  ```

Rules inside one `tx.write`, in this order:
1. `checkFields(edit.fields)` → `Err`.
2. Load the row. Missing, deleted, or `type == ADJUSTMENT` → `NotFound`.
3. `existing.copy(...)` with: the six fields; `note.trim()`; reimbursement pair only when the new type is `EXPENSE`, else `false` / `NONE`; `timestamp` and `postBalance` as below; `updatedAt = clock.millis()`.
4. The checks the use case runs today (old accounts usable, new accounts usable, `TransactionRules.check`), then `repository.update`; `ArithmeticException` → `AmountOverflow`.

Timestamp, with `oldDate` = the row's date in `clock.zone`:
- `edit.date == oldDate` → unchanged.
- else → `edit.date` at the row's old local time, in `clock.zone`. If `edit.date` is today and that instant is after `clock.instant()` → `clock.millis()`.

`postBalance`: kept only when `accountId` is unchanged **and** `edit.date == oldDate`; else `null`.

- [ ] **Step 1: Rewrite `UpdateTransactionUseCaseTest`** for the new signature. Use a fixed clock `2026-11-15T07:00:00Z`, zone `Asia/Ho_Chi_Minh` (14:00 local) and a notification row: EXPENSE 150_000, account `a1`, timestamp `2026-11-14T16:30:00Z` (23:30 local on 14/11), `postBalance = 5_000_000`, `source = NOTIFICATION`, `notificationLogId = "log1"`, `merchantName = "CIRCLE K"`, `createdAt = 1`. Tests:
  - note-only edit: `source`, `notificationLogId`, `merchantName`, `createdAt`, `timestamp`, `postBalance` all unchanged; `updatedAt == clock.millis()`
  - amount → 120_000, same date and account: `postBalance == 5_000_000`
  - account → `a2`: `postBalance == null`
  - date → 2026-11-10: `postBalance == null`; timestamp is 2026-11-10 23:30 local
  - date → 2026-11-15 (today): timestamp `== clock.millis()` (23:30 would be ahead of 14:00)
  - type EXPENSE → INCOME with `PENDING`/`true` passed: stored `NONE`/`false`
  - EXPENSE → TRANSFER to `a2`, fee 1_100: stored `toAccountId == "a2"`, `feeAmount == 1_100`, `categoryId == null`
  - missing id, deleted row, ADJUSTMENT row → `NotFound`
  - `amount = 0` → `InvalidAmount`; nothing written
  - old account archived → `AccountUnusable`; new account archived → `AccountUnusable`
  - Review Focus 2: zone `Europe/Berlin`, row at 02:30 local, date → 2026-03-29 (02:30 does not exist): `Ok`, and the stored timestamp is on 2026-03-29 local.
- [ ] **Step 2: Update `TransactionUseCaseTransactionTest`**: the update case still rolls back on overflow with the new signature.
- [ ] **Step 3: Run** `--tests '*UpdateTransactionUseCase*' --tests '*TransactionUseCaseTransactionTest*'`. Expected: compile failure.
- [ ] **Step 4: Implement** `TransactionEdit`, `GetTransactionUseCase`, the new `UpdateTransactionUseCase`.
- [ ] **Step 5: Remove the only old caller.** In `ui/transactions`: delete `ReimbursementPicked`, `SheetDismissed`, `editingId`, `hasSaveError`, `pick`, `write`, `pickJob`, the `ReimbursementSheet(...)` call, the `ReimbursementSheet` wrapper composable, and `UpdateTransactionUseCase` from the ViewModel. `RowClicked` does nothing until Task 5; `DayList` passes `onClick = null`. Keep `ReimbursementChoice.kt` and `ReimbursementSheetContent` (Task 4 moves them). Delete the reimbursement tests in `TransactionListViewModelTest` and the sheet frames in `TransactionListScreenshots`.
- [ ] **Step 6: Run** `testDebugUnitTest`. Expected: PASS.
- [ ] **Step 7: Commit** `refactor: update use case reads and writes in one transaction`.

---

### Task 3: Edit mode in `CashEntryViewModel`

**Files:**
- Modify: `ui/cashentry/CashEntryViewModel.kt`, `CashEntryContract.kt`, `CashEntryActivity.kt`
- Move: `ui/transactions/component/ReimbursementChoice.kt` → `ui/cashentry/component/ReimbursementChoice.kt` (fix imports in `ui/transactions`)
- Test: `ui/cashentry/CashEntryViewModelTest.kt`

**Interfaces:**
- Consumes: `GetTransactionUseCase`, `UpdateTransactionUseCase(id, TransactionEdit)`, `DeleteTransactionUseCase(id)`.
- Produces:
  ```kotlin
  // CashEntryActivity
  companion object { const val EXTRA_TRANSACTION_ID = "transaction_id"
                     fun editIntent(context: Context, id: String): Intent }
  // CashEntryState, new fields
  val isEditing: Boolean = false
  val isLoading: Boolean = false          // true in edit mode until the row filled the state
  val reimbursement: ReimbursementChoice = ReimbursementChoice.NORMAL
  val isDeleteConfirmOpen: Boolean = false
  // CashEntryIntent, new
  data class ReimbursementPicked(val choice: ReimbursementChoice)
  data object DeleteClicked; data object DeleteConfirmed; data object DeleteDismissed
  // CashEntryEffect, new
  data object Close     // the sheet was never shown, so the Activity must finish without the exit animation
  ```
- The ViewModel gets `SavedStateHandle` and reads `EXTRA_TRANSACTION_ID`. Null → create mode, behaviour unchanged.

Behaviour (spec, section "Form"):
- Edit mode starts with `isEditing = true`, `isLoading = true`. On the first lists emission it loads the row once and fills: `expression = amount.toString()`, `result = Ok(amount)`, `type`, `selectedAccountId`, `destinationId`, `fee = feeAmount`, `selectedCategoryIds = mapOf(row.type to pickCategory(row.categoryId))`, `note`, `date`, `isToday`, `reimbursement = row.reimbursementChoice()`, `isLoading = false`. The last-choices seed is skipped.
- Row null, or `ADJUSTMENT`, or its source account not in the live list (at load or on a later emission) → `sendEffect(Close)` when still loading, else `isVisible = false`. Edit mode never calls `CashEntryDefaults.pickAccount`.
- Save: same per-type normalisation as create (`save()` today), then `updateTransaction(id, TransactionEdit(...))`; reimbursement from state.
- `DeleteClicked` opens the confirm; `DeleteDismissed` closes it; `DeleteConfirmed` closes it, sets `isSaving = true`, calls `deleteTransaction(id)`; `Ok` → `isVisible = false`; `Err` or storage error → `showSaveError()`. Delete intents are ignored in create mode and while `isSaving`.
- If the file passes ~350 lines, move the prefill mapping (`Transaction` → state) to a top-level function in a new `CashEntryPrefill.kt`. Do not split further.

- [ ] **Step 1: Write failing tests** in `CashEntryViewModelTest` (helper: a ViewModel built with `SavedStateHandle(mapOf("transaction_id" to "t1"))` and a fake repository holding the row):
  - `edit mode fills the form from the row`: TRANSFER 200_000 `a1`→`a2`, fee 3_300, note "ATM" → state has `expression == "200000"`, `type == TRANSFER`, `destinationId == "a2"`, `fee == 3_300L`, `isEditing`, `!isLoading`
  - `a row without a category stays without one` (notification EXPENSE): `selectedCategoryIds[EXPENSE] == null`, and after save the stored `categoryId == null`
  - `a hidden category shows as none`
  - `save in edit mode updates and does not add`: repository has one row, amount changed
  - `transfer changed to expense saves no destination and no fee`
  - `reimbursement pick is saved for an expense`
  - `delete confirmed soft-deletes and closes`; `a failed delete closes the dialog and shows the save error`; `delete is ignored in create mode`
  - `a missing row sends Close`; `a row on an archived account sends Close`
  - `create mode is unchanged`: no id → `!isEditing`, seed from last choices as today
  - Review Focus 1: a second ViewModel built from the same `SavedStateHandle` fills the form again
  - Review Focus 3: destination `a2` archived → `destinationId == null`, `!isSaveEnabled`, repository row unchanged
  - Review Focus 4: amount `99_999_999_999_999` (14 digits), edit the note only, save → stored amount unchanged
- [ ] **Step 2: Run** `--tests '*CashEntryViewModelTest*'`. Expected: compile failure.
- [ ] **Step 3: Implement** the contract additions, the ViewModel edit mode, `CashEntryActivity.editIntent`, the file move.
- [ ] **Step 4: Run** `--tests '*CashEntryViewModelTest*'`. Expected: PASS.
- [ ] **Step 5: Commit** `feat(ui): edit mode in the cash entry view model`.

---

### Task 4: Edit mode in the sheet

**Files:**
- Modify: `ui/cashentry/CashEntryScreen.kt`, `ui/cashentry/component/CashEntryPickers.kt` (or a new small file in `ui/cashentry/component/` for the reimbursement row and delete button when a composable would pass 80 lines)
- Move and trim: `ui/transactions/component/ReimbursementSheet.kt` → the reimbursement row in `ui/cashentry/component/`
- Modify: `res/values*/strings.xml` (4 files)
- Test: `app/src/screenshotTest/.../cashentry/CashEntryScreenshots.kt`

**Interfaces:**
- Consumes: Task 3 state, intents, `CashEntryEffect.Close`.

- [ ] **Step 1: Check `FinoteBottomSheet`** with `isVisible = false` from the first composition: it must not call `onDismissComplete`. If it does, the route must guard it (ignore the callback while `state.isLoading`).
- [ ] **Step 2: Route.** `CashEntryRoute` shows the sheet with `isVisible = state.isVisible && !state.isLoading`, and collects `effects`: `Close` → `onClose()`.
- [ ] **Step 3: Keypad page, edit mode only.**
  - A trash `FinoteIconButton` in the row of the type selector, content description `cash_entry_delete_cd`, disabled while `isSaving`. Not on picker pages.
  - Under the selectors, when `type == EXPENSE`: the reimbursement segmented control (the existing `FinoteSegmented` over `ReimbursementChoice.entries`) with the existing title string as its label.
  - `FinoteDialog(isVisible = state.isDeleteConfirmOpen, isDestructive = true, ...)` with title `cash_entry_delete_title`, text `cash_entry_delete_text`, confirm `action_delete` (reuse an existing delete string if one exists).
- [ ] **Step 4: Strings**, four languages:
  - `cash_entry_delete_cd`: vi "Xóa giao dịch", en "Delete transaction"
  - `cash_entry_delete_title`: vi "Xóa giao dịch này?", en "Delete this transaction?"
  - `cash_entry_delete_text`: vi "Số dư tài khoản sẽ được tính lại.", en "The account balance will be recalculated."
  - `ja`, `zh-rCN`: translate the same meaning.
- [ ] **Step 5: Screenshots.** Add `CashEntryEditExpensePreview` (edit mode, reimbursement row, `PENDING`), `CashEntryEditTransferPreview`, `CashEntryDeleteConfirmPreview`. Record them with the project's screenshot update task, look at the images.
- [ ] **Step 6: Run** `testDebugUnitTest` and the screenshot validation task. Expected: PASS.
- [ ] **Step 7: Commit** `feat(ui): edit and delete in the cash entry sheet`.

---

### Task 5: The list opens the editor; adjustments are delete-only

**Files:**
- Create: `ui/transactions/TransactionOpenRule.kt`
- Modify: `TransactionListViewModel.kt`, `TransactionListContract.kt`, `TransactionListScreen.kt`, `ui/navigation/FinoteNavHost.kt`, `res/values*/strings.xml`
- Test: `TransactionListViewModelTest.kt`, `TransactionListScreenshots.kt`

**Interfaces:**
- Consumes: `CashEntryActivity.editIntent`, `DeleteTransactionUseCase`.
- Produces:
  ```kotlin
  /** Every account the row touches (source, and destination of a transfer) is in [liveAccountIds]. */
  fun Transaction.isOpenable(liveAccountIds: Set<String>): Boolean
  // TransactionListState
  val deletingId: String? = null
  // TransactionListIntent
  data object DeleteConfirmed; data object DeleteDismissed
  // TransactionListEffect
  data class OpenEditor(val id: String); data object DeleteFailed
  // TransactionListRoute gains
  snackbarState: FinoteSnackbarState
  ```

Behaviour:
- `RowClicked(id)`: not openable → nothing. `ADJUSTMENT` → `deletingId = id`. Else → `sendEffect(OpenEditor(id))`.
- `DeleteConfirmed`: clears `deletingId`, calls `deleteTransaction(id)`; `Err` or storage error → `sendEffect(DeleteFailed)`. `DeleteDismissed` clears `deletingId`.
- Screen: `DayList` uses `transaction.isOpenable(openableAccountIds)` for `onClick`; a `FinoteDialog` for `deletingId != null` (title `transactions_delete_adjustment_title`: vi "Xóa điều chỉnh này?", en "Delete this adjustment?"; text: vi "Nếu số dư còn lệch, bạn có thể điều chỉnh lại.", en "If the balance still differs, you can adjust again.").
- Route: collects effects with the lifecycle; `OpenEditor` → `context.startActivity(CashEntryActivity.editIntent(context, id))`; `DeleteFailed` → snackbar `transactions_delete_error` (vi "Không xóa được. Thử lại.", en "Could not delete. Try again."). `FinoteNavHost` passes its `snackbarState`.

- [ ] **Step 1: Write failing tests** in `TransactionListViewModelTest` (Turbine on `effects`):
  - `a click on an expense, an income and a transfer sends OpenEditor`
  - `a row on an archived account sends nothing`; `a transfer whose destination is archived sends nothing`
  - `a click on an adjustment opens the confirm and sends no effect`
  - `confirm deletes the adjustment`; `a failed delete sends DeleteFailed`; `dismiss clears deletingId`
  - unit test for `isOpenable`: source only, both ends, destination missing
- [ ] **Step 2: Run** `--tests '*TransactionListViewModelTest*'`. Expected: FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Screenshot:** list with the adjustment confirm dialog. Record, look, validate.
- [ ] **Step 5: Run** `testDebugUnitTest`. Expected: PASS.
- [ ] **Step 6: Commit** `feat(ui): open the editor from the transaction list`.

---

### Task 6: N-04, gate, docs, device check

**Files:**
- Modify: `domain/account/usecase/ObserveBalanceDriftUseCase.kt`, `ui/transactions/TransactionListScreen.kt` (`DriftBanners`), `ui/transactions/component/BalanceDriftBanner.kt`, `res/values*/strings.xml`, `docs/roadmap.md`
- Test: a new `ObserveBalanceDriftUseCaseTest.kt`, `TransactionListScreenshots.kt`

**Interfaces:**
- Produces: `BalanceDriftBanner(..., otherCount: Int = 0)`; plurals `balance_drift_others` (en: one "and %d other account", other "and %d other accounts"; vi: "và %d tài khoản khác").

- [ ] **Step 1: Failing test** `one account that overflows does not hide the drift of another`: two usable accounts; the first one's ledger makes `detectBalanceDrift` throw `ArithmeticException` (initial balance `Long.MAX_VALUE` plus an income before the anchor); the second drifts by 10_000 → the flow emits exactly the second account's drift.
- [ ] **Step 2: Run** `--tests '*ObserveBalanceDriftUseCaseTest*'`. Expected: FAIL with `ArithmeticException`.
- [ ] **Step 3: Implement.** Inside the `combine` lambda catch `ArithmeticException` per account and skip that account. Catch nothing wider, log nothing.
- [ ] **Step 4: Banner.** `DriftBanners` draws only the first drift whose account is in `accountsById`, with `otherCount = the remaining count`; the banner shows the plurals line under the message when `otherCount > 0`. `AdjustDrift` keeps carrying that drift's `accountId`.
- [ ] **Step 5: Screenshot:** list with three drifting accounts (one banner, "and 2 other accounts"). Record, look, validate.
- [ ] **Step 6: Run `qualityGate`** through `gradle-run`. Expected: `exit_status` 0. Fix what it reports; do not suppress without the reason `docs/architecture.md` asks for.
- [ ] **Step 7: Roadmap.** In `docs/roadmap.md`: step 4 → ✅; section 1 "next step" → step 5; N2 row: the form has an edit mode; delete the N-04 row; under step 4 note that `postBalance` is cleared on an account or date change.
- [ ] **Step 8: Commit** `fix(ui): one drift banner and a drift flow that survives one bad account`, then `docs: roadmap after transaction edit`.
- [ ] **Step 9: `reviewer`** on the branch (once). Send findings to `coder`, review again.
- [ ] **Step 10: Manual check by the user on a device** (ask; do not run instrumented tests unasked):
  - edit a manual row and a notification row; change type Expense → Transfer; delete; delete an adjustment and adjust again
  - fee page: number keyboard and focus; TalkBack reads destination, fee, reimbursement, delete
  - sheet height in Transfer mode and in edit mode with the reimbursement row
  - Review Focus 5: two fast taps on one row open one editor
- [ ] **Step 11: Ask the user** before the squash into `dev`.
