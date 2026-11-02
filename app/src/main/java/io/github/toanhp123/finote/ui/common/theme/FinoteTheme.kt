package io.github.toanhp123.finote.ui.common.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

@Composable
fun FinoteTheme(content: @Composable () -> Unit) {
    val isDark = isSystemInDarkTheme()
    val colorScheme =
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                val context = LocalContext.current
                if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }

            isDark -> {
                darkColorScheme()
            }

            else -> {
                lightColorScheme()
            }
        }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
