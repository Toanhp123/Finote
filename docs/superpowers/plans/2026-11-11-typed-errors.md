# Typed Errors (R3–R6) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Business failures become typed `Outcome.Err` values decided in the domain, use cases own check-then-write inside `TransactionRunner.write`, and the UI handles storage and unexpected errors through two `BaseViewModel` helpers.

**Architecture:** One feature per branch. Each feature gets `domain/<f>/error/<F>Error.kt` (sealed interface) and pure checks in `domain/<f>/service/<F>Rules.kt`. Use cases read, check, and write inside `tx.write { }` and return `Outcome`. Repositories lose every business `require`; they keep CRUD, the reads the rules need, and data-shape logic (field preservation, balance rebuild). R3 (category) sets the pattern and adds the UI helpers; R4–R6 copy it.

**Tech Stack:** Kotlin, Room 3, Hilt, Konsist, JUnit4, Robolectric (`InMemoryDatabaseRule`), kotlinx-coroutines-test, Turbine.

**Spec:** `docs/superpowers/specs/2026-11-10-clean-architecture-design.md` (sections 2, 3, 4).

**Built on R1/R2 (in `dev`):** `domain/common/{Outcome, TransactionRunner, StorageException}`, `data/database/{RoomTransactionRunner, StorageCalls.kt (storage, storageWrite, mapStorageErrors), SeedTransaction.kt}`. Room 3.0.3 facts: `withWriteTransaction` begins `IMMEDIATE`; a nested `storageWrite` is a SAVEPOINT inside the outer `tx.write`, and an outer `Err` or exception rolls back everything.

## Global Constraints

- Branches, each from `dev`, each squashed into `dev`: R3 `chore/errors-category`, R4 `chore/errors-account`, R5 `chore/errors-transaction`, R6 `chore/errors-notification`. One branch open at a time.
- `Outcome` starts with no helpers. Add `map` or `getOrElse` only when two or more call sites repeat the same `when` (spec 2).
- `require`/`check` stay only for invariants that mean a bug.
- `StorageException`: no message, original as `cause`; nothing shows or logs the cause. Only `data/database` constructs it.
- Only the outermost use case opens `tx.write`. A use case never calls another use case inside `tx.write`; shared steps are `<F>Rules` functions or plain private functions.
- Never write notification content, balances, or account numbers to Logcat. The UI helpers log nothing.
- Money is `Long` VND. No `Double`.
- Tests that asserted `IllegalArgumentException` for a business rule now assert the exact `Outcome.Err`. Every rule moved to the domain gets a JVM test in `<F>RulesTest`.
- Use case tests run on the JVM with `FakeTransactionRunner` (Task R3.1), not Room.
- Gradle only through `gradle-run`. `testDebugUnitTest` after each task; `qualityGate` before each squash. If a build fails with `FileSystemException` on a jar in `app/build`, run `--stop` through `gradle-run` once and retry.
- Commit scopes allowed: `db`, `ui`, `balance`, `notif`, `ota`, or none. No AI attribution.
- Every new Konsist rule gets a red proof (violate, see the named test fail, remove). Never commit the violation.
- Per branch: tasks → `reviewer` on the whole branch → `qualityGate` → ask the user → squash.

## Review Focus

1. A rule check and its write must not race with a background settle: the check runs inside the same `tx.write` as the write → each branch's "transaction" test uses the real `RoomTransactionRunner` + real repository (Tasks R3.2, R4.2, R5.2, R6.3).
2. Release build, unexpected exception in a ViewModel action: the screen reports a failure instead of crashing; debug crashes → Task R3.3 tests both flags.
3. `CancellationException` is never swallowed by `launchCatching` or `catchStorage` → Task R3.3.
4. Merge rejections keep today's order and reasons (same account → gone → different bank → transfer between) → Task R4.1 table test mirrors `AccountMergeTest`.
5. A settle decision that turns out stale (account no longer usable, log no longer pending) leaves the log `PENDING`, writes nothing, and does not stop the other decisions → Tasks R6.2 (use case) and R6.3 (real runner).

