# UI Overlay API Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix the overlay API once (dialog buttons, content while closing, sheet close callback, preview rendering) and move every caller and screenshot onto it.

**Architecture:** `FinoteDialog` owns its buttons; `ModalSheet` reports when it settles hidden; a `rememberLastNonNull` helper keeps content while an overlay leaves; in `LocalInspectionMode` overlays draw inline so screenshots render real feature UI. Dialog and sheet visibility live in ViewModel state.

**Tech Stack:** Kotlin, Jetpack Compose, composeunstyled (`UnstyledDialog`, `UnstyledModalBottomSheet`), Robolectric + `createComposeRule()`, Turbine, AGP `screenshotTest` suite.

**Spec:** `docs/superpowers/specs/2026-11-14-ui-overlay-api-design.md`

## Global Constraints

- Branch `chore/ui-overlay-api`. Commit scope `ui` (docs-only: no scope). No AI attribution in commits.
- Gradle only through the `gradle-run` skill, wrapper `'.\gradlew.bat'` single-quoted from Bash.
- Strings from `strings.xml`; tokens only, no new raw numbers or colors.
- Konsist and detekt stay green. Run `spotlessKotlinCheck detekt` before each commit.
- `coder` subagents never commit; the main session commits after each task's review.
- Internal helpers (`DialogCard`, `SheetSurface`) are not API: no feature and, after Task 7, no screenshot calls them.

## Review Focus

1. Rotation while the cash entry sheet is leaving: the activity must still finish → Task 5 test `first composition hidden calls onDismissed`.
2. A different item opens right after one closes (merge dialog, then ignore dialog; sheet for account A, then B): the new content shows, not the old → Task 3 test `switches to the new value`.
3. Reopening the ignore dialog after a tick and a cancel starts unticked → Task 4 test `ignore dialog reopens unticked`.
4. `shouldTrash` ticked while `trashableCount == 0` must never trash → Task 4 test `no trashable rows never trashes`.
5. A user dismiss of the sheet calls `onDismiss` exactly once, then `onDismissed` once → Task 5 test `user dismiss calls onDismiss then onDismissed`.

---

### Task 1: Spike — inline overlays in preview mode

Decides approach (a) vs (b) for screenshots. Keep the code if it works.

**Files:**
- Modify: `app/src/main/java/io/github/toanhp123/finote/ui/common/component/overlay/FinoteDialog.kt`
- Modify: `app/src/main/java/io/github/toanhp123/finote/ui/common/component/overlay/FinoteBottomSheet.kt` (`ModalSheet`)
- Create: `app/src/main/java/io/github/toanhp123/finote/ui/common/component/overlay/InlineOverlay.kt`
- Modify: `app/src/main/java/io/github/toanhp123/finote/ui/settings/accounts/AccountsSettingsScreen.kt:65-89`
- Modify: `app/src/screenshotTest/kotlin/io/github/toanhp123/finote/ui/settings/accounts/AccountsSettingsScreenshots.kt`

**Interfaces:**
- Produces: `@Composable internal fun InlineOverlay(alignment: Alignment, content: @Composable () -> Unit)` — full-size `Box` with `scheme.scrim` at the existing screenshot alpha (move `0.6f` into a token or a named constant in this file), content aligned.
- Produces: placement rule — `AccountsSettingsScreen` wraps its `Column` and overlays in one `Box(modifier.fillMaxSize())`.

