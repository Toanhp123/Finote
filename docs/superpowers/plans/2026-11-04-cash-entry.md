# Cash Entry Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Bottom sheet nhập tiền mặt (bàn phím `+ − × ÷`, chọn tài khoản và danh mục), mở thẳng từ Quick Settings Tile và App Shortcut.

**Architecture:** Logic thuần ở `domain/transaction` (`AmountExpression`, `CashEntryDefaults`). Data thêm `Account.nameKey`, seed tài khoản `sys_cash`, hai query "dùng lần trước". UI ở `ui/cashentry`: một `AppCompatActivity` trong suốt chỉ vẽ sheet, MVI trên `BaseViewModel`, lưu qua `AddTransactionUseCase` có sẵn.

**Tech Stack:** Kotlin, Compose, Hilt, Room 3, composeunstyled sheet, JUnit4, Robolectric, Turbine, Compose screenshot test.

**Spec:** `docs/superpowers/specs/2026-11-04-cash-entry-design.md`

## Global Constraints

- Tiền là `Long` VND. Không `Double`/`Float` cho `amount`, `balance`.
- Không `android.util.Log`, `println`. Không ghi số tiền, ghi chú, tên tài khoản vào Logcat.
- Không `@Suppress` dưới mọi dạng. Lint `warningsAsErrors`.
- Mọi chữ UI nằm trong `strings.xml` đủ 4 bộ: `values/` (vi), `values-en/`, `values-zh-rCN/`, `values-ja/`.
- Giới hạn: 300 dòng/file, 40 dòng/hàm, 80 dòng/`@Composable`, 6 tham số/hàm (Composable miễn), độ phức tạp 10.
- Package gốc chỉ có `MainActivity`, `FinoteApplication`. Không file nào import package gốc.
- Màu, khoảng cách, chữ lấy từ `FinoteTheme`. Component chung chỉ dùng API công khai trong `docs/design-system.md`.
- Dependency mới chỉ qua `gradle/libs.versions.toml`. Plan này không cần dependency mới: Activity lấy ViewModel bằng `by viewModels()` (Hilt hỗ trợ cho `@AndroidEntryPoint`).
- Gradle chạy qua skill `gradle-run`, wrapper `'.\gradlew.bat'` từ Bash.
- Commit chỉ khi user bảo. Không `Co-Authored-By`.

## Review Focus

1. Mở sheet lần đầu ngay sau khi cài: tài khoản phải là `sys_cash`, không phải "Chưa có tài khoản". → Task 7 test `empty accounts then seeded account selects it`.
2. Lưu với ngày đã chọn khác hôm nay: `timestamp` là 12:00 giờ máy của ngày đó, và phải qua được `ValidateTransactionUseCase` (≥ 2000-01-01, ≤ now + 366 ngày). → Task 7 test `save on past date uses noon local time`.
3. Gõ số âm giữa chừng rồi sửa thành dương: không hiện chữ lỗi lúc âm, Lưu bật khi dương lại. → Task 4 test `negative intermediate then positive is ok`, Task 7 test `not positive shows no error text`.
4. User lưu trữ tài khoản đang chọn trong lúc sheet mở: chọn lại theo chuỗi mặc định, không lưu vào tài khoản đã lưu trữ. → Task 7 test `selected account archived reselects default`.
5. Đổi ngôn ngữ app sau khi seed: "Tiền mặt" đổi tên theo; tài khoản đã đổi tên giữ tên user. → Task 1 test `update with new name clears nameKey`, Task 6 test `every system key has a string`.

---

### Task 1: `Account.nameKey` và DB version 3

**Files:**
- Modify: `domain/account/model/Account.kt`, `data/account/AccountEntity.kt`, `data/account/AccountMapper.kt`, `data/account/AccountRepositoryImpl.kt`, `domain/account/repository/AccountRepository.kt` (KDoc của `update`), `data/FinoteDatabase.kt` (`version = 3`)
- Create: `app/schemas/.../3.json` (KSP tự sinh khi build)
- Modify test: `test/.../data/Fixtures.kt`, `test/.../domain/account/AccountFixtures.kt`, `test/.../data/account/AccountMapperTest.kt`, `test/.../data/account/AccountRepositoryImplTest.kt`

