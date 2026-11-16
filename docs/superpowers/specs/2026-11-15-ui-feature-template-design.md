# UI feature template: design

Date: 2026-11-15. Status: implemented.
Branch: `refactor/ui-feature-template`, cut from `dev` after the transaction-edit work lands.
Revises section 3.4 of `2026-11-07-ui-packaging-review.md`.

## Why

The app has three real screens and they already disagree on where UI code goes. One screen keeps six private composables in its `*Screen.kt`, another splits the same kind of piece into `component/`. Dialogs sit in the feature root in one place and in `component/` in another. `ui/cashentry/` has eight sibling files with five different roles, so finding the logic to read means opening files one by one.

The user comes from Feature-Sliced Design, where the folder name says the role. The goal is the same here: the place of a file tells its job, the rule is checked by a machine, and every later screen copies one template. Fixing it now is cheap because only three screens exist.

`docs/architecture.md` is the root cause. It says `component/` holds "sub-composables used by this feature only" and gives no rule for when a composable must move there. No Konsist test covers it.

## Goals

1. One template for every `ui/<feature>/`, written in `docs/architecture.md`.
2. Konsist tests fail the build when a file breaks the template.
3. The existing screens follow the template.

## Non-goals

- `ui/common/` and `ui/navigation/`. They are not features and keep their own rules.
- The inside of any `*Contract`, `*ViewModel`, or composable. Files move; bodies do not change.
- `domain/` and `data/`.
- Splitting private section composables out of a `*Screen.kt` that is under the limits.

## Anti-goals

- Any change in behavior. Screenshot tests must pass with no regenerated image.
- A folder that every small screen must create and leave nearly empty.
- A file that only wraps one call, created just to satisfy a rule.

## What others do

Measured on the main branches on 2026-11-15:

| Project | Screen file | Private pieces in it | Where extracted pieces go |
|---|---|---|---|
| Now in Android | `ForYouScreen` 606 lines | 6–12 | Only shared ones, to `core/ui` |
| Tivi | `ShowDetails` 1083 lines | 20 | Not extracted |
| Bitwarden | `VaultAddEditScreen` 897 lines | 6 | Content variants in own files; `component/`, `model/`, `util/`, `handlers/` under a feature |
| Element X | `RoomDetailsView` 943 lines | 20 | Rarely extracted |

No project forbids private composables in the screen file. Pieces leave it when they are reused or are a content variant of their own. Marco Gomiero's guide puts the route, the stateless screen, and the preview in one file per screen. Bitwarden is the one project that gives each feature role folders.

## Approaches

Screen file:

- **A. Only `Route` and `Screen` in `*Screen.kt`.** Fully mechanical, but no surveyed project does it and it creates many tiny files. Rejected.
- **B+. Private sections stay; fixed triggers force a move to `component/`.** Picked. It matches the surveyed projects and the triggers are checkable.

Feature root:

- **1. Flat root with strict suffixes.** Cheapest, but `cashentry` keeps eight sibling files. Rejected.
- **2. Full FSD segments in every feature.** Uniform, but a three-file screen becomes three folders of one file. Rejected.
- **3. Fixed root, the rest in role folders.** Picked. The root holds only files with a fixed role suffix; everything else goes to `component/` or `logic/`. A small screen stays three files.

The folder is named `logic/`, not `util/`: `docs/architecture.md` already calls the role "Screen logic", and `util/` attracts unrelated code.

## Design

### Template

```text
ui/<feature>/
├── <Feature>Contract.kt     # State, Intent, Effect
├── <Feature>ViewModel.kt    # takes intents, emits state and effects
├── <Feature>Screen.kt       # <Feature>Route + <Feature>Screen + private sections
├── *Activity.kt, *TileService.kt   # Android entry points, only when the feature has one
├── component/               # composables of this feature, one per file
└── logic/                   # pure screen logic, no Compose
```

`component/` and `logic/` exist only when a file needs them. A feature may hold sub-features (`settings/accounts`, `settings/capture`); each sub-feature follows the same template, with its own `component/` and `logic/` beside its `*Screen.kt`.

FSD map, for orientation: `ui` is `*Screen.kt` plus `component/`; `model` is `*Contract.kt` plus `*ViewModel.kt`; `lib` is `logic/`; `shared` is `ui/common/`.

### Rules

1. **Feature root.** A file directly in a feature package ends with `Contract`, `ViewModel`, `Screen`, `Activity`, or `TileService`.
2. **Screen file.** `<Feature>Screen.kt` declares `<Feature>Screen` and, when the screen has a ViewModel, `<Feature>Route`. Every other top-level declaration in it is `private`. Order: `Route`, `Screen`, sections top-down, `@Composable` text helpers last.
3. **A composable leaves the screen file for `component/` when** it is a dialog or a sheet, or a second file uses it, or the file would pass the 400-line limit (move the largest section). A page of a multi-page screen also moves; this last trigger is judgment and is checked by `reviewer`.
4. **Component file.** A file in a feature's `component/` declares exactly one non-private composable, named like the file. Its helpers are `private` and sit below it. No grab-bag files (`*Pickers`, `*Dialogs`).
5. **Logic file.** A file in `logic/` holds pure input or display logic for the feature: no `@Composable`, no `androidx.compose` import. It may co-locate a small type with the functions that serve it.
6. **Text helpers.** A `@Composable` function that only turns state into a string through `stringResource` stays `private` in the file that calls it.
7. **Visibility.** Feature composables are public or `private`. No `internal` in a `*Screen.kt` or a feature `component/` file.
8. **ViewModel base.** Every `*ViewModel` class in `ui/` extends `BaseViewModel`, so each screen has one `state`, one `effects` flow, one `onIntent`, and the shared error handling. All six do today, by habit only.
9. **Hosts.** Inside the app, a screen is reached through `FinoteNavHost` in the main Activity, never through `startActivity`. A feature has its own `*Activity` only for entry from outside the app (Quick Settings tile, shortcut, notification, widget), where no main Activity is running to draw it. `Route` and `Screen` do not know their host: closing is a callback the host supplies. Why: the list once opened `CashEntryActivity`, which owns a separate task for the tile, so the edit sheet and the app were two windows that the system ordered on its own.
10. **Tests** follow the package of their subject.

