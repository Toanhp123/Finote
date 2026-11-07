package io.github.toanhp123.finote.ui.common.component.finance

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import io.github.toanhp123.finote.ui.common.component.preview.FinotePreviewSurface
import io.github.toanhp123.finote.ui.common.component.preview.FinotePreviews
import io.github.toanhp123.finote.ui.common.format.MoneyKind
import io.github.toanhp123.finote.ui.common.format.formatMoney
import io.github.toanhp123.finote.ui.common.theme.FinoteTheme

/**
 * Every amount on screen goes through here. It never wraps and never breaks a number.
 * Income, expense and transfer have a fixed color. Neutral uses the color of [style], or `onSurface` when it has none.
 * With [shrinkToFit] the text gets smaller, down to half of [style], when the width is not enough. It never clips.
 */
@Composable
fun MoneyText(
    amount: Long,
    kind: MoneyKind,
    modifier: Modifier = Modifier,
    style: TextStyle = FinoteTheme.typography.amountSmall,
    shrinkToFit: Boolean = false,
) {
    val locale = LocalConfiguration.current.locales[0]
    val text = remember(amount, kind, locale) { formatMoney(amount, kind, locale) }
    val colors = FinoteTheme.colors
    val color =
        when (kind) {
            MoneyKind.Income -> colors.income.color
            MoneyKind.Expense -> colors.expense.color
            MoneyKind.Transfer -> colors.transfer.color
            MoneyKind.Neutral -> style.color.takeOrElse { FinoteTheme.scheme.onSurface }
        }
    Text(
        text = text,
        modifier = modifier,
        color = color,
        style = style,
        maxLines = 1,
        softWrap = false,
        autoSize =
            if (shrinkToFit) {
                TextAutoSize.StepBased(minFontSize = style.fontSize / 2, maxFontSize = style.fontSize)
            } else {
                null
            },
    )
}

@FinotePreviews
@Composable
private fun MoneyTextPreview() {
    FinotePreviewSurface {
        Column {
            MoneyText(amount = 45_000, kind = MoneyKind.Expense)
            MoneyText(amount = 15_000_000, kind = MoneyKind.Income)
            MoneyText(amount = 2_000_000, kind = MoneyKind.Transfer)
            MoneyText(amount = 12_450_000, kind = MoneyKind.Neutral)
        }
    }
}
