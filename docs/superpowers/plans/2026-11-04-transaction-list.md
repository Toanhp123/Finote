# Transaction List Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Thanh tab 4 tab và màn danh sách giao dịch: lọc theo tài khoản bằng chip, sắp `timestamp DESC`, nhóm theo ngày có tổng ròng (roadmap P1 việc 11).

**Architecture:** DAO và repository mở Flow giao dịch (tất cả hoặc một tài khoản). Hàm thuần `groupByDay` ở domain nhóm ngày và tính `net`. `TransactionListViewModel` (MVI, `BaseViewModel`) combine giao dịch, tài khoản, danh mục; `TransactionListScreen` vẽ bằng component có sẵn. `FinoteNavHost` bọc `NavDisplay` trong `FinoteScaffold` + `FinoteNavBar`.

**Tech Stack:** Kotlin, Compose, Navigation3 1.2.0, Hilt, Room 3, JUnit4, Robolectric (`InMemoryDatabaseRule`), Turbine, `kotlinx.coroutines.test`, AGP `screenshotTest`.

**Spec:** `docs/superpowers/specs/2026-11-04-transaction-list-design.md`

## Global Constraints

- Tiền là `Long` VND. Không `Double`. Cộng trừ bằng `Math.addExact` / `Math.subtractExact` / `Math.negateExact`.
- Không log nội dung giao dịch, số tiền, tên tài khoản ra Logcat.
- Feature UI chỉ import `Text`, `Icon` từ Material 3; không `.dp`, `.sp`, `Color(`, `MaterialTheme` ngoài `ui/common/` (Konsist `DesignSystemKonsistTest`).
- Mọi chuỗi mới vào `res/values/strings.xml` (vi, mặc định), `values-en`, `values-ja`, `values-zh-rCN`.
- Icon mới là vector `res/drawable/ic_<name>.xml`. Không `material-icons-extended`.
- Dependency mới chỉ qua `gradle/libs.versions.toml`, không hardcode version.
- Tầng và tên theo `docs/architecture.md`; Konsist trong `app/src/test/java/io/github/toanhp123/finote/architecture/` phải pass.
- Gradle chạy qua skill `gradle-run`, wrapper `'.\gradlew.bat'` (từ Bash).
- Commit chỉ khi user đồng ý, stage file theo tên, không AI attribution (`docs/git-workflow.md`).

## Review Focus

1. Chuyển khoản A → B có phí, lọc theo B → dòng hiện, `net` ngày = 0 (test ở Task 3).
2. Hai giao dịch cùng `timestamp` và `createdAt` → thứ tự theo `id DESC`, ổn định (test ở Task 1).
3. Giao dịch 23:30 giờ Việt Nam = 16:30 UTC cùng ngày, 00:30 giờ Việt Nam = 17:30 UTC hôm trước → nhóm theo `Asia/Ho_Chi_Minh`, không theo UTC (test ở Task 3).
4. Tài khoản đang chọn bị lưu trữ khi màn đang mở → về `Tất cả`, danh sách không rỗng giả (test ở Task 6).
5. Chuyển khoản tới tài khoản lưu trữ → phụ đề vẫn có tên tài khoản đó (test ở Task 7, hàm `rowSubtitle`).

---

### Task 0: Nhánh

- [ ] **Step 1:** Từ `dev`, `git switch -c feature/transaction-list`. Spec và plan (chưa commit) đi theo working tree.
- [ ] **Step 2 (cần user đồng ý):** commit `docs/superpowers/specs/2026-11-04-transaction-list-design.md` và `docs/superpowers/plans/2026-11-04-transaction-list.md`, message `docs(ui): add transaction list design and plan`.

### Task 1: DAO `observeByAccount` và thứ tự tất định

**Files:**
- Modify: `app/src/main/java/io/github/toanhp123/finote/data/transaction/TransactionDao.kt`
- Test: `app/src/test/java/io/github/toanhp123/finote/data/transaction/TransactionDaoTest.kt`

**Interfaces:**
- Produces: `fun observeByAccount(accountId: String): Flow<List<TransactionEntity>>`. `observeTransactions()` giữ tên, thêm `, id DESC` vào `ORDER BY`.

