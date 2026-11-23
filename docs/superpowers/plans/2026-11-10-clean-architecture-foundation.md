# Clean Architecture Foundation (R1 + R2) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give `domain` and `data` one folder shape per feature (R1), then route every Room transaction and storage error through one shared place (R2).

**Architecture:** R1 is a pure move: packages change, behavior does not. R2 adds `Outcome` and the `TransactionRunner` port to `domain/common`, and a `data/database` package that owns `withWriteTransaction` and the `SQLException` → `StorageException` mapping. Repositories switch to the shared helpers. Typed business errors (R3–R6) get their own plan after R2 lands.

**Tech Stack:** Kotlin, Room 3 (`androidx.room3`, `androidx.sqlite`), Hilt, Konsist, JUnit4, Robolectric (`AndroidJUnit4` + `InMemoryDatabaseRule`), kotlinx-coroutines-test.

**Spec:** `docs/superpowers/specs/2026-11-10-clean-architecture-design.md`

## Global Constraints

- Start only after cleanup branch 6 (`chore/test-hygiene`) is squashed into `dev`, and with no other work branch open (R1 moves many files).
- Branches: `chore/package-layout` (R1), then `chore/data-database` (R2), each from `dev`, each squashed into `dev`.
- Move files with `git mv` so history follows them. Tests move with the code they test.
- R1 changes no behavior: existing tests pass with only `package` and `import` edits, no assertion edits.
- `StorageException` keeps no message and keeps the original exception as `cause`. Nothing shows or logs the cause.
- Catch `android.database.SQLException`, never `java.sql.SQLException`.
- Never write notification content, balances, or account numbers to Logcat.
- Commits: ask the user before every commit; the `coder` subagent never commits. Allowed scopes: `db`, `build`, or none. No AI attribution in commit messages.
- Run Gradle through the `gradle-run` skill: `testDebugUnitTest` after logic changes, `qualityGate` before the squash.
- Every new Konsist rule needs a red proof: add a deliberate violation, run the rule, see it fail, record the output in the task notes, remove the violation. Never commit the violation.

## Review Focus

1. Room reads previous schemas from `app/schemas/<database FQN>/`. Moving `FinoteDatabase` without moving that folder breaks the auto-migration build or exports a fresh schema → Task 2 moves the folder and checks git sees a rename.
2. An existing install must open its encrypted database after the update: the file name and identity hash must not change → Task 2 checks `7.json` is byte-identical after the build.
3. An inner `storageWrite` failure inside an outer `tx.write` must roll back the outer writes too, not only the inner ones → Task 6 test 4.
4. A Flow from Room that fails while being collected reaches the collector as `StorageException`, not raw `SQLException` → Task 5 tests per repository.
5. A seeder that fails midway on `onOpen` leaves no partial rows, so the next open seeds again → Task 7 test.

---

## R1: `chore/package-layout`

### Task 1: Branch and spec commit

**Files:**
- Add: `docs/superpowers/specs/2026-11-10-clean-architecture-design.md`, `docs/superpowers/plans/2026-11-10-clean-architecture-foundation.md`

- [ ] **Step 1:** `git switch dev && git pull --ff-only` (if a remote exists), then `git switch -c chore/package-layout`.
- [ ] **Step 2:** Ask the user, then commit: `docs: clean architecture spec and foundation plan`.

### Task 2: Move domain and data packages

**Files (production, under `app/src/main/java/io/github/toanhp123/finote/`):**