---

## R3: `chore/errors-category` (pilot)

### Task R3.1: Category error, rules, and use cases on the runner

**Files:**
- Create: `app/src/main/java/io/github/toanhp123/finote/domain/category/error/CategoryError.kt`
- Create: `app/src/main/java/io/github/toanhp123/finote/domain/category/service/CategoryRules.kt`
- Modify: `domain/category/usecase/{CreateCategoryUseCase, UpdateCategoryUseCase, DeleteCategoryUseCase, SetCategoryHiddenUseCase}.kt`
- Delete: `domain/category/usecase/ValidateCategoryUseCase.kt`
- Modify: `domain/category/repository/CategoryRepository.kt` (add `countLiveChildren`; KDoc no longer promises `IllegalArgumentException`)
- Modify: `data/category/repository/CategoryRepositoryImpl.kt` (drop every business `require`; keep the field-preserving copies)
- Create: `app/src/test/java/io/github/toanhp123/finote/domain/common/FakeTransactionRunner.kt`
- Test: `app/src/test/.../domain/category/service/CategoryRulesTest.kt`; update `domain/category/usecase/*UseCaseTest.kt`, `FakeCategoryRepository.kt`, `data/category/repository/CategoryRepositoryImplTest.kt`

**Interfaces (produces):**

```kotlin
// domain/category/error/CategoryError.kt
sealed interface CategoryError {
    data object NotFound : CategoryError            // missing or soft-deleted
    data object BlankName : CategoryError
    data object TypeChanged : CategoryError
    data object SystemParentChanged : CategoryError
    data object SystemCategory : CategoryError      // delete of a system category
    data object HasChildren : CategoryError
    data object InvalidParent : CategoryError       // self, missing/deleted, not top level, other type
}

// domain/category/service/CategoryRules.kt — pure, null means allowed
object CategoryRules {
    fun checkNew(category: Category, parent: Category?): CategoryError?
    fun checkUpdate(old: Category, new: Category, parent: Category?, liveChildren: Int): CategoryError?
    fun checkDelete(old: Category, liveChildren: Int): CategoryError?
}

// domain/category/repository/CategoryRepository.kt — added
suspend fun countLiveChildren(id: String): Int

// use case signatures
CreateCategoryUseCase:    suspend operator fun invoke(category: Category): Outcome<String, CategoryError>
UpdateCategoryUseCase:    suspend operator fun invoke(category: Category): Outcome<Unit, CategoryError>
DeleteCategoryUseCase:    suspend operator fun invoke(id: String): Outcome<Unit, CategoryError>
SetCategoryHiddenUseCase: suspend operator fun invoke(id: String, hidden: Boolean): Outcome<Unit, CategoryError>  // keep today's params

// test/domain/common/FakeTransactionRunner.kt
class FakeTransactionRunner : TransactionRunner { var writes = 0 }  // runs block, counts calls
```

`parent` is the stored parent looked up by `category.parentId` (null when `parentId` is null *or* the row is missing; `checkNew`/`checkUpdate` return `InvalidParent` when `parentId != null && parent == null`). A parent is valid when it is live, `parentId == null`, same `type`, and `id != category.id`. `checkUpdate` order: `TypeChanged`, `SystemParentChanged` (only when `old.isSystemDefault`), parent check, `HasChildren` (when `new.parentId != null && liveChildren > 0`), `BlankName`. `checkNew`: `BlankName`, then parent. `checkDelete`: `SystemCategory`, then `HasChildren`. Delete of an already soft-deleted row stays a no-op returning `Ok(Unit)`; delete of an unknown id returns `NotFound`.

