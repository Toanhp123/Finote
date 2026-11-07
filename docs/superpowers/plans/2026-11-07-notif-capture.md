# Notif Capture (P2a) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Capture bank and e-wallet notifications into an encrypted `notification_logs` table (`PENDING` or metadata-only `IGNORED_SPAM`), plus a first-run setup screen and a banner that ask for Notification Listener access.

**Architecture:** A thin `automation/FinoteNotificationListener` reads extras into a pure `RawNotification` and calls `CaptureNotificationUseCase` (domain), one notification at a time behind a fair `Mutex`. Filtering data lives in a new `parser_rules` table seeded by version. UI adds `ui/setup` (MVI) and a `Setup(isFromOnboarding)` nav key.

**Tech Stack:** Kotlin, Room 3 (KSP), Hilt, Compose Material 3, Navigation3, kotlinx-serialization-json, JUnit4, Robolectric, Turbine, kotlinx-coroutines-test.

**Spec:** `docs/superpowers/specs/2026-11-07-notif-capture-design.md`

## Global Constraints

- Branch `feature/notif-capture` from `dev`. Commit scope `notif` (or `db`, `ui`). English, imperative, lowercase subject ≤ 72 chars. No `Co-Authored-By`.
- Commit steps run only after the user approves each commit (`CLAUDE.md`). Stage files by name.
- Layers per `docs/architecture.md`: `domain` imports no `android.*`/`androidx.*`; `automation` imports only `domain`; `ui` never imports `data`.
- No `android.util.Log`, `println`, `printStackTrace` anywhere. Never put notification text, amounts, or account numbers in any output.
- `@Suppress` only for `LongMethod`, `CyclomaticComplexMethod`, `LongParameterList`, with `// why:`.
- No new dependencies. JSON uses `kotlinx-serialization-json` (already `implementation`).
- Every UI string in `values/` (vi), `values-en`, `values-zh-rCN`, `values-ja`.
- UI uses components from `docs/design-system.md` only; no new shared component.
- Run Gradle via the `gradle-run` skill, wrapper `'.\gradlew.bat'` from Bash.
- `FinoteDatabase` goes 3 → 4 through the existing destructive fallback; no migration.

## Review Focus

1. A transaction notification whose merchant or note contains a keyword fragment ("Kichi Hotpot", "Luu Dai Trang") must stay `PENDING` → Task 1 and Task 4 tests.
2. A bank sends the same notification again on reboot rescan → one row only → Task 3 DAO test and Task 2 `Duplicate` test.
3. Two spam notifications at the same `postTime` with different text → two rows (hash from original text) → Task 2 test.
4. Setup reached from the banner, user presses "Để sau" → returns to the transaction list, not a fresh `Overview` → Task 7 `finishSetup` test.
5. A user-custom rule must survive a seed version bump → Task 4 seeder test.

---

### Task 1: Pure text helpers

**Files:**
- Create: `app/src/main/java/io/github/toanhp123/finote/domain/notification/NotificationText.kt`
- Test: `app/src/test/java/io/github/toanhp123/finote/domain/notification/NotificationTextTest.kt`

**Interfaces:**
- Produces (all top-level, pure):
  - `fun normalizeForMatch(text: String): String`
  - `fun firstMatchingKeyword(text: String, keywords: List<String>): String?` — returns the original (un-normalized) keyword
  - `fun contentHash(packageName: String, postTime: Long, title: String, content: String): String`
  - `fun notificationContent(bigText: CharSequence?, textLines: Array<CharSequence>?, text: CharSequence?): String?`

- [ ] **Step 1: Write failing tests** `NotificationTextTest`:
  - `normalizeForMatch("Mã XÁC THỰC Đăng")` == `"ma xac thuc dang"`; NFD input `"Mã"` → `"ma"`.
  - `firstMatchingKeyword("Thanh toan tai Kichi Hotpot", listOf("OTP"))` == `null`.
  - `firstMatchingKeyword("Chuyen tien cho Luu Dai Trang", listOf("ưu đãi"))` == `null`.
  - `firstMatchingKeyword("Mã OTP: 123456", listOf("OTP"))` == `"OTP"`; `"Smart OTP thanh cong"` → `"OTP"`.
  - `firstMatchingKeyword("Ưu đãi và chúc mừng", listOf("chúc mừng", "ưu đãi"))` == `"chúc mừng"` (list order wins, not text order).
  - `firstMatchingKeyword("x", emptyList())` == `null`.
  - `contentHash` is 64 lowercase hex chars, same input → same output; changing `postTime`, `title`, or `content` changes it; `("a","b")` vs `("ab","")` in title/content differ.
  - `notificationContent("  \n ", null, "SD TK -50,000")` == `"SD TK -50,000"`; `notificationContent(null, arrayOf("l1", " ", "l2"), "x")` == `"l1\nl2"`; all blank → `null`; result is trimmed.
