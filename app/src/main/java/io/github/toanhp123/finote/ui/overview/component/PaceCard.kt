package io.github.toanhp123.finote.ui.overview.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import io.github.toanhp123.finote.R
import io.github.toanhp123.finote.domain.report.model.Pace
import io.github.toanhp123.finote.ui.common.component.display.ChartLine
import io.github.toanhp123.finote.ui.common.component.display.ChartSeries
import io.github.toanhp123.finote.ui.common.component.display.FinoteCard
import io.github.toanhp123.finote.ui.common.component.display.FinoteLineChart
import io.github.toanhp123.finote.ui.common.theme.FinoteTheme
import kotlin.math.abs

/**
 * The pace against the same days of last month: a sentence with the percentage, a cumulative line chart and a legend
 * that names each line, so the lines differ by more than color. A null percentage has no basis, so it shows neither
 * the sentence nor last month's line (nor its legend entry).
 */
@Composable
fun PaceCard(
    pace: Pace,
    dayCount: Int,
    hasFutureRows: Boolean,
    modifier: Modifier = Modifier,
) {
    val isLastDrawn = pace.percent != null && pace.lastMonth.isNotEmpty()
    FinoteCard(modifier = modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(FinoteTheme.spacing.sm)) {
            if (pace.percent != null) PaceText(pace.percent)
            FinoteLineChart(
                series =
                    listOfNotNull(
                        ChartSeries(pace.thisMonth, ChartLine.Primary),
                        ChartSeries(pace.lastMonth, ChartLine.Muted).takeIf { isLastDrawn },
                    ),
                dayCount = dayCount,
                contentDescription = stringResource(R.string.overview_pace_chart_cd),
            )
            // The same colors as the chart lines, see FinoteLineChart.
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(FinoteTheme.spacing.lg),
                verticalArrangement = Arrangement.spacedBy(FinoteTheme.spacing.xs),
            ) {
                LegendEntry(FinoteTheme.scheme.primary, stringResource(R.string.overview_pace_legend_this))
                if (isLastDrawn) {
                    LegendEntry(FinoteTheme.scheme.onSurfaceVariant, stringResource(R.string.overview_pace_legend_last))
                }
            }
            if (hasFutureRows) {
                Text(
                    text = stringResource(R.string.overview_pace_future_note),
                    color = FinoteTheme.scheme.onSurfaceVariant,
                    style = FinoteTheme.typography.material.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun LegendEntry(
    swatch: Color,
    label: String,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(FinoteTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(FinoteTheme.sizes.legendSwatch)
                .height(FinoteTheme.sizes.chartStroke)
                .clip(FinoteTheme.shapes.full)
                .background(swatch),
        )
        Text(
            text = label,
            color = FinoteTheme.scheme.onSurfaceVariant,
            style = FinoteTheme.typography.material.labelMedium,
        )
    }
}

// More spending is the expense color, less is the income color; the sign in the words says the same.
@Composable
private fun PaceText(percent: Int) {
    val colors = FinoteTheme.colors
    val (text, color) =
        when {
            percent > 0 -> stringResource(R.string.overview_pace_up, percent) to colors.expense.color
            percent < 0 -> stringResource(R.string.overview_pace_down, abs(percent)) to colors.income.color
            else -> stringResource(R.string.overview_pace_flat) to FinoteTheme.scheme.onSurfaceVariant
        }
    Text(text = text, color = color, style = FinoteTheme.typography.material.titleSmall)
}
