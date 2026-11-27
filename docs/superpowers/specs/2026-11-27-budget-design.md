# Budget and app notifications (roadmap step 18): design

Date: 2026-11-27. Status: implemented on feature/budget (2026-11-28). Revised the same day after a builder check and an outside review (Gemini) checked it against the code.
Branch (planned): `feature/budget` from `dev`, after `feature/dashboard` is squashed into `dev` (this step needs `domain/report`). Source: `docs/roadmap.md` section 4 (N6, N7), section 5 step 18; debt N-28. Spec 3.5 `Budget` (spec L136–146), spec 4 "Ngân sách hạn mức & Kết chuyển (Rollover)" (spec L348–350). Related: step 17 (dashboard, `ReportCalculator`, `ReportList`), step 24 (recurring bills reuse N7), step 26 (trash purge), step 28 (masking through `MoneyText`).

## Why

The user wants a budget that works "like other apps" (Money Lover, Cashew, Spendee): set a monthly limit once, see at a glance how much is left, and get warned before overspending. Finote captures spending automatically, so the warning can come without the user opening the app.

The `Budget` tab already exists in the nav bar (`NavKeys.kt` `Budget`, icon `ic_pie_chart`) and shows `ComingSoonScreen`.

## Goals

1. **Two kinds of budget**, both monthly:
   - One total budget for all spending of the month (`categoryId = null`).
   - Budgets for top-level Expense categories. A parent's budget counts its children's spending.
2. **Set once, repeats every month.** A month that was never set up gets a copy of the latest earlier set-up month (see "Copy forward"). Only the current month can be created, edited or deleted. Past months are read-only history.
3. **Rollover**, off by default, a switch per budget. When on, last month's leftover (or overspend) is added to (or taken from) this month's available amount.
4. **Alerts**: one system notification when spending reaches the threshold (default 80%) and one at 100%, at most once each per budget per month. Only for the current month.
5. **Budget screen** (the `Budget` tab):
   ```
   Ngân sách                                 [+]
   ‹  Tháng 11/2026  ›
   ┌ Tổng ngân sách ─────────────────┐
   │ Đã chi 6.200.000 / 10.000.000   │
   │ ████████████░░░░░░  62%         │
   │ Còn 3.800.000 · ~950.000/ngày   │
   └─────────────────────────────────┘
   Ăn uống      2.900.000 / 3.000.000  ███████████░ 96%     [✎]
   Mua sắm      1.500.000 / 1.000.000  ████████████ Vượt   [✎]
   Đi lại         300.000 / 1.000.000  ███░░░░░░░░ 30%      [✎]
   ```
   - `+` sits on `FinoteTopBar` (no FAB in this design system), current month only, hidden when the total and every shown top-level Expense category already have a budget. The empty state (`FinoteEmptyState`) has its own add button.
   - Month header with ‹ ›, no future months (same as Overview). The screen refreshes the current month on resume (`LifecycleResumeEffect`, like `OverviewScreen`).
   - The total card on top when a total budget exists; category rows below, sorted by percent used (highest first), then spent (highest first), then the category's `sortOrder`, then id.
   - States (see "Percent and states"): normal; warning from the threshold; over from 100%. Warning and over always carry a word ("Sắp hết", "Vượt", "Hết"), not only a color.
   - Card text: "Còn X" while something is left, "Vượt X" when over. "~X/ngày" = what is left ÷ days left in the month including today; current month only, hidden when nothing is left.
   - With rollover on and a non-zero carry, a small line under the row: "Gồm kết chuyển +X" (or "−X"), so the user sees why the amount differs from the limit they set.
   - Tapping a row opens `ReportList` for that month: the category's bucket, or a new "whole month spending" kind for the total card. Its total equals the spent number shown.
   - Each row has an edit icon button (current month only) that opens the edit sheet. No long press (hard with TalkBack, and `FinoteListRow` has no long-click).
   - Every amount is its own `MoneyText`, laid out in a `Row`/`FlowRow`, never formatted into a sentence, so step 28 can mask it and text at 200% wraps instead of clipping.
6. **Edit sheet** (create and edit), in its own file under the feature's `component/`:
   - Category: total, or a shown top-level Expense category without a budget this month. Locked when editing.
   - Limit: numeric `FinoteTextField`, > 0, capped at the cash entry maximum (13 digits, `AmountExpression`).
   - Rollover switch.
   - Threshold: chips 50 / 70 / 80 / 90, default 80.
   - Delete, behind a `FinoteDialog` confirmation.