- [ ] **Step 2: Run** `testDebugUnitTest --tests '*NotificationTextTest'` → FAIL (unresolved).
- [ ] **Step 3: Implement.** `normalizeForMatch`: `Normalizer.normalize(s, NFD)`, remove `\p{InCombiningDiacriticalMarks}+`, `đ/Đ → d`, `lowercase(Locale.ROOT)`. `firstMatchingKeyword`: normalize text once; for each keyword in order, regex `(?<![\p{L}\p{N}])` + `Pattern.quote(normalizeForMatch(k))` + `(?![\p{L}\p{N}])`, skip blank keywords. `contentHash`: SHA-256 (`MessageDigest`) of `packageName\u0000postTime\u0000title\u0000content`, UTF-8, lowercase hex. `notificationContent`: per spec 4, `isNotBlank` checks, lines filtered then joined by `\n`, `trim()`.
- [ ] **Step 4: Run** same tests → PASS.
- [ ] **Step 5: Commit** `feat(notif): add notification text helpers`.

### Task 2: Capture use case

**Files:**
- Create in `domain/notification/model/`: `RawNotification.kt`, `ParserRule.kt`, `NotificationLog.kt`, `NotificationStatus.kt`, `CaptureResult.kt`
- Create: `domain/notification/repository/NotificationCaptureRepository.kt`, `domain/notification/usecase/CaptureNotificationUseCase.kt`
- Test: `app/src/test/java/io/github/toanhp123/finote/domain/notification/usecase/CaptureNotificationUseCaseTest.kt` (+ `FakeNotificationCaptureRepository.kt` beside it)

**Interfaces:**
- Consumes: Task 1 helpers.
- Produces:
  - `data class RawNotification(val packageName: String, val channelId: String?, val title: String, val content: String, val postTime: Long)`
  - `data class ParserRule(val id: String, val packageName: String, val bankName: String, val channelIdAllowlist: List<String>?, val ignoreKeywords: List<String>, val priority: Int, val isUserCustom: Boolean, val version: Int)`
  - `enum class NotificationStatus { PENDING, CONFIRMED, DEDUPLICATED, PAIRED_TRANSFER, IGNORED_SPAM, FAILED }`
  - `data class NotificationLog(val id: String, val packageName: String, val channelId: String?, val title: String, val content: String, val contentHash: String, val postTime: Long, val receivedAt: Long, val status: NotificationStatus, val failureReason: String?)` (P2b adds parsed fields)
  - `sealed interface CaptureResult { data object Stored; data class Ignored(val reason: String); data object Duplicate; data object NotTracked }`
  - `interface NotificationCaptureRepository { suspend fun getRules(packageName: String): List<ParserRule>; suspend fun insertLog(log: NotificationLog): Boolean }` — `getRules` sorted by `priority`, then `id`; `insertLog` false when `contentHash` exists.
  - `class CaptureNotificationUseCase @Inject constructor(repository: NotificationCaptureRepository, clock: Clock) { suspend operator fun invoke(raw: RawNotification): CaptureResult }`

- [ ] **Step 1: Write failing tests** (fake repo stores logs in a list, rejects duplicate hash, `Clock.fixed`):
  - `package without rules is not tracked` → `NotTracked`, no log.
  - `keyword hit stores metadata only` → `Ignored("keyword:OTP")`; log status `IGNORED_SPAM`, `title == ""`, `content == ""`, `failureReason == "keyword:OTP"`, `contentHash == contentHash(pkg, time, origTitle, origContent)`.
  - `keyword in title is caught` (title "Ưu đãi", content clean).
  - `two rules merge keywords in priority order` → rule priority 1 `["chúc mừng"]`, priority 2 `["ưu đãi"]`, text has both → reason `keyword:chúc mừng`.
  - `null allowlists do not filter channels`; `all allowlists set blocks other channel` → `Ignored("channel:PROMOTION")`; `null channelId with filter on` → `Ignored("channel:null")`; `one null allowlist disables channel filter`.
  - `clean notification is stored pending` → `Stored`, full title/content, `receivedAt == clock.millis()`, `id` is a UUID string.
  - `same notification twice is duplicate`.
  - `two spam notifications at same postTime differ` → two logs.
