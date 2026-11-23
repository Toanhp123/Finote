# Reconciliation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** User nhập số dư thực tế; app ghi một `ADJUSTMENT` bù chênh lệch để `currentBalance` khớp (roadmap P1 việc 9).

**Architecture:** `TransactionRepository.reconcile` đọc tài khoản, tính chênh lệch, insert và rebuild trong một `db.withWriteTransaction`. `ReconcileBalanceUseCase` chỉ sinh `id` và `now` rồi gọi repository.

**Tech Stack:** Kotlin, Room 3 (`androidx.room3`), Hilt, JUnit4, Robolectric (`InMemoryDatabaseRule`), `kotlinx.coroutines.test.runTest`.

**Spec:** `docs/superpowers/specs/2026-11-04-reconciliation-design.md`

## Global Constraints

- Tiền là `Long` VND. Không dùng `Double`. Tràn dùng `Math.subtractExact` / `Math.negateExact` (`ArithmeticException`).
- Mọi thay đổi số dư hoặc snapshot nằm trong một `db.withWriteTransaction`.
- Lỗi nghiệp vụ là `IllegalArgumentException`, thông báo tài khoản: `"account does not exist, is deleted or archived"`.
- Không log số dư, nội dung giao dịch ra Logcat.
- Đặt tên và tầng theo `docs/architecture.md`; Konsist trong `app/src/test/java/io/github/toanhp123/finote/architecture/` phải pass.
- Gradle chạy qua skill `gradle-run`, wrapper `'.\gradlew.bat'` (từ Bash).
- Commit chỉ khi user đồng ý, stage file theo tên, không AI attribution (`docs/git-workflow.md`).

## Review Focus

1. Đối soát hai lần liên tiếp cùng số → lần hai trả `null`, không thêm dòng (test ở Task 1).
2. Tài khoản đã có giao dịch ở tháng trước → chỉ snapshot tháng này đổi, tháng trước giữ nguyên (test ở Task 1).
3. Có giao dịch hẹn ngày tương lai → chênh lệch tính theo `currentBalance` đã gồm giao dịch đó (test ở Task 1).
4. Số thực bằng 0 trên tài khoản dương → `OUT` đúng bằng toàn bộ số dư, `currentBalance = 0` (test ở Task 1).

---

### Task 0: Nhánh

- [ ] **Step 1:** Từ `dev`, `git switch -c feature/reconciliation`. Spec và plan (chưa commit) đi theo working tree.
- [ ] **Step 2 (cần user đồng ý):** commit `docs/superpowers/specs/2026-11-04-reconciliation-design.md` và `docs/superpowers/plans/2026-11-04-reconciliation.md`, message `docs(balance): add reconciliation design and plan`.

### Task 1: `TransactionRepository.reconcile`

**Files:**
- Modify: `app/src/main/java/io/github/toanhp123/finote/domain/transaction/repository/TransactionRepository.kt`
- Modify: `app/src/main/java/io/github/toanhp123/finote/data/transaction/TransactionRepositoryImpl.kt`
- Modify: `app/src/test/java/io/github/toanhp123/finote/domain/transaction/usecase/FakeTransactionRepository.kt`
- Test: `app/src/test/java/io/github/toanhp123/finote/data/transaction/TransactionRepositoryImplTest.kt`

**Interfaces:**
- Produces:
  ```kotlin
  /**
   * Writes one ADJUSTMENT that brings the account's currentBalance to [actualBalance], all in one
   * atomic unit. Returns null and writes nothing when the balance already matches.
   */
  suspend fun reconcile(id: String, accountId: String, actualBalance: Long, now: Long): Transaction?
  ```
- Fake: `data class ReconcileCall(val id: String, val accountId: String, val actualBalance: Long, val now: Long)`; `val reconcileCalls = mutableListOf<ReconcileCall>()`; `var reconcileResult: Transaction? = null`. `reconcile` thêm vào `reconcileCalls`, trả `reconcileResult`. Không đụng `writes`.

Test dùng `clock` sẵn có (2026-11-15 UTC), `acc-1` có số dư 1_000_000, helper `domainTx`, `balanceOf`, `snapshotsOf`. Thêm helper:

```kotlin
private suspend fun liveTxCount() = dbRule.db.transactionDao().observeTransactions().first().size
```

