# Account CRUD Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Tạo, sửa, lưu trữ, xóa mềm tài khoản, và sửa hai lỗi reviewer (Clock chốt múi giờ, tài khoản kẹt khi vượt `MAX_SNAPSHOT_MONTHS`).

**Architecture:** Domain `Account` + `AccountRepository` (interface) + 5 use case ở `domain/account`. `AccountRepositoryImpl` ở `data/account` bọc mỗi ghi trong `db.withWriteTransaction` và dùng lại `AccountBalanceRebuilder`. Tài khoản lưu trữ bị loại khỏi `getActiveAccountIds` nên luồng giao dịch tự chặn.

**Tech Stack:** Kotlin, Room 3 (`androidx.room3`), Hilt, JUnit4, Robolectric (`InMemoryDatabaseRule`), `kotlinx.coroutines.test.runTest`.

**Spec:** `docs/superpowers/specs/2026-11-03-account-crud-design.md`

## Global Constraints

- Tiền là `Long` VND. Không dùng `Double`.
- `currency` chỉ nhận `"VND"`.
- Xóa mềm qua `deletedAt`. Không xóa cứng dòng nào, kể cả snapshot.
- Mọi thay đổi số dư hoặc snapshot nằm trong một `db.withWriteTransaction`.
- Lỗi nghiệp vụ là `IllegalArgumentException` (`require`/`requireNotNull`).
- Không log nội dung tài khoản, số dư ra Logcat.
- Đặt tên và tầng theo `docs/architecture.md`; Konsist trong `app/src/test/java/io/github/toanhp123/finote/architecture/` phải pass.
- Gradle chạy qua skill `gradle-run`, wrapper `'.\gradlew.bat'` (từ Bash).
- Commit chỉ khi user đồng ý, stage file theo tên, không AI attribution (`docs/git-workflow.md`).

## Review Focus

1. Tài khoản lưu trữ làm **đích** của TRANSFER mới → phải bị chặn (test ở Task 7).
2. Sửa `initialBalance` khi tài khoản chưa có giao dịch → `currentBalance = initialBalance` mới, không sinh snapshot (test ở Task 5).
3. `initialBalance` âm (thẻ tín dụng đang nợ) → hợp lệ, validation không chặn (test ở Task 6).
4. `update` tài khoản đã bị xóa mềm (stale UI) → lỗi, dòng không đổi (test ở Task 5).
5. User đổi múi giờ giữa phiên → tháng snapshot tính theo zone mới mà không cần khởi động lại (test ở Task 1).

---

### Task 0: Nhánh

- [ ] **Step 1:** Từ `dev`, `git switch -c feature/account-crud`. Spec và plan này (chưa commit) đi theo working tree.
- [ ] **Step 2 (cần user đồng ý):** commit `docs/superpowers/specs/2026-11-03-account-crud-design.md` và `docs/superpowers/plans/2026-11-03-account-crud.md`, message `docs(account): add account crud design and plan`.

### Task 1: Clock đọc múi giờ mỗi lần gọi

**Files:**
- Modify: `app/src/main/java/io/github/toanhp123/finote/di/TimeModule.kt`
- Test: `app/src/test/java/io/github/toanhp123/finote/di/SystemZoneClockTest.kt`

**Interfaces:**
- Produces: `internal class SystemZoneClock : Clock()` trong `TimeModule.kt`; `provideClock(): Clock = SystemZoneClock()`.

- [ ] **Step 1: Test fail**

```kotlin
class SystemZoneClockTest {
    @Test
    fun `zone follows the current system default`() {
        val saved = TimeZone.getDefault()
        try {
            val clock = SystemZoneClock()
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            assertEquals(ZoneId.of("UTC"), clock.zone)
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"))
            assertEquals(ZoneId.of("Asia/Ho_Chi_Minh"), clock.zone)
            assertEquals(YearMonth.now(ZoneId.of("Asia/Ho_Chi_Minh")), YearMonth.now(clock))
        } finally {
            TimeZone.setDefault(saved)
        }
    }

    @Test
    fun `withZone pins the given zone`() {
        assertEquals(ZoneOffset.UTC, SystemZoneClock().withZone(ZoneOffset.UTC).zone)
    }
}
```

