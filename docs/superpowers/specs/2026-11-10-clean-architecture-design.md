# Clean architecture inside `:app`: design

Date: 2026-11-10. Status: approved in chat, waiting for spec review.

## Goal

Make the code follow one clean, textbook-style structure inside the single `:app` module, so the next features (P2d first) build on clear boundaries. Three outcomes:

1. One error system: expected business failures are typed values; storage failures have one type and one handler.
2. A pure, tidy domain: no loose files at `domain/<feature>/` root; `model`, `repository`, `usecase` hold only their role.
3. A tidy data layer: one folder shape per feature, and transactions and storage error mapping written once in a shared place.

Behavior the user sees does not change, except where noted (error handling in release builds).

## Non-goals

- No Gradle module split, no `core/` package, no `build-logic`.
- No reorganization of `ui/`, `automation/`, `work/`, `di/` beyond the error helpers.
- No app-wide error-to-string mapper. Each screen maps its own errors.
- No local error log. A later, separate idea.

## Problems found (2026-11-10)

- About 15 business rules live as `require`/`check` inside `*RepositoryImpl` (for example "system category cannot be deleted", "account still has transactions"). The domain cannot see or test them.
- Business errors are `IllegalArgumentException` with a string. ViewModels catch `IllegalArgumentException`, `StorageException`, `ArithmeticException`, or `Exception`, each in its own way.
- Only `TransactionRepositoryImpl` maps `SQLException` to `StorageException`. Other repositories and every `observe*` Flow leak raw `SQLException`.
- Eleven `db.withWriteTransaction` calls are spread over five repositories. A use case cannot put two repositories in one transaction.
- Eight pure-logic files sit loose at `domain/<feature>/` root.
- `domain/<feature>/model` holds exceptions; `domain/<feature>/repository` holds ports that are not repositories.
- Three seeders copy the same manual `BEGIN`/`COMMIT`/`ROLLBACK` code.

## 1. Package layout

```text
domain/
  common/          Outcome, StorageException, TransactionRunner, DefaultDispatcher
  <feature>/
    model/         data classes and enums only, no Throwable subclasses
    error/         sealed <Feature>Error types
    repository/    *Repository interfaces only
    port/          other outward interfaces: ParserRuleFeed, RuleUpdateScheduler, SettleScheduler
    service/       pure logic: NotificationParser, NotificationClassifier, NotificationText,
                   SettlementPlanner, AccountResolver, BalanceCalculator, BalanceAnchor,
                   BalanceDriftDetector, and <Feature>Rules moved up from data
    usecase/       *UseCase

data/
  database/        FinoteDatabase, FinoteDatabaseFactory, RoomTransactionRunner,
                   StorageCalls.kt, SeedTransaction.kt
  security/        unchanged
  <feature>/
    local/         *Entity, *Dao, TransactionTagCrossRef
    mapper/        *Mapper.kt
    repository/    *RepositoryImpl, AccountBalanceRebuilder
    seed/          System*, *Seeder
    remote/        HttpParserRuleFeed, RuleFeedCodec (notification only)
```

Rules:

- A feature creates a subfolder only when it has a file of that role. No empty folders.
- Every feature uses the same shape, even when a folder holds one file. Folder rules 2 (three or more files) and 3 (no split when the suffix names the role) of `docs/architecture.md` no longer apply to `domain/<feature>` and `data/<feature>`. They still apply to `ui`.
- `Validate*UseCase` classes become pure functions in `<Feature>Rules` (in `service/`). A use case that needs repository data reads it and passes it in.

New Konsist rules:

- `domain/**/model` declares no `Throwable` subclass.
- `domain/**/repository` declares only interfaces named `*Repository`.
- `domain/**/port` declares only interfaces.
- `domain/**/service` imports nothing from `repository` or `port`.
- `domain/**/error`: every top-level declaration is a sealed interface, a sealed class, or an enum used as a reason inside one (such as `MergeRejection`); every other class there implements a sealed type of the same file. Turned on in R6: until then `AccountMergeRejectedException` and `RuleFeedException` sit in `error/` as plain exceptions.
- `*Entity` and `*Dao` live in `data/<feature>/local`, `*Mapper.kt` in `mapper`, `*RepositoryImpl` in `repository`, `*Seeder` in `seed`.

In R1, `AccountMergeRejectedException`, `MergeRejection`, `RuleFeedException`, and `RuleFeedError` move from `model/` to `error/`, so the `model/` rule holds from R1 on.

## 2. Error system