- [ ] **Step 1: Write failing tests.** `CategoryRulesTest`: one test per error value and one allowed case per function. Use case tests (with `FakeTransactionRunner` and `FakeCategoryRepository`): each former `IllegalArgumentException` assertion becomes `assertEquals(Outcome.Err(CategoryError.X), result)`; each error case asserts the fake stored nothing; each success asserts `runner.writes == 1`.
- [ ] **Step 2:** Run `testDebugUnitTest`. Expected: compile FAIL (unresolved `CategoryError`).
- [ ] **Step 3:** Implement. Each use case: `tx.write { read old/parent/children → CategoryRules → Err or repository write → Ok }`. Repository impl: remove `require`, `requireLive`, `requireValidParent`; `update`/`setHidden` read the old row for field preservation and do nothing when it is missing (the use case already returned `NotFound`). Remove the business tests from `CategoryRepositoryImplTest` (they move to rules/use case tests); keep its CRUD and field-preservation tests.
- [ ] **Step 4:** Run `testDebugUnitTest`. Expected: PASS.
- [ ] **Step 5:** Commit `refactor: category rules return typed errors`.

### Task R3.2: Category check-then-write runs in one transaction

**Files:**
- Test: `app/src/test/.../domain/category/usecase/CategoryUseCaseTransactionTest.kt` (`@RunWith(AndroidJUnit4::class)`, `InMemoryDatabaseRule`, real `RoomTransactionRunner` + `CategoryRepositoryImpl`)

- [ ] **Step 1:** Tests: (a) `delete with a live child returns HasChildren and writes nothing` — row still live after the call. (b) `update into an invalid parent leaves the row unchanged`. (c) `create then read back` — `Ok(id)` and the row exists.
- [ ] **Step 2:** Run `testDebugUnitTest`. Expected: PASS (behavior from R3.1). If any fails, fix in R3.1's code, not in the test.
- [ ] **Step 3:** Commit `test: category use cases on the real transaction runner`.

### Task R3.3: `BaseViewModel` error helpers

**Files:**
- Create: `app/src/main/java/io/github/toanhp123/finote/domain/common/IsDebuggable.kt` (`@Qualifier annotation class IsDebuggable`, same style as `DefaultDispatcher`)
- Modify: `app/src/main/java/io/github/toanhp123/finote/di/` — the module that provides app-wide values: `@Provides @IsDebuggable fun isDebuggable(@ApplicationContext c: Context): Boolean = c.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0`
- Modify: `ui/common/BaseViewModel.kt`; every subclass (`CashEntryViewModel`, `AccountsSettingsViewModel`, `NotificationCaptureSettingsViewModel`, `SettingsViewModel`, `SetupViewModel`, `TransactionListViewModel`) gains `@IsDebuggable isDebuggable: Boolean` and passes it up; their tests pass `isDebuggable = true`.
- Test: `app/src/test/.../ui/common/BaseViewModelTest.kt`
- Modify: `docs/architecture.md` (error handling section: the four kinds from spec section 2, the two helpers, "each screen maps its own `Outcome.Err`")

**Interfaces (produces):**

```kotlin
abstract class BaseViewModel<S, I, E>(initial: S, private val isDebuggable: Boolean) : ViewModel() {
    /** Storage error → [onStorageError]. Other non-cancellation error → rethrow in debug, [onUnexpected] in release. */
    protected fun launchCatching(
        onStorageError: () -> Unit,
        onUnexpected: () -> Unit = onStorageError,
        block: suspend CoroutineScope.() -> Unit,
    ): Job

    /** Same split for a flow; ends the flow like `catch`. [onError] may emit a fallback. */
    protected fun <T> Flow<T>.catchStorage(onError: suspend FlowCollector<T>.() -> Unit): Flow<T>
}
```

