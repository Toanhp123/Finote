# UI Packaging Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Group `ui/common/component`, `ui/common/theme` and feature folders by responsibility, with no change in behavior.

**Architecture:** Pure package moves. `git mv` each file, rewrite its `package` line, fix imports everywhere. Kotlin `internal` is module-wide, so visibility does not change. One Konsist test pins the new component layout.

**Tech Stack:** Kotlin, Jetpack Compose, Konsist, detekt, Spotless, Gradle (run through the `gradle-run` skill).

**Spec:** `docs/superpowers/specs/2026-11-07-ui-packaging-review.md` (section 3 is the decision).

## Global Constraints

- Root package: `io.github.toanhp123.finote`. Paths below are under `app/src/main/java/io/github/toanhp123/finote/`.
- Move files only. Do not change any declaration, signature, visibility or logic.
- Use `git mv` so history follows the file.
- Explicit imports only, no wildcard imports.
- Do not move files under `app/src/screenshotTest/`. Their reference images are keyed by their package. Only fix their imports.
- Unit tests under `app/src/test/` move to mirror the new package of the class they test.
- Branch: `chore/ui-packaging` from `dev`. Commit type `refactor(ui)` (or `docs` for doc-only commits). No AI attribution in commits. Stage files by name.
- Run Gradle only through the `gradle-run` skill: `assembleDebug`, `testDebugUnitTest`, `qualityGate`.

## Review Focus

1. Files that used a same-package symbol without import (e.g. `LocalHazeState`, `finoteGlass`, `FinotePreviewSurface`, `LightIncome`) now need an import. Compile catches it; `assembleDebug` and the screenshot source set must both compile.
2. `screenshotTest` sources import moved classes. `qualityGate` must compile them; check it does.
3. `AndroidManifest.xml` names `.ui.cashentry.CashEntryActivity` and `.ui.cashentry.CashEntryTileService`. Both stay in the feature root; do not move them.
4. detekt exclude `**/ui/common/theme/**` must still cover `theme/color/`. The glob already matches subfolders; confirm `qualityGate` reports no new raw-number findings in `theme/color/`.
5. Konsist rules that use `inPackage(UI_THEME)` / `inPackage(UI_COMPONENT)` must still match subpackages. `inPackage` is prefix-based; confirm the architecture tests stay green.

---

### Task 1: Theme colors into `theme/color/`

**Files:**
- Move to `ui/common/theme/color/` (package `io.github.toanhp123.finote.ui.common.theme.color`): `BrandColors.kt`, `CategoryIconColors.kt`, `CategoryPalette.kt`, `FinoteColors.kt`, `FixedColors.kt`, `GlassColors.kt`, `HeroColors.kt`, `MoneyColorRole.kt`
- Stay in `ui/common/theme/`: `FinoteTheme.kt`, `FinoteTypography.kt`, `FinoteShapes.kt`, `FinoteSizes.kt`, `FinoteSpacing.kt`, `FinoteRadii.kt`, `FinoteMotion.kt`, `FinoteBrushes.kt`, `ShimmerBrush.kt`
- Modify: every file importing a moved symbol (main, test, screenshotTest)
- Modify: `docs/design-system.md` token table (line ~11) so color tokens point to `ui/common/theme/color/`

**Interfaces:**
- Produces: color types and values under `io.github.toanhp123.finote.ui.common.theme.color`. `FinoteTheme` stays at `io.github.toanhp123.finote.ui.common.theme.FinoteTheme`.

- [ ] **Step 1:** Create branch `chore/ui-packaging` from `dev`.
- [ ] **Step 2:** `git mv` the 8 color files; set their `package` line.
- [ ] **Step 3:** Add imports where the theme root and other files use moved symbols; update imports in `app/src/test` and `app/src/screenshotTest`.
- [ ] **Step 4:** Update the token table in `docs/design-system.md`.
- [ ] **Step 5:** Run `assembleDebug` then `testDebugUnitTest` via `gradle-run`. Expected: `exit_status` 0.
- [ ] **Step 6:** Commit `refactor(ui): move color tokens into theme.color`.

### Task 2: Component role subpackages