- [ ] **Step 1: Test hỏng.** Trong `TransactionDaoTest`, dùng fixture `transaction(...)` của `data/Fixtures.kt`, tài khoản `acc-1`, `acc-2`, `acc-3`:
  - `observeByAccount returns source rows and incoming transfers`: dòng `acc-1` chi, chuyển `acc-2 → acc-1`, chi `acc-2`. `observeByAccount("acc-1")` → đúng 2 id đầu.
  - `observeByAccount hides soft-deleted rows`: dòng `acc-1` có `deletedAt` → không có.
  - `observeByAccount orders by timestamp, createdAt, then id`: ba dòng `acc-1`; `t-old` timestamp nhỏ hơn; `t-a`, `t-b` cùng `timestamp` và `createdAt`. Kỳ vọng `["t-b", "t-a", "t-old"]`.
  - `observeTransactions breaks ties by id`: như trên, không lọc, cùng kỳ vọng.
- [ ] **Step 2:** `testDebugUnitTest --tests "*TransactionDaoTest"` → FAIL (`observeByAccount` chưa có; ca tie có thể đỏ).
- [ ] **Step 3:** Thêm query theo spec mục 3.1; sửa `ORDER BY` của `observeTransactions`.
- [ ] **Step 4:** Chạy lại → PASS. Chạy cả `*TransactionRepositoryImplTest` (dùng `observeTransactions`) → PASS.

### Task 2: `TransactionRepository.observeTransactions`

**Files:**
- Modify: `app/src/main/java/io/github/toanhp123/finote/domain/transaction/repository/TransactionRepository.kt`
- Modify: `app/src/main/java/io/github/toanhp123/finote/data/transaction/TransactionRepositoryImpl.kt`
- Modify: `app/src/test/java/io/github/toanhp123/finote/domain/transaction/usecase/FakeTransactionRepository.kt`
- Test: `app/src/test/java/io/github/toanhp123/finote/data/transaction/TransactionRepositoryImplTest.kt`

**Interfaces:**
- Consumes: Task 1.
- Produces:
  ```kotlin
  /** Live transactions, newest first. [accountId] null means every account; otherwise source or transfer destination. */
  fun observeTransactions(accountId: String?): Flow<List<Transaction>>
  ```
- Fake: `val transactionsFlow = MutableStateFlow<List<Transaction>>(emptyList())`; `val observedAccountIds = mutableListOf<String?>()`. `observeTransactions(id)` ghi `id` vào `observedAccountIds` rồi trả `transactionsFlow` (fake không tự lọc; test ViewModel đặt sẵn danh sách). `var failObserve: Throwable? = null`: khác null thì trả `flow { throw it }`.

- [ ] **Step 1: Test hỏng.** `observeTransactions maps rows and filters by account`: add chi `acc-1` và chuyển `acc-1 → acc-2` qua repository. `observeTransactions(null).first()` có 2 phần tử kiểu `Transaction`; `observeTransactions("acc-2").first()` chỉ có dòng chuyển.
- [ ] **Step 2:** Chạy → FAIL (không biên dịch).
- [ ] **Step 3:** Impl: `null` → `transactions.observeTransactions()`, còn lại `observeByAccount(id)`; `.map { rows -> rows.map { it.toDomain() } }`. Cập nhật fake.
- [ ] **Step 4:** Chạy `*TransactionRepositoryImplTest` và các test dùng fake (`*usecase*`) → PASS.

### Task 3: Hàm thuần `groupByDay`

**Files:**
- Create: `app/src/main/java/io/github/toanhp123/finote/domain/transaction/TransactionDays.kt`
- Test: `app/src/test/java/io/github/toanhp123/finote/domain/transaction/TransactionDaysTest.kt`

**Interfaces:**
- Produces:
  ```kotlin
  data class TransactionDay(val date: LocalDate, val net: Long, val transactions: List<Transaction>)

  /** [transactions] newest first. [accountId] is the filtered account, null for all. */
  fun groupByDay(transactions: List<Transaction>, zone: ZoneId, accountId: String?): List<TransactionDay>
  ```

Quy tắc `net`: spec mục 3.3. Dùng fixture `tx(...)` của `domain/account/TransactionFixtures.kt` (thêm tham số `id`, `excluded`, `direction` nếu thiếu; giữ mặc định cũ). `zone = ZoneId.of("Asia/Ho_Chi_Minh")`.