**Interfaces:**
- Produces: `Account(…, nameKey: String? = null, …)` và `AccountEntity(…, nameKey: String?, …)`. Đặt `nameKey` ngay sau `name`. Ở `Account` có default `null` để các chỗ tạo `Account` hiện có không phải sửa.

- [ ] **Step 1: Viết test hỏng**
  - `AccountMapperTest`: `nameKey survives round trip` — entity có `nameKey = "sys_cash"` → `toDomain().toEntity()` giữ `"sys_cash"`.
  - `AccountRepositoryImplTest`:
    - `add forces nameKey to null`: `add(testAccount().copy(nameKey = "sys_x"))` → `getAccount(id)!!.nameKey == null`.
    - `update with same name keeps nameKey`: chèn entity có `nameKey = "sys_cash"`, `name = "Cash"` qua DAO; `update(getAccount(id)!!.copy(sortOrder = 5))` (giữ `name = "Cash"`) → key vẫn `"sys_cash"`.
    - `update with new name clears nameKey`: như trên nhưng `name = "Ví"` → key `null`.
- [ ] **Step 2: Chạy, xác nhận hỏng** — `testDebugUnitTest --tests "*AccountMapperTest" --tests "*AccountRepositoryImplTest"` qua `gradle-run`. Expected: lỗi biên dịch `nameKey`.
- [ ] **Step 3: Cài đặt**
  - Thêm field ở model, entity, mapper (hai chiều).
  - `AccountRepositoryImpl.add`: `copy(currentBalance = …, nameKey = null)`.
  - `AccountRepositoryImpl.update`: thêm `nameKey = if (account.name == old.name) old.nameKey else null` vào `copy` (chép `CategoryRepositoryImpl.kt:47`).
  - KDoc `Account`: chép câu về `nameKey` từ KDoc `Category`. KDoc `AccountRepository.update`: chép đoạn "Contract for `name`" từ `CategoryRepository.update`.
  - `FinoteDatabase.version = 3`. `Fixtures.account(…)` thêm `nameKey: String? = null`.
- [ ] **Step 4: Chạy lại** — Expected: PASS. Kiểm `app/schemas/io.github.toanhp123.finote.data.FinoteDatabase/3.json` đã sinh và có cột `nameKey`.
- [ ] **Step 5: Commit** — `feat(db): add nameKey to account`

### Task 2: Seed tài khoản `sys_cash`

**Files:**
- Create: `data/account/SystemAccountSeeder.kt`, `res/drawable/ic_payments.xml` (Material Symbols "payments", 24dp, cùng kiểu với các `ic_*` có sẵn)
- Modify: `data/FinoteDatabaseFactory.kt`, 4 file `strings.xml`, `test/.../data/InMemoryDatabaseRule.kt`
- Test: `test/.../data/account/SystemAccountSeederTest.kt`

**Interfaces:**
- Produces: `const val SYSTEM_CASH_ACCOUNT_ID = "sys_cash"` đặt trong **`domain/account/model/SystemAccounts.kt`** (domain cần nó cho `CashEntryDefaults`; data import từ domain).
- Produces: `class SystemAccountSeeder(clock: Clock) : RoomDatabase.Callback()`.
- Produces: `InMemoryDatabaseRule(seedSystemCategories, seedSystemAccount: Boolean = false, clock)`.

Giá trị dòng seed: `id = nameKey = "sys_cash"`, `name = "Cash"`, `type = CASH`, `initialBalance = currentBalance = 0`, `currency = "VND"`, `icon = "payments"`, `color = 0xFF2E7D32.toInt()`, `sortOrder = 0`, `excludeFromNetWorth = 0`, `isArchived = 0`, `isInitialized = 0`, `createdAt = updatedAt = clock.millis()`, `deletedAt = NULL`.

Chuỗi `sys_cash`: vi `Tiền mặt`, en `Cash`, zh-rCN `现金`, ja `現金`.