- [ ] **Step 2:** `testDebugUnitTest --tests "*SystemZoneClockTest"` → FAIL (chưa có lớp).
- [ ] **Step 3:** Viết `SystemZoneClock`: `getZone() = ZoneId.systemDefault()`, `instant() = Instant.now()`, `withZone(zone) = Clock.system(zone)`. KDoc một dòng: vì sao không dùng `Clock.systemDefaultZone()` (chốt zone lúc tạo).
- [ ] **Step 4:** Chạy lại → PASS.
- [ ] **Step 5 (cần user đồng ý):** commit `fix(time): read the system zone on every clock call`.

### Task 2: Snapshot không còn kéo dãy theo tháng snapshot cũ

**Files:**
- Modify: `app/src/main/java/io/github/toanhp123/finote/data/account/AccountBalanceRebuilder.kt` (`writeSnapshots`, `flat`, KDoc lớp)
- Test: `app/src/test/java/io/github/toanhp123/finote/data/transaction/TransactionRepositoryImplTest.kt`

**Interfaces:**
- Consumes: `BalanceCalculator.monthlySnapshots` (không đổi).
- Produces: hành vi mới của `AccountBalanceRebuilder.rebuild` (signature không đổi).

- [ ] **Step 1: Test fail** (thêm vào `TransactionRepositoryImplTest`, clock cố định 2026-11)

```kotlin
@Test
fun `a far future stored snapshot no longer blocks writes`() = runTest {
    dbRule.db.accountBalanceSnapshotDao().insert(
        AccountBalanceSnapshotEntity("snap-far", "acc-1", "9999-01", 1, 1, 0, 0, 0, 0, 1L),
    )
    repo.add(domainTx()) // 200k expense in 2026-09
    val far = snapshotsOf("acc-1").single { it.monthYear == "9999-01" }
    assertEquals(Triple("9999-01", 800_000L, 800_000L), far.figures())
    assertEquals(listOf(0L, 0L, 0L, 0L), listOf(far.totalIncome, far.totalExpense, far.totalTransferIn, far.totalTransferOut))
    assertEquals("snap-far", far.id)
}

@Test
fun `a corrupt live transaction can still be deleted`() = runTest {
    dbRule.db.transactionDao().insert(domainTx(id = "tx-old", at = "1900-01-10T00:00:00Z").toEntity())
    assertThrows(IllegalArgumentException::class.java) { runBlocking { repo.add(domainTx(id = "tx-new")) } }
    repo.softDelete("tx-old", deletedAt = 2L)
    assertEquals(1_000_000L, balanceOf("acc-1"))
}
```

(Kiểm thứ tự field của `AccountBalanceSnapshotEntity` trước khi dùng constructor theo vị trí; dùng named args nếu khác. Dùng `assertThrows` theo cách test hiện có trong file.)

- [ ] **Step 2:** Chạy `--tests "*TransactionRepositoryImplTest"` → test 1 FAIL (`IllegalArgumentException` từ trần tháng).
- [ ] **Step 3:** Sửa `writeSnapshots`:
  - `until = YearMonth.now(clock)` (bỏ `stored.keys.max`).
  - Tháng cũ không có trong `computed`: trước tháng đầu của `computed` → `flat(account, month, account.initialBalance)`; sau tháng cuối → `flat(account, month, computed.last().closingBalance)`; `computed` rỗng → `initialBalance`.
  - `flat(account, monthYear, balance: Long)` thay cho bản cũ.
  - Sửa KDoc lớp: mô tả cả tháng phẳng sau dãy.
- [ ] **Step 4:** Chạy cả `TransactionRepositoryImplTest` → PASS, test cũ không đổi kết quả.
- [ ] **Step 5 (cần user đồng ý):** commit `fix(balance): bound snapshot range by live transactions`.

### Task 3: Đếm giao dịch sống của tài khoản

