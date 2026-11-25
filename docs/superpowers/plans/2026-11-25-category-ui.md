# Category Management Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let the user create, edit, hide, reorder and delete categories (delete moves their transactions), with a larger icon set (roadmap step 15, debt N-11 DAO part).

**Architecture:** Domain first: `CategoryRules` gains branch delete and move-target checks, `CategoryVisibility` decides what pickers offer, and `CategoryRepository.deleteAndMove` is the one place that knows which tables point at a category. Two new sub-features, `ui/settings/categories` (list, layout B) and `ui/settings/categoryedit` (create/edit, delete sheet), shaped like `accounts` and `accountedit`. Cash entry keeps a hidden category on an edited transaction.

**Tech Stack:** Kotlin, Compose Material 3, Room 3, Hilt, Navigation3, `sh.calvin.reorderable` (already in the catalog), JUnit + Robolectric + Turbine.

**Spec:** `docs/superpowers/specs/2026-11-25-category-ui-design.md`

## Global Constraints

- Branch `feature/category-ui` from `dev`. Commit scopes: `db`, `ui`, `build`, or none (`docs:`); `category` is not an allowed scope. No AI attribution in commits. Pre-commit runs spotless and detekt; `qualityGate` also runs detekt on test sources.
- No DB schema change: no new column, no `DATABASE_VERSION` bump. A task that seems to need one stops and asks.
- Soft delete only (`deletedAt`). Money is untouched by this feature.
- Moving transactions never writes `updatedAt` (`TransferPairing` pairs only `updatedAt == createdAt`).
- Every multi-row write runs in one `TransactionRunner.write` (use case) / `db.storageWrite` (repository).
- `CategoryRepository.update` contract: send the stored `name` unless the user edited the name field, else `nameKey` is cleared.
- Layers, naming, limits: `docs/architecture.md` (400 lines per file, 60 per function, 80 per `@Composable`, 120 per `*Screen`). Dialogs and sheets in their own file under the feature's `component/`. A composable used by two features lives in `ui/common/component/<group>/`. Tokens and components: `docs/design-system.md`; no Material 3 FAB or dialog in features.
- Strings in `values/` (Vietnamese, default), `values-en`, `values-zh-rCN`, `values-ja`; zh and ja may be machine-written (N-16).
- Gradle runs through the `gradle-run` skill: `'.\gradlew.bat' testDebugUnitTest --tests "<pattern>"`; before the squash, `'.\gradlew.bat' qualityGate`.
- Skills for implementers: `ponytail:ponytail` always; `compose-state-and-effects` for UI tasks; `kotlin-concurrency-and-flow` for flows; `testing-coroutines-with-runtest`, `testing-flows-with-turbine`, `using-robolectric-correctly` for tests.

## Review Focus

1. A notification row younger than 30 minutes is moved by a category delete and must still auto-pair. Test: Task 2, `deleteAndMove keeps updatedAt`.
2. A user hides a category, then edits only the note of an old transaction in it: the category survives the save. Test: Task 4, `a hidden category stays selected`.
3. A category is created after the user reordered: it lands last, not second. Test: Task 3, `create puts the category last in its level`.
4. The category is deleted while its edit screen or the cash-entry sheet is open: the edit screen closes; a save from cash entry is refused with `CategoryNotFound`, not written to a deleted category. Tests: Task 6, `category gone closes the screen`; Task 2, `category type of a deleted category is null`.
5. Reorder while some rows of the level are hidden: every row ends with its own `sortOrder`, the hidden ones after the visible ones. Test: Task 3, `reorder appends unlisted rows of the level in their old order`.

---

### Task 1: Domain rules, move target, visibility

**Files:**
- Modify: `app/src/main/java/io/github/toanhp123/finote/domain/category/service/CategoryRules.kt`
- Modify: `app/src/main/java/io/github/toanhp123/finote/domain/category/error/CategoryError.kt`
- Create: `app/src/main/java/io/github/toanhp123/finote/domain/category/model/MoveTarget.kt`
- Create: `app/src/main/java/io/github/toanhp123/finote/domain/category/service/CategoryVisibility.kt`
- Test: `app/src/test/java/io/github/toanhp123/finote/domain/category/service/CategoryRulesTest.kt`, `.../domain/category/service/CategoryVisibilityTest.kt`

