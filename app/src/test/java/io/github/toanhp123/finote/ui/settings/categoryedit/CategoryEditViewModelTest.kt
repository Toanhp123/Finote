package io.github.toanhp123.finote.ui.settings.categoryedit

import app.cash.turbine.test
import io.github.toanhp123.finote.domain.category.error.CategoryError
import io.github.toanhp123.finote.domain.category.model.Category
import io.github.toanhp123.finote.domain.category.model.CategoryType
import io.github.toanhp123.finote.domain.category.testCategory
import io.github.toanhp123.finote.domain.category.usecase.CountCategoryTransactionsUseCase
import io.github.toanhp123.finote.domain.category.usecase.CreateCategoryUseCase
import io.github.toanhp123.finote.domain.category.usecase.DeleteCategoryUseCase
import io.github.toanhp123.finote.domain.category.usecase.FakeCategoryRepository
import io.github.toanhp123.finote.domain.category.usecase.ObserveCategoriesUseCase
import io.github.toanhp123.finote.domain.category.usecase.ReorderCategoriesUseCase
import io.github.toanhp123.finote.domain.category.usecase.SetCategoryHiddenUseCase
import io.github.toanhp123.finote.domain.category.usecase.UpdateCategoryUseCase
import io.github.toanhp123.finote.domain.common.FakeTransactionRunner
import io.github.toanhp123.finote.ui.common.MainDispatcherRule
import io.github.toanhp123.finote.ui.common.theme.color.CategoryPaletteArgb
import io.github.toanhp123.finote.ui.navigation.EditCategory
import io.github.toanhp123.finote.ui.settings.categoryedit.logic.CategoryDraft
import io.github.toanhp123.finote.ui.settings.categoryedit.logic.DeleteChoice
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class CategoryEditViewModelTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    private val clock = Clock.fixed(Instant.ofEpochMilli(5_000L), ZoneOffset.UTC)
    private val repo = FakeCategoryRepository()

    private fun viewModel(
        key: EditCategory,
        vararg categories: Category,
    ): CategoryEditViewModel {
        categories.forEach { repo.stored[it.id] = it }
        val tx = FakeTransactionRunner()
        return CategoryEditViewModel(
            key,
            ObserveCategoriesUseCase(repo),
            CreateCategoryUseCase(repo, tx, clock),
            UpdateCategoryUseCase(repo, tx, clock),
            DeleteCategoryUseCase(repo, tx, clock),
            CountCategoryTransactionsUseCase(repo),
            ReorderCategoriesUseCase(repo, tx, clock),
            SetCategoryHiddenUseCase(repo, tx, clock),
            isDebuggable = true,
        )
    }

    private fun TestScope.loaded(
        key: EditCategory,
        vararg categories: Category,
    ): CategoryEditViewModel = viewModel(key, *categories).also { runCurrent() }

    /** The first effect that [action] causes. */
    private suspend fun TestScope.effectOf(
        vm: CategoryEditViewModel,
        action: () -> Unit,
    ): CategoryEditEffect {
        var effect: CategoryEditEffect? = null
        vm.effects.test {
            action()
            runCurrent()
            effect = awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
        return checkNotNull(effect)
    }

    @Test
    fun `create at top level starts with the default icon and color`() =
        runTest(mainRule.dispatcher) {
            val vm = loaded(EditCategory())

            assertEquals(CategoryDraft("", null, "more_horiz", CategoryPaletteArgb.first()), vm.state.value.draft)
            assertNull(vm.state.value.stored)
        }

    @Test
    fun `create from add subcategory presets parent, icon and color of the parent`() =
        runTest(mainRule.dispatcher) {
            val parent = testCategory(id = "p").copy(icon = "home", color = 0x123456)

            val vm = loaded(EditCategory(parentId = "p"), parent)

            assertEquals(CategoryDraft("", "p", "home", 0x123456), vm.state.value.draft)
        }

    @Test
    fun `create saves the new category and closes the screen`() =
        runTest(mainRule.dispatcher) {
            val vm = loaded(EditCategory(type = CategoryType.INCOME))
            vm.onIntent(CategoryEditIntent.DraftChanged(checkNotNull(vm.state.value.draft).copy(name = " Gym ")))

            val effect = effectOf(vm) { vm.onIntent(CategoryEditIntent.SaveClicked) }

            assertEquals(CategoryEditEffect.Close, effect)
            val created = repo.stored.values.single()
            assertEquals("Gym", created.name)
            assertEquals(CategoryType.INCOME, created.type)
        }

    @Test
    fun `save needs a name`() =
        runTest(mainRule.dispatcher) {
            val vm = loaded(EditCategory())
            assertFalse(vm.state.value.isSaveEnabled)

            vm.onIntent(CategoryEditIntent.DraftChanged(checkNotNull(vm.state.value.draft).copy(name = "Gym")))

            assertTrue(vm.state.value.isSaveEnabled)
        }

    @Test
    fun `parent is locked for a system category and for a parent with children`() =
        runTest(mainRule.dispatcher) {
            val system = testCategory(id = "s").copy(isSystemDefault = true)
            val parent = testCategory(id = "p")
            val child = testCategory(id = "c", parentId = "p")
            val plain = testCategory(id = "x")

            assertTrue(loaded(EditCategory(id = "s"), system).state.value.isParentLocked)
            assertTrue(loaded(EditCategory(id = "p"), parent, child).state.value.isParentLocked)
            assertFalse(loaded(EditCategory(id = "c"), parent, child).state.value.isParentLocked)
            assertFalse(loaded(EditCategory(id = "x"), plain).state.value.isParentLocked)
        }

    @Test
    fun `a locked parent field ignores a draft change`() =
        runTest(mainRule.dispatcher) {
            val vm = loaded(EditCategory(id = "s"), testCategory(id = "s").copy(isSystemDefault = true))

            vm.onIntent(CategoryEditIntent.DraftChanged(checkNotNull(vm.state.value.draft).copy(parentId = "other")))

            assertNull(checkNotNull(vm.state.value.draft).parentId)
        }

    @Test
    fun `an untouched system name is saved as the stored name and keeps its key`() =
        runTest(mainRule.dispatcher) {
            val system = testCategory(id = "s", name = "Food").copy(nameKey = "sys_food", isSystemDefault = true)
            val vm = loaded(EditCategory(id = "s"), system)
            assertEquals("Food", checkNotNull(vm.state.value.draft).name)

            vm.onIntent(CategoryEditIntent.DraftChanged(checkNotNull(vm.state.value.draft).copy(icon = "home")))
            vm.onIntent(CategoryEditIntent.SaveClicked)
            runCurrent()

            val saved = repo.stored.getValue("s")
            assertEquals("Food", saved.name)
            assertEquals("sys_food", saved.nameKey)
            assertEquals("home", saved.icon)
        }

    @Test
    fun `an edited name is saved trimmed`() =
        runTest(mainRule.dispatcher) {
            val system = testCategory(id = "s", name = "Food").copy(nameKey = "sys_food", isSystemDefault = true)
            val vm = loaded(EditCategory(id = "s"), system)

            vm.onIntent(CategoryEditIntent.DraftChanged(checkNotNull(vm.state.value.draft).copy(name = " Cafe ")))
            vm.onIntent(CategoryEditIntent.SaveClicked)
            runCurrent()

            assertEquals("Cafe", repo.stored.getValue("s").name)
        }

    @Test
    fun `a live update keeps the typed draft`() =
        runTest(mainRule.dispatcher) {
            val vm = loaded(EditCategory(id = "a"), testCategory(id = "a", name = "Food"))
            vm.onIntent(CategoryEditIntent.DraftChanged(checkNotNull(vm.state.value.draft).copy(name = "Typed")))

            repo.stored["a"] = repo.stored.getValue("a").copy(isHidden = true)
            repo.notifyChanged()
            runCurrent()

            assertEquals("Typed", checkNotNull(vm.state.value.draft).name)
            assertTrue(checkNotNull(vm.state.value.stored).isHidden)
        }

    @Test
    fun `category gone closes the screen`() =
        runTest(mainRule.dispatcher) {
            val vm = loaded(EditCategory(id = "a"), testCategory(id = "a"))

            val effect =
                effectOf(vm) {
                    repo.stored["a"] = repo.stored.getValue("a").copy(deletedAt = 1L)
                    repo.notifyChanged()
                }

            assertEquals(CategoryEditEffect.Close, effect)
            assertNull(vm.state.value.stored)
        }

    @Test
    fun `an unknown id closes the screen`() =
        runTest(mainRule.dispatcher) {
            val vm = viewModel(EditCategory(id = "missing"))

            assertEquals(CategoryEditEffect.Close, effectOf(vm) {})
        }

    @Test
    fun `a child of a hidden parent is marked hidden by its parent`() =
        runTest(mainRule.dispatcher) {
            val parent = testCategory(id = "p").copy(isHidden = true)
            val child = testCategory(id = "c", parentId = "p")

            assertTrue(loaded(EditCategory(id = "c"), parent, child).state.value.isHiddenByParent)
            assertFalse(loaded(EditCategory(id = "p"), parent, child).state.value.isHiddenByParent)
        }

    @Test
    fun `hide toggle flips the stored flag`() =
        runTest(mainRule.dispatcher) {
            val vm = loaded(EditCategory(id = "a"), testCategory(id = "a"))

            vm.onIntent(CategoryEditIntent.HiddenToggled)
            runCurrent()

            assertTrue(repo.stored.getValue("a").isHidden)
            assertTrue(checkNotNull(vm.state.value.stored).isHidden)
        }

    @Test
    fun `children show only for a top level category whose parent is unchanged`() =
        runTest(mainRule.dispatcher) {
            val parent = testCategory(id = "p")
            val child = testCategory(id = "c1", parentId = "p")
            val other = testCategory(id = "other")

            val vm = loaded(EditCategory(id = "p"), parent, child, other)
            assertEquals(
                listOf("c1"),
                vm.state.value.children
                    .map { it.id },
            )

            vm.onIntent(CategoryEditIntent.DraftChanged(checkNotNull(vm.state.value.draft).copy(parentId = "other")))
            // The parent has live children, so the field stays locked and the section stays.
            assertEquals(
                listOf("c1"),
                vm.state.value.children
                    .map { it.id },
            )

            assertTrue(
                loaded(EditCategory(id = "c1"))
                    .state.value.children
                    .isEmpty(),
            )
            assertTrue(
                loaded(EditCategory(id = "other"))
                    .state.value.children
                    .isEmpty(),
            )
        }

    @Test
    fun `children hide while the draft parent differs from the stored one`() =
        runTest(mainRule.dispatcher) {
            // No children: the parent field is free, so the draft can point elsewhere.
            val vm = loaded(EditCategory(id = "x"), testCategory(id = "x"), testCategory(id = "y"))
            assertTrue(
                vm.state.value.children
                    .isEmpty(),
            )

            vm.onIntent(CategoryEditIntent.DraftChanged(checkNotNull(vm.state.value.draft).copy(parentId = "y")))

            assertTrue(
                vm.state.value.children
                    .isEmpty(),
            )
        }

    @Test
    fun `children reorder writes the parent's level`() =
        runTest(mainRule.dispatcher) {
            val vm =
                loaded(
                    EditCategory(id = "p"),
                    testCategory(id = "p"),
                    testCategory(id = "c1", parentId = "p").copy(sortOrder = 0),
                    testCategory(id = "c2", parentId = "p").copy(sortOrder = 1),
                    testCategory(id = "c3", parentId = "p").copy(sortOrder = 2),
                )

            vm.onIntent(CategoryEditIntent.ChildrenReordered(listOf("c3", "c1", "c2")))
            runCurrent()

            assertEquals(0, repo.stored.getValue("c3").sortOrder)
            assertEquals(1, repo.stored.getValue("c1").sortOrder)
            assertEquals(2, repo.stored.getValue("c2").sortOrder)
            assertEquals(
                listOf("c3", "c1", "c2"),
                vm.state.value.children
                    .map { it.id },
            )
        }

    @Test
    fun `add subcategory opens create with this category as parent`() =
        runTest(mainRule.dispatcher) {
            val vm = loaded(EditCategory(id = "p"), testCategory(id = "p", type = CategoryType.INCOME))

            val effect = effectOf(vm) { vm.onIntent(CategoryEditIntent.AddSubcategoryClicked) }

            assertEquals(
                CategoryEditEffect.OpenEditCategory(EditCategory(type = CategoryType.INCOME, parentId = "p")),
                effect,
            )
        }

    @Test
    fun `delete with transactions waits for a choice`() =
        runTest(mainRule.dispatcher) {
            val vm = loaded(EditCategory(id = "a"), testCategory(id = "a"))
            repeat(3) { repo.transactionCategory["t$it"] = "a" }

            vm.onIntent(CategoryEditIntent.DeleteClicked)
            runCurrent()
            assertEquals(3, checkNotNull(vm.state.value.delete).transactionCount)
            assertEquals(DeleteChoice.Unset, checkNotNull(vm.state.value.delete).choice)

            vm.onIntent(CategoryEditIntent.DeleteConfirmed)
            runCurrent()
            assertNull(repo.stored.getValue("a").deletedAt)
            assertNotNull(vm.state.value.delete)

            vm.onIntent(CategoryEditIntent.DeleteChoicePicked(DeleteChoice.Uncategorized))
            val effect = effectOf(vm) { vm.onIntent(CategoryEditIntent.DeleteConfirmed) }

            assertEquals(CategoryEditEffect.Close, effect)
            assertNotNull(repo.stored.getValue("a").deletedAt)
            assertEquals(setOf<String?>(null), repo.transactionCategory.values.toSet())
        }

    @Test
    fun `delete moves the transactions into the picked category`() =
        runTest(mainRule.dispatcher) {
            val vm = loaded(EditCategory(id = "a"), testCategory(id = "a"), testCategory(id = "b"))
            repo.transactionCategory["t"] = "a"

            vm.onIntent(CategoryEditIntent.DeleteClicked)
            runCurrent()
            vm.onIntent(CategoryEditIntent.DeleteChoicePicked(DeleteChoice.Into("b")))
            vm.onIntent(CategoryEditIntent.DeleteConfirmed)
            runCurrent()

            assertEquals("b", repo.transactionCategory["t"])
        }

    @Test
    fun `delete without transactions confirms directly`() =
        runTest(mainRule.dispatcher) {
            val vm = loaded(EditCategory(id = "a"), testCategory(id = "a"))

            vm.onIntent(CategoryEditIntent.DeleteClicked)
            runCurrent()
            assertEquals(0, checkNotNull(vm.state.value.delete).transactionCount)
            val effect = effectOf(vm) { vm.onIntent(CategoryEditIntent.DeleteConfirmed) }

            assertEquals(CategoryEditEffect.Close, effect)
            assertNotNull(repo.stored.getValue("a").deletedAt)
        }

    @Test
    fun `delete targets leave out the branch and hidden categories`() =
        runTest(mainRule.dispatcher) {
            val vm =
                loaded(
                    EditCategory(id = "a"),
                    testCategory(id = "a"),
                    testCategory(id = "a1", parentId = "a"),
                    testCategory(id = "b"),
                    testCategory(id = "b1", parentId = "b"),
                    testCategory(id = "h").copy(isHidden = true),
                    testCategory(id = "h1", parentId = "h"),
                    testCategory(id = "i", type = CategoryType.INCOME),
                )

            vm.onIntent(CategoryEditIntent.DeleteClicked)
            runCurrent()

            assertEquals(listOf("b", "b1"), checkNotNull(vm.state.value.delete).targets.map { it.id })
        }

    @Test
    fun `a refused save sends ActionFailed and keeps the draft`() =
        runTest(mainRule.dispatcher) {
            val vm = loaded(EditCategory(id = "a"), testCategory(id = "a"))
            vm.onIntent(CategoryEditIntent.DraftChanged(checkNotNull(vm.state.value.draft).copy(parentId = "ghost")))

            val effect = effectOf(vm) { vm.onIntent(CategoryEditIntent.SaveClicked) }

            assertEquals(CategoryEditEffect.ActionFailed(CategoryError.InvalidParent), effect)
            assertEquals("ghost", checkNotNull(vm.state.value.draft).parentId)
            assertFalse(vm.state.value.isSaving)
        }
}