- [ ] **Step 1: Test fail** — thêm vào `TransactionRepositoryImplTest`:

```kotlin
@Test
fun `reconcile above balance writes adjustment in`() = runTest {
    val tx = repo.reconcile("adj-1", "acc-1", 1_300_000L, clock.millis())
    assertEquals(TransactionType.ADJUSTMENT, tx?.type)
    assertEquals(AdjustmentDirection.IN, tx?.adjustmentDirection)
    assertEquals(300_000L, tx?.amount)
    assertEquals(0L, tx?.feeAmount)
    assertNull(tx?.categoryId)
    assertEquals(TransactionSource.MANUAL, tx?.source)
    assertEquals(clock.millis(), tx?.timestamp)
    assertEquals(clock.millis(), tx?.createdAt)
    assertEquals(tx, repo.getTransaction("adj-1"))
    assertEquals(1_300_000L, balanceOf("acc-1"))
    val nov = snapshotsOf("acc-1").single()
    assertEquals(Triple("2026-11", 1_000_000L, 1_300_000L), nov.figures())
    assertEquals(300_000L, nov.totalIncome)
}

@Test
fun `reconcile below balance writes adjustment out`()
// actual 750_000 → OUT 250_000, balance 750_000, snapshot 2026-11 totalExpense 250_000

@Test
fun `reconcile to zero takes the whole balance out`()
// actual 0 → OUT 1_000_000, balance 0

@Test
fun `reconcile negative credit balance`()
// insert account(id = "card", currentBalance = -500_000L); actual -700_000 → OUT 200_000, balance -700_000

@Test
fun `reconcile equal balance writes nothing`()
// actual 1_000_000 → null; liveTxCount() == 0; snapshotsOf("acc-1") empty; balance 1_000_000

@Test
fun `reconcile twice with the same figure writes once`()
// reconcile 1_300_000 ("adj-1") rồi reconcile 1_300_000 ("adj-2") → lần hai null; liveTxCount() == 1; getTransaction("adj-2") null

@Test
fun `reconcile only changes the current month snapshot`()
// repo.add(domainTx()) (EXPENSE 200_000 tháng 2026-09, balance 800_000); reconcile 900_000 → IN 100_000
// chain() == [("2026-09",1_000_000,800_000), ("2026-10",800_000,800_000), ("2026-11",800_000,900_000)]

@Test
fun `reconcile counts future dated transactions in the current balance`()
// repo.add(domainTx(at = "2027-01-10T00:00:00Z")) (balance 800_000); reconcile 1_000_000 → IN 200_000; balance 1_000_000

@Test
fun `reconcile rejects archived, deleted and missing accounts`()
// insert account(id = "arch").copy(isArchived = true), account(id = "gone", deletedAt = 5L)
// với mỗi "arch", "gone", "missing": IllegalArgumentException, message "account does not exist, is deleted or archived"; liveTxCount() == 0

@Test
fun `reconcile overflow in subtraction writes nothing`()
// insert account(id = "one", currentBalance = 1L); actual Long.MIN_VALUE → ArithmeticException
// balanceOf("one") == 1; liveTxCount() == 0; snapshotsOf("one") empty

@Test
fun `reconcile overflow in negation writes nothing`()
// insert account(id = "zero", currentBalance = 0L); actual Long.MIN_VALUE → ArithmeticException
// balanceOf("zero") == 0; liveTxCount() == 0; snapshotsOf("zero") empty

@Test
fun `soft deleting the adjustment restores the old balance`()
// reconcile 1_300_000 ("adj-1"); repo.softDelete("adj-1", clock.millis()) → balance 1_000_000
```

Bắt lỗi theo cách file đang làm (`runCatching { ... }.exceptionOrNull()` rồi `assertTrue(failure is ...)`).

- [ ] **Step 2: Chạy, thấy fail**

`gradle-run`: `testDebugUnitTest --tests "io.github.toanhp123.finote.data.transaction.TransactionRepositoryImplTest"`. Mong đợi: lỗi biên dịch, `reconcile` chưa có.

- [ ] **Step 3: Cài đặt**