**Files:**
- Modify: `app/src/main/java/io/github/toanhp123/finote/data/transaction/TransactionDao.kt`
- Test: `app/src/test/java/io/github/toanhp123/finote/data/transaction/TransactionDaoTest.kt`

**Interfaces:**
- Produces: `suspend fun countActiveByAccount(accountId: String): Int`, SQL `SELECT COUNT(*) FROM transactions WHERE deletedAt IS NULL AND (accountId = :accountId OR toAccountId = :accountId)`.

- [ ] **Step 1: Test fail** `countActiveByAccount counts source and destination and skips deleted`:
  - acc-1: một EXPENSE sống (nguồn) → `1`.
  - acc-2: một TRANSFER acc-1 → acc-2 sống → acc-2 đếm `1`; xóa mềm TRANSFER đó (đặt `deletedAt`) → acc-2 đếm `0`, acc-1 vẫn `1` (EXPENSE).
- [ ] **Step 2:** Chạy `--tests "*TransactionDaoTest"` → FAIL (chưa có hàm).
- [ ] **Step 3:** Thêm query trên.
- [ ] **Step 4:** Chạy lại → PASS.
- [ ] **Step 5 (cần user đồng ý):** commit `feat(db): count live transactions of an account`.

### Task 4: Model `Account` và mapper

**Files:**
- Create: `app/src/main/java/io/github/toanhp123/finote/domain/account/model/Account.kt`
- Create: `app/src/main/java/io/github/toanhp123/finote/data/account/AccountMapper.kt`
- Test: `app/src/test/java/io/github/toanhp123/finote/data/account/AccountMapperTest.kt`

**Interfaces:**
- Produces: `data class Account(id: String, name: String, type: AccountType, initialBalance: Long, currentBalance: Long, currency: String, icon: String, color: Int, sortOrder: Int, isExcludedFromNetWorth: Boolean, isArchived: Boolean, isInitialized: Boolean, createdAt: Long, updatedAt: Long, deletedAt: Long?)`; `fun AccountEntity.toDomain(): Account`; `fun Account.toEntity(): AccountEntity`.

- [ ] **Step 1: Test fail** `round-trips every field`: một `AccountEntity` với mọi field khác mặc định (`isArchived = true`, `deletedAt = 5L`, `initialBalance = -300_000L`, `type = CREDIT_CARD`) → `entity.toDomain().toEntity() == entity`.
- [ ] **Step 2:** Chạy → FAIL.
- [ ] **Step 3:** Viết model (KDoc: tiền VND, `currentBalance` có dấu) và mapper giống `TransactionMapper.kt`.
- [ ] **Step 4:** Chạy → PASS.
- [ ] **Step 5 (cần user đồng ý):** commit `feat(account): add account domain model and mapper`.

### Task 5: `AccountRepository` và impl

**Files:**
- Create: `app/src/main/java/io/github/toanhp123/finote/domain/account/repository/AccountRepository.kt`
- Create: `app/src/main/java/io/github/toanhp123/finote/data/account/AccountRepositoryImpl.kt`
- Modify: `app/src/main/java/io/github/toanhp123/finote/di/RepositoryModule.kt` (thêm `bindAccountRepository`)
- Test: `app/src/test/java/io/github/toanhp123/finote/data/account/AccountRepositoryImplTest.kt`

**Interfaces:**
- Consumes: Task 3 `countActiveByAccount`, Task 4 mapper, `AccountBalanceRebuilder(accounts, transactions, snapshots, clock)`.
- Produces:
  ```kotlin
  interface AccountRepository {
      suspend fun getAccount(id: String): Account?
      fun observeAccounts(): Flow<List<Account>>
      suspend fun add(account: Account)
      suspend fun update(account: Account)
      suspend fun setArchived(id: String, archived: Boolean, updatedAt: Long)
      suspend fun softDelete(id: String, deletedAt: Long)
  }
  class AccountRepositoryImpl @Inject constructor(db: FinoteDatabase, clock: Clock) : AccountRepository
  ```

