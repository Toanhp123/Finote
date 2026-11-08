# Rule OTA design (P2b-3, `feature/rule-ota`)

Spec module 4 (L41–43), table 3.8 (L185–207), MoSCoW "Remote Rule OTA" (L269–270), acceptance 8 (L417–418).
Roadmap P2a debt #9.

## Scope

In this branch:

- Download a signed rule feed from the Finote GitHub repo, on demand or daily, only after the user turns it on.
- Merge it into `parser_rules` without touching rules with `isUserCustom = true`.
- Re-run filter and parser on `FAILED` rows after every successful update.
- P2a debt #9: parsing leaves the main thread.
- A Settings tab with one entry, and a "Notification capture" settings page.

Out of scope: picking banks one by one, Regex Sandbox and the rule builder (`feature/failed-inbox`), a bank detail page, key rotation, `minAppVersion` in the feed, an on-device regex time limit (see "Regex safety").

## Privacy promise

The promise to the user is "your financial data never leaves this phone". It is not "this app has no network".

- The app adds the `INTERNET` permission. It is used only to download rules.
- Rule download is **off by default**. With it off, the app makes no automatic network call; only a tap on "Check now" downloads.
- The only network code is `HttpParserRuleFeed`. It sends one plain `GET` to one fixed URL:
  - no query string
  - no body
  - `User-Agent: Finote`, so the default `Dalvik/… (Linux; Android …; <model> Build/…)` header does not leak the device model
- A Konsist rule keeps imports from `java.net`, `javax.net`, `android.net.http`, `android.webkit`, `okhttp3` and `io.ktor` out of every other file.
- `network_security_config.xml` forbids cleartext traffic.
- Play Data safety: no data collected, no data shared.
- No notification text, balance, account number or rule content is written to Logcat. Errors are logged as reason codes only (`bad_signature`).

## Feed

### Location

One file in the Finote repo: `rules/v1/feed.json`.

- It is read through `https://raw.githubusercontent.com/Toanhp123/Finote/main/rules/v1/feed.json`.
- Only `main` is served, so a rule change goes live when it reaches `main`, not `dev`.
- `v1` is the feed schema. A change an old app cannot read goes to `v2`; old installs keep reading `v1`.

One file, not a JSON file next to a `.sig` file. The CDN in front of `raw.githubusercontent.com` caches each URL on its own, so two files can be served from two different commits for a few minutes. That would give a false `bad_signature`.

### Envelope

```json
{ "payload": "<base64 of the payload bytes>", "signature": "<base64 of the DER ECDSA signature>" }
```

The signature covers the decoded payload bytes. Git line-ending conversion (`core.autocrlf`) cannot change bytes inside base64, so a file checked out on Windows still verifies.

### Payload

```json
{
  "schemaVersion": 1,
  "rules": [
    {
      "id": "vcb_account_v1",
      "packageName": "com.VCB",
      "bankName": "Vietcombank",
      "channelIdAllowlist": null,
      "ignoreKeywords": ["OTP"],
      "priority": 0,
      "version": 2,
      "regexPattern": "…",
      "fallbackTransactionType": null,
      "dateFormatPattern": null,
      "isActive": true
    }
  ]
}
```

- The feed has no `isUserCustom`. A feed rule is always stored with `isUserCustom = false`.
- `updatedAt` is set by the app when it writes the row.
- To retire a rule, publish it with `isActive: false` and a higher `version`. A rule missing from the feed is left in the database.

### One source of truth

The payload is generated from `SystemParserRules`, never edited by hand. So every OTA rule is also an APK seed rule, and it passes the corpus tests and the regex safety test (below) before it is signed.

To publish:

1. Edit `SystemParserRules` and raise the rule's `version`.
2. Run `testDebugUnitTest --tests '*RuleFeedSigningTool*' -PsignRuleFeed=true`. The opt-in test reads the private key at `~/.finote/finote-rules.pem`, serializes `SystemParserRules`, signs the payload, and writes `rules/v1/feed.json`. Without the flag it is skipped, so normal test runs never rewrite the file.
3. Merge to `main`.

Two unit tests guard this:

- The committed `feed.json` verifies with the public key in the app.
- Its decoded rules equal `SystemParserRules`, so a forgotten re-sign fails the build.

### Signature

- ECDSA P-256, `SHA256withECDSA` (available on API 26, no new dependency).
- The private key never enters the repo.
- The public key is a base64 X.509 constant in `HttpParserRuleFeed`.

### Checks

Any failure rejects the **whole** file and writes nothing. A bad rule in a signed file is a publishing bug; applying half a file is worse than applying none.

