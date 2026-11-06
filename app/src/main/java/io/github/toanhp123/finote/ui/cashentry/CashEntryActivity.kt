package io.github.toanhp123.finote.ui.cashentry

import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import dagger.hilt.android.AndroidEntryPoint
import io.github.toanhp123.finote.ui.common.theme.FinoteTheme

// Translucent host so the sheet floats over whatever app the user was in.
@AndroidEntryPoint
class CashEntryActivity : AppCompatActivity() {
    private val viewModel: CashEntryViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // The sheet animates itself, so the window close animation would only slow the exit. Before API 34 the
        // theme's null windowAnimationStyle is all there is: overridePendingTransition is deprecated (warnings fail).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
        }
        setContent {
            FinoteTheme {
                CashEntryRoute(viewModel, onClose = ::finish)
            }
        }
    }
}
