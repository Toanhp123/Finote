package io.github.toanhp123.finote.ui.settings.capture

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.toanhp123.finote.domain.notification.usecase.CheckRulesNowUseCase
import io.github.toanhp123.finote.domain.notification.usecase.ObserveParserRulesUseCase
import io.github.toanhp123.finote.domain.notification.usecase.ObserveRuleCheckingUseCase
import io.github.toanhp123.finote.domain.notification.usecase.ObserveRuleUpdateStatusUseCase
import io.github.toanhp123.finote.domain.notification.usecase.SetRuleAutoUpdateUseCase
import io.github.toanhp123.finote.ui.common.BaseViewModel
import io.github.toanhp123.finote.ui.settings.summarizeBanks
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NotificationCaptureSettingsViewModel
    @Inject
    constructor(
        observeStatus: ObserveRuleUpdateStatusUseCase,
        observeRules: ObserveParserRulesUseCase,
        observeChecking: ObserveRuleCheckingUseCase,
        private val checkRulesNow: CheckRulesNowUseCase,
        private val setAutoUpdate: SetRuleAutoUpdateUseCase,
    ) : BaseViewModel<
            NotificationCaptureSettingsState,
            NotificationCaptureSettingsIntent,
            NotificationCaptureSettingsEffect,
        >(NotificationCaptureSettingsState()) {
        init {
            collectInto(observeStatus()) {
                copy(isAutoUpdateEnabled = it.isAutoUpdateEnabled, lastCheck = it.lastCheck)
            }
            collectInto(observeChecking()) { copy(isChecking = it) }
            collectInto(observeRules()) { copy(banks = summarizeBanks(it)) }
        }

        private fun <T> collectInto(
            flow: Flow<T>,
            reduce: NotificationCaptureSettingsState.(T) -> NotificationCaptureSettingsState,
        ) {
            viewModelScope.launch {
                flow
                    .catch {
                        // The value keeps its last state. Nothing is logged: rules and checks are data.
                    }.collect { value -> setState { reduce(value) } }
            }
        }

        override fun onIntent(intent: NotificationCaptureSettingsIntent) {
            when (intent) {
                is NotificationCaptureSettingsIntent.Resumed -> {
                    setState { copy(isAccessEnabled = intent.isGranted) }
                }

                NotificationCaptureSettingsIntent.AccessClicked -> {
                    sendEffect(NotificationCaptureSettingsEffect.OpenListenerSettings)
                }

                is NotificationCaptureSettingsIntent.AutoUpdateToggled -> {
                    viewModelScope.launch { setAutoUpdate(intent.isEnabled) }
                }

                NotificationCaptureSettingsIntent.CheckNowClicked -> {
                    if (!state.value.isChecking) checkRulesNow()
                }

                NotificationCaptureSettingsIntent.BackClicked -> {
                    sendEffect(NotificationCaptureSettingsEffect.Close)
                }
            }
        }
    }
