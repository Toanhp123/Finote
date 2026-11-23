# UI overlay API (cleanup branch 4): design

Date: 2026-11-14. Status: approaches approved in chat, waiting for doc approval.
Branch: `chore/ui-overlay-api`. Source: findings A10, A6, A16 in `docs/superpowers/handoff/2026-11-10-cleanup-before-p2d.md`.

## Why

Features work around gaps in the overlay API with hard-coded UI: `isVisible = true` under an `if`, a `lastShown` patch, a guessed 250 ms delay, hand-built Cancel buttons, screenshot frames that copy the real dialog. The user's rule: no hard-coded UI that bypasses component APIs. Fix the overlay API once, then move every caller onto it.

## Goals

1. Dialogs and sheets animate in and out; their content stays on screen while they leave.
2. A dialog gets its buttons from `FinoteDialog`, not from each feature.
3. The cash entry sheet closes when its exit animation ends, not after a guessed delay. Rotation never reopens it.
4. Screenshots draw the real feature overlays, not hand copies. Screenshot boilerplate lives in one file.

## Non-goals

- Shared switch row, section, listener-access wiring, raw numbers, date format, accessibility (A12–A15): branch 5.
- `BalanceHeroCard` (unused): branch 5 decides.
- A date-range picker for `fts-search`: built with that feature.
- Changing when a dialog closes. `AccountsSettingsViewModel.finishAction` closes it first, then writes. Keep that.

## Anti-goals

- Two overlay APIs side by side (old slots and new parameters). Callers move in this branch.
- Production behavior that differs from what the screenshots show.

## Approaches (chosen in chat)

**Content while closing.**
(a) A helper `rememberLastNonNull(value: T?): T?` in `ui/common/`; overlays keep `isVisible`. Small diff, one way for sheets and dialogs; each caller must use it.
(b) Overlays take `item: T?` instead of `isVisible` and remember the last item. Callers cannot forget, but `FinoteDialog` takes `title`/`text` as parameters and the cash entry sheet has no item, so two API shapes.
**Chosen: (a).**

**Cash entry close.** One sensible way: `onDismissed` on the sheet, visibility in the ViewModel state (below).

**Screenshots.**
(a) In `LocalInspectionMode` the overlays draw inline (scrim + card or sheet surface) instead of opening a window. Screenshots call the real feature composables. Cost: one preview branch in production code; risk that an inline overlay does not cover the screen.
(b) Keep drawing internal surfaces; only move shared pieces into one file. `DialogFrame` still copies the dialog.
**Chosen: (a), with (b) as the fallback if the spike fails.**

## Design

### 1. `FinoteDialog` API

```kotlin
fun FinoteDialog(
    isVisible: Boolean,
    title: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    text: String? = null,
    isDestructive: Boolean = false,
    dismissText: String = stringResource(R.string.action_cancel),
    content: (@Composable ColumnScope.() -> Unit)? = null,
)
```

- The dialog draws a `Ghost` dismiss button (calls `onDismiss`) and a confirm button: `Danger` when `isDestructive`, else `Primary`.
- The `confirm` and `dismiss` slots go away. `DialogCard` takes the same parameters.
- A caller with dynamic data composes it as `rememberLastNonNull(state.dialog)?.let { d -> FinoteDialog(isVisible = state.dialog != null, title = ..., ...) }`: `title` and `confirmText` are non-null, so the dialog enters the tree on its first opening with `isVisible = true`.
- That first opening must still run the enter animation. Check what `UnstyledDialog` does when it is first composed with `visible = true`; if it skips the animation, drive it from a transition state that starts at `false`.
- Update the `FinoteDialog` row in `docs/design-system.md`.

### 2. Content while closing

- `rememberLastNonNull` lives in `ui/common/` (next to `ObserveEffects`). It returns the latest non-null value, and the last one after the value becomes null. No state write during composition and no one-frame lag:

  ```kotlin
  @Composable
  fun <T : Any> rememberLastNonNull(value: T?): T? {
      val last = remember { Ref<T>() }
      SideEffect { if (value != null) last.value = value }
      return value ?: last.value
  }
  ```
- `AccountsSettingsScreen`: the sheet shows `rememberLastNonNull(state.selected)`. `AccountSheetContent` takes the account as a parameter instead of reading `state.selected`.
- `ReimbursementSheet`: drop `lastShown` and its `LaunchedEffect`; use the helper.
- One Compose test for the helper (`createComposeRule()` on Robolectric, like `FinoteBottomSheetTest`): null → A → null shows A, then A → B shows B.

### 3. Accounts dialog state

```kotlin
sealed interface AccountsDialog {
    data class Merge(val source: Account, val target: Account, val count: Int) : AccountsDialog
    data class Ignore(val trashableCount: Int, val shouldTrash: Boolean = false) : AccountsDialog
}
```