- [ ] **Step 1: Viết test hỏng** (Robolectric, theo mẫu `SystemCategorySeederTest`)
  - `new database holds the cash account`: rule `seedSystemAccount = true` → `accountDao().getById("sys_cash")` có đúng mọi giá trị ở trên.
  - `soft deleted cash account is not seeded again`: soft delete `sys_cash` bằng `execSql`, đóng rồi mở lại DB file (dùng file DB thật như test destructive migration) → vẫn đúng 1 dòng, `deletedAt != null`.
  - `destructive migration from an old version seeds again`: chép test cùng tên của seeder danh mục, `user_version = 2`.
- [ ] **Step 2: Chạy, xác nhận hỏng.**
- [ ] **Step 3: Cài đặt** — chép cấu trúc `SystemCategorySeeder`: `onCreate` seed; `onOpen` chỉ seed khi `SELECT 1 FROM accounts LIMIT 1` không có dòng (tính cả dòng đã xóa mềm), trong `BEGIN IMMEDIATE`/`COMMIT`/`ROLLBACK`. Thêm `.addCallback(SystemAccountSeeder(clock))` vào `createFinoteDatabase`. Thêm cờ vào `InMemoryDatabaseRule`.
- [ ] **Step 4: Chạy lại** — Expected: PASS, và `SystemCategorySeederTest` vẫn PASS.
- [ ] **Step 5: Commit** — `feat(db): seed a system cash account`

### Task 3: Query "dùng lần trước"

**Files:**
- Modify: `data/transaction/TransactionDao.kt`, `domain/transaction/repository/TransactionRepository.kt`, `data/transaction/TransactionRepositoryImpl.kt`, `test/.../domain/transaction/usecase/FakeTransactionRepository.kt`
- Test: `test/.../data/transaction/TransactionDaoTest.kt`

**Interfaces:**
- Produces (`TransactionRepository`):
  - `suspend fun getLastManualAccountId(): String?`
  - `suspend fun getLastManualCategoryId(type: TransactionType): String?`
- DAO:
  - `@Query("SELECT accountId FROM transactions WHERE deletedAt IS NULL AND source = 'MANUAL' ORDER BY createdAt DESC LIMIT 1") suspend fun getLastManualAccountId(): String?`
  - `@Query("SELECT categoryId FROM transactions WHERE deletedAt IS NULL AND source = 'MANUAL' AND type = :type AND categoryId IS NOT NULL ORDER BY createdAt DESC LIMIT 1") suspend fun getLastManualCategoryId(type: TransactionType): String?`
- `FakeTransactionRepository`: thêm `var lastAccountId: String? = null`, `val lastCategoryIds: MutableMap<TransactionType, String?> = mutableMapOf()`, trả về các giá trị đó.

- [ ] **Step 1: Viết test hỏng** — trong `TransactionDaoTest`, dùng `Fixtures.transaction(…)`:
  - `last manual account skips deleted and non manual rows`: 3 dòng `createdAt` 1, 2, 3; dòng 3 `source = NOTIFICATION`, dòng 2 đã xóa → trả `accountId` của dòng 1.
  - `last manual category skips null category`: dòng mới nhất `categoryId = null` → trả danh mục của dòng cũ hơn.
  - `last manual category filters by type`: dòng EXPENSE mới hơn, dòng INCOME cũ hơn; hỏi `INCOME` → danh mục của dòng INCOME.
  - `empty table returns null` cho cả hai.
- [ ] **Step 2: Chạy, xác nhận hỏng.**
- [ ] **Step 3: Cài đặt** DAO, interface, impl (gọi thẳng DAO), fake.
- [ ] **Step 4: Chạy lại** — Expected: PASS. Nếu Room báo enum `source`/`type` không so được với chuỗi literal, đổi literal thành tham số `TransactionSource` của hàm DAO.
- [ ] **Step 5: Commit** — `feat(db): query last manual account and category`

### Task 4: `AmountExpression`

**Files:**
- Create: `domain/transaction/AmountExpression.kt`
- Test: `test/.../domain/transaction/AmountExpressionTest.kt`