### Konsist

New tests, in `LayoutKonsistTest` or a new file beside it:

- rule 1: allowed suffixes in a feature root;
- rule 2: non-private declarations of a `*Screen.kt` are only `<X>Screen` and `<X>Route`;
- rule 3, in part: a top-level composable named `*Dialog` or `*Sheet` in a feature is in `component/` (the screen itself, `<X>Screen`, is exempt whatever it draws);
- rule 4: one non-private composable per feature `component/` file, named like the file;
- rule 5: no `@Composable` and no `androidx.compose` import in `logic/`;
- rule 7: no `internal` composable in `*Screen.kt` or feature `component/`;
- rule 8: every class named `*ViewModel` under `ui` has `BaseViewModel` as its parent (`BaseViewModel` itself is exempt);
- rule 9, in part: no `startActivity` call and no `Intent` built for an `*Activity` of this app in a `*Screen.kt` or a feature `component/` file. Whether an Activity is a real outside entry is checked by `reviewer`.

Rule 9's test can only be switched on once the edit sheet is hosted in the main Activity (session `edit-sheet-in-app`). If the `TransactionEditActivity` patch is kept instead, the rule stays in the docs, the test waits, and the gap is recorded in `docs/roadmap.md` section 6.

The "second file uses it" trigger needs no test: a `private` composable cannot be used from another file, and rule 2 forbids a non-private one in the screen file.

A feature package is any package under `ui` except `ui.common` and `ui.navigation`, and except `component` and `logic` folders.

### Moves

`ui/cashentry/`

- Root keeps `CashEntryContract`, `CashEntryViewModel`, `CashEntryScreen`, `CashEntryActivity`, `CashEntryTileService`, and `TransactionEditActivity` once it lands.
- `CashEntrySheet` is renamed `CashEntryScreen`.
- To `logic/`: `AmountExpression`, `CashEntryDefaults`, `CashEntryPrefill`, and `ReimbursementChoice` (now in `component/`, has no composable).
- `component/CashEntryPickers.kt` (383 lines, 13 composables) is split by its non-private composables: `AccountList`, `CategoryList`, `Selectors`, and the text page behind `NotePage` and `FeePage`. `PickerRow` serves both lists, so it gets its own file.
- `KeypadPage` (with `AmountHeader`) and `PickerPage` leave `CashEntryScreen.kt` (381 lines) for `component/`: they are pages of a multi-page screen.

`ui/transactions/`

- To `logic/`: `TransactionOpenRule`, `TransactionRowText` (now in `component/`, has no composable).
- `TransactionListScreen.kt` keeps its private sections.

`ui/settings/`

- To `logic/`: `BankSummary`. `SettingsViewModel` and the capture sub-feature both use it, so it stays at the `settings` level.

`ui/settings/accounts/`

- `AccountsSettingsDialogs.kt` becomes `component/MergeDialog.kt` and `component/IgnoreDialog.kt`; `MergeBalances` and `TrashOption` become private helpers of their dialog.
- `AccountSheet`, `AccountSheetContent`, and `MergeTargets` leave the screen file for `component/AccountSheet.kt`.

`ui/settings/capture/`, `ui/setup/`: no move.

Tests and screenshot tests of a moved file move with it.

### Docs

- `docs/architecture.md`: replace "Feature template"; amend folder rules 3 and 4 (a feature root holds only the fixed suffixes; `component/` and `logic/` are exempt from the three-file minimum; sub-features are allowed); set the "Screen logic" row to `ui/<f>/logic`; list the new Konsist tests.
- `docs/design-system.md`: fix any path the moves change.

## Constraints

- `qualityGate` green before the squash. Screenshot tests pass without regenerating images.
- Moves change package and import lines only, plus the one rename and the visibility changes rule 7 requires (`internal` composables in feature `component/` files become public; merged helpers become `private`).
- The docs rule change goes in its own commit, before the moves, as `docs/architecture.md` requires for a rule change.
- Start after the transaction-edit branches land, so the moves do not collide with files in flight.

## Left to the builder

- Whether `NotePage` and `FeePage` stay as two thin files over a shared text page or collapse into one composable the caller configures. Prefer the fewer files if the call sites stay clear.
- The names of the files that `KeypadPage` and `PickerPage` move to.
- One Konsist file or an addition to `LayoutKonsistTest`, and the test names.
- The commit split per feature.
- If a rule turns out wrong for a real case during the moves, stop and propose a rule change instead of bending the code.