- [ ] **Step 2: Run** `--tests '*CaptureNotificationUseCaseTest'` → FAIL.
- [ ] **Step 3: Implement** per spec 3.1. Merge keywords into a `LinkedHashSet` in rule order. Channel filter only when `rules.all { it.channelIdAllowlist != null }`.
- [ ] **Step 4: Run** → PASS.
- [ ] **Step 5: Commit** `feat(notif): add capture notification use case`.

### Task 3: Room tables and repository

**Files:**
- Create in `data/notification/`: `ParserRuleEntity.kt`, `ParserRuleDao.kt`, `ParserRuleMapper.kt`, `NotificationLogEntity.kt`, `NotificationLogDao.kt`, `NotificationLogMapper.kt`, `NotificationCaptureRepositoryImpl.kt`
- Modify: `data/FinoteDatabase.kt` (entities + `parserRuleDao()`, `notificationLogDao()`, `version = 4`), `di/DatabaseModule.kt` (two DAO providers), `di/RepositoryModule.kt` (bind repository)
- Test: `app/src/test/java/io/github/toanhp123/finote/data/notification/NotificationLogDaoTest.kt`, `ParserRuleMapperTest.kt`, `NotificationCaptureRepositoryImplTest.kt`
- Generated: new `app/schemas/.../4.json` (commit it)

**Interfaces:**
- Consumes: Task 2 models and repository interface.
- Produces:
  - `ParserRuleEntity` table `parser_rules`, columns per spec 2.1 (`channelIdAllowlist: String?`, `ignoreKeywords: String` as JSON), `Index("packageName")`.
  - `NotificationLogEntity` table `notification_logs`, all spec 3.7 columns, `status: NotificationStatus`, `Index("packageName")`, `Index(value = ["contentHash"], unique = true)`.
  - `ParserRuleDao.getByPackage(packageName: String): List<ParserRuleEntity>` (`ORDER BY priority, id`)
  - `NotificationLogDao.insert(log): Long` with `onConflict = IGNORE` (`-1` on duplicate); `getAll(): List<NotificationLogEntity>` for tests.
  - `ParserRuleEntity.toDomain()`, `ParserRule.toEntity()`, `NotificationLog.toEntity()`.

- [ ] **Step 1: Write failing tests:**
  - `NotificationLogDaoTest` (Robolectric, `InMemoryDatabaseRule()`): insert returns rowId > 0; second insert with same `contentHash` returns `-1` and `getAll().size == 1`.
  - `ParserRuleMapperTest`: `"[\"OTP\",\"ưu đãi\"]"` → `listOf("OTP","ưu đãi")`; `channelIdAllowlist = "[]"` → `null`; `null` → `null`; round trip `toEntity().toDomain()` equal for non-empty lists.
  - `NotificationCaptureRepositoryImplTest`: `insertLog` true then false for the same hash; `getRules` returns rules sorted by priority.
- [ ] **Step 2: Run** `--tests '*notification*'` → FAIL.
- [ ] **Step 3: Implement.** Mapper uses a file-level `Json` instance and `encodeToString`/`decodeFromString<List<String>>`. Unset parsed columns stay `null` in `toEntity()`.
- [ ] **Step 4: Run** tests → PASS, then `assembleDebug` → BUILD SUCCESSFUL (KSP, schema 4 exported).
- [ ] **Step 5: Commit** `feat(db): add parser rule and notification log tables`.

### Task 4: Rule seed and corpus gate

**Files:**
- Create: `data/notification/SystemParserRule.kt` (`val SystemParserRules: List<ParserRuleEntity>`), `data/notification/ParserRuleSeeder.kt`
- Modify: `data/FinoteDatabaseFactory.kt` (`.addCallback(ParserRuleSeeder())`), `app/src/test/.../data/InMemoryDatabaseRule.kt` (add `seedParserRules: Boolean = false`)
- Test: `data/notification/ParserRuleSeederTest.kt`, `data/notification/SystemParserRulesCorpusTest.kt`

**Interfaces:**
- Consumes: Task 3 entity/DAO/mapper, Task 2 use case, `NotificationCorpusFixtures` (existing test fixture).
- Produces: `class ParserRuleSeeder : RoomDatabase.Callback()` (`onCreate` and `onOpen`).