| From | To |
|---|---|
| `domain/account/{BalanceAnchor,BalanceCalculator,BalanceDriftDetector}.kt` | `domain/account/service/` |
| `domain/account/model/{AccountMergeRejectedException,MergeRejection}.kt` | `domain/account/error/` |
| `domain/notification/{AccountResolver,NotificationClassifier,NotificationParser,NotificationText,SettlementPlanner}.kt` | `domain/notification/service/` |
| `domain/notification/model/RuleFeedException.kt` (holds `RuleFeedError` too) | `domain/notification/error/` |
| `domain/notification/repository/{ParserRuleFeed,RuleUpdateScheduler,SettleScheduler}.kt` | `domain/notification/port/` |
| `data/{FinoteDatabase,FinoteDatabaseFactory}.kt` | `data/database/` |
| `data/account/{AccountBalanceSnapshotDao,AccountBalanceSnapshotEntity,AccountDao,AccountEntity,AccountMergeDao}.kt` | `data/account/local/` |
| `data/account/AccountMapper.kt` | `data/account/mapper/` |
| `data/account/{AccountRepositoryImpl,AccountBalanceRebuilder}.kt` | `data/account/repository/` |
| `data/account/SystemAccountSeeder.kt` | `data/account/seed/` |
| `data/category/{CategoryDao,CategoryEntity}.kt` | `data/category/local/` |
| `data/category/CategoryMapper.kt` | `data/category/mapper/` |
| `data/category/CategoryRepositoryImpl.kt` | `data/category/repository/` |
| `data/category/{SystemCategory,SystemCategorySeeder}.kt` | `data/category/seed/` |
| `data/notification/{NotificationLogDao,NotificationLogEntity,ParserRuleDao,ParserRuleEntity}.kt` | `data/notification/local/` |
| `data/notification/{NotificationLogMapper,ParserRuleMapper}.kt` | `data/notification/mapper/` |
| `data/notification/{NotificationCaptureRepositoryImpl,NotificationSettlementRepositoryImpl,ParserRuleRepositoryImpl,RuleUpdateSettingsRepositoryImpl}.kt` | `data/notification/repository/` |
| `data/notification/{ParserRuleSeeder,SystemParserRule}.kt` | `data/notification/seed/` |
| `data/notification/{HttpParserRuleFeed,RuleFeedCodec}.kt` | `data/notification/remote/` |
| `data/setup/SetupRepositoryImpl.kt` | `data/setup/repository/` |
| `data/transaction/{TagDao,TagEntity,TransactionDao,TransactionEntity,TransactionTagCrossRef}.kt` | `data/transaction/local/` |
| `data/transaction/TransactionMapper.kt` | `data/transaction/mapper/` |
| `data/transaction/TransactionRepositoryImpl.kt` | `data/transaction/repository/` |

Unchanged: `domain/common/`, `data/security/`, every `model/`, `repository/`, `usecase/` file not listed.

**Files (schemas):** `app/schemas/io.github.toanhp123.finote.data.FinoteDatabase/` → `app/schemas/io.github.toanhp123.finote.data.database.FinoteDatabase/`.

**Files (tests, `app/src/test/java/io/github/toanhp123/finote/` and `app/src/androidTest/...`):** each test moves to the package of the class it tests:

| Test | To |
|---|---|
| `domain/account/{BalanceAnchorTest,BalanceCalculatorTest,BalanceDriftTest,MonthlySnapshotsTest}.kt` | `domain/account/service/` |
| `domain/notification/{AccountResolverTest,NotificationClassifierTest,NotificationParserTest,NotificationTextTest,SettlementPlannerTest,NotificationCorpusTest}.kt` | `domain/notification/service/` |
| `data/{FinoteDatabaseTransactionTest,InMemoryDatabaseRule,Fixtures}.kt`, androidTest `data/EncryptedDatabaseTest.kt` | `data/database/` |
| `*DaoTest`, `AccountMergeTest` | `data/<f>/local/` |
| `*MapperTest` | `data/<f>/mapper/` |
| `*RepositoryImplTest`, `*Repository*Test`, `TransactionTestSupport` | `data/<f>/repository/` |
| `*SeederTest`, `SystemCategoriesTest`, `SystemCategoryResourcesTest`, `SystemParserRules*Test` | `data/<f>/seed/` |
| `HttpParserRuleFeedTest`, `RuleFeedCodecTest`, `CommittedRuleFeedTest`, `RuleFeedSigningTool`, `RuleFeedTestSigner` | `data/notification/remote/` |
| `data/setup/SetupRepositoryImplTest.kt` | `data/setup/repository/` |

Fixtures and fakes not listed (`AccountFixtures`, `TransactionFixtures`, `NotificationCorpusFixtures`, `NotificationFixtures`, `TestCategory`, `Fake*`) stay where they are.

**Interfaces:** Produces the package names above. Later tasks import `io.github.toanhp123.finote.data.database.FinoteDatabase` and `io.github.toanhp123.finote.data.database.InMemoryDatabaseRule`.