**Interfaces:**
- Produces:
  - `sealed interface MoveTarget { data object Uncategorized : MoveTarget; data class Into(val id: String) : MoveTarget }`
  - `CategoryError.InvalidMoveTarget` (data object). `HasChildren` stays (used by `checkUpdate`).
  - `CategoryRules.checkDelete(old: Category): CategoryError?` (drops the `liveChildren` parameter; refuses only `isSystemDefault`).
  - `CategoryRules.checkMoveTarget(deleted: Category, target: Category?, branchIds: Set<String>): CategoryError?`; `target` is the stored row of a `MoveTarget.Into`, null when missing.
  - `object CategoryVisibility { fun isShown(category: Category, parent: Category?): Boolean }` and `fun List<Category>.shown(): List<Category>` (in the same file).

- [ ] **Step 1: Write the failing tests**

`CategoryRulesTest`: replace the `checkDelete` children test with:
- `delete of a parent with children is allowed` → `checkDelete(userParent)` is null.
- `delete of a system category is refused` → `SystemCategory`.
- `move target must be live, same type, outside the branch`: missing → `InvalidMoveTarget`; deleted → `InvalidMoveTarget`; INCOME target for an EXPENSE category → `InvalidMoveTarget`; target id in `branchIds` → `InvalidMoveTarget`; live hidden EXPENSE target outside the branch → null.

`CategoryVisibilityTest`:
- `a live visible top-level category is shown`
- `a hidden category is not shown`
- `a visible child of a hidden parent is not shown`
- `a deleted category is not shown`
- `shown keeps order and drops hidden branches` → for [P(hidden), P.c1, Q, Q.c2(hidden)], `shown()` is [Q].

- [ ] **Step 2: Run them, expect compile failures**

Run: `'.\gradlew.bat' testDebugUnitTest --tests "*CategoryRulesTest" --tests "*CategoryVisibilityTest"`
Expected: FAIL (unresolved `MoveTarget`, `CategoryVisibility`, `InvalidMoveTarget`).

- [ ] **Step 3: Implement** the signatures above. `isShown`: `deletedAt == null && !isHidden && (parent == null || (!parent.isHidden && parent.deletedAt == null))`. `shown()` looks parents up by id in the same list; a child whose parent is not in the list counts as having no parent.

- [ ] **Step 4: Run the tests, expect PASS** (same command). `DeleteCategoryUseCase` stops compiling; Task 3 fixes it, so in this task change its call to `checkDelete(old)` only.

- [ ] **Step 5: Commit** `git commit -m "feat: allow branch delete and check move targets in category rules"`

---

### Task 2: Repository and DAO: level, count, delete-and-move, reorder, deleted type

**Files:**
- Modify: `app/src/main/java/io/github/toanhp123/finote/data/category/local/dao/CategoryDao.kt`
- Modify: `app/src/main/java/io/github/toanhp123/finote/domain/category/repository/CategoryRepository.kt`
- Modify: `app/src/main/java/io/github/toanhp123/finote/data/category/repository/CategoryRepositoryImpl.kt`
- Modify: `app/src/main/java/io/github/toanhp123/finote/data/transaction/repository/TransactionRepositoryImpl.kt:46-47`
- Modify: `app/src/main/java/io/github/toanhp123/finote/data/notification/repository/FailedInboxRepositoryImpl.kt:168`
- Modify: `app/src/test/java/io/github/toanhp123/finote/domain/category/usecase/FakeCategoryRepository.kt`
- Test: `.../data/category/local/dao/CategoryDaoTest.kt`, `.../data/category/repository/CategoryRepositoryImplTest.kt`, `.../data/transaction/repository/TransactionRepositoryImplTest.kt`, `.../domain/category/usecase/FakeCategoryRepositoryTest.kt`

**Interfaces:**
- Produces on `CategoryRepository`:
  - `suspend fun getLevel(type: CategoryType, parentId: String?): List<Category>`: live rows of one level, ordered `sortOrder, createdAt, id`; hidden ones included.
  - `suspend fun countLiveTransactions(categoryIds: Set<String>): Int`
  - `suspend fun deleteAndMove(categoryIds: Set<String>, targetId: String?, deletedAt: Long)`: KDoc says it is the only place that knows which tables point at a category; step 16 (`CategoryKeywordRule`) and step 18 (`Budget`) add their table here.
  - `suspend fun reorder(orderedIds: List<String>, now: Long)`: index becomes `sortOrder`.
  - `softDelete` is removed (no caller after Task 3); remove it from the fake and its tests.
