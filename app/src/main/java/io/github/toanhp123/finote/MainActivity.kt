package io.github.toanhp123.finote

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import dagger.hilt.android.AndroidEntryPoint
import io.github.toanhp123.finote.domain.notification.port.SettleScheduler
import io.github.toanhp123.finote.domain.setup.repository.SetupRepository
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        settleScheduler.settleNow()
        setContent {
            FinoteTheme {
                FinoteNavHost(shouldStartAtSetup = !setupRepository.isSetupDone())
            }
        }
    }
}