| Kind | Example | How it is reported | Who handles it |
|---|---|---|---|
| Expected business failure | delete a system category, merge across banks, unusable account, drift too large | use case returns `Outcome.Err(<Feature>Error)` | ViewModel, with an exhaustive `when` |
| Expected port failure | rule feed network error or invalid payload | port returns `Outcome<…, RuleFeedError>` | use case |
| Infrastructure failure | SQLite error | `StorageException`, created only in `data/database` | `BaseViewModel` helper |
| Bug | broken invariant (`require` in a model) | exception | debug: crash; release: generic failure |

Domain type, in `domain/common/Outcome.kt`:

```kotlin
sealed interface Outcome<out T, out E> {
    data class Ok<T>(val value: T) : Outcome<T, Nothing>
    data class Err<E>(val error: E) : Outcome<Nothing, E>
}
```

- `Outcome` starts with no helper functions. R3 (the pilot) adds `map` or `getOrElse` only if two or more call sites repeat the same `when`.
- Each feature has `error/<Feature>Error.kt`, a sealed interface. `MergeRejection` and `AccountMergeRejectedException` become `AccountError.MergeRejected(reason)`. `RuleFeedException` is removed; `RuleFeedError` moves to `domain/notification/error`.
- A `Long` overflow from money math (`Math.addExact`, `Math.subtractExact`) on user input becomes a typed error, such as `TransactionError.AmountOverflow`.
- `StorageException` has no message of its own: content may be financial data. It keeps the original exception as `cause` (as it does today), so a debugger and a test can see the failing query. No code shows or logs the cause.
- Only `data/database` may call the `StorageException` constructor. A Konsist rule enforces it (R2).
- `require` and `check` stay only for invariants that mean a bug.

Data:

- Every Room read, write, and `Flow` goes through `data/database`, which maps `android.database.SQLException` to `StorageException`. Not `java.sql.SQLException`. On Android `androidx.sqlite.SQLiteException` is the same type; test 3 in section 3 proves the real Room error is caught.
- Codec and HTTP code may throw internally. The feed port maps to `Outcome` at its boundary.

UI, in `ui/common/BaseViewModel`:

- `launchCatching(onStorageError, onUnexpected) { … }`. It catches `StorageException` and calls `onStorageError`. Any other exception except `CancellationException` crashes in debug builds and calls `onUnexpected` in release builds. Nothing is logged.
- `Flow.catchStorage { … }` for `observe*` flows, with the same debug/release split for other exceptions. Like `catch`, it ends the flow: the screen shows its error state until the ViewModel is created again. No automatic retry; this matches today's `.catch { emit(fallback) }`.
- The debug flag is an injected `isDebuggable` read from `ApplicationInfo.FLAG_DEBUGGABLE`. `buildConfig` stays off.
- Each ViewModel maps `Outcome.Err` to its own state or effect.

New Konsist rules, turned on in the last branch:

- `ui` does not `catch` `IllegalArgumentException`, `IllegalStateException`, `ArithmeticException`, or `Exception`.
- `ui` does not call `.catch {` on a Flow except through `catchStorage`.
- Both rules skip `ui/common/BaseViewModel.kt`, which defines the helpers. Screen logic that is not storage related (`AmountExpression` catching `ArithmeticException` for its own math) is listed by name as an exception.

Callers outside `ui`:

