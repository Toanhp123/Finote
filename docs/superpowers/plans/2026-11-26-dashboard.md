# Dashboard and Report Core Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The Overview tab shows this month's spending, income, net, pace against the same days of last month, spending by category and by merchant. Every amount on it opens a report list whose total matches. The transaction list's day nets add up to the same net.

**Architecture:** A pure calculator in `domain/report/service` turns live transactions, ignored account ids and categories into a `MonthReport` or a filtered `ReportRows`. Both come from one internal entry list, so the totals always agree. Use cases feed it from a new DAO range query. Two new screens, `ui/overview` and `ui/report`, plus three fixes in `ui/transactions`.

**Tech Stack:** Kotlin, Room 3 (SQLCipher), Hilt, Compose M3, Navigation3, Vico (new), JUnit4 + Robolectric, Turbine, Konsist, AGP screenshot tests.

**Spec:** `docs/superpowers/specs/2026-11-25-dashboard-design.md`

## Global Constraints

- Branch `feature/dashboard` from `dev` (`docs/git-workflow.md`). Commit scopes only: `balance`, `db`, `ui`, `build`, or none (`docs:`). No AI attribution in commits. `coder` never commits; the main session commits each reviewed task.
- Money is `Long` VND. Every sum uses `Math.addExact` / `subtractExact`. No `Double`/`Float` on a name with a money word (`docs/architecture.md`). Percentages use `BigDecimal` with `RoundingMode.HALF_UP`.
- No snapshot is read for any report number (debt T3).
- No schema change: `transactions` already has `Index(["timestamp"])`. DB version stays 13.
- Every new model that holds an amount, a merchant name or a note overrides `toString()` without them. Nothing of them reaches Logcat.
- Every amount on screen goes through `MoneyText`. Dates are formatted only in `ui/common/format/`.
- Copy in `vi` (default), `en`, `ja`, `zh-rCN`. Fixed copy from the spec: "so với cùng kỳ tháng trước", "Hoàn nhiều hơn chi", "Xem tất cả (%1$d)", "Phí chuyển khoản", "Ngoài báo cáo"; "Chưa phân loại" reuses `transactions_uncategorized`.
- UI follows `docs/design-system.md` and the feature template in `docs/architecture.md`. The hero gradient stays only in `BalanceHeroCard`.
- Run Gradle through the `gradle-run` skill: `testDebugUnitTest` after each logic task; screenshot tasks run `updateDebugScreenshotTestDefaultTestSuite`, then open and look at the PNGs; `qualityGate` at the end.

## Review Focus

1. A row at 23:59:59.999 on the last day of a month and one at 00:00 on the first day of the next land in different months, in the device zone → tests in Tasks 2 and 4.
2. A row whose category was deleted, or a child whose parent is missing from the live categories, must not crash or vanish from the totals → test in Task 2.
3. A notification booked while Overview is open updates the numbers without leaving the screen → test in Task 7.
4. A report list whose rows were all moved away (re-categorized or deleted) shows an empty list with total 0 → test in Task 8.
5. The chosen month survives process death; at the start of a new month the current month moves on at the next resume or the next data change (not on a timer) → tests in Task 7.

---

### Task 1: Chart component and Vico spike

**Files:**
- Modify: `gradle/libs.versions.toml`, `app/build.gradle.kts`
- Create: `app/src/main/java/io/github/toanhp123/finote/ui/common/component/display/FinoteLineChart.kt`
- Modify: `app/src/screenshotTest/kotlin/io/github/toanhp123/finote/ui/common/component/DisplayScreenshots.kt`
- Modify: `docs/design-system.md` (component list)

**Interfaces:**
- Produces:
  - `enum class ChartLine { Primary, Muted }` (same file)
  - `data class ChartSeries(val points: List<Long>, val line: ChartLine)`: one point per day, index 0 is day 1.
  - `@Composable fun FinoteLineChart(series: List<ChartSeries>, contentDescription: String, modifier: Modifier = Modifier, dayCount: Int)`: x runs 1..`dayCount`; a shorter series stops where it ends. No axis labels with money (masking in step 28 goes through `MoneyText` only). Animation off.