- DAO queries (Room):
  - `observeCategories`: `ORDER BY sortOrder ASC, createdAt ASC, id ASC` (N-11).
  - `getLevel`: `... WHERE type = :type AND parentId IS :parentId AND deletedAt IS NULL ORDER BY sortOrder, createdAt, id`.
  - `countLiveTransactions`: `SELECT COUNT(*) FROM transactions WHERE deletedAt IS NULL AND categoryId IN (:ids)`.
  - `moveTransactions(ids, to)`: `UPDATE transactions SET categoryId = :to WHERE categoryId IN (:ids)`: every row, deleted and paired-away too, no `updatedAt`.
  - `softDeleteAll(ids, now)`: `UPDATE categories SET deletedAt = :now, updatedAt = :now WHERE id IN (:ids) AND deletedAt IS NULL`.
  - `setSortOrder(id, sortOrder, now)`.
- `TransactionRepository.getCategoryType` returns null for a soft-deleted category; the same for the read in `FailedInboxRepositoryImpl`.
- Fake: keeps `stored`; adds `transactionCategory: MutableMap<String, String?>` (transaction id → category id) so use-case tests can count and check moves; `observeCategories` sorts `thenBy { it.id }`.

- [ ] **Step 1: Write the failing tests**
- `CategoryDaoTest`: `observeCategories breaks ties by id`; `getLevel returns one level with hidden rows`; `getLevel with null parent returns top level of one type`.
- `CategoryRepositoryImplTest`:
  - `deleteAndMove moves live, deleted and paired rows`: three transactions in category A (live, `deletedAt` set, `pairedIntoId` set) → all have `categoryId == B`.
  - `deleteAndMove keeps updatedAt`: a row with `updatedAt == createdAt` keeps both.
  - `deleteAndMove to null clears the category`.
  - `deleteAndMove soft-deletes every id`: A and its child both have `deletedAt`.
  - `countLiveTransactions ignores deleted rows`.
  - `reorder writes index as sortOrder`.
- `TransactionRepositoryImplTest`: `category type of a deleted category is null`.
- Fake test: `observeCategories breaks ties by id`.

- [ ] **Step 2: Run, expect FAIL**: `'.\gradlew.bat' testDebugUnitTest --tests "*CategoryDaoTest" --tests "*CategoryRepositoryImplTest" --tests "*TransactionRepositoryImplTest" --tests "*FakeCategoryRepositoryTest"`

- [ ] **Step 3: Implement.** `deleteAndMove` runs `moveTransactions` then `softDeleteAll` inside one `db.storageWrite`. `reorder` loops `setSortOrder` inside one `db.storageWrite` (same as `AccountRepositoryImpl.reorder`).

- [ ] **Step 4: Run, expect PASS** (same command).

- [ ] **Step 5: Commit** `git commit -m "feat(db): add category level, count, delete-and-move and reorder queries"`

---

### Task 3: Use cases: create last, parent change last, delete with move, count, reorder

**Files:**
- Modify: `app/src/main/java/io/github/toanhp123/finote/domain/category/usecase/CreateCategoryUseCase.kt`
- Modify: `.../domain/category/usecase/UpdateCategoryUseCase.kt`
- Modify: `.../domain/category/usecase/DeleteCategoryUseCase.kt`
- Create: `.../domain/category/usecase/CountCategoryTransactionsUseCase.kt`
- Create: `.../domain/category/usecase/ReorderCategoriesUseCase.kt`
- Test: `CreateCategoryUseCaseTest`, `UpdateCategoryUseCaseTest`, `DeleteCategoryUseCaseTest`, `CategoryUseCaseTransactionTest`, new `CountCategoryTransactionsUseCaseTest`, `ReorderCategoriesUseCaseTest` (all in `app/src/test/java/io/github/toanhp123/finote/domain/category/usecase/`)

