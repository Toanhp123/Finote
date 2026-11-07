# Design system

Binding for every human and agent that writes UI in this repo. Machines enforce most rules. A rule that only a human can check is listed in `docs/architecture.md`, "Rules only `reviewer` can check", and repeated here.

Everything lives in `ui/common/`. A feature screen in `ui/<feature>` draws with tokens and shared components only.

## Tokens

Code outside `ui/common/theme/` reads tokens through `FinoteTheme`. It never calls `MaterialTheme`, never writes `Color(...)`, `.dp`, `.sp`, `TextStyle(...)`, `RoundedCornerShape(...)`, or `Brush.`.

| Token | File in `ui/common/theme/` (colors in `color/`) | Call |
|---|---|---|
| M3 color role | `FinoteTheme.kt` | `FinoteTheme.scheme.surface` |
| Income, expense, transfer | `color/FinoteColors.kt` (values in `color/BrandColors.kt`) | `FinoteTheme.colors.income.color` (also `onColor`, `container`, `onContainer`) |
| Hero card colors | `color/FinoteColors.kt` | `FinoteTheme.colors.hero.start`, `.end`, `.onHero`, `.chipContainer` |
| Glass colors | `color/FinoteColors.kt` | `FinoteTheme.colors.glass.fill`, `.solidFill`, `.stroke` |
| Screen background, card | `color/FinoteColors.kt` | `FinoteTheme.colors.background`, `FinoteTheme.colors.card` |
| Disabled alpha | `color/FinoteColors.kt` | `FinoteTheme.colors.disabledAlpha` (0.38) |
| Hero gradient | `FinoteBrushes.kt` | `FinoteTheme.brushes.hero` |
| Dark flag | `FinoteTheme.kt` | `FinoteTheme.isDark` |
| Text styles | `FinoteTypography.kt` | `FinoteTheme.typography.material.bodyLarge`, `.amountLarge`, `.amountMedium`, `.amountSmall` |
| Spacing | `FinoteSpacing.kt` | `FinoteTheme.spacing.xs` 4, `sm` 8, `md` 12, `lg` 16, `xl` 24, `xxl` 32 |
| Sizes | `FinoteSizes.kt` | `FinoteTheme.sizes.touchTarget`, `.iconMd`, `.avatar`, `.navBarHeight`, and more |
| Radii | `FinoteRadii.kt` | `FinoteTheme.radii.md` (`Dp`: `sm` 12, `md` 16, `lg` 20, `xl` 24) |
| Shapes | `FinoteShapes.kt` | `FinoteTheme.shapes.md` (`sm`, `md`, `lg`, `xl`, `full`, `sheet`) |
| Motion | `FinoteMotion.kt` | `FinoteTheme.motion.standard`, `.emphasized`, `.emphasizedDecelerate`, `.pressedScale` (0.96), `.shimmerMillis` |
| Category palette | `color/CategoryColors.kt` | `CategoryPalette` (14 colors; the only colors a user may pick) |

- `FinoteTheme { }` is the root. It uses Dynamic Color on Android 12+ and the brand scheme (seed `#006A6A`, TonalSpot, in `color/BrandColors.kt`) on API 26-30. `dynamicColor = false` forces the brand scheme.
- Income, expense, and transfer colors are fixed. They do not follow Dynamic Color. Hero, glass, background, and card follow the scheme.
- Amount styles use tabular figures (`tnum`) so digits line up.
- `CategoryIcon` takes the category color as `colorArgb: Int` and derives its tile and tint. `ContrastTest` checks every palette color.
- Depth is flat. No shadows. Layers are told apart by color: screen background, then card, then floating glass.
- The fixed money colors in `color/BrandColors.kt` and `shimmerBrush` in `FinoteBrushes.kt` are `internal` helpers of the theme.

## Components

All are in `ui/common/component/<group>/`, one main component per file; a small type that serves it sits in the same file. `modifier: Modifier = Modifier` is the first optional parameter. Signatures below are shortened.