- [ ] **Step 1: Test hỏng.** Mỗi ca một test:
  - `empty list gives no days`.
  - `keeps order and splits days at local midnight`: `2026-11-04T16:30Z` (23:30 ngày 4 giờ VN) và `2026-11-04T17:30Z` (00:30 ngày 5) → hai ngày `2026-11-05` rồi `2026-11-04`.
  - `same instant lands on different days in different zones`: `2026-11-04T17:30Z` với `UTC` → `2026-11-04`.
  - `net adds income and refund, subtracts expense`: thu 500_000, hoàn 20_000, chi 120_000 cùng ngày → `net = 400_000`.
  - `fees count as spending for every type`: chi 100_000 phí 1_000, chuyển 1_000_000 phí 3_300 (`accountId = null`) → `net = -104_300`.
  - `transfer principal and adjustment are left out`: chuyển 1_000_000, `ADJUSTMENT IN` 50_000 → `net = 0`, cả hai còn trong `transactions`.
  - `excluded rows stay listed but add nothing, fee included`: chi 200_000 phí 2_000 `isExcludedFromAnalytics = true` → `net = 0`, 1 dòng.
  - `fee of a transfer is skipped when filtering by its destination`: chuyển A → B phí 3_300, `accountId = B` → `net = 0`; `accountId = A` → `net = -3_300`.
  - `overflow throws`: hai thu `Long.MAX_VALUE` cùng ngày → `ArithmeticException`.
  - `net of Long.MIN_VALUE throws`: chi `Long.MAX_VALUE` và phí 1 cùng dòng → `net` chạm `Long.MIN_VALUE` → `ArithmeticException`.
- [ ] **Step 2:** Chạy `*TransactionDaysTest` → FAIL.
- [ ] **Step 3:** Impl theo spec 3.3. Giữ thứ tự đầu vào: gom liên tiếp theo ngày (đầu vào đã sắp).
- [ ] **Step 4:** Chạy → PASS.

### Task 4: `ObserveTransactionListUseCase`

**Files:**
- Create: `app/src/main/java/io/github/toanhp123/finote/domain/transaction/usecase/ObserveTransactionListUseCase.kt`
- Test: `app/src/test/java/io/github/toanhp123/finote/domain/transaction/usecase/ObserveTransactionListUseCaseTest.kt`

**Interfaces:**
- Consumes: Task 2, Task 3.
- Produces: `class ObserveTransactionListUseCase @Inject constructor(repository: TransactionRepository, clock: Clock)`, `operator fun invoke(accountId: String?): Flow<List<TransactionDay>>`.

- [ ] **Step 1: Test hỏng.** `passes the account and groups in the clock zone`: fake có một chi tại `2026-11-04T17:30Z`; clock zone `Asia/Ho_Chi_Minh`; `useCase("acc-a").first()` → một ngày `2026-11-05`; `fake.observedAccountIds == ["acc-a"]`.
- [ ] **Step 2:** Chạy → FAIL.
- [ ] **Step 3:** Impl như spec 3.4. Đặt comment `shortcut:` ở spec mục 6 trên lớp.
- [ ] **Step 4:** Chạy → PASS.

### Task 5: Thanh tab và `ComingSoonScreen`

**Files:**
- Delete: `app/src/main/java/io/github/toanhp123/finote/ui/navigation/Home.kt`
- Create: `app/src/main/java/io/github/toanhp123/finote/ui/navigation/TopLevelTab.kt`
- Create: `app/src/main/java/io/github/toanhp123/finote/ui/navigation/ComingSoonScreen.kt`
- Modify: `app/src/main/java/io/github/toanhp123/finote/ui/navigation/FinoteNavHost.kt`
- Modify: 4 file `strings.xml`
- Modify: `gradle/libs.versions.toml`, `app/build.gradle.kts`
- Test: `app/src/test/java/io/github/toanhp123/finote/ui/navigation/TopLevelTabTest.kt`
- Screenshot: `app/src/screenshotTest/kotlin/io/github/toanhp123/finote/ui/navigation/ComingSoonScreenshots.kt`