**Interfaces:**
- Consumes: Task 1 rules and `MoveTarget`; Task 2 repository methods.
- Produces:
  - `DeleteCategoryUseCase.invoke(id: String, target: MoveTarget): Outcome<Unit, CategoryError>`
  - `CountCategoryTransactionsUseCase.invoke(id: String): Int`: live transactions of the category and of its live children.
  - `ReorderCategoriesUseCase.invoke(type: CategoryType, parentId: String?, orderedIds: List<String>): Outcome<Unit, CategoryError>`
  - `CreateCategoryUseCase`: ignores the caller's `sortOrder`, writes `(getLevel(type, parentId).maxOfOrNull { it.sortOrder } ?: -1) + 1`.
  - `UpdateCategoryUseCase`: when `parentId` changes, the same "last in the new level" value.

- [ ] **Step 1: Write the failing tests**
- `create puts the category last in its level`: level holds sortOrder 0, 1, 5 → new row gets 6; empty level → 0; caller passes 0 → still 6.
- `moving a child to another parent puts it last there`.
- Delete:
  - `delete of a parent deletes its live children and moves all their transactions` (target `Into(B)`): parent and child have `deletedAt`, both transactions point to B.
  - `delete to Uncategorized clears the category`.
  - `delete refuses a target inside the branch` → `InvalidMoveTarget`, nothing written (`writes == 0`).
  - `delete refuses a system category` → `SystemCategory`.
  - `delete of an unknown id is NotFound`; `delete of a deleted category does nothing` (Ok, no write).
- `CategoryUseCaseTransactionTest`: the delete runs in exactly one `tx.write`.
- `count includes live children and ignores deleted transactions`.
- `reorder appends unlisted rows of the level in their old order`: level [a(0), h(1, hidden), b(2)], call with [b, a] → sortOrder b=0, a=1, h=2.

- [ ] **Step 2: Run, expect FAIL**: `'.\gradlew.bat' testDebugUnitTest --tests "io.github.toanhp123.finote.domain.category.*"`

- [ ] **Step 3: Implement.** Delete, inside one `tx.write`: load `old` (NotFound if null; Ok if already deleted); `checkDelete(old)`; children = `getLevel(old.type, old.id)`, each through `checkDelete`; branchIds = old + children ids; for `MoveTarget.Into`, `checkMoveTarget(old, getCategory(id), branchIds)`; then `deleteAndMove(branchIds, targetIdOrNull, clock.millis())`.

- [ ] **Step 4: Run, expect PASS** (same command).

- [ ] **Step 5: Commit** `git commit -m "feat: delete category branches with a move target and keep new categories last"`

---

### Task 4: Cash entry: hidden parents, keep the edited row's hidden category

**Files:**
- Modify: `app/src/main/java/io/github/toanhp123/finote/ui/cashentry/logic/CashEntryReducers.kt` (`offeredFor`, `stillIn`, `withEditLists`)
- Modify: `.../ui/cashentry/logic/CashEntryDefaults.kt` (`pickCategory`)
- Modify: `.../ui/cashentry/CashEntryContract.kt` (state field)
- Modify: `.../ui/cashentry/CashEntryViewModel.kt:230` and the `filledFrom` path
- Test: `.../ui/cashentry/CashEntryEditViewModelTest.kt`, `.../ui/cashentry/logic/CashEntryDefaultsTest.kt`, `.../ui/cashentry/CashEntrySelectorsTest.kt` if it covers `offeredFor`

**Interfaces:**
- Consumes: `CategoryVisibility` (Task 1).
- Produces:
  - `CashEntryState.keptCategoryId: String? = null`: in edit mode, the row's own `categoryId`; null in create mode.
  - `List<Category>.offeredFor(type: TransactionType, keptId: String? = null): List<Category>`: `shown()` of that type, plus the kept row when it is live and of that type, in `sortOrder`.
  - `CashEntryDefaults.pickCategory(categories, lastUsedId, type, keptId: String? = null): String?`: a live id of that type that is shown, or equal to `keptId`.

- [ ] **Step 1: Write the failing tests**
- Rename and flip `a hidden category shows as none` → `a hidden category stays selected`: the row's hidden `c1` is selected, and saving after a note change keeps `categoryId == "c1"`.
- `a hidden category is offered only to its own transaction`: edit mode lists `c1`; create mode does not.
- `CashEntryDefaultsTest`: `child of a hidden parent is not picked`.
- `a deleted category of the row shows as none` (unchanged behavior, keep or add).