| Group | What goes there |
|---|---|
| `control/` | Input and actions: `FinoteButton`, `FinoteIconButton`, `FinoteTextField`, `FinoteSwitch`, `FinoteChip`, `FinoteSegmented`. `FinoteButtonStyle` is in `FinoteButton.kt` |
| `overlay/` | Layers and messages: `FinoteBottomSheet`, `FinoteDialog`, `FinoteDatePicker`, `FinoteSnackbar` with its state in the same file, helper `DatePickerDates` |
| `layout/` | Screen frame: `FinoteScaffold`, `FinoteTopBar`, `FinoteNavBar` (`FinoteNavItem` is in the same file) |
| `display/` | Generic display with no finance meaning: `FinoteCard`, `FinoteListRow`, `FinoteEmptyState`, `FinoteSkeleton`, helper `ListRowLayout` |
| `finance/` | Finance widgets: `BalanceHeroCard`, `TransactionRow`, `MoneyText`, `CategoryIcon` |
| `glass/` | Glass effect infrastructure: `HazeLocals` (holds `LocalBlurEnabled`) |
| `preview/` | Preview infrastructure: `FinotePreviews` (holds `FinotePreviewSurface`) |

A component file sits directly in one group. No group has a sub-group. `DesignSystemKonsistTest` checks this.

| Component | Signature | Use when |
|---|---|---|
| `FinoteScaffold` | `(modifier, topBar, bottomBar?, snackbarState, content: (PaddingValues) -> Unit)` | Root of every screen. Owns the blur source. Content gets the padding that clears the floating bar, or only the system nav bar when `bottomBar` is null |
| `FinoteTopBar` | `(title, modifier, onBack?, actions)` | Screen title, back button, right-side actions |
| `FinoteNavBar` | `(items: List<FinoteNavItem>, selectedIndex, onSelect, modifier)` | Floating bottom navigation. Pass it as `bottomBar` |
| `FinoteBottomSheet` | `(isVisible, onDismiss, modifier, content)` | Pick or edit something without leaving the screen |
| `FinoteDialog` | `(isVisible, title, onDismiss, confirm, modifier, text?, dismiss?)` | Confirm a destructive or irreversible action |
| `FinoteDatePicker` | `(isVisible, selected: LocalDate, onConfirm: (LocalDate), onDismiss, modifier)` | Date input. Never expose `DatePickerState` |
| `FinoteCalendar` | `(selected: LocalDate, onPick: (LocalDate), modifier)` | Bare calendar page without chrome |
| `FinoteButton` | `(text, onClick, modifier, style, enabled, isLoading, leadingIcon?)` | Every button. `style`: `Primary`, `Secondary`, `Ghost`, `Danger` |
| `FinoteIconButton` | `(iconRes, contentDescription, onClick, modifier, enabled)` | Icon-only action. 48dp touch target |
| `FinoteTextField` | `(value, onValueChange, label, modifier, placeholder?, errorText?, maxLength?, singleLine, keyboardOptions, keyboardActions)` | Text input with label, hint, error, counter |
| `FinoteSwitch` | `(checked, onCheckedChange, modifier, enabled)` | On/off setting |
| `FinoteChip` | `(text, selected, onClick, modifier, iconRes?)` | Filter or single choice in a row |
| `FinoteSegmented` | `<T>(options, selected, onSelect, label, modifier)` | Pick one of 2-3 modes (expense, income, transfer) |
| `BalanceHeroCard` | `(label, balance, income, expense, modifier)` | The balance summary on a screen. See the gradient rule below |
| `FinoteCard` | `(modifier, onClick?, content)` | Group content on a separate surface |
| `FinoteListRow` | `(title, modifier, subtitle?, leading?, trailing?, onClick?)` | One list line. Trailing content drops below the title when the title gets too narrow |
| `TransactionRow` | `(title, subtitle, amount, kind, iconRes, colorArgb, modifier, onClick?)` | One transaction in a list |
| `CategoryIcon` | `(iconRes, colorArgb, contentDescription?, modifier)` | Category or account tile |
| `MoneyText` | `(amount: Long, kind: MoneyKind, modifier, style, shrinkToFit)` | Every amount on the UI. `shrinkToFit` shrinks down to 50% for large balances |
| `FinoteSnackbarState` | `rememberFinoteSnackbarState()`, then `suspend show(message, actionLabel?): Boolean` | Transient message. Pass the state to `FinoteScaffold`. `show` returns true when the user pressed the action |
| `FinoteEmptyState` | `(iconRes, title, modifier, message?, action?)` | A list or screen with no data |
| `FinoteSkeleton` | `(modifier, shape)` | Placeholder block while data loads |

