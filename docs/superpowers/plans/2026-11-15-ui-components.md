# Shared UI components (cleanup branch 5) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace copied UI pieces, raw numbers, fixed date patterns and missing accessibility labels with shared components, theme tokens and one date formatter.

**Architecture:** New shared components in `ui/common/component/{control,display}`, listener helpers in `ui/common/NotificationListenerPermission.kt`, tokens in `FinoteMotion`/`FinoteColors`, every `ui/` date formatter in `ui/common/format/`. Features switch to them; Konsist keeps dates in `format/`.

**Tech Stack:** Kotlin, Jetpack Compose, composeunstyled, Robolectric + `createComposeRule()`, Konsist, AGP screenshot tests.

**Spec:** `docs/superpowers/specs/2026-11-15-ui-components-design.md`

## Global Constraints

- Root package `io.github.toanhp123.finote`; app sources `app/src/main/java/io/github/toanhp123/finote/`.
- Strings come from `strings.xml`; no new raw numbers outside `ui/common/theme`.
- Every new public composable under `ui/common/component/` carries `@FinotePreviews` and a row in `docs/design-system.md`.
- `BalanceHeroCard` stays.
- Gradle only through the `gradle-run` skill, with `'.\gradlew.bat'` from Bash. Unit tests: `testDebugUnitTest`.
- Coders never commit; the controller commits each task with scope `ui` (or `build`/none for docs), no AI attribution.
- Never log notification content, balances or account numbers.

## Review Focus

1. TalkBack reads a selector or switch row twice (once for the row, once for the inner text): the row's semantics must replace its children's (`clearAndSetSemantics` or merged with the children cleared). Pinned in Task 1 and Task 6 tests by counting nodes with the text.
2. A date in the previous year shows the year, even when it is yesterday's neighbour (31 Dec seen on 2 Jan). Pinned in Task 5.
3. `ReportListenerAccess` reports on the first resume, not only after a pause, so the screen state is right on first open. Pinned in Task 3.
4. A section heading keeps `heading()` semantics after the move. Pinned in Task 2.
5. Vietnamese day labels look exactly as before (`Th 2, 02/11`). Pinned in Task 5.

---

### Task 1: `FinoteSwitchRow`

**Files:**
- Create: `ui/common/component/control/FinoteSwitchRow.kt`
- Modify: `ui/settings/accounts/AccountsSettingsDialogs.kt` (`TrashOption`), `ui/settings/capture/NotificationCaptureSettingsScreen.kt` (auto-update row in `PatternsSection`)
- Test: `app/src/test/java/io/github/toanhp123/finote/ui/common/component/control/FinoteSwitchRowTest.kt`

**Interfaces:**
- Produces: `@Composable fun FinoteSwitchRow(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier)`.

- [ ] **Step 1: Write the failing tests** (Robolectric, `createComposeRule()`, same setup as `FinoteBottomSheetTest`)
  - `tapping the row reports the opposite state`: `checked = false`, click node with text `"Trash"` → callback got `true`.
  - `the row is one switch node`: `onAllNodes(hasText("Trash")).assertCountEquals(1)`, that node `assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))` and `assertIsOff()`.
- [ ] **Step 2: Run, expect FAIL** (unresolved `FinoteSwitchRow`).
- [ ] **Step 3: Implement.** A `FinoteListRow` with `Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)` and trailing `FinoteSwitch(checked, onCheckedChange = null)`; KDoc carries the existing "row owns the tap" comment. Add a `@FinotePreviews` preview (off, on). Move both callers onto it; `TrashOption` keeps its plural title.
- [ ] **Step 4: Run `testDebugUnitTest`, expect PASS.**
- [ ] **Step 5: Commit** `refactor(ui): shared switch row`.

### Task 2: `FinoteSection`

**Files:**
- Create: `ui/common/component/display/FinoteSection.kt`
- Modify: `ui/settings/SettingsScreen.kt` (two heading + card pairs), `ui/settings/capture/NotificationCaptureSettingsScreen.kt` (delete private `Section`, callers pass `stringResource(...)`)
- Test: `app/src/test/java/io/github/toanhp123/finote/ui/common/component/display/FinoteSectionTest.kt`

**Interfaces:**
- Produces: `@Composable fun FinoteSection(title: String, modifier: Modifier = Modifier, content: @Composable () -> Unit)`.