**Interfaces:**
- Produces:
```kotlin
enum class CalcKey { D0, D1, D2, D3, D4, D5, D6, D7, D8, D9, TRIPLE_ZERO, BACKSPACE, PLUS, MINUS, TIMES, DIVIDE }

sealed interface AmountResult {
    data object Empty : AmountResult
    data class Ok(val amount: Long) : AmountResult
    data class NotPositive(val value: Long) : AmountResult
    data class Invalid(val reason: InvalidReason) : AmountResult
}
enum class InvalidReason { DIVIDE_BY_ZERO, OVERFLOW }

object AmountExpression {
    const val MAX_DIGITS = 13
    /** Returns the new expression text. Operators are stored as '+', '−', '×', '÷'. */
    fun press(expression: String, key: CalcKey): String
    fun evaluate(expression: String): AmountResult
}
```
Spec viết `Error(NOT_POSITIVE)`; plan tách thành `NotPositive(value)` vì nó mang giá trị và UI xử lý khác hẳn hai lỗi kia. Hai enum và sealed interface được phép nằm cùng file vì luật "1 public class mỗi file" (nếu Konsist báo thì tách `AmountResult.kt`, `CalcKey.kt`).

- [ ] **Step 1: Viết test hỏng.** Helper `fun type(vararg keys: CalcKey) = keys.fold("") { e, k -> AmountExpression.press(e, k) }`. Các test (giá trị là đồng):
  - `precedence`: `20000 + 5000 × 3` → `Ok(35000)`.
  - `left to right same level`: `100 − 20 − 30` → `Ok(50)`; `100 ÷ 5 × 2` → `Ok(40)`.
  - `division rounds half up`: `5 ÷ 2` → `Ok(3)`; `7 ÷ 2` → `Ok(4)`; `10 ÷ 4` → `Ok(3)`; `10 ÷ 3` → `Ok(3)`.
  - `division rounds at each step`: `100000 ÷ 3 × 3` → `Ok(99999)`.
  - `divide by zero`: `5 ÷ 0` → `Invalid(DIVIDE_BY_ZERO)`.
  - `overflow`: `9999999999999 × 9999999999999` → `Invalid(OVERFLOW)`.
  - `trailing operator ignored`: `35000 +` → `Ok(35000)`.
  - `operator replaces operator`: press `5, PLUS, TIMES, 3` → text `"5×3"`.
  - `cannot start with operator`: press `PLUS` trên `""` → `""`.
  - `max 13 digits`: 14 lần `D9` → số có 13 chữ số.
  - `triple zero fills to cap`: số 11 chữ số + `TRIPLE_ZERO` → 13 chữ số; 12 chữ số → 13; 13 chữ số → không đổi.
  - `triple zero on empty gives zero`: `""` + `TRIPLE_ZERO` → `"0"`; `"5+"` + `TRIPLE_ZERO` → `"5+0"`.
  - `leading zero replaced`: `D0, D5` → `"5"`.
  - `backspace`: `"12+"` → `"12"`; `""` → `""`.
  - `zero result not positive`: `1 ÷ 3` → `NotPositive(0)`.
  - `negative intermediate then positive is ok`: `10 − 20` → `NotPositive(-10)`; thêm `+ 50` → `Ok(40)`.
  - `empty`: `""` → `Empty`.
- [ ] **Step 2: Chạy, xác nhận hỏng.**
- [ ] **Step 3: Cài đặt.** Tách chuỗi thành số và phép tính; gộp `×`/`÷` trong từng hạng tử trước, rồi cộng trừ. Phép chia: `Math.addExact(Math.multiplyExact(a, 2), b) / Math.multiplyExact(b, 2)` (a, b ≥ 0). Bắt `ArithmeticException` → `OVERFLOW`.
- [ ] **Step 4: Chạy lại** — Expected: PASS.
- [ ] **Step 5: Commit** — `feat: add cash entry amount expression`

### Task 5: `CashEntryDefaults`

**Files:**
- Create: `domain/transaction/CashEntryDefaults.kt`
- Test: `test/.../domain/transaction/CashEntryDefaultsTest.kt`

**Interfaces:**
- Consumes: `SYSTEM_CASH_ACCOUNT_ID` (Task 2), `Account`, `Category`.
- Produces:
```kotlin
object CashEntryDefaults {
    /** Live (not deleted, not archived) accounts only. */
    fun pickAccount(accounts: List<Account>, lastUsedId: String?): String?
    /** Null unless the id is a live, not hidden category whose type matches. */
    fun pickCategory(categories: List<Category>, lastUsedId: String?, type: CategoryType): String?
}
```