7. **N7, app notifications**: a notification channel "Ngân sách", `POST_NOTIFICATIONS` in the manifest, and the runtime request on Android 13+ when the user saves the first budget (asked once; the flag is kept in prefs excluded from backup, like other app prefs). Denied → budgets still work; the budget screen shows a banner when budget alerts are off (see "Alerts"). Its button asks again when Android still shows the dialog (33+ and `shouldShowRequestPermissionRationale` is true; the first ask already happened at the first save), and otherwise opens `Settings.ACTION_APP_NOTIFICATION_SETTINGS`. The banner re-checks on resume, so it hides once the user turns alerts on. Step 24 reuses this.
8. **Debt N-28** closed: deleting a category moves or drops its budgets, and the delete dialog counts them (see "Category delete").

## Non-goals

- Weekly, yearly or custom-range budgets; budgets for Income categories; budgets per account. The user set these aside on 2026-11-27.
- Budgets on subcategories (my call: top-level only keeps the budget equal to an Overview bucket and lets `ReportList` open as is; can be added later).
- Editing past months, and future months.
- Budget on the Overview screen. The dashboard design said the pace gauge would give way to a budget bar here. My call: Overview stays as it is in this step; update the note in `2026-11-25-dashboard-design.md`.
- Amounts in the notification text.
- A trash or undo for deleted budgets.

## Anti-goals

- "Spent" on a budget differs from the matching number on Overview, or from the `ReportList` it opens, for the same month.
- The same alert fires twice in a month for one budget.
- An alert fires right after the user saves a budget.
- A new month silently loses the user's budgets, or brings back one the user deleted.
- A notification on the lock screen shows money.

## Approaches (for repeating every month)

- **A. One set of rows per month, copied forward lazily (chosen by the user).** Matches spec 3.5 (`monthYear`, unique with `categoryId`). The first read of a month that was never set up copies the latest set-up month. Past months are read-only, so "an edit applies from now on" holds with no cascade. `lastAlertSentThreshold` lives on the month's row, so it starts empty each month.
- B. One row "effective from month X", read as the latest row at or before the month. No copying, but deleting needs a tombstone row, the alert state needs another table, and it drifts from the spec. Rejected.
- C. A WorkManager job copies rows at the start of each month. Misses months when the phone is off or the app is not opened; one more worker. Rejected.

## Rules

### Spent

Spent comes from `ReportCalculator.monthReport` (N6), no new rule:
- Total budget: `MonthReport.expense`.
- Category budget: the `BucketTotal.amount` of `ReportFilter.ByCategory(id)`, which already folds children into the parent, subtracts refunds, and skips transfers, adjustments, excluded rows and ignored accounts. A bucket with no rows is 0.
- Spent can be negative (refunds).

### Available and rollover

- `available(M) = limitAmount(M) + carry(M)`.
- `carry(M)`: if the row of month M has `isRollover = true` and the same budget (same `categoryId`, or both total) has a row in M−1, then `carry(M) = available(M−1) − spent(M−1)`. Otherwise 0. The chain walks back while rows exist. Copy forward fills every month, so a month the user did not open does not break the chain.
- Computed when read, not stored (my call): auto-captured rows often get their category fixed later, and a stored carry would go stale. So the table has no `carryOverAmount` column. This differs from spec 3.5 on purpose.
- All sums use exact arithmetic (`Math.addExact`), like `ReportCalculator`. The limit cap keeps a long chain far from overflow.

### Percent and states

Compare with integers, never with a rounded percent:
- over: `available <= 0`, or `spent >= available`. The 100% alert uses the same test, except that it also needs `spent > 0` (no alert for a month whose carry alone ate the budget).
- warning: not over, and `spent * 100 >= available * alertThreshold`.
- normal: otherwise.
- Shown percent: `floor(spent * 100 / available)`, clamped at 0; hidden when `available <= 0`. Flooring means 79.6% shows "79%" and is still normal, and 100.1% shows "100%" and is over, so the number and the state never disagree.
- Wording (revised after the second outside review): `available <= 0` → "Hết", whatever spent is, and the card shows no "Còn"/"Vượt" amount. Else `spent > available` → "Vượt X" with X = `spent − available`; `spent == available` → "Hết". Warning → "Sắp hết". Normal → no word. The 100% alert still follows the test above (`spent > 0`).
- Percent is computed in `Long` and clamped to `0..Int.MAX_VALUE` before it becomes an `Int`.
- Colors: new token `warning` (light and dark, added to `FinoteColors`, `ContrastTest` and `docs/design-system.md`); over uses the existing error color. No `Color(...)` outside `ui/common/theme/`.