- Thêm `reconcile` (KDoc ở Interfaces) vào `TransactionRepository`, sau `add`.
- `TransactionRepositoryImpl.reconcile`: trong `db.withWriteTransaction`:
  1. `val account = db.accountDao().getById(accountId)`; `require(account != null && account.deletedAt == null && !account.isArchived) { "account does not exist, is deleted or archived" }`. Không gọi `requireActiveAccounts`.
  2. `val delta = Math.subtractExact(actualBalance, account.currentBalance)`; `0L` → trả `null`.
  3. Dựng `Transaction(id, amount = if (delta > 0) delta else Math.negateExact(delta), feeAmount = 0, type = ADJUSTMENT, adjustmentDirection = IN/OUT theo dấu, accountId, toAccountId = null, timestamp = now, deletedAt = null, createdAt = now, updatedAt = now)`; các field còn lại để mặc định.
  4. `transactions.insert(tx.toEntity())`; `rebuilder.rebuild(setOf(accountId))`; trả `tx`.
  `withWriteTransaction` phải trả giá trị của block; nếu API của Room 3 không trả, gán vào biến cục bộ trong block.
- Fake: thêm như mục Interfaces.

- [ ] **Step 4: Chạy, thấy pass** — cùng lệnh Step 2. Mong đợi: `exit_status` 0, mọi test pass.

- [ ] **Step 5 (cần user đồng ý): Commit** — stage 4 file trên, message `feat(balance): reconcile account balance with an adjustment`.

### Task 2: `ReconcileBalanceUseCase`

**Files:**
- Create: `app/src/main/java/io/github/toanhp123/finote/domain/transaction/usecase/ReconcileBalanceUseCase.kt`
- Test: `app/src/test/java/io/github/toanhp123/finote/domain/transaction/usecase/ReconcileBalanceUseCaseTest.kt`

**Interfaces:**
- Consumes: `TransactionRepository.reconcile` và fake `reconcileCalls` / `reconcileResult` (Task 1).
- Produces: `class ReconcileBalanceUseCase @Inject constructor(repository: TransactionRepository, clock: Clock)` với `suspend operator fun invoke(accountId: String, actualBalance: Long): Transaction?`. `id = UUID.randomUUID().toString()`, `now = clock.millis()`.

- [ ] **Step 1: Test fail**

```kotlin
class ReconcileBalanceUseCaseTest {
    private val now = Instant.parse("2026-11-04T00:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val repo = FakeTransactionRepository(activeAccounts = setOf(A))
    private val reconcile = ReconcileBalanceUseCase(repo, clock)

    @Test
    fun `passes account, figure and clock time to the repository`() = runTest {
        reconcile(A, 1_500_000L)
        val call = repo.reconcileCalls.single()
        assertEquals(A, call.accountId)
        assertEquals(1_500_000L, call.actualBalance)
        assertEquals(now.toEpochMilli(), call.now)
        UUID.fromString(call.id) // throws if not a UUID
    }

    @Test
    fun `each call gets a fresh id`()
    // gọi 2 lần → hai id khác nhau

    @Test
    fun `returns the repository result`()
    // reconcileResult = tx(TransactionType.EXPENSE, 1) → trả đúng object đó; reconcileResult = null → trả null
}
```

- [ ] **Step 2: Chạy, thấy fail** — `testDebugUnitTest --tests "io.github.toanhp123.finote.domain.transaction.usecase.ReconcileBalanceUseCaseTest"`. Mong đợi: lỗi biên dịch.
- [ ] **Step 3: Cài đặt** — theo Interfaces. KDoc một dòng: không gọi `ValidateTransactionUseCase` vì `amount` chỉ biết sau khi đọc số dư trong repository.
- [ ] **Step 4: Chạy, thấy pass** — cùng lệnh.
- [ ] **Step 5 (cần user đồng ý): Commit** — message `feat(balance): add reconcile balance use case`.

### Task 3: Kiểm toàn nhánh

- [ ] **Step 1:** `gradle-run` `testDebugUnitTest`. Mong đợi: `exit_status` 0.
- [ ] **Step 2:** `gradle-run` `qualityGate`. Mong đợi: `exit_status` 0 (detekt, Spotless, Konsist, screenshot).
- [ ] **Step 3:** Agent `reviewer` một lần trên nhánh, so với spec. Lỗi gửi lại `coder`, sửa xong review lại.
- [ ] **Step 4 (cần user đồng ý):** squash vào `dev` theo `docs/git-workflow.md`.
