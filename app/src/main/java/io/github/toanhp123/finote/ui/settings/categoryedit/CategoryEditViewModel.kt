package io.github.toanhp123.finote.ui.settings.categoryedit

import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.toanhp123.finote.domain.category.error.CategoryError
import io.github.toanhp123.finote.domain.category.model.Category
import io.github.toanhp123.finote.domain.category.service.shown
import io.github.toanhp123.finote.domain.category.usecase.CountCategoryTransactionsUseCase
import io.github.toanhp123.finote.domain.category.usecase.CreateCategoryUseCase
import io.github.toanhp123.finote.domain.category.usecase.DeleteCategoryUseCase
import io.github.toanhp123.finote.domain.category.usecase.ObserveCategoriesUseCase
import io.github.toanhp123.finote.domain.category.usecase.ReorderCategoriesUseCase
import io.github.toanhp123.finote.domain.category.usecase.SetCategoryHiddenUseCase
import io.github.toanhp123.finote.domain.category.usecase.UpdateCategoryUseCase
import io.github.toanhp123.finote.domain.common.IsDebuggable
import io.github.toanhp123.finote.domain.common.Outcome
import io.github.toanhp123.finote.ui.common.BaseViewModel
import io.github.toanhp123.finote.ui.common.theme.color.CategoryPaletteArgb
import io.github.toanhp123.finote.ui.navigation.EditCategory
import io.github.toanhp123.finote.ui.settings.categoryedit.logic.CategoryDraft
import io.github.toanhp123.finote.ui.settings.categoryedit.logic.DeleteChoice
import io.github.toanhp123.finote.ui.settings.categoryedit.logic.DeleteSheet
import io.github.toanhp123.finote.ui.settings.categoryedit.logic.toCategory
import io.github.toanhp123.finote.ui.settings.categoryedit.logic.toDraft
import io.github.toanhp123.finote.ui.settings.categoryedit.logic.toMoveTarget
import kotlinx.coroutines.launch

private const val DEFAULT_ICON = "more_horiz"

/**
 * Create mode ([EditCategory.id] null) edits a draft only. Edit mode fills the draft once from the stored category,
 * then lets the database update [CategoryEditState.stored] and [CategoryEditState.categories] without touching what
 * the user typed.
 */