### Copy forward

- Table `budget_months(monthYear)` marks months that were set up. A month is set up when it is copied into, or when the user saves its first budget.
- Trigger: whenever the current month's budgets are read (the budget screen and the alert check) and the current month is not in `budget_months`.
- Source: the latest month in `budget_months` before it. Copy its rows month by month up to the current month, marking each month: same `categoryId`, `limitAmount`, `isRollover`, `alertThreshold`; new `id`; `lastAlertSentThreshold = null`. No earlier month → nothing to copy, and nothing is marked until the user saves a budget.
- Why a marker table: if the user deletes every budget in October, November must copy nothing, not reach back to September.
- One write transaction; check the marker again inside it, so two callers cannot copy twice (the unique index is the last guard).

### Alerts

- Trigger (my call, revised while planning): Room's `InvalidationTracker` on the tables `transactions`, `budgets`, `categories`, `accounts`, conflated so a burst of writes runs one check. It sees every commit, whichever path wrote it, and fires only after the outermost commit. A hook in `RoomTransactionRunner` was rejected: today every ledger write does go through the runner (notification booking runs its `storageWrite` inside `SettlePendingLogsUseCase`'s `tx.write`), but the hook would rely on that staying true, and a nested `write` would have to know it is not the outermost one.
- Start (my call): the watcher starts when the real database is first built, from `DatabaseModule.provideDatabase`, through a `Provider` and a launch on an app-scoped `CoroutineScope`, so the launch resolves the watcher's dependencies after the database singleton exists. Nothing opens the database at app start. Robolectric tests start the real `FinoteApplication`, and an eager watcher there would open SQLCipher in every test; tests that build an in-memory database never go through the module.
- The check writes only when something changes (a copy, a new mark), so its own invalidation runs one more check that finds nothing to do.
- Each check reads `YearMonth.now(clock)` anew, so a process that lives past midnight at month end checks the new month.
- The check returns at once when the `budgets` table is empty (one `EXISTS` query).
- For each budget of the current month: if the 100% test holds and the mark is below 100, set the mark to 100 and notify 100. Else if the warning test holds and the mark is null or below `alertThreshold`, set the mark to `alertThreshold` and notify the threshold. Decide and write inside one write transaction that re-reads the row, then notify after the commit, so two checks cannot both notify. The re-read row must equal the one the status was computed from (limit, threshold, rollover, category, mark); a row that changed or is gone is skipped, so a limit raised a moment ago never gets a stale alert. The next change runs a fresh check.
- Save reads the month's spending just before its write transaction, not inside it. A payment booked in between can leave the stored mark one step low, which at worst sends one alert right after the save. Accepted.
- Save copies the month forward first (inside its write), so the first save of a new month never marks the month set up before the copy and loses last month's budgets.
- Create and every edit (limit, threshold, rollover switch) set the mark to the highest mark the new values already reach (100, else `alertThreshold`, else null). So saving never fires an alert, a mark already reached never fires twice, and a mark the edit falls below can fire again later.
- Spending that goes down (an edit, a refund) does not lower the mark.
- No alert for past months.
- Text: the category name (localized for a system category through its `nameKey`, as the UI shows it) or "Tổng ngân sách", and the mark, e.g. "Ăn uống đã dùng 80% ngân sách tháng". No amount. Lock screen visibility: private. One notification id per budget (derived from the month and category), so alerts do not replace each other.
- Placement (my call, revised): the domain owns a port (`BudgetNotifier`). Its Android adapter lives in `ui/common/notification/` (a feature package may hold only Contract/ViewModel/Screen files, and `ui/common` already holds `NotificationListenerPermission.kt`), bound in `di`. The intent extra key lives there too, because `ui/common` may not import `ui/navigation`. It uses the app's chosen locale (`AppCompatDelegate.getApplicationLocales()`), because on API 26–32 the application context does not apply it, and `systemNameRes(nameKey)` for system names.
- It posts only when notifications are on and, on 33+, `POST_NOTIFICATIONS` is granted (`checkSelfPermission`); Lint's `MissingPermission` needs the explicit check and `@SuppressLint` is banned.
- Tap opens the app at the Budget tab: the launch intent from `packageManager.getLaunchIntentForPackage` (fallback: `ACTION_MAIN` + `CATEGORY_LAUNCHER` + the package) plus an extra. `MainActivity` (`launchMode="singleTop"`) reads it in `onCreate` only when `savedInstanceState == null`, and in `onNewIntent`, then removes it, so a rotation does not jump back. The nav host ignores it while the onboarding `Setup` is the root; otherwise the stack becomes `backStackFor(Budget)` unless `Budget` is already on top, so a sheet or report list above it is closed. Nothing outside the root package names `MainActivity` (Konsist).
- A user who turns off only the "Ngân sách" channel in system settings counts as notifications off (channel importance `NONE`), for the banner and for posting.
- Never log budget amounts or spending (privacy rule). `toString()` of the entity, domain model, UI state and draft hides amounts, like `MonthReport` and `Transaction`.

