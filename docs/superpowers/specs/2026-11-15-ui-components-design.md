# Shared UI components (cleanup branch 5): design

Date: 2026-11-15. Status: approved in chat.
Branch: `chore/ui-components`. Source: findings A12–A15 in `docs/superpowers/handoff/2026-11-10-cleanup-before-p2d.md`.

## Why

Features copy UI pieces instead of using shared components, write raw numbers outside the theme, format dates with a fixed pattern for every locale, and miss a few accessibility labels. The user's rule from branch 4 holds: no hard-coded UI that bypasses the design system. Fix it once, before P2d builds more screens on top.

## Goals

1. One shared component per copied piece: switch row, section, listener-access wiring.
2. No motion, size or alpha number outside the theme in the files A13 names.
3. Every date in `ui/` is formatted in `ui/common/format/`, by locale. Konsist enforces it.
4. TalkBack reads what each cash-entry selector is for, the picker check is an icon, and the setup screen announces when access is granted.

## Non-goals

- `BalanceHeroCard` stays (the v0.3 dashboard needs it), like `FinoteDatePicker` in branch 4.
- The "Small" findings in the handoff doc.
- Private alpha constants outside A13's files (`InlineOverlay`, `FinoteBrushes`).

## Anti-goals

- A shared component with options no caller uses.
- A Vietnamese date that looks different from today's `Th 7, 15/11`.

## Approaches

One sensible way for each finding; the alternatives were weaker:

- Dimmed ignored account: a new token `colors.mutedAlpha = 0.6f`, not `disabledAlpha` (0.38). The row is still tappable, so it is not disabled, and 0.38 makes the text hard to read.
- Date pattern: a locale skeleton (`MMddEEE`, `yMMddEEE`) through `android.icu.text.DateTimePatternGenerator`, not a fixed pattern per language. CLDR gives `EEE, dd/MM` for Vietnamese (unchanged) and `EEE, MM/dd` for English. The skeletons are padded because ICU `MEd` drops the Vietnamese zero padding, and the anti-goal forbids changing the Vietnamese look.

## Design

### A12: shared pieces

- `ui/common/component/control/FinoteSwitchRow.kt`: `FinoteSwitchRow(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier)`. A `FinoteListRow` that is `toggleable(role = Role.Switch)`; the trailing `FinoteSwitch` gets `onCheckedChange = null`. Replaces `TrashOption`'s body and the auto-update row in Capture.
- `ui/common/component/display/FinoteSection.kt`: `FinoteSection(title: String, modifier: Modifier = Modifier, content: @Composable () -> Unit)`. A heading (`labelMedium`, `onSurfaceVariant`, `heading()` semantics) above a full-width `FinoteCard`. Replaces Capture's private `Section` and the two heading + card pairs in `SettingsScreen`. Spacing between heading and card is `spacing.sm`; the top padding of Capture's `Section` moves to the callers' arrangement only if the screenshots show a difference.
- `ui/common/NotificationListenerPermission.kt` gains:
  - `@Composable fun ReportListenerAccess(onResult: (Boolean) -> Unit)`: calls `onResult(isNotificationListenerEnabled(context))` on every resume.
  - `fun openListenerSettings(context: Context)`: starts `Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS`.
  - `SettingsRoute`, `NotificationCaptureSettingsRoute`, `SetupRoute` use both.
- Each new public component carries `@FinotePreviews` (Konsist) and a row in `docs/design-system.md`.

### A13: tokens

- `FinoteMotion` gains `pageSlideMillis = 300`, `pageFadeOutMillis = 90`, `pageFadeInMillis = 210`, `pageSlideOffset: Dp = 30.dp`. `CashEntryScreen` uses them; `PAGE_*` constants and `Dp(30f)` go.
- `FinoteColors` gains `mutedAlpha = 0.6f`. `AccountsSettingsScreen` uses it; `IGNORED_ALPHA` goes.
- `FALLBACK_HEIGHT_FRACTION` stays: it is layout logic, not a style value.
- Token table in `docs/design-system.md` updated.

### A14: dates

- `ui/common/format/DateFormat.kt` (name left to the builder) holds every date formatter in `ui/`:
  - day label: skeleton `MMddEEE` in the current year, `yMMddEEE` otherwise. `dayLabel` calls it.
  - cash-entry date: `FormatStyle.MEDIUM`, as today.
  - capture last check: `FormatStyle.SHORT` date-time, as today.
- `TransactionRowTextTest` moves to Robolectric (ICU needs Android) and expects `Mon, 11/02` and `Tue, 11/04/2025` for English, plus Vietnamese `Th 2, 02/11` (this year) and `Th 3, 04/11/2025` (other year), and ja and zh-CN cases.
- Konsist: a new check in `DesignSystemKonsistTest`: no `DateTimeFormatter` in `ui/` outside `ui/common/format/`. `data/` keeps its parser (`RuleFeedCodec`).

### A15: accessibility

- `SelectorRow` takes `label: String` (`cash_entry_account`, `cash_entry_category`, `cash_entry_date`, `cash_entry_note`) and sets `contentDescription = "$label, $text"` with merged semantics.
- `PickerRow` draws `ic_check` (`contentDescription = null`, `selected` semantics already there) instead of `"✓"`.
- The `SetupActions` granted row gets `liveRegion = LiveRegionMode.Polite`.

### Screenshots

Images will change (English dates, possibly section spacing). Regenerate with `updateDebugScreenshotTestDefaultTestSuite`; the user looks at the changed images before the commit.

## Constraints

- Konsist, detekt and `qualityGate` stay green before the squash.
- Strings come from `strings.xml`; no new raw numbers outside the theme.
- Commit scope `ui`; no AI attribution.

## Left to the builder

- File names in `ui/common/format/`.
- Whether `ReportListenerAccess` keys its effect on the callback or on `Unit`.
- Test names.