- [ ] **Step 1: Viết test hỏng** (dùng `testAccount()`, `testCategory()` có sẵn):
  - `last used live account wins`.
  - `archived last used falls back to cash`; `deleted last used falls back to cash`.
  - `no cash falls back to lowest sortOrder` (tài khoản sortOrder 2 và 1 → trả sortOrder 1).
  - `all archived returns null`; `empty list returns null`.
  - `category last used kept when live`; `hidden`, `deleted`, `wrong type`, `unknown id` → null.
- [ ] **Step 2–4:** chạy hỏng, cài đặt, chạy PASS.
- [ ] **Step 5: Commit** — `feat: pick cash entry default account and category`

### Task 6: Tên và icon hệ thống trên UI

**Files:**
- Create: `ui/common/format/SystemResources.kt`
- Test: `test/.../ui/common/format/SystemResourcesTest.kt`

Theo spec mục 2.3: bảng tường minh thay cho `getIdentifier`, vì lint `DiscouragedApi` chặn (`warningsAsErrors`).

**Interfaces:**
- Produces:
```kotlin
@StringRes fun systemNameRes(nameKey: String): Int?   // null for an unknown key
@DrawableRes fun iconRes(icon: String): Int           // R.drawable.ic_more_horiz for an unknown name
@Composable fun displayName(name: String, nameKey: String?): String  // stringResource when the key is known, else name
```

- [ ] **Step 1: Viết test hỏng** (JVM thuần):
  - `every system key has a string`: mọi `nameKey` trong `SystemCategories` (data, test được phép import) và `"sys_cash"` → `systemNameRes(key) != null`.
  - `every system icon has a drawable`: mọi `icon` trong `SystemCategories` và `"payments"` → `iconRes(icon) != R.drawable.ic_more_horiz`, trừ chính `"more_horiz"`.
  - `unknown key returns null`.
- [ ] **Step 2–4:** chạy hỏng, cài đặt hai bảng `mapOf(...)`, chạy PASS.
- [ ] **Step 5: Commit** — `feat(ui): map system name keys and icons to resources`

### Task 7: Contract và ViewModel

**Files:**
- Create: `ui/cashentry/CashEntryContract.kt`, `ui/cashentry/CashEntryViewModel.kt`
- Test: `test/.../ui/cashentry/CashEntryViewModelTest.kt`

**Interfaces:**
- Consumes: `AmountExpression`, `CalcKey`, `AmountResult` (Task 4); `CashEntryDefaults` (Task 5); `TransactionRepository.getLastManual*` (Task 3); `AccountRepository.observeAccounts()`, `CategoryRepository.observeCategories()`, `AddTransactionUseCase`, `Clock`.
- Produces:
```kotlin
enum class CashEntryPage { KEYPAD, ACCOUNT, CATEGORY, NOTE }

data class CashEntryState(
    val type: TransactionType = TransactionType.EXPENSE,
    val expression: String = "",
    val result: AmountResult = AmountResult.Empty,
    val accounts: List<Account> = emptyList(),          // live, not archived, by sortOrder
    val selectedAccountId: String? = null,
    val categories: List<Category> = emptyList(),       // live, not hidden, current type
    val selectedCategoryIds: Map<TransactionType, String?> = emptyMap(),
    val date: LocalDate,
    val note: String = "",
    val page: CashEntryPage = CashEntryPage.KEYPAD,
    val isSaving: Boolean = false,
    val hasSaveError: Boolean = false,
) { val canSave: Boolean /* result is Ok, account selected, !isSaving */ }

sealed interface CashEntryIntent {
    data class KeyPressed(val key: CalcKey)
    data class TypeChanged(val type: TransactionType)
    data class AccountPicked(val id: String)
    data class CategoryPicked(val id: String?)
    data class DatePicked(val date: LocalDate)
    data class NoteChanged(val note: String)
    data class PageOpened(val page: CashEntryPage)
    data object PageClosed
    data object SaveClicked
    data object Dismissed
}
sealed interface CashEntryEffect { data object Close }

@HiltViewModel
class CashEntryViewModel @Inject constructor(
    accounts: AccountRepository,
    categories: CategoryRepository,
    transactions: TransactionRepository,
    private val addTransaction: AddTransactionUseCase,
    private val clock: Clock,
) : BaseViewModel<CashEntryState, CashEntryIntent, CashEntryEffect>(…)
```
Danh mục của Chi lấy `CategoryType.EXPENSE`, của Thu lấy `INCOME`. `date` ban đầu = `LocalDate.now(clock)`. `note` giới hạn 200 ký tự (cắt ở `NoteChanged`).

