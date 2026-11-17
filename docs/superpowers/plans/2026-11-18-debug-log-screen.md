# Notification Log Screen Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A read-only screen of `notification_logs` with a "copy masked corpus sample" action, reachable in `debug` and a new `dogfood` build type, never in `release`.

**Architecture:** DAO flow → `NotificationCaptureRepository.observeRecentLogs` → `ObserveNotificationLogsUseCase` → `NotificationLogsViewModel` in `ui/settings/logs/`. Masking is a pure domain service; the corpus JSON is built in the feature's `logic/` (domain may not import `kotlinx.serialization`). A `@ToolsEnabled` Hilt qualifier, fed from `BuildConfig.TOOLS_ENABLED`, gates the Settings row and the nav entry.

**Tech Stack:** Kotlin, Compose M3, Navigation3, Hilt, Room 3, kotlinx.serialization-json, JUnit4, Robolectric, Turbine.

**Spec:** `docs/superpowers/specs/2026-11-17-debug-log-screen-design.md`

## Global Constraints

- Branch `feature/debug-log-screen` from `dev`; squash into `dev` after `reviewer` (`docs/git-workflow.md`).
- Commit `<type>(<scope>): <subject>`, scopes `notif`, `ui`, `build`, `db` or none; no AI attribution. The main session commits each reviewed task; `coder` never commits.
- No schema change: DB stays at version 8.
- `TOOLS_ENABLED` is read from `BuildConfig` in exactly one place: `di/BuildModule.kt`.
- Masking replaces only ASCII `'0'..'9'`, each with `'1'`. Never `Char.isDigit()` or `\d`. No trim, no whitespace or newline change. `packageName` is never masked.
- No notification text, amount or mask in Logcat, test failure messages, or a `toString()`.
- New strings only in `values/strings.xml`, `translatable="false"`, Vietnamese.
- Gradle through the `gradle-run` skill with `'.\gradlew.bat'` from Bash.
- Skills: `ponytail:ponytail`, `compose-state-and-effects`, `kotlin-concurrency-and-flow`, `testing-coroutines-with-runtest`, `testing-flows-with-turbine`, `using-robolectric-correctly`, `gradle-run`, `superpowers:verification-before-completion`.

## Review Focus

1. A notification whose content holds `\n` or `"` must copy into JSON that decodes back to the same title and content — Task 3 `json keeps line breaks and quotes`.
2. A Unicode digit (`50m²`) must stay as is while every ASCII digit changes — Task 1 `only ASCII digits change`.
3. A secret row or an empty group-summary row (blank title and content) must not offer copy — Task 4 `copy is off for a blank log`.
4. `release` must have no way in: no Settings row and no registered nav entry when the flag is false — Task 5 `logs row hidden when tools are off` (ViewModel); the `FinoteNavHost` guard is checked by `reviewer`.
5. State `toString()` must never show sample text, even with the detail sheet open — Task 4 `state toString hides text`.

---

### Task 1: Digit masking (domain)

**Files:**
- Create: `app/src/main/java/io/github/toanhp123/finote/domain/notification/service/SampleMasking.kt`
- Test: `app/src/test/java/io/github/toanhp123/finote/domain/notification/service/SampleMaskingTest.kt`
- Test: `app/src/test/java/io/github/toanhp123/finote/data/notification/seed/MaskedCorpusTest.kt`

**Interfaces:**
- Produces:
  - `fun maskDigits(text: String): String` — each `'0'..'9'` becomes `'1'`, every other char unchanged.
  - `fun NotificationLog.masked(): NotificationLog` — `copy(title = maskDigits(title), content = maskDigits(content))`; all other fields unchanged.

- [ ] **Step 1: Create the branch**

Run: `git switch -c feature/debug-log-screen dev`
Expected: `Switched to a new branch 'feature/debug-log-screen'`

- [ ] **Step 2: Write the failing tests**

`SampleMaskingTest` (plain JUnit):

```kotlin
@Test fun `only ASCII digits change`() =
    assertEquals("TK 1111 -111,111 VND 11m²", maskDigits("TK 0123 -450,000 VND 50m²"))

@Test fun `spacing and line breaks survive`() =
    assertEquals("  A 11\n\tB  ", maskDigits("  A 09\n\tB  "))

@Test fun `masked keeps package and every non-text field`() {
    val log = testLog(packageName = "com.VCB9", title = "SD 12", content = "x 34")
    assertEquals(log.copy(title = "SD 11", content = "x 11"), log.masked())
}
```