Hành vi từng hàm: bảng ở spec mục 4.2. `add` ghi `currentBalance = initialBalance`. `update` giữ `currentBalance`, `createdAt`, `deletedAt` cũ; rebuild chỉ khi `initialBalance` đổi.

- [ ] **Step 1: Test fail** (Robolectric, `InMemoryDatabaseRule`, clock cố định `2026-11-15T00:00:00Z` UTC; tạo giao dịch qua `TransactionRepositoryImpl` cùng DB):
  - `add then get returns the account with currentBalance equal to initialBalance`
  - `changing initialBalance rebuilds balance and every snapshot`: initial 1_000_000, EXPENSE 200k ở 2026-09, INCOME 50k ở 2026-10 → update initial 2_000_000 → `currentBalance == 1_850_000`; chain `[("2026-09", 2_000_000, 1_800_000), ("2026-10", 1_800_000, 1_850_000), ("2026-11", 1_850_000, 1_850_000)]`.
  - `changing initialBalance without transactions sets the balance and writes no snapshot`
  - `update keeps currentBalance createdAt and deletedAt of the stored row`: input có `currentBalance = 1`, `createdAt = 99`, `deletedAt = 7` → đọc lại thấy giá trị cũ, `deletedAt == null`.
  - `update of a soft-deleted account fails and changes nothing`
  - `setArchived flips the flag and leaves snapshots alone`; `setArchived on a missing or deleted account fails`
  - `softDelete with a live source or transfer destination transaction fails and writes nothing`
  - `softDelete succeeds when only deleted transactions remain, including a deleted transfer into it`
  - `softDelete twice is a no-op`
  - `failed rebuild rolls back the account row`: có INCOME 50k, update `initialBalance = Long.MAX_VALUE` → `ArithmeticException`; dòng tài khoản y như trước.
- [ ] **Step 2:** Chạy `--tests "*AccountRepositoryImplTest"` → FAIL.
- [ ] **Step 3:** Viết interface (KDoc như `TransactionRepository`: mọi ghi nguyên tử) và impl theo spec mục 4.2, mẫu `TransactionRepositoryImpl`. Bind trong `RepositoryModule`.
- [ ] **Step 4:** Chạy → PASS.
- [ ] **Step 5 (cần user đồng ý):** commit `feat(account): add account repository with atomic rebuild`.

### Task 6: Use case tài khoản

**Files:**
- Create trong `app/src/main/java/io/github/toanhp123/finote/domain/account/usecase/`: `ValidateAccountUseCase.kt`, `CreateAccountUseCase.kt`, `UpdateAccountUseCase.kt`, `ArchiveAccountUseCase.kt`, `DeleteAccountUseCase.kt`
- Create test trong `app/src/test/java/io/github/toanhp123/finote/domain/account/usecase/`: `FakeAccountRepository.kt`, `CreateAccountUseCaseTest.kt`, `UpdateAccountUseCaseTest.kt`, `ArchiveAccountUseCaseTest.kt`, `DeleteAccountUseCaseTest.kt`

**Interfaces:**
- Consumes: Task 5 `AccountRepository`, `Clock`.
- Produces (mỗi lớp `@Inject constructor`):
  - `ValidateAccountUseCase()`: `operator fun invoke(account: Account)`
  - `CreateAccountUseCase(repository, validate, clock)`: `suspend operator fun invoke(account: Account)`
  - `UpdateAccountUseCase(repository, validate, clock)`: `suspend operator fun invoke(account: Account)`
  - `ArchiveAccountUseCase(repository, clock)`: `suspend operator fun invoke(id: String, archived: Boolean)`
  - `DeleteAccountUseCase(repository, clock)`: `suspend operator fun invoke(id: String)`
- `FakeAccountRepository`: `stored: MutableMap<String, Account>`, `writes: Int`, theo mẫu `FakeTransactionRepository`.