Hành vi:
- `init`: đọc `getLastManualAccountId()` và `getLastManualCategoryId` cho cả hai loại một lần; rồi `combine(observeAccounts, observeCategories)` → sau mỗi lần phát: lọc danh sách; nếu `selectedAccountId` null hoặc không còn trong danh sách thì `pickAccount`; mỗi loại: lần đầu dùng `pickCategory`, các lần sau chỉ đưa về null nếu id không còn hợp lệ.
- `SaveClicked` khi `canSave`: tạo `Transaction(id = UUID.randomUUID().toString(), amount, feeAmount = 0, type, adjustmentDirection = null, accountId, toAccountId = null, timestamp, deletedAt = null, createdAt = 0, updatedAt = 0, categoryId = selectedCategoryIds[type], note = note.trim(), source = MANUAL)`. `timestamp`: hôm nay → `clock.millis()`; ngày khác → `date.atTime(12, 0).atZone(clock.zone)`. Gọi use case trong `try`; thành công → `sendEffect(Close)`; bắt `IllegalArgumentException` → `hasSaveError = true`, `isSaving = false`, giữ mọi thứ khác.
- `CategoryPicked`, `AccountPicked`: ghi lựa chọn, `page = KEYPAD`.
- `PageClosed`: `page = KEYPAD`. `Dismissed`: `sendEffect(Close)`.
- `KeyPressed`: `expression = press(…)`, `result = evaluate(…)`, `hasSaveError = false`.

- [ ] **Step 1: Viết test hỏng** (`MainDispatcherRule`, Turbine, `Clock.fixed(2026-11-04T03:00:00Z, ZoneId.of("Asia/Ho_Chi_Minh"))`, repo giả: `FakeAccountRepository`, `FakeCategoryRepository`, `FakeTransactionRepository`, `AddTransactionUseCase` thật trên fake):
  - `opens with last used account and categories`.
  - `empty accounts then seeded account selects it`: fake phát `[]` rồi `[sys_cash]` → `selectedAccountId == "sys_cash"`.
  - `selected account archived reselects default`.
  - `category choice survives type switch`: chọn tay danh mục Chi `c2`, đổi Thu, đổi lại Chi → vẫn `c2`.
  - `category picked null clears it`.
  - `not positive shows no error text and disables save`: phím `1 ÷ 3` → `result is NotPositive`, `canSave == false`.
  - `save today uses clock millis and closes`: → fake `stored` có 1 giao dịch đúng `amount`, `type`, `accountId`, `categoryId`, `source = MANUAL`, `timestamp = clock.millis()`; effect `Close`.
  - `save on past date uses noon local time`: `DatePicked(2026-10-01)` → `timestamp == 2026-10-01T12:00+07:00`.
  - `save failure keeps input`: fake không có tài khoản active → `hasSaveError`, `expression` giữ nguyên, không có effect.
- [ ] **Step 2–4:** chạy hỏng, cài đặt, chạy PASS.
- [ ] **Step 5: Commit** — `feat(ui): add cash entry view model`

### Task 8: Màn hình sheet

**Files:**
- Create: `ui/cashentry/CashEntryScreen.kt` (`CashEntrySheet(state, onIntent)`, không có ViewModel), `ui/cashentry/CashKeypad.kt`, `ui/cashentry/CashEntryPickers.kt` (trang tài khoản, danh mục, ghi chú)
- Modify: 4 file `strings.xml`
- Test: `app/src/screenshotTest/kotlin/io/github/toanhp123/finote/ui/cashentry/CashEntryScreenshots.kt`

