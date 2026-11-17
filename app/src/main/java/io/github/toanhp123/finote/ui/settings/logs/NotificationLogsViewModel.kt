package io.github.toanhp123.finote.ui.settings.logs

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.toanhp123.finote.domain.common.IsDebuggable
import io.github.toanhp123.finote.domain.notification.usecase.ObserveNotificationLogsUseCase
import io.github.toanhp123.finote.domain.notification.usecase.ObserveParserRulesUseCase
import io.github.toanhp123.finote.ui.common.BaseViewModel
import io.github.toanhp123.finote.ui.settings.logic.summarizeBanks
import io.github.toanhp123.finote.ui.settings.logs.logic.corpusSampleJson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NotificationLogsViewModel
    @Inject
    constructor(
        observeLogs: ObserveNotificationLogsUseCase,
        observeRules: ObserveParserRulesUseCase,
        @IsDebuggable isDebuggable: Boolean,
    ) : BaseViewModel<NotificationLogsState, NotificationLogsIntent, NotificationLogsEffect>(
            NotificationLogsState(),
            isDebuggable,
        ) {
        init {
            collectInto(observeLogs()) { logs ->
                // The open sheet follows its log through list changes and closes when the log is gone.
                copy(logs = logs, selected = selected?.let { open -> logs.firstOrNull { it.id == open.id } })
            }
            collectInto(observeRules()) { rules ->
                copy(bankNames = summarizeBanks(rules).associate { it.packageName to it.bankName })
            }
        }

        private fun <T> collectInto(
            flow: Flow<T>,
            reduce: NotificationLogsState.(T) -> NotificationLogsState,
        ) {
            viewModelScope.launch {
                flow
                    .catchStorage {
                        // The value keeps its last state. Nothing is logged: notifications are private.
                    }.collect { value -> setState { reduce(value) } }
            }
        }

        override fun onIntent(intent: NotificationLogsIntent) {
            when (intent) {
                is NotificationLogsIntent.LogClicked -> {
                    val log = state.value.logs.firstOrNull { it.id == intent.id }
                    if (log != null) setState { copy(selected = log) }
                }

                NotificationLogsIntent.SheetDismissed -> {
                    setState { copy(selected = null) }
                }

                NotificationLogsIntent.CopyClicked -> {
                    val current = state.value
                    val log = current.selected?.takeIf { current.isCopyEnabled } ?: return
                    sendEffect(NotificationLogsEffect.CopySample(corpusSampleJson(log)))
                }

                NotificationLogsIntent.BackClicked -> {
                    sendEffect(NotificationLogsEffect.Close)
                }
            }
        }
    }
