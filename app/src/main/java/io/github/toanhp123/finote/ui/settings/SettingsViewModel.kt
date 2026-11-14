package io.github.toanhp123.finote.ui.settings

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.toanhp123.finote.domain.common.IsDebuggable
import io.github.toanhp123.finote.domain.notification.usecase.ObserveParserRulesUseCase
import io.github.toanhp123.finote.ui.common.BaseViewModel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        observeRules: ObserveParserRulesUseCase,
        @IsDebuggable isDebuggable: Boolean,
    ) : BaseViewModel<SettingsState, SettingsIntent, SettingsEffect>(SettingsState(), isDebuggable) {
        init {
            viewModelScope.launch {
                observeRules()
                    .catch {
                        // The count is only a hint on the row; keep the last one. Nothing is logged: rules are data.
                    }.collect { setState { copy(bankCount = summarizeBanks(it).size) } }
            }
        }

        override fun onIntent(intent: SettingsIntent) {
            when (intent) {
                is SettingsIntent.Resumed -> {
                    setState { copy(isAccessEnabled = intent.isGranted) }
                }

                SettingsIntent.CaptureClicked -> {
                    sendEffect(SettingsEffect.OpenCaptureSettings)
                }

                SettingsIntent.AccountsClicked -> {
                    sendEffect(SettingsEffect.OpenAccountsSettings)
                }
            }
        }
    }
