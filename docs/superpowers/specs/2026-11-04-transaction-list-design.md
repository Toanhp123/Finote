# Thiết kế `feature/transaction-list`

Ngày: 2026-11-04. Phase: P1 (roadmap mục 3, việc 11). Nguồn: `docs/roadmap.md` dòng 125; `docs/architecture.md`; `docs/design-system.md`.

## 1. Mục tiêu

Màn danh sách giao dịch cơ bản: lọc theo tài khoản, sắp `timestamp DESC`, nhóm theo ngày. Đây là màn đầu tiên của app chính, nên nhánh này dựng luôn thanh tab.

Đã chốt với user:
- Thanh tab 4 tab: `Tổng quan · Giao dịch · Ngân sách · Cài đặt`. Tab chưa có màn hiện "Sắp có". Mở app vào `Tổng quan`.
- Lọc bằng một hàng chip cuộn ngang: `Tất cả` + mỗi tài khoản chưa lưu trữ. Chọn một chip tại một lúc.
- Nhóm theo ngày, tiêu đề ngày có tổng ròng.
- Nạp cả danh sách qua Flow, nhóm bằng hàm thuần. Không dùng Paging ở P1.

Ngoài phạm vi:
- Bấm dòng: chưa làm gì. `feature/reimbursement-flag` (việc 10) sẽ mở sheet cờ chi hộ.
- Sửa, xóa giao dịch từ danh sách. Tìm kiếm (P3). Lọc theo danh mục, khoảng ngày.
- Paging 3. Xem mục 6.
- `isSplitParent`: chưa có trong model `Transaction`. Khi thêm (P4), `groupByDay` phải bỏ dòng cha khỏi tổng.

## 2. Điều hướng

`ui/navigation/`:
- Key `@Serializable data object`: `Overview`, `Transactions`, `Budget`, `Settings`, đều là `NavKey`. Xóa `Home`, thay bằng `Overview`. Back stack bắt đầu `[Overview]`.
- `FinoteNavHost` bọc `NavDisplay` trong `FinoteScaffold` với `bottomBar = FinoteNavBar(...)`. Thứ tự tab cố định như mục 1.
- Bấm tab: back stack thành `[Overview]` nếu chọn `Overview`, còn lại `[Overview, <tab>]`. Không chồng tab lên nhau. Bấm tab đang chọn: không đổi gì.
- Back ở tab khác `Overview` → về `Overview`. Back ở `Overview` → thoát app (mặc định của `NavDisplay` khi back stack còn một phần tử).
- Tab đang chọn = `backStack.lastOrNull { it in topLevelTabs }`, không lưu riêng. `FinoteNavBar` chỉ hiện khi `backStack.last()` là một tab gốc. Hiện chưa có màn con nào trong `NavDisplay`, nhưng quy tắc này giữ đúng khi thêm.
- `CashEntry` không bị ảnh hưởng: nó là `CashEntryActivity` riêng, mở từ Quick Settings tile và shortcut, không từ `Home`. `Home` hiện chỉ là `Box` trống. Nút "Thêm" trong app ngoài phạm vi nhánh này.
- `Overview`, `Budget`, `Settings` dùng chung `ComingSoonScreen(titleRes)` trong `ui/navigation/`: `FinoteTopBar` + `FinoteEmptyState` "Sắp có".
- Icon tab: `ic_<name>.xml` vector. Dùng lại cái có sẵn, thiếu thì thêm.
- Chuỗi mới vào `strings.xml` của cả 4 ngôn ngữ (D9).

## 3. Dữ liệu và domain

### 3.1 DAO

`TransactionDao.observeTransactions()` nhận thêm tham số, thay vì thêm hàm mới (detekt `TooManyFunctions` giới hạn 11 hàm mỗi DAO):

```kotlin
/** Live rows, newest first. [accountId] null = all; otherwise source or transfer destination. */
@Query(
    "SELECT * FROM transactions WHERE deletedAt IS NULL " +
        "AND (:accountId IS NULL OR accountId = :accountId OR toAccountId = :accountId) " +
        "ORDER BY timestamp DESC, createdAt DESC, id DESC",
)
fun observeTransactions(accountId: String?): Flow<List<TransactionEntity>>
```

`id DESC` giữ thứ tự tất định khi trùng `timestamp` và `createdAt` (test dùng clock cố định). Cập nhật `TransactionDaoTest` và mọi chỗ gọi.

### 3.2 Repository

`TransactionRepository` thêm:

```kotlin
/** Live transactions, newest first. [accountId] null means every account; otherwise source or transfer destination. */
fun observeTransactions(accountId: String?): Flow<List<Transaction>>
```

`TransactionRepositoryImpl` map entity qua `toDomain()`. `FakeTransactionRepository` (test) thêm bản dựa trên `MutableStateFlow`.

### 3.3 Hàm thuần `groupByDay`

File `domain/transaction/TransactionDays.kt`:

```kotlin
data class TransactionDay(val date: LocalDate, val net: Long, val transactions: List<Transaction>)

fun groupByDay(transactions: List<Transaction>, zone: ZoneId, accountId: String?): List<TransactionDay>
```

