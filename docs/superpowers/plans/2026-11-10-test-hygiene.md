# Test Hygiene (Cleanup Branch 6) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the test suite a trustworthy safety net before the clean architecture upgrade: shared builders, fakes that match the DAOs, fixed clocks, closed Konsist gaps, and notification models that do not print their text.

**Architecture:** Test-side changes plus four small production edits: one `AccountDao` query, three `toString()` overrides, and the Gradle dependency configurations. Paths are the current (pre-R1) packages.

**Tech Stack:** Kotlin, JUnit4, Robolectric, kotlinx-coroutines-test, Konsist, Room 3.

**Spec:** `docs/superpowers/specs/2026-11-10-test-hygiene-design.md`

## Global Constraints

- Branch `chore/test-hygiene` from `dev`, squashed into `dev` at the end.
- No test changes what it asserts, except a test that relied on a fake's drift (Task 2); that test is named in the commit message.
- Test defaults hold no real bank notification, balance, or account number.
- New or changed Konsist rule: red proof (deliberate violation → rule fails → record the failing test name in the task notes → remove). Never commit the violation.
- If a new money word flags a valid `Float` or `Double`, stop and ask the user.
- Run Gradle through the `gradle-run` skill; from Bash pass `'.\gradlew.bat'`. `testDebugUnitTest` after each task, `qualityGate` before the squash.
- Ask the user before every commit; the `coder` subagent never commits. Allowed scopes: `db`, `notif`, `build`, or none. No AI attribution.
- Never write notification content, balances, or account numbers to Logcat.

## Review Focus

1. A copied builder that derived its id from another field (`"r-$pkg"`) or a counter (`"id${seq++}"`) keeps that derivation, so lists built in one test never share an id → Task 1 keeps per-test wrappers; Step 4 diff check.
2. Two transactions with equal `timestamp` and `createdAt` come back in `id DESC` order from the fake, as from SQL → Task 2 test includes that tie.
3. A name that used to match a money word as a substring but is not a money word (`feedback` contains `fee`) stops matching, and a real one (`MAX_FEE`, `postBalance`) still matches → Task 4 tokenizer test.
4. `NotificationLog.toString()` must not leak through its nested `parsed: ParsedFields` → Task 5 test puts distinct values in `parsed` too.
5. A layer-filtered Konsist rule over an empty file list passes silently → Task 4 asserts each list is non-empty.

---

### Task 1: Shared notification builders

**Files:**
- Create: `app/src/test/java/io/github/toanhp123/finote/domain/notification/NotificationTestBuilders.kt`
- Modify (replace the body of the private builder with a call to the shared one): `domain/notification/{AccountResolverTest,NotificationClassifierTest,NotificationParserTest,SettlementPlannerTest}.kt`, `domain/notification/usecase/{CaptureNotificationUseCaseTest,ReparseFailedLogsUseCaseTest,SettlePendingLogsUseCaseTest,UpdateParserRulesUseCaseTest}.kt`, `ui/settings/{BankSummaryTest,SettingsViewModelTest}.kt`, `ui/settings/capture/NotificationCaptureSettingsViewModelTest.kt` (all under `app/src/test/java/io/github/toanhp123/finote/`)

**Interfaces (produces):**
```kotlin
fun testRule(
    id: String = "r1",
    packageName: String = "pkg",
    bankName: String = "Bank",
    channelIdAllowlist: List<String>? = null,
    ignoreKeywords: List<String> = emptyList(),
    priority: Int = 0,
    isUserCustom: Boolean = false,
    version: Int = 1,
    regexPattern: String = ".*",
    fallbackTransactionType: TransactionType? = null,
    dateFormatPattern: String? = null,
    isActive: Boolean = true,
    updatedAt: Long = 0L,
    accountType: AccountType? = null,
): ParserRule

fun testLog(
    id: String = "log1",
    packageName: String = "pkg",
    channelId: String? = null,
    title: String = "title",
    content: String = "content",
    contentHash: String = id,
    postTime: Long = 1_000L,
    receivedAt: Long = postTime,
    status: NotificationStatus = NotificationStatus.PENDING,
    failureReason: String? = null,
    matchedRuleId: String? = null,
    parsed: ParsedFields? = null,
    linkedTransactionId: String? = null,
): NotificationLog

fun testRaw(
    packageName: String = "pkg",
    channelId: String? = "ch",
    title: String = "title",
    content: String = "content",
    postTime: Long = 1_000L,
): RawNotification
```
Each is a plain constructor call with the parameters passed through.

