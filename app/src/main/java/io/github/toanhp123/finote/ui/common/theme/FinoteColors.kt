package io.github.toanhp123.finote.ui.common.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color

data class FinoteColors(
    val income: MoneyColorRole,
    val expense: MoneyColorRole,
    val transfer: MoneyColorRole,
    val hero: HeroColors,
    val glass: GlassColors,
    val background: Color,
    val card: Color,
    val disabledAlpha: Float = 0.38f,
)

/** Hero, glass, background and card follow [scheme], so they change with Dynamic Color. */
internal fun finoteColors(
    scheme: ColorScheme,
    isDark: Boolean,
): FinoteColors =
    if (isDark) {
        FinoteColors(
            income = DarkIncome,
            expense = DarkExpense,
            transfer = DarkTransfer,
            hero = HeroColors(scheme.primaryContainer, scheme.tertiaryContainer, scheme.onPrimaryContainer),
            glass =
                GlassColors(
                    fill = scheme.surfaceContainerHigh.copy(alpha = 0.70f),
                    solidFill = scheme.surfaceContainerHigh,
                    stroke = scheme.onSurface.copy(alpha = 0.08f),
                ),
            background = scheme.surface,
            card = scheme.surfaceContainer,
        )
    } else {
        FinoteColors(
            income = LightIncome,
            expense = LightExpense,
            transfer = LightTransfer,
            hero = HeroColors(scheme.primary, scheme.tertiary, scheme.onPrimary),
            glass =
                GlassColors(
                    fill = scheme.surfaceContainerLowest.copy(alpha = 0.75f),
                    solidFill = scheme.surfaceContainerLowest,
                    stroke = scheme.onSurface.copy(alpha = 0.06f),
                ),
            background = scheme.surfaceContainer,
            card = scheme.surfaceContainerLowest,
        )
    }