- [ ] **Step 2: Run, expect FAIL**: `'.\gradlew.bat' testDebugUnitTest --tests "io.github.toanhp123.finote.ui.cashentry.*"`

- [ ] **Step 3: Implement.** Set `keptCategoryId` in the edit fill; pass it in every `offeredFor` / `pickCategory` call of the edit path.

- [ ] **Step 4: Run, expect PASS** (same command).

- [ ] **Step 5: Commit** `git commit -m "fix(ui): keep a hidden category when editing its transaction"`

---

### Task 5: Icon set and icon groups

**Files:**
- Create: about 50–65 `app/src/main/res/drawable/ic_<name>.xml` (60–80 icons in total with the current 17), plus `ic_visibility.xml`, `ic_visibility_off.xml`
- Modify: `app/src/main/java/io/github/toanhp123/finote/ui/common/format/SystemResources.kt` (`SystemIcons`; add the existing `ic_local_gas_station`)
- Create: `app/src/main/java/io/github/toanhp123/finote/ui/common/format/CategoryIconGroups.kt`
- Test: `app/src/test/java/io/github/toanhp123/finote/ui/common/format/SystemResourcesTest.kt`

**Interfaces:**
- Produces: `enum class CategoryIconGroup { FOOD, TRANSPORT, HOME, SHOPPING, HEALTH, FUN, STUDY, FAMILY, MONEY, OTHER }`; `val CategoryIconGroups: Map<CategoryIconGroup, List<String>>` (icon names without `ic_`, each name in exactly one group, every seeded icon included).

Source: Google Material Icons, filled ("baseline"), Apache 2.0, tag `4.0.0`: `https://raw.githubusercontent.com/google/material-design-icons/4.0.0/android/<category>/<name>/materialicons/black/res/drawable/baseline_<name>_24.xml`. Keep the 24×24 viewport; set `android:fillColor="#FFFFFFFF"` and drop `android:tint`, like `ic_restaurant.xml`. Vietnamese-life picks the builder should cover: coffee, rice bowl, motorbike (`two_wheeler`), fuel, parking, bus, flight, electricity (`bolt`), water (`water_drop`), wifi, phone, rent (`apartment`), groceries (`local_grocery_store`), clothes (`checkroom`), beauty (`spa`), pharmacy (`local_pharmacy`), gym (`fitness_center`), games (`sports_esports`), travel (`luggage`), pets, kids (`child_care`), gift, charity (`volunteer_activism`), savings (`savings`), interest (`percent`), bank (`account_balance`).

- [ ] **Step 1: Write the failing tests**: `every grouped icon resolves to its own drawable` (no name falls back to `ic_more_horiz`); `every seeded category icon is in a group`; `no icon is in two groups`; `ic_local_gas_station resolves`.
- [ ] **Step 2: Run, expect FAIL**: `'.\gradlew.bat' testDebugUnitTest --tests "*SystemResourcesTest"`
- [ ] **Step 3: Add the drawables, `SystemIcons` entries and groups.** Keep `SystemResources.kt` under 400 lines.
- [ ] **Step 4: Run, expect PASS.** Then `'.\gradlew.bat' assembleDebug` to check the drawables compile.
- [ ] **Step 5: Commit** `git commit -m "feat(ui): add the category icon set"`

---

### Task 6: Category edit: state and ViewModel

**Files:**
- Create: `app/src/main/java/io/github/toanhp123/finote/ui/settings/categoryedit/CategoryEditContract.kt`
- Create: `.../ui/settings/categoryedit/CategoryEditViewModel.kt`
- Create: `.../ui/settings/categoryedit/logic/CategoryDraft.kt`
- Create: `.../ui/settings/categoryedit/logic/ParentOptions.kt`
- Create: `.../ui/settings/categoryedit/logic/DeleteChoice.kt`
- Modify: `app/src/main/java/io/github/toanhp123/finote/ui/navigation/NavKeys.kt`
- Test: `app/src/test/java/io/github/toanhp123/finote/ui/settings/categoryedit/CategoryEditViewModelTest.kt`, `.../categoryedit/logic/CategoryDraftTest.kt`, `.../categoryedit/logic/ParentOptionsTest.kt`