1. HTTP 200.
2. The body is read in chunks with a hard cap of 256 KB. It stops and fails at byte 256 K + 1, whatever `Content-Length` says (it is `-1` for chunked or gzip responses).
3. Connect and read timeouts are 10 s each.
4. The envelope decodes, and the signature verifies.
5. `schemaVersion == 1` and `rules` is not empty.
6. For each rule:
   - `id`, `packageName` and `bankName` are not blank, and `id` is unique in the file.
   - `version >= 1`.
   - No `ignoreKeywords` entry and no `channelIdAllowlist` entry is blank. A blank keyword would match every notification and silence the whole bank.
   - `regexPattern` compiles and has an `amount` group. Group names are read with the parser's `GROUP_NAME` scan, since `Pattern.namedGroups()` needs API 34.
   - The pattern has a `sign` group, or `fallbackTransactionType` is set.
   - `fallbackTransactionType` is `INCOME`, `EXPENSE` or null.
   - `dateFormatPattern` is null, or `DateTimeFormatter.ofPattern` accepts it.

## Regex safety

On Android, `java.util.regex` runs inside ICU (native code) on a `String` copy of the input, and nothing in Java can stop a native match partway. A `CharSequence` wrapper that checks a deadline in `charAt` only works on the desktop JVM. So the app does not try to time-limit a match on the device.

Instead:

- **Source:** every system and OTA rule comes from `SystemParserRules`. It is checked on the JVM before signing by a unit test that runs each rule against adversarial inputs (long runs of digits, separators, spaces, and text with no match). Each run must finish within 50 ms.
- **On the device:** parsing runs off the main thread, so a slow rule cannot cause an ANR (debt #9). A pathological rule would block one background thread. The code marks this limit with `shortcut: no on-device regex time limit; add one if users can type raw regex`.
- **Rule builder:** the future `feature/failed-inbox` builder only produces patterns from a fixed set of safe pieces, and runs the same safety test before it saves a rule.

## Merge

Reuses `outdatedRules()` from `ParserRuleSeeder.kt`: a feed rule is written when its `id` is missing, or when the stored copy is a system rule (`isUserCustom = false`) with a lower `version`. So:

- A user rule is never overwritten (acceptance 8).
- An old signed file cannot downgrade a rule. Replaying it is harmless.
- The APK seeder keeps working: a rule raised by OTA is not lowered by the bundled seed on the next open, and a newer APK seed still wins over an older OTA rule.

The merge runs in one Room `@Transaction`.

## Components

```
["Check now" button] ──┐
[periodic 24 h, network]┴─► RuleUpdateWorker (work/)
                              └─► UpdateParserRulesUseCase (domain)
                                    1. feed.fetch()                     → checked List<ParserRule>
                                    2. repository.applyRules(rules)     → @Transaction, outdatedRules()
                                    3. reparseFailed(feed packages)     → ReparseFailedLogsUseCase
                                    4. Outcome
```

### Domain (`domain/notification`, no `android.*`)

- `ParserRuleFeed` (interface): `suspend fun fetch(): List<ParserRule>`. It throws `RuleFeedException(reason: RuleFeedError)`, where the reason is `NETWORK` or `INVALID`.
- `UpdateParserRulesUseCase`: returns `Updated(count: Int)` (the number of rules written) or one of `UpToDate`, `Network`, `Invalid`, `Error`. `Updated` needs `count > 0`.
- `classifyNotification(raw, rules)`: the filter-then-parse step that `CaptureNotificationUseCase` does today, moved into one pure function. It returns the status, `failureReason`, matched rule and parsed fields. Capture and re-parse both call it, so they cannot drift.
- `ReparseFailedLogsUseCase(packages: Set<String>): Int`:
  - Loads the `FAILED` rows of those packages.
  - Runs `classifyNotification` on each with the package's current rules.
  - Writes the new outcome when it is no longer `FAILED`.
  - It is a separate use case so the future rule builder in `feature/failed-inbox` can call it.
- `CaptureNotificationUseCase` and `ReparseFailedLogsUseCase` run the parse in `withContext(Dispatchers.Default)` (debt #9). The listener scope stays on Main.

### Re-parse

- After every successful fetch, re-parse **all** `FAILED` rows of every package in the feed, not only packages whose rules changed. If the app dies after the merge and before the re-parse, the next run still finds those rows. No "re-parse pending" flag is needed, and `FAILED` rows are few.
- The outcome follows today's capture rules:
  - **A rule matches:** the row becomes `PENDING`, with `matchedRuleId` and `parsed*` set and `failureReason = null`. P2c turns these rows into transactions.
  - **A new channel or keyword filter blocks it:** the row becomes `IGNORED_SPAM`, with `failureReason = "keyword:…"` or `"channel:…"`. The text is kept, as for any keyword block, so a false positive can be recovered. If a newer build adds a secret keyword, a re-parsed row that now matches it becomes `IGNORED_SPAM` with `secret:<kw>` and its text is blanked, as at capture.
  - **Still no match:** the row stays as it is.
- Dates are not an issue: the parser reads no date (`ParsedFields` has none), and each row keeps its original `postTime`, which P2c uses.
- The write runs `UPDATE … WHERE id = :id AND status = 'FAILED'`, so a row that changed status meanwhile is left alone.
- `PENDING` and `IGNORED_SPAM` rows are never re-parsed.
- A row that throws is skipped and stays `FAILED`. It does not fail the update.

### Data (`data/notification`)

- `HttpParserRuleFeed` implements `ParserRuleFeed`:
  - `HttpURLConnection` with `User-Agent: Finote`, the chunked 256 KB cap, and 10 s timeouts. No new network library.
  - `verifyAndDecode(envelope: ByteArray): List<ParserRule>` is a pure function, tested without a server. It decodes with kotlinx-serialization, which is already in the catalog.
- New repository functions:
  - `applyRules(rules): Int`
  - `getFailedLogs(packages)`
  - `updateFailedOutcome(id, outcome): Boolean`
- New DAO queries:
  - `ParserRuleDao.observeAll(): Flow<List<ParserRuleEntity>>`
  - `NotificationLogDao.getFailed(packages)`
  - `NotificationLogDao.updateFailedOutcome(…)`
- `RuleUpdatePrefs` (SharedPreferences, same pattern as `SetupRepositoryImpl`):
  - Stores `autoUpdateEnabled`, `lastCheckedAt` and `lastResult`. The worker writes the last two.
  - Exposes `Flow`s built with `callbackFlow`. The listener is held inside the flow and unregistered in `awaitClose`, so it is not lost to the `WeakHashMap` that SharedPreferences keeps.

### Scheduling (`work/`)

Per `docs/architecture.md`, workers live in `work/`, which depends on `domain` only. `RuleUpdateScheduler` is a domain interface (`domain/notification`); `WorkRuleUpdateScheduler` in `work/` implements it with WorkManager, so `ui` never imports `androidx.work`.

`RuleUpdateWorker` is a `@HiltWorker` `CoroutineWorker`. An input flag `isManual` decides its error policy.

`RuleUpdateScheduler`:

| Call | Work | Constraint | Policy |
|---|---|---|---|
| `enablePeriodic()` | unique periodic `rule-update-periodic`, 24 h, `isManual = false` | `NetworkType.CONNECTED` | `ExistingPeriodicWorkPolicy.KEEP`, so a second call does not reset the period |
| `disablePeriodic()` | cancels `rule-update-periodic` | | |
| `checkNow()` | unique one-time `rule-update-now`, `isManual = true` | none | `ExistingWorkPolicy.KEEP` |

- `checkNow()` has no network constraint on purpose. Without a network it runs at once, fails fast, and shows "No network" instead of waiting in `ENQUEUED` forever.
- `checkNow()` does not turn on the periodic work.
- A new periodic work runs its first period right away. The UI treats either work as "checking" (see UI).
- The two works can still overlap. That is harmless: the merge is idempotent, and the outcome write only moves `FAILED` rows, so a second run writes nothing new.

Wiring:

- New dependencies, through the version catalog: `androidx.work:work-runtime-ktx`, `androidx.hilt:hilt-work`, and the KSP compiler `androidx.hilt:hilt-compiler`.
- The Application implements `Configuration.Provider` with `HiltWorkerFactory`.
- The manifest removes the default `WorkManagerInitializer`.

## Error handling

| Case | `lastResult` | Periodic run | Manual run |
|---|---|---|---|
| No network, timeout, non-200 | `NETWORK` | `Result.retry()` | `Result.failure()` |
| Over 256 KB, bad envelope, bad signature, bad schema, bad rule | `INVALID` | `Result.failure()` | `Result.failure()` |
| Database error during merge | `ERROR`; the transaction rolls back | `Result.retry()` | `Result.failure()` |
| One row fails to re-parse | unchanged; the row stays `FAILED` | update still succeeds | update still succeeds |
| Success | `UPDATED(n)` or `UP_TO_DATE` | `Result.success()` | `Result.success()` |

Every run writes `lastCheckedAt` and `lastResult` before it returns, so the result line is current even when the run gives up.

## UI

### Settings tab (`ui/settings/SettingsScreen`)

Replaces `ComingSoonScreen` on the Settings tab. It lists only entries that exist, with no "coming soon" rows.

```
Settings
 AUTOMATIC CAPTURE
 ┌───────────────────────────────────────┐
 │ 🔔 Capture from notifications        › │
 │    On · 4 banks                        │
 └───────────────────────────────────────┘
```

- `FinoteListRow` with `leading`, `subtitle`, a chevron `trailing`, and `onClick`.
- The subtitle shows whether notification access is on, and the number of supported banks (defined below).

### Notification capture page (`ui/settings/capture/NotificationCaptureSettingsScreen`)

A new `NavKey`, pushed on top of Settings. It has a `FinoteTopBar` with a back button and no bottom bar.

```
← Capture from notifications

 ACCESS
 Read notifications                 On  ›     → system listener settings

 RECOGNITION PATTERNS
 Update daily                       [off]     FinoteSwitch
 Downloads recognition patterns only. Nothing leaves this phone.
 Check now                            ⟳       spinner while checking
 Last check: 10/11 08:30 · 2 patterns updated

 SUPPORTED BANKS
 Vietcombank        3 patterns · v2
 MB Bank            2 patterns · v1
 MoMo               5 patterns · v1 · has your edits
 Techcombank        2 patterns · v1
```

- Each screen has its own MVI contract and ViewModel: `SettingsContract` + `SettingsViewModel`, and `NotificationCaptureSettingsContract` + `NotificationCaptureSettingsViewModel`.
- **Read notifications:** reuses `isNotificationListenerEnabled()` and re-reads it on every resume, as the setup screen does.
- **Checking:** `isChecking` is true while `rule-update-now` is `ENQUEUED` or `RUNNING`, or while `rule-update-periodic` is `RUNNING`. While it is true, "Check now" shows a spinner and cannot be tapped.
- **Result line:** follows the `RuleUpdatePrefs` flows. It shows one of `UPDATED(n)`, `UP_TO_DATE`, `NETWORK`, `INVALID`, `ERROR`, and nothing before the first check.
- **Supported banks:**
  - Built from `ParserRuleDao.observeAll()`, using **active rules only** (`isActive = true`).
  - Grouped by `packageName`, not `bankName`, so two spellings of one bank stay one row. The row shows the `bankName` of the rule with the highest `version`.
  - Each row shows the active rule count, the highest active `version`, and "has your edits" when any rule has `isUserCustom`.
  - A package with no active rule is not listed. The list is read only, with no chevron.
- **Navigation:** helpers are pure functions in `NavBackStack.kt`, for example `openNotificationCaptureSettings(backStack)`. A second call while the page is on top does nothing.
- **Strings:** go in all four `strings.xml` files.
- **Components:** from `docs/design-system.md`: `FinoteListRow`, `FinoteSwitch`, `FinoteTopBar`, `FinoteCard`.
- **After `feature/failed-inbox`:** that branch adds one line under the switch: "Rather stay offline? Teach Finote a notification in the Failed Inbox."

## Testing

- `verifyAndDecode`:
  - a valid envelope
  - one flipped byte in the payload, and one in the signature
  - an envelope that is not JSON, or has bad base64
  - wrong `schemaVersion`, or empty `rules`
  - a regex that does not compile
  - a missing `amount` group
  - no `sign` group and no fallback type
  - a duplicate `id`
  - a blank `bankName`
  - `version = 0`
  - `ignoreKeywords = [""]` or `[" "]`, or a blank channel id
  - a bad `dateFormatPattern`
- Body cap: a stream longer than 256 KB with `Content-Length: -1` stops at the cap and gives `INVALID`.
- `HttpParserRuleFeed` sends `User-Agent: Finote` and no query string (a local `ServerSocket`, or `MockWebServer` if it is already on the classpath).
- The committed `rules/v1/feed.json` verifies with the app's public key, and its rules equal `SystemParserRules`.
- Regex safety: every rule of `SystemParserRules` finishes within 50 ms on each adversarial input.
- `classifyNotification` gives the same results as today's capture. The existing `CaptureNotificationUseCaseTest` and corpus tests stay green.
- `UpdateParserRulesUseCase` with fakes:
  - a user rule is not overwritten
  - a lower version does not overwrite
  - a feed error changes nothing
  - re-parse runs for every package in the feed
- `ReparseFailedLogsUseCase`:
  - `FAILED` becomes `PENDING` with parsed fields
  - `FAILED` that a new keyword blocks becomes `IGNORED_SPAM` and keeps its text
  - `PENDING` and `IGNORED_SPAM` rows are untouched
  - a row that throws is skipped
- DAO on the in-memory database:
  - `updateFailedOutcome` changes only `FAILED` rows
  - `applyRules` keeps `isUserCustom` rows
- `RuleUpdateWorker` with `TestListenableWorkerBuilder`:
  - `NETWORK` gives `retry` when periodic, and `failure` when manual
  - `INVALID` gives `failure` in both
  - every run writes `lastResult`
- The ViewModels, with Turbine:
  - turning the switch on and off calls the scheduler
  - `isChecking` follows both works
  - the result line follows the prefs flow
  - the bank list counts active rules only, groups by package, and names the bank from the highest version
- Navigation: opening the capture page twice leaves one copy on top.
- Konsist: only `HttpParserRuleFeed` imports the network packages listed in "Privacy promise".