- Features may import only `Text` and `Icon` from `androidx.compose.material3`. A feature never holds a type of Material 3, Haze, or Compose Unstyled. The wrappers keep that state.
- Icons are vector drawables named `ic_<name>.xml`. `material-icons-extended` is not used.
- A component used by one feature only (the cash-entry keypad) lives in `ui/<feature>/component/` and still uses tokens. When a second feature needs it, move it to the matching group in `ui/common/component/`.
- Internal helpers (`FinoteSnackbar`, `finoteGlass`, `SheetSurface`, `DialogCard`, `DatePickerContent`, `SnackbarCard`) are not API. Do not call them from a feature.

## Usage rules

Konsist enforces these in `DesignSystemKonsistTest`, inside `testDebugUnitTest`:

| # | Rule | Scope |
|---|---|---|
| 1 | No import from `androidx.compose.material3`, Haze, or Compose Unstyled, except `Text` and `Icon` | `ui/`, not `ui/common/` |
| 2 | No `MaterialTheme`, `Color(`, `Color.<Name>` (except `Transparent`, `Unspecified`), `.dp`, `.sp`, `TextStyle(`, `RoundedCornerShape(`, `Brush.` | `ui/`, not `ui/common/theme/` |
| 3 | No `NumberFormat`, `DecimalFormat`, `String.format(`, `"...".format(` | all production code, not `ui/common/format/` |
| 4 | A file with a public `@Composable` has `@FinotePreviews` | `ui/common/component/` |

Rule 2 scans comments and strings too. `import` and `package` lines are skipped.

Only `reviewer` can check these. They are also in `docs/architecture.md`:
- The hero gradient (`brushes.hero`) is used only by `BalanceHeroCard`, at most once per screen.
- Glass is only for floating chrome: the nav bar, sheets, the snackbar. Never for content cards.
- Color is never the only signal. An amount has a sign, a transaction has a type icon, an error has text.

Every component must also:
- Show every relevant state: normal, pressed, disabled, error, loading.
- Keep a 48dp touch target, the right `Role`, and a `contentDescription` from `strings.xml`.
- Survive font scale 200%. No clipped amount, no hidden title.

## Add a token or a component

Token:
1. Put it in the matching class in `ui/common/theme/` (`FinoteSizes`, `FinoteSpacing`, and so on). Every token group is its own class in its own file.
2. Expose it through `FinoteTheme` if it is a new group.
3. A constant token gets a default and is not provided. Only tokens that follow the color scheme are provided in `FinoteTheme`.
4. A new `CompositionLocal` in `ui/common/` needs an allowlist line in `.editorconfig` for compose-rules. That edit goes in its own commit. Never suppress a compose-rules finding; `@Suppress` is allowed only for the detekt size rules in `docs/architecture.md`.

Component:
1. One file in the matching group of `ui/common/component/` (`control/`, `overlay/`, `layout/`, `display/`, `finance/`), named after the component. Tokens only, no raw numbers or colors.
2. Add a `@FinotePreviews` preview (light, dark, font scale 2) inside `FinotePreviewSurface`. Rule 4 fails without it.
3. Add a `@PreviewTest` function in `app/src/screenshotTest/kotlin/io/github/toanhp123/finote/ui/common/component/`. Group related components per file (`InputScreenshots.kt`, `DisplayScreenshots.kt`, `FrameScreenshots.kt`, `OverlayScreenshots.kt`). Use `@FinotePreviews` for the three variants, or a plain `@Preview` for one case such as a locale.
4. Generate the reference images (next section), look at them, then run the gate.
5. A text that a user sees comes from `strings.xml`. Preview-only strings may be literals.

## Screenshot workflow

Screenshot tests use the experimental AGP test suite `screenshotTest`. Sources are in `app/src/screenshotTest/kotlin/`. Reference PNGs are in `app/src/screenshotTestDefaultDebug/` and are committed.

