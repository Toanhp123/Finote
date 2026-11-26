package io.github.toanhp123.finote.ui.common.component.display

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.Zoom
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModel
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.compose.cartesian.data.LineCartesianLayerModel
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoZoomState
import com.patrykandpatrick.vico.compose.common.Fill
import io.github.toanhp123.finote.ui.common.component.preview.FinotePreviewSurface
import io.github.toanhp123.finote.ui.common.component.preview.FinotePreviews
import io.github.toanhp123.finote.ui.common.theme.FinoteTheme

/** Which theme color a chart line takes. */
enum class ChartLine { Primary, Muted }

/** One line. [points] holds one value per day; index 0 is day 1. */
data class ChartSeries(
    val points: List<Long>,
    val line: ChartLine,
) {
    // The points are spending amounts: they must never reach a log or crash report.
    override fun toString() = "ChartSeries(size=${points.size}, line=$line)"
}

/**
 * Line chart over days 1..[dayCount] with no axes and no labels, so no amount is ever drawn here.
 * A series shorter than [dayCount] stops where it ends. Not interactive, no animation.
 */
@Composable
fun FinoteLineChart(
    series: List<ChartSeries>,
    dayCount: Int,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val sizes = FinoteTheme.sizes
    val primary = rememberChartLine(FinoteTheme.scheme.primary, sizes.chartStroke)
    val muted = rememberChartLine(FinoteTheme.scheme.onSurfaceVariant, sizes.chartStroke)
    // Vico rejects an empty series, so those are dropped before the model is built.
    val drawn = remember(series) { series.filter { it.points.isNotEmpty() } }
    // The floor is zero, or lower when refunds pull a cumulative line below it, so such a line is not drawn off-chart.
    val floorY = remember(drawn) { minOf(0L, drawn.minOfOrNull { it.points.min() } ?: 0L).toDouble() }
    val model =
        remember(drawn) {
            drawn.takeIf { it.isNotEmpty() }?.let {
                CartesianChartModel(
                    LineCartesianLayerModel.build {
                        it.forEach { s -> this.series(x = s.points.indices.map { i -> i + 1 }, y = s.points) }
                    },
                )
            }
        }
    val chart =
        rememberCartesianChart(
            rememberLineCartesianLayer(
                lineProvider =
                    LineCartesianLayer.LineProvider.series(
                        drawn.map { if (it.line == ChartLine.Primary) primary else muted },
                    ),
                rangeProvider =
                    remember(dayCount, floorY) {
                        CartesianLayerRangeProvider.fixed(minX = 1.0, maxX = dayCount.toDouble(), minY = floorY)
                    },
            ),
        )
    val boxModifier =
        modifier
            .fillMaxWidth()
            .height(sizes.chartHeight)
            .semantics { this.contentDescription = contentDescription }
    if (model == null) {
        Box(boxModifier)
    } else {
        CartesianChartHost(
            chart = chart,
            model = model,
            modifier = boxModifier,
            scrollState = rememberVicoScrollState(scrollEnabled = false),
            zoomState = rememberVicoZoomState(zoomEnabled = false, initialZoom = Zoom.Content),
        )
    }
}

@Composable
private fun rememberChartLine(
    color: Color,
    stroke: Dp,
) = LineCartesianLayer.rememberLine(
    fill = remember(color) { LineCartesianLayer.LineFill.single(Fill(color)) },
    stroke = remember(stroke) { LineCartesianLayer.LineStroke.Continuous(thickness = stroke) },
)

private const val PREVIEW_LONG_DAYS = 30
private const val PREVIEW_SHORT_DAYS = 18
private const val PREVIEW_DAY_COUNT = 31
private const val PREVIEW_DAILY_SPEND = 45_000L

@FinotePreviews
@Composable
private fun FinoteLineChartPreview() {
    FinotePreviewSurface {
        FinoteLineChart(
            series =
                listOf(
                    ChartSeries(cumulative(PREVIEW_LONG_DAYS), ChartLine.Primary),
                    ChartSeries(cumulative(PREVIEW_SHORT_DAYS), ChartLine.Muted),
                ),
            dayCount = PREVIEW_DAY_COUNT,
            contentDescription = "Spending this month against last month",
        )
    }
}

private fun cumulative(days: Int): List<Long> = List(days) { (it + 1) * PREVIEW_DAILY_SPEND }
