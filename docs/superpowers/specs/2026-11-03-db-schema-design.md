# Thiết kế `feature/db-schema`

Ngày: 2026-11-03. Phase: P1 (roadmap mục 3, việc 2). Nguồn: spec mục 3.1–3.4, 3.9; `docs/architecture.md`.

## 1. Mục tiêu

Tạo tầng data Room 3 cho 6 bảng của P1. App có `FinoteDatabase` qua Hilt, DB chưa mã hóa. Nhánh `db-encryption` làm sau, chỉ đổi driver.

Đã chốt với user:
- Nhánh này làm trước `db-encryption`. Cập nhật thứ tự trong `docs/roadmap.md` mục 3 và mục 10.
- Chỉ tạo cột P1 cần. Cột của phase sau thêm bằng cách tăng version DB. Trước v1.0 được tạo lại DB, không cần migration.
- Chỉ làm data + DI, cộng các enum ở `domain/<f>/model` (mục 4). Domain model, mapper, repository làm ở nhánh CRUD.
- Test DAO bằng Robolectric, chạy trong `testDebugUnitTest`.

Ngoài phạm vi: domain model, use case, query nghiệp vụ (snapshot theo tháng, lọc), seed danh mục, mã hóa, FTS, CHECK constraint.

## 2. Build

Thêm qua `gradle/libs.versions.toml`, không hardcode version:
- `androidx.room3:room3-runtime` và `androidx.room3:room3-compiler` (`ksp`), version `3.0.3`.
- Plugin `androidx.room3`, cùng version. Cấu hình `room3 { schemaDirectory("$projectDir/schemas") }`.
- `androidx.sqlite:sqlite-framework` cho `AndroidSQLiteDriver`. Lấy version mà Room 3.0.3 yêu cầu (đọc từ dependency tree khi làm).
- Commit `app/schemas/` (file JSON của version 1).

## 3. Bảng

Quy ước chung:
- `id`: `String` UUID, là khóa chính.
- Thời gian: `Long`, epoch millis.
- Tiền: `Long`, VND.
- Enum: lưu dạng TEXT theo tên. Room 3 tự đổi enum. Nếu không đổi được thì dùng `@ColumnTypeConverter`.
- `monthYear`: `String` dạng `YYYY-MM`.
- Không dùng CHECK constraint. Validation (amount > 0, `adjustmentDirection` đi cùng `ADJUSTMENT`, danh mục cùng type) nằm ở domain, trong nhánh CRUD.

### 3.1 `accounts` — `AccountEntity`

| Cột | Kiểu | Ghi chú |
|---|---|---|
| id | String | PK |
| name | String | |
| type | `AccountType` | BANK, WALLET, CASH, SAVINGS, CREDIT_CARD |
| initialBalance | Long | |
| currentBalance | Long | Có dấu. CREDIT_CARD âm khi đang nợ |
| currency | String | Luôn `"VND"` ở MVP |
| icon | String | |
| color | Int | |
| sortOrder | Int | |
| excludeFromNetWorth | Boolean | |
| isArchived | Boolean | |
| isInitialized | Boolean | |
| createdAt, updatedAt | Long | |
| deletedAt | Long? | |

Chưa tạo: `bankPackageName`, `accountNumberMask` (P2); `creditLimit`, `statementDay`, `dueDay` (P4).

### 3.2 `categories` — `CategoryEntity`

| Cột | Kiểu | Ghi chú |
|---|---|---|
| id | String | PK |
| name | String | |
| nameKey | String? | Khóa chuỗi cho danh mục hệ thống. Null khi user đổi tên |
| icon | String | |
| color | Int | |
| type | `CategoryType` | EXPENSE, INCOME |
| parentId | String? | FK → `categories.id`, `RESTRICT`, index |
| isSystemDefault | Boolean | |
| sortOrder | Int | |
| createdAt, updatedAt | Long | |
| deletedAt | Long? | |

### 3.3 `transactions` — `TransactionEntity`

Tên bảng là `transactions`, vì `Transaction` là từ khóa SQL.

