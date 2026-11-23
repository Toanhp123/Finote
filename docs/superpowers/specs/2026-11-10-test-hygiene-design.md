# Test hygiene (cleanup branch 6): design

Date: 2026-11-10. Status: approved in chat, waiting for spec review.
Branch: `chore/test-hygiene`. Source: findings A18, A19 and the small items in `docs/superpowers/handoff/2026-11-10-cleanup-before-p2d.md`.

## Goal

Make the test suite a trustworthy safety net before the clean architecture upgrade (`docs/superpowers/specs/2026-11-10-clean-architecture-design.md`) moves most files. Four outcomes:

1. Test builders are shared, not copied.
2. Fakes behave like the real repositories where tests depend on it, and say where they do not.
3. Konsist closes the gaps found in the audit.
4. Domain models that hold notification text do not print it.

No user-visible behavior changes, except one: accounts with equal `sortOrder` and `createdAt` get a fixed order (`id ASC`).

## Non-goals

- Repository contract tests (one suite run against fake and Room impl). Revisit after R5, when repository interfaces stop changing.
- Fake emulation of business rules (`require` in repositories). R3 to R5 move those rules into the domain.
- Unused UI (`FinoteDatePicker`, `DatePickerContent`, `BalanceHeroCard`): the later UI upgrade decides.
- Unread `Boolean` results of `fail`, `ignore`, `markRepost`: R6 reworks settlement with `Outcome`.
- OTA key rotation, blocking destructive migration and key-loss wipe: cleanup branch 7.
- `toString()` overrides for `Account`, `Transaction`, and other balance-bearing types.

## 1. Shared builders and fakes

### Builders

New file `app/src/test/java/io/github/toanhp123/finote/domain/notification/NotificationTestBuilders.kt` with `testRule(...)`, `testLog(...)`, `testRaw(...)`. Every parameter has a neutral default; a test passes only the fields it cares about.

| Builder | Replaces |
|---|---|
| `testRule` | private `rule(` in `AccountResolverTest`, `NotificationClassifierTest`, `NotificationParserTest`, `SettlementPlannerTest`, `CaptureNotificationUseCaseTest`, `ReparseFailedLogsUseCaseTest`, `SettlePendingLogsUseCaseTest`, `UpdateParserRulesUseCaseTest`, `BankSummaryTest`, `NotificationCaptureSettingsViewModelTest`, `SettingsViewModelTest` |
| `testLog` | private `log(` in `SettlementPlannerTest`, `SettlePendingLogsUseCaseTest` (domain `NotificationLog`) |
| `testRaw` | private `raw(` in `NotificationClassifierTest`, `CaptureNotificationUseCaseTest` |

Rules:

- Before merging, compare every copy. Where a copy's default differs from the shared default, that test passes the value explicitly. No test changes what it asserts.
- `log(` in `NotificationCaptureRepositoryImplTest` and `NotificationSettlementRepositoryImplTest` builds entities, not domain models. It stays with the data fixtures (`data/Fixtures.kt`, `data/notification/NotificationFixtures.kt`); merge it there only if two copies build the same type.
- Defaults hold no real bank notification, balance, or account number.
- Default ids are fixed strings. Before replacing a copy, find every test that builds two or more rules or logs in one test case; where an old copy derived the id from another field or a counter, the new call passes `id` explicitly, so no two objects in one list share an id.

### Fakes

| Fake | Drift | Fix |
|---|---|---|
| `FakeTransactionRepository.observeTransactions` | keeps deleted rows, no order | keep `deletedAt == null`; order `timestamp DESC, createdAt DESC, id DESC`; filter the account with `Transaction.touches` |
| `FakeAccountRepository.observeAccounts` | no order | order `sortOrder ASC, createdAt ASC, id ASC` |
| `AccountDao.observeAccounts` (production) | `ORDER BY sortOrder ASC, createdAt ASC` has no tie-breaker, so two accounts with equal keys come back in any order | add `, id ASC` to the query, so real and fake are deterministic and equal |

Checked and already matching, no change: `FakeAccountRepository.observeAccounts` filters only `deletedAt == null`, like the DAO (it does not filter `mergedIntoId`); `FakeTransactionRepository.getTransaction` returns deleted rows, like `TransactionDao.getById`, which has no `deletedAt` filter.

- Each fix gets a test in `Fake*Test.kt` next to the fake: input with one deleted row and shuffled order; the fake returns what the SQL query returns.
- Every fake (`Fake*Repository`, `Fake*Feed`, `Fake*Scheduler`) gets a KDoc header: which real behavior it copies (filter, order, the `update` contract) and which it does not (business `require` checks).
- The other fakes are compared with their DAO queries. Any drift found is fixed the same way, with a test; no drift means a KDoc header only.

### Fixed clocks

`SettleWorkerTest`, `RuleUpdateWorkerTest`, `SystemAccountSeederTest`, `SystemCategorySeederTest` replace `Clock.systemUTC()`, `System.currentTimeMillis()`, and `Instant.now()` with `Clock.fixed(...)` or a fixed millisecond value.