- [ ] **Step 1: Write failing tests** with a tiny test subclass: `storage error calls onStorageError`; `unexpected error calls onUnexpected when not debuggable`; `unexpected error is rethrown when debuggable` (assert it reaches the coroutine exception handler / test scope fails); `cancellation is not caught` (cancel the job, neither callback runs); `catchStorage emits fallback on StorageException`; `catchStorage rethrows unexpected when debuggable`; `catchStorage calls onError for unexpected when not debuggable`; `catchStorage lets CancellationException through`.
- [ ] **Step 2:** Run `testDebugUnitTest`. Expected: compile FAIL.
- [ ] **Step 3:** Implement helpers, qualifier, provider, and the constructor change in all six ViewModels. No ViewModel switches to the helpers yet (R4–R6 do).
- [ ] **Step 4:** Run `assembleDebug` (Hilt graph) and `testDebugUnitTest`. Expected: both PASS.
- [ ] **Step 5:** Commit `feat(ui): storage and unexpected error helpers in BaseViewModel`.
- [ ] **Step 6:** `reviewer` on the branch → fixes → `qualityGate` → ask the user → squash `refactor: typed errors for categories`.

---

## R4: `chore/errors-account`

### Task R4.1: Account error, rules, and use cases on the runner

**Files:**
- Create: `domain/account/error/AccountError.kt`; Delete: `domain/account/error/AccountMergeRejectedException.kt` (keep `MergeRejection.kt`)
- Create: `domain/account/service/AccountRules.kt`
- Modify: use cases `CreateAccountUseCase`, `UpdateAccountUseCase`, `ArchiveAccountUseCase`, `DeleteAccountUseCase`, `MergeAccountsUseCase`, `SetAccountIgnoredUseCase`; Delete `ValidateAccountUseCase.kt`
- Modify: `domain/account/repository/AccountRepository.kt` (add `countTransfersBetween`; `merge` no longer checks or throws), `data/account/repository/AccountRepositoryImpl.kt` (drop `require*`, `requireMergeable`, `reject`)
- Test: `domain/account/service/AccountRulesTest.kt`; update account use case tests, `FakeAccountRepository`, `AccountRepositoryImplTest`, `data/account/local/dao/AccountMergeTest.kt`

**Interfaces (produces):**

```kotlin
sealed interface AccountError {
    data object NotFound : AccountError             // missing or soft-deleted
    data object BlankName : AccountError
    data object UnsupportedCurrency : AccountError  // currency != "VND"
    data object HasTransactions : AccountError
    data class MergeRejected(val reason: MergeRejection) : AccountError
}

object AccountRules {
    fun checkNew(account: Account): AccountError?                                   // BlankName, UnsupportedCurrency
    fun checkDelete(liveTransactions: Int): AccountError?                           // HasTransactions
    fun checkMerge(sourceId: String, targetId: String, source: Account?, target: Account?,
                   transfersBetween: Int): MergeRejection?                          // order below
}

// AccountRepository — added
suspend fun countTransfersBetween(sourceId: String, targetId: String): Int

// use cases return Outcome<Unit, AccountError>; CreateAccountUseCase returns Outcome<String, AccountError>
```

`checkMerge` order is today's: `SAME_ACCOUNT` (ids equal), `ACCOUNT_GONE` (either null or deleted), `DIFFERENT_BANK` (source `bankPackageName` null or differs), `HAS_TRANSFER` (`transfersBetween > 0`). `UpdateAccountUseCase` checks `BlankName` only (currency is not editable). Soft delete of an already deleted account stays a no-op `Ok(Unit)`.

- [ ] **Step 1: Write failing tests.** `AccountRulesTest` with a table test for `checkMerge` covering the five `AccountMergeTest` rejection cases. Use case tests assert exact `Outcome.Err` values and that the fake stored nothing on error. In `AccountMergeTest` (DAO level) remove the rejection cases that now live in rules; keep the move/rebuild cases.
- [ ] **Step 2:** Run `testDebugUnitTest`. Expected: compile FAIL.
- [ ] **Step 3:** Implement as in R3.1. `MergeAccountsUseCase`: `tx.write { read both, count transfers, checkMerge → Err(MergeRejected(r)) or repository.merge → Ok }`. Repository `merge` keeps its multi-statement `storageWrite` (joins the outer transaction).
- [ ] **Step 4:** Run `testDebugUnitTest`. Expected: PASS.
- [ ] **Step 5:** Commit `refactor(balance): account rules return typed errors`.