`accountId` là tài khoản đang lọc, `null` = tất cả.

- Đầu vào đã sắp `timestamp DESC`. Đầu ra giữ thứ tự đó, ngày mới nhất trước.
- Ngày = `Instant.ofEpochMilli(timestamp).atZone(zone).toLocalDate()`.
- `net` của ngày, cộng bằng `Math.addExact` / `Math.subtractExact`:
  - `INCOME`, `REFUND`: `+amount`.
  - `EXPENSE`: `-amount`.
  - Mọi loại: `-feeAmount`, chỉ khi `accountId == null || tx.accountId == accountId`. Phí trừ ở tài khoản nguồn, nên lọc theo tài khoản nhận của chuyển khoản thì không trừ phí.
  - `TRANSFER` (tiền gốc), `ADJUSTMENT`: không tính.
  - Dòng `isExcludedFromAnalytics = true`: bỏ hết, cả phí. Dòng vẫn nằm trong `transactions`.
- Tràn `Long` → `ArithmeticException`. `net == Long.MIN_VALUE` cũng ném `ArithmeticException`, vì UI đổi dấu nó (`negateExact`) và `MoneyText` từ chối giá trị này. Mọi lỗi tràn nằm ở domain, không nổ lúc render.

Khi lọc một tài khoản, chuyển khoản đi và đến đều hiện, nhưng không vào `net`. Lý do: tiền chuyển chỗ trong ví của user, không phải thu chi.

### 3.4 Use case

`domain/transaction/usecase/ObserveTransactionListUseCase`:

```kotlin
operator fun invoke(accountId: String?): Flow<List<TransactionDay>> =
    repository.observeTransactions(accountId).map { groupByDay(it, clock.zone, accountId) }
```

`clock` là `java.time.Clock` đã inject sẵn (`DatabaseModule` dùng). Tên tài khoản và danh mục do ViewModel ghép (mục 4), vì tên danh mục hệ thống cần `stringResource`.

## 4. Màn hình (MVI)

Thư mục `ui/transactions/`: `TransactionListContract.kt`, `TransactionListViewModel.kt`, `TransactionListScreen.kt`. Theo mẫu `ui/cashentry/` và `BaseViewModel`.

### 4.1 Contract

```kotlin
data class TransactionListState(
    val isLoading: Boolean = true,
    val today: LocalDate,                            // LocalDate.now(clock) ở ViewModel; Screen không đọc đồng hồ
    val hasError: Boolean = false,
    val accounts: List<Account> = emptyList(),      // chip: chưa lưu trữ, theo sortOrder
    val selectedAccountId: String? = null,          // null = Tất cả
    val days: List<TransactionDay> = emptyList(),
    val accountsById: Map<String, Account> = emptyMap(),   // cả tài khoản lưu trữ, để hiện tên
    val categoriesById: Map<String, Category> = emptyMap(),
)

sealed interface TransactionListIntent {
    data class SelectAccount(val accountId: String?) : TransactionListIntent
}
```

Không có Effect.

### 4.2 ViewModel

- `selectedAccountId = savedStateHandle.getStateFlow<String?>("selected_account_id", null)`. Intent `SelectAccount` ghi `savedStateHandle["selected_account_id"] = id`. Không có `MutableStateFlow` riêng.
- `selectedAccountId.flatMapLatest { useCase(it) }` combine với `accountRepository.observeAccounts()` và `categoryRepository.observeCategories()`.
- `observeAccounts()` trả mọi tài khoản chưa xóa mềm, kể cả lưu trữ → dựng `accountsById`. `state.accounts` (chip) = `filter { !it.isArchived }`.
- `today = LocalDate.now(clock)` khi dựng state. Qua nửa đêm khi màn đang mở thì nhãn cũ tới lần phát tiếp theo; chấp nhận ở P1.
- Tài khoản đang chọn không còn trong danh sách chip (bị lưu trữ hoặc xóa mềm) → đặt `selectedAccountId = null`.
- Flow lỗi → `catch` bên trong `flatMapLatest` (theo từng tài khoản) đặt `hasError = true`, `isLoading = false`. Chọn chip khác thì thử lại. Không log nội dung giao dịch, số tiền, tên tài khoản (luật Privacy).

### 4.3 Dòng giao dịch

Dùng `TransactionRow` có sẵn. Ánh xạ trong một hàm `@Composable` trong `TransactionListScreen.kt`:

| Trường | Quy tắc |
|---|---|
| Tiêu đề | Có danh mục còn sống: `displayName(name, nameKey)`. Không: `TRANSFER` → "Chuyển khoản", `ADJUSTMENT` → "Điều chỉnh số dư", còn lại → "Chưa phân loại" |
| Phụ đề | Tên tài khoản qua `displayName`. `TRANSFER`: "Nguồn → Đích". Không tìm thấy tài khoản → "Tài khoản đã xóa". `note` không rỗng → nối `" · " + note`. `FinoteListRow` cắt phụ đề ở 1 dòng |
| Icon, màu | Có danh mục: icon và màu của danh mục (`SystemResources.kt`). Không: icon theo loại, màu trung tính từ token |
| `MoneyKind` | `INCOME`, `REFUND` → `Income`; `EXPENSE` → `Expense`; `TRANSFER` → `Transfer`; `ADJUSTMENT` → `Income` khi `adjustmentDirection == IN`, `Expense` khi `OUT` (model bắt buộc có trường này với `ADJUSTMENT`) |
| Số tiền | `amount`. Phí không cộng vào dòng |
| `onClick` | `null` |

