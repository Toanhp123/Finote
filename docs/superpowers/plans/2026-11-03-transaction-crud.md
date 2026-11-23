# Transaction CRUD Plan

**Goal:** Thêm, sửa, xóa mềm giao dịch. Mỗi thao tác là một transaction DB cập nhật `currentBalance` và `AccountBalanceSnapshot` của mọi tài khoản bị ảnh hưởng (roadmap P1 mục 4, spec 3.3, 3.9, mục 7).

**Spec:** dòng 66–67, 99–121, 208–218, 412–416.

## Thiết kế

- **Tính lại cả tài khoản, không cộng dồn.** Mỗi lần ghi: với mỗi tài khoản bị chạm (cũ và mới: `accountId`, `toAccountId`), đọc mọi giao dịch chưa xóa của tài khoản, gọi `BalanceCalculator.balance` và `monthlySnapshots`, ghi kết quả. Đúng theo định nghĩa, tự xử lý đổi tháng, đổi tài khoản, đổi loại.
  - `shortcut: rebuild từ tháng đầu, O(số giao dịch của tài khoản) mỗi lần ghi; chuyển sang tính từ tháng bị sửa khi đo thấy chậm ở P5`.
- **Snapshot không xóa cứng.** Đọc snapshot cũ của tài khoản, giữ `id` theo `monthYear`: tháng có trong kết quả thì `update` hoặc `insert` mới; tháng cũ không còn trong kết quả (đứng trước giao dịch đầu tiên) thì ghi phẳng `opening = closing = initialBalance`, tổng = 0. `untilMonth = max(tháng hiện tại, tháng snapshot cũ cuối cùng)` để cuối dãy không bị hụt. Không `REPLACE`, không `@Upsert` (theo plan db-schema).
- **Nguyên tử:** `TransactionRepositoryImpl` bọc mọi bước trong một write transaction của Room 3 (`useWriterConnection { immediateTransaction { … } }`, coder kiểm API thật).
- **Model:** mở rộng `domain/transaction/model/Transaction` thêm `categoryId`, `merchantName`, `note`, `source`, `isExcludedFromAnalytics`, `reimbursementStatus`, `createdAt`, `updatedAt`. Thêm `TransactionMapper.kt` (`toDomain`, `toEntity`). Sửa `TransactionFixtures.kt` theo.
- **Use case** (`domain/transaction/usecase`): `AddTransactionUseCase`, `UpdateTransactionUseCase`, `DeleteTransactionUseCase` (xóa mềm). `TransactionRepository` interface ở `domain/transaction/repository`. Bind trong `di/`.
- **Validation** (use case, trước khi ghi):
  - Tài khoản nguồn và đích tồn tại, chưa xóa mềm.
  - `categoryId` nếu có: EXPENSE chỉ gắn danh mục EXPENSE, INCOME chỉ gắn INCOME.
  - Giới hạn `timestamp` (xem Cần chốt).
- **Giới hạn vòng lặp:** `monthlySnapshots` `require` số tháng ≤ `MAX_SNAPSHOT_MONTHS`. Là chốt chặn cuối khi DB đã có dữ liệu hỏng.

## Task

1. `BalanceCalculator`: trần số tháng + test (ném lỗi khi vượt, đúng trần thì chạy).
2. Mở rộng model `Transaction`, mapper, fixtures. Test mapper round-trip.
3. DAO: `getActiveByAccount(accountId)` (gồm cả `toAccountId`), `AccountBalanceSnapshotDao.getByAccount`. Test DAO.
4. `TransactionRepository` + impl + rebuild snapshot. Test Robolectric:
   - thêm → `currentBalance` và snapshot đúng;
   - sửa số tiền; đổi timestamp sang tháng khác (tháng cũ và mọi tháng sau đổi theo);
   - đổi `accountId` → cả hai tài khoản đúng; TRANSFER cập nhật cả nguồn và đích;
   - xóa mềm → không tính; xóa giao dịch đầu tiên → tháng đầu thành phẳng, không xóa dòng;
   - rollback: số tiền gây tràn `Long` ở bước tính → ném `ArithmeticException`, giao dịch không được lưu, số dư và snapshot giữ nguyên.
5. Use case + validation + test JVM (repository giả).
6. `testDebugUnitTest`, rồi `qualityGate` qua `gradle-run`. `reviewer` một lần trước squash.

Không làm ở nhánh này: UI, tag gắn vào giao dịch, `isSplitParent` (chưa có cột, P6), REFUND liên kết (P4).

## Đã chốt (2026-11-03)

| Mã | Câu hỏi | Quyết định |
|---|---|---|
| T1 | Khoảng `timestamp` hợp lệ khi ghi | Từ `2000-01-01` tới `now + 366 ngày` |
| T2 | `MAX_SNAPSHOT_MONTHS` | 1200 (100 năm) |
