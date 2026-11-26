# Dashboard and the report core (roadmap step 17): design

Date: 2026-11-25. Status: implemented on feature/dashboard (2026-11-26). Revised the same day after a builder check and an outside review (Gemini) checked it against the code.
Branch (planned): `feature/dashboard` from `dev`. Source: `docs/roadmap.md` section 4 (N6), section 5 step 17; debt T3 (fixed here). Spec 4 "Dashboard & Báo cáo dòng tiền tháng" (spec L304–305), spec 3 `excludeFromAnalytics` (L114, L280), spec 3.5 `Budget` (L136–142), acceptance L413. Related: step 18 (budget, reuses N6 and replaces the pace gauge), step 19 (net worth, reuses the chart wrapper), step 22 (refund), step 28 (balance masking through `MoneyText`), debt T2 (future-dated rows).

## Why

The `Overview` tab is the start screen and still shows `ComingSoonScreen`. The user wants to open the app and see at once how much came in and went out this month, and whether spending is running over ("có lố không").

There is no budget yet (step 18), so "over" means: spending from day 1 to today, compared with the same days of last month. The user picked this (option 1) over a 3-month average (too little data: the app has been used since November) and over a self-set monthly cap (overlaps step 18).

Research of other apps (2026-11-25): apps that capture transactions automatically, such as Copilot, lead with spending pace against a reference line; manual-entry apps (Money Lover, MISA) lead with total balance. Almost all compare this month with the same period last month. Finote captures automatically, so it follows the pace pattern.

Today the snapshot's `totalIncome`/`totalExpense` include adjustments, fees and refunds (debt T3). So the report must read transactions, not snapshots (roadmap rule: "Snapshot only serves balance and net worth").

### What the transaction list does today (checked in code)

`netOf` in `TransactionDay.kt` (the day header net) already skips `isExcludedFromAnalytics` rows, skips `TRANSFER` and `ADJUSTMENT`, adds `REFUND`, and subtracts `feeAmount`. For the net, adding a refund as income equals subtracting it from expense. So the day nets already match the report net, except in three places:

1. With "All accounts", rows of an account the user ignored without trashing its old rows (`AccountRepositoryImpl.setIgnored`, `shouldTrashOldTransactions = false`) are shown and counted in the day net. The report skips them.
2. A transfer's fee (`TransactionRules`: only `TRANSFER` may have `feeAmount > 0`) is subtracted from the day net but never shown on the row, so adding rows by hand comes out short by the fees.
3. An excluded row (`isExcludedFromAnalytics`, the "paid for someone" case) looks like a normal expense; only a small pending/repaid label tells it apart, so adding rows by hand counts it.

The user chose to fix all three in this step (option 2), so that the sum of a month's day nets equals the Overview net.

## Goals

