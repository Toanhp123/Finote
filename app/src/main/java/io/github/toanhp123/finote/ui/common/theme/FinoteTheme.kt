package io.github.toanhp123.finote.ui.common.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

// Constant tokens have defaults. Only the ones that follow the color scheme are provided.
private val LocalFinoteColors = staticCompositionLocalOf<FinoteColors> { error("FinoteTheme is missing") }
private val LocalFinoteBrushes = staticCompositionLocalOf<FinoteBrushes> { error("FinoteTheme is missing") }
private val LocalIsDark = staticCompositionLocalOf { false }
private val LocalFinoteTypography = staticCompositionLocalOf { FinoteTypography() }
private val LocalFinoteSpacing = staticCompositionLocalOf { FinoteSpacing() }
private val LocalFinoteSizes = staticCompositionLocalOf { FinoteSizes() }
private val LocalFinoteRadii = staticCompositionLocalOf { FinoteRadii() }
private val LocalFinoteShapes = staticCompositionLocalOf { finoteShapes(FinoteRadii()) }
private val LocalFinoteMotion = staticCompositionLocalOf { FinoteMotion() }

@Composable
fun FinoteTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val scheme =
        when {
            dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                val context = LocalContext.current
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }

            darkTheme -> {
                DarkBrandScheme
            }

            else -> {
                LightBrandScheme
            }
        }
    val colors = remember(scheme, darkTheme) { finoteColors(scheme, darkTheme) }
    val brushes = remember(colors) { finoteBrushes(colors) }
    CompositionLocalProvider(
        LocalFinoteColors provides colors,
        LocalFinoteBrushes provides brushes,
        LocalIsDark provides darkTheme,
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = LocalFinoteTypography.current.material,
            content = content,
        )
    }
}

object FinoteTheme {
    val scheme: ColorScheme
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.colorScheme
    val colors: FinoteColors
        @Composable @ReadOnlyComposable
        get() = LocalFinoteColors.current
    val typography: FinoteTypography
        @Composable @ReadOnlyComposable
        get() = LocalFinoteTypography.current
    val spacing: FinoteSpacing
        @Composable @ReadOnlyComposable
        get() = LocalFinoteSpacing.current
    val sizes: FinoteSizes
        @Composable @ReadOnlyComposable
        get() = LocalFinoteSizes.current
    val radii: FinoteRadii
        @Composable @ReadOnlyComposable
        get() = LocalFinoteRadii.current
    val shapes: FinoteShapes
        @Composable @ReadOnlyComposable
        get() = LocalFinoteShapes.current
    val motion: FinoteMotion
        @Composable @ReadOnlyComposable
        get() = LocalFinoteMotion.current
    val brushes: FinoteBrushes
        @Composable @ReadOnlyComposable
        get() = LocalFinoteBrushes.current
    val isDark: Boolean
        @Composable @ReadOnlyComposable
        get() = LocalIsDark.current
}