- [ ] **Step 1: Write the failing test** `the title is a heading`: `onNodeWithText("Accounts").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))`, and content text is displayed.
- [ ] **Step 2: Run, expect FAIL.**
- [ ] **Step 3: Implement.** `Column(spacedBy(spacing.sm))` with the heading `Text` (`labelMedium`, `onSurfaceVariant`, `semantics { heading() }`) and `FinoteCard(Modifier.fillMaxWidth()) { content() }`. Capture's extra `padding(top = spacing.sm)` on the heading: drop it and give Capture's outer column the spacing it needs so its screenshots match today's; if they cannot match without an option, keep the look of Settings and note it in the report. `@FinotePreviews` preview.
- [ ] **Step 4: Run `testDebugUnitTest`, expect PASS.**
- [ ] **Step 5: Commit** `refactor(ui): shared section`.

### Task 3: Listener access helpers

**Files:**
- Modify: `ui/common/NotificationListenerPermission.kt`, `ui/settings/SettingsScreen.kt` (`SettingsRoute`), `ui/settings/capture/NotificationCaptureSettingsScreen.kt` (route), `ui/setup/SetupScreen.kt` (`SetupRoute`)
- Test: `app/src/test/java/io/github/toanhp123/finote/ui/common/NotificationListenerPermissionTest.kt`

**Interfaces:**
- Produces: `@Composable fun ReportListenerAccess(onResult: (Boolean) -> Unit)` and `fun openListenerSettings(context: Context)`.

- [ ] **Step 1: Write the failing tests**
  - `reports access on the first resume`: compose `ReportListenerAccess { results += it }` → `results == listOf(false)` (Robolectric has no listener enabled).
  - `opens the listener settings`: `openListenerSettings(context)` → `shadowOf(application).nextStartedActivity.action == Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS`. Pass an Activity or add `FLAG_ACTIVITY_NEW_TASK` only if Robolectric demands it; callers pass `LocalContext.current` (an Activity).
- [ ] **Step 2: Run, expect FAIL.**
- [ ] **Step 3: Implement** with `LifecycleResumeEffect` (keyed so a new callback does not re-fire; use `rememberUpdatedState`). Replace the three routes' `LifecycleResumeEffect` blocks and `startActivity(Intent(...))` calls; drop now-unused imports.
- [ ] **Step 4: Run `testDebugUnitTest`, expect PASS.**
- [ ] **Step 5: Commit** `refactor(ui): shared listener access wiring`.

### Task 4: Motion and alpha tokens

**Files:**
- Modify: `ui/common/theme/FinoteMotion.kt`, `ui/common/theme/color/FinoteColors.kt`, `ui/cashentry/CashEntryScreen.kt`, `ui/settings/accounts/AccountsSettingsScreen.kt`, `docs/design-system.md` (token table)

**Interfaces:**
- Produces: `FinoteMotion.pageSlideMillis = 300`, `pageFadeOutMillis = 90`, `pageFadeInMillis = 210`, `pageSlideOffset: Dp = 30.dp`; `FinoteColors.mutedAlpha = 0.6f`.

- [ ] **Step 1: Add the tokens** with one-line KDoc each (`mutedAlpha`: still usable but de-emphasized, unlike `disabledAlpha`).
- [ ] **Step 2: Use them.** Remove `PAGE_SLIDE_MILLIS`, `PAGE_FADE_OUT_MILLIS`, `PAGE_FADE_IN_MILLIS`, `PAGE_SLIDE_DP`, `Dp(...)`, `IGNORED_ALPHA`. Keep `FALLBACK_HEIGHT_FRACTION`.
- [ ] **Step 3: Run `testDebugUnitTest`, expect PASS** (Konsist included). No new test: values are unchanged.
- [ ] **Step 4: Commit** `refactor(ui): motion and muted alpha tokens`.

### Task 5: One date formatter

**Files:**
- Create: `ui/common/format/DateFormat.kt`
- Modify: `ui/transactions/component/TransactionRowText.kt` (`dayLabel`), `ui/cashentry/component/CashEntryPickers.kt` (`dateLabel`), `ui/settings/capture/NotificationCaptureSettingsScreen.kt` (`formatShortDateTime`), `app/src/test/java/io/github/toanhp123/finote/architecture/DesignSystemKonsistTest.kt`
- Test: `app/src/test/java/io/github/toanhp123/finote/ui/transactions/component/TransactionRowTextTest.kt` (move to Robolectric), new `app/src/test/java/io/github/toanhp123/finote/ui/common/format/DateFormatTest.kt` only if a function is not covered through `dayLabel`