### Task R4.2: Account transaction test and the settings screen

**Files:**
- Test: `domain/account/usecase/AccountUseCaseTransactionTest.kt` (real runner + repository)
- Modify: `ui/settings/accounts/AccountsSettingsViewModel.kt`, its test

- [ ] **Step 1:** Transaction tests: `merge across banks returns MergeRejected(DIFFERENT_BANK) and moves nothing`; `delete with a live transaction returns HasTransactions`.
- [ ] **Step 2:** ViewModel: replace `failureOf` with `launchCatching(onStorageError = { sendEffect(ActionFailed) })` plus a `when` on the `Outcome`: `MergeRejected(HAS_TRANSFER)` → `MergeBlockedByTransfer`, any other `Err` → `ActionFailed`, `Ok` → the success effect. The two count reads use the same helper. `observeAccounts().catch { }` becomes `.catchStorage { }` (list keeps its last state). Effects and their order stay as today; existing ViewModel tests keep their assertions, only the fakes return `Outcome`.
- [ ] **Step 3:** Run `testDebugUnitTest`. Expected: PASS.
- [ ] **Step 4:** Commit `refactor(ui): account settings reads typed errors`.
- [ ] **Step 5:** `reviewer` → fixes → `qualityGate` → ask the user → squash `refactor(balance): typed errors for accounts`.

---

## R5: `chore/errors-transaction`

### Task R5.1: Transaction error, rules, and use cases on the runner

**Files:**
- Create: `domain/transaction/error/TransactionError.kt`, `domain/transaction/service/TransactionRules.kt`
- Delete: `domain/transaction/usecase/ValidateTransactionUseCase.kt` (its constants `MIN_TIMESTAMP_MS = 946_684_800_000L`, `MAX_FUTURE_DAYS = 366L` move to `TransactionRules`)
- Modify: `AddTransactionUseCase`, `AddManualTransactionUseCase`, `UpdateTransactionUseCase`, `DeleteTransactionUseCase`, `ReconcileBalanceUseCase`, `AdjustBalanceDriftUseCase`
- Modify: `data/transaction/repository/TransactionRepositoryImpl.kt` (drop `requireUsableAccounts`, `requireUsableAccount`, the `require`s in `update`/`softDelete`; `ACCOUNT_INACTIVE` goes)
- Test: `domain/transaction/service/TransactionRulesTest.kt`; update the six use case tests, `TransactionRepositoryImplTest`, `TransactionRepositoryAdjustDriftTest`

**Interfaces (produces):**

```kotlin
sealed interface TransactionError {
    data object NotFound : TransactionError            // missing or soft-deleted
    data object InvalidTimestamp : TransactionError
    data object AccountUnusable : TransactionError     // missing, deleted, archived, or ignored
    data object CategoryNotFound : TransactionError
    data object CategoryTypeMismatch : TransactionError
    data object AmountOverflow : TransactionError
}

object TransactionRules {
    const val MIN_TIMESTAMP_MS = 946_684_800_000L
    const val MAX_FUTURE_DAYS = 366L
    fun check(transaction: Transaction, now: Instant, usableAccountIds: Set<String>,
              categoryType: CategoryType?): TransactionError?   // timestamp, accounts, category; today's order
}
```

