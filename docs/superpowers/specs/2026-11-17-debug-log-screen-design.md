# Design: notification log screen (roadmap step 8, `feature/debug-log-screen`)

## Why

Step 9 is one week of dogfood on a real phone. Every P2 path (capture, parse, settle, pair) has only run against the synthetic corpus (D4). During that week the user needs to:

1. **See** what the app did with each bank notification: captured or not, which status, why it failed, which rule matched, what it parsed. Without this the user can only guess from the transaction list.
2. **Collect samples** for the corpus. Steps 10 and 11 wait on real VCB, MB, MoMo and Techcombank notifications, anonymized.

The screen is a dogfood tool, not a user feature. Fixing a failed log in-app (fill a form, teach a regex, re-parse) is step 13 (`failed-inbox`).

## Research (2026-11-17)

- **Debug drawer** libraries (Hyperion, Beagle, Chucker): `debugImplementation` plus a no-op release artifact. Debug build only.
- **Dogfood build type** (`internal`, `qa`): release-like (R8, signed) with tools turned on. Common way to test on real phones.
- **User report flow** (PennyWise, open-source SMS tracker): a user-started "Report a parsing problem" with the text prefilled; new-bank samples come through an issue template that requires masking (account and card digits → `X`, amounts and references → fake numbers of the same length, names replaced) and requires keeping wording, spacing and punctuation, because the parser is written against them. Finote's version of that in-app flow is step 13.

## Goals

- A screen listing `notification_logs`, newest first, with each row's status, failure reason, matched rule and parsed fields.
- A detail view per log with the full text and a **Copy masked sample** action.
- The screen is reachable in `debug` and in a new `dogfood` build type, never in `release`.
- `dogfood` is the APK installed for step 9: release-like and signed, so it installs.

## Non-goals

- No edit, re-parse, delete or status change from this screen (step 13).
- No export to a file and no share sheet. Clipboard only.
- No filter or search. Dogfood volume is small (hundreds of rows).
- No translation. The tool's strings are not shipped to users.

## Anti-goals

- Notification text, balances or account numbers reaching Logcat, crash reports, or any place other than the screen and the clipboard (on an explicit tap).
- Any route to the screen in a `release` APK.
- A copied sample that keeps a real account number, card number, amount or balance.

## Decided with the user

- **Purpose:** inspect logs and copy masked samples, for the dogfood week.
- **Approach A:** `dogfood` build type; the screen lives in `main` behind a `BuildConfig` flag.

## Approaches considered

| | Approach | Trade-off | Verdict |
|---|---|---|---|
| A | `dogfood` build type; screen in `main`; `BuildConfig.TOOLS_ENABLED` hides the entry | Least wiring; the screen follows the normal layers and Konsist. Screen code stays in the release APK but unreachable (R8 does not touch app code: `packageScope` is `androidx`, `kotlin`, `kotlinx` only) | **Picked** |
| B | `dogfood` build type; screen in a variant source set (`src/tools`) | Zero tool code in release. Needs a hook interface plus a release no-op, per-variant Hilt modules, and Konsist on another source set | Rejected: too much wiring for one read-only screen |
| C | No new build type; screen in release behind a hidden gesture | Least work; real users can reach it, so it becomes a feature (4 languages, overlaps step 13); signing still has to be solved apart | Rejected |

## Design

### 1. Build type

- `dogfood` in `app/build.gradle.kts`: `initWith(release)`, `signingConfig = signingConfigs.getByName("debug")`, `matchingFallbacks += "release"`. This also settles the step 9 item "release needs a `signingConfig`".
- `buildFeatures.buildConfig = true`. `buildConfigField("boolean", "TOOLS_ENABLED", ...)`: `true` in `debug` and `dogfood`, `false` in `release`.
- The flag reaches code only through Hilt, like `@IsDebuggable`: a qualifier `@ToolsEnabled` in `domain/common`, provided in `di/BuildModule.kt` from `BuildConfig.TOOLS_ENABLED`. Nothing else reads `BuildConfig`, so `SettingsViewModelTest` can cover both `true` and `false`. `@IsDebuggable` cannot be reused: it reads `FLAG_DEBUGGABLE`, which is `false` in `dogfood`.
- No `applicationIdSuffix`: dogfood data is real data and keeps the same app id. `debug` and `dogfood` built on the same machine share the debug key, so either installs over the other and keeps the database (same schema version). Build both on the same machine.
- R8 stays on as in `release`. App code is outside `packageScope`, so stack traces from app code are readable. No `debuggable`, no mapping upload. (Call made in review.)
- **Known limit:** a future Play-signed release cannot install over a debug-key dogfood APK. Moving from dogfood to the real release needs an uninstall, so data moves through export (step 27) or is lost. Accepted for v0.2: it is not on Play.

### 2. Data and domain

