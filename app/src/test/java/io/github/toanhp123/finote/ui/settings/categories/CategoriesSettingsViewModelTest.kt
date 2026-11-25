package io.github.toanhp123.finote.ui.settings.categories

import app.cash.turbine.test
import io.github.toanhp123.finote.domain.category.model.Category
import io.github.toanhp123.finote.domain.category.model.CategoryType
import io.github.toanhp123.finote.domain.category.repository.CategoryRepository
import io.github.toanhp123.finote.domain.category.testCategory
import io.github.toanhp123.finote.domain.category.usecase.FakeCategoryRepository
import io.github.toanhp123.finote.domain.category.usecase.ObserveCategoriesUseCase
import io.github.toanhp123.finote.domain.category.usecase.ReorderCategoriesUseCase
import io.github.toanhp123.finote.domain.category.usecase.SetCategoryHiddenUseCase
import io.github.toanhp123.finote.domain.common.FakeTransactionRunner
import io.github.toanhp123.finote.domain.common.StorageException
import io.github.toanhp123.finote.ui.common.MainDispatcherRule
import io.github.toanhp123.finote.ui.navigation.EditCategory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class CategoriesSettingsViewModelTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    private val clock = Clock.fixed(Instant.ofEpochMilli(5_000L), ZoneOffset.UTC)
    private val categories = FakeCategoryRepository()

    private class FailingWritesRepository(
        fake: FakeCategoryRepository,
    ) : CategoryRepository by fake {
        override suspend fun setHidden(
            id: String,
            hidden: Boolean,
            updatedAt: Long,
        ): Unit = throw StorageException(IllegalStateException("disk"))

        override suspend fun reorder(
            orderedIds: List<String>,
            now: Long,
        ): Unit = throw StorageException(IllegalStateException("disk"))
    }

    private fun cat(
        id: String,
        parentId: String? = null,
        type: CategoryType = CategoryType.EXPENSE,
        sortOrder: Int = 0,
        isHidden: Boolean = false,
    ): Category =
        testCategory(id = id, type = type, parentId = parentId).copy(sortOrder = sortOrder, isHidden = isHidden)

    private fun TestScope.viewModel(
        vararg seeded: Category,
        repo: CategoryRepository = categories,
    ): CategoriesSettingsViewModel {
        seeded.forEach { categories.stored[it.id] = it }
        val vm =
            CategoriesSettingsViewModel(
                ObserveCategoriesUseCase(repo),
                SetCategoryHiddenUseCase(repo, FakeTransactionRunner(), clock),
                ReorderCategoriesUseCase(repo, FakeTransactionRunner(), clock),
                isDebuggable = true,
            )
        runCurrent()
        return vm
    }

    private fun CategoriesSettingsViewModel.shownIds() =
        state.value.tree.shown
            .map { it.category.id }

    private fun CategoriesSettingsViewModel.hiddenIds() =
        state.value.tree.hidden
            .map { it.category.id }

    @Test
    fun `the expense tab loads first and the income tab switches the tree`() =
        runTest(mainRule.dispatcher) {
            val vm = viewModel(cat("food"), cat("salary", type = CategoryType.INCOME))
            assertEquals(CategoryType.EXPENSE, vm.state.value.type)
            assertEquals(listOf("food"), vm.shownIds())

            vm.onIntent(CategoriesSettingsIntent.TabSelected(CategoryType.INCOME))

            assertEquals(CategoryType.INCOME, vm.state.value.type)
            assertEquals(listOf("salary"), vm.shownIds())
        }

    @Test
    fun `a category row and the hidden section start collapsed and toggle`() =
        runTest(mainRule.dispatcher) {
            val vm = viewModel(cat("food"))
            assertTrue(
                vm.state.value.expanded
                    .isEmpty(),
            )
            assertFalse(vm.state.value.isHiddenExpanded)

            vm.onIntent(CategoriesSettingsIntent.ExpandToggled("food"))
            vm.onIntent(CategoriesSettingsIntent.HiddenSectionToggled)
            assertEquals(setOf("food"), vm.state.value.expanded)
            assertTrue(vm.state.value.isHiddenExpanded)

            vm.onIntent(CategoriesSettingsIntent.ExpandToggled("food"))
            assertTrue(
                vm.state.value.expanded
                    .isEmpty(),
            )
        }

    @Test
    fun `add opens create with the tab type`() =
        runTest(mainRule.dispatcher) {
            val vm = viewModel()
            vm.onIntent(CategoriesSettingsIntent.TabSelected(CategoryType.INCOME))

            vm.effects.test {
                vm.onIntent(CategoriesSettingsIntent.AddClicked)
                assertEquals(
                    CategoriesSettingsEffect.OpenEditCategory(EditCategory(id = null, type = CategoryType.INCOME)),
                    awaitItem(),
                )
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a click opens the edit screen of that category`() =
        runTest(mainRule.dispatcher) {
            val vm = viewModel(cat("food"))

            vm.effects.test {
                vm.onIntent(CategoriesSettingsIntent.CategoryClicked("food"))
                assertEquals(
                    CategoriesSettingsEffect.OpenEditCategory(EditCategory(id = "food", type = CategoryType.EXPENSE)),
                    awaitItem(),
                )
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `eye toggles hidden through the use case`() =
        runTest(mainRule.dispatcher) {
            val vm = viewModel(cat("food"), cat("home", sortOrder = 1))

            vm.onIntent(CategoriesSettingsIntent.VisibilityToggled("home"))
            runCurrent()

            assertTrue(categories.stored.getValue("home").isHidden)
            assertEquals(listOf("food"), vm.shownIds())
            assertEquals(listOf("home"), vm.hiddenIds())

            vm.onIntent(CategoriesSettingsIntent.VisibilityToggled("home"))
            runCurrent()

            assertFalse(categories.stored.getValue("home").isHidden)
            assertEquals(listOf("food", "home"), vm.shownIds())
        }

    @Test
    fun `reorder done writes the top level of the tab`() =
        runTest(mainRule.dispatcher) {
            val vm =
                viewModel(
                    cat("a", sortOrder = 0),
                    cat("b", sortOrder = 1),
                    cat("hid", sortOrder = 2, isHidden = true),
                    cat("c", sortOrder = 3),
                    cat("a1", parentId = "a"),
                    cat("inc", type = CategoryType.INCOME),
                )

            vm.onIntent(CategoriesSettingsIntent.ReorderClicked)
            assertEquals(
                listOf("a", "b", "c"),
                vm.state.value.reorder
                    ?.map { it.id },
            )
            vm.onIntent(CategoriesSettingsIntent.ReorderMoved(from = 2, to = 0))
            vm.onIntent(CategoriesSettingsIntent.ReorderDone)
            runCurrent()

            assertNull(vm.state.value.reorder)
            // Visible rows in the new order, then the hidden one; the income level is untouched.
            assertEquals(listOf("c", "a", "b"), vm.shownIds())
            assertEquals(3, categories.stored.getValue("hid").sortOrder)
            assertEquals(0, categories.stored.getValue("inc").sortOrder)
        }

    @Test
    fun `reorder cancel and a move outside the draft write nothing`() =
        runTest(mainRule.dispatcher) {
            val vm = viewModel(cat("a"), cat("b", sortOrder = 1))
            val writes = categories.writes

            vm.onIntent(CategoriesSettingsIntent.ReorderClicked)
            vm.onIntent(CategoriesSettingsIntent.ReorderMoved(from = 0, to = 5))
            assertEquals(
                listOf("a", "b"),
                vm.state.value.reorder
                    ?.map { it.id },
            )
            vm.onIntent(CategoriesSettingsIntent.ReorderMoved(from = 0, to = 1))
            assertEquals(
                listOf("b", "a"),
                vm.state.value.reorder
                    ?.map { it.id },
            )
            vm.onIntent(CategoriesSettingsIntent.ReorderCancelled)
            runCurrent()

            assertNull(vm.state.value.reorder)
            assertEquals(listOf("a", "b"), vm.shownIds())
            assertEquals(writes, categories.writes)
        }

    @Test
    fun `switching tabs while reordering cancels the reorder`() =
        runTest(mainRule.dispatcher) {
            val vm = viewModel(cat("a"), cat("salary", type = CategoryType.INCOME))
            vm.onIntent(CategoriesSettingsIntent.ReorderClicked)

            vm.onIntent(CategoriesSettingsIntent.TabSelected(CategoryType.INCOME))

            assertNull(vm.state.value.reorder)
        }

    @Test
    fun `a failed write sends ActionFailed`() =
        runTest(mainRule.dispatcher) {
            val vm = viewModel(cat("a"), cat("b", sortOrder = 1), repo = FailingWritesRepository(categories))

            vm.effects.test {
                vm.onIntent(CategoriesSettingsIntent.VisibilityToggled("a"))
                assertEquals(CategoriesSettingsEffect.ActionFailed, awaitItem())

                vm.onIntent(CategoriesSettingsIntent.ReorderClicked)
                vm.onIntent(CategoriesSettingsIntent.ReorderDone)
                assertEquals(CategoriesSettingsEffect.ActionFailed, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }
}