- [ ] **Step 1:** In `FinoteDialog`, when `LocalInspectionMode.current`: return if `!isVisible`, else `InlineOverlay(Alignment.Center) { DialogCard(...) }` with the `safeDrawingPadding().padding(spacing.xl)` the window uses. No `UnstyledDialog`, no animation.
- [ ] **Step 2:** In `ModalSheet`, same: return if `!isVisible`, else `InlineOverlay(Alignment.BottomCenter) { SheetSurface(contentPadding, content) }` inside the same `LocalHazeState provides null`.
- [ ] **Step 3:** Wrap `AccountsSettingsScreen` body + overlays in a `Box`.
- [ ] **Step 4:** Add `AccountsMergeDialogRealPreview` to `AccountsSettingsScreenshots.kt`: `AccountsSettingsScreen(state = AccountsSettingsState(accounts = listed, pendingMerge = vcb1 to vcb2, mergeCount = 12), onIntent = {}, onBack = {})` inside `FinoteTheme(dynamicColor = false)` with the background `Box`.
- [ ] **Step 5:** Run `updateDebugScreenshotTestDefaultTestSuite`, open the new PNG. Pass: scrim covers the whole screen including the top bar, the card is centered, buttons as in `AccountsMergeDialogPreview`.
- [ ] **Step 6: Gate.** If it fails, stop and report to the user (fallback (b) changes Tasks 2 and 7). If it passes, delete `AccountsMergeDialogRealPreview` and its PNG (Task 7 redoes it), keep the rest, run `testDebugUnitTest`.
- [ ] **Step 7: Commit** — `refactor(ui): draw overlays inline in preview mode`

### Task 2: `FinoteDialog` owns its buttons

**Files:**
- Modify: `.../ui/common/component/overlay/FinoteDialog.kt` (both functions and previews)
- Modify: `.../ui/settings/accounts/AccountsSettingsDialogs.kt` (`MergeDialog`, `IgnoreDialog`; delete `CancelButton`)
- Modify: `app/src/screenshotTest/.../ui/common/component/OverlayScreenshots.kt`, `.../settings/accounts/AccountsSettingsScreenshots.kt` (`DialogFrame` only, until Task 7)
- Modify: `docs/design-system.md:58` (`FinoteDialog` row)

**Interfaces:**
- Produces:
  ```kotlin
  fun FinoteDialog(isVisible: Boolean, title: String, confirmText: String, onConfirm: () -> Unit,
      onDismiss: () -> Unit, modifier: Modifier = Modifier, text: String? = null,
      isDestructive: Boolean = false, dismissText: String = stringResource(R.string.action_cancel),
      content: (@Composable ColumnScope.() -> Unit)? = null)
  internal fun DialogCard(title: String, confirmText: String, onConfirm: () -> Unit, onDismiss: () -> Unit,
      modifier: Modifier = Modifier, text: String? = null, isDestructive: Boolean = false,
      dismissText: String = stringResource(R.string.action_cancel), content: (@Composable ColumnScope.() -> Unit)? = null)
  ```
  Buttons: `FinoteButton(dismissText, onDismiss, style = Ghost)`, then `FinoteButton(confirmText, onConfirm, style = if (isDestructive) Danger else Primary)`.