| Cột | Kiểu | Ghi chú |
|---|---|---|
| id | String | PK |
| amount | Long | > 0 (domain kiểm) |
| feeAmount | Long | Mặc định 0. Luôn trừ vào `accountId` |
| type | `TransactionType` | EXPENSE, INCOME, TRANSFER, ADJUSTMENT, REFUND |
| adjustmentDirection | `AdjustmentDirection`? | IN, OUT. Chỉ có khi ADJUSTMENT |
| accountId | String | FK → `accounts.id`, `RESTRICT` |
| toAccountId | String? | FK → `accounts.id`, `RESTRICT`. Bắt buộc khi TRANSFER |
| categoryId | String? | FK → `categories.id`, `RESTRICT`, index |
| timestamp | Long | |
| merchantName | String? | |
| note | String | |
| source | `TransactionSource` | MANUAL, NOTIFICATION_PARSER, RECURRING, IMPORT. Bỏ ADJUSTMENT so với spec (user chốt 2026-11-03): source chỉ nói ai tạo giao dịch, còn `type` đã nói đây là điều chỉnh. Đối soát do user bấm nên có source = MANUAL |
| excludeFromAnalytics | Boolean | |
| reimbursementStatus | `ReimbursementStatus` | NONE, PENDING, SETTLED. Không null, mặc định NONE |
| createdAt, updatedAt | Long | |
| deletedAt | Long? | |

Index:
- `(accountId, timestamp)`: danh sách lọc theo tài khoản. Không thêm index đơn `accountId`, vì composite đã có `accountId` đứng đầu.
- `(toAccountId, timestamp)`: lịch sử một tài khoản gồm cả TRANSFER nhận vào (`accountId = ? OR toAccountId = ?`). SQLite dùng hai index này cho phép `OR`. Thay cho index đơn `toAccountId`.
- `timestamp`: danh sách mọi tài khoản sắp `DESC`.

`reimbursementStatus` không null, khác với spec (`Enum?`). Lý do: spec đã có giá trị NONE, nếu cho null thì có hai cách nói "không có".

Chưa tạo: `isSplitParent`, `parentTransactionId` (P6); `originalAmount`, `originalCurrency`, `exchangeRate` (P6); `refundForTransactionId` (P4); `postBalance`, `notificationLogId` (P2); `attachmentUri` (P6). Vì chưa có `isSplitParent`, test của `balance-engine` bỏ trường hợp "bỏ qua giao dịch cha đã tách".

### 3.4 `tags` — `TagEntity`

| Cột | Kiểu |
|---|---|
| id | String, PK |
| name | String |
| color | Int |
| isArchived | Boolean |
| createdAt, updatedAt | Long |
| deletedAt | Long? |

### 3.5 `transaction_tag_cross_ref` — `TransactionTagCrossRef`

- PK `(transactionId, tagId)`.
- `transactionId` FK → `transactions.id`, `CASCADE`.
- `tagId` FK → `tags.id`, `CASCADE`, index. `transactionId` đã nằm đầu PK nên không cần index riêng.

### 3.6 `account_balance_snapshots` — `AccountBalanceSnapshotEntity`

| Cột | Kiểu | Ghi chú |
|---|---|---|
| id | String | PK |
| accountId | String | FK → `accounts.id`, `CASCADE` |
| monthYear | String | Unique `(accountId, monthYear)` |
| openingBalance, closingBalance | Long | |
| totalIncome, totalExpense | Long | |
| totalTransferIn, totalTransferOut | Long | |
| updatedAt | Long | |

Unique index `(accountId, monthYear)` cũng phục vụ truy vấn theo `accountId`.

Snapshot không cần thêm cột. Spec 3.9 (bổ sung 2026-11-02) đã chốt cách gộp:
- `totalIncome` = INCOME + REFUND + ADJUSTMENT IN.
- `totalExpense` = EXPENSE + mọi `feeAmount` (kể cả phí của TRANSFER) + ADJUSTMENT OUT.
- `totalTransferIn` / `totalTransferOut` = `amount` của TRANSFER nhận vào / chuyển đi, không gồm phí.

