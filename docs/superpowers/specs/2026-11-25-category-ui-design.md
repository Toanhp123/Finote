# Category management (roadmap step 15): design

Date: 2026-11-25. Status: implemented on `feature/category-ui` (2026-11-25).
Branch (planned): `feature/category-ui` from `dev`. Source: `docs/roadmap.md` section 5, step 15; debt N-11 (DAO part). Spec 3.2 (Category table), spec 4 "Category Core". Related: step 16 (`CategoryKeywordRule`), step 18 (`Budget`), step 26 (trash).

## Why

The user has hit categories that do not fit how they spend, and wants to fix them in the app, "like Cashew": add, edit, delete. Step 16 (auto category) and step 17 (dashboard by category) need a category tree the user trusts, so this comes first.

Today the domain is mostly done and no screen calls it:

- `CreateCategoryUseCase`, `UpdateCategoryUseCase`, `DeleteCategoryUseCase`, `SetCategoryHiddenUseCase`, `ObserveCategoriesUseCase`;
- `CategoryRules`: two levels, a child has the type of its parent, a system category cannot be deleted (only hidden) and cannot change parent, a parent with live children cannot become a child, type never changes;
- 47 seeded system categories (17 top-level, 30 children) with `nameKey` for 4 languages;
- `sortOrder` exists, but nothing reorders;
- only 17 icons (`SystemIcons` in `ui/common/format/SystemResources.kt`) and 14 colors (`CategoryPalette`).

## Prior art (researched 2026-11-25)