**Interfaces:**
- Produces:
  ```kotlin
  @Serializable data object Overview : NavKey
  @Serializable data object Transactions : NavKey
  @Serializable data object Budget : NavKey
  @Serializable data object Settings : NavKey

  val TopLevelTabs: List<NavKey> = listOf(Overview, Transactions, Budget, Settings)

  /** Back stack after picking [tab]: [Overview] alone, or [Overview, tab]. */
  fun backStackFor(tab: NavKey): List<NavKey>

  /** The tab to highlight: the last top-level key on [backStack], or Overview. */
  fun currentTab(backStack: List<NavKey>): NavKey

  @Composable fun ComingSoonScreen(@StringRes titleRes: Int, modifier: Modifier = Modifier)
  ```
- `FinoteNavHost`: bấm tab khác tab hiện tại → `backStack.clear(); backStack.addAll(backStackFor(tab))`. `bottomBar` chỉ truyền khi `backStack.last() in TopLevelTabs`.
- Icon tab: `ic_home` (Tổng quan), `ic_receipt_long` (Giao dịch), `ic_pie_chart` (Ngân sách), `ic_settings` (Cài đặt), đều có sẵn.
- Chuỗi (vi / en / ja / zh-CN):
  - `tab_overview`: Tổng quan / Overview / 概要 / 概览
  - `tab_transactions`: Giao dịch / Transactions / 取引 / 交易
  - `tab_budget`: Ngân sách / Budget / 予算 / 预算
  - `tab_settings`: Cài đặt / Settings / 設定 / 设置
  - `coming_soon_title`: Sắp có / Coming soon / 近日公開 / 即将推出
- Dependency để `TransactionListViewModel` (Task 7) sống theo entry và lấy qua Hilt:
  - `androidx-lifecycle-viewmodel-navigation3` (group `androidx.lifecycle`, `version.ref = "lifecycle"`), dùng `rememberViewModelStoreNavEntryDecorator()` trong `entryDecorators` của `NavDisplay` cùng `rememberSaveableStateHolderNavEntryDecorator()`.
  - `androidx-hilt-lifecycle-viewmodel-compose` (group `androidx.hilt`, version mới nhất ổn định, thêm `hiltAndroidx` vào `[versions]`), cho `hiltViewModel()`.
  - Kiểm tên artifact và version trên Google Maven trước khi thêm. Không có bản tương thích thì dừng, báo lại (không tự thay giải pháp).

- [ ] **Step 1: Test hỏng.** `TopLevelTabTest`:
  - `backStackFor(Overview) == listOf(Overview)`; `backStackFor(Budget) == listOf(Overview, Budget)`.
  - `currentTab(listOf(Overview, Transactions)) == Transactions`.
  - `currentTab` bỏ qua key không phải tab: dùng một `data object Child : NavKey` cục bộ trong test, `currentTab(listOf(Overview, Transactions, Child)) == Transactions`.
  - `currentTab(emptyList()) == Overview`.
- [ ] **Step 2:** Chạy → FAIL.
- [ ] **Step 3:** Impl `TopLevelTab.kt`, `ComingSoonScreen` (`FinoteTopBar(stringResource(titleRes))` + `FinoteEmptyState(ic_stars, coming_soon_title)`), sửa `FinoteNavHost` (bốn entry; `Transactions` tạm dùng `ComingSoonScreen`, Task 7 thay). Thêm dependency, chuỗi.
- [ ] **Step 4:** Chạy `*TopLevelTabTest` và `assembleDebug` → PASS.
- [ ] **Step 5:** Screenshot `ComingSoonScreen` theo mẫu `CashEntryScreenshots.kt` (`@PreviewTest`). `updateDebugScreenshotTestDefaultTestSuite`, mở PNG kiểm bằng mắt.

### Task 6: `TransactionListViewModel`

**Files:**
- Create: `app/src/main/java/io/github/toanhp123/finote/ui/transactions/TransactionListContract.kt`
- Create: `app/src/main/java/io/github/toanhp123/finote/ui/transactions/TransactionListViewModel.kt`
- Test: `app/src/test/java/io/github/toanhp123/finote/ui/transactions/TransactionListViewModelTest.kt`