**Interfaces:**
- Consumes: Tasks 1–3 use cases; `ObserveCategoriesUseCase`, `SetCategoryHiddenUseCase`.
- Produces:
  - `@Serializable data class EditCategory(val id: String? = null, val type: CategoryType = CategoryType.EXPENSE, val parentId: String? = null) : NavKey`: `id` null is create; `parentId` presets the parent ("+ Add subcategory").
  - `data class CategoryDraft(val name: String, val parentId: String?, val icon: String, val color: Int)`; `fun CategoryDraft.toCategory(stored: Category?, shownName: String, type: CategoryType): Category`: `name = if (stored != null && name == shownName) stored.name else name.trim()`.
  - `sealed interface DeleteChoice { data object Unset; data object Uncategorized; data class Into(val id: String) }`; `fun DeleteChoice.toMoveTarget(): MoveTarget?` (null for `Unset`).
  - `fun parentOptions(all: List<Category>, editing: Category?, type: CategoryType): List<Category>`: shown top-level categories of `type`, without `editing`, plus `editing.parentId`'s row when hidden.
  - State exposes: `isParentLocked` (system category, or edit of a category with live children), `isHiddenByParent`, `children: List<Category>` (only for a stored top-level category whose draft parent is unchanged), delete flow state (`transactionCount`, `choice`, targets), `canSave`.
  - Intents include save, hide toggle, delete open/confirm, choice picked, add subcategory, children reorder. Effects: `Close`, `OpenEditCategory(key: EditCategory)`, `ActionFailed`.
  - The shown name comes from `Resources.displayName(name, nameKey)` (exists in `SystemResources.kt`), read once when the stored row loads.

- [ ] **Step 1: Write the failing tests**
- Draft: `unchanged shown name sends the stored name` (system row with `nameKey`, shown "Ăn uống", stored "Food" → `name == "Food"`); `edited name is trimmed and sent`.
- ParentOptions: `hidden parents are left out except the current one`; `the category itself is not offered`; `children are not offered as parents`.
- ViewModel:
  - `create from add subcategory presets parent, icon and color of the parent`.
  - `parent is locked for a system category and for a parent with children`.
  - `category gone closes the screen` (row deleted while open → `Close`).
  - `delete with transactions waits for a choice`: count 3 → confirm does nothing while `Unset`; after `Uncategorized` it calls delete with `MoveTarget.Uncategorized`.
  - `delete targets leave out the branch and hidden categories`.
  - `delete without transactions confirms directly` (count 0 → plain confirm calls delete with `Uncategorized`).
  - `a refused save sends ActionFailed and keeps the draft`.
  - `children reorder writes the parent's level` → `ReorderCategoriesUseCase(type, parentId = stored.id, ids)`.
- [ ] **Step 2: Run, expect FAIL**: `'.\gradlew.bat' testDebugUnitTest --tests "io.github.toanhp123.finote.ui.settings.categoryedit.*"`
- [ ] **Step 3: Implement**, mirroring `AccountEditViewModel` (BaseViewModel, `launchCatching`, live updates keep the typed draft).
- [ ] **Step 4: Run, expect PASS.**
- [ ] **Step 5: Commit** `git commit -m "feat(ui): add category edit state"`

---

### Task 7: Category edit: screen, delete sheet, subcategories, navigation

**Files:**
- Create: `.../ui/settings/categoryedit/CategoryEditScreen.kt`
- Create: `.../ui/settings/categoryedit/component/CategoryFormSection.kt` (name, parent row, icon picker, color picker)
- Create: `.../ui/settings/categoryedit/component/CategoryIconPicker.kt` (grouped, `CategoryIconGroups`)
- Create: `.../ui/settings/categoryedit/component/ParentPickerSheet.kt`
- Create: `.../ui/settings/categoryedit/component/SubcategorySection.kt`
- Create: `.../ui/settings/categoryedit/component/DeleteMoveSheet.kt` (mirror `accountedit/component/MergeTargetPickerSheet.kt`)
- Create: `.../ui/settings/categoryedit/component/DeleteCategoryDialog.kt` (zero-transaction confirm, `FinoteDialog`)
- Create: `app/src/main/java/io/github/toanhp123/finote/ui/common/component/finance/CategoryReorderList.kt` (used by Task 7 and Task 9)
- Modify: `.../ui/navigation/FinoteNavHost.kt` (register `EditCategory`)
- Modify: `app/src/main/res/values*/strings.xml` (4 files)