1. `domain/report` (N6): one pure calculator that turns transactions, accounts and categories into a month report. It is the only place with the income/expense rules. Steps 18 and 22 reuse it.
2. The `Overview` screen, top to bottom:
   - Month header with ‹ › to move between months (no future months).
   - **Spent this month**, large. Below it: income, and net = income − expense (the spec's "Số dư ròng").
   - **Pace vs last month**: "+12% so với cùng kỳ tháng trước" and a small line chart of cumulative spending per day, this month over last month.
   - **Spending by bucket**: buckets are top-level categories, "Chưa phân loại" and "Phí chuyển khoản". The top 5 by amount show; a "Xem tất cả (n)" row expands the rest in place. Every row shows amount and share, and every row is tappable.
   - **Top merchants**: top 5 by spending, from `merchantName`, tappable. The section is hidden when no row in the month has a merchant name.
3. **Report list**: a tap opens a separate route (not a mode of `TransactionList`), with a back button and no tab bar. Its rows are exactly the rows the calculator counted for that bucket, and its total line comes from the calculator, so it always equals the number tapped. Filters: one top-level category (a parent includes its children), uncategorized, transfer fees (rows with `feeAmount > 0`; each row shows the fee as its amount), one merchant name. All for one month.
4. Transaction list fixes (the three places above):
   - `ObserveTransactionListUseCase` drops rows whose source account is ignored when "All accounts" is shown (same rule as the report), so `netOf` needs no change. The "Chờ trả" chip keeps them (a debt stays visible). An ignored account is already left out of the account chips.
   - A transfer row with a fee shows the fee as a second amount line ("Phí" + `MoneyText`), not in the subtitle, so balance masking in step 28 still covers it. Seen from the destination account, no fee (same as `netOf`).
   - An excluded row shows a short "Ngoài báo cáo" label in its subtitle and is drawn at the muted alpha; design per `docs/design-system.md`.
5. Debt T3 is closed: no report number comes from snapshots.

## Non-goals

- Balance masking button (`***`): step 28, through `MoneyText`.
- Budget, "left to spend", the ideal-pace line: step 18. The pace gauge here is replaced by the budget bar then.
- Net worth over years, and a total balance line on Overview: step 19. Whether it skips `excludeFromNetWorth` and subtracts card debt is decided there; a number here now could disagree later.
- Refund creation and screens: step 22. No code path creates a `REFUND` today; the rules below only keep room for it.
- Tapping the Spent or Income totals: the buckets already break Spent down. Income has no breakdown in this step.
- Account filter on the dashboard: all accounts together.
- Custom date ranges, week view, year view, income breakdown by category, home-screen widget.

## Anti-goals

- A number on Overview differs from the total of the report list it opens.
- The sum of a month's day nets in the transaction list (all accounts) differs from the Overview net for that month.
- The bucket rows, all expanded, do not add up to Spent.
- A percentage against an invented baseline.

## Report rules (the calculator)

Input: transactions with `deletedAt == null` and `timestamp` in the half-open range `[start of month, start of next month)` in the device zone (via the injected `Clock`, as in `BalanceCalculator`), plus accounts and categories. The calculator does the filtering itself; the ViewModel does not pre-filter.

| Row | Income | Expense | Bucket |
|---|---|---|---|
| `INCOME` | `amount` | — | — |
| `EXPENSE` | — | `amount` | top-level of its `categoryId`; "Chưa phân loại" when null or the category is missing |
| `REFUND` | — | `−amount` | same as `EXPENSE` (lowers that bucket) |
| `TRANSFER` | — | `feeAmount` only | "Phí chuyển khoản" |
| `ADJUSTMENT` | — | — | — |
| `isExcludedFromAnalytics = true` | skipped entirely, fee included | | |
| source account `isIgnored` | skipped entirely | | |

- Archived and deleted accounts still count: their history is real spending.
- Hidden categories still count.
- The calculator keeps totals per `categoryId` (leaf level). The top-level grouping is a projection on that, so step 18 can read a child category's spending.
- A bucket can be zero or negative through refunds. It still shows (not hidden), so the rows add up to Spent. Share is not shown when Spent ≤ 0.
- Spent < 0 (refunds above spending): the hero shows `|Spent|` under the label "Hoàn nhiều hơn chi" in place of "Đã chi", never formatted as a plain expense; no shares.
- All sums use `Math.addExact`/`subtractExact`. Percentages use integer math (Konsist bans `Double`/`Float` on money names).
- Merchant ranking: `EXPENSE` and `REFUND` rows with a non-blank `merchantName`, grouped by exact name; merchants with a total ≤ 0 are not in the top list.
- Report models override `toString()` without amounts, merchant names or notes (privacy rule).
- `netOf` already skips excluded rows; ignored-account rows are dropped before it, in `ObserveTransactionListUseCase`. A test checks that, for a fixture month, the sum of day nets equals the calculator's net.
- Signs: on the hero card every amount is neutral (red/green text has no contrast on the gradient); words carry the meaning. Elsewhere a negative amount (a net refund) shows as `+|amount|` in the income colour, a positive one as an expense.
- Share and bar length are measured against the sum of positive buckets, so they never pass 100%. A negative bucket has no share.

### Pace

- "Has data last month" means at least one row last month that the calculator counts as spending (`EXPENSE`, `REFUND`, or a transfer fee). Without one: no percentage and no last-month line.
- Current month: spending of days 1..today versus days 1..min(today, length of last month) of last month. Today comes from the `Clock` on each emission and on each screen resume; a new month with the app in front and no new row moves on at the next resume (accepted, no timer).
- A past month: whole month versus the whole month before it.
- Percentage = (this − last) / last, rounded to a whole percent. Rounded 0 → "0%", no sign, neutral colour. Above 0 red with "+", below 0 green with "−". Last ≤ 0 → no percentage.
- Chart: cumulative spending per day. This month's line stops at today. Last month's line stops at its last day when it is shorter.
- Future-dated rows in the current month (allowed up to 366 days ahead, debt T2): Spent and the buckets count the whole month, so they match the report list; the chart and the percentage stop at today. A small note under the chart says so when such rows exist.

### Empty states

- No transactions at all: the screen's empty state.
- A month with income but no spending, or no rows in the shown month while last month has some: the blocks still show with zeros; the bucket list shows a short empty line and the merchant section hides.

## Approaches considered

| | Approach | Trade-off | Picked |
|---|---|---|---|
| A | DAO returns a `Flow` of live rows in [start of previous month, start of the month after the shown one); a pure Kotlin calculator in `domain/report` builds the report | Rules in one place, unit-testable without Room, reused by steps 18 and 22. Loads two months of rows (hundreds), fine for this screen | **Yes** (user, 2026-11-25) |
| B | SQL aggregation per block (`GROUP BY`, `SUM(CASE …)`) | Fastest at scale, but the rules are copied into SQL per query; refund and split later change many queries; harder to test | No |
| C | Derived per-category monthly table kept in step on the write path | Fast reads, but one more table to keep consistent inside transactions, plus a migration, to save summing a few hundred rows; the roadmap already rules out report columns on snapshots | No |

## Decided

- Approach A (user).
- Comparison baseline: same period last month (user).
- Fix the three list mismatches in this step (user).
- Layout order: spending, income, net → pace → buckets → merchants (user).
- No synthetic "Khác" bucket: `sys_other` is already named "Khác", and a bucket that cannot be tapped hides where money went. "Xem tất cả (n)" expands instead. (Claude's call, after review.)
- The fee bucket is named "Phí chuyển khoản" (only transfers carry fees), distinct from the system category `sys_fees` "Phí & lệ phí". (Claude's call.)
- The report list is its own route. `TransactionListViewModel` already has 12 dependencies and `TransactionListScreen.kt` is near the Konsist file limit; the tab bar logic keys on top-level tabs. (Claude's call, after review.)
- Rows on archived or deleted accounts open read-only in the report list, as in the transaction list (`isOpenable`). An uncategorized row there cannot be categorized from this screen.
- Vico for the cumulative line, wrapped in a component under `ui/common/component/` (step 19 reuses it), added through `gradle/libs.versions.toml`. The first plan task is a spike: Vico renders in the screenshot test (layoutlib) with animation off, on the current Compose BOM. If it fails, fall back to a Compose `Canvas` line inside the same wrapper. (Claude's call.)
- The top block shows Spent, Income and Net. `BalanceHeroCard` today has a fixed income/expense chip pair and a neutral main number; the builder changes it or adds a card for this layout. If it is no longer used, close debt N-17's part about it.
- No migration: `transactions` already has an index on `timestamp`.

## Left to the builder

- Names, file layout inside `domain/report`, `ui/overview`, the report list route, following `docs/architecture.md` and MVI like the other screens.
- How the ViewModel combines the transactions flow with categories and accounts.
- Loading rows and exact visuals from `docs/design-system.md`, including the "not in reports" mark and the chart line colours.
- Where the "Xem tất cả" expanded state lives (screen state, not saved across launches).

## Testing

- Calculator unit tests: every row type in the table, transfer fee bucket, excluded rows (fee included), ignored account, archived account counted, child → parent projection and leaf totals, missing category → uncategorized, refund lowering a bucket below zero, Spent ≤ 0, merchant ranking, empty last month, last month with income only, short previous month (31 vs 30, March vs February), past month, future-dated rows, 0% rounding, overflow refusal, `toString()` hides money.
- Match tests: each bucket amount equals its report list total; bucket rows add up to Spent; the sum of day nets (`groupByDay`, all accounts) equals the calculator net for a fixture month.
- `netOf` tests for the ignored-account skip; existing `TransactionDayTest` stays green.
- ViewModel tests with Turbine: month switching, data change re-emits, day change after midnight.
- DAO range query test in Robolectric: first millisecond included, start of next month excluded, deleted rows excluded.
- Screenshot tests: Overview (data, partial empty, no data), report list, a transaction row with a fee and an excluded row.
