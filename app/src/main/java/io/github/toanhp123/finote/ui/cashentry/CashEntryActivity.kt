package io.github.toanhp123.finote.ui.cashentry

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
        setContent {
            FinoteTheme {
                CashEntryRoute(viewModel, onClose = ::finish)
            }
        }
    }
}
