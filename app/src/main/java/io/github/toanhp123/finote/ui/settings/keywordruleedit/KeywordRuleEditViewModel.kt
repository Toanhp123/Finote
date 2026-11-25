package io.github.toanhp123.finote.ui.settings.keywordruleedit

import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.toanhp123.finote.domain.category.model.Category
import io.github.toanhp123.finote.domain.category.model.KeywordRule
import io.github.toanhp123.finote.domain.category.usecase.ApplyRuleToPastUseCase
import io.github.toanhp123.finote.domain.category.usecase.CountRuleMatchesUseCase
import io.github.toanhp123.finote.domain.category.usecase.DeleteKeywordRuleUseCase
import io.github.toanhp123.finote.domain.category.usecase.ObserveCategoriesUseCase
import io.github.toanhp123.finote.domain.category.usecase.ObserveKeywordRulesUseCase
import io.github.toanhp123.finote.domain.category.usecase.SaveKeywordRuleUseCase
import io.github.toanhp123.finote.domain.common.IsDebuggable
import io.github.toanhp123.finote.domain.common.Outcome
import io.github.toanhp123.finote.ui.common.BaseViewModel
import io.github.toanhp123.finote.ui.navigation.EditKeywordRule
import io.github.toanhp123.finote.ui.settings.keywordruleedit.logic.KeywordRuleDraft
import io.github.toanhp123.finote.ui.settings.keywordruleedit.logic.pickerCategories
import io.github.toanhp123.finote.ui.settings.keywordruleedit.logic.toDraft
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Create mode ([EditKeywordRule.id] null) edits a blank draft. Edit mode fills the draft once from the stored user
 * rule, then lets the database update [KeywordRuleEditState.stored] without touching what the user typed. An id that
 * is unknown or a system rule closes the screen.
 */
@HiltViewModel(assistedFactory = KeywordRuleEditViewModel.Factory::class)
class KeywordRuleEditViewModel
    @Suppress("LongParameterList") // why: Hilt injects one collaborator per use case; grouping them would hide the deps
    @AssistedInject
    constructor(
        @Assisted private val key: EditKeywordRule,
        observeRules: ObserveKeywordRulesUseCase,
        observeCategories: ObserveCategoriesUseCase,
        private val saveRule: SaveKeywordRuleUseCase,
        private val deleteRule: DeleteKeywordRuleUseCase,
        private val countMatches: CountRuleMatchesUseCase,
        private val applyRule: ApplyRuleToPastUseCase,
        @IsDebuggable isDebuggable: Boolean,
    ) : BaseViewModel<KeywordRuleEditState, KeywordRuleEditIntent, KeywordRuleEditEffect>(
            KeywordRuleEditState(),
            isDebuggable,
        ) {
        @AssistedFactory
        interface Factory {
            fun create(key: EditKeywordRule): KeywordRuleEditViewModel
        }

        private var isClosed = false

        // The rule the bulk apply dialog asks about; the dialog's answer needs it, the state only holds the count.
        private var savedRule: KeywordRule? = null

        init {
            viewModelScope.launch {
                combine(observeRules(), observeCategories()) { rules, categories -> rules to categories }
                    .catchStorage {
                        sendEffect(KeywordRuleEditEffect.ActionFailed)
                        // Nothing was ever shown, so there is nothing to keep.
                        if (state.value.draft == null) close()
                    }.collect { (rules, categories) -> onLoaded(rules, categories) }
            }
        }

        override fun onIntent(intent: KeywordRuleEditIntent) {
            when (intent) {
                is KeywordRuleEditIntent.DraftChanged -> {
                    setState { copy(draft = intent.draft, error = null) }
                }

                KeywordRuleEditIntent.SaveClicked -> {
                    save()
                }

                KeywordRuleEditIntent.DeleteClicked -> {
                    if (state.value.stored != null) setState { copy(isDeleteOpen = true) }
                }

                KeywordRuleEditIntent.DeleteConfirmed -> {
                    confirmDelete()
                }

                KeywordRuleEditIntent.DeleteDismissed -> {
                    setState { copy(isDeleteOpen = false) }
                }

                KeywordRuleEditIntent.BulkApplyConfirmed -> {
                    applyToPast()
                }

                KeywordRuleEditIntent.BulkApplyDismissed -> {
                    close()
                }
            }
        }

        private fun onLoaded(
            rules: List<KeywordRule>,
            categories: List<Category>,
        ) {
            val stored = key.id?.let { id -> rules.firstOrNull { it.id == id } }
            if (key.id != null && stored?.isUserDefined != true) {
                setState { copy(stored = null, isDeleteOpen = false) }
                close()
                return
            }
            // The draft is filled once; later emissions leave what the user typed alone.
            setState {
                copy(
                    stored = stored,
                    categories = pickerCategories(categories, stored?.categoryId),
                    draft = draft ?: stored?.toDraft() ?: KeywordRuleDraft(),
                )
            }
        }

        private fun save() {
            val current = state.value
            val draft = current.draft
            val categoryId = draft?.categoryId
            if (draft == null || categoryId == null || !current.isSaveEnabled) return
            launchCatching(onStorageError = ::sendStorageFailed) {
                setState { copy(isSaving = true, error = null) }
                try {
                    when (val outcome = saveRule(key.id, draft.keyword, draft.merchantName, categoryId)) {
                        is Outcome.Err -> setState { copy(error = outcome.error) }
                        is Outcome.Ok -> afterSave(outcome.value, current.stored)
                    }
                } finally {
                    setState { copy(isSaving = false) }
                }
            }
        }

        /** A new rule, a new keyword or a new category may match past rows: ask. A short name alone cannot. */
        private suspend fun afterSave(
            rule: KeywordRule,
            old: KeywordRule?,
        ) {
            val isMatchChanged =
                old == null || old.normalizedKeyword != rule.normalizedKeyword || old.categoryId != rule.categoryId
            val count = if (isMatchChanged) countMatches(rule) else 0
            if (count > 0) {
                savedRule = rule
                setState { copy(bulkApplyCount = count) }
            } else {
                close()
            }
        }

        private fun applyToPast() {
            // Taken at once, so a second tap on Apply finds nothing to write.
            val rule = savedRule ?: return
            savedRule = null
            setState { copy(bulkApplyCount = null) }
            launchCatching(onStorageError = ::sendStorageFailed) {
                applyRule(rule)
                close()
            }
        }

        private fun confirmDelete() {
            val id = state.value.stored?.id ?: return
            setState { copy(isDeleteOpen = false) }
            launchCatching(onStorageError = ::sendStorageFailed) {
                deleteRule(id)
                close()
            }
        }

        private fun close() {
            // The own write and the rule vanishing from the database both ask to close: send once.
            if (isClosed) return
            isClosed = true
            sendEffect(KeywordRuleEditEffect.Close)
        }

        private fun sendStorageFailed() = sendEffect(KeywordRuleEditEffect.ActionFailed)
    }
