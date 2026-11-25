package io.github.toanhp123.finote.ui.settings.categories

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.toanhp123.finote.domain.category.model.Category
import io.github.toanhp123.finote.domain.category.usecase.ObserveCategoriesUseCase
import io.github.toanhp123.finote.domain.category.usecase.ReorderCategoriesUseCase
import io.github.toanhp123.finote.domain.category.usecase.SetCategoryHiddenUseCase
import io.github.toanhp123.finote.domain.common.IsDebuggable
import io.github.toanhp123.finote.domain.common.Outcome
import io.github.toanhp123.finote.ui.common.BaseViewModel
import io.github.toanhp123.finote.ui.navigation.EditCategory
import io.github.toanhp123.finote.ui.settings.categories.logic.buildTree
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CategoriesSettingsViewModel
    @Inject
    constructor(
        observeCategories: ObserveCategoriesUseCase,
        private val setHidden: SetCategoryHiddenUseCase,
        private val reorderCategories: ReorderCategoriesUseCase,
        @IsDebuggable isDebuggable: Boolean,
    ) : BaseViewModel<CategoriesSettingsState, CategoriesSettingsIntent, CategoriesSettingsEffect>(
            CategoriesSettingsState(),
            isDebuggable,
        ) {
        // All live categories of both tabs, so a tab switch rebuilds the tree without a new read.
        private var all: List<Category> = emptyList()

        init {
            viewModelScope.launch {
                observeCategories()
                    .catchStorage {
                        // The list keeps its last state.
                    }.collect { loaded ->
                        all = loaded
                        setState { copy(tree = buildTree(loaded, type)) }
                    }
            }
        }

        override fun onIntent(intent: CategoriesSettingsIntent) {
            when (intent) {
                is CategoriesSettingsIntent.TabSelected -> {
                    if (intent.type != state.value.type) {
                        setState { copy(type = intent.type, tree = buildTree(all, intent.type), reorder = null) }
                    }
                }

                is CategoriesSettingsIntent.ExpandToggled -> {
                    setState { copy(expanded = expanded.toggled(intent.id)) }
                }

                CategoriesSettingsIntent.HiddenSectionToggled -> {
                    setState { copy(isHiddenExpanded = !isHiddenExpanded) }
                }

                is CategoriesSettingsIntent.VisibilityToggled -> {
                    toggleHidden(intent.id)
                }

                is CategoriesSettingsIntent.CategoryClicked -> {
                    sendEffect(CategoriesSettingsEffect.OpenEditCategory(EditCategory(intent.id, state.value.type)))
                }

                CategoriesSettingsIntent.AddClicked -> {
                    sendEffect(CategoriesSettingsEffect.OpenEditCategory(EditCategory(type = state.value.type)))
                }

                CategoriesSettingsIntent.ReorderClicked -> {
                    setState { copy(reorder = tree.shown.map { it.category }) }
                }

                is CategoriesSettingsIntent.ReorderMoved -> {
                    setState { copy(reorder = reorder?.moved(intent.from, intent.to)) }
                }

                CategoriesSettingsIntent.ReorderDone -> {
                    finishReorder()
                }

                CategoriesSettingsIntent.ReorderCancelled -> {
                    setState { copy(reorder = null) }
                }
            }
        }

        private fun toggleHidden(id: String) {
            val category = all.firstOrNull { it.id == id } ?: return
            launchCatching(onStorageError = ::sendActionFailed) {
                if (setHidden(id, !category.isHidden) is Outcome.Err) sendActionFailed()
            }
        }

        private fun finishReorder() {
            val draft = state.value.reorder ?: return
            val type = state.value.type
            // Leaves the mode first, so a second tap on Done finds nothing to write.
            setState { copy(reorder = null) }
            // The use case appends the hidden rows of the level itself.
            launchCatching(onStorageError = ::sendActionFailed) {
                if (reorderCategories(type, null, draft.map { it.id }) is Outcome.Err) sendActionFailed()
            }
        }

        private fun sendActionFailed() = sendEffect(CategoriesSettingsEffect.ActionFailed)
    }

private fun Set<String>.toggled(value: String) = if (value in this) this - value else this + value

private fun List<Category>.moved(
    from: Int,
    to: Int,
): List<Category> = if (from !in indices || to !in indices) this else toMutableList().apply { add(to, removeAt(from)) }
