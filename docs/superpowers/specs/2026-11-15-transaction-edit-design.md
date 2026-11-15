# Transaction edit (roadmap step 4): design

Date: 2026-11-15. Status: approved in chat.
Branch: `feature/transaction-edit`. Source: `docs/roadmap.md` section 5 step 4, debt N-04. Uses foundations N1 (one ledger write path) and N2 (shared transaction form).

## Why

Notification capture will book wrong rows during the dogfood week (step 9): wrong category, an ATM withdrawal booked as an expense, a wrong account. Today the user cannot open a row and fix it. Without edit and delete, a wrong auto-captured row stays wrong.

## Goals

1. Tap a row in the transaction list, change amount, date, account, category, note, type, and save.
2. Soft-delete a row from the same place.
3. One form for create and edit. No second form, no second ViewModel.
4. A bad field combination is an `Outcome.Err`, never an exception from `Transaction.init`, on both the add path and the edit path.
5. N-04: several drifting accounts do not push the list off a small screen; one failing account does not stop drift reports for the others.

## Non-goals

- Undo after delete, a trash screen, restore (step 26).
- Editing an `ADJUSTMENT` row (delete only).
- Editing `merchantName`, tags, split, refund link.
- Renaming `CashEntry*` to a neutral name.
- An in-app "add" button; the tile and the shortcut stay the only create entries.
- The reimbursement choice in create mode.

## Anti-goals

- An edit that silently changes `source`, `notificationLogId`, `createdAt` or `merchantName`.
- A `postBalance` that ends up describing an account the bank never printed it for.
- Any balance change outside the existing `UpdateTransactionUseCase` / `DeleteTransactionUseCase` write transaction.

## Approaches

1. **Launch `CashEntryActivity` with a transaction id (picked).** The list emits an effect, the Activity gets an extra, `CashEntryViewModel` reads it from `SavedStateHandle` and runs in edit mode. One form, one ViewModel, least new code. Cost: the ViewModel grows from 215 to roughly 300 lines (limit 400), and the `CashEntry` name fits a little worse.
2. **Host the sheet inside the list screen.** No Activity hop, but the form ViewModel must be scoped per open row in Navigation3 and the sheet gets two hosts. More wiring, nothing the user can see.
3. **A separate `EditTransactionViewModel` over the stateless sheet composable.** Copies keypad, account, category and fee logic. The next N2 users (failed-inbox, refund) would copy it a third time.

The user picked 1.

## Decided with the user

- Tapping a row opens the edit form directly. The reimbursement choice and the delete button live in the form. The old `ReimbursementSheet` on the list goes away.
- An `ADJUSTMENT` row is delete-only: tap, confirm dialog, soft delete. If the account still drifts, the banner comes back and "Adjust" books a fresh, correct row.

## Design

### Domain

- `TransactionRules` gets a pure field check that runs before a `Transaction` is built, so `Transaction.init` can never be the first to refuse. It holds what `AddManualTransactionUseCase.refusalOf` holds today (negative fee, destination or fee on a non-transfer, missing or same destination, category on a transfer, `amount + fee` overflow), and two new refusals, each with its own new `TransactionError`: `amount <= 0`, and a type the form cannot write (`ADJUSTMENT`). `AddManualTransactionUseCase` calls it and loses `refusalOf`.
- `UpdateTransactionUseCase` changes shape. Its only UI caller today is the reimbursement sheet, which this branch removes, so there is no second use case. It takes the row id and one parameter object with the form fields (amount, type, source, destination, category, date, note, fee, reimbursement). Inside **one** `tx.write`:
  1. Runs the field check. An error returns `Outcome.Err`.
  2. Loads the row. Missing, deleted, or `ADJUSTMENT` is `NotFound`.
  3. Builds `existing.copy(...)` with only the form fields changed.
  4. Runs the checks it runs today (old and new accounts usable, `TransactionRules.check`), then writes and rebuilds the ledger.

  Read, copy and write share the transaction, so a background write between the read and the write cannot be overwritten.