How each file changes: the private `rule(`/`log(`/`raw(` keeps its name and parameter list (so no call site changes) and becomes a one-expression delegation that passes every value the old body set and the shared default does not match. Example for `SettlePendingLogsUseCaseTest`:
```kotlin
private fun rule(pkg: String) = testRule(id = "r-$pkg", packageName = pkg, bankName = pkg, updatedAt = 1L)
```
Counters (`seq++`) and derived ids stay inside the wrapper exactly as before.

- [ ] **Step 1:** `git switch dev`, then `git switch -c chore/test-hygiene`. Ask the user, then commit the spec and this plan: `docs: test hygiene spec and plan`.
- [ ] **Step 2:** Create `NotificationTestBuilders.kt` with the three functions.
- [ ] **Step 3:** Rewrite each private builder as a delegation. For every field, compare the old body's value with the shared default and pass it when they differ (for example `regexPattern = "(?<sign>[+-])(?<amount>[\\d,]+)"`, `bankName = "Vietcombank"`, `updatedAt = 1L`, `packageName = PKG`, `title = "Biến động số dư"`).
- [ ] **Step 4:** Check no assertion changed: `git diff dev -- app/src/test | grep '^[-+]' | grep -E 'assert|expect' ` prints nothing.
- [ ] **Step 5:** Run `testDebugUnitTest`. Expected: `exit_status` 0.
- [ ] **Step 6:** Ask the user, then commit: `test: share notification test builders`.

### Task 2: Fakes match the DAOs

**Files:**
- Modify: `app/src/test/java/io/github/toanhp123/finote/domain/transaction/usecase/FakeTransactionRepository.kt`, `domain/account/usecase/FakeAccountRepository.kt`, every other `Fake*.kt` under `domain/*/usecase/` (KDoc only, unless drift is found)
- Modify: `app/src/main/java/io/github/toanhp123/finote/data/account/AccountDao.kt` (`observeAccounts` query)
- Test: `domain/transaction/usecase/FakeTransactionRepositoryTest.kt`, `domain/account/usecase/FakeAccountRepositoryTest.kt`, `data/account/AccountDaoTest.kt` (new case)

**Interfaces:** No signature changes.

- [ ] **Step 1: Write failing tests.**
  - `FakeTransactionRepositoryTest`: `observe drops deleted rows and sorts like the DAO` — store `a` (timestamp 2, createdAt 1), `b` (timestamp 3), `c` (timestamp 2, createdAt 1, id greater than `a`), `d` (deleted); `observeTransactions(null).first()` ids equal `["b", "c", "a"]`. `observe filters by account like touches` — a transfer whose `toAccountId` is `acc-2` is returned for `observeTransactions("acc-2")`.
  - `FakeAccountRepositoryTest`: `observe sorts by sortOrder, createdAt, id` — accounts `y` (sortOrder 0, createdAt 1), `x` (sortOrder 0, createdAt 1), `z` (sortOrder -1); ids equal `["z", "x", "y"]`; a deleted account is absent.
  - `AccountDaoTest`: `observeAccounts breaks ties by id` — insert `y` then `x` with equal `sortOrder` and `createdAt`; ids equal `["x", "y"]`.
- [ ] **Step 2:** Run `testDebugUnitTest`. Expected: these cases FAIL.
- [ ] **Step 3:** `AccountDao.observeAccounts`: `ORDER BY sortOrder ASC, createdAt ASC, id ASC`. `FakeTransactionRepository.observeTransactions`: filter `deletedAt == null` and `accountId == null || it.touches(accountId)`, sort `compareByDescending<Transaction> { it.timestamp }.thenByDescending { it.createdAt }.thenByDescending { it.id }`. `FakeAccountRepository.observeAccounts`: sort `compareBy<Account> { it.sortOrder }.thenBy { it.createdAt }.thenBy { it.id }`.
- [ ] **Step 4:** Compare every other fake with its DAO query or repository KDoc (`FakeCategoryRepository`, `FakeNotificationCaptureRepository`, `FakeNotificationSettlementRepository`, `FakeParserRuleRepository`, `FakeRuleUpdateSettingsRepository`, `FakeParserRuleFeed`, `FakeRuleUpdateScheduler`, `FakeSettleScheduler`). Drift in a filter or order gets the same test-then-fix treatment.
- [ ] **Step 5:** Add a KDoc header to every fake: one line on what it copies from the real repository (filter, order, the `update` contract), one line on what it does not (business `require` checks).
- [ ] **Step 6:** Run `testDebugUnitTest`. Expected: PASS. If an older test turns red because it relied on the drift, fix that test to the real behavior and list it for the commit message.
- [ ] **Step 7:** Ask the user, then commit: `fix(db): fakes and account order match the DAOs` (body lists any test fixed in Step 6).

