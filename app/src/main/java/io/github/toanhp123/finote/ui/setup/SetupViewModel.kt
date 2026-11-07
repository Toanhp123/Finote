package io.github.toanhp123.finote.ui.setup

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.toanhp123.finote.domain.setup.usecase.CompleteSetupUseCase
import io.github.toanhp123.finote.ui.common.BaseViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

/** How long "Granted" stays on screen before setup finishes. */
const val GRANTED_HOLD_MS = 800L

@HiltViewModel
class SetupViewModel
    @Inject
    constructor(
        private val completeSetup: CompleteSetupUseCase,
    ) : BaseViewModel<SetupState, SetupIntent, SetupEffect>(SetupState()) {
        override fun onIntent(intent: SetupIntent) {
            when (intent) {
                is SetupIntent.Resumed -> {
                    onResumed(intent.isGranted)
                }

                SetupIntent.EnableClicked -> {
                    setState { copy(hasLaunchedSettings = true) }
                    sendEffect(SetupEffect.OpenListenerSettings)
                }

                SetupIntent.LaterClicked -> {
                    completeSetup()
                    sendEffect(SetupEffect.Finished)
                }
            }
        }

        private fun onResumed(isGranted: Boolean) {
            // Once granted, the hold is running; a second resume must not finish twice.
            if (state.value.step == SetupStep.GRANTED) return
            if (isGranted) {
                setState { copy(step = SetupStep.GRANTED) }
                completeSetup()
                viewModelScope.launch {
                    delay(GRANTED_HOLD_MS)
                    sendEffect(SetupEffect.Finished)
                }
            } else if (state.value.hasLaunchedSettings) {
                setState { copy(step = SetupStep.RETRY) }
            }
        }
    }