@HiltViewModel(assistedFactory = CategoryEditViewModel.Factory::class)
class CategoryEditViewModel
    @Suppress("LongParameterList") // why: Hilt injects one collaborator per use case; grouping them would hide the deps
    @AssistedInject
    constructor(
        @Assisted private val key: EditCategory,
        observeCategories: ObserveCategoriesUseCase,
        private val createCategory: CreateCategoryUseCase,
        private val updateCategory: UpdateCategoryUseCase,
        private val deleteCategory: DeleteCategoryUseCase,
        private val countTransactions: CountCategoryTransactionsUseCase,
        private val reorderCategories: ReorderCategoriesUseCase,
        private val setHidden: SetCategoryHiddenUseCase,
        @IsDebuggable isDebuggable: Boolean,
    ) : BaseViewModel<CategoryEditState, CategoryEditIntent, CategoryEditEffect>(
            CategoryEditState(type = key.type),
            isDebuggable,
        ) {
        @AssistedFactory
        interface Factory {
            fun create(key: EditCategory): CategoryEditViewModel
        }

        private var isClosed = false

        init {
            viewModelScope.launch {
                observeCategories()
                    .catchStorage {
                        sendStorageFailed()
                        // Nothing was ever shown, so there is nothing to keep.
                        if (state.value.draft == null) close()
                    }.collect(::onCategories)
            }
        }

        override fun onIntent(intent: CategoryEditIntent) {
            when (intent) {
                is CategoryEditIntent.DraftChanged -> {
                    changeDraft(intent.draft)
                }

                CategoryEditIntent.SaveClicked -> {
                    save()
                }

                CategoryEditIntent.HiddenToggled -> {
                    withStored { act { setHidden(it.id, !it.isHidden) } }
                }

                CategoryEditIntent.AddSubcategoryClicked -> {
                    addSubcategory()
                }

                is CategoryEditIntent.ChildrenReordered -> {
                    withStored { act { reorderCategories(it.type, it.id, intent.ids) } }
                }

                CategoryEditIntent.DeleteClicked -> {
                    openDelete()
                }

                is CategoryEditIntent.DeleteChoicePicked -> {
                    setState { copy(delete = delete?.copy(choice = intent.choice)) }
                }

                CategoryEditIntent.DeleteConfirmed -> {
                    confirmDelete()
                }

                CategoryEditIntent.DeleteDismissed -> {
                    setState { copy(delete = null) }
                }
            }
        }

        private fun onCategories(list: List<Category>) {
            val stored = key.id?.let { id -> list.firstOrNull { it.id == id } }
            if (key.id != null && stored == null) {
                setState { copy(stored = null, delete = null) }
                close()
                return
            }
            val first = if (state.value.draft == null) firstDraft(stored, list) else null
            if (state.value.draft == null && first == null) {
                close()
                return
            }
            // The draft is filled once; later emissions leave what the user typed alone.
            setState { copy(categories = list, stored = stored, type = stored?.type ?: type, draft = draft ?: first) }
        }

        /** Null when a new subcategory has lost its parent: there is nothing to create under. */
        private fun firstDraft(
            stored: Category?,
            list: List<Category>,
        ): CategoryDraft? {
            val parent = key.parentId?.let { id -> list.firstOrNull { it.id == id } }
            return when {
                stored != null -> {
                    stored.toDraft()
                }

                key.parentId != null && parent == null -> {
                    null
                }

                else -> {
                    CategoryDraft(
                        name = "",
                        parentId = key.parentId,
                        icon = parent?.icon ?: DEFAULT_ICON,
                        color = parent?.color ?: CategoryPaletteArgb.first(),
                    )
                }
            }
        }

        /** Runs [block] with the stored category; does nothing in create mode or once the category is gone. */
        private fun withStored(block: (Category) -> Unit) {
            state.value.stored?.let(block)
        }

        private fun changeDraft(next: CategoryDraft) =
            setState {
                copy(draft = draft?.let { if (isParentLocked) next.copy(parentId = it.parentId) else next })
            }

        private fun save() {
            val current = state.value
            val draft = current.draft ?: return
            // An edit whose category is gone has nothing to write.
            if (!current.isSaveEnabled || (key.id != null && current.stored == null)) return
            val category = draft.toCategory(current.stored, current.type)
            launchCatching(onStorageError = ::sendStorageFailed) {
                setState { copy(isSaving = true) }
                try {
                    val isSaved =
                        attempt { if (current.stored == null) createCategory(category) else updateCategory(category) }
                    if (isSaved) close()
                } finally {
                    setState { copy(isSaving = false) }
                }
            }
        }

        private fun addSubcategory() =
            withStored {
                // A child cannot have children.
                if (it.parentId == null) {
                    sendEffect(CategoryEditEffect.OpenEditCategory(EditCategory(type = it.type, parentId = it.id)))
                }
            }

        private fun openDelete() =
            withStored { stored ->
                launchCatching(onStorageError = ::sendStorageFailed) {
                    val count = countTransactions(stored.id)
                    // The category may have gone while the count loaded.
                    if (state.value.stored?.id != stored.id) return@launchCatching
                    val targets =
                        state.value.categories
                            .shown()
                            .filter { it.type == stored.type && it.id != stored.id && it.parentId != stored.id }
                    val choice = if (count == 0) DeleteChoice.Uncategorized else DeleteChoice.Unset
                    setState { copy(delete = DeleteSheet(count, targets, choice)) }
                }
            }

        private fun confirmDelete() =
            withStored { stored ->
                // Nothing picked yet: the sheet stays open.
                val target =
                    state.value.delete
                        ?.choice
                        ?.toMoveTarget() ?: return@withStored
                setState { copy(delete = null) }
                launchCatching(onStorageError = ::sendStorageFailed) {
                    if (attempt { deleteCategory(stored.id, target) }) close()
                }
            }

        /** Runs a write; an `Err` becomes an [CategoryEditEffect.ActionFailed]. True when it succeeded. */
        private suspend fun attempt(block: suspend () -> Outcome<*, CategoryError>): Boolean =
            when (val outcome = block()) {
                is Outcome.Ok -> {
                    true
                }

                is Outcome.Err -> {
                    sendEffect(CategoryEditEffect.ActionFailed(outcome.error))
                    false
                }
            }

        private fun act(block: suspend () -> Outcome<*, CategoryError>) {
            launchCatching(onStorageError = ::sendStorageFailed) { attempt(block) }
        }

        private fun close() {
            // The own write and the category vanishing from the database both ask to close: send once.
            if (isClosed) return
            isClosed = true
            sendEffect(CategoryEditEffect.Close)
        }

        private fun sendStorageFailed() = sendEffect(CategoryEditEffect.ActionFailed(null))
    }