Use cases return `Outcome<…, TransactionError>` keeping today's success types (`Transaction?` for reconcile/adjust drift, `Unit` otherwise). Inside `tx.write`, a repository write that throws `ArithmeticException` (balance rebuild or `Math.*Exact` on a user amount) is caught there and returned as `Err(AmountOverflow)`; the runner then rolls back. `reconcile`/`adjustDrift` re-check `AccountUnusable` in the use case before the write. The `Math.*Exact` calls in `writeAdjustment` and `BalanceCalculator` stay: they are the overflow source, and the use case maps what they throw.

- [ ] **Step 1: Write failing tests.** `TransactionRulesTest`: each error, boundaries `MIN_TIMESTAMP_MS` and `now + MAX_FUTURE_DAYS`. Use case tests: former IAE assertions become exact `Err`; `amount near Long.MAX that overflows the balance returns AmountOverflow` (fake repository throws `ArithmeticException`).
- [ ] **Step 2:** Run `testDebugUnitTest`. Expected: compile FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Run `testDebugUnitTest`. Expected: PASS.
- [ ] **Step 5:** Commit `refactor(balance): transaction rules return typed errors`.

### Task R5.2: Transaction transaction test and the two screens

**Files:**
- Test: `domain/transaction/usecase/TransactionUseCaseTransactionTest.kt` (real runner + repository)
- Modify: `ui/transactions/TransactionListViewModel.kt`, `ui/cashentry/CashEntryViewModel.kt`, their tests

- [ ] **Step 1:** Transaction tests: `add to an archived account returns AccountUnusable and writes nothing`; `overflowing add returns AmountOverflow and leaves balance and snapshots unchanged`.
- [ ] **Step 2:** ViewModels: replace every `catch (IllegalArgumentException | ArithmeticException | StorageException)` with `launchCatching` + `when` on the `Outcome`, mapping each `Err` to the effect the matching catch produced before; replace `.catch { emit(x) }` with `.catchStorage { emit(x) }`. `AmountExpression` keeps its own `ArithmeticException` handling.
- [ ] **Step 3:** Run `testDebugUnitTest`. Expected: PASS, existing ViewModel assertions unchanged.
- [ ] **Step 4:** Commit `refactor(ui): transaction screens read typed errors`.
- [ ] **Step 5:** `reviewer` → fixes → `qualityGate` → ask the user → squash `refactor(balance): typed errors for transactions`.

---

## R6: `chore/errors-notification`

### Task R6.1: Rule feed port returns `Outcome`

**Files:**
- Modify: `domain/notification/port/ParserRuleFeed.kt`
- Create: `domain/notification/error/RuleFeedError.kt` (the enum, moved); Delete: `domain/notification/error/RuleFeedException.kt`
- Modify: `data/notification/remote/{HttpParserRuleFeed, RuleFeedCodec}.kt` (internal failures use a `private`/`internal` exception in `remote`, mapped at `fetch`)
- Modify: `domain/notification/usecase/UpdateParserRulesUseCase.kt`
- Test: update `HttpParserRuleFeedTest`, `RuleFeedCodecTest`, `UpdateParserRulesUseCaseTest`, `RuleUpdateWorkerTest`

**Interfaces (produces):**

```kotlin
enum class RuleFeedError { NETWORK, INVALID }
interface ParserRuleFeed { suspend fun fetch(): Outcome<List<ParserRule>, RuleFeedError> }
```