**Interfaces:**
- Consumes: Task 4; `AccountRepository.observeAccounts()`, `CategoryRepository.observeCategories()`.
- Produces: `TransactionListState`, `TransactionListIntent.SelectAccount` đúng như spec 4.1 (`today: LocalDate` không có mặc định). Không có Effect: `BaseViewModel<TransactionListState, TransactionListIntent, Nothing>`.
  ```kotlin
  @HiltViewModel
  class TransactionListViewModel @Inject constructor(
      savedStateHandle: SavedStateHandle,
      observeList: ObserveTransactionListUseCase,
      accountRepository: AccountRepository,
      categoryRepository: CategoryRepository,
      clock: Clock,
  )
  ```
- Hành vi: spec 4.2. Thu thập trong `viewModelScope` ở `init`, theo cách `CashEntryViewModel` làm.

Test: `MainDispatcherRule`, `runTest(mainRule.dispatcher)`, Turbine, fake `FakeAccountRepository`, `FakeCategoryRepository`, `FakeTransactionRepository`, `testAccount(...)`. Clock `2026-11-04T03:00:00Z`, zone `Asia/Ho_Chi_Minh`.

- [ ] **Step 1: Test hỏng.**
  - `starts loading with today from the clock`: state đầu `isLoading = true`, `today = 2026-11-04`.
  - `shows days and only unarchived accounts as chips`: hai tài khoản, một lưu trữ; một giao dịch → `isLoading = false`, `days.size == 1`, `accounts` chỉ có tài khoản chưa lưu trữ, `accountsById` có cả hai.
  - `select account asks for that account`: `onIntent(SelectAccount("acc-a"))` → `selectedAccountId == "acc-a"`, `fakeTx.observedAccountIds.last() == "acc-a"`.
  - `selection survives in SavedStateHandle`: `SavedStateHandle(mapOf("selected_account_id" to "acc-a"))` → state đầu sau tải có `selectedAccountId == "acc-a"`.
  - `archiving the selected account resets to all`: chọn `acc-a`, đổi `acc-a` thành lưu trữ trong fake → `selectedAccountId == null`.
  - `flow error sets hasError`: `fakeTx.failObserve = SQLiteException()` → `hasError = true`, `isLoading = false`.
- [ ] **Step 2:** Chạy → FAIL.
- [ ] **Step 3:** Impl.
- [ ] **Step 4:** Chạy → PASS.

### Task 7: `TransactionListScreen`

**Files:**
- Create: `app/src/main/java/io/github/toanhp123/finote/ui/transactions/TransactionListScreen.kt`
- Create: `app/src/main/java/io/github/toanhp123/finote/ui/transactions/TransactionRowText.kt`
- Create: `app/src/main/res/drawable/ic_swap_horiz.xml`, `ic_tune.xml` (Material Symbols, 24dp, cùng cách các icon có sẵn)
- Modify: `app/src/main/java/io/github/toanhp123/finote/ui/navigation/FinoteNavHost.kt` (entry `Transactions`)
- Modify: 4 file `strings.xml`
- Test: `app/src/test/java/io/github/toanhp123/finote/ui/transactions/TransactionRowTextTest.kt`
- Screenshot: `app/src/screenshotTest/kotlin/io/github/toanhp123/finote/ui/transactions/TransactionListScreenshots.kt`

**Interfaces:**
- Consumes: Task 5 (entry, `hiltViewModel()`), Task 6.
- Produces:
  ```kotlin
  @Composable fun TransactionListRoute(viewModel: TransactionListViewModel)
  @Composable fun TransactionListScreen(state: TransactionListState, onIntent: (TransactionListIntent) -> Unit, modifier: Modifier = Modifier)

  // TransactionRowText.kt: pure, no Compose, testable on JVM.
  sealed interface DayLabel { data object Today : DayLabel; data object Yesterday : DayLabel; data class Date(val text: String) : DayLabel }
  fun dayLabel(date: LocalDate, today: LocalDate, locale: Locale): DayLabel
  fun moneyKindOf(transaction: Transaction): MoneyKind
  /** [accountName] resolves an id to a display name, null when the account is gone. */
  fun rowSubtitle(transaction: Transaction, accountName: (String) -> String?, deletedLabel: String): String
  ```