Với cách này, `closing = opening + totalIncome − totalExpense + totalTransferIn − totalTransferOut` luôn đúng. Nhánh `balance-engine` kiểm đẳng thức này.

## 4. Package

Theo `docs/architecture.md`, mỗi file một public class:

```text
domain/account/model/      AccountType
domain/category/model/     CategoryType
domain/transaction/model/  TransactionType, AdjustmentDirection,
                           TransactionSource, ReimbursementStatus
data/FinoteDatabase.kt
data/account/      AccountEntity, AccountDao,
                   AccountBalanceSnapshotEntity, AccountBalanceSnapshotDao
data/category/     CategoryEntity, CategoryDao
data/transaction/  TransactionEntity, TransactionDao,
                   TagEntity, TagDao, TransactionTagCrossRef
di/DatabaseModule.kt
```

Enum đặt luôn ở `domain/<f>/model`. Enum là Kotlin thuần, `data` được phép phụ thuộc `domain`, nên nhánh CRUD không phải sửa import.

## 5. DAO

Tối thiểu, đủ để test schema. Query nghiệp vụ thêm ở nhánh CRUD.

Mỗi DAO của bảng có id:
- `suspend fun insert(entity)`: `@Insert` với mặc định `OnConflictStrategy.ABORT`. Không dùng `REPLACE` hay `@Upsert`: `REPLACE` xóa rồi chèn lại, sẽ kích hoạt CASCADE và làm hỏng dữ liệu con.
- `suspend fun update(entity)`.
- `suspend fun getById(id): Entity?` (trả cả dòng đã xóa mềm).
- Một hàm `observeX(): Flow<List<Entity>>`, có lọc và có `ORDER BY` cố định:

| DAO | Hàm | Lọc | Sắp xếp |
|---|---|---|---|
| Account | `observeAccounts()` | `deletedAt IS NULL` | `sortOrder ASC, createdAt ASC` |
| Category | `observeCategories()` | `deletedAt IS NULL` | `sortOrder ASC, createdAt ASC` |
| Transaction | `observeTransactions()` | `deletedAt IS NULL` | `timestamp DESC, createdAt DESC` |
| Tag | `observeTags()` | `deletedAt IS NULL` | `name ASC` |
| Snapshot | `observeByAccount(accountId)` | (không có `deletedAt`) | `monthYear ASC` (theo thời gian, cho biểu đồ) |

Cột thứ hai trong `ORDER BY` giữ thứ tự cố định khi hai dòng trùng giá trị cột đầu.

Bảng CrossRef: `TransactionDao` có `insertTagRef` (`ABORT`), `deleteTagRef` và `getTagRefs(transactionId)`. CrossRef không có `deletedAt`. Gỡ tag khỏi giao dịch là xóa cứng dòng CrossRef, và app được phép làm việc này.

Bốn bảng có xóa mềm (`accounts`, `categories`, `transactions`, `tags`) không có hàm xóa cứng trong DAO. Test xóa cứng chúng bằng SQL thô. Trong app, chỉ WorkManager dọn rác (P4) mới được xóa cứng.

Ghi xuyên nhiều DAO dùng `FinoteDatabase.withWriteTransaction { }` của Room 3. Nhánh này chỉ dùng nó trong test rollback. Nhánh CRUD dùng thật.

## 6. Database và DI

- `@Database(entities = [6 entity], version = 1, exportSchema = true)`.
- `di/DatabaseModule` (`@InstallIn(SingletonComponent)`):
  - `FinoteDatabase` singleton: `Room.databaseBuilder<FinoteDatabase>(context, "finote.db").setDriver(AndroidSQLiteDriver()).build()`.
  - Một hàm `@Provides` cho mỗi DAO.
- Nhánh `db-encryption` chỉ đổi `setDriver(...)` thành `SQLCipherDriver`.

## 7. Test

Robolectric (`@RunWith(AndroidJUnit4::class)`), DB in-memory với `AndroidSQLiteDriver`, `runTest`.

Task đầu tiên của plan là một test nhỏ: mở DB in-memory, insert một account, đọc lại. Nếu Robolectric không chạy được driver này, dừng lại và báo user trước khi đổi cách.