**Interfaces:**
- Consumes: Task 6 state, intents, `EditCategory`; Task 5 groups.
- Produces: `CategoryEditRoute(onClose: () -> Unit, onOpenCategory: (EditCategory) -> Unit)`; `CategoryReorderList(rows: List<Category>, onMove: (from: Int, to: Int) -> Unit, modifier)` with drag handles and accessibility move-up/down actions like `ReorderList.kt`.

Fixed copy (vi / en):
- Sheet title: "Xóa \"%1$s\"?" / "Delete \"%1$s\"?"
- Sheet body (plural): "%1$d giao dịch sẽ chuyển sang danh mục bạn chọn. Không hoàn tác được." / "%1$d transactions will move to the category you pick. This cannot be undone."
- Branch note: "Danh mục con cũng bị xóa." / "Its subcategories are deleted too."
- Option: "Không danh mục" / "No category"
- Hint: "Đang ẩn vì danh mục cha đang ẩn" / "Hidden because its parent is hidden"
- Parent lock reasons: "Danh mục hệ thống không đổi được danh mục cha" / "A system category keeps its parent"; "Danh mục đang có danh mục con" / "This category has subcategories"

- [ ] **Step 1: Build the screen and components**: no Delete button for system categories; the danger button in `DeleteMoveSheet` is disabled while the choice is `Unset`; the `SubcategorySection` shows "+ Add subcategory" and a reorder mode using `CategoryReorderList`; previews for each component, including a 2× font-scale one for the form.
- [ ] **Step 2: Run the architecture and UI tests**: `'.\gradlew.bat' testDebugUnitTest --tests "io.github.toanhp123.finote.architecture.*" --tests "io.github.toanhp123.finote.ui.settings.categoryedit.*"`. Expected: PASS.
- [ ] **Step 3: Build**: `'.\gradlew.bat' assembleDebug`. Expected: BUILD SUCCESSFUL.
- [ ] **Step 4: Commit** `git commit -m "feat(ui): add the category edit screen and delete sheet"`

---

### Task 8: Categories list: tree logic and ViewModel

**Files:**
- Create: `.../ui/settings/categories/CategoriesSettingsContract.kt`
- Create: `.../ui/settings/categories/CategoriesSettingsViewModel.kt`
- Create: `.../ui/settings/categories/logic/CategoryTree.kt`
- Modify: `.../ui/navigation/NavKeys.kt` (`@Serializable data object CategoriesSettings : NavKey`)
- Test: `app/src/test/java/io/github/toanhp123/finote/ui/settings/categories/CategoriesSettingsViewModelTest.kt`, `.../categories/logic/CategoryTreeTest.kt`

**Interfaces:**
- Consumes: `ObserveCategoriesUseCase`, `SetCategoryHiddenUseCase`, `ReorderCategoriesUseCase`; `EditCategory` (Task 6).
- Produces:
  - `data class CategoryNode(val category: Category, val children: List<Category>)`; `data class CategoryTree(val shown: List<CategoryNode>, val hidden: List<CategoryNode>)`; `fun buildTree(all: List<Category>, type: CategoryType): CategoryTree`: top-level by `sortOrder`, children (hidden included) by `sortOrder`; hidden top-level nodes go to `hidden` with all their children.
  - State: `type` (tab), `tree`, `expanded: Set<String>`, `isHiddenExpanded`, `reorder: List<Category>?`. Intents: tab, expand toggle, hidden-section toggle, eye toggle, open, add, reorder start/move/done. Effect: `OpenEditCategory(EditCategory)`, `ActionFailed`.

- [ ] **Step 1: Write the failing tests**
- Tree: `hidden parent goes to the hidden section with its children`; `hidden child stays under its visible parent`; `a tab shows only its type`.
- ViewModel:
  - `add opens create with the tab type` → `EditCategory(id = null, type = INCOME)` on the Income tab.
  - `eye toggles hidden through the use case`.
  - `reorder done writes the top level of the tab` → `ReorderCategoriesUseCase(type, null, ids)` with the visible order.
  - `a failed write sends ActionFailed`.