- `AccountsSettingsState.dialog: AccountsDialog?` replaces `pendingMerge`, `mergeCount`, `isIgnoreDialogOpen`, `trashableCount`.
- New intent `TrashToggled(val isChecked: Boolean)`. `IgnoreConfirmed` loses its parameter; the VM reads `shouldTrash` from the state and keeps the `trashableCount > 0` guard in one place. The `remember` in `IgnoreDialog` goes away.
- The screen draws one dialog for `rememberLastNonNull(state.dialog)` with `isVisible = state.dialog != null`, so both dialogs animate.
- Both dialogs pass `isDestructive = true` (they use `Danger` today). The ignore dialog passes `content = null`, not an empty lambda, when `trashableCount == 0`.
- Update `AccountsSettingsViewModelTest` for the new state and intents.

### 4. Sheet close callback and cash entry

- `FinoteBottomSheet`, `ModalSheet` and `FinoteDatePicker` get `onDismissed: () -> Unit = {}`. It runs each time the sheet settles at `Hidden` while `isVisible` is false, whoever started the hide (user or caller), **including the first composition with `isVisible = false`**. `onDismiss` keeps its meaning: the user asked to close.
- Why the first composition counts: a rotation during the exit recreates the sheet with `isVisible = false` (the VM survives). A "hidden after being shown" rule would never fire again, and the translucent `CashEntryActivity` would stay open with nothing drawn, eating touches. Firing on a hidden start finishes it. Sheets that start closed (accounts, reimbursement) get an extra call to the default no-op; no caller but cash entry passes `onDismissed`.
- `CashEntryState` gets `isVisible: Boolean = true`. `Dismissed` and a successful save set it to false. The `Close` effect is removed if nothing else needs it. `isSaving` stays true after a successful save, so `isSaveEnabled` already blocks a second save during the exit; keep it that way.
- `CashEntryRoute` passes `state.isVisible` and calls `onClose` from `onDismissed`. Remove `SHEET_EXIT_MILLIS`, `delay`, `isVisible` and `isDismissedByUser` `remember` flags.
- Tests: `FinoteBottomSheetTest` gets "caller hide calls onDismissed after the animation", "user dismiss calls onDismiss, then onDismissed" (the fixture's `onDismiss` sets its `isVisible` to false, like a real caller), and "first composition with isVisible false calls onDismissed". `CashEntryViewModel` tests check `isVisible` after dismiss and after save.

### 5. Screenshots

- **Spike first (plan task 1):** in `LocalInspectionMode`, `FinoteDialog` and `ModalSheet` draw inline: nothing when `!isVisible`, else a full-size `Box` with the scrim and the card (centered) or `SheetSurface` (bottom), fully opaque, with no window and no animation (layoutlib does not run the animation clock). Check on one real screen (`AccountsSettingsScreen` with a merge dialog) that the overlay covers the whole screen, top bar included. If it does not, stop and fall back to approach (b).
- **Placement rule:** an inline overlay only covers what it is drawn after, in the same parent. Every screen calls its overlays at its root, after the screen body, inside one `Box` (for `AccountsSettingsScreen`: wrap the `Column` and the overlays in a `Box`). Never inside a `Scaffold` body or a scrolling `Column`. Run the spike with this placement, so a wrong placement is not mistaken for a failed approach.
- New `app/src/screenshotTest/.../ui/ScreenshotSupport.kt`: `PHONE`, the `ScreenPreviews` annotation (light, dark, font scale 2), `SCRIM_ALPHA` if still needed. Every screenshot file uses it; `CashEntryPreviews` keeps its taller device if the images need it.
- Feature screenshots render the real screen with state that opens the overlay. Remove `DialogFrame`, `SheetFrame`, `SheetOverScrim`, `ScreenBehind`, `Backdrop` where the real screen replaces them. No screenshot imports `DialogCard` or `SheetSurface`.
- `docs/design-system.md` Blur section: replace "Screenshots ... draw the internal surface over a scrim" with the inspection-mode rule.
- Regenerate references with `updateDebugScreenshotTestDefaultTestSuite`; the user looks at the changed images before the commit.

### 6. `FinoteDatePicker`

Kept (roadmap: `recurring-plan` start date, editing a transaction date). It gets `onDismissed` and the new behavior through `ModalSheet`. Its own Cancel/OK row stays.

## Constraints

- Konsist and detekt rules stay green; `qualityGate` passes before the squash.
- Strings come from `strings.xml`; no new raw numbers.
- No commit adds AI attribution; commit scope `ui`.

## Left to the builder

- Where exactly the inspection-mode branch sits (inside `ModalSheet` / `FinoteDialog`, or a small internal `InlineOverlay`).
- Whether `CashEntryEffect.Close` survives.
- Test names and fixture shapes.