`UpdateParserRulesUseCase` maps `Err(NETWORK)` → `RuleUpdateResult.Network`, `Err(INVALID)` → `Invalid`; `StorageException` and any other non-cancellation exception → `Error` (today's behavior, spec 2 table). Reason codes stay internal to `remote` and are never logged.

- [ ] **Step 1:** Update tests: feed tests assert `Err(NETWORK)` / `Err(INVALID)` for each case that asserted `RuleFeedException` with that reason; use case tests unchanged in outcome.
- [ ] **Step 2:** Run `testDebugUnitTest`. Expected: FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Run `testDebugUnitTest`. Expected: PASS.
- [ ] **Step 5:** Commit `refactor(ota): rule feed returns typed errors`.

### Task R6.2: Settlement on the runner

**Files:**
- Create: `domain/notification/error/SettlementError.kt`
- Modify: `domain/notification/repository/NotificationSettlementRepository.kt`, `data/notification/repository/NotificationSettlementRepositoryImpl.kt`, `domain/notification/usecase/SettlePendingLogsUseCase.kt`
- Test: update `SettlePendingLogsUseCaseTest`, `FakeNotificationSettlementRepository` (or the fake in use), `NotificationSettlementRepositoryImplTest`

**Interfaces (produces):**

```kotlin
sealed interface SettlementError {
    data object AccountUnusable : SettlementError   // gone, deleted, archived, ignored
    data object LogNotPending : SettlementError
}
// NotificationSettlementRepository.book(...) : SettlementError?   (null = written)
```

`book` returns the error instead of `check`-throwing; `checkNotNull(bank.parsed)` stays (bug invariant). `SettlePendingLogsUseCase` runs each decision's write in its own `tx.write { }`; on `Err` the runner rolls back and the log stays `PENDING` for the next run, as today. One decision's `Err` does not stop the others. `fail`/`ignore`/`markRepost` keep their `Boolean` results.

- [ ] **Step 1: Write failing tests:** `stale account leaves the log pending and books nothing`; `log settled by another run returns LogNotPending and books nothing`; `one stale decision does not stop the next`.
- [ ] **Step 2:** Run `testDebugUnitTest`. Expected: FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Run `testDebugUnitTest`. Expected: PASS.
- [ ] **Step 5:** Commit `refactor(notif): settlement writes on the transaction runner`.

### Task R6.3: Settlement transaction test, last screens, and the UI rules

**Files:**
- Test: `domain/notification/usecase/SettlementTransactionTest.kt` (real runner + repository)
- Modify: `ui/settings/capture/NotificationCaptureSettingsViewModel.kt`, `ui/settings/SettingsViewModel.kt` (`.catch` → `.catchStorage`)
- Modify: `app/src/test/.../architecture/ProjectRulesKonsistTest.kt` (UI rules), `LayoutKonsistTest.kt` (error folder rule)
- Modify: `docs/architecture.md`

Rules:

| Test | Asserts |
|---|---|
| `ui does not catch broad exceptions` | no file in `UI` except `ui/common/BaseViewModel.kt` and `ui/cashentry/AmountExpression.kt` has `catch (` with `IllegalArgumentException`, `IllegalStateException`, `ArithmeticException`, or `Exception` (code lines) |
| `ui flows catch only through catchStorage` | no file in `UI` except `ui/common/BaseViewModel.kt` contains `.catch {` or imports `kotlinx.coroutines.flow.catch` |
| `error folders hold sealed errors` | every top-level declaration in `domain.<f>.error` is a sealed interface, sealed class, or enum class; any other class there implements a sealed interface/class declared in the same file |

- [ ] **Step 1:** Transaction test: `book with an archived account rolls back the account insert and leaves the log PENDING`.
- [ ] **Step 2:** Convert the two ViewModels.
- [ ] **Step 3:** Add the three rules. Run `testDebugUnitTest`. Expected: PASS.
- [ ] **Step 4: Red proofs:** `catch (_: Exception)` in a ViewModel; `.catch {}` in a ViewModel; `class Boom` in `domain/account/error/`. See each FAIL, remove.
- [ ] **Step 5:** `docs/architecture.md`: the three rules, and the caller table from spec 2 (workers and listener).
- [ ] **Step 6:** Run `qualityGate`. Expected: exit 0.
- [ ] **Step 7:** Commit `test: confine error handling in ui and error folders`.
- [ ] **Step 8:** `reviewer` → fixes → ask the user → squash `refactor(notif): typed errors for notifications`.

---

## After R6

Update the memory note for the upgrade and start P2d on the new pattern.
