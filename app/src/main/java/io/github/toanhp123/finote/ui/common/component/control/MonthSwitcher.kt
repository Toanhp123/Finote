package io.github.toanhp123.finote.ui.common.component.control

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import io.github.toanhp123.finote.R
import io.github.toanhp123.finote.ui.common.component.preview.FinotePreviewSurface
import io.github.toanhp123.finote.ui.common.component.preview.FinotePreviews
import io.github.toanhp123.finote.ui.common.format.formatMonthYear
import io.github.toanhp123.finote.ui.common.theme.FinoteTheme
import java.time.Month
import java.time.YearMonth

private const val PREVIEW_YEAR = 2026

/** A month title between a previous and a next button. [isNextEnabled] is false at the newest month. */
@Composable
fun MonthSwitcher(
    month: YearMonth,
    isNextEnabled: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = LocalConfiguration.current.locales[0]
    val title = remember(month, locale) { formatMonthYear(month, locale) }
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = FinoteTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FinoteIconButton(
            iconRes = R.drawable.ic_chevron_left,
            contentDescription = stringResource(R.string.overview_month_prev_cd),
            onClick = onPrevious,
        )
        Text(
            text = title,
            modifier = Modifier.weight(1f).semantics { heading() },
            color = FinoteTheme.scheme.onSurface,
            style = FinoteTheme.typography.material.titleMedium,
            textAlign = TextAlign.Center,
        )
        FinoteIconButton(
            iconRes = R.drawable.ic_chevron_right,
            contentDescription = stringResource(R.string.overview_month_next_cd),
            onClick = onNext,
            enabled = isNextEnabled,
        )
    }
}

@FinotePreviews
@Composable
private fun MonthSwitcherPreview() {
    FinotePreviewSurface {
        MonthSwitcher(
            month = YearMonth.of(PREVIEW_YEAR, Month.NOVEMBER),
            isNextEnabled = false,
            onPrevious = {},
            onNext = {},
        )
    }
}
