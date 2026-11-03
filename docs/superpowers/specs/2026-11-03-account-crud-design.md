# Thiết kế `feature/account-crud`

Ngày: 2026-11-03. Phase: P1 (roadmap mục 3, việc 5). Nguồn: spec mục 3.1 (dòng 62–82); `docs/architecture.md`; review của nhánh `transaction-crud`.

## 1. Mục tiêu

Tạo, sửa, lưu trữ, xóa mềm tài khoản. Đặt `initialBalance` và `isInitialized`. Sửa thêm hai lỗi reviewer nêu ở `transaction-crud`: Clock giữ múi giờ lúc tạo, và tài khoản bị kẹt khi dữ liệu vượt `MAX_SNAPSHOT_MONTHS`.

Đã chốt với user:
- Không UI. Chỉ domain, data, DI, test.
- Lỗi kẹt: biên dãy tháng theo giao dịch sống, không theo snapshot cũ.
- Sửa được `initialBalance` sau khi tạo. Mỗi lần sửa tính lại `currentBalance` và mọi snapshot trong cùng write transaction.
- Tài khoản lưu trữ (`isArchived = true`) không nhận giao dịch mới, không cho sửa giao dịch chạm vào nó. Xóa mềm giao dịch của nó vẫn được.

Ngoài phạm vi: UI; cột `bankPackageName`, `accountNumberMask` (P2); cột thẻ tín dụng `creditLimit`, `statementDay`, `dueDay`; sắp xếp lại `sortOrder`; WorkManager dọn thùng rác.

## 2. Sửa lỗi Clock

Hiện tại `TimeModule.provideClock()` trả `Clock.systemDefaultZone()`. Zone bị chốt lúc tạo. Các lớp giữ Clock lâu (repository) tính tháng snapshot theo múi giờ cũ sau khi user đổi múi giờ.

Sửa: `provideClock()` trả một `Clock` riêng, đặt cùng file `di/TimeModule.kt`:
- `getZone()` trả `ZoneId.systemDefault()` mỗi lần gọi.
- `instant()` trả `Instant.now()`.
- `withZone(zone)` trả `Clock.system(zone)`.

Không đổi nơi dùng: mọi nơi đã đọc `clock.zone` hoặc `YearMonth.now(clock)`.

Test JVM: đặt `TimeZone.setDefault` thành `UTC`, `clock.zone` bằng `UTC`; đổi sang `Asia/Ho_Chi_Minh`, đọc lại → bằng `Asia/Ho_Chi_Minh`. Hai zone cố định, nên zone mặc định của máy test không làm test đúng giả. Khôi phục zone cũ trong `finally`.

## 3. Sửa lỗi tài khoản bị kẹt

Hiện tại `AccountBalanceRebuilder.writeSnapshots` đặt `until = max(thisMonth, tháng snapshot cũ lớn nhất)`. Snapshot không bao giờ bị xóa. Một dòng snapshot tháng hỏng (ví dụ `9999-01`) làm dãy tháng luôn vượt 1200, `monthlySnapshots` luôn ném lỗi, mọi ghi vào tài khoản đó hỏng vĩnh viễn.

Sửa trong `writeSnapshots`:
- `until = YearMonth.now(clock)`. `monthlySnapshots` tự kéo dãy tới tháng giao dịch cuối nếu nó sau tháng này.
- Tháng snapshot cũ không nằm trong dãy tính:
  - trước tháng đầu của dãy → phẳng `initialBalance` (như cũ);
  - sau tháng cuối của dãy → phẳng bằng `closingBalance` tháng cuối: `opening = closing = số dư cuối`, mọi tổng = 0;
  - dãy rỗng (không còn giao dịch sống) → mọi tháng cũ phẳng `initialBalance` (như cũ, vì số dư cuối = `initialBalance`).
- Không xóa dòng snapshot nào.

`monthlySnapshots` giữ nguyên: dãy bắt đầu ở tháng giao dịch đầu. Một giao dịch hỏng duy nhất ở `9999-01` sinh dãy ngắn tới `9999-01`, không ném lỗi, không kẹt. Snapshot đó đúng với dữ liệu. Xóa giao dịch thì dòng `9999-01` thành phẳng bằng số dư cuối.

Kết quả: chỉ giao dịch sống hỏng mới làm vượt trần. Khi đó ghi vẫn ném lỗi, nhưng xóa mềm hoặc sửa chính giao dịch đó chạy được, vì sau thao tác nó không còn trong dãy.

Test Robolectric (`TransactionRepositoryImplTest`):
- Chèn tay snapshot `9999-01` cho tài khoản → thêm giao dịch thành công; dòng `9999-01` có `opening = closing = currentBalance`, tổng = 0.
- Chèn tay qua DAO một giao dịch năm 1900 (bỏ qua validation) → thêm giao dịch khác ném lỗi; xóa mềm giao dịch 1900 thành công và số dư đúng.

## 4. Account CRUD

### 4.1 Model và mapper

- `domain/account/model/Account`: cùng field với `AccountEntity`. Tiền là `Long` VND.
- `data/account/AccountMapper.kt`: `AccountEntity.toDomain()`, `Account.toEntity()`. Test round-trip mọi field.

### 4.2 Repository

`domain/account/repository/AccountRepository` (interface), impl `data/account/AccountRepositoryImpl`, bind trong `di/RepositoryModule`.

Mọi hàm ghi trả `Unit`. `id` là UUID `String` do caller sinh, như `Transaction`.

