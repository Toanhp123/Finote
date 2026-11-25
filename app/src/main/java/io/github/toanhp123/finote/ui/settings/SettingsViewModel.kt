package io.github.toanhp123.finote.ui.settings

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.toanhp123.finote.domain.common.IsDebuggable
import io.github.toanhp123.finote.domain.common.ToolsEnabled
import io.github.toanhp123.finote.domain.notification.usecase.ObserveInboxAlertsUseCase
import io.github.toanhp123.finote.domain.notification.usecase.ObserveParserRulesUseCase
import io.github.toanhp123.finote.ui.common.BaseViewModel
import io.github.toanhp123.finote.ui.settings.logic.summarizeBanks
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        observeRules: ObserveParserRulesUseCase,
        observeInboxAlerts: ObserveInboxAlertsUseCase,
        @ToolsEnabled isToolsEnabled: Boolean,
        @IsDebuggable isDebuggable: Boolean,
    ) : BaseViewModel<SettingsState, SettingsIntent, SettingsEffect>(
            SettingsState(isToolsEnabled = isToolsEnabled),
            isDebuggable,
        ) {
        init {
            viewModelScope.launch {
                observeRules()
                    .catchStorage {
                        // The count is only a hint on the row; keep the last one. Nothing is logged: rules are data.
                    }.collect { setState { copy(bankCount = summarizeBanks(it).size) } }
            }
            viewModelScope.launch {
                observeInboxAlerts()
                    .catchStorage {
                        // The count and the dot are only hints on the row; keep the last ones. Nothing is logged.
                    }.collect { setState { copy(inboxCount = it.shownCount, hasUnseenInbox = it.hasUnseen) } }
            }
        }

        override fun onIntent(intent: SettingsIntent) {
            when (intent) {
                SettingsIntent.CaptureClicked -> {
                    sendEffect(SettingsEffect.OpenCaptureSettings)
                }

                SettingsIntent.AccountsClicked -> {
                    sendEffect(SettingsEffect.OpenAccountsSettings)
                }

                SettingsIntent.CategoriesClicked -> {
                    sendEffect(SettingsEffect.OpenCategoriesSettings)
                }

                SettingsIntent.InboxClicked -> {
                    sendEffect(SettingsEffect.OpenFailedInbox)
                }

                SettingsIntent.LogsClicked -> {
                    sendEffect(SettingsEffect.OpenNotificationLogs)
                }
            }
        }
    }