- [ ] **Step 1:** Change both signatures and the button row. Update both `FinoteDialog` previews.
- [ ] **Step 2:** `MergeDialog` and `IgnoreDialog` pass `confirmText`, `onConfirm`, `isDestructive = true`; delete `CancelButton`. Keep `content = null` when `trashableCount == 0`.
- [ ] **Step 3:** Fix screenshot callers of `DialogCard` to the new parameters.
- [ ] **Step 4: Enter animation check.** Read `UnstyledDialog` in the composeunstyled sources (Gradle cache, `~/.gradle/caches/modules-2/files-2.1/com.composables*`). If first composition with `visible = true` skips the enter animation, feed it from `remember { MutableTransitionState(false) }.apply { targetState = isVisible }` (or the library's equivalent). Write the finding as a one-line comment above the call.
- [ ] **Step 5:** Update the design-system row to the new signature.
- [ ] **Step 6:** Run `testDebugUnitTest` and `testDebugScreenshotTestDefaultTestSuite`. Expected: PASS, no image diff (same buttons, same styles).
- [ ] **Step 7: Commit** — `refactor(ui): dialog buttons come from FinoteDialog`

### Task 3: `rememberLastNonNull`

**Files:**
- Create: `app/src/main/java/io/github/toanhp123/finote/ui/common/RememberLastNonNull.kt`
- Test: `app/src/test/java/io/github/toanhp123/finote/ui/common/RememberLastNonNullTest.kt`

**Interfaces:**
- Produces: `@Composable fun <T : Any> rememberLastNonNull(value: T?): T?` — body as in the spec (`Ref` + `SideEffect`, `return value ?: last.value`).

- [ ] **Step 1: Write the failing tests** (Robolectric, `@Config(sdk = [37])`, `createComposeRule()` from `androidx.compose.ui.test.junit4.v2`, like `FinoteBottomSheetTest`). Hold `var value by mutableStateOf<String?>(null)`, render `Text(rememberLastNonNull(value) ?: "none")`.
  - `starts null` → `onNodeWithText("none").assertExists()`.
  - `keeps the last value after null`: `value = "A"`, idle, `value = null`, idle → `"A"` exists.
  - `switches to the new value`: `"A"` → `null` → `"B"` → `"B"` exists, `"A"` does not.
- [ ] **Step 2:** Run `testDebugUnitTest --tests '*RememberLastNonNullTest'`. Expected: FAIL (unresolved reference).
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Run again. Expected: PASS.
- [ ] **Step 5: Commit** — `feat(ui): keep overlay content while it closes`

### Task 4: Accounts dialog state

**Files:**
- Modify: `.../ui/settings/accounts/AccountsSettingsContract.kt`, `AccountsSettingsViewModel.kt`, `AccountsSettingsScreen.kt`, `AccountsSettingsDialogs.kt`
- Modify: `app/src/test/java/io/github/toanhp123/finote/ui/settings/accounts/AccountsSettingsViewModelTest.kt`
- Modify: `app/src/screenshotTest/.../settings/accounts/AccountsSettingsScreenshots.kt` (state constructors only)

**Interfaces:**
- Consumes: `rememberLastNonNull` (Task 3), new `FinoteDialog` (Task 2).
- Produces:
  ```kotlin
  sealed interface AccountsDialog {
      data class Merge(val source: Account, val target: Account, val count: Int) : AccountsDialog
      data class Ignore(val trashableCount: Int, val shouldTrash: Boolean = false) : AccountsDialog
  }
  // AccountsSettingsState: val dialog: AccountsDialog? = null  (replaces pendingMerge, mergeCount, isIgnoreDialogOpen, trashableCount)
  // AccountsSettingsIntent: data class TrashToggled(val isChecked: Boolean); data object IgnoreConfirmed (no parameter)
  ```
  `MergeDialog(dialog: AccountsDialog.Merge, onIntent)`, `IgnoreDialog(dialog: AccountsDialog.Ignore, onIntent)`. `AccountSheetContent(account: Account, mergeTargets: List<Account>, onIntent)`.

- [ ] **Step 1: Update tests to the new state** — every `pendingMerge`/`mergeCount`/`isIgnoreDialogOpen`/`trashableCount` read becomes a read of `vm.state.value.dialog` (e.g. `assertEquals(AccountsDialog.Merge(src, dst, 7), ...)` by ids); `IgnoreConfirmed(shouldTrash = x)` becomes `TrashToggled(x)` then `IgnoreConfirmed`. Add:
  - `ignore dialog reopens unticked`: open ignore (liveCounts gives trashable 3), `TrashToggled(true)`, `DialogDismissed`, `IgnoreClicked`, runCurrent → `(dialog as AccountsDialog.Ignore).shouldTrash == false`.
  - `no trashable rows never trashes`: trashable 0, `TrashToggled(true)`, `IgnoreConfirmed` → `repo.ignores.single().trash == false`.
  - `ticked trash with rows trashes`: trashable 3, `TrashToggled(true)`, `IgnoreConfirmed` → `repo.ignores.single().trash == true`.
- [ ] **Step 2:** Run `testDebugUnitTest --tests '*AccountsSettingsViewModelTest'`. Expected: compile FAIL.
- [ ] **Step 3:** Contract + VM: `confirmIgnore` reads `shouldTrash && trashableCount > 0` from `state.dialog as? Ignore` (the only guard); `TrashToggled` updates the `Ignore` dialog; `withoutDialogs()` sets `dialog = null`.
- [ ] **Step 4:** Screen: `val shownAccount = rememberLastNonNull(state.selected)`; sheet shows `shownAccount?.let { AccountSheetContent(it, state.mergeTargets, onIntent) }`. Dialogs: `rememberLastNonNull(state.dialog)?.let { d -> when (d) { is Merge -> MergeDialog(...); is Ignore -> IgnoreDialog(...) } }` with `isVisible = state.dialog != null` passed in. Remove the `remember` in `IgnoreDialog`; `TrashOption` toggle sends `TrashToggled`.
- [ ] **Step 5:** Fix screenshot state constructors. Run `testDebugUnitTest` and `testDebugScreenshotTestDefaultTestSuite`. Expected: PASS, no diff.
- [ ] **Step 6: Commit** — `refactor(ui): accounts dialogs as one sealed state`

### Task 5: Sheet `onDismissed` and the reimbursement sheet

**Files:**
- Modify: `.../ui/common/component/overlay/FinoteBottomSheet.kt`, `FinoteDatePicker.kt`
- Modify: `.../ui/transactions/component/ReimbursementSheet.kt`
- Test: `app/src/test/java/io/github/toanhp123/finote/ui/common/component/overlay/FinoteBottomSheetTest.kt`
- Modify: `docs/design-system.md:57,59` (sheet and date picker rows)

**Interfaces:**
- Consumes: `rememberLastNonNull` (Task 3).
- Produces: `onDismissed: () -> Unit = {}` as the parameter after `onDismiss` on `FinoteBottomSheet`, `ModalSheet`, `FinoteDatePicker`. Contract: runs each time the sheet settles at `Hidden` while `isVisible` is false, including the first composition with `isVisible = false`. In inspection mode it is never called.

- [ ] **Step 1: Write the failing tests.** Change `showSheet(initial: Boolean = true)` to pass `onDismiss = { dismissCount++; isVisible = false }` and `onDismissed = { dismissedCount++ }`. Add:
  - `caller hide calls onDismissed after the animation`: show, `isVisible = false`, idle → `dismissCount == 0`, `dismissedCount == 1`.
  - `user dismiss calls onDismiss then onDismissed`: show, `pressBack()`, idle → `dismissCount == 1`, `dismissedCount == 1`.
  - `first composition hidden calls onDismissed`: `showSheet(initial = false)` → `dismissedCount == 1`, `dismissCount == 0`.
  - Existing `caller hide does not call onDismiss` and `back press calls onDismiss while visible` still pass.
- [ ] **Step 2:** Run `--tests '*FinoteBottomSheetTest'`. Expected: FAIL.
- [ ] **Step 3:** In `ModalSheet`'s `LaunchedEffect(isVisible)`: after `state.animateTo(Hidden)` completes with `!isVisible`, call the latest `onDismissed` (`rememberUpdatedState`). A cancelled animation (visibility flipped back) must not call it.
- [ ] **Step 4:** Run again. Expected: PASS.
- [ ] **Step 5:** `ReimbursementSheet`: drop `lastShown` and its `LaunchedEffect`; content gets `rememberLastNonNull(selected) ?: ReimbursementChoice.NORMAL`. Pass `onDismissed` through `FinoteDatePicker`. Update the design-system rows.
- [ ] **Step 6:** Run `testDebugUnitTest`. Expected: PASS.
- [ ] **Step 7: Commit** — `feat(ui): sheets report when they finish closing`

### Task 6: Cash entry closes from state

**Files:**
- Modify: `.../ui/cashentry/CashEntryContract.kt`, `CashEntryViewModel.kt`, `CashEntryScreen.kt:65-110`
- Test: `app/src/test/java/io/github/toanhp123/finote/ui/cashentry/CashEntryViewModelTest.kt`

**Interfaces:**
- Consumes: `onDismissed` (Task 5).
- Produces: `CashEntryState.isVisible: Boolean = true`. `CashEntryEffect.Close` is deleted (and the effect type if it becomes empty; then remove the route's `ObserveEffects`).

- [ ] **Step 1: Update tests.** `save today uses clock millis and closes`: replace the effect assertion with `assertFalse(viewModel.state.value.isVisible)`. Add `dismiss hides the sheet` (`Dismissed` → `isVisible == false`) and `failed save keeps the sheet visible` (in `save failure keeps input`: `assertTrue(isVisible)`). Add `save after a successful save does nothing`: second `SaveClicked` → `transactions.stored.size == 1`.
- [ ] **Step 2:** Run `--tests '*CashEntryViewModelTest'`. Expected: FAIL.
- [ ] **Step 3:** VM: `Dismissed` and `Outcome.Ok` set `isVisible = false`; keep `isSaving = true` after success. Route: `FinoteBottomSheet(isVisible = state.isVisible, onDismiss = { onIntent(Dismissed) }, onDismissed = onClose)`; delete `SHEET_EXIT_MILLIS`, the `delay` import, both `remember` flags and the `LaunchedEffect`.
- [ ] **Step 4:** Run `testDebugUnitTest`. Expected: PASS.
- [ ] **Step 5: Commit** — `fix(ui): close cash entry when its sheet finishes leaving`

### Task 7: One screenshot support file, real overlays

**Files:**
- Create: `app/src/screenshotTest/kotlin/io/github/toanhp123/finote/ui/ScreenshotSupport.kt`
- Modify: every file under `app/src/screenshotTest/kotlin/io/github/toanhp123/finote/ui/` that defines `PHONE`, `SCRIM_ALPHA` or `ScreenPreviews`; `cashentry/CashEntryPreviews.kt`
- Modify: `docs/design-system.md:152` (Blur section)
- Update: reference PNGs under `app/src/screenshotTestDefaultDebug/`

**Interfaces:**
- Produces: `internal const val PHONE = "spec:width=360dp,height=640dp"`, `internal annotation class ScreenPreviews` (Light, Dark night mode, Large font 2f — the current trio). `CashEntryPreviews` keeps its own `700dp` device under a distinct name (e.g. `TALL_PHONE`) if its images need it.

- [ ] **Step 1:** Create the file; delete every local `PHONE`/`ScreenPreviews` copy.
- [ ] **Step 2:** Accounts: sheet and dialog previews render `AccountsSettingsScreen` with `selected`/`dialog` set; delete `SheetFrame`, `DialogFrame`, `Backdrop` overlay part, `SCRIM_ALPHA`.
- [ ] **Step 3:** Cash entry: render `CashEntryRoute`-equivalent real sheet — `FinoteBottomSheet(isVisible = true, onDismiss = {}) { CashEntrySheet(state, {}, {}) }` over the screen background; delete `SheetOverScrim`.
- [ ] **Step 4:** Transaction list: render `TransactionListScreen` with `editingId` set for the reimbursement sheet; delete its frame helper.
- [ ] **Step 5:** `OverlayScreenshots.kt`: call `FinoteDialog`/`FinoteBottomSheet`/`FinoteDatePicker` with `isVisible = true` over `ScreenBehind`'s body (keep the body, drop its scrim). Afterwards `grep -rn "DialogCard\|SheetSurface\|SCRIM_ALPHA" app/src/screenshotTest` returns nothing.
- [ ] **Step 6:** Design-system Blur section: "In preview mode (`LocalInspectionMode`) overlays draw inline over a scrim; screenshots render real screens. Call overlays at the screen root, after the body, in one `Box`."
- [ ] **Step 7:** Run `updateDebugScreenshotTestDefaultTestSuite`. List changed/added/removed PNGs. Show them to the user; wait for an OK on the images.
- [ ] **Step 8:** Run `testDebugScreenshotTestDefaultTestSuite`. Expected: PASS.
- [ ] **Step 9: Commit** — `test(ui): screenshots render real overlays`

### Task 8: Gate

- [ ] **Step 1:** Run `qualityGate`. Expected: `exit_status` 0.
- [ ] **Step 2:** Roadmap `docs/roadmap.md` cleanup item 4: mark done in the same style as items 1–3. Commit — `docs: mark cleanup branch 4 done`.
- [ ] **Step 3:** `reviewer` agent checks the branch against the spec. Fixes go back through `coder`. Ask the user before the squash into `dev`.