**Interfaces:**
- Consumes: `CashEntryState`, `CashEntryIntent` (Task 7); `displayName`, `iconRes` (Task 6).
- Produces: `@Composable fun CashEntrySheet(state: CashEntryState, onIntent: (CashEntryIntent) -> Unit, modifier: Modifier = Modifier)` — chỉ phần nội dung bên trong sheet. `@Composable fun CashEntryRoute(viewModel: CashEntryViewModel, onClose: () -> Unit)` — gom state, `ObserveEffects`, `FinoteBottomSheet(isVisible = true, onDismiss = { onIntent(Dismissed) })`, `BackHandler(enabled = state.page != KEYPAD) { onIntent(PageClosed) }` đặt **trong** nội dung sheet.

Chuỗi mới (key → vi / en / zh-rCN / ja):
`cash_entry_expense` Chi / Expense / 支出 / 支出 · `cash_entry_income` Thu / Income / 收入 / 収入 · `cash_entry_save` Lưu / Save / 保存 / 保存 · `cash_entry_no_account` Chưa có tài khoản / No account / 没有账户 / 口座がありません · `cash_entry_no_category` Không chọn danh mục / No category / 不选分类 / カテゴリなし · `cash_entry_note` Ghi chú / Note / 备注 / メモ · `cash_entry_today` Hôm nay / Today / 今天 / 今日 · `cash_entry_error_divide_by_zero` Không chia được cho 0 / Cannot divide by zero / 不能除以零 / 0で割れません · `cash_entry_error_overflow` Số quá lớn / Number too large / 数字太大 / 数値が大きすぎます · `cash_entry_error_save` Không lưu được, thử lại / Could not save, try again / 无法保存，请重试 / 保存できません。もう一度お試しください · `cash_entry_done` Xong / Done / 完成 / 完了 · `cash_entry_backspace` Xóa / Delete / 删除 / 削除 (contentDescription của `⌫`).

Bố cục theo spec mục 3.3. Số tiền: `MoneyText(kind = Expense|Income, style = amountLarge, shrinkToFit = true)` với `Ok.amount` hoặc `NotPositive.value`; `Empty` hiện `0`. Dòng lỗi chỉ cho `Invalid` và `hasSaveError`. Phím `CashKeypad`: lưới 4×4 như mockup đã duyệt (`7 8 9 ÷ / 4 5 6 × / 1 2 3 − / 000 0 ⌫ +`), mỗi phím ≥ 48dp, `⌫` có contentDescription. Trang ghi chú: `FinoteTextField(maxLength = 200)` tự focus bằng `FocusRequester`, `keyboardOptions(imeAction = Done)` → `PageClosed`.

- [ ] **Step 1: Viết screenshot test** — `@PreviewTest` Light, Dark, Large font (`fontScale = 2f`) cho 3 trạng thái: bàn phím có `Ok(35000)`, trang danh mục (có dòng "Không chọn danh mục" và một mục con thụt vào), trang ghi chú. Vẽ `SheetSurface` qua `CashEntrySheet` như `OverlayScreenshots` (không mở cửa sổ thật).
- [ ] **Step 2: Cài đặt** ba file. Mỗi `@Composable` ≤ 80 dòng: tách `AmountHeader`, `SelectorRow`, `CategoryList`, `AccountList`, `NotePage`.
- [ ] **Step 3: Sinh ảnh tham chiếu** — `updateDebugScreenshotTestDefaultTestSuite` qua `gradle-run`. Mở ảnh và kiểm theo `docs/design-system.md`: không có chữ bị cắt ở cỡ 200%, dấu `−` đúng, độ tương phản ở dark.
- [ ] **Step 4: Chạy** `testDebugScreenshotTestDefaultTestSuite` — Expected: PASS.
- [ ] **Step 5: Commit** — `feat(ui): add cash entry sheet`

### Task 9: Activity, Tile, Shortcut

**Files:**
- Create: `ui/cashentry/CashEntryActivity.kt`, `ui/cashentry/CashEntryTileService.kt`, `res/xml/shortcuts.xml`
- Modify: `AndroidManifest.xml`, `res/values/themes.xml`, 4 file `strings.xml`

**Interfaces:**
- Consumes: `CashEntryRoute`, `CashEntryViewModel` (Task 7, 8).

