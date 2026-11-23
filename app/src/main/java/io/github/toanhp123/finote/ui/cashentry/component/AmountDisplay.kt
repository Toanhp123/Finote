package io.github.toanhp123.finote.ui.cashentry.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import io.github.toanhp123.finote.R
import io.github.toanhp123.finote.ui.cashentry.logic.AmountResult
import io.github.toanhp123.finote.ui.cashentry.logic.InvalidReason
import io.github.toanhp123.finote.ui.cashentry.logic.valueOrNull
import io.github.toanhp123.finote.ui.common.component.finance.MoneyText
import io.github.toanhp123.finote.ui.common.format.MoneyKind
import io.github.toanhp123.finote.ui.common.theme.FinoteTheme

/**
 * The typed [expression], its [result] as a big amount, and one error line, right-aligned above a keypad. Only an
 * invalid result or a failed save ([hasSaveError]) shows text; a result that is not positive is just unfinished.
 */
@Composable
fun AmountDisplay(
    expression: String,
    result: AmountResult,
    kind: MoneyKind,
    modifier: Modifier = Modifier,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    hasSaveError: Boolean = false,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = verticalArrangement,
        horizontalAlignment = Alignment.End,
    ) {
        Text(
            text = expression,
            color = FinoteTheme.scheme.onSurfaceVariant,
            style = FinoteTheme.typography.material.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.StartEllipsis,
            textAlign = TextAlign.End,
            modifier = Modifier.fillMaxWidth(),
            minLines = 1,
        )
        MoneyText(
            amount = result.valueOrNull() ?: 0L,
            kind = kind,
            style = FinoteTheme.typography.amountLarge,
            shrinkToFit = true,
        )
        // Always one line tall, so the keypad does not jump when an error appears.
        Text(
            text = errorText(result, hasSaveError),
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            color = FinoteTheme.colors.expense.color,
            style = FinoteTheme.typography.material.bodySmall,
            minLines = 1,
        )
    }
}

@Composable
private fun errorText(
    result: AmountResult,
    hasSaveError: Boolean,
): String {
    val res =
        when {
            result is AmountResult.Invalid && result.reason == InvalidReason.DIVIDE_BY_ZERO -> {
                R.string.cash_entry_error_divide_by_zero
            }

            result is AmountResult.Invalid -> {
                R.string.cash_entry_error_overflow
            }

            hasSaveError -> {
                R.string.cash_entry_error_save
            }

            else -> {
                return ""
            }
        }
    return stringResource(res)
}