| Task | Run |
|---|---|
| Update references | `updateDebugScreenshotTestDefaultTestSuite` |
| Check references | `testDebugScreenshotTestDefaultTestSuite` (part of `qualityGate`) |

Run both through the `gradle-run` skill.

- Always open the new or changed PNGs and look at them before you commit. A green run only means the image matches itself.
- Look for: clipped or hidden text at font scale 2, low contrast in dark, a wrong sign on an amount, a layout that differs from the other variants.
- Do not run `spotlessApply` and the update task in one Gradle invocation. Run them one after the other.
- The first run downloads the layoutlib jar (about 60 MB) and can look stuck on a slow link. Wait for it.
- A failed check writes a diff under `app/build/`. Read it. Update the references only when the change is intended.
- Commit the PNGs together with the code that changed them.

## Blur

`finoteGlass` is the only place that decides.

| Android | Behavior |
|---|---|
| 31+ | Real blur. `FinoteScaffold` makes a `HazeState` and marks the content as the source. Glass surfaces call Haze `hazeBlur` over it, under `glass.fill` |
| 26-30 | No blur. The surface draws `glass.solidFill`, fully opaque |
| Outside a `FinoteScaffold` | Same as 26-30 |
| Modal sheet, date picker | Always `glass.solidFill`. `ModalSheet` sets `LocalHazeState` to null |
| Dialog | Always opaque. It draws `colors.card`, no glass |

- The design must look right without blur. Check the no-blur look first.
- The nav bar blurs on API 31+ and draws the opaque `solidFill` on 26-30. `FinoteBottomSheet`, `FinoteDatePicker`, and `FinoteDialog` run in their own window and never blur: sheets and the date picker take the opaque `solidFill` path, the dialog is an opaque card. Nothing shows through them.
- layoutlib cannot open a second window. Screenshots of sheets, dialogs, and the date picker draw the internal surface over a scrim, not the real window.
- `LocalBlurEnabled` (internal) lets a preview switch blur off.

## Money display

Every amount on the UI goes through `MoneyText`, which calls `formatMoney(amount: Long, kind: MoneyKind, locale: Locale)` in `ui/common/format/`. No other code formats a number (rule 3).

| Case | Result |
|---|---|
| `Income`, amount > 0 | `+` then the value |
| `Expense`, amount > 0 | U+2212 then the value |
| Any kind, amount < 0 | U+2212 then the absolute value (a credit-card balance) |
| `Transfer`, `Neutral`, amount > 0 | no sign |
| Amount 0 | no sign |
| `Long.MIN_VALUE` | `IllegalArgumentException` (`abs` overflows) |

- The minus is U+2212, not a hyphen. The app writes the sign. `NumberFormat` only formats the absolute value.
- The value is VND currency, 0 decimals, in the locale of the app (`vi`, `en`, `zh-CN`, `ja`). The position of the dong sign and the non-breaking space (U+00A0) come from the locale.
- Color follows `kind`: income, expense, transfer colors; `Neutral` uses the text style color or `onSurface`. The sign and color together carry the meaning (color is never the only signal).
- In tests write `\u00A0` and `\u2212` as escapes. A formatter or a tool write can turn them into raw characters and break the string compare. After `spotlessApply` or any tool write, check that `MoneyFormatTest.kt` and `MoneyFormat.kt` still hold the escapes.
- Unit tests use JDK locale data. A device uses ICU. The space or the dong position can differ. Check the four languages by eye on a device once.
- Money is `Long`. Never `Double`.

## Known risks

- Reference PNGs were made on Windows. Layoutlib on Linux (GitHub Actions `quality-gate`) may render text a few pixels differently, and `testDebugScreenshotTestDefaultTestSuite` would fail on the first CI run. If it fails only for that reason, decide with the owner: allow a small difference threshold, or generate the references on CI. This is not yet checked on CI.
- The AGP test suite API (`testOptions.screenshotTests`, engine `0.0.1-alpha16`, flag `android.experimental.enableScreenshotTest`) is experimental. A new AGP version can change the DSL or the task names.
- Modal blur is unverified (see Blur).