- [ ] **Step 1: Create the branch** `feature/dashboard` from `dev`.
- [ ] **Step 2: Add Vico** `com.patrykandpatrick.vico:compose-m3`, latest stable on Maven Central, as `vico` in `libs.versions.toml`, and as `implementation(libs.vico.compose.m3)`.
- [ ] **Step 3: Implement `FinoteLineChart`** with Vico. Line colours come from theme tokens: `Primary` = `scheme.primary`, `Muted` = `scheme.onSurfaceVariant`. Chart height and stroke width are new tokens in `FinoteSizes.kt` (`chartHeight`, `chartStroke`): `DesignSystemKonsistTest` bans `.dp` outside `ui/common/theme/`. Add `@FinotePreviews` with two series (30 days and 18 days, `dayCount = 31`).
- [ ] **Step 4: Add `@PreviewTest` `FinoteLineChartScreenshot`** in `DisplayScreenshots.kt`, then run `updateDebugScreenshotTestDefaultTestSuite`.
  Expected: three PNGs with both lines drawn (not just "no crash": Vico's model producer updates in an effect, so a first frame can be blank). **Spike gate:** if layoutlib renders nothing or crashes, remove Vico from the catalog and draw the same API with a Compose `Canvas` path. The public signature does not change either way. Record which one won in the commit message.
- [ ] **Step 5: Open the PNGs and look.** Light, dark and font scale 2 all show two lines, the muted one shorter.
- [ ] **Step 6: Run `testDebugUnitTest`** (Konsist `DesignSystemKonsistTest`). Expected: PASS.
- [ ] **Step 7: Add the line** for `FinoteLineChart` to the `display/` list in `docs/design-system.md`, then commit: `feat(ui): add line chart component`.

### Task 2: Report model and calculator (totals, buckets, merchants, rows)

**Files:**
- Create: `app/src/main/java/io/github/toanhp123/finote/domain/report/model/ReportFilter.kt`
- Create: `app/src/main/java/io/github/toanhp123/finote/domain/report/model/MonthReport.kt`
- Create: `app/src/main/java/io/github/toanhp123/finote/domain/report/model/ReportInput.kt`
- Create: `app/src/main/java/io/github/toanhp123/finote/domain/report/service/ReportCalculator.kt`
- Test: `app/src/test/java/io/github/toanhp123/finote/domain/report/service/ReportCalculatorTest.kt`

**Interfaces:**
- Consumes: `Transaction`, `TransactionType`, `Category` (`id`, `parentId`).
- Produces:
  - `sealed interface ReportFilter { data class ByCategory(val categoryId: String); data object Uncategorized; data object TransferFees; data class ByMerchant(val name: String) }`. `ByCategory.categoryId` is a top-level id. `ByMerchant.toString()` hides the name. (Not `Category`, to avoid a clash with the domain model.)
  - `data class ReportInput(val transactions: List<Transaction>, val ignoredAccountIds: Set<String>, val categories: List<Category>, val zone: ZoneId)`. `toString()` shows only the counts.
  - `data class BucketTotal(val filter: ReportFilter, val amount: Long)`. `filter` is `ByCategory`, `Uncategorized` or `TransferFees`.
  - `data class MerchantTotal(val name: String, val amount: Long)`
  - `data class Pace(val percent: Int?, val thisMonth: List<Long>, val lastMonth: List<Long>)`. Cumulative spending per day. Filled in Task 3; this task returns `Pace(null, emptyList(), emptyList())`.
  - `data class MonthReport(val month: YearMonth, val income: Long, val expense: Long, val net: Long, val buckets: List<BucketTotal>, val merchants: List<MerchantTotal>, val expenseByCategory: Map<String, Long>, val pace: Pace, val hasFutureRows: Boolean)`. `expenseByCategory` is keyed by the leaf `categoryId` (step 18 reads it). Uncategorized rows are not in it.
  - `data class ReportRow(val transaction: Transaction, val amount: Long)`
  - `data class ReportRows(val rows: List<ReportRow>, val total: Long)`
  - `object ReportCalculator { fun monthReport(input: ReportInput, month: YearMonth, today: LocalDate): MonthReport; fun rows(input: ReportInput, month: YearMonth, filter: ReportFilter): ReportRows }`

**Rules** (spec "Report rules"). Both public functions build on one private entry list per month. A row is kept when `deletedAt == null`, its local date (in `zone`) is in `month`, `!isExcludedFromAnalytics`, and `accountId !in ignoredAccountIds`. From each kept row:
- `INCOME` → income `+amount`.
- `EXPENSE` → expense `+amount`, to its bucket.
- `REFUND` → expense `−amount`, to its bucket.
- `TRANSFER` and `ADJUSTMENT` add no principal.
- Any row with `feeAmount > 0` → expense `+feeAmount`, to `TransferFees`. Only transfers carry fees today; the rule matches `netOf`.

Bucket of an `EXPENSE`/`REFUND`: look `categoryId` up in `categories`. Missing (or null) → `Uncategorized`. Its `parentId` is present in `categories` → `ByCategory(parentId)`. Otherwise → `ByCategory(categoryId)`.

`buckets` holds every bucket with at least one entry, zero and negative included. Sort by amount descending; on ties, categories by id, then `Uncategorized`, then `TransferFees`.

`merchants` takes `EXPENSE` and `REFUND` rows with a non-blank `merchantName`, grouped by exact name. Keep the top 5 with amount > 0, sorted by amount descending, then by name. The 5 is a `private const val` (detekt `MagicNumber`).

`expenseByCategory` gets only rows whose `categoryId` is found in `categories`; a missing one goes to `Uncategorized` alone.

`rows(filter)` returns the entries of that bucket, or for `ByMerchant` the merchant's rows. Each row's `amount` is its share: the fee for `TransferFees`, `±amount` otherwise. Order: `timestamp` desc, `createdAt` desc, `id` desc. `total` is the sum.

`hasFutureRows` is true when `month == YearMonth.from(today)` and a kept row's date is after `today`.

- [ ] **Step 1: Write failing tests** in `ReportCalculatorTest`, one `@Test` each:
  - `income and expense add up and net is income minus expense`: INCOME 1_000_000, EXPENSE 300_000 gives income 1_000_000, expense 300_000, net 700_000.
  - `transfer adds only its fee to the fee bucket`: TRANSFER 2_000_000 with fee 5_000 gives expense 5_000, buckets `[BucketTotal(TransferFees, 5_000)]`.
  - `adjustment adds nothing`.
  - `refund lowers its bucket and the expense, never income`: EXPENSE 200_000 and REFUND 50_000, same category, give income 0, expense 150_000, bucket 150_000.
  - `excluded row is skipped with its fee`.
  - `row of an ignored account is skipped, archived account still counts`: the archived account id is not in `ignoredAccountIds`.
  - `child rolls into its parent and expenseByCategory keeps the leaf`: child `c1` (parent `p`) 100_000 and `p` 50_000 give bucket `ByCategory("p")` 150_000; `expenseByCategory == mapOf("c1" to 100_000, "p" to 50_000)`.
  - `missing category is uncategorized and stays out of expenseByCategory, child of a missing parent is its own bucket`.
  - `negative bucket still shows and buckets add up to expense`: REFUND 80_000 alone in category `b`, EXPENSE 100_000 in `a` give buckets `[a 100_000, b −80_000]`, expense 20_000.
  - `merchant top five skips blank names and totals at or below zero`.
  - `rows of a bucket total to the bucket amount` for every bucket of a mixed fixture: `rows(input, m, b.filter).total == b.amount`.
  - `fee rows carry the fee as amount`.
  - `month boundary uses the zone`: zone `Asia/Ho_Chi_Minh`; rows at `2026-10-31T23:59:59.999+07:00` and `2026-11-01T00:00+07:00`; only the second is in November.
  - `future row in the current month sets hasFutureRows` with today 2026-11-25 and a row on 2026-11-28; the past month gives false.
  - `overflow throws ArithmeticException`: two EXPENSE rows of `Long.MAX_VALUE / 2 + 1`.
  - `toString hides money and names` for `MonthReport`, `MerchantTotal`, `ReportFilter.ByMerchant`, `ReportRow`: no digits of the amount and no merchant name in the string.
- [ ] **Step 2: Run** `testDebugUnitTest --tests "*.ReportCalculatorTest"`. Expected: FAIL (unresolved references).
- [ ] **Step 3: Implement** the model files and `ReportCalculator` as above.
- [ ] **Step 4: Run** the same tests, then the whole `testDebugUnitTest` (Konsist layer and naming rules). Expected: PASS.
- [ ] **Step 5: Commit** `feat(balance): add month report calculator`.

### Task 3: Pace

**Files:**
- Modify: `app/src/main/java/io/github/toanhp123/finote/domain/report/service/ReportCalculator.kt`
- Test: `app/src/test/java/io/github/toanhp123/finote/domain/report/service/ReportPaceTest.kt`

**Interfaces:**
- Consumes: Task 2 entries and `Pace`.
- Produces: `MonthReport.pace` filled in.

**Rules** (spec "Pace"):
- The current month is `YearMonth.from(today)`. A month before it is past.
- `cutoff` is `today.dayOfMonth` for the current month, the month's length for a past one.
- `thisMonth` holds days 1..`cutoff`, cumulative spending. Spending is the entries' expense side, fees included.
- `lastMonth` holds days 1..min(`cutoff`, length of the previous month), or the whole previous month when `month` is past. It is empty when the previous month has no spending entry (no `EXPENSE`, `REFUND` or fee).
- `percent` is `(this − last) × 100 / last`, `HALF_UP` to an `Int`. `this` and `last` are the last points of each list. It is null when `lastMonth` is empty or `last <= 0`.

- [ ] **Step 1: Write failing tests** in `ReportPaceTest`:
  - `current month compares the same days`: today 2026-11-10; Oct 5 50_000, Oct 20 900_000, Nov 3 56_000 give `thisMonth.size == 10`, `lastMonth.size == 10`, `lastMonth.last() == 50_000`, `percent == 12`.
  - `previous month shorter stops at its last day`: today 2026-03-31 gives `lastMonth.size == 28`.
  - `past month compares whole months`: month 2026-10, today 2026-11-25 gives `thisMonth.size == 31`, `lastMonth.size == 30`.
  - `last month with income only gives no baseline`: `lastMonth` empty, `percent == null`.
  - `rounding`: 100_400 vs 100_000 gives 0; 100_600 gives 1; 99_500 gives −1.
  - `last total at or below zero gives no percent` (refund larger than spending last month).
  - `future row is in the totals but not in the pace`: today 2026-11-25, row on 2026-11-28. Expense includes it; `thisMonth.last()` does not.
- [ ] **Step 2: Run** `testDebugUnitTest --tests "*.ReportPaceTest"`. Expected: FAIL.
- [ ] **Step 3: Implement** pace inside `monthReport`.
- [ ] **Step 4: Run** `testDebugUnitTest`. Expected: PASS, `ReportCalculatorTest` included.
- [ ] **Step 5: Commit** `feat(balance): add spending pace against last month`.

### Task 4: Range query and report use cases

**Files:**
- Modify: `app/src/main/java/io/github/toanhp123/finote/data/transaction/local/dao/TransactionDao.kt`
- Modify: `app/src/main/java/io/github/toanhp123/finote/domain/transaction/repository/TransactionRepository.kt`
- Modify: `app/src/main/java/io/github/toanhp123/finote/data/transaction/repository/TransactionRepositoryImpl.kt`
- Modify: `app/src/test/java/io/github/toanhp123/finote/domain/transaction/usecase/FakeTransactionRepository.kt`
- Create: `app/src/main/java/io/github/toanhp123/finote/domain/report/usecase/ObserveReportInputUseCase.kt`
- Create: `app/src/main/java/io/github/toanhp123/finote/domain/report/usecase/ObserveMonthReportUseCase.kt`
- Create: `app/src/main/java/io/github/toanhp123/finote/domain/report/usecase/ObserveReportRowsUseCase.kt`
- Test: `app/src/test/java/io/github/toanhp123/finote/data/transaction/local/dao/TransactionDaoTest.kt` (extend)
- Test: `app/src/test/java/io/github/toanhp123/finote/domain/report/usecase/ObserveMonthReportUseCaseTest.kt`

**Interfaces:**
- Consumes: `ReportCalculator`, `AccountRepository.observeAccounts()`, `CategoryRepository.observeCategories()`, `Clock`.
- Produces:
  - DAO: `fun observeBetween(startInclusive: Long, endExclusive: Long): Flow<List<TransactionEntity>>`. Live rows with `timestamp >= start AND timestamp < end`, ordered like `observeTransactions`.
  - Repository: `fun observeTransactionsBetween(startInclusive: Long, endExclusive: Long): Flow<List<Transaction>>`, with `mapStorageErrors` like `observeTransactions`.
  - `class ObserveReportInputUseCase @Inject constructor(transactions: TransactionRepository, accounts: AccountRepository, categories: CategoryRepository, clock: Clock) { operator fun invoke(from: YearMonth, toInclusive: YearMonth): Flow<ReportInput> }`. The query range is `from.atDay(1)` to `toInclusive.plusMonths(1).atDay(1)`, at start of day in `clock.zone` read once at `invoke`. `ignoredAccountIds` = ids of accounts with `isIgnored`. `ReportInput.zone` is `clock.zone` read on each emission (the calculator re-filters by month, so a zone change between the two is safe).
  - `class ObserveMonthReportUseCase @Inject constructor(input: ObserveReportInputUseCase, clock: Clock) { operator fun invoke(month: YearMonth): Flow<MonthReport> }`. Loads `month − 1 .. month`. `today = LocalDate.now(clock)` on each emission.
  - `class ObserveReportRowsUseCase @Inject constructor(input: ObserveReportInputUseCase) { operator fun invoke(month: YearMonth, filter: ReportFilter): Flow<ReportRows> }`
  - `fun observeHasLive(): Flow<Boolean>` on the DAO (`SELECT EXISTS(SELECT 1 FROM transactions WHERE deletedAt IS NULL)`), `fun observeHasTransactions(): Flow<Boolean>` on the repository, and `class ObserveHasTransactionsUseCase` in `domain/report/usecase`.

- [ ] **Step 1: Write failing DAO tests** in `TransactionDaoTest`:
  - `observeBetween includes start and excludes end`: rows at `start`, `end − 1` and `end` give the first two.
  - `observeBetween skips deleted rows`.
  - `observeHasLive is false for only deleted rows and true after an insert`.
- [ ] **Step 2: Write failing use case tests** in `ObserveMonthReportUseCaseTest`, with fakes and a fixed `Clock` in `Asia/Ho_Chi_Minh`:
  - `loads last month and this month`: an October row reaches `pace.lastMonth`.
  - `ignored account ids come from accounts`.
  - `a new row re-emits` (Turbine): `awaitItem()` twice, expense grows.
- [ ] **Step 3: Run** both test classes. Expected: FAIL.
- [ ] **Step 4: Implement** the DAO queries, the repository methods, the fake, and the four use cases.
- [ ] **Step 5: Run** `testDebugUnitTest`. Expected: PASS.
- [ ] **Step 6: Commit** `feat(db): observe transactions by month for reports`.

### Task 5: Ledger list matches the report

**Files:**
- Modify: `app/src/main/java/io/github/toanhp123/finote/domain/transaction/usecase/ObserveTransactionListUseCase.kt`
- Modify: `app/src/test/java/io/github/toanhp123/finote/domain/transaction/usecase/ObserveTransactionListUseCaseTest.kt`
- Modify: `app/src/test/java/io/github/toanhp123/finote/ui/transactions/TransactionListViewModelTest.kt` (constructor only)
- Test: `app/src/test/java/io/github/toanhp123/finote/domain/report/usecase/LedgerReportMatchTest.kt`

**Interfaces:**
- Consumes: `AccountRepository.observeAccounts()`, `ObserveMonthReportUseCase` (test only).
- Produces: `ObserveTransactionListUseCase(repository, accounts: AccountRepository, clock)`. With `accountId == null` and `pendingOnly == false`, rows whose `accountId` is an ignored account are dropped before `groupByDay`. The "Chờ trả" chip (`pendingOnly`) keeps them: a debt someone owes stays visible after its account is ignored, and those rows are excluded from reports anyway. With an account chosen, nothing changes: ignored accounts are never chips, and a transfer from an ignored account into the chosen one still touches it. `netOf` stays as it is (the spec's "netOf skips" is done by this filter).

- [ ] **Step 1: Write failing tests:**
  - In `ObserveTransactionListUseCaseTest`: `all accounts hides rows of an ignored account`, `a chosen account still shows a transfer from an ignored account`, and `pending chip keeps a pending row of an ignored account`.
  - In `LedgerReportMatchTest`: `day nets of a month add up to the report net`. One November fixture has INCOME, EXPENSE, a REFUND, a TRANSFER with fee, an ADJUSTMENT, an excluded EXPENSE with a fee, a row of an ignored account and a row of an archived account, plus one October row (for pace). Sum `net` of the days with `YearMonth.from(day.date) == 2026-11` from `ObserveTransactionListUseCase(null, false)` and assert it equals `ObserveMonthReportUseCase(2026-11).first().net`.
- [ ] **Step 2: Run** them. Expected: FAIL on the ignored-account cases.
- [ ] **Step 3: Implement** the filter: combine `observeTransactions(null)` with `observeAccounts()` only when `accountId == null`.
- [ ] **Step 4: Run** `testDebugUnitTest`. Expected: PASS, `TransactionDayTest` unchanged and green.
- [ ] **Step 5: Commit** `fix(balance): leave ignored accounts out of the all-accounts list`.

### Task 6: Fee and "not in reports" on transaction rows

**Files:**
- Modify: `app/src/main/java/io/github/toanhp123/finote/ui/common/component/finance/TransactionRow.kt`
- Modify: `app/src/main/java/io/github/toanhp123/finote/ui/transactions/logic/TransactionRowText.kt`
- Modify: `app/src/main/java/io/github/toanhp123/finote/ui/transactions/TransactionListScreen.kt`
- Modify: `app/src/main/res/values*/strings.xml` (4 locales)
- Test: `app/src/test/java/io/github/toanhp123/finote/ui/transactions/logic/TransactionRowTextTest.kt` (extend; create if absent)
- Modify: `app/src/screenshotTest/kotlin/io/github/toanhp123/finote/ui/transactions/TransactionListScreenshots.kt` and the finance component screenshot file

**Interfaces:**
- Produces:
  - `TransactionRow(..., fee: Long = 0, isMuted: Boolean = false)`. With `fee > 0`, a second trailing line reads label `transaction_row_fee` ("Phí") followed by `MoneyText(fee, MoneyKind.Expense)`. `isMuted` applies `Modifier.alpha(FinoteTheme.colors.mutedAlpha)` (exists, 0.6) to the `FinoteListRow` inside the card, not to `FinoteCard`, so the card background stays solid.
  - `rowSubtitle(..., excludedLabel: String? = null)`: optional like `autoPairedLabel`, so the 14 calls in `TransactionRowTextTest` still compile. Shown right after the accounts when `transaction.isExcludedFromAnalytics`. The subtitle is one line (`maxLines = 1`), so the label is short.
  - `TransactionItem` gets `selectedAccountId: String?`; `fee = transaction.feeAmount` only when `selectedAccountId == null || selectedAccountId == transaction.accountId` (same rule as `netOf`: a transfer seen from its destination has no fee).
  - Strings: `transaction_row_fee` ("Phí"), `transactions_not_in_reports` ("Ngoài báo cáo"; shorter than the spec's "Không tính báo cáo" so the one-line subtitle keeps the merchant or note).

- [ ] **Step 1: Write the failing test** `excluded row says not in reports`: `rowSubtitle` of an excluded row contains `excludedLabel` right after the account name. Also `normal row has no excluded label`. Add a list screenshot case for a transfer filtered by its destination account: no fee line.
- [ ] **Step 2: Run** it. Expected: FAIL (no such parameter).
- [ ] **Step 3: Implement** the parameters and wire them in `TransactionItem`: `fee = transaction.feeAmount` on the source account's view, and `isMuted = transaction.isExcludedFromAnalytics`.
- [ ] **Step 4: Add screenshots** for a transfer with a fee and an excluded row in `TransactionListScreenshots`, and for the component. Run `updateDebugScreenshotTestDefaultTestSuite`, then open the PNGs. The fee line is not clipped at font scale 2, and the muted row is still readable in dark.
- [ ] **Step 5: Run** `testDebugUnitTest`. Expected: PASS.
- [ ] **Step 6: Commit** `feat(ui): show transfer fees and report exclusion on rows`.

### Task 7: Overview screen

**Files:**
- Create: `app/src/main/java/io/github/toanhp123/finote/ui/overview/OverviewContract.kt`, `OverviewViewModel.kt`, `OverviewScreen.kt`
- Create: `app/src/main/java/io/github/toanhp123/finote/ui/overview/component/PaceCard.kt`, `BucketList.kt`, `MerchantList.kt`
- Create: `app/src/main/java/io/github/toanhp123/finote/ui/overview/logic/BucketTitle.kt` (if the title mapping is more than a `when` in the screen)
- Modify: `app/src/main/java/io/github/toanhp123/finote/ui/common/component/finance/BalanceHeroCard.kt`
- Modify: `app/src/main/java/io/github/toanhp123/finote/ui/common/format/DateFormat.kt` (month header)
- Modify: `app/src/main/java/io/github/toanhp123/finote/ui/navigation/FinoteNavHost.kt` (`entry<Overview>`)
- Modify: `app/src/main/res/values*/strings.xml`
- Test: `app/src/test/java/io/github/toanhp123/finote/ui/overview/OverviewViewModelTest.kt`, `app/src/test/java/io/github/toanhp123/finote/ui/common/format/DateFormatTest.kt` (extend or create)
- Create: `app/src/screenshotTest/kotlin/io/github/toanhp123/finote/ui/overview/OverviewScreenshots.kt`

**Interfaces:**
- Consumes: `ObserveMonthReportUseCase`, `ObserveHasTransactionsUseCase`, `ObserveCategoriesUseCase`, `FinoteLineChart`, `ReportFilter`.
- Produces:
  - `data class OverviewState(val isLoading: Boolean = true, val hasError: Boolean = false, val month: YearMonth, val currentMonth: YearMonth, val hasTransactions: Boolean = true, val report: MonthReport? = null, val categoriesById: Map<String, Category> = emptyMap(), val isBucketListExpanded: Boolean = false)`
  - `sealed interface OverviewIntent { Resumed; PreviousMonth; NextMonth; ToggleBuckets; BucketClicked(val filter: ReportFilter); MerchantClicked(val name: String) }`
  - `sealed interface OverviewEffect { data class OpenReport(val month: YearMonth, val filter: ReportFilter) }`
  - `OverviewRoute(viewModel, onOpenReport: (YearMonth, ReportFilter) -> Unit, contentPadding: PaddingValues)`. Task 8 supplies `onOpenReport`; until then the host passes `{ _, _ -> }`.
  - `BalanceHeroCard(label: String, amount: Long, income: Long, net: Long, modifier: Modifier = Modifier)`. Every amount on the hero stays `MoneyKind.Neutral` in `hero.onHero`: red or green text has no contrast on the hero gradient, and `MoneyText` ignores `style.color` for `Income`/`Expense`. The words carry the meaning: label "Đã chi", chips "↑ Thu" (income) and "Ròng" (net; a negative net gets its "−" from `formatMoney`). Callers to update: its preview, `DisplayScreenshots.kt` (3 tests), `FrameScreenshots.kt` (`SampleScreen`), and the signature line in `docs/design-system.md`.
  - `fun formatMonthYear(month: YearMonth, locale: Locale): String` in `DateFormat.kt`, skeleton `yMMMM`.

Behaviour:
- The month is saved in `SavedStateHandle` as `"yyyy-MM"`. `currentMonth = YearMonth.now(clock)`, read again on each report emission. `NextMonth` is a no-op at `currentMonth`. The › button is disabled there.
- Spent < 0 shows `|Spent|` (neutral, on the hero) with the label "Hoàn nhiều hơn chi" in place of "Đã chi". Spent ≤ 0 hides shares.
- **Signs off the hero** (bucket rows, merchant rows, report list rows and total): amount ≥ 0 → `MoneyText(amount, Expense)`; amount < 0 → `MoneyText(|amount|, Income)`, so a net refund reads "+…" in green everywhere. One helper does this in `ui/overview/logic/` and the report list uses it too (`fun moneyOf(amount: Long): Pair<Long, MoneyKind>`).
- Share and bar length: `amount × 100 / positiveTotal` (`HALF_UP`), where `positiveTotal` is the sum of buckets with amount > 0. It is shown only for amount > 0 and `positiveTotal > 0`, so it never passes 100%. A negative bucket has no share and an empty bar. Negative buckets sort last, so the collapsed top 5 can differ from Spent; expanded, the rows add up to Spent.
- The month can change while the screen stays open: an `OverviewIntent.Resumed` sent from a `LifecycleResumeEffect` in the route recomputes `currentMonth`, and so does each report emission. A new month with the app in front and no new row moves on at the next resume (accepted).
- The pace text: `percent > 0` "+N% so với cùng kỳ tháng trước" (expense colour); `< 0` "−N% …" (income colour); `0` "0% …" neutral; `null`: no text and no last-month line. Under the chart, a note when `hasFutureRows`.
- Buckets show the top 5 and a "Xem tất cả (n)" row while `n > 0` more exist. `ToggleBuckets` expands in place. Titles: the category's `displayName`, `transactions_uncategorized`, or "Phí chuyển khoản". The numbers 5 and 100 are `private const val` (detekt `MagicNumber`). A bar fraction is named `ratio`/`barFraction`, never with a money word (Konsist bans `Float` there).
- The merchant section is hidden when `report.merchants` is empty.
- `hasTransactions == false` shows `FinoteEmptyState`. Otherwise the blocks show with zeros, and an empty bucket list shows one short line.

- [ ] **Step 1: Write failing tests** in `OverviewViewModelTest` (Turbine, `MainDispatcherRule`, fakes, fixed clock):
  - `starts at the current month and shows its report`.
  - `previous then next month moves and next stops at the current month`.
  - `month survives process death`: a new VM with the same `SavedStateHandle` starts at the saved month.
  - `a new row re-emits the report`.
  - `bucket click sends OpenReport with month and filter`; `merchant click sends ByMerchant filter`.
  - `storage error sets hasError`.
  - `new month after midnight moves currentMonth`: the clock passes the month end; after `Resumed`, and after a new emission, `currentMonth` is the next month.
  - In a logic test `OverviewMoneyTest`: `moneyOf(−80_000) == 80_000 to Income`, `moneyOf(0) == 0 to Expense`; `share` of a bucket never passes 100 with a negative bucket present; negative bucket has no share.
  - In `DateFormatTest`: `formatMonthYear gives month and year` for `vi` and `en`.
- [ ] **Step 2: Run** them. Expected: FAIL.
- [ ] **Step 3: Implement** the contract, ViewModel, screen, components, `BalanceHeroCard` change, `formatMonthYear`, strings, and `entry<Overview>`.
- [ ] **Step 4: Run** `testDebugUnitTest`. Expected: PASS, Konsist feature template included.
- [ ] **Step 5: Add `OverviewScreenshots`**: full data, partial empty (income only), no data, Spent ≤ 0. Update the `BalanceHeroCard` screenshot. Run `updateDebugScreenshotTestDefaultTestSuite`, then open every PNG. Signs on amounts, font scale 2, dark contrast.
- [ ] **Step 6: Commit** `feat(ui): add overview dashboard`.

### Task 8: Report list screen

**Files:**
- Modify: `app/src/main/java/io/github/toanhp123/finote/ui/navigation/NavKeys.kt`
- Modify: `app/src/main/java/io/github/toanhp123/finote/ui/navigation/FinoteNavHost.kt`
- Create: `app/src/main/java/io/github/toanhp123/finote/ui/report/ReportListContract.kt`, `ReportListViewModel.kt`, `ReportListScreen.kt`
- Create: `app/src/main/java/io/github/toanhp123/finote/ui/report/logic/ReportListKey.kt`
- Modify: `app/src/main/res/values*/strings.xml`
- Test: `app/src/test/java/io/github/toanhp123/finote/ui/report/ReportListViewModelTest.kt`, `app/src/test/java/io/github/toanhp123/finote/ui/report/logic/ReportListKeyTest.kt`
- Create: `app/src/screenshotTest/kotlin/io/github/toanhp123/finote/ui/report/ReportListScreenshots.kt`

**Interfaces:**
- Consumes: `ObserveReportRowsUseCase`, `ObserveAccountsUseCase`, `ObserveCategoriesUseCase`, `isOpenable`, `rowSubtitle`, `TransactionRow`, Task 7's `onOpenReport`.
- Produces:
  - `@Serializable enum class ReportListKind { CATEGORY, UNCATEGORIZED, TRANSFER_FEES, MERCHANT }` and `@Serializable data class ReportList(val month: String, val kind: ReportListKind, val key: String? = null) : NavKey`. `key` is the category id or the merchant name. Its `toString()` hides `key`.
  - `fun ReportFilter.toNavKey(month: YearMonth): ReportList` and `fun ReportList.toFilter(): ReportFilter`, plus `fun ReportList.yearMonth(): YearMonth`, in `logic/ReportListKey.kt`.
  - `ReportListViewModel` with `@AssistedInject` `key: ReportList` (same pattern as `CategoryEditViewModel.Factory`). State: `isLoading`, `hasError`, `title` source (filter and category), `rows: List<ReportRow>`, `total: Long`, `accountsById`, `categoriesById`, `openableAccountIds: Set<String>`. Intent `RowClicked(id)`. Effect `OpenEditor(id)`.
  - `ReportListRoute(viewModel, onOpenEditor: (String) -> Unit, onClose: () -> Unit, modifier)`.
  - `toFilter()` uses `requireNotNull(key)` for `CATEGORY` and `MERCHANT` and `require(key == null)` for the other two, so a bad key fails loudly.
  - The host pushes it on top of `Overview`. `isTabBarVisible` is then false (the bottom bar hides), and `currentTab` stays `Overview`. A row opens `EditTransaction` only when `isOpenable(openableAccountIds)`; otherwise it is read-only, as in the transaction list.

Display: the title (category name, "Chưa phân loại", "Phí chuyển khoản" or merchant name) with the month under it. The total line and every row go through Task 7's `moneyOf`, so a negative total reads "+…" like its refund rows. An empty list shows a short empty line with total 0.

- [ ] **Step 1: Write failing tests:**
  - `ReportListKeyTest`: `every filter round trips through the nav key`.
  - `ReportListKeyTest`: `category key without id throws`.
  - `ReportListViewModelTest`: `shows rows and total of the filter`; `rows moved away leave an empty list with total zero`; `row of an archived account does not open`; `openable row sends OpenEditor`.
- [ ] **Step 2: Run** them. Expected: FAIL.
- [ ] **Step 3: Implement** the NavKey, mapping, ViewModel, screen, strings, the nav entry, and pass `onOpenReport` from `entry<Overview>`.
- [ ] **Step 4: Run** `testDebugUnitTest`. Expected: PASS.
- [ ] **Step 5: Add `ReportListScreenshots`** (category with a refund row, fees, empty). Run `updateDebugScreenshotTestDefaultTestSuite`, then open the PNGs.
- [ ] **Step 6: Commit** `feat(ui): add report list for overview amounts`.

### Task 9: Roadmap and full gate

**Files:**
- Modify: `docs/roadmap.md`
- Modify: `docs/superpowers/specs/2026-11-25-dashboard-design.md` (status line)

- [ ] **Step 1: Update `docs/roadmap.md`.** Set step 17 to ✅ with the spec link. Update section 1 (screens, next step 13b or 18). Remove T3 from section 6. Update N-17: `BalanceHeroCard` is now used, so drop that part. Add a debt line if any spec item was left out.
- [ ] **Step 2: Set the spec status** to `implemented on feature/dashboard (date)`.
- [ ] **Step 3: Run `qualityGate`** through `gradle-run` and read the summary. Expected: `exit_status` 0.
- [ ] **Step 4: Commit** `docs: mark dashboard done in roadmap`.
- [ ] **Step 5: Run `reviewer`** once on the branch against the spec, before asking the user about the squash into `dev`.