Seed rows (all `channelIdAllowlist = null`, `priority = 0`, `isUserCustom = false`, `version = 1`): `vcb_filter_v1`/`com.VCB`/Vietcombank, `mb_filter_v1`/`com.mbmobile`/MB Bank, `momo_filter_v1`/`com.mservice.momotransfer`/MoMo, `tcb_filter_v1`/`vn.com.techcombank.bb.app`/Techcombank. Same starting `ignoreKeywords` for all four, derived from the corpus `shouldIgnore` samples:
`OTP, mã xác thực, đăng nhập, cảnh báo, thiết bị, ưu đãi, chúc mừng, voucher, nhắc nợ, nhắc hạn, nhanh tay, phê duyệt, rinh quà, đổi quà, sinh lời`.
The corpus test is the judge: edit the list until it passes; never weaken the test.

- [ ] **Step 1: Write failing tests:**
  - `ParserRuleSeederTest` (`InMemoryDatabaseRule(seedParserRules = true)`): new DB has exactly the 4 ids; opening twice does not duplicate; a row with lower `version` and `isUserCustom = false` is overwritten on `onOpen`; a row with `isUserCustom = true` and lower version is kept unchanged; a row with equal version is kept.
  - `SystemParserRulesCorpusTest`: build `CaptureNotificationUseCase` over a fake repo serving `SystemParserRules.map { it.toDomain() }`; feed each corpus item as `RawNotification(pkg, channelId, title, bigText ?: content, postTime)`. Assert every `ignoredOrSpam()` item → `Ignored` (100%) and every `financialTransactions()` item → `Stored` (0 false positives). Failure message lists only item ids.
  - Same test: `"Thanh toan QR tai Kichi Hotpot"` and `"ND: Luu Dai Trang chuyen tien"` from `com.VCB` → `Stored`.
- [ ] **Step 2: Run** `--tests '*ParserRuleSeederTest' --tests '*SystemParserRulesCorpusTest'` → FAIL.
- [ ] **Step 3: Implement** seeder with raw SQL like `SystemCategorySeeder`: read `id, version, isUserCustom` of seed ids; collect rows to write (missing, or not custom and older); if none, return; else `BEGIN IMMEDIATE`, `INSERT OR REPLACE` each, `COMMIT`, `ROLLBACK` in `finally` on failure. `onCreate` writes all without a transaction of its own.
- [ ] **Step 4: Run** → PASS. Then run full `testDebugUnitTest` → PASS (existing seeder/DB tests untouched).
- [ ] **Step 5: Commit** `feat(notif): seed bank filter rules`.

### Task 5: Notification listener service

**Files:**
- Create: `app/src/main/java/io/github/toanhp123/finote/automation/FinoteNotificationListener.kt`
- Modify: `app/src/main/AndroidManifest.xml`

**Interfaces:**
- Consumes: `CaptureNotificationUseCase`, `RawNotification`, `notificationContent`.

- [ ] **Step 1: Manifest.** Add:
  ```xml
  <service
      android:name=".automation.FinoteNotificationListener"
      android:exported="true"
      android:label="@string/app_name"
      android:permission="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE">
      <intent-filter>
          <action android:name="android.service.notification.NotificationListenerService" />
      </intent-filter>
  </service>
  ```
- [ ] **Step 2: Implement** `@AndroidEntryPoint class FinoteNotificationListener : NotificationListenerService()` with `@Inject lateinit var capture: CaptureNotificationUseCase`.
  - Scope: `CoroutineScope(SupervisorJob() + Dispatchers.Main + CoroutineExceptionHandler { _, _ -> })` — one comment: a failed notification is dropped; the next `onListenerConnected` rescan retries it. `Dispatchers.Main` because Room suspend calls leave the main thread themselves and detekt `InjectDispatcher` flags `IO`. A `Mutex` (fair, FIFO) around each `capture(raw)` keeps order.
  - `onNotificationPosted(sbn)` → `scope.launch { process(sbn) }`.
  - `onListenerConnected()` → `scope.launch { activeNotifications.orEmpty().forEach { process(it) } }` (the call itself is inside the launch, so a ROM exception reaches the handler).
  - `process(sbn)`: skip if `sbn.packageName == packageName`, `sbn.isOngoing`, `flags and FLAG_GROUP_SUMMARY != 0`, or `flags and FLAG_FOREGROUND_SERVICE != 0`. Read `getCharSequence(EXTRA_TITLE)`, `getCharSequence(EXTRA_BIG_TEXT)`, `getCharSequenceArray(EXTRA_TEXT_LINES)`, `getCharSequence(EXTRA_TEXT)`; `notificationContent(...) ?: return`; build `RawNotification(sbn.packageName, notification.channelId, title?.toString().orEmpty(), content, sbn.postTime)`; `mutex.withLock { capture(raw) }`.
  - `onDestroy()` → `scope.cancel()`.
