package io.github.toanhp123.finote.ui.settings.keywordrules

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.toanhp123.finote.domain.category.usecase.ObserveCategoriesUseCase
import io.github.toanhp123.finote.domain.category.usecase.ObserveKeywordRulesUseCase
import io.github.toanhp123.finote.domain.category.usecase.SetSystemRuleActiveUseCase
import io.github.toanhp123.finote.domain.common.IsDebuggable
import io.github.toanhp123.finote.ui.common.BaseViewModel
import io.github.toanhp123.finote.ui.navigation.EditKeywordRule
import io.github.toanhp123.finote.ui.settings.keywordrules.logic.KeywordRuleItem
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class KeywordRulesViewModel
    @Inject
    constructor(
        observeRules: ObserveKeywordRulesUseCase,
        observeCategories: ObserveCategoriesUseCase,
        private val setSystemActive: SetSystemRuleActiveUseCase,
        @IsDebuggable isDebuggable: Boolean,
    ) : BaseViewModel<KeywordRulesState, KeywordRulesIntent, KeywordRulesEffect>(KeywordRulesState(), isDebuggable) {
        init {
            viewModelScope.launch {
                combine(observeRules(), observeCategories()) { rules, categories ->
                    val byId = categories.associateBy { it.id }
                    rules.map { KeywordRuleItem(it, byId[it.categoryId]) }.partition { it.rule.isUserDefined }
                }.catchStorage {
                    // The list keeps its last state.
                }.collect { (user, system) ->
                    setState {
                        copy(
                            user = user.sortedByDescending { it.rule.createdAt },
                            system = system.sortedBy { it.rule.normalizedKeyword },
                        )
                    }
                }
            }
        }

        override fun onIntent(intent: KeywordRulesIntent) {
            when (intent) {
                KeywordRulesIntent.AddClicked -> {
                    sendEffect(KeywordRulesEffect.OpenEditRule(EditKeywordRule()))
                }

                is KeywordRulesIntent.RuleClicked -> {
                    sendEffect(KeywordRulesEffect.OpenEditRule(EditKeywordRule(intent.id)))
                }

                is KeywordRulesIntent.SystemRuleToggled -> {
                    launchCatching(onStorageError = { sendEffect(KeywordRulesEffect.ActionFailed) }) {
                        setSystemActive(intent.id, intent.isActive)
                    }
                }
            }
        }
    }
