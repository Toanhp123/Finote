# UI Feature Template Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Every `ui/<feature>/` follows one template, Konsist fails the build when a file breaks it, and the existing screens are moved to fit.

**Architecture:** Rules first in `docs/architecture.md`, then the Konsist tests (red on today's code, which is their red proof), then file moves per feature until the tests are green. Moves change package lines, imports, one rename, and visibility. No body changes.

**Tech Stack:** Kotlin, Compose, Konsist (inside `testDebugUnitTest`), Compose screenshot tests.

**Spec:** `docs/superpowers/specs/2026-11-15-ui-feature-template-design.md`. Read it with this plan. Rule numbers below (rule 1–10) are the spec's.

## Global Constraints

- No change in behavior. Screenshot tests pass with **no regenerated image**: after the gate, `git status --short` lists no file under `app/src/screenshotTestDebug/` (or wherever the reference images live).
- Moves change `package` and `import` lines only, plus: the rename `CashEntrySheet` → `CashEntryScreen`, `internal` composables in a `*Screen.kt` or a feature `component/` file become public, merged helpers become `private`.
- The docs rule change is its own commit, before any move.
- Branch: `chore/ui-feature-template` from `dev`. The spec says `refactor/…`; `docs/git-workflow.md` has no such branch type, and a refactor is `chore/`.
- Commits follow `docs/git-workflow.md`: stage files by name, no AI attribution, scope `ui` where one fits. `coder` never commits; the main session does.
- Gradle only through the `gradle-run` skill with `.\gradlew.bat`.
- Skills for `coder`: `ponytail:ponytail`, `gradle-run`, `superpowers:verification-before-completion`.
- If a rule turns out wrong for a real case, stop and propose a rule change. Do not bend the code.

## Decisions the spec left open

| Open point | Decision |
|---|---|
| Konsist file | New file `FeatureTemplateKonsistTest.kt`. `LayoutKonsistTest` stays for `domain` and `data`. |
| `NotePage` / `FeePage` | Both are deleted. `TextPage` becomes the public composable of `component/TextPage.kt`; `PageContent` in `CashEntryScreen.kt` calls it twice. Fallback: if `PageContent` then passes 80 lines, keep `NotePage.kt` and `FeePage.kt` as thin files over a public `TextPage`. |
| Page file names | `component/KeypadPage.kt` (with private `AmountHeader`, `errorMessage`) and `component/PickerPage.kt`. |
| Commit split | One commit per task step marked "Commit" below: docs, tests, cashentry, transactions, settings. |
| Spec says "amend folder rules 3 and 4" | The three-file minimum is folder rule **2** in `docs/architecture.md`. Amend rules 2 and 4. |
| Rule 9 test | Switched on. The edit sheet is hosted in the main Activity (`cde0f8a`) and no `TransactionEditActivity` exists. |
| Screenshot test files | They do not move: a moved preview renames its reference image. Only their imports change. Unit tests move with their subject. |

## File Structure (end state)

```text
ui/cashentry/
  CashEntryContract.kt  CashEntryViewModel.kt  CashEntryScreen.kt  CashEntryActivity.kt  CashEntryTileService.kt
  component/  AccountList  CategoryList  PickerRow  Selectors  TextPage  KeypadPage  PickerPage
              CashKeypad  DeleteConfirmDialog  ReimbursementRow  TypeRow
  logic/      AmountExpression  CashEntryDefaults  CashEntryPrefill  ReimbursementChoice
ui/transactions/
  TransactionListContract.kt  TransactionListViewModel.kt  TransactionListScreen.kt
  component/  BalanceDriftBanner  DeleteAdjustmentDialog  ListenerOffBanner  TransactionFilterChips
  logic/      TransactionOpenRule  TransactionRowText
ui/settings/
  SettingsContract.kt  SettingsViewModel.kt  SettingsScreen.kt
  logic/      BankSummary
  accounts/   AccountsSettingsContract.kt  AccountsSettingsViewModel.kt  AccountsSettingsScreen.kt
              component/  AccountSheet  MergeDialog  IgnoreDialog
  capture/    (no move)
ui/setup/     (no move)
```

## Review Focus

1. **A rule that passes on an empty set.** A wrong package pattern makes every new Konsist rule green forever. Each rule gets a red proof in Task 2, recorded in the commit body.
2. **A `private` dialog or sheet in a screen file.** Rule 3 must fail it; visibility must not be part of that test. Red proof: today's private `AccountSheet` in `AccountsSettingsScreen.kt`.
3. **A sub-feature read as a stray folder.** `ui.settings.accounts.component` must count as a component folder and `ui.settings.accounts` as a feature. Covered when Task 4 turns the rules green without an exemption.
4. **A silently regenerated screenshot.** `TextPage` replaces `NotePage` and `FeePage`: the fee field must still be empty at fee 0 and the note field must still cap at `CashEntryViewModel.MAX_NOTE_LENGTH`. Task 5 checks that no reference image changed.
5. **A test left in its old package.** It still compiles through imports, and no machine checks rule 10. `reviewer` compares the test tree with the File Structure above.

---

### Task 1: Branch and rules in `docs/architecture.md`

**Files:**
- Modify: `docs/architecture.md` (Layers block line 27, line 80, Folder rules, Feature template, "Screen logic" row, "Rules only `reviewer` can check")

- [ ] **Step 1: Cut the branch.** `git switch dev`, then `git switch -c chore/ui-feature-template`. Commit the spec and this plan, both untracked: `docs: ui feature template spec and plan`.

- [ ] **Step 2: Replace the "Feature template" section** with the spec's template block (`## Design` → `### Template`, including the sub-feature sentence and the FSD map line) and the spec's rules 1–10, copied with their numbers. Keep the two closing lines of the old section (shared components, MVI flow). Add: "`FeatureTemplateKonsistTest` enforces rules 1, 2, 4, 5, 7, 8 and parts of 3 and 9."

- [ ] **Step 3: Amend the rest.**
  - Layers block: `ui/<feature>/        Contract, ViewModel, Screen; component/, logic/`.
  - Line 80: exempt path becomes `ui/cashentry/logic/AmountExpression.kt`.
  - Folder rule 2: the exceptions also name a feature's `logic/`.
  - Folder rule 4: "At most one level of subfolder under `component/` or `theme/`. A feature may hold sub-features; each sub-feature has its own `component/` and `logic/`."
  - "Screen logic" row: Where = `ui/<f>/logic`.
  - "Rules only `reviewer` can check": add "A page of a multi-page screen lives in `component/`", "A feature `*Activity` exists only for entry from outside the app", "A test sits in the package of its subject".

- [ ] **Step 4: Commit.** `docs: ui feature template rules`

---

### Task 2: Konsist rules, red first

**Files:**
- Create: `app/src/test/java/io/github/toanhp123/finote/architecture/FeatureTemplateKonsistTest.kt`
- Modify: `app/src/test/java/io/github/toanhp123/finote/architecture/KonsistScopes.kt` (add `UI_NAVIGATION = "$ROOT.ui.navigation"`)

**Interfaces:**
- Consumes: `productionFiles`, `filesIn`, `packageName()`, `inPackage`, `topLevelDeclarations()`, `UI`, `UI_COMMON` from `KonsistScopes.kt`.
- Produces: the eight test names below. Later tasks run them by name.

Scopes, private to the test class:

- **feature package:** in `UI`, not `UI` itself, not in `UI_COMMON`, not in `UI_NAVIGATION`, last segment not `component` or `logic`.
- **feature component file:** in `UI`, not in `UI_COMMON`, package ends with `.component`.
- **feature logic file:** in `UI`, not in `UI_COMMON`, package ends with `.logic`.
- **screen file:** a file in a feature package whose name ends with `Screen`.

- [ ] **Step 1: Write the tests.** All use `assertTrue(strict = false, testName = …)` like `LayoutKonsistTest`.

| Test name | Scope | Passes when |
|---|---|---|
| `feature roots hold only fixed roles` | files in a feature package | file name ends with `Contract`, `ViewModel`, `Screen`, `Activity`, or `TileService` |
| `screen files expose only the screen and its route` | screen files | every top-level declaration is `private`, or is named `<X>Screen` or `<X>Route` where the file is `<X>Screen.kt` |
| `dialogs and sheets live in component` | files in a feature package | no top-level `@Composable` function, private or not, has a name ending `Dialog` or `Sheet` |
| `component files declare one composable named like the file` | feature component files | exactly one top-level `@Composable` function is not `private`, and its name is the file name |
| `logic files hold no compose` | feature logic files | no import starts with `androidx.compose` and no function has `@Composable` |
| `feature composables are not internal` | screen files and feature component files | no top-level `@Composable` function is `internal` |
| `view models extend BaseViewModel` | classes in `UI` named `*ViewModel`, except `BaseViewModel` | a parent is named `BaseViewModel` |
| `feature ui starts no activity` | screen files and feature component files | the text holds neither `startActivity` nor `Activity::class` |

- [ ] **Step 2: Run them.** `testDebugUnitTest --tests "*.architecture.FeatureTemplateKonsistTest"`

Expected: five FAIL, three PASS. The failures must name exactly these files:

| Test | Files |
|---|---|
| feature roots | `AmountExpression`, `CashEntryDefaults`, `CashEntryPrefill`, `TransactionOpenRule`, `BankSummary`, `AccountsSettingsDialogs` |
| screen files | `CashEntryScreen` (`CashEntrySheet`), `AccountsSettingsScreen` (`AccountSheetContent`) |
| dialogs and sheets | `CashEntryScreen` (`CashEntrySheet`), `AccountsSettingsScreen` (`AccountSheet`), `AccountsSettingsDialogs` |
| component files | `CashEntryPickers`, `ReimbursementChoice`, `TransactionRowText` |
| not internal | `CashEntryPickers`, `TypeRow`, `ReimbursementRow`, `TransactionFilterChips`, `AccountsSettingsScreen` |

A missing or extra file means the scope is wrong: fix the test, not the list.

- [ ] **Step 3: Red proof for the three green rules.** One at a time: add the violation, run the one test, see it fail, remove the violation.
  - logic: a file `ui/setup/logic/Probe.kt` with `import androidx.compose.runtime.Composable`.
  - view models: a class `ProbeViewModel : ViewModel()` in `ui/setup/SetupViewModel.kt`.
  - starts no activity: a line `context.startActivity(intent)` inside a function in `ui/setup/SetupScreen.kt`.

  Then `git status --short` shows only the two test files.

- [ ] **Step 4: Commit.** `test: konsist rules for the ui feature template`. Body: one line per rule with the file that made it red. The branch hook runs `spotlessCheck` and `detekt` only, so the red tests do not block it.

---

### Task 3: `ui/cashentry/`

**Files:**
- Move to `ui/cashentry/logic/`: `AmountExpression.kt`, `CashEntryDefaults.kt`, `CashEntryPrefill.kt`, `component/ReimbursementChoice.kt`
- Delete: `ui/cashentry/component/CashEntryPickers.kt`
- Create in `ui/cashentry/component/`: `AccountList.kt`, `CategoryList.kt`, `PickerRow.kt`, `Selectors.kt`, `TextPage.kt`, `KeypadPage.kt`, `PickerPage.kt`
- Modify: `CashEntryScreen.kt`, `CashEntryContract.kt`, `CashEntryViewModel.kt`, `component/TypeRow.kt`, `component/ReimbursementRow.kt`, `component/CashKeypad.kt`
- Modify: `app/src/test/java/io/github/toanhp123/finote/architecture/ProjectRulesKonsistTest.kt:150` (path → `ui/cashentry/logic/AmountExpression.kt`)
- Move tests to `test/…/ui/cashentry/logic/`: `AmountExpressionTest.kt`, `CashEntryDefaultsTest.kt`, `component/ReimbursementChoiceTest.kt`
- Move test to `test/…/ui/cashentry/component/`: `CashEntrySelectorsTest.kt` (name kept)
- Modify imports: `CashEntryEditViewModelTest.kt`, `CashEntryViewModelTest.kt`, `CashEntryTestSupport.kt`, `screenshotTest/…/ui/cashentry/CashEntryScreenshots.kt`

**Interfaces (produces):** every signature is today's, only the visibility and package change.

| File | Public composable | Private in the same file |
|---|---|---|
| `AccountList.kt` | `AccountList` | none |
| `CategoryList.kt` | `CategoryList` | `asTree`, `CHILD_DEPTH`, `NO_CATEGORY_KEY` |
| `PickerRow.kt` | `PickerRow` | none |
| `Selectors.kt` | `Selectors` | `AccountSelector`, `CategorySelector`, `DestinationSelector`, `FeeSelector`, `dateLabel`, `SelectorRow`, `NOTE_MAX_LINES` |
| `TextPage.kt` | `TextPage` | none |
| `KeypadPage.kt` | `KeypadPage` | `AmountHeader`, `errorMessage` |
| `PickerPage.kt` | `PickerPage` | none |

- [ ] **Step 1: Move the four logic files** (`git mv`), set `package io.github.toanhp123.finote.ui.cashentry.logic`, fix every import in `main`, `test`, `screenshotTest`. Update the exempt path in `ProjectRulesKonsistTest.kt`. Move their three tests the same way.

- [ ] **Step 2: Split `CashEntryPickers.kt`** into the five files of the table. `internal` becomes public; `PickerRow` and `TextPage` lose `private`. Delete `NotePage` and `FeePage`; their two doc comments become comments at the two `TextPage` calls.

- [ ] **Step 3: Edit `CashEntryScreen.kt`.**
  - Rename `CashEntrySheet` → `CashEntryScreen` (also `CashEntryScreenshots.kt:113`).
  - Move `PickerPage` to `component/PickerPage.kt`; move `KeypadPage`, `AmountHeader`, `errorMessage` to `component/KeypadPage.kt`.
  - In `PageContent`, `FEE` calls `TextPage(value = if (state.fee > 0) state.fee.toString() else "", onValueChange = { onIntent(CashEntryIntent.FeeChanged(it)) }, label = stringResource(R.string.cash_entry_fee), onDone = onPageClose, keyboardType = KeyboardType.Number)`; `NOTE` calls `TextPage(value = state.note, onValueChange = { onIntent(CashEntryIntent.NoteChanged(it)) }, label = stringResource(R.string.cash_entry_note), onDone = onPageClose, maxLength = CashEntryViewModel.MAX_NOTE_LENGTH)`.
  - Order left in the file: `CashEntryRoute`, `CashEntryScreen`, `PageContent`, `escapeSheetPadding`, `pageTitle`.

- [ ] **Step 4: Make `TypeRow` and `ReimbursementRow` public.** Move `CashEntrySelectorsTest.kt` to the `component` test package.

- [ ] **Step 5: Verify.** `testDebugUnitTest`. Expected: all pass except `FeatureTemplateKonsistTest`, whose failures no longer name any `cashentry` file.

- [ ] **Step 6: Commit.** `refactor(ui): cash entry follows the feature template`

---

### Task 4: `ui/transactions/` and `ui/settings/`

**Files:**
- Move to `ui/transactions/logic/`: `TransactionOpenRule.kt`, `component/TransactionRowText.kt`; their tests `TransactionOpenRuleTest.kt`, `component/TransactionRowTextTest.kt` to `test/…/ui/transactions/logic/`
- Modify: `ui/transactions/TransactionListScreen.kt` (imports), `TransactionListViewModel.kt` (import, if it uses `isOpenable`), `component/TransactionFilterChips.kt` (public)
- Move to `ui/settings/logic/`: `BankSummary.kt`; test `BankSummaryTest.kt` to `test/…/ui/settings/logic/`
- Modify imports: `SettingsViewModel.kt`, `settings/capture/NotificationCaptureSettings{Contract,ViewModel,Screen}.kt`, `screenshotTest/…/settings/capture/NotificationCaptureSettingsScreenshots.kt`
- Delete: `ui/settings/accounts/AccountsSettingsDialogs.kt`
- Create in `ui/settings/accounts/component/`: `MergeDialog.kt`, `IgnoreDialog.kt`, `AccountSheet.kt`
- Modify: `ui/settings/accounts/AccountsSettingsScreen.kt`

**Interfaces (produces):** signatures are today's.

| File | Public composable | Private in the same file |
|---|---|---|
| `MergeDialog.kt` | `MergeDialog` | `MergeBalances` |
| `IgnoreDialog.kt` | `IgnoreDialog` | `TrashOption` |
| `AccountSheet.kt` | `AccountSheet(state, onIntent)` | `ColumnScope.AccountSheetContent`, `MergeTargets` |

- [ ] **Step 1: Transactions.** Move the two logic files and their tests, fix imports, make `TransactionFilterChips` public. `TransactionListScreen.kt` keeps its private sections.

- [ ] **Step 2: Verify and commit.** `testDebugUnitTest`; no `transactions` file in the Konsist failures. `refactor(ui): transaction list follows the feature template`

- [ ] **Step 3: Settings.** Move `BankSummary.kt` and its test, fix imports. Split `AccountsSettingsDialogs.kt` and move the sheet per the table. `AccountsSettingsScreen.kt` keeps `AccountsSettingsRoute`, `AccountsSettingsScreen`, `AccountRow`, `AccountsDialogs`.

- [ ] **Step 4: Verify and commit.** `testDebugUnitTest`. Expected: every test passes, `FeatureTemplateKonsistTest` included, with no exemption added. `refactor(ui): settings follows the feature template`

---

### Task 5: Gate, review, squash

- [ ] **Step 1: Paths in docs.** Search `docs/design-system.md`, `docs/roadmap.md`, `CLAUDE.md`, `.claude/agents/` for `ui/cashentry`, `ui/transactions`, `ui/settings`, `CashEntrySheet`, `CashEntryPickers`, `AccountsSettingsDialogs`. Fix any hit (a search on 2026-11-16 found none outside `docs/architecture.md` and `docs/roadmap-history.md`; history is not edited). Commit only if something changed: `docs: paths after the ui feature template`.

- [ ] **Step 2: Full gate.** `qualityGate`. Expected: `exit_status` 0.

- [ ] **Step 3: No regenerated image.** `git status --short` lists no `.png`, and `git diff --stat dev -- "*.png"` lists nothing.

- [ ] **Step 4: Review.** Run `reviewer` once on the branch diff against the spec. It also checks Review Focus 5 and the two judgment rules (page trigger, outside-entry Activity). Send findings to `coder`, then review again.

- [ ] **Step 5: Squash.** Ask the user first. `git switch dev`, `git merge --squash chore/ui-feature-template`, commit `refactor(ui): one template for every ui feature`. Set the spec's status line to "implemented".