Các test:
1. Mỗi bảng: insert rồi `getById` trả đúng mọi cột, kể cả enum và cột null.
2. FK:
   - Insert giao dịch có `accountId` không tồn tại thì lỗi.
   - Xóa cứng account còn giao dịch thì lỗi (`RESTRICT`).
   - Xóa category cha còn con thì lỗi.
3. Unique `(accountId, monthYear)`: chèn hai snapshot trùng thì lỗi.
4. CASCADE:
   - Xóa cứng tag thì dòng CrossRef mất.
   - Xóa cứng giao dịch thì dòng CrossRef mất.
   - Xóa cứng account không còn giao dịch thì snapshot của nó mất.
5. Xóa mềm: `observeX` bỏ qua dòng có `deletedAt`, còn `getById` vẫn trả về dòng đó. `observeX` đúng thứ tự `ORDER BY` ở mục 5.
6. Rollback: trong `withWriteTransaction`, insert account rồi ném lỗi. Sau đó account không tồn tại.

Mỗi test tạo DB in-memory mới. `db.close()` trong `@After`.

Fixture dùng tên và số tiền giả.

## 8. DoD

- `testDebugUnitTest` pass, có đủ 6 nhóm test trên.
- `qualityGate` pass.
- Luật Konsist `@Suppress` không báo nhầm code KSP (Room, Hilt). Konsist đọc `src/main`, không đọc `build/generated`, nên chỉ cần gate xanh sau khi Room sinh code.
- `app/schemas/.../1.json` đã commit.
- `docs/roadmap.md`: `db-schema` đứng trước `db-encryption`; ghi chú dòng 6 (catalog chưa có Robolectric, Turbine) đã sửa.

## 9. Rủi ro

- Robolectric có thể không chạy `AndroidSQLiteDriver`. Task đầu của plan kiểm việc này.
- Room 2 tự đổi enum sang TEXT theo tên. Release notes Room 3 không nói về enum. Task đầu của plan xác nhận việc này. Nếu Room 3 không tự đổi, thêm `@ColumnTypeConverter`.
- Room cảnh báo khi cột FK không có index đứng đầu. Chưa rõ Room có tính khóa chính tổ hợp `(transactionId, tagId)` là index của `transactionId` không. `allWarningsAsErrors` của Kotlin không chặn cảnh báo của KSP, nên phải đọc log build. Nếu Room cảnh báo, thêm index `transactionId` một cách tường minh.
- SQLite chỉ kiểm FK khi `PRAGMA foreign_keys = ON`. Test 2 xác nhận Room bật pragma này. Nếu Room không bật, thêm callback bật khi mở kết nối.

## 10. Ghi chú khi làm

Kết quả của các rủi ro ở mục 9:
- Robolectric chạy được `AndroidSQLiteDriver`.
- Room 3 tự đổi enum sang TEXT. Không cần `@ColumnTypeConverter`.
- Room bật FK mà không cần callback pragma.
- Room không cảnh báo thiếu index cho khóa chính của CrossRef. Không thêm index `transactionId`.

Điều chỉnh khi làm:
- Code Kotlin do Room 3 sinh vi phạm `allWarningsAsErrors` và `extraWarnings`. `app/build.gradle.kts` tắt `REDUNDANT_VISIBILITY_MODIFIER` và `CAN_BE_VAL` bằng `-Xwarning-level`. detekt vẫn quét code viết tay. Chỉ thêm cờ mới khi code sinh ra cần.
- Thuộc tính Boolean phải có tiền tố is/has/should (luật Konsist). `@ColumnInfo(name = ...)` giữ tên cột theo spec: `isExcludedFromNetWorth` → `excludeFromNetWorth`, `isExcludedFromAnalytics` → `excludeFromAnalytics`.
- Transaction ghi của Room 3 là `androidx.room3.withWriteTransaction`. Receiver của lambda là `TransactionScope`, nên gọi DAO bên trong phải đi qua tham chiếu database.
- TRANSFER có `toAccountId` null: DB không chặn. Nhánh `transaction-crud` kiểm trong domain.