**Interfaces:**
- Produces (names are the builder's call; these are the shapes): `fun formatDayDate(date: LocalDate, today: LocalDate, locale: Locale): String`, `fun formatMediumDate(date: LocalDate, locale: Locale): String`, `fun formatShortDateTime(epochMillis: Long, zone: ZoneId): String`.

- [ ] **Step 1: Update the tests first** (today = 2026-11-05 as the test has now, add `@RunWith(AndroidJUnit4::class)`):
  - English this year: `DayLabel.Date("Mon, 11/2")` for 2026-11-02.
  - English other year: `DayLabel.Date("Tue, 11/4/2025")` for 2025-11-04.
  - Vietnamese this year: `dayLabel(LocalDate.of(2026, 11, 2), today, Locale("vi"))` → `DayLabel.Date("Th 2, 02/11")`.
  - Year boundary: today 2026-01-02, date 2025-12-31 → English label contains `2025`.
  - Konsist: `ui files format dates only in the format package`: `filesIn(UI).filterNot { inPackage(UI_FORMAT) }` has no `\bDateTimeFormatter\b`.
- [ ] **Step 2: Run, expect FAIL** (English pattern and Konsist).
- [ ] **Step 3: Implement.** Day date: `DateTimePatternGenerator.getInstance(locale).getBestPattern(if same year "MEd" else "yMEd")` from `android.icu.text`, then `DateTimeFormatter.ofPattern(pattern, locale)`. If ICU output differs from the expected strings above, report the actual strings instead of bending the test. Medium date and short date-time keep their current `ofLocalized*` behaviour. Move the three callers.
- [ ] **Step 4: Run `testDebugUnitTest`, expect PASS.**
- [ ] **Step 5: Commit** `refactor(ui): one locale-aware date formatter`.

### Task 6: Accessibility fixes

**Files:**
- Modify: `ui/cashentry/component/CashEntryPickers.kt` (`SelectorRow`, its four callers, `PickerRow`), `ui/setup/SetupScreen.kt` (`SetupActions` granted row)
- Test: `app/src/test/java/io/github/toanhp123/finote/ui/cashentry/CashEntrySelectorsTest.kt` (compose the public composable that holds the selectors with a default `CashEntryState`)

**Interfaces:**
- Consumes: strings `cash_entry_account`, `cash_entry_category`, `cash_entry_date`, `cash_entry_note`; drawable `ic_check`.

- [ ] **Step 1: Write the failing tests**
  - `each selector reads its label`: a node with content description starting `"Danh mục, "` exists and has a click action; same for `"Tài khoản, "`, `"Ngày, "`, `"Ghi chú, "`. Use `onNode(hasContentDescription("Danh mục, ", substring = true))`.
  - `a selector is read once`: the selector's value text is not a separate node (`onAllNodesWithText(value).assertCountEquals(0)` under default merged tree, or 1 node that is the selector).
  - `the granted row is a polite live region`: compose `SetupScreen` with `SetupStep.GRANTED`, node with text from `setup_granted` (or its merged parent) has `LiveRegion == Polite`.
- [ ] **Step 2: Run, expect FAIL.**
- [ ] **Step 3: Implement.** `SelectorRow(label: String, text: String, ...)` with `clearAndSetSemantics { contentDescription = "$label, $text"; role = Role.Button; onClick(...) }` (or `semantics(mergeDescendants)` + `contentDescription` if click still works; the test decides). `PickerRow` trailing becomes `Icon(painterResource(R.drawable.ic_check), contentDescription = null, size iconMd, tint primary)`. Granted `Row` gets `Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }`.
- [ ] **Step 4: Run `testDebugUnitTest`, expect PASS.**
- [ ] **Step 5: Commit** `fix(ui): selector labels, check icon, granted announcement`.

### Task 7: Docs, screenshots, gate

**Files:**
- Modify: `docs/design-system.md` (rows for `FinoteSwitchRow`, `FinoteSection`; date rule pointing at `ui/common/format/`), `docs/roadmap.md` (mark cleanup branch 5 done), screenshot references.

- [ ] **Step 1: Docs.**
- [ ] **Step 2: Regenerate screenshots** with `updateDebugScreenshotTestDefaultTestSuite`; list changed PNGs. Controller shows them to the user before commit.
- [ ] **Step 3: Run `qualityGate`, expect exit 0.**
- [ ] **Step 4: Commit** after the user approves the images: `test(ui): screenshots for shared components` and `docs: mark cleanup branch 5 done`.
