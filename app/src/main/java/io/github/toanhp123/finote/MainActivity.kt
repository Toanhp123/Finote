package io.github.toanhp123.finote

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import dagger.hilt.android.AndroidEntryPoint
import io.github.toanhp123.finote.automation.FinoteNotificationListener
import io.github.toanhp123.finote.domain.common.ToolsEnabled
import io.github.toanhp123.finote.domain.notification.port.SettleScheduler
import io.github.toanhp123.finote.domain.setup.repository.SetupRepository
import io.github.toanhp123.finote.ui.common.isNotificationListenerEnabled
import io.github.toanhp123.finote.ui.common.notification.EXTRA_OPEN_TAB
import io.github.toanhp123.finote.ui.common.notification.OPEN_TAB_BUDGET
import io.github.toanhp123.finote.ui.common.theme.FinoteTheme
import io.github.toanhp123.finote.ui.navigation.FinoteNavHost
import javax.inject.Inject

// AppCompatActivity: per-app language below API 33 and FragmentActivity for BiometricPrompt.
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    @Inject
    lateinit var setupRepository: SetupRepository

    @Inject
    lateinit var settleScheduler: SettleScheduler

    @Inject
    @field:ToolsEnabled
    @JvmField
    var isToolsEnabled: Boolean = false

    // Bumped for each tap on a budget alert; the nav host reacts to the change.
    private var openTabRequest by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // A recreated activity must not jump to the tab again.
        if (savedInstanceState == null) consumeOpenTab(intent)
        settleScheduler.settleNow()
        if (isNotificationListenerEnabled(this)) FinoteNotificationListener.rebindIfDisconnected(this)
        setContent {
            FinoteTheme {
                FinoteNavHost(
                    shouldStartAtSetup = !setupRepository.isSetupDone(),
                    isToolsEnabled = isToolsEnabled,
                    openTabRequest = openTabRequest,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        consumeOpenTab(intent)
    }

    private fun consumeOpenTab(intent: Intent) {
        if (intent.getStringExtra(EXTRA_OPEN_TAB) == OPEN_TAB_BUDGET) openTabRequest++
        intent.removeExtra(EXTRA_OPEN_TAB)
    }
}