`MaskedCorpusTest` (data/notification/seed, same harness as `SystemParserRulesCorpusTest`): for every `NotificationCorpusFixtures.financialTransactions()` item, run `classifyNotification(RawNotification(pkg, channelId, title, text, postTime), rulesOf(pkg))` on the original text (`bigText ?: content`) and on `maskDigits(text)`; assert the masked run has the same `matchedRuleId` and the same `parsed?.type`. Rules: `SystemParserRules.mapNotNull { it.toDomain() }.filter { it.packageName == pkg }`. On failure list item ids only:

```kotlin
assertEquals("mask changed match: $changed", emptyList<String>(), changed)
```

- [ ] **Step 3: Run tests to verify they fail**

Run `testDebugUnitTest --tests "*SampleMaskingTest" --tests "*MaskedCorpusTest"` through `gradle-run`.
Expected: compile failure, `maskDigits` unresolved.

- [ ] **Step 4: Implement `maskDigits` and `masked` in `SampleMasking.kt`**

`text.map { if (it in '0'..'9') '1' else it }.joinToString("")` or a `buildString`; KDoc says why `isDigit()` is not used.

- [ ] **Step 5: Run tests to verify they pass**

Same command. Expected: PASS. If a corpus item changes its match, stop and report the item id; do not change the rule or the digit.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/io/github/toanhp123/finote/domain/notification/service/SampleMasking.kt app/src/test/java/io/github/toanhp123/finote/domain/notification/service/SampleMaskingTest.kt app/src/test/java/io/github/toanhp123/finote/data/notification/seed/MaskedCorpusTest.kt
git commit -m "feat(notif): mask digits of a notification sample"
```

---

### Task 2: Read path for recent logs

**Files:**
- Modify: `app/src/main/java/io/github/toanhp123/finote/data/notification/local/dao/NotificationLogDao.kt`
- Modify: `app/src/main/java/io/github/toanhp123/finote/domain/notification/repository/NotificationCaptureRepository.kt`
- Modify: `app/src/main/java/io/github/toanhp123/finote/data/notification/repository/NotificationCaptureRepositoryImpl.kt`
- Create: `app/src/main/java/io/github/toanhp123/finote/domain/notification/usecase/ObserveNotificationLogsUseCase.kt`
- Modify: `app/src/test/java/io/github/toanhp123/finote/domain/notification/usecase/FakeNotificationCaptureRepository.kt`
- Test: `app/src/test/java/io/github/toanhp123/finote/data/notification/local/dao/NotificationLogDaoTest.kt`
- Test: `app/src/test/java/io/github/toanhp123/finote/data/notification/repository/NotificationCaptureRepositoryImplTest.kt`

**Interfaces:**
- Produces:
  - DAO: `@Query("SELECT * FROM notification_logs ORDER BY receivedAt DESC, id DESC LIMIT :limit") fun observeRecent(limit: Int): Flow<List<NotificationLogEntity>>`
  - Repository: `fun observeRecentLogs(limit: Int): Flow<List<NotificationLog>>` — maps with `toDomain()`, then `.mapStorageErrors()` (same as `ParserRuleRepositoryImpl.observeRules`).
  - `class ObserveNotificationLogsUseCase @Inject constructor(repository: NotificationCaptureRepository) { operator fun invoke(): Flow<List<NotificationLog>> }` — passes `LOG_LIMIT = 500` with `// shortcut: newest 500 only, add paging if dogfood logs grow past it`.

- [ ] **Step 1: Write the failing DAO test**

```kotlin
@Test fun `observeRecent returns newest first and stops at the limit`() = runTest {
    // receivedAt 1, 3, 2 with ids "a", "b", "c"; plus "d" with receivedAt 3 to check the id tie-break
    // insert via notificationLogEntity(...).copy(receivedAt = …)
    assertEquals(listOf("d", "b", "c"), dao.observeRecent(limit = 3).first().map { it.id })
}
```

And in `NotificationCaptureRepositoryImplTest`: `observeRecentLogs maps rows to domain` — insert one row with `parsedAmount`/`parsedType`, assert the first emission's `parsed?.amount`.

- [ ] **Step 2: Run tests to verify they fail**

Run `testDebugUnitTest --tests "*NotificationLogDaoTest" --tests "*NotificationCaptureRepositoryImplTest"` through `gradle-run`. Expected: compile failure.

