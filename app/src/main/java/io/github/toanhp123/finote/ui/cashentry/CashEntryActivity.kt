package io.github.toanhp123.finote.ui.cashentry

import android.content.Context
import android.content.Intent
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // singleTask reuses this instance, and its ViewModel keeps the first id: another mode needs a fresh instance.
        val current = this.intent.getStringExtra(EXTRA_TRANSACTION_ID)
        if (shouldRestartFor(current, intent.getStringExtra(EXTRA_TRANSACTION_ID))) {
            finish()
            startActivity(intent)
        }
    }

    companion object {
        /** The id of the transaction to edit. Absent: the sheet adds a new one. */
        const val EXTRA_TRANSACTION_ID = "transaction_id"

        /** Another row or mode needs a fresh ViewModel; the same one keeps what the user typed. */
        internal fun shouldRestartFor(
            current: String?,
            incoming: String?,
        ): Boolean = current != incoming

        fun editIntent(
            context: Context,
            id: String,
        ): Intent = Intent(context, CashEntryActivity::class.java).putExtra(EXTRA_TRANSACTION_ID, id)
    }
}
