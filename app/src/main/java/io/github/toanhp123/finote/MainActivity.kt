package io.github.toanhp123.finote

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import dagger.hilt.android.AndroidEntryPoint
import io.github.toanhp123.finote.ui.common.theme.FinoteTheme
import io.github.toanhp123.finote.ui.navigation.FinoteNavHost

// AppCompatActivity: per-app language below API 33 and FragmentActivity for BiometricPrompt.
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FinoteTheme {
                FinoteNavHost()
            }
        }
    }
}