- [ ] **Step 3: Verify** `assembleDebug` and `lintDebug` → BUILD SUCCESSFUL. Run `testDebugUnitTest --tests '*architecture*'` → PASS (automation imports only domain).
- [ ] **Step 4: Commit** `feat(notif): add notification listener service`.

### Task 6: Setup state (domain, data, ViewModel)

**Files:**
- Create: `domain/setup/repository/SetupRepository.kt`, `domain/setup/usecase/CompleteSetupUseCase.kt`, `data/setup/SetupRepositoryImpl.kt`, `ui/setup/SetupContract.kt`, `ui/setup/SetupViewModel.kt`
- Modify: `di/RepositoryModule.kt`
- Test: `app/src/test/.../ui/setup/SetupViewModelTest.kt`, `app/src/test/.../data/setup/SetupRepositoryImplTest.kt`

**Interfaces:**
- Produces:
  - `interface SetupRepository { fun isSetupDone(): Boolean; fun markSetupDone() }` — synchronous; SharedPreferences `app_setup`, key `setup_done`.
  - `class CompleteSetupUseCase @Inject constructor(repo: SetupRepository) { operator fun invoke() }`
  - `SetupContract.kt`: `enum class SetupStep { INTRO, RETRY, GRANTED }`; `data class SetupState(val step: SetupStep = SetupStep.INTRO, val hasLaunchedSettings: Boolean = false)`; `sealed interface SetupIntent { data class Resumed(val isGranted: Boolean); data object EnableClicked; data object LaterClicked }`; `sealed interface SetupEffect { data object OpenListenerSettings; data object Finished }`.
  - `@HiltViewModel class SetupViewModel @Inject constructor(completeSetup: CompleteSetupUseCase) : BaseViewModel<SetupState, SetupIntent, SetupEffect>`; `GRANTED_HOLD_MS = 800L`.

Spec change: the permission check moves to the screen (`Resumed(isGranted)`), so no `ListenerPermissionChecker` interface; the ViewModel stays pure and testable. Update spec 5.1 in Task 8.

- [ ] **Step 1: Write failing tests** (`MainDispatcherRule`, Turbine, fake `SetupRepository`):
  - `first resume without access keeps intro`.
  - `first resume with access grants and finishes` → state `GRANTED`, `markSetupDone` called, after `advanceTimeBy(GRANTED_HOLD_MS)` effect `Finished`.
  - `enable opens settings` → effect `OpenListenerSettings`, `hasLaunchedSettings == true`.
  - `resume after settings without access shows retry`.
  - `later marks done and finishes` → `markSetupDone` called, effect `Finished`.
  - `SetupRepositoryImplTest` (Robolectric): default `false`; after `markSetupDone()` a new instance reads `true`.
- [ ] **Step 2: Run** `--tests '*Setup*'` → FAIL.
- [ ] **Step 3: Implement.** A second `Resumed(true)` while already `GRANTED` does nothing (no double `Finished`).
- [ ] **Step 4: Run** → PASS.
- [ ] **Step 5: Commit** `feat(ui): add setup state and view model`.

### Task 7: Setup screen and navigation

**Files:**
- Create: `ui/common/NotificationAccess.kt`, `ui/setup/SetupScreen.kt`, `ui/navigation/SetupKey.kt`
- Modify: `ui/navigation/FinoteNavHost.kt`, `MainActivity.kt`, `res/values*/strings.xml` (4 files)
- Test: `app/src/test/.../ui/navigation/SetupKeyTest.kt`

**Interfaces:**
- Consumes: Task 6 contract and ViewModel, `SetupRepository`.
- Produces:
  - `fun isNotificationListenerEnabled(context: Context): Boolean` (`NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)`) and `@Composable fun rememberNotificationListenerEnabled(): Boolean` (re-checks in `LifecycleResumeEffect`).
  - `@Serializable data class Setup(val isFromOnboarding: Boolean) : NavKey`
  - `fun finishSetup(backStack: MutableList<NavKey>, key: Setup)` — `isFromOnboarding`: clear, add `Overview`; else `removeLastOrNull()`.
  - `fun FinoteNavHost(shouldStartAtSetup: Boolean, modifier: Modifier = Modifier)`; start key `Setup(true)` when `shouldStartAtSetup`.
  - `@Composable fun SetupRoute(viewModel: SetupViewModel, onFinished: () -> Unit, modifier: Modifier = Modifier)`.