- [ ] **Step 1:** `git mv` every production file in the table, then the schema folder, then the tests.
- [ ] **Step 2:** Rewrite each moved file's `package` line to its new folder.
- [ ] **Step 3:** Fix imports across `main`, `test`, `androidTest`, and `di/`. For each moved top-level symbol, replace `old.package.Symbol` with `new.package.Symbol` (top-level functions too, for example `domain.notification.parseNotification`, `domain.account.detectBalanceDrift`, `domain.notification.notificationContent`). Files that shared a package before the split and no longer do (for example `AccountRepositoryImpl` using `AccountDao`, mapper `toDomain`/`toEntity` extensions) now need explicit imports. Let the compiler list what is left.
- [ ] **Step 4:** Run `assembleDebug` through `gradle-run`. Expected: `exit_status` 0.
- [ ] **Step 5:** Check the schema move: `git status --porcelain app/schemas` shows only `R` (rename) lines and no new `??` folder, and `git diff --stat dev -- app/schemas` shows 0 line changes for `7.json`.
- [ ] **Step 6:** Run `testDebugUnitTest` through `gradle-run`. Expected: `exit_status` 0, and `git diff dev --stat -- app/src/test` shows no changed lines other than `package` and `import` lines (`git diff dev -- app/src/test | grep '^[+-]' | grep -vE '^(\+\+\+|---|[+-](package|import) )'` prints nothing).
- [ ] **Step 7:** Ask the user, then commit: `refactor: move domain and data into role folders`.

### Task 3: Layout Konsist rules

**Files:**
- Modify: `app/src/test/java/io/github/toanhp123/finote/architecture/NamingKonsistTest.kt`
- Create: `app/src/test/java/io/github/toanhp123/finote/architecture/LayoutKonsistTest.kt`
- Modify: `app/src/test/java/io/github/toanhp123/finote/architecture/KonsistScopes.kt` (constants only)

**Interfaces:**
- Produces in `KonsistScopes.kt`: `DOMAIN_COMMON = "$ROOT.domain.common"`, `DATA_DATABASE = "$ROOT.data.database"`, `DATA_SECURITY = "$ROOT.data.security"`, `DOMAIN_ROLES = setOf("model", "error", "repository", "port", "service", "usecase")`, `DATA_ROLES = setOf("local", "mapper", "repository", "seed", "remote")`.

Rules (each `strict = false`, testName = the backticked name):

| Test | Asserts |
|---|---|
| `domain feature files sit in a role folder` | every file in `domain.<f>` (not `domain.common`) has package `domain.<f>.<role>` with `<role>` in `DOMAIN_ROLES` |
| `data feature files sit in a role folder` | every file in `data.<f>` (not `data.database`, `data.security`) has package `data.<f>.<role>` with `<role>` in `DATA_ROLES` |
| `models declare no throwables` | no top-level class in a `domain.<f>.model` package has a parent whose name ends with `Exception` or `Throwable` |
| `ports are interfaces` | every top-level declaration in `domain.<f>.port` is a `KoInterfaceDeclaration` |
| `services do not reach repositories or ports` | files in `domain.<f>.service` import nothing whose name has segment `repository` or `port` |
| `data roles hold their suffixes` | `*Entity`, `*Dao`, `TransactionTagCrossRef` types in `data.<f>.local`; `*Mapper.kt` files in `data.<f>.mapper`; `*RepositoryImpl` types in `data.<f>.repository`; `*Seeder` types in `data.<f>.seed` |

Change in `NamingKonsistTest`: `repository package holds only interfaces` also requires the interface name to end with `Repository`. Rename the test to `repository package holds only repository interfaces`.

- [ ] **Step 1:** Write `LayoutKonsistTest` with the six rules and the `NamingKonsistTest` change.
- [ ] **Step 2:** Run `testDebugUnitTest` through `gradle-run`. Expected: PASS.
- [ ] **Step 3: Red proofs.** For each rule, one violation, run, see FAIL, record the failing test name, remove: a file `domain/account/Loose.kt`; a file `data/account/Loose.kt`; `class Boom : Exception()` in `domain/account/model/`; `class Port` in `domain/notification/port/`; an `import ...domain.account.repository.AccountRepository` in a `service` file; `AccountDao` moved back to `data/account/repository/`; `interface Feed` in `domain/notification/repository/`.
- [ ] **Step 4:** Run `testDebugUnitTest` again with all violations removed. Expected: PASS.
- [ ] **Step 5:** Ask the user, then commit: `test: enforce role folders in domain and data`.

### Task 4: Architecture doc

**Files:**
- Modify: `docs/architecture.md` (sections "Layers", "Folder rules", "Naming and roles")
- Modify: `CLAUDE.md` only if a path it names moved (none expected)