### Task 3: Fixed clocks

**Files:**
- Modify: `app/src/test/java/io/github/toanhp123/finote/work/{SettleWorkerTest,RuleUpdateWorkerTest}.kt`, `data/account/SystemAccountSeederTest.kt`, `data/category/SystemCategorySeederTest.kt`

- [ ] **Step 1:** Replace wall-clock calls with `Clock.fixed(Instant.parse("2026-11-03T00:00:00Z"), ZoneOffset.UTC)` or a fixed millisecond value. Where a test asserted a time window around "now", assert the exact fixed value instead.
- [ ] **Step 2:** Run from the repo root: `grep -rnE 'Clock\.system|System\.currentTimeMillis|Instant\.now\(\)|LocalDate\.now\(\)|LocalDateTime\.now\(\)|ZonedDateTime\.now\(\)' app/src/test app/src/androidTest`. Expected: no output.
- [ ] **Step 3:** Run `testDebugUnitTest`. Expected: PASS.
- [ ] **Step 4:** Ask the user, then commit: `test: fixed clocks everywhere`.

### Task 4: Konsist gaps

**Files:**
- Modify: `app/src/test/java/io/github/toanhp123/finote/architecture/LayerDependencyKonsistTest.kt`, `ProjectRulesKonsistTest.kt`, `KonsistScopes.kt`
- Test: `app/src/test/java/io/github/toanhp123/finote/architecture/NameWordsTest.kt`

**Interfaces (produces, in `KonsistScopes.kt`):** `internal fun String.nameWords(): List<String>` — splits on `_` and on lower→upper and letter→digit boundaries, lowercases each word. `"totalAmount"` → `["total", "amount"]`, `"MAX_FEE"` → `["max", "fee"]`, `"postBalance2"` → `["post", "balance", "2"]`, `"feedback"` → `["feedback"]`.

| Test (file) | Assertion |
|---|---|
| `domain imports only allowed packages` (LayerDependency; replaces `domain has no framework imports`) | every import in `filesIn(DOMAIN)` satisfies `isFrom` for one of `kotlin`, `kotlinx.coroutines`, `java.time`, `java.util`, `java.text`, `java.security`, `java.math`, `javax.inject`, `DOMAIN` |
| `automation and work do not use repositories` (LayerDependency) | `filesIn(AUTOMATION) + filesIn(WORK)` is not empty; no import's last segment ends with `Repository` |
| `di holds only bindings` (LayerDependency) | `filesIn(DI)` is not empty; each top-level type has annotation `Module`, or is an annotation class with annotation `Qualifier`; each function declared in a `Module` type (all visibilities) has annotation `Provides` or `Binds` |
| money rules (ProjectRules) | `isMoneyName()` becomes `nameWords().any { it in MONEY_WORDS }`; `MONEY_WORDS` adds `price`, `cost`, `total`, `debt`, `income`, `expense`, `spent`, `budget`, `difference`, `drift`, `delta` |
| logging rule (ProjectRules) | also fails on `System.out`, `System.err`, and imports from `java.util.logging` |

- [ ] **Step 1: Write failing test** `NameWordsTest` with the four examples above as `assertEquals`.
- [ ] **Step 2:** Run `testDebugUnitTest`. Expected: FAIL, unresolved `nameWords`.
- [ ] **Step 3:** Implement `nameWords()` and the five rule changes.
- [ ] **Step 4:** Run `testDebugUnitTest`. Expected: PASS. If a money rule fails on existing code, stop and ask the user.
- [ ] **Step 5: Red proofs**, one at a time, each run then removed: `import android.content.Context` in a domain file; `import ...domain.transaction.repository.TransactionRepository` in `SettleWorker`; `private fun helper() = 1` inside `TimeModule`; `val totalCost: Double = 0.0` in a domain model; `System.out.print("")` in a domain file. Record each failing test name.
- [ ] **Step 6:** Run `testDebugUnitTest`. Expected: PASS.
- [ ] **Step 7:** Ask the user, then commit: `test: close konsist gaps from the audit`.

