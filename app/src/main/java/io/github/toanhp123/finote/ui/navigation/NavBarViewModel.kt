package io.github.toanhp123.finote.ui.navigation

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.toanhp123.finote.domain.common.IsDebuggable
import io.github.toanhp123.finote.domain.notification.usecase.ObserveInboxAlertsUseCase
import io.github.toanhp123.finote.ui.common.BaseViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * State of the bottom bar. It lives as long as the host, not as long as a tab, so the dot shows on every tab.
 * The bar takes no intent and sends no effect.
 */
@HiltViewModel
class NavBarViewModel
    @Inject
    constructor(
        observeAlerts: ObserveInboxAlertsUseCase,
        @IsDebuggable isDebuggable: Boolean,
    ) : BaseViewModel<NavBarState, Nothing, Nothing>(NavBarState(), isDebuggable) {
        init {
            viewModelScope.launch {
                observeAlerts()
                    .catchStorage {
                        // The dot is only a hint; keep the last one. Nothing is logged: the inbox holds notifications.
                    }.collect { alerts -> setState { copy(hasSettingsBadge = alerts.hasUnseen) } }
            }
        }

        override fun onIntent(intent: Nothing) = Unit
    }