Chi tiết:
- `@AndroidEntryPoint class CashEntryActivity : AppCompatActivity()`: `enableEdgeToEdge()`, `private val viewModel: CashEntryViewModel by viewModels()`, `setContent { FinoteTheme { CashEntryRoute(viewModel, onClose = ::finish) } }`. `onClose` chạy sau khi sheet ẩn xong (`Close` → đặt `isVisible = false`, `finish()` khi state sheet về `Hidden`), để không nháy (spec mục 6).
- Theme `Theme.Finote.Translucent` (parent `Theme.Finote`): `windowIsTranslucent = true`, `windowBackground = @android:color/transparent`, `windowNoTitle`, `backgroundDimEnabled = false`, `windowAnimationStyle = @null`.
- Manifest Activity: `exported="false"` (Tile và shortcut trong cùng app vẫn mở được), `excludeFromRecents="true"`, `taskAffinity=""`, `launchMode="singleTask"`, theme trên.
- `CashEntryTileService : TileService`, `onClick()` → `unlockAndRun { open() }`. `open()`: API ≥ 34 `startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, FLAG_IMMUTABLE))`; dưới 34 `startActivityAndCollapse(intent)` với `intent.addFlags(FLAG_ACTIVITY_NEW_TASK)`. Rẽ nhánh bằng `Build.VERSION.SDK_INT`; lint `NewApi` phải sạch.
- Manifest service: `exported="true"`, `permission="android.permission.BIND_QUICK_SETTINGS_TILE"`, `icon="@drawable/ic_payments"`, `label="@string/cash_entry_tile_label"`, intent-filter `android.service.quicksettings.action.QS_TILE`.
- `shortcuts.xml`: một shortcut `cash_entry`, `shortcutShortLabel="@string/cash_entry_shortcut_short"`, `shortcutLongLabel="@string/cash_entry_shortcut_long"`, icon `ic_payments`, intent `action=android.intent.action.VIEW`, `targetPackage` = applicationId, `targetClass="io.github.toanhp123.finote.ui.cashentry.CashEntryActivity"`. Vì shortcut cần Activity mở được từ launcher, Activity đổi sang `exported="true"` nếu shortcut không chạy được trên máy (kiểm ở Step 3). Gắn `<meta-data android:name="android.app.shortcuts" android:resource="@xml/shortcuts"/>` vào `MainActivity` trong manifest.
- Chuỗi: `cash_entry_tile_label` Ghi tiền mặt / Cash entry / 记现金 / 現金記録 · `cash_entry_shortcut_short` Ghi tiền mặt / Cash entry / 记现金 / 現金記録 · `cash_entry_shortcut_long` Ghi khoản tiền mặt / Record a cash transaction / 记录现金交易 / 現金の取引を記録.

- [ ] **Step 1: Cài đặt** các file trên.
- [ ] **Step 2: Build** — `assembleDebug` và `lintDebug` qua `gradle-run`. Expected: PASS, không cảnh báo.
- [ ] **Step 3: Kiểm tay trên máy thật** (nhờ user, ghi kết quả vào mô tả nhánh): đủ 5 mục kiểm tay của spec mục 5. Riêng mục Back: mở trang danh mục, bấm Back → phải về bàn phím. Nếu sheet đóng luôn thì `BackHandler` thua phần xử lý Back của `composeunstyled`; khi đó đổi sang: `onDismiss` của sheet gửi `PageClosed` khi `page != KEYPAD`, gửi `Dismissed` khi ở `KEYPAD` (vuốt xuống ở trang con sẽ về bàn phím thay vì đóng; chấp nhận).
- [ ] **Step 4: Commit** — `feat(ui): open cash entry from tile and shortcut`

### Task 10: Gate và review

- [ ] **Step 1:** `testDebugUnitTest` qua `gradle-run`. Expected: `exit_status` 0.
- [ ] **Step 2:** `qualityGate` qua `gradle-run`, đọc tóm tắt. Lỗi Konsist hay detekt thì sửa nguyên nhân, không thêm `@Suppress`.
- [ ] **Step 3:** chạy agent `reviewer` một lần trên toàn bộ diff của nhánh so với `dev`, kèm spec và plan. Gửi phát hiện về `coder` sửa, review lại.
- [ ] **Step 4:** báo user; squash vào `dev` chỉ khi user bảo.