| Hàm | Hành vi |
|---|---|
| `getAccount(id): Account?` | Đọc một dòng, kể cả đã xóa mềm. |
| `observeAccounts(): Flow<List<Account>>` | Dùng `AccountDao.observeAccounts` (chưa xóa, gồm cả lưu trữ). |
| `add(account)` | Insert, `currentBalance = initialBalance`. Không tạo snapshot. |
| `update(account)` | Một write transaction: đọc dòng cũ (không có hoặc đã xóa → `IllegalArgumentException`). Ghi field mới nhưng giữ `currentBalance`, `createdAt`, `deletedAt`, `isArchived` của dòng cũ. Nếu `initialBalance` đổi thì gọi `AccountBalanceRebuilder.rebuild(setOf(id))`. |
| `setArchived(id, archived, updatedAt)` | Một write transaction: đọc dòng (không có hoặc đã xóa → lỗi), ghi `isArchived` và `updatedAt`. Không rebuild. |
| `softDelete(id, deletedAt)` | Một write transaction, theo thứ tự: không có → lỗi; đã xóa → trả về luôn; `countActiveByAccount(id) > 0` → `IllegalArgumentException`; còn lại ghi `deletedAt` và `updatedAt = deletedAt`. |

`update` không cho đổi `deletedAt` hay `isArchived`: xóa mềm chỉ đi qua `softDelete`, nơi có kiểm tra giao dịch; lưu trữ chỉ đi qua `setArchived`, nên form sửa cũ không bỏ lưu trữ được.

DAO thêm: `TransactionDao.countActiveByAccount(accountId): Int`, câu SQL có ngoặc: `WHERE deletedAt IS NULL AND (accountId = :accountId OR toAccountId = :accountId)`. Test DAO gồm một TRANSFER đã xóa mềm có `toAccountId` là tài khoản đó → đếm 0.

`AccountRepositoryImpl` tạo `AccountBalanceRebuilder` riêng, giống `TransactionRepositoryImpl`.

### 4.3 Use case

Ở `domain/account/usecase`, mỗi lớp một `operator fun invoke`. Lỗi là `IllegalArgumentException` có lý do.

- `ValidateAccountUseCase(account)`: `name.isNotBlank()`, `currency == "VND"`.
- `CreateAccountUseCase(account)`: `createdAt = updatedAt = clock.millis()`, `deletedAt = null`, `isInitialized = true`, `currentBalance = initialBalance`; validate; `repository.add`.
- `UpdateAccountUseCase(account)`: `updatedAt = clock.millis()`, `isInitialized = true`; validate; `repository.update`.
- `ArchiveAccountUseCase(id, archived: Boolean)`: `repository.setArchived(id, archived, clock.millis())`. Đọc và ghi cùng nằm trong một write transaction của repository.
- `DeleteAccountUseCase(id)`: `repository.softDelete(id, clock.millis())`.

### 4.4 Chặn giao dịch vào tài khoản lưu trữ

- `TransactionRepository.getActiveAccountIds`: "active" = tồn tại, `deletedAt == null`, `isArchived == false`. Sửa KDoc.
- Thông báo lỗi trong `ValidateTransactionUseCase` và `TransactionRepositoryImpl.requireActiveAccounts`: `"account does not exist, is deleted or archived"`.
- `TransactionRepositoryImpl.update`: kiểm tài khoản của cả giao dịch cũ lẫn mới, trong write transaction.
- `UpdateTransactionUseCase`: đã đọc `existing` qua `getTransaction`. Thêm kiểm tra `getActiveAccountIds(setOfNotNull(existing.accountId, existing.toAccountId))` chứa đủ; phía mới đã do `ValidateTransactionUseCase` kiểm.
- `softDelete` giao dịch không kiểm tài khoản (như cũ).

## 5. Test

JVM (repository giả, `FakeAccountRepository`):
- Validation: tên rỗng hoặc chỉ khoảng trắng, currency khác VND.
- Create: timestamps từ clock, `isInitialized = true`, `currentBalance = initialBalance`.
- Update, archive, delete: timestamps từ clock.
- `UpdateTransactionUseCaseTest`: giao dịch cũ nằm ở tài khoản lưu trữ, sửa sang tài khoản thường → lỗi ngay ở use case, repository không được gọi.

Robolectric:
- `AccountRepositoryImplTest`:
  - add rồi đọc lại;
  - sửa `initialBalance` khi đã có giao dịch ở nhiều tháng → `currentBalance` và mọi snapshot đổi theo;
  - update giữ `currentBalance`, `createdAt`, `deletedAt` cũ dù input khác;
  - `setArchived`: bật/tắt cờ, `updatedAt` đổi, snapshot không đổi; tài khoản không có hoặc đã xóa → lỗi;
  - xóa mềm khi còn giao dịch (nguồn, hoặc đích của TRANSFER) → lỗi, không ghi gì;
  - xóa mềm khi chỉ còn giao dịch đã xóa, gồm TRANSFER đã xóa có `toAccountId` là tài khoản đó → thành công;
  - rollback: rebuild ném lỗi (ví dụ tràn `Long` do `initialBalance` mới) → dòng tài khoản giữ nguyên.
- `TransactionRepositoryImplTest`: thêm giao dịch vào tài khoản lưu trữ → lỗi; sửa giao dịch ra khỏi tài khoản lưu trữ → lỗi; xóa mềm giao dịch của tài khoản lưu trữ → được.
- Test cũ dựa trên nghĩa cũ của "active" sửa theo.

Chạy `testDebugUnitTest`, rồi `qualityGate` qua `gradle-run`. `reviewer` một lần trước khi squash vào `dev`.