- `NotificationLogDao`: one new query returning `Flow<List<NotificationLogEntity>>`, ordered `receivedAt DESC, id DESC`, `LIMIT 500`. Comment: `shortcut: newest 500 only, add paging if dogfood logs grow past it`.
- Repository: an `observeLogs()` read on the notification side, mapped with the existing `toDomain()`. The builder picks whether it goes on `NotificationCaptureRepository` or a small read-only repository, per `docs/architecture.md`.
- Use case `ObserveNotificationLogsUseCase`, following the existing `Observe…UseCase` shape.
- **Masking** is a pure domain function in `domain/notification/service` that returns the log with title and content masked. A second step turns it into the copy text.
  - **Copy text is one JSON object in the shape of `NotificationCorpusItem`** (`app/src/test/.../domain/notification/NotificationCorpusFixtures.kt`, file `fixtures/notification_corpus.json`): `id` (new placeholder), `packageName`, `postTime`, `channelId`, `title`, `content`, and `"expected": {"shouldIgnore": false}` for the user to fill (`shouldIgnore` is required by the corpus loader). JSON keeps line breaks and quotes inside the text exactly (`\n`, `\"`), so the sample pastes into the corpus file as is. Where the JSON encoding lives (domain has no serialization today) is the builder's call per `docs/architecture.md`.
  - Only title and content are masked. `packageName` stays real: rules match on it, and it holds no personal data.
  - Only ASCII `'0'..'9'` is replaced, never `Char.isDigit()` or `\d` (they also match `²` and other Unicode digits). Each becomes `1`. `1` keeps amounts valid (no `0` amount, no leading zero) and keeps dates and times inside common range checks (`11/11/1111 11:11`). No trim, no whitespace change, no newline change: rules depend on exact spacing.
  - The acceptance check is the unit test below, not the corpus test: the corpus test compares parsed fields against expected values the user writes into the fixture by hand after pasting.
  - Names and free text (for example a transfer note with a person's name) cannot be found reliably. They are not masked. The detail view states this next to the copy button: "Check names before committing."
  - Unit tests: run masking over every item of the existing synthetic corpus. No ASCII digit of the input survives, a Unicode digit (`²`) is left alone, line breaks survive, and the masked text still matches the same rule and parses to the same type. The copy JSON decodes back into `NotificationCorpusItem` fields.

### 3. UI

Feature package `ui/settings/logs/` (or the name `docs/architecture.md` gives), on the standard UI feature template (Contract, ViewModel, Route/Screen, component files for sheets).

- **Entry:** in Settings, under the capture group, a row "Nhật ký thông báo" shown only when `@ToolsEnabled` is true. Opening it pushes a new `NavKey` (`NotificationLogs`), the same way as `NotificationCaptureSettings`. The `entry<NotificationLogs>` in `FinoteNavHost` is also registered only when the flag is true, so no path (for example a restored back stack) opens it in `release`.
- **List row:** time (`receivedAt`), app (package → bank name if known, else package), the status as plain text, failure reason when present, the parsed amount through `MoneyText` when present, and one line of the content (`maxLines = 1`, ellipsis) so a missed bank transaction stands out from an ad without opening it.
- **Detail sheet** (own file under the feature's `component/`): full title and content, `postTime` and `receivedAt`, `channelId`, `contentHash`, matched rule id, every parsed field, linked transaction id, and the **Sao chép mẫu đã che số** button with the note "Kiểm tra và xóa tên riêng trước khi commit."
- **Copy:** writes the copy JSON to the clipboard. On API 33+ it sets `ClipDescription.EXTRA_IS_SENSITIVE`, so the clipboard preview hides it; below 33 the flag is skipped (`minSdk` is 26, so guard with `Build.VERSION.SDK_INT`). A snackbar confirms.
- The copy button is disabled when title and content are both blank (secret rows, empty or group-summary notifications).
- Strings in `values/strings.xml` only, marked `translatable="false"`, in Vietnamese.
- The screen shows real, unmasked data. It is the user's own phone and the same data the transaction list already shows. No `FLAG_SECURE` here: that comes app-wide with app lock (step 28). (Call made in review.)
- `failureReason` values are codes (`no_rule_matched`, `secret:<kind>`, settlement reasons such as `ambiguous_account`), not notification text, so showing them in the list is safe. The builder confirms no reason embeds text.

### 4. Privacy

- `NotificationLog.toString()` already hides text. State, list items and the detail model hold `NotificationLog` itself. Any new model that holds title, content, amounts or masks must override `toString()` the same way. A unit test checks that the state's `toString()` contains no sample text.
- Nothing new is written to Logcat.

### 5. Docs

- `docs/roadmap.md`: mark step 8 done, and change its row and note from "chỉ bản debug" to "debug và dogfood". In step 9, replace the `signingConfig` item with "install `dogfood` (`assembleDogfood`)".
- DAO test reuses the existing in-memory Room rule. A read-only query needs no schema change or migration.
- `docs/architecture.md`: one line on the `TOOLS_ENABLED` flag (where it is read, and that a tool screen must not be reachable when it is `false`).

## Left to the builder

- Where `observeLogs()` lives, per `docs/architecture.md` and Konsist.
- Bank display names: reuse the package-to-name strings that exist today (debt N-13); otherwise show the package name. Not `PackageManager.getApplicationLabel`: the manifest has no `<queries>`, so on Android 11+ bank packages are not visible to the app.
- Whether `qualityGate` also builds `dogfood`. Default: no. Run `assembleDogfood` once in the branch.

## Verification

- Unit tests: `maskSample`, the DAO query (order and limit, Robolectric), the ViewModel (state from the flow, copy effect).
- `testDebugUnitTest`, `assembleDogfood` and `qualityGate` through `gradle-run`.
- On the device during step 9: the entry shows in `dogfood` and not in `release`.