- [ ] **Step 3: Implement the DAO query, the repository method, the use case; add `observeRecentLogs` to the fake (backed by a `MutableStateFlow<List<NotificationLog>>`)**

- [ ] **Step 4: Run tests to verify they pass**

Same command. Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add -A app/src/main/java/io/github/toanhp123/finote/data/notification app/src/main/java/io/github/toanhp123/finote/domain/notification app/src/test/java/io/github/toanhp123/finote
git commit -m "feat(notif): observe recent notification logs"
```

---

### Task 3: Corpus sample JSON

**Files:**
- Create: `app/src/main/java/io/github/toanhp123/finote/ui/settings/logs/logic/CorpusSample.kt`
- Test: `app/src/test/java/io/github/toanhp123/finote/ui/settings/logs/logic/CorpusSampleTest.kt`

**Interfaces:**
- Consumes: `NotificationLog.masked()` (Task 1).
- Produces: `fun corpusSampleJson(log: NotificationLog): String` — masks first, then a pretty-printed JSON object with keys in this order: `id` = `"real_" + log.id.take(8)`, `packageName`, `postTime`, `channelId` (omit when null), `title`, `content`, `expected` = `{"shouldIgnore": false}`. Built with `kotlinx.serialization.json.buildJsonObject`.

- [ ] **Step 1: Write the failing tests**

```kotlin
private val json = Json { ignoreUnknownKeys = true }

@Test fun `json decodes as a corpus item with masked text`() {
    val log = testLog(id = "abcdef123456", packageName = "com.VCB", title = "VCB", content = "SD TK 0123 +50,000 VND")
    val item = json.decodeFromString<NotificationCorpusItem>(corpusSampleJson(log))
    assertEquals("real_abcdef12", item.id)
    assertEquals("com.VCB", item.packageName)
    assertEquals("SD TK 1111 +11,111 VND", item.content)
    assertFalse(item.expected.shouldIgnore)
}

