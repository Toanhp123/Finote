# Thiết kế `feature/reconciliation`

Ngày: 2026-11-04. Phase: P1 (roadmap mục 3, việc 9). Nguồn: spec mục 4 "Điều chỉnh số dư nhanh (Reconciliation)" (dòng 284–287), mục 7 (dòng 411–412); `docs/architecture.md`.

## 1. Mục tiêu

User nhập số dư thực tế của một tài khoản. App tự tạo một giao dịch `ADJUSTMENT` bù đúng phần chênh lệch, để `currentBalance` khớp số thực.

Đã chốt với user:
- Không UI. Chỉ domain, data, test. UI gắn vào sau, ở nhánh màn tài khoản.
- Chênh lệch tính bên trong write transaction, không tính ở use case. Lý do: một ghi xen vào giữa (ví dụ notification parser ở P2) làm số dư đọc trước bị cũ.

Ngoài phạm vi:
- Đối soát lùi ngày ("số dư ngày X là Y"). Số dư ở quá khứ khác `currentBalance`, cần tính khác.
- Cold-start `isInitialized` / gợi ý `initialBalance` từ `postBalance` (Module 8, P2).
- UI, chuỗi hiển thị.

## 2. Hành vi

Đầu vào: `accountId`, `actualBalance: Long` (VND, được âm, ví dụ thẻ tín dụng).

Trong một write transaction:
1. Đọc tài khoản. Không có, đã xóa mềm hoặc đã lưu trữ → `IllegalArgumentException("account does not exist, is deleted or archived")`. Không ghi gì.
2. `delta = Math.subtractExact(actualBalance, currentBalance)`.
3. `delta == 0` → trả `null`, không ghi gì.
4. Còn lại insert một giao dịch:
   - `type = ADJUSTMENT`, `adjustmentDirection = IN` nếu `delta > 0`, `OUT` nếu `delta < 0`;
   - `amount = |delta|` (`Math.negateExact` khi âm);
   - `feeAmount = 0`, `categoryId = null`, `toAccountId = null`, `note = ""`, `source = MANUAL`;
   - `timestamp = createdAt = updatedAt = now`, `deletedAt = null`.
5. `rebuilder.rebuild(setOf(accountId))`. Sau bước này `currentBalance == actualBalance`.
6. Trả giao dịch vừa ghi.

Tràn `Long` ở bước 2 hoặc 4 → `ArithmeticException`, không ghi gì.

`currentBalance` gồm cả giao dịch hẹn ngày tương lai (tối đa 366 ngày, theo `ValidateTransactionUseCase`). Đối soát so với `currentBalance`, không so với "số dư tới hôm nay". Đây là số dư app đang hiện cho user.

Sửa hay xóa bản ghi điều chỉnh đi qua `update` và `softDelete` hiện có. Không thêm gì.

## 3. Thay đổi code

### 3.1 Repository

`TransactionRepository` thêm:

```kotlin
/**
 * Writes one ADJUSTMENT that brings the account's currentBalance to [actualBalance], all in one
 * atomic unit. Returns null and writes nothing when the balance already matches.
 */
suspend fun reconcile(
    id: String,
    accountId: String,
    actualBalance: Long,
    now: Long,
): Transaction?
```

`TransactionRepositoryImpl.reconcile`: theo mục 2, trong `db.withWriteTransaction`. Đọc `db.accountDao().getById(accountId)` một lần. Kiểm `null`, `deletedAt`, `isArchived` trên chính entity đó (cùng thông báo lỗi với `requireActiveAccounts`), rồi lấy `currentBalance` từ nó. Không gọi `requireActiveAccounts`, vì hàm đó đọc lại dòng tài khoản. Dùng lại `transactions.insert`, `rebuilder.rebuild`.

`FakeTransactionRepository` (test) thêm `reconcile`: ghi tham số mỗi lần gọi vào `reconcileCalls` và trả `reconcileResult: Transaction?` đặt sẵn. Fake không giả lập số dư; số dư và chênh lệch test ở Robolectric.

### 3.2 Use case

`domain/transaction/usecase/ReconcileBalanceUseCase`:

```kotlin
suspend operator fun invoke(accountId: String, actualBalance: Long): Transaction? =
    repository.reconcile(UUID.randomUUID().toString(), accountId, actualBalance, clock.millis())
```

Không gọi `ValidateTransactionUseCase`: chưa biết `amount` trước khi đọc số dư, nên chưa dựng được `Transaction`. Timestamp lấy từ clock nên luôn hợp lệ. Kiểm tài khoản nằm trong repository.

## 4. Test

JVM (`ReconcileBalanceUseCaseTest`, repository giả):
- Truyền đúng `accountId`, `actualBalance`, `now = clock.millis()`, `id` là UUID mới mỗi lần gọi.
- Trả nguyên kết quả của repository, kể cả `null`.

Robolectric (`TransactionRepositoryImplTest`):
- Số thực lớn hơn → một `ADJUSTMENT IN`, `amount` đúng, `currentBalance == actualBalance`, snapshot tháng này có `totalIncome` tăng đúng.
- Số thực nhỏ hơn → `ADJUSTMENT OUT`, `totalExpense` tăng đúng.
- Bằng nhau → trả `null`, không có dòng giao dịch mới, snapshot không đổi.
- Tài khoản âm (thẻ tín dụng): `currentBalance = -500_000`, thực `-700_000` → `OUT 200_000`.
- Tài khoản lưu trữ, đã xóa mềm, không tồn tại → lỗi, không ghi gì.
- Tràn `Long`, hai ca. Mỗi ca: `ArithmeticException`, `currentBalance`, bảng giao dịch và snapshot giữ nguyên.
  - Tràn ở bước 2 (`subtractExact`): `currentBalance = 1`, `actualBalance = Long.MIN_VALUE`.
  - Tràn ở bước 4 (`negateExact`): `currentBalance = 0`, `actualBalance = Long.MIN_VALUE`. Bước 2 cho `delta = Long.MIN_VALUE`, đổi dấu thì tràn.
- Xóa mềm bản ghi điều chỉnh → `currentBalance` về lại số cũ.

Chạy `testDebugUnitTest`, rồi `qualityGate` qua `gradle-run`. `reviewer` một lần trước khi squash vào `dev`.