- Kept as is on every edit: `id`, `source`, `notificationLogId`, `merchantName`, `createdAt`, `adjustmentDirection` (null).
- `postBalance` is kept only when both the source account and the local date are unchanged. **When `accountId` or the date changes, `postBalance` becomes null.** The bank printed that balance for one account at one point in the ledger order (`findBalanceAnchor` takes the newest row that carries one). On another account or another day it would be a false anchor for the opening balance and the drift check. The row itself stays; only its anchor role goes. (Author's call; the roadmap line says "keep `postBalance`" and did not consider these two edits.)
- Changing the amount, type, category or note of a notification row keeps `postBalance`: the drift banner may appear, and the user decides.
- Timestamp: the date is unchanged → `timestamp` is unchanged. The date changed → the row's old local time of day on the new date, in the clock's zone, so order inside a day does not jump. One exception: the new date is today and that time is still ahead of the clock → now, the same rule the add path uses for today. (Author's call.)
- Reimbursement: saved only when the type is `EXPENSE`; any other type saves `isExcludedFromAnalytics = false`, `ReimbursementStatus.NONE`.
- A read use case for one transaction if none exists yet (the ViewModel must not call the repository).
- `LongParameterList`: the parameter object keeps the update use case and the field check short. The add use case keeps its signature and its existing justified `@Suppress`.

### Form (`ui/cashentry`)

- `CashEntryActivity` accepts an optional string extra with the transaction id. `CashEntryViewModel` reads it from `SavedStateHandle`. Null means create mode, unchanged.
- Edit mode, on the first lists emission: load the row and fill the state. `expression = amount.toString()`, type, source account, destination, `fee = feeAmount` (a `Long`, 0 for none), the category of that type, note, date (from `timestamp` in the clock's zone), reimbursement choice. The "last manual choices" seed is skipped. A row that is missing, deleted or an `ADJUSTMENT` closes the sheet.
- Edit mode never picks an account for the user. Today `onLists` replaces a source that left the live list with a default; in edit mode that would move the row silently. A source account that is not in the live list closes the sheet instead. A transfer destination that left the list is cleared, which disables Save until the user picks one.
- The row's category goes through the existing `CashEntryDefaults.pickCategory`: a deleted, hidden or wrong-type category shows as none and saves as null. A row without a category (every notification row today) stays without one. The other type's category starts empty.
- The sheet must not draw the create-mode defaults before the row is loaded (no flash of "Expense, 0").
- Type can change among Expense, Income, Transfer, also for a notification row. Save sends only the fields of the current type, as `save()` does today: a transfer sends no category; another type sends no destination and fee 0. A switch to Transfer has no destination until the user picks one, as in create mode.
- New state: an `isEditing` flag, the reimbursement choice, and whether the delete confirm is open. New intents: reimbursement picked, delete clicked, delete confirmed, delete dismissed.
- Sheet in edit mode:

```
[Expense | Income | Transfer]        🗑
            150.000 đ
Wallet · Food · Today · Note
Reimbursement: [None | Pending | Settled]     (Expense only)
[ keypad ]                           [Save]
```

- Save calls `UpdateTransactionUseCase`. `Ok` closes the sheet; `Err` or a storage error shows the existing save error line.
- Delete: trash icon button with a content description, on the keypad page only → confirm dialog (the shared dialog component) → the dialog closes, then `DeleteTransactionUseCase` runs → the sheet closes. A failure shows the save error line on the sheet, never under an open dialog. Save and delete are both disabled while either runs.
- `ReimbursementChoice` and its segmented control move from `ui/transactions/component` to where the form can use them; the list no longer needs them.
- Strings in `vi`, `en`, `zh-rCN`, `ja`.

### List (`ui/transactions`)

- `RowClicked`:
  - Expense, Income, Transfer whose accounts (source, and destination for a transfer) are all in the live account list → effect `OpenEditor(id)`. The route starts `CashEntryActivity` with the extra.
  - `ADJUSTMENT` on a live account → state holds the id to delete; the screen shows the confirm dialog; confirm closes it and calls `DeleteTransactionUseCase`. A failure shows a snackbar, the way the accounts screen reports a failed action.
  - Anything else has no click, as today. The screen's click condition and the ViewModel's guard are the same rule.
- Removed: `ReimbursementSheet`, `ReimbursementPicked`, `editingId`, `hasSaveError` for that sheet, and `UpdateTransactionUseCase` from this ViewModel.
- The list updates by itself through its `Flow` after the Activity saves.

### N-04

- Banners: at most one drift banner is on screen (the first drift). When more accounts drift, the banner says how many others, with a `<plurals>` resource. Adjusting one reveals the next. `AdjustDrift` already carries the account id of the banner shown. (Author's call.)
- Drift flow: an account whose drift cannot be computed is skipped; the others still report. Nothing about the failure is logged. `CancellationException` is never swallowed.

## Tests

- `TransactionRules` field check: each refusal, and the allowed shapes.
- `UpdateTransactionUseCase`: kept fields; `postBalance` kept, and cleared on an account change and on a date change; timestamp kept, time of day kept on a date change, and capped at now for today; reimbursement cleared for a non-expense; `NotFound` for missing, deleted, `ADJUSTMENT`; a bad combination (also amount 0 and type `ADJUSTMENT`) is `Err`, not an exception; rollback on overflow (same shape as the existing write-path tests).
- `CashEntryViewModel`: prefill from a row; a row without a category stays without one; a hidden category shows as none; create mode unchanged; save calls update, not add; Transfer → Expense saves no destination and no fee; delete flow and its failure; a missing row or an unusable source account closes the sheet.
- `TransactionListViewModel`: `OpenEditor` effect for the three types; no effect for a row on an archived account; `ADJUSTMENT` confirm and delete; reimbursement tests removed.
- Drift: one failing account does not hide the others.
- Screenshot tests: edit sheet (expense with reimbursement row, transfer), list with one banner and a count.
- Gate: `testDebugUnitTest`, then `qualityGate` before the squash.

## Manual check on a device (user)

Step 3 was never tried on a real device; check it together with the edit form: number keyboard and focus on the fee page, TalkBack on destination, fee, reimbursement and delete, sheet height in Transfer mode and in edit mode with the reimbursement row.

## Left to the builder

- Names of the new intent, effect, extra key and use case parameters.
- Whether the ViewModel needs a small helper file to stay readable under the 400-line limit.
- The exact wording of the "N other accounts" banner line.
- How the drift use case isolates a failing account.