| Caller | `Outcome.Err` | `StorageException` | Other exception |
|---|---|---|---|
| `SettleWorker` | per-log status, decided by the settlement use case | `Result.retry()` (today's behavior) | `Result.retry()` (today's behavior) |
| `RuleUpdateWorker` | the use case returns `RuleUpdateResult`, mapped as today | recorded as `RuleUpdateResult.Error` by the use case | same |
| `FinoteNotificationListener` | the capture use case records a log status | dropped; the next `onListenerConnected` rescan retries (today's behavior) | same |

Reason for the release split: Finote has no crash reporting, so a release crash gives the developer nothing and only hurts the user. Large apps either catch at the UI edge and report non-fatals (Now in Android's `asResult()` catches every exception) or crash and rely on crash reporting. Without reporting, the first is the only useful choice in release.

## 3. Transactions

Port, in `domain/common/TransactionRunner.kt`:

```kotlin
interface TransactionRunner {
    /** One write transaction. An Err result or an exception rolls everything back. */
    suspend fun <T, E> write(block: suspend () -> Outcome<T, E>): Outcome<T, E>
}
```

- An `Err` result rolls back every write made in the block. Safety does not depend on checking rules before writing.
- A use case that checks a rule and then writes runs both inside `tx.write { }`. `SettleWorker` paths do the same, so a background settle cannot race between check and write.

Implementation, in `data/database`:

| File | Job |
|---|---|
| `RoomTransactionRunner` | `withWriteTransaction`; on `Err`, throws a private signal to roll back, then returns the `Err`; maps `SQLException` to `StorageException` |
| `StorageCalls.kt` | `storage { }` for single reads and writes; `storageWrite { }` for a multi-statement repository write (same `withWriteTransaction` path as the runner, without `Outcome`); `Flow.mapStorageErrors()` |

Runner details:

- The rollback signal is created with `writableStackTrace = false`, so an expected business failure does not pay for a stack trace.
- The runner catches only the signal instance it threw (`e === signal`), never a signal from another scope.
- Only the outermost use case opens `tx.write`. A use case does not call another use case inside `tx.write`; shared steps are plain functions or `<Feature>Rules`. `reviewer` checks this.
- `storage { }` and `storageWrite { }` only add a `try`/`catch`. They never start a new scope, `launch`, or `async`, because Room tracks the transaction connection in the coroutine context and a new scope loses it.
- Room 3 has one writer connection, so writes and the reads inside one write transaction are serialized. R2 confirms from the Room 3 source that `withWriteTransaction` begins `IMMEDIATE`, and records the finding in a comment on the runner.
| `SeedTransaction.kt` | `SQLiteConnection.immediateTransaction { }`, replacing the three copied seeder blocks |

Repositories after the change:

- CRUD plus the queries rules need (`countLiveChildren`, `countTransfersBetween`, and so on). No business `require`.
- Balance snapshot rebuild stays in the repository: the snapshot is a data cache, not a business rule. A multi-statement repository write wraps itself in a transaction; inside a use case's `tx.write` it joins the outer one.
- `withWriteTransaction` appears only in `data/database`. A Konsist rule enforces it. Repositories use `storageWrite { }`.

Tests that must exist (`InMemoryDatabaseRule`):

1. A write followed by `Err` leaves nothing behind.
2. An exception in the block rolls back and is rethrown.
3. `SQLException` reaches the caller as `StorageException`.
4. A nested transaction joins the outer one; an outer rollback also undoes the inner writes. An inner `storageWrite` that fails inside a `tx.write` rolls back the whole outer transaction.
5. A seeder that fails midway leaves no partial rows.

If test 4 shows Room 3 does not join nested write transactions, repositories use a helper that opens a transaction only when none is active (`inTransaction()`).

Use case tests use a `FakeTransactionRunner` that runs the block, so they stay on the JVM without Room.

## 4. Branches and order

Start after cleanup branch 6 (`chore/test-hygiene`) and before P2d. Cleanup branches 4 and 5 (UI) are deferred to a later UI upgrade; they will build on R3's `BaseViewModel` helpers. Cleanup branches 7 and 8 are independent.

| # | Branch | Content | Behavior change |
|---|---|---|---|
| R1 | `chore/package-layout` | Move domain and data packages (section 1) with `git mv`; tests move with them; layout Konsist rules; update `docs/architecture.md` | None |
| R2 | `chore/data-database` | `Outcome` and `TransactionRunner` in `domain/common`; `data/database` runner, `storage {}`, `mapStorageErrors()`, seeder helper; every repository uses them; the five tests in section 3 | Storage errors become typed |
| R3 | `chore/errors-category` | Pilot: `CategoryError`, `CategoryRules`, use cases on `tx.write` returning `Outcome`, `require` removed from the repository; `BaseViewModel.launchCatching`, `catchStorage`, `isDebuggable` | Yes |
| R4 | `chore/errors-account` | Same pattern; `MergeRejection` folds into `AccountError` | Yes |
| R5 | `chore/errors-transaction` | Same pattern; `ValidateTransactionUseCase` becomes rules; overflow becomes a typed error | Yes |
| R6 | `chore/errors-notification` | Feed port returns `Outcome`; `RuleFeedException` removed; settlement uses the runner; `ui` catch rules from section 2 turn on | Yes |

Per branch: `coder` writes code and tests → `reviewer` checks the branch → `qualityGate` → squash into `dev`. R3 sets the pattern; R4 to R6 copy it.

Verification:

- R1: every existing test passes with only package and import changes, no assertion edits.
- R2 to R6: tests that asserted `IllegalArgumentException` now assert the exact `Outcome.Err`. Every rule moved to the domain gets its own JVM test. Balance and money logic changes run `testDebugUnitTest`.

Risks:

- R1 moves many files. No other branch may be open while it runs, or merges will conflict heavily.
- R2 depends on Room 3 nested transaction behavior; the fallback is in section 3.
- Release builds hide bugs behind a generic failure message. Accepted: there is no crash reporting.