### Task 5: Notification text stays out of `toString()`

**Files:**
- Modify: `app/src/main/java/io/github/toanhp123/finote/domain/notification/model/{RawNotification,NotificationLog,ParsedFields}.kt`
- Modify: `app/src/test/java/io/github/toanhp123/finote/architecture/ProjectRulesKonsistTest.kt`
- Test: `app/src/test/java/io/github/toanhp123/finote/domain/notification/model/NotificationToStringTest.kt`

**Interfaces:** `toString()` output exactly:
- `RawNotification(packageName=$packageName, postTime=$postTime)`
- `NotificationLog(id=$id, packageName=$packageName, status=$status)`
- `ParsedFields(type=$type, hasMask=${accountMask != null}, hasPostBalance=${postBalance != null})`

- [ ] **Step 1: Write failing tests** using `testRaw`/`testLog` (Task 1) with distinct markers: `title = "T-SECRET"`, `content = "C-SECRET"`, `contentHash = "H-SECRET"`, `failureReason = "F-SECRET"`, and `ParsedFields(amount = 987_654L, type = EXPENSE, accountMask = "M-SECRET", postBalance = 123_456L, merchant = "MER-SECRET", note = "N-SECRET")` (also set as `parsed` on the log). For each class: `toString()` equals the exact output above, and contains none of `SECRET`, `987654`, `123456`.
- [ ] **Step 2:** Run `testDebugUnitTest`. Expected: FAIL.
- [ ] **Step 3:** Add the three overrides.
- [ ] **Step 4:** Add Konsist `notification text holders hide it`: every data class in `filesIn("$DOMAIN.notification")` with a property named one of `title`, `content`, `accountMask`, `postBalance`, `merchant`, `note` declares `override fun toString`. The file list is not empty.
- [ ] **Step 5: Red proof:** remove the override from `ParsedFields`, run, see the rule fail, restore.
- [ ] **Step 6:** Run `testDebugUnitTest`. Expected: PASS.
- [ ] **Step 7:** Ask the user, then commit: `fix(notif): keep notification text out of toString`.

### Task 6: Dependencies, example tests, KDoc

**Files:**
- Modify: `app/build.gradle.kts` (dependencies block)
- Delete: `app/src/test/java/io/github/toanhp123/finote/ExampleUnitTest.kt`, `app/src/androidTest/java/io/github/toanhp123/finote/ExampleInstrumentedTest.kt`
- Modify: `app/src/main/java/io/github/toanhp123/finote/data/notification/NotificationLogEntity.kt` (KDoc on `title`, `content`)
- Modify: `gradle/libs.versions.toml` only if an alias becomes unused (it should not: `espresso-core` stays for `test`)

- [ ] **Step 1:** `implementation(libs.sqlite.framework)` → `testImplementation(libs.sqlite.framework)`. Remove `androidTestImplementation(libs.androidx.espresso.core)`. Keep `testImplementation(libs.androidx.espresso.core)`.
- [ ] **Step 2:** Delete the two example tests.
- [ ] **Step 3:** Find every place that writes a `NotificationLogEntity` or `NotificationLog` with empty `title`/`content` (capture and classifier paths). Rewrite the two KDoc lines to name exactly those statuses.
- [ ] **Step 4:** Run `assembleDebug`, `testDebugUnitTest`, and `compileDebugAndroidTestKotlin`. Expected: each `exit_status` 0.
- [ ] **Step 5:** Ask the user, then commit: `build: test-only sqlite driver, drop example tests`.

### Task 7: Gate and review

- [ ] **Step 1:** Run `qualityGate`. Expected: `exit_status` 0.
- [ ] **Step 2:** `reviewer` checks the branch against the spec. Fix findings through `coder`, re-review.
- [ ] **Step 3:** Ask the user, then `git switch dev && git merge --squash chore/test-hygiene` and commit `test: test hygiene before the architecture upgrade`.
- [ ] **Step 4:** Update the progress line in the memory file `cleanup-before-p2d` (branch 6 done; next R1).