- [ ] **Step 2: Run, expect FAIL**: `'.\gradlew.bat' testDebugUnitTest --tests "io.github.toanhp123.finote.ui.settings.categories.*"`
- [ ] **Step 3: Implement**, mirroring `AccountsSettingsViewModel`.
- [ ] **Step 4: Run, expect PASS.**
- [ ] **Step 5: Commit** `git commit -m "feat(ui): add categories list state"`

---

### Task 9: Categories list screen and Settings entry

**Files:**
- Create: `.../ui/settings/categories/CategoriesSettingsScreen.kt`
- Create: `.../ui/settings/categories/component/CategoryParentRow.kt` (icon, name, `· n`, eye button, `ExpandChevron`)
- Create: `.../ui/settings/categories/component/CategoryChildRow.kt` (dimmed with eye-off when hidden)
- Create: `.../ui/settings/categories/component/HiddenSection.kt`
- Move: `app/src/main/java/io/github/toanhp123/finote/ui/settings/accounts/component/ExpandChevron.kt` → `ui/common/component/display/ExpandChevron.kt` (now used by two features); update its imports
- Modify: `.../ui/settings/SettingsContract.kt`, `SettingsViewModel.kt`, `SettingsScreen.kt` (new "Danh mục" section under Accounts; `verticalScroll` on the column)
- Modify: `.../ui/navigation/FinoteNavHost.kt` (register `CategoriesSettings`; open helper that ignores a double tap like `openAccountsSettings`)
- Modify: `app/src/main/res/values*/strings.xml` (4 files)
- Test: `app/src/test/java/io/github/toanhp123/finote/ui/settings/SettingsViewModelTest.kt` (if it exists; else add one case to the nearest Settings test)

**Interfaces:**
- Consumes: Task 8 state and intents; `CategoryReorderList` (Task 7).
- Produces: `CategoriesSettingsRoute(onBack: () -> Unit, onOpenCategory: (EditCategory) -> Unit)`; `SettingsEffect.OpenCategoriesSettings`.

- [ ] **Step 1: Write the failing test**: `categories row opens the categories page` (SettingsViewModel sends `OpenCategoriesSettings`).
- [ ] **Step 2: Run, expect FAIL**: `'.\gradlew.bat' testDebugUnitTest --tests "io.github.toanhp123.finote.ui.settings.*"`
- [ ] **Step 3: Build the screen.** `FinoteTopBar` actions: reorder (`ic_swap_vert`) and add (`ic_add`), as `AccountsSettingsScreen`. `FinoteSegmented` tabs Chi / Thu. A category with no child shows no count and no chevron. The empty state never hides `HiddenSection`. Previews include a 2× font-scale one.
- [ ] **Step 4: Run, expect PASS**: the command above plus `--tests "io.github.toanhp123.finote.architecture.*"`; then `'.\gradlew.bat' assembleDebug`.
- [ ] **Step 5: Commit** `git commit -m "feat(ui): add the categories page to settings"`

---

### Task 10: Roadmap, debts and full gate

**Files:**
- Modify: `docs/roadmap.md` (step 15 ✅ with the spec link; section 1 state; remove the N-11 DAO part; add debts below)
- Modify: `docs/superpowers/specs/2026-11-25-category-ui-design.md` (status: implemented)

New debt lines (section 6, "Nợ nhỏ"):
- N-28: `CategoryRepository.deleteAndMove` must also move `CategoryKeywordRule` (step 16) and `Budget` (step 18) rows; step 16 must recheck the open-sheet race (a background booking into a category being deleted moves with the others).
- N-29: create a category from the cash-entry picker (Money Lover style), deferred from step 15.

- [ ] **Step 1: Edit the docs.**
- [ ] **Step 2: Run the gate**: `'.\gradlew.bat' qualityGate`. Expected: `exit_status` 0. Read the summary; fix any detekt or Konsist finding in the task that owns the file.
- [ ] **Step 3: Commit** `git commit -m "docs: close roadmap step 15"`
- [ ] **Step 4:** Hand the branch to `reviewer` (once), then ask the user before the squash into `dev`.