**Files:**
- Move under `ui/common/component/` (package `io.github.toanhp123.finote.ui.common.component.<group>`):
  - `control/`: `FinoteButton`, `FinoteButtonStyle`, `FinoteIconButton`, `FinoteTextField`, `FinoteSwitch`, `FinoteChip`, `FinoteSegmented`
  - `overlay/`: `FinoteBottomSheet`, `FinoteDialog`, `FinoteDatePicker`, `DatePickerDates`, `FinoteSnackbar`, `FinoteSnackbarState`
  - `layout/`: `FinoteScaffold`, `FinoteTopBar`, `FinoteNavBar`, `FinoteNavItem`
  - `display/`: `FinoteCard`, `FinoteListRow`, `ListRowLayout`, `FinoteEmptyState`, `FinoteSkeleton`
  - `finance/`: `BalanceHeroCard`, `TransactionRow`, `MoneyText`, `CategoryIcon`
  - `glass/`: `BlurSupport`, `HazeLocals`
  - `preview/`: `FinotePreviews`, `FinotePreviewSurface`
- Move tests: `app/src/test/.../ui/common/component/DatePickerDatesTest.kt` and `FinoteBottomSheetTest.kt` → `.../ui/common/component/overlay/`
- Create test: `app/src/test/java/io/github/toanhp123/finote/architecture/DesignSystemKonsistTest.kt` (add one test)
- Modify: all importers (main, test, screenshotTest); `docs/design-system.md` lines ~38 and ~102 (component location)

**Interfaces:**
- Consumes: Task 1 color package.
- Produces: component packages listed above; later tasks import from them.

- [ ] **Step 1: Write the failing test** in `DesignSystemKonsistTest`:

```kotlin
@Test
fun `shared components live in a role subpackage`() {
    val groups = listOf("control", "overlay", "layout", "display", "finance", "glass", "preview")
        .map { "$UI_COMPONENT.$it" }
    filesIn(UI_COMPONENT).assertTrue(testName = "shared components live in a role subpackage") { file ->
        groups.any { file.packageName() == it }
    }
}
```

Use the same `packageName()` accessor the file already uses; equality (not `inPackage`) enforces exactly one level.

- [ ] **Step 2:** Run `testDebugUnitTest` via `gradle-run`. Expected: FAIL on this test only (30 files in the root package).
- [ ] **Step 3:** `git mv` the 30 files into their groups; set `package` lines; fix imports across main, test, screenshotTest; move the two unit tests.
- [ ] **Step 4:** Update `docs/design-system.md`: components live in `ui/common/component/<group>/`, with the seven groups and one line each on what goes there (copy from spec 3.2).
- [ ] **Step 5:** Run `assembleDebug` then `testDebugUnitTest` via `gradle-run`. Expected: `exit_status` 0, new test passes.
- [ ] **Step 6:** Commit `refactor(ui): group shared components by role`.

### Task 3: Feature `component/` folders and docs rule

**Files:**
- Move to `ui/cashentry/component/`: `CashKeypad.kt`, `CashEntryPickers.kt`
- Move to `ui/transactions/component/`: `ReimbursementChoice.kt`, `ReimbursementSheet.kt`, `TransactionFilterChips.kt`, `TransactionRowText.kt`
- Move tests: `app/src/test/.../ui/transactions/ReimbursementChoiceTest.kt`, `TransactionRowTextTest.kt` → `.../ui/transactions/component/`
- Stay in feature root: `*Contract`, `*ViewModel`, `*Screen`, `CashEntryActivity`, `CashEntryTileService`, `AmountExpression`, `CashEntryDefaults`
- Modify: importers; `docs/design-system.md` line ~67 (feature-only component lives in `ui/<feature>/component/`); `docs/architecture.md` (add the four folder rules from spec 3.1 and the feature template from spec 3.4, short)

**Interfaces:**
- Consumes: Task 2 component packages.

- [ ] **Step 1:** `git mv` the 6 files and 2 tests; set `package` lines; fix imports.
- [ ] **Step 2:** Update `docs/design-system.md` and `docs/architecture.md`.
- [ ] **Step 3:** Run `assembleDebug` then `testDebugUnitTest` via `gradle-run`. Expected: `exit_status` 0.
- [ ] **Step 4:** Commit `refactor(ui): move feature sub-composables into component folders`.

### Task 4: Full gate

- [ ] **Step 1:** Run `qualityGate` via `gradle-run`. Expected: `exit_status` 0, no Spotless, detekt or Konsist failure. If Spotless fails on import order only, run `spotlessApply`, re-run, and commit `refactor(ui): reorder imports`.
- [ ] **Step 2:** Confirm Review Focus items 2, 4 and 5 from the gate summary.