`ADJUSTMENT` dùng `Income`/`Expense` để có dấu và màu. Luật "màu không là tín hiệu duy nhất" vẫn giữ nhờ dấu `+`/`−` và tiêu đề.

### 4.4 Bố cục

- `FinoteScaffold` của nav host đã có `bottomBar`. Màn chỉ đặt `FinoteTopBar("Giao dịch")` và nội dung.
- Hàng chip: `LazyRow` các `FinoteChip`. Chip đầu `Tất cả`.
- Danh sách: `LazyColumn`. Mỗi ngày một header (`stickyHeader` không bắt buộc) + các dòng. Key dòng = `transaction.id`, key header = ngày.
- Header ngày: trái là nhãn, phải là `MoneyText(net, kind)`. `net > 0` → `MoneyText(net, Income)`; `net < 0` → `MoneyText(Math.negateExact(net), Expense)`; `net == 0` → `MoneyText(0, Neutral)`. `MoneyText` tự viết dấu và màu (`docs/design-system.md` mục "Money display").
- Nhãn ngày: hôm nay → "Hôm nay", hôm qua → "Hôm qua", còn lại thứ + ngày/tháng theo locale (`DateTimeFormatter.ofLocalizedDate` hoặc pattern trong `ui/common/format/`). Năm khác năm nay thì thêm năm. "Hôm nay" tính theo `Clock` inject, không gọi `LocalDate.now()` trực tiếp.

### 4.5 Trạng thái

- `isLoading` → vài `FinoteSkeleton` dạng dòng.
- `hasError` → `FinoteEmptyState` "Không tải được giao dịch".
- `days` rỗng, `Tất cả` → `FinoteEmptyState` "Chưa có giao dịch".
- `days` rỗng, đang lọc → `FinoteEmptyState` "Tài khoản này chưa có giao dịch".

## 5. Test

JVM:
- `groupByDay`: nhóm đúng ngày; giao dịch 23:59 và 00:01 rơi hai ngày; cùng timestamp khác múi giờ cho ngày khác; `net` cho từng loại; bỏ tiền gốc chuyển khoản và điều chỉnh; phí chuyển khoản tính là chi; lọc theo tài khoản nhận thì không trừ phí; `net == Long.MIN_VALUE` → `ArithmeticException`; dòng `isExcludedFromAnalytics` không vào `net` nhưng vẫn trong danh sách; danh sách rỗng; tràn `Long` → `ArithmeticException`.
- `ObserveTransactionListUseCase`: truyền đúng `accountId`, dùng `clock.zone`.
- `TransactionListViewModel` (Turbine, `MainDispatcherRule`): trạng thái đầu là loading; có dữ liệu; đổi chip đổi danh sách; tài khoản đang chọn bị lưu trữ → về `Tất cả`; lỗi → `hasError`; `SavedStateHandle` giữ lựa chọn.
- Nav: bấm tab đặt đúng back stack; bấm tab đang chọn không đổi; back từ tab về `Overview`; tab đang chọn tìm bằng `lastOrNull { it in topLevelTabs }`.

Robolectric (`TransactionDaoTest`):
- `observeTransactions(accountId)` lấy dòng nguồn và dòng chuyển khoản đến; bỏ dòng xóa mềm; bỏ dòng tài khoản khác; thứ tự `timestamp DESC, createdAt DESC, id DESC` (có ca trùng cả `timestamp` và `createdAt`).
- `observeTransactions(null)` trả mọi tài khoản, cùng thứ tự.

Screenshot (theo `docs/design-system.md` mục "Screenshot workflow"): danh sách có dữ liệu (nhiều ngày, có chuyển khoản, điều chỉnh), rỗng, lỗi, và `ComingSoonScreen`. Ngày cố định, không phụ thuộc ngày chạy test.

Chạy `testDebugUnitTest`, rồi `qualityGate` qua `gradle-run`. `reviewer` một lần trước khi squash vào `dev`.

## 6. Nâng cấp sang Paging sau này

`shortcut: nạp cả danh sách vào bộ nhớ, ổn tới vài nghìn dòng; chuyển Paging 3 khi danh sách chậm trên máy thật hoặc trước P2 nếu notification sinh nhiều dòng.` Ghi comment này ở `ObserveTransactionListUseCase`.

Khi chuyển: giữ query lọc, mapping dòng, màn, chip. Thay `groupByDay` bằng `insertSeparators` và một query `GROUP BY` ngày cho `net`, viết lại quy tắc mục 3.3 bằng SQL.