- [ ] **Step 1: Test fail** (clock cố định, `now = clock.millis()`):
  - Create: `blank name is rejected` (`""` và `"   "`), `non VND currency is rejected` (`"USD"`), không ghi (`writes == 0`); `valid account is stored with clock timestamps, isInitialized true, currentBalance equal to initialBalance, deletedAt null`; `negative initialBalance is accepted` (`-300_000L`).
  - Update: `updatedAt is stamped and isInitialized becomes true`; validation như Create.
  - Archive: gọi `setArchived(id, true, now)` và `(id, false, now)`.
  - Delete: gọi `softDelete(id, now)`.
- [ ] **Step 2:** Chạy `--tests "io.github.toanhp123.finote.domain.account.usecase.*"` → FAIL.
- [ ] **Step 3:** Viết 5 lớp theo spec mục 4.3. Thông báo lỗi: `"name must not be blank"`, `"only VND is supported"`.
- [ ] **Step 4:** Chạy → PASS.
- [ ] **Step 5 (cần user đồng ý):** commit `feat(account): add account use cases with validation`.

### Task 7: Chặn giao dịch vào tài khoản lưu trữ

**Files:**
- Modify: `app/src/main/java/io/github/toanhp123/finote/domain/transaction/repository/TransactionRepository.kt` (KDoc `getActiveAccountIds`)
- Modify: `app/src/main/java/io/github/toanhp123/finote/data/transaction/TransactionRepositoryImpl.kt` (`getActiveAccountIds`, `update`, `requireActiveAccounts`)
- Modify: `app/src/main/java/io/github/toanhp123/finote/domain/transaction/usecase/ValidateTransactionUseCase.kt` (thông báo)
- Modify: `app/src/main/java/io/github/toanhp123/finote/domain/transaction/usecase/UpdateTransactionUseCase.kt`
- Test: `TransactionRepositoryImplTest.kt`, `UpdateTransactionUseCaseTest.kt`, và test nào đang assert thông báo cũ

**Interfaces:**
- `getActiveAccountIds`: tồn tại, `deletedAt == null`, `!isArchived`.
- Thông báo chung: `"account does not exist, is deleted or archived"`.
- `TransactionRepositoryImpl.update`: `requireActiveAccounts` trên `old.accountIds() + entity.accountIds()`.
- `UpdateTransactionUseCase`: sau khi đọc `existing`, `require(repository.getActiveAccountIds(old).containsAll(old))` với `old = setOfNotNull(existing.accountId, existing.toAccountId)`, trước `validate`.

- [ ] **Step 1: Test fail**
  - Repo (đặt `isArchived = true` qua `accountDao().update`):
    - `adding to an archived account fails`
    - `a transfer into an archived account fails` (đích lưu trữ)
    - `moving a transaction out of an archived account fails`: tx ở acc-1, rồi archive acc-1, update sang acc-2 → lỗi, số dư hai tài khoản không đổi.
    - `deleting a transaction of an archived account works`: số dư acc-1 về 1_000_000.
  - Use case: `moving a transaction out of an archived account is rejected before writing`: `FakeTransactionRepository(activeAccounts = setOf("acc-2"))`, `stored` có tx ở acc-1 → update sang acc-2 ném lỗi, `writes == 0`.
- [ ] **Step 2:** Chạy `--tests "*TransactionRepositoryImplTest" --tests "*UpdateTransactionUseCaseTest"` → FAIL.
- [ ] **Step 3:** Sửa theo Interfaces. Cập nhật test cũ assert chuỗi `"account does not exist or is deleted"`.
- [ ] **Step 4:** Chạy lại → PASS.
- [ ] **Step 5 (cần user đồng ý):** commit `feat(transaction): block writes into archived accounts`.

### Task 8: Kiểm tra toàn nhánh

- [ ] **Step 1:** `testDebugUnitTest` qua `gradle-run` → `exit_status` 0.
- [ ] **Step 2:** `qualityGate` qua `gradle-run` → `exit_status` 0, đọc summary (detekt, Spotless, Konsist).
- [ ] **Step 3:** Agent `reviewer` một lần trên cả nhánh so với spec. Lỗi tìm được gửi `coder` sửa, chạy lại Step 1–2.
- [ ] **Step 4 (cần user đồng ý):** squash vào `dev`: `feat(account): add account crud with archive and soft delete`.
