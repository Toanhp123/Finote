package io.github.toanhp123.finote.ui.settings.capture

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.toanhp123.finote.domain.common.IsDebuggable
import io.github.toanhp123.finote.domain.notification.usecase.CheckRulesNowUseCase
import io.github.toanhp123.finote.domain.notification.usecase.DeleteAdKeywordUseCase
import io.github.toanhp123.finote.domain.notification.usecase.ObserveAdKeywordsUseCase
import io.github.toanhp123.finote.domain.notification.usecase.ObserveParserRulesUseCase
import io.github.toanhp123.finote.domain.notification.usecase.ObserveRuleCheckingUseCase
import io.github.toanhp123.finote.domain.notification.usecase.ObserveRuleUpdateStatusUseCase
import io.github.toanhp123.finote.domain.notification.usecase.SetRuleAutoUpdateUseCase
import io.github.toanhp123.finote.ui.common.BaseViewModel
import io.github.toanhp123.finote.ui.settings.logic.summarizeBanks
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NotificationCaptureSettingsViewModel
    @Suppress("LongParameterList") // why: Hilt injects one collaborator per use case; grouping them hides the deps
    @Inject
    constructor(
        observeStatus: ObserveRuleUpdateStatusUseCase,
        observeRules: ObserveParserRulesUseCase,
        observeChecking: ObserveRuleCheckingUseCase,
        observeKeywords: ObserveAdKeywordsUseCase,
        private val checkRulesNow: CheckRulesNowUseCase,
        private val setAutoUpdate: SetRuleAutoUpdateUseCase,
        private val deleteKeyword: DeleteAdKeywordUseCase,
        @IsDebuggable isDebuggable: Boolean,
    ) : BaseViewModel<
            NotificationCaptureSettingsState,
            NotificationCaptureSettingsIntent,
            NotificationCaptureSettingsEffect,
        >(NotificationCaptureSettingsState(), isDebuggable) {
        init {
            collectInto(observeStatus()) {
                copy(isAutoUpdateEnabled = it.isAutoUpdateEnabled, lastCheck = it.lastCheck)
            }
            collectInto(observeChecking()) { copy(isChecking = it) }
            collectInto(observeRules()) { copy(banks = summarizeBanks(it)) }
            collectInto(observeKeywords()) { copy(adKeywords = it) }
        }

        private fun <T> collectInto(
            flow: Flow<T>,
            reduce: NotificationCaptureSettingsState.(T) -> NotificationCaptureSettingsState,
        ) {
            viewModelScope.launch {
                flow
                    .catchStorage {
                        // The value keeps its last state. Nothing is logged: rules and checks are data.
                    }.collect { value -> setState { reduce(value) } }
            }
        }

        override fun onIntent(intent: NotificationCaptureSettingsIntent) {
            when (intent) {
                NotificationCaptureSettingsIntent.AccessClicked -> {
                    sendEffect(NotificationCaptureSettingsEffect.OpenListenerSettings)
                }

                is NotificationCaptureSettingsIntent.AutoUpdateToggled -> {
                    viewModelScope.launch { setAutoUpdate(intent.isEnabled) }
                }

                NotificationCaptureSettingsIntent.CheckNowClicked -> {
                    if (!state.value.isChecking) checkRulesNow()
                }

                is NotificationCaptureSettingsIntent.AdKeywordDeleteClicked -> {
                    // shortcut: a failed delete leaves the row on screen with no message (this page has no snackbar);
                    // the user taps again. Add a snackbar here if the failure shows up.
                    launchCatching(onStorageError = {}) { deleteKeyword(intent.keywordId) }
                }

                NotificationCaptureSettingsIntent.BackClicked -> {
                    sendEffect(NotificationCaptureSettingsEffect.Close)
                }
            }
        }
    }