- [ ] **Step 1:** Replace the `domain/<feature>/` and `data/<feature>/` lines in the layer tree with the spec's section 1 tree. State that folder rules 2 and 3 apply to `ui` only, and that `domain/<f>` and `data/<f>` use the fixed role folders, created only when a file of that role exists.
- [ ] **Step 2:** In "Naming and roles": pure logic `NotificationParser` → `domain/<f>/service`; repository impl → `data/<f>/repository`; entity and DAO → `data/<f>/local`; mapper → `data/<f>/mapper`; add rows for port (`ParserRuleFeed`, `domain/<f>/port`, interface only), error (`domain/<f>/error`), seeder (`data/<f>/seed`), remote (`data/<f>/remote`). Replace the paragraph that lets `repository` hold other ports with: `repository/` holds only `*Repository` interfaces; other outward interfaces go in `port/`.
- [ ] **Step 3:** Run `qualityGate` through `gradle-run`. Expected: `exit_status` 0.
- [ ] **Step 4:** Ask the user, then commit: `docs: role folders for domain and data`.
- [ ] **Step 5:** `reviewer` checks the branch against spec section 1. Fix findings through `coder`, re-review, then ask the user before `git switch dev && git merge --squash chore/package-layout` and the squash commit `refactor: role folders for domain and data`.

---

## R2: `chore/data-database`

### Task 5: `Outcome`, ports, and storage calls

**Files:**
- Create: `app/src/main/java/io/github/toanhp123/finote/domain/common/Outcome.kt`
- Create: `app/src/main/java/io/github/toanhp123/finote/domain/common/TransactionRunner.kt`
- Create: `app/src/main/java/io/github/toanhp123/finote/data/database/StorageCalls.kt`
- Test: `app/src/test/java/io/github/toanhp123/finote/data/database/StorageCallsTest.kt`

**Interfaces (produces):**
```kotlin
// domain/common/Outcome.kt — exactly as spec section 2, no helpers
sealed interface Outcome<out T, out E> {
    data class Ok<T>(val value: T) : Outcome<T, Nothing>
    data class Err<E>(val error: E) : Outcome<Nothing, E>
}

// domain/common/TransactionRunner.kt
interface TransactionRunner {
    /** One write transaction. An Err result or an exception rolls everything back. */
    suspend fun <T, E> write(block: suspend () -> Outcome<T, E>): Outcome<T, E>
}

// data/database/StorageCalls.kt
suspend fun <T> storage(block: suspend () -> T): T
suspend fun <T> FinoteDatabase.storageWrite(block: suspend () -> T): T
fun <T> Flow<T>.mapStorageErrors(): Flow<T>
```
Each maps `android.database.SQLException` to `StorageException(cause)` and lets every other exception through. `storageWrite` is `storage { withWriteTransaction { block() } }`. `mapStorageErrors` uses `catch { if (it is SQLException) throw StorageException(it) else throw it }`. None of them starts a scope, `launch`, or `async`.