Done means this search over `app/src/test` and `app/src/androidTest` prints nothing: `Clock.system`, `System.currentTimeMillis`, `Instant.now()`, `LocalDate.now()`, `LocalDateTime.now()`, `ZonedDateTime.now()`.

## 2. Konsist gaps

| Rule | Today | After |
|---|---|---|
| `domain imports only allowed packages` (renamed from `domain has no framework imports`) | denylist: `android`, `androidx`, `dagger`, `room` | allowlist: `kotlin`, `kotlinx.coroutines`, `java.time`, `java.util`, `java.text`, `java.security`, `java.math`, `javax.inject`, `io.github.toanhp123.finote.domain` |
| `automation and work do not use repositories` | none | no import in `automation` or `work` whose last segment ends with `Repository` |
| `di holds only bindings` | none | every top-level type in `di` has `@Module` or is an `annotation class` with `@Qualifier`; every function in a module, private ones included, has `@Provides` or `@Binds`. Setup logic belongs in a factory outside `di` (as `FinoteDatabaseFactory` does) |
| `MONEY_WORDS` | `amount`, `balance`, `limit`, `fee`, matched as substrings | adds `price`, `cost`, `total`, `debt`, `income`, `expense`, `spent`, `budget`, `difference`, `drift`, `delta`; the name is split into camelCase and snake_case words first, and a word must equal a money word (`totalAmount` → `total`, `amount`; `dragDelta` matches `delta` and stays an error only if its type is `Float` or `Double`) |
| logging rule | `android.util.Log`, `println`, `printStackTrace` | also `System.out`, `System.err`, `java.util.logging` |

- Every new or changed rule gets a red proof: a deliberate violation, the rule fails, the output is recorded, the violation is removed.
- If a new money word flags a valid `Float` (for example a `dragDelta` in Compose), stop and ask the user. Do not add an exception alone. A search on 2026-11-10 found no such property today.
- Package matching uses the existing `isFrom` / `inPackage` helpers, which match whole segments, so `kotlin` does not admit `kotlinx.serialization`.
- Rules that filter by layer (`automation`, `work`, `di`) also assert their file list is not empty, because Konsist passes an empty list.
- Not added: "automation imports only `usecase`". `FinoteNotificationListener` calls the pure domain function `notificationContent`, which is fine; the `*Repository` ban covers the real risk.

## 3. Privacy and small items

### `toString()`

`toString()` output per class:

| Class | Output |
|---|---|
| `RawNotification` | `RawNotification(packageName=…, postTime=…)` |
| `NotificationLog` | `NotificationLog(id=…, packageName=…, status=…)` |
| `ParsedFields` | `ParsedFields(type=…, hasMask=…, hasPostBalance=…)` |

- Never printed: `title`, `content`, `amount`, `accountMask`, `postBalance`, `merchant`, `note`, `contentHash`, `failureReason`.
- One test per class: build a fixture with a distinct fake string or number in every never-printed field; `toString()` contains none of them.
- New Konsist rule `notification text holders hide it`: a data class in `domain.notification` with a property named `title`, `content`, `accountMask`, `postBalance`, `merchant`, or `note` overrides `toString`. Scoped to `domain.notification` because `Transaction.note` is the user's own text, out of scope here.

### Cleanup

- Delete `app/src/test/.../ExampleUnitTest.kt` and `app/src/androidTest/.../ExampleInstrumentedTest.kt`.
- `sqlite-framework`: `implementation` → `testImplementation`. `androidTest` (`EncryptedDatabaseTest`) does not import `androidx.sqlite.driver`, so no `androidTestImplementation`.
- `espresso-core`: remove `androidTestImplementation`; keep `testImplementation`, because `FinoteBottomSheetTest` imports `androidx.test.espresso`. androidTest gets `androidx.test:runner` directly (`androidTestImplementation(libs.androidx.test.runner)`) because espresso was what pulled it, and `testInstrumentationRunner` needs it.
- `app/src/androidTest` keeps `EncryptedDatabaseTest`, so the folder stays.
- `NotificationLogEntity` KDoc on `title` and `content`: check which statuses really store empty text, then make the KDoc match the code.

## 4. Tasks and verification

| # | Task |
|---|---|
| 1 | Shared builders |
| 2 | Fake fixes with tests and KDoc |
| 3 | Fixed clocks |
| 4 | Konsist gaps with red proofs |
| 5 | `toString()` privacy with tests and Konsist rule |
| 6 | Dependencies, example tests, KDoc |
| 7 | `qualityGate`, `reviewer`, squash into `dev` |

- Tasks 1 and 3 change test code only: every test keeps its assertions, and `testDebugUnitTest` passes after each.
- Task 2 may turn an existing test red if it relied on the fake's drift. That test is fixed to match real behavior, and the fix is named in the commit message.
- Task 6 runs `assembleDebug` and `testDebugUnitTest`; the dependency change must not break `androidTest` compilation (`compileDebugAndroidTestKotlin`).
- Before the squash: `qualityGate` through `gradle-run`, then `reviewer` against this spec.