- Actual Budget, YNAB, PocketSmith: deleting a category in use asks which category takes its transactions. PocketSmith also moves budgets and category rules; without a target, transactions become uncategorised.
- Wallet (BudgetBakers): blocks delete while in use. Users dislike it (Wealthfolio issue #2480 asks to replace the same behavior with "move then delete").
- Money Lover: "Merge category" moves transactions and makes the merged category's children children of the target; cannot be undone. Some default categories cannot be edited or deleted.
- Cashew: subcategories are created from the parent's edit page; hide is an eye toggle on the edit-and-reorder list; "Convert into subcategory".

## Goals

1. A Categories page under Settings with Expense / Income tabs. Top-level categories only, each with its child count and an expand arrow that shows its children (layout B, chosen by the user).
2. Create a category: name, parent (none = top level), icon, color. Type comes from the tab.
3. Edit a category: same fields. System categories cannot change parent and have no delete.
4. A "Subcategories" section on a parent's edit screen with "+ Add subcategory" (from Cashew).
5. Hide and unhide with an eye button on the row (from Cashew) and on the edit screen. Hidden categories sit in a collapsed "Hidden (n)" section at the bottom of each tab.
6. Delete with move: deleting a category that has transactions asks where they go, another category of the same type or "No category" (option B, chosen by the user). This doubles as merge.
7. Delete a whole branch: deleting a parent with children deletes the children too, and all their transactions move to the chosen target (option 2, chosen by the user).
8. Reorder by drag and drop: top-level among top-level, children within their parent. Moving a child to another parent is done on the edit screen.
9. A larger icon set: about 60–80 icons, grouped.
10. Fix N-11 (DAO part): `CategoryDao.observeCategories` gets the `id` tie-break.

## Non-goals

- Creating a category from the cash-entry picker (Money Lover does it). Later: it touches the stable sheet.
- Money Lover style merge where children follow the target. The user chose branch delete; with two levels the target may itself be a child and cannot take children.
- Free color picker. `docs/design-system.md` says `CategoryPalette` holds the only colors a user may pick.
- Trash screen and restore: step 26.
- Per-category totals on this page: dashboard, step 17.
- Changing a category's type.

## Anti-goals

- Losing a transaction's category without the user choosing it. Every delete that touches transactions says how many and where they go.
- Touching balances. Moving transactions changes only `categoryId`.
- Breaking transfer pairing by the move (see "Move does not stamp `updatedAt`").

## Approaches

1. **Follow the account-management pattern (chosen).** Two features, `ui/settings/categories` (list) and `ui/settings/categoryedit` (edit), shaped like `accounts` and `accountedit`. Drag with `sh.calvin.reorderable` as in `ReorderList.kt`, with accessibility move actions. Icons as vector drawables plus the explicit `SystemIcons` table. Domain: extend `DeleteCategoryUseCase`, add `ReorderCategoriesUseCase`.
2. **`material-icons-extended` dependency for icons.** Rejected: large, and the stored icon name still needs a name-to-icon table.
3. **Edit in a bottom sheet instead of a screen.** Rejected: a 60–80 icon grid is cramped in a sheet, and it differs from the account edit screen the user knows.

The user approved approach 1 and the two Cashew extras (eye button, subcategories section) on 2026-11-25.

## Decided with the user

### List page (layout B)

```
← Categories                 ⇅  +
[ Expense | Income ]
(icon) Food & drink · 2      👁  ▾
      (icon) Breakfast       👁
      (icon) Coffee  (dimmed) ⊘
(icon) Transport · 1         👁  ▸
(icon) Home · 3              👁  ▸
Hidden (3) ▸
```

- Tapping a name opens the edit screen. Tapping the arrow expands or collapses.
- The selected tab and the expanded set are screen state, not saved.
- "+" and "Reorder" sit on `FinoteTopBar`, as on `AccountsSettingsScreen` (the design system has no FAB, and Konsist bans the Material 3 one).
- "+" opens create with the current tab's type and no parent.
- `· n` counts all live children, hidden ones included (they show dimmed when expanded). A category with no child has no count and no arrow.
- "Hidden (n)": n counts hidden top-level categories of the tab only. Hidden children of a visible parent stay under the parent.
- When every top-level category of a tab is hidden, the empty state must not hide the "Hidden (n)" section.

### Delete with move

- The sheet counts the live transactions of the category and of its live children.
- With zero live transactions: a plain confirm. With some: "N transactions. Move them to: [list]". The list holds live categories of the same type, except the deleted category and its children, plus "No category".
- One write transaction: soft-delete the category and its live children, then set `categoryId` of every transaction pointing at any of them to the target (or null).

### System categories

- Name, icon and color are editable (spec 3.2: renaming clears `nameKey` and the name is no longer translated). No "reset to default" in this step.
- Parent cannot change, no delete; hide is allowed (existing `CategoryRules`).
- A user can add subcategories under a system parent; "+ Add subcategory" shows on system parents too. A user child under a system parent is deleted like any user category; the system parent is never part of a branch delete.

### Editing a transaction whose category is hidden (decided 2026-11-25)

- The edit form keeps the hidden category: it shows as selected, and saving keeps `categoryId`. Today `CashEntryDefaults.pickCategory` turns it into "No category", so saving a note change wipes the category.
- The picker of that transaction lists its own hidden category too, so the user can see and keep it. Other hidden categories stay out. New transactions never offer hidden categories.
- A deleted category is not kept: after delete-with-move no transaction points at one.
- The test `a hidden category shows as none` (`CashEntryEditViewModelTest`) flips to "a hidden category stays selected".

### Branch delete

- `CategoryRules.checkDelete` no longer refuses `HasChildren`. It still refuses a system category. `CategoryError.HasChildren` stays: `checkUpdate` still uses it (a parent with children cannot become a child).
- A user parent can only have user children (system children cannot change parent), so a branch delete never reaches a system row. The rule still checks every row in the branch.

## My calls (the user can override)

### Data and domain

- **Move does not stamp `updatedAt`.** `TransferPairing` pairs only rows with `updatedAt == createdAt`. Stamping would stop a notification row younger than 30 minutes from pairing. A category change has nothing to do with pairing. Same pattern as `AccountMergeDao`.
- **Move every row, including soft-deleted and paired-away rows** (`deletedAt` set, `pairedIntoId` set). Restoring them later (trash, step 26; "Not a transfer" unpair) must not point at a deleted category. The count shows live rows only. With zero live rows the use case moves the remaining (deleted) rows to null: nothing the user can see loses its category.
- **Delete target is a type, not a nullable id:** `MoveTarget = Uncategorized | Into(id)`. The UI keeps its own "nothing picked yet" state; the use case never sees it.
- **Target rules** (in `CategoryRules`, new error `CategoryError.InvalidMoveTarget`): the target exists, is live, has the same type, and is not in the branch being deleted. A hidden target is allowed by the domain; the UI list leaves hidden categories out.
- **Accepted race:** the count is read when the sheet opens. A transaction booked into the category while the sheet is open moves with the others to the chosen target. Today nothing books into a category in the background; step 16 (auto category) must recheck this.
- **One place for "category X is going away".** The repository method that moves rows is the only one that knows which tables point at a category. Step 16 (`CategoryKeywordRule`) and step 18 (`Budget`) add their table there. Recorded as a debt line for those steps.
- **Restoring a deleted category later (step 26) does not move transactions back.** The sheet says the move is permanent.
- **New category goes last:** `CreateCategoryUseCase` gives `sortOrder = max + 1` among its level (same type and same `parentId`), 0 when the level is empty. Today it keeps the caller's value (0), so a new category jumps to the second place. Changing the parent on edit also puts the category last in its new level.
- **Reorder covers hidden rows:** the written order is the visible rows in the new order, then the hidden rows of that level in their old order, so every row of the level gets its own `sortOrder` (as `AccountsSettingsViewModel.finishReorder` appends archived accounts). Writes one level per call, in one transaction.
- **Hiding a parent hides its children, derived at read time.** `SetCategoryHiddenUseCase` stays a one-row write; children keep their own `isHidden`. A category is effectively hidden when it or its parent is hidden. Put that check in one shared place in the domain model; pickers (cash entry) use it. Today a child of a hidden parent shows as a root (`asTree` treats a missing parent as root), which this fixes.
- **A deleted category is not valid for a write:** `TransactionRepositoryImpl.getCategoryType` and `FailedInboxRepositoryImpl` (about line 168) read the type with `getById` and ignore `deletedAt`. Return null for a deleted row, so `TransactionRules` answers `CategoryNotFound`. Covers a cash-entry sheet left open while its category is deleted.

### Screens

- **Delete is a bottom sheet, not a dialog.** One `FinoteBottomSheet` in the edit feature's `component/`, shaped like `MergeTargetPickerSheet`: count text, the target list, and a danger `FinoteButton` disabled until a target is picked. `FinoteDialog` has no disabled-confirm option and is too narrow for a 40-row list. Zero live transactions: a plain `FinoteDialog` confirm.
- **Reorder top level on the list page, children on the parent's edit screen.** The list page has a reorder mode from the top bar, top-level rows only, like Accounts. The "Subcategories" section of a parent's edit screen has its own drag handles. No nested drag in one list.
- **Edit screen, parent field:**
  - Locked with a short reason when the category has live children (rule `HasChildren`), or is a system category.
  - The list offers visible top-level categories of the same type, plus the current parent even when hidden.
- **Edit screen, "Subcategories" section:** only in edit mode of a top-level category. While the draft's parent field is changed and not saved, the section is hidden.
- **Hint on a child of a hidden parent:** "Hidden because its parent is hidden", so the user knows why it does not show in the cash-entry sheet.
- **Hidden on the list page:** the "Hidden (n)" section holds hidden top-level categories with all their children. A hidden child of a visible parent stays under its parent, dimmed, with the eye-off icon. Unhiding a child of a hidden parent is allowed; it shows again once the parent is unhidden.
- **A child gets its own icon and color.** On create from "+ Add subcategory", they start as the parent's.
- **Icons:** about 60–80 icons, filled style with a 24×24 viewport like the current drawables (`ic_restaurant.xml`), so old and new match. Grouped in the picker: food, transport, home, shopping, health, fun, study, family, money, other. The 17 current names stay valid; add the existing `ic_local_gas_station` to `SystemIcons`. The group list lives in its own file under `ui/common/format/`, apart from `SystemResources.kt`. `AccountFormSection` keeps its own short list.
- **Entry point:** a new "Categories" section on the Settings page, under Accounts (the Accounts section title is about accounts). The Settings column gets `verticalScroll` for 200% font scale. New nav keys like `AccountsSettings` and `EditAccount`.
- **Long rows:** a parent row holds icon, name, count, eye and arrow buttons. Rely on `FinoteListRow` wrapping; add a 2× font-scale preview as on other screens.

## Constraints

- Money is `Long` VND; this feature does not touch amounts.
- `CategoryRepository.update` contract: pass the stored `name` unless the user edited the name field; otherwise `nameKey` is cleared and the category loses its translation. The edit screen must show `displayName(name, nameKey)` but send back the stored `name` when the field is unchanged.
- Strings in `values/` (Vietnamese, the default), `values-en`, `values-zh-rCN`, `values-ja`. Machine-written `ja` and `zh-rCN` fall under debt N-16.
- Layers, naming and limits: `docs/architecture.md`; Konsist tests. UI pieces (dialogs, sheets) each in their own file under the feature's `component/` (project rule).
- UI tokens and components: `docs/design-system.md`. Category tiles use `CategoryIcon`.
- No DB schema change is expected (no new column). If the builder finds one is needed, it needs a migration (N4).
- Every multi-row write in one `TransactionRunner.write`.

## Left to the builder

- The exact icon set and its groups.
- How the sheet gets the count (a DAO count query over a list of category ids is enough).
- Whether `ReorderList.kt` is shared or copied: share only if it fits without account-specific branches.
- Error to snackbar mapping on the new screens, following the account screens.
- Tests: rule changes in `CategoryRules`, the delete-and-move use case (branch, target validation, `updatedAt` untouched, deleted rows moved), create `sortOrder`, reorder with hidden rows, DAO tie-break, `getCategoryType` on a deleted row, ViewModel state.
- Tests that change with this design: `CategoryRulesTest` (`checkDelete` with children), `DeleteCategoryUseCaseTest`, `CategoryUseCaseTransactionTest`, `FakeCategoryRepository` (add `thenBy { it.id }` with N-11).