- [ ] **Step 1: Write failing test** `SetupKeyTest`: onboarding `[Setup(true)]` → `[Overview]`; banner `[Overview, Transactions, Setup(false)]` → `[Overview, Transactions]`.
- [ ] **Step 2: Run** → FAIL.
- [ ] **Step 3: Implement** `SetupKey.kt`. Then the screen per spec 5.1: intro text, three commitments, how-to line, restricted-settings hint, primary button (`setup_enable` / `setup_retry` by step), secondary `setup_later`, `GRANTED` shows `setup_granted`. Route sends `Resumed(isNotificationListenerEnabled(context))` from `LifecycleResumeEffect`; `OpenListenerSettings` → `startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))`; `Finished` → `onFinished()`. Screen ≤ 120 lines per composable; add a `@FinotePreviews` preview like other screens.
- [ ] **Step 4: Wire.** `FinoteNavHost`: `entry<Setup> { key -> SetupRoute(hiltViewModel(), onFinished = { finishSetup(backStack, key) }) }`. `MainActivity`: `@Inject lateinit var setupRepository: SetupRepository`; `FinoteNavHost(shouldStartAtSetup = !setupRepository.isSetupDone())`.
- [ ] **Step 5: Strings** (vi shown; translate for en, zh-rCN, ja): `setup_title` "Tự động ghi giao dịch", `setup_body` "Finote đọc thông báo của app ngân hàng để tự ghi giao dịch.", `setup_point_banks` "Chỉ đọc app đã hỗ trợ: Vietcombank, MB Bank, MoMo, Techcombank.", `setup_point_otp` "Bỏ qua OTP và mã xác thực, không lưu.", `setup_point_local` "Dữ liệu nằm trên máy, đã mã hóa, không gửi đi đâu.", `setup_howto` "Tìm Finote trong danh sách và bật.", `setup_restricted_hint` "Nếu thấy \"Restricted setting\": Thông tin ứng dụng → ⋮ → Cho phép cài đặt hạn chế.", `setup_enable` "Bật tự động ghi", `setup_retry` "Thử lại", `setup_later` "Để sau, tôi nhập tay", `setup_granted` "Đã bật".
- [ ] **Step 6: Run** `SetupKeyTest` → PASS; `assembleDebug` → BUILD SUCCESSFUL.
- [ ] **Step 7: Commit** `feat(ui): add first-run setup screen`.

### Task 8: Listener-off banner, docs, gate

**Files:**
- Create: `ui/transactions/component/ListenerOffBanner.kt`
- Modify: `ui/transactions/TransactionListScreen.kt` (`TransactionListRoute` gains `onOpenSetup: () -> Unit`), `ui/navigation/FinoteNavHost.kt`, `res/values*/strings.xml`, `docs/superpowers/specs/2026-11-07-notif-capture-design.md` (5.1 checker → `Resumed(isGranted)`; section 4 drop "log exception class name": the project bans `Log`, failures are dropped silently and retried on rescan)

**Interfaces:**
- Consumes: `rememberNotificationListenerEnabled()`, `Setup(false)`.

- [ ] **Step 1: Implement** `ListenerOffBanner(onClick: () -> Unit, modifier: Modifier = Modifier)` on `FinoteCard`, strings `transactions_listener_off` "Tự động ghi đang tắt. Bấm để bật." (4 languages). `TransactionListRoute` shows it above the list when `!rememberNotificationListenerEnabled()`. Nav host passes `onOpenSetup = { backStack.add(Setup(isFromOnboarding = false)) }`.
- [ ] **Step 2: Edit spec** as listed under Files.
- [ ] **Step 3: Run** `qualityGate` via `gradle-run` → `exit_status` 0. Read its summary; fix and re-run on failure.
- [ ] **Step 4: Commit** `feat(ui): add listener-off banner to transaction list`.
- [ ] **Step 5: Manual check (user, device):** grant via setup; a VCB/MB/MoMo notification creates a `PENDING` row; reboot with a pending notification on the shade → no duplicate row.