- [ ] **Step 1: Write failing tests** in `StorageCallsTest` (`@RunWith(AndroidJUnit4::class)`, `InMemoryDatabaseRule`):
  - `storage maps SQLException` — `dbRule.execSql("DROP TABLE accounts")`, then `storage { db.accountDao().getById("a") }` fails with `StorageException` whose `cause is android.database.SQLException`.
  - `storage lets other exceptions through` — `storage { error("boom") }` fails with `IllegalStateException`, message `"boom"`.
  - `storageWrite rolls back on failure` — inside `db.storageWrite { insert account "acc-1"; error("boom") }`; afterwards `getById("acc-1")` is null.
  - `flow maps SQLException` — drop `accounts`, then `db.accountDao().observeAccounts().mapStorageErrors().first()` (use the DAO's actual observe function name) fails with `StorageException`.
- [ ] **Step 2:** Run `testDebugUnitTest` through `gradle-run`. Expected: FAIL, unresolved `storage`.
- [ ] **Step 3:** Create the three files with the signatures above.
- [ ] **Step 4:** Run `testDebugUnitTest`. Expected: PASS.
- [ ] **Step 5:** Ask the user, then commit: `feat(db): shared storage calls and transaction port`.

### Task 6: `RoomTransactionRunner`

**Files:**
- Create: `app/src/main/java/io/github/toanhp123/finote/data/database/RoomTransactionRunner.kt`
- Modify: `app/src/main/java/io/github/toanhp123/finote/di/RepositoryModule.kt` (add `@Binds fun bindTransactionRunner(impl: RoomTransactionRunner): TransactionRunner`)
- Test: `app/src/test/java/io/github/toanhp123/finote/data/database/RoomTransactionRunnerTest.kt`
- Delete: `app/src/test/java/io/github/toanhp123/finote/data/database/FinoteDatabaseTransactionTest.kt` (its case becomes test 2 below)

**Interfaces:**
- Consumes: `Outcome`, `TransactionRunner`, `storageWrite` (Task 5).
- Produces: `class RoomTransactionRunner @Inject constructor(private val db: FinoteDatabase) : TransactionRunner`.

Approach: `write` creates one private `RollbackSignal(val outcome: Outcome.Err<*>) : Throwable(null, null, false, false)` per `Err`, throws it from inside `db.storageWrite { … }`, catches only that instance (`e === signal`) outside, and returns its `outcome`. A KDoc line on the class records what Task 9 finds about `IMMEDIATE`.

- [ ] **Step 1: Write failing tests** (`InMemoryDatabaseRule`, real `RoomTransactionRunner(dbRule.db)`):
  1. `err rolls back writes` — block inserts account `acc-1`, returns `Outcome.Err("no")`; result is `Outcome.Err("no")`; `getById("acc-1")` is null.
  2. `exception rolls back and is rethrown` — block inserts `acc-1`, throws `IllegalStateException("boom")`; the call fails with that exception; `acc-1` is null.
  3. `sql failure becomes StorageException` — block inserts `acc-1` twice (same primary key); the call fails with `StorageException` whose `cause is android.database.SQLException`; `acc-1` is null.
  4. `inner storageWrite joins the outer transaction`, two cases. (a) Block inserts `acc-1`, then `db.storageWrite { insert acc-2 }`, then returns `Outcome.Err("no")`; both `acc-1` and `acc-2` are null. (b) Block inserts `acc-1`, then `db.storageWrite { insert acc-2; error("inner") }` uncaught; the call fails with `"inner"`; both are null. If either case fails on Room 3, change `storageWrite` to skip opening a transaction when one is active (`inTransaction()`), as spec section 3 allows, and note it in the test's KDoc.
  5. `ok commits` — block inserts `acc-1`, returns `Outcome.Ok(1)`; result `Outcome.Ok(1)`; `acc-1` exists.
- [ ] **Step 2:** Run `testDebugUnitTest`. Expected: FAIL, unresolved `RoomTransactionRunner`.
- [ ] **Step 3:** Implement `RoomTransactionRunner` and the `@Binds`.
- [ ] **Step 4:** Run `testDebugUnitTest`. Expected: PASS.
- [ ] **Step 5:** Ask the user, then commit: `feat(db): room transaction runner with rollback on err`.

### Task 7: Seeder transaction helper

**Files:**
- Create: `app/src/main/java/io/github/toanhp123/finote/data/database/SeedTransaction.kt`
- Modify: `data/account/seed/SystemAccountSeeder.kt`, `data/category/seed/SystemCategorySeeder.kt`, `data/notification/seed/ParserRuleSeeder.kt` (replace the `BEGIN IMMEDIATE`/`COMMIT`/`ROLLBACK` block in `onOpen`)
- Test: `app/src/test/java/io/github/toanhp123/finote/data/database/SeedTransactionTest.kt`

**Interfaces (produces):** `inline fun <T> SQLiteConnection.immediateTransaction(block: () -> T): T` — `BEGIN IMMEDIATE TRANSACTION`, run, `COMMIT`; on failure `ROLLBACK` inside `runCatching` so a failing rollback does not hide the original error, then rethrow.

- [ ] **Step 1: Write failing tests** (use `db.useWriterConnection { connection -> … }` from `InMemoryDatabaseRule`):
  - `commits on success` — inside `immediateTransaction`, insert one category row with raw SQL; afterwards the row exists.
  - `rolls back on failure` — insert one row, then `error("boom")`; the call fails with `"boom"`; the row does not exist.
- [ ] **Step 2:** Run `testDebugUnitTest`. Expected: FAIL, unresolved `immediateTransaction`.
- [ ] **Step 3:** Implement the helper and switch the three seeders to it.
- [ ] **Step 4:** Run `testDebugUnitTest`. Expected: PASS, including the existing `*SeederTest` files.
- [ ] **Step 5:** Ask the user, then commit: `refactor(db): one transaction helper for seeders`.

### Task 8: Repositories use the shared calls

**Files (all under `data/`):**
- Modify: `account/repository/AccountRepositoryImpl.kt`, `category/repository/CategoryRepositoryImpl.kt`, `transaction/repository/TransactionRepositoryImpl.kt` (delete its private `write` and `storage`), `notification/repository/NotificationCaptureRepositoryImpl.kt`, `notification/repository/NotificationSettlementRepositoryImpl.kt`, `notification/repository/ParserRuleRepositoryImpl.kt`
- Not touched: `RuleUpdateSettingsRepositoryImpl`, `SetupRepositoryImpl` (SharedPreferences, not Room)
- Test: `account/repository/AccountRepositoryStorageErrorTest.kt`, `category/repository/CategoryRepositoryStorageErrorTest.kt`, `notification/repository/NotificationRepositoryStorageErrorTest.kt`

**Interfaces:** Consumes `storage`, `storageWrite`, `mapStorageErrors` (Task 5). Public repository signatures do not change; `require`/`check` stay where they are (R3–R6 move them).

Rules for the rewrite: every `db.withWriteTransaction { }` becomes `db.storageWrite { }`; every other `suspend` override body is wrapped in `storage { }`; every `Flow` override ends with `.mapStorageErrors()`.

- [ ] **Step 1: Write failing tests**, same shape as the existing `TransactionRepositoryStorageErrorTest` (drop the table, then call). For Account, Category, and ParserRule repositories: one `suspend` read, one write, and the `observe*` flow (`.first()`) each fail with `StorageException` whose `cause is android.database.SQLException`. For NotificationCapture and NotificationSettlement: one write each. Add a flow case to the existing `TransactionRepositoryStorageErrorTest` for `observeTransactions(null)`.
- [ ] **Step 2:** Run `testDebugUnitTest`. Expected: the new cases FAIL (raw `SQLException`).
- [ ] **Step 3:** Rewrite the six repositories by the rules above.
- [ ] **Step 4:** Run `testDebugUnitTest`. Expected: PASS, all old repository tests unchanged.
- [ ] **Step 5:** Ask the user, then commit: `refactor(db): repositories map storage errors in one place`.

### Task 9: Konsist rules, Room check, docs

**Files:**
- Modify: `app/src/test/java/io/github/toanhp123/finote/architecture/ProjectRulesKonsistTest.kt`
- Modify: `app/src/main/java/io/github/toanhp123/finote/data/database/RoomTransactionRunner.kt` (KDoc only)
- Modify: `docs/architecture.md`

Rules:

| Test | Asserts |
|---|---|
| `write transactions open only in data database` | no file outside `DATA_DATABASE` contains `withWriteTransaction` in code lines (skip `import` lines) |
| `storage exceptions are created only in data database` | no file outside `DATA_DATABASE` and outside `domain/common/StorageException.kt` contains `StorageException(` in code lines |

- [ ] **Step 1:** Add both rules. Run `testDebugUnitTest`. Expected: PASS.
- [ ] **Step 2: Red proofs.** Add `db.withWriteTransaction {}` to a repository, and `throw StorageException(e)` to a use case; run; see each rule FAIL; record; remove.
- [ ] **Step 3: Room check.** Read the Room 3 sources in the Gradle cache for `withWriteTransaction` (`find ~/.gradle/caches -name 'room3-runtime*sources*.jar'`, or the decompiled class if no sources jar). Write in the `RoomTransactionRunner` KDoc whether it begins `IMMEDIATE` and how nested calls join (savepoint or not), matching what Task 6 test 4 observed. If it is not `IMMEDIATE`, stop and tell the user before continuing.
- [ ] **Step 4:** In `docs/architecture.md` add `data/database/` to the layer tree and two rules: transactions open only through `TransactionRunner` (use cases) or `storageWrite` (repositories); `SQLException` maps to `StorageException` only in `data/database`.
- [ ] **Step 5:** Run `qualityGate` through `gradle-run`. Expected: `exit_status` 0.
- [ ] **Step 6:** Ask the user, then commit: `test: confine transactions and storage errors to data database`.
- [ ] **Step 7:** `reviewer` checks the branch against spec sections 2 (data part) and 3. Fix findings through `coder`, re-review, then ask the user before the squash into `dev` with `refactor(db): shared transactions and storage errors`.

---

## After R2

Write the R3–R6 plan (`docs/superpowers/plans/YYYY-MM-DD-typed-errors.md`) from spec sections 2 and 4, using the names R1 and R2 produced and the Room nesting result from Task 6.