- `dayLabel` khác năm `today` thì có năm. Pattern: `DateTimeFormatter.ofPattern("EEE, dd/MM", locale)` và `"EEE, dd/MM/yyyy"`. Không dùng `String.format`.
- `moneyKindOf`: bảng spec 4.3.
- `rowSubtitle`: tên tài khoản (chuyển khoản: `"Nguồn → Đích"`), thiếu thì `deletedLabel`; `note` không rỗng → nối `" · " + note`.
- Tiêu đề, icon, màu theo spec 4.3. Danh mục: `displayName`, `iconRes(category.icon)`, `category.color`. Không danh mục: `TRANSFER` → `ic_swap_horiz`, `ADJUSTMENT` → `ic_tune`, còn lại `ic_more_horiz`; màu trung tính lấy từ token `FinoteTheme` (không `Color(`).
- Header ngày: spec 4.4.
- Chuỗi (vi / en / ja / zh-CN):
  - `transactions_all`: Tất cả / All / すべて / 全部
  - `transactions_today`: Hôm nay / Today / 今日 / 今天
  - `transactions_yesterday`: Hôm qua / Yesterday / 昨日 / 昨天
  - `transactions_transfer`: Chuyển khoản / Transfer / 振替 / 转账
  - `transactions_adjustment`: Điều chỉnh số dư / Balance adjustment / 残高調整 / 余额调整
  - `transactions_uncategorized`: Chưa phân loại / Uncategorized / 未分類 / 未分类
  - `transactions_deleted_account`: Tài khoản đã xóa / Deleted account / 削除済みの口座 / 已删除的账户
  - `transactions_empty`: Chưa có giao dịch / No transactions yet / 取引はまだありません / 暂无交易
  - `transactions_empty_account`: Tài khoản này chưa có giao dịch / No transactions in this account / この口座の取引はありません / 该账户暂无交易
  - `transactions_error`: Không tải được giao dịch / Couldn't load transactions / 取引を読み込めませんでした / 无法加载交易

- [ ] **Step 1: Test hỏng.** `TransactionRowTextTest`, `today = 2026-11-04`:
  - `dayLabel` → `Today` cho 04/11, `Yesterday` cho 03/11, `Date("Mon, 02/11")` cho 02/11 với `Locale.ENGLISH`, `Date("Tue, 04/11/2025")` cho 04/11/2025.
  - `moneyKindOf`: INCOME, REFUND → `Income`; EXPENSE → `Expense`; TRANSFER → `Transfer`; ADJUSTMENT IN → `Income`; ADJUSTMENT OUT → `Expense`.
  - `rowSubtitle`: chi tài khoản "Tiền mặt" → `"Tiền mặt"`; có note "Bún bò" → `"Tiền mặt · Bún bò"`; chuyển `acc-a → acc-b` (b đã lưu trữ, vẫn tra được tên "MoMo") → `"Vietcombank → MoMo"`; tài khoản không tra được → `deletedLabel`.
- [ ] **Step 2:** Chạy → FAIL.
- [ ] **Step 3:** Impl `TransactionRowText.kt`, rồi `TransactionListScreen` (spec 4.4, 4.5) và `TransactionListRoute` (`collectAsStateWithLifecycle`). Entry `Transactions` gọi `TransactionListRoute(hiltViewModel())`.
- [ ] **Step 4:** Chạy `*TransactionRowTextTest` → PASS; `assembleDebug` → PASS.
- [ ] **Step 5:** Screenshot theo mẫu `CashEntryScreenshots.kt`, `today = 2026-11-04` cố định: (a) 3 ngày có chi, thu, chuyển có note, điều chỉnh OUT, chip `Tất cả` chọn; (b) rỗng `Tất cả`; (c) rỗng đang lọc; (d) lỗi; (e) đang tải. `updateDebugScreenshotTestDefaultTestSuite`, mở từng PNG: dấu số tiền đúng, chữ không bị cắt ở font scale 2, tương phản dark.

### Task 8: Cổng chất lượng

- [ ] **Step 1:** `testDebugUnitTest` qua `gradle-run` → PASS.
- [ ] **Step 2:** `qualityGate` qua `gradle-run` → PASS, đọc tóm tắt.
- [ ] **Step 3:** `reviewer` một lần cho cả nhánh, so với spec. Lỗi gửi lại `coder`, review lại.
- [ ] **Step 4 (cần user đồng ý):** commit theo nhóm Task, rồi squash vào `dev` theo `docs/git-workflow.md`.