@Test fun `json keeps line breaks and quotes`() {
    val log = testLog(content = "a \"b\"\nc")
    assertEquals("a \"b\"\nc", json.decodeFromString<NotificationCorpusItem>(corpusSampleJson(log)).content)
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run `testDebugUnitTest --tests "*CorpusSampleTest"`. Expected: compile failure.

- [ ] **Step 3: Implement `corpusSampleJson` in `CorpusSample.kt`**

- [ ] **Step 4: Run tests to verify they pass**

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/io/github/toanhp123/finote/ui/settings/logs/logic app/src/test/java/io/github/toanhp123/finote/ui/settings/logs/logic
git commit -m "feat(ui): build a masked corpus sample"
```

---

### Task 4: Notification logs screen

**Files:**
- Create: `app/src/main/java/io/github/toanhp123/finote/ui/settings/logs/NotificationLogsContract.kt`
- Create: `app/src/main/java/io/github/toanhp123/finote/ui/settings/logs/NotificationLogsViewModel.kt`
- Create: `app/src/main/java/io/github/toanhp123/finote/ui/settings/logs/NotificationLogsScreen.kt`
- Create: `app/src/main/java/io/github/toanhp123/finote/ui/settings/logs/component/LogDetailSheet.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Test: `app/src/test/java/io/github/toanhp123/finote/ui/settings/logs/NotificationLogsViewModelTest.kt`

**Interfaces:**
- Consumes: `ObserveNotificationLogsUseCase` (Task 2), `ObserveParserRulesUseCase` + `summarizeBanks` (existing, for package → bank name), `corpusSampleJson` (Task 3).
- Produces (Contract):
  - `data class NotificationLogsState(val logs: List<NotificationLog> = emptyList(), val bankNames: Map<String, String> = emptyMap(), val selected: NotificationLog? = null)` with `val canCopy: Boolean get() = selected != null && (selected.title.isNotBlank() || selected.content.isNotBlank())`. Holds `NotificationLog` itself so `toString()` stays text-free.
  - `sealed interface NotificationLogsIntent { data class LogClicked(val id: String); data object SheetDismissed; data object CopyClicked; data object BackClicked }`
  - `sealed interface NotificationLogsEffect { data class CopySample(val json: String) /* override toString: "CopySample" */; data object Close }`
  - `NotificationLogsRoute(viewModel, snackbarState: FinoteSnackbarState, onClose: () -> Unit, modifier)` — on `CopySample` writes a `ClipData` to the clipboard; on API 33+ sets `ClipDescription.EXTRA_IS_SENSITIVE = true` in its extras; then shows snackbar `logs_copied`.

Strings (`translatable="false"`): `settings_logs_title` "Nhật ký thông báo", `settings_logs_subtitle` "Chỉ có ở bản debug và dogfood", `logs_empty` "Chưa có thông báo nào", `logs_copy` "Sao chép mẫu đã che số", `logs_copy_note` "Kiểm tra và xóa tên riêng trước khi commit.", `logs_copied` "Đã sao chép mẫu". Field labels in the sheet are the builder's, same rule.

- [ ] **Step 1: Write the failing ViewModel tests** (`MainDispatcherRule`, fake repos as in `SettingsViewModelTest`)

```kotlin
@Test fun `state follows the log flow and names banks from rules`()
    // logs flow emits [testLog(id = "a", packageName = "com.VCB")]; rules have bankName "Vietcombank" for com.VCB
    // assert state.logs ids == ["a"], state.bankNames["com.VCB"] == "Vietcombank"

@Test fun `log click opens the sheet and dismiss closes it`()
    // LogClicked("a") → selected?.id == "a"; SheetDismissed → selected == null

@Test fun `copy emits masked json`()
    // select a log with content "SD 0123"; CopyClicked → effect CopySample whose json contains "SD 1111" and not "0123"

@Test fun `copy is off for a blank log`()
    // select testLog(title = "", content = ""); assertFalse(state.canCopy); CopyClicked emits nothing (expectNoEvents)

@Test fun `state toString hides text`()
    // select testLog(title = "SECRET-T", content = "SECRET-C"); assertFalse(state.toString().contains("SECRET"))
```

- [ ] **Step 2: Run tests to verify they fail**

Run `testDebugUnitTest --tests "*NotificationLogsViewModelTest"`. Expected: compile failure.

- [ ] **Step 3: Implement Contract and ViewModel**

`BaseViewModel` with `@IsDebuggable`; collect logs and rules with `catchStorage`, as `NotificationCaptureSettingsViewModel` does. A `LogClicked` id no longer in `logs` is ignored. When the flow drops the selected log, `selected` follows the new list (null if gone).

- [ ] **Step 4: Implement Screen and `LogDetailSheet`**

Screen: `FinoteTopBar` with back; `LazyColumn` of rows: time (`receivedAt`, local zone), bank name or package, status, failure reason if any, amount through `MoneyText` if parsed, content `maxLines = 1` with ellipsis; empty text `logs_empty`. Sheet (`FinoteBottomSheet`, like `AccountSheet`): title, content, `postTime`, `receivedAt`, `channelId`, `contentHash`, `matchedRuleId`, every `ParsedFields` field, `linkedTransactionId`, then `logs_copy` button (`enabled = state.canCopy`) and `logs_copy_note`. Components from `docs/design-system.md`.

- [ ] **Step 5: Run tests to verify they pass**

Same command. Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/io/github/toanhp123/finote/ui/settings/logs app/src/main/res/values/strings.xml app/src/test/java/io/github/toanhp123/finote/ui/settings/logs
git commit -m "feat(ui): notification log screen"
```

---

### Task 5: Dogfood build type, gate, entry, docs

**Files:**
- Modify: `app/build.gradle.kts` (buildTypes, `buildFeatures.buildConfig = true`)
- Create: `app/src/main/java/io/github/toanhp123/finote/domain/common/ToolsEnabled.kt`
- Modify: `app/src/main/java/io/github/toanhp123/finote/di/BuildModule.kt`
- Modify: `app/src/main/java/io/github/toanhp123/finote/ui/settings/SettingsContract.kt`, `SettingsViewModel.kt`, `SettingsScreen.kt`
- Modify: `app/src/main/java/io/github/toanhp123/finote/ui/navigation/NavKeys.kt`, `FinoteNavHost.kt`
- Modify: `app/src/main/java/io/github/toanhp123/finote/MainActivity.kt`
- Modify: `docs/roadmap.md`, `docs/architecture.md`
- Test: `app/src/test/java/io/github/toanhp123/finote/ui/settings/SettingsViewModelTest.kt`

**Interfaces:**
- Consumes: `NotificationLogsRoute` (Task 4).
- Produces:
  - `@Qualifier @Retention(RUNTIME) annotation class ToolsEnabled` (same shape as `IsDebuggable`).
  - `@Provides @ToolsEnabled fun provideToolsEnabled(): Boolean = BuildConfig.TOOLS_ENABLED` in `BuildModule`.
  - `SettingsState.isToolsEnabled: Boolean`, `SettingsIntent.LogsClicked`, `SettingsEffect.OpenNotificationLogs`; `SettingsViewModel(observeRules, @ToolsEnabled isToolsEnabled: Boolean, @IsDebuggable isDebuggable: Boolean)`.
  - `@Serializable data object NotificationLogs : NavKey`; `FinoteNavHost(shouldStartAtSetup: Boolean, isToolsEnabled: Boolean, modifier)`; `entry<NotificationLogs>` registered only inside `if (isToolsEnabled)`; `SettingsRoute` gets `onOpenLogs`.
  - `MainActivity`: `@Inject @ToolsEnabled @JvmField var isToolsEnabled: Boolean = false`, passed to `FinoteNavHost`.

- [ ] **Step 1: Write the failing tests** in `SettingsViewModelTest`

```kotlin
@Test fun `logs row hidden when tools are off`() =
    assertFalse(SettingsViewModel(ObserveParserRulesUseCase(repo), isToolsEnabled = false, isDebuggable = true).state.value.isToolsEnabled)

@Test fun `logs click emits OpenNotificationLogs`()
    // isToolsEnabled = true; LogsClicked → effects.test { assertEquals(SettingsEffect.OpenNotificationLogs, awaitItem()) }
```

Update the existing `SettingsViewModel(...)` calls in this file to pass `isToolsEnabled`.

- [ ] **Step 2: Run tests to verify they fail**

Run `testDebugUnitTest --tests "*SettingsViewModelTest"`. Expected: compile failure.

- [ ] **Step 3: Add the build type and flag in `app/build.gradle.kts`**

```kotlin
buildFeatures { compose = true; buildConfig = true }
buildTypes {
    debug { buildConfigField("boolean", "TOOLS_ENABLED", "true") }
    release { /* existing optimization block */ buildConfigField("boolean", "TOOLS_ENABLED", "false") }
    create("dogfood") {
        initWith(getByName("release"))
        buildConfigField("boolean", "TOOLS_ENABLED", "true")
        signingConfig = signingConfigs.getByName("debug")
        matchingFallbacks += "release"
    }
}
```

No `applicationIdSuffix`, no `isDebuggable`.

- [ ] **Step 4: Implement qualifier, provider, Settings row (under the capture section, only when `state.isToolsEnabled`), NavKey, nav entry, MainActivity wiring**

- [ ] **Step 5: Run tests to verify they pass**

Same command. Expected: PASS.

- [ ] **Step 6: Build the dogfood APK**

Run `assembleDogfood` through `gradle-run`. Expected: `exit_status` 0 and a signed APK under `app/build/outputs/apk/dogfood/`.

- [ ] **Step 7: Update docs**

- `docs/roadmap.md`: step 8 row → ✅, type "Công cụ (bản debug và dogfood)"; note 8 says "debug và dogfood"; step 9 first item → "Cài bản `dogfood` (`assembleDogfood`): giống release, ký bằng khóa debug. Bản release ký khóa Play sau này không cài đè được lên bản này." Section 1 "Bước tiếp theo" → bước 9.
- `docs/architecture.md`: one line under the Hilt/`di` rules: "`BuildConfig.TOOLS_ENABLED` is read only in `di/BuildModule.kt` and reaches code as `@ToolsEnabled`. A tool screen has no Settings row and no nav entry when it is false."

- [ ] **Step 8: Full gate**

Run `testDebugUnitTest`, then `qualityGate`, through `gradle-run`. Expected: both `exit_status` 0. Read the summary; fix any Konsist, detekt or lint finding in this branch.

- [ ] **Step 9: Commit**

```bash
git add app/build.gradle.kts app/src/main docs/roadmap.md docs/architecture.md app/src/test/java/io/github/toanhp123/finote/ui/settings/SettingsViewModelTest.kt
git commit -m "feat(build): dogfood build type with the notification log screen"
```

---

## After the tasks

- `reviewer` on the whole branch (`ponytail:ponytail-review`), against the spec and this plan.
- Ask the user before the squash into `dev`.
- Not run: `connectedDebugAndroidTest`, a device check that the row shows in `dogfood` and not in `release` (step 9 does it).