### Delete

Budget rows are plans, not money records: they are hard-deleted (decided by the user on 2026-11-27, because soft-deleted rows only got in the way of the unique index, of moving budgets between categories and of step 26's purge). Add this exception next to `account_identifiers` and `category_keyword_rules` in `CLAUDE.md` and `docs/roadmap.md` section 2. `budget_months` rows are never deleted by the user.

### Category delete (N-28)

- `DeleteImpact` and `CountCategoryTransactionsUseCase` also count budgets (all months) of the categories being deleted. A category with budgets but no transactions or rules opens the move sheet like one with transactions, and the sheet shows the budget count.
- `CategoryRepository.deleteAndMove` (the one place that knows which tables point at a category) also handles `budgets`, all months:
  - Target null → delete the budgets of the deleted categories.
  - Target set → the budget moves to the target's top-level category (the target itself, or its parent; the repository resolves it, because transactions and rules keep the exact target). Per month: if that category already has a budget, the moved one is deleted (the target's own budget wins, like keyword rules drop duplicates); else its `categoryId` changes.
  - A deleted child category has no budget (top-level only), so nothing moves for it.
- A top-level category with budgets that the user turns into a child (`CategoryRules.checkUpdate` allows it for a non-system category without children) is handled the same way, in the same write as `CategoryRepository.update`: its budgets of all months move to the new parent, and a budget the parent already has that month wins. Without this the budget would read 0 forever and keep copying forward.
- Since a deleted category's budgets are gone before step 26 purges the category, the `RESTRICT` foreign key never blocks the purge.

### Hidden categories

- The edit sheet's category list shows only shown categories (`CategoryVisibility.shown()`).
- A budget whose category is hidden later stays on the screen and keeps alerting, because its spending still counts on Overview.

## Data

- Table `budgets`: `id`, `categoryId` (nullable, FK to `categories`, `RESTRICT` like the other tables), `limitAmount` (Long VND, > 0), `monthYear` (`yyyy-MM`), `isRollover`, `alertThreshold` (Int, one of the chip values), `lastAlertSentThreshold` (Int?), `createdAt`, `updatedAt`. Unique index `(monthYear, categoryId)`; index on `categoryId`. Not in spec 3.5's list as is: no `carryOverAmount` (computed), no `deletedAt` (hard delete).
- SQLite allows several `NULL` values in a unique index, so the index does not stop two total budgets in one month. The write transaction checks it.
- Table `budget_months`: `monthYear` primary key.
- Migration 13 → 14 with a migration test (`MigrationPolicyTest` pattern). No backfill.

## Constraints

- Money is `Long` VND; overflow throws through `Math.addExact`, like the report.
- Every write goes through `TransactionRunner.write`; copy forward, alert marks and category moves are atomic.
- Layers, naming and limits per `docs/architecture.md` (domain `budget` package beside `report`; Konsist rules apply). UI per `docs/design-system.md` (house components only; no Material `Slider` or FAB).
- Strings in `vi`, `en`, `zh-rCN`, `ja`.
- `minSdk` 26: the runtime permission only on 33+; the channel on 26+.

## Left to the builder

- Package and class names; how the post-commit hook avoids work when there are no budgets.
- Whether the copy runs in one shared use case that the screen and the check both call (preferred).
- How many months of transactions the carry chain loads. A `shortcut:` comment is allowed if it loads from the first budget month; N8 (step 30) revisits performance.
- Exact layout, banner wording and the wording of the delete sheet line, within the design system.
- Doc sync in the same branch: `CLAUDE.md` and roadmap (hard-delete exception), roadmap section 6 (close N-28), the dashboard design note.
