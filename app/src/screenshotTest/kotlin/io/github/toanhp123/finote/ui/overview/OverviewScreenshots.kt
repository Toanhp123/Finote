package io.github.toanhp123.finote.ui.overview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import io.github.toanhp123.finote.domain.category.model.Category
import io.github.toanhp123.finote.domain.category.model.CategoryType
import io.github.toanhp123.finote.domain.report.model.BucketTotal
import io.github.toanhp123.finote.domain.report.model.MerchantTotal
import io.github.toanhp123.finote.domain.report.model.MonthReport
import io.github.toanhp123.finote.domain.report.model.Pace
import io.github.toanhp123.finote.domain.report.model.ReportFilter
import io.github.toanhp123.finote.ui.PHONE
import io.github.toanhp123.finote.ui.TallScreenPreviews
import io.github.toanhp123.finote.ui.common.theme.FinoteTheme
import java.time.YearMonth

private val NOVEMBER: YearMonth = YearMonth.of(2026, 11)
private const val TODAY_DAY = 15
private const val LAST_MONTH_DAYS = 30

@PreviewTest
@TallScreenPreviews
@Composable
private fun OverviewFullPreview() = Frame(populated())

@PreviewTest
@Preview(name = "Light", device = "spec:width=360dp,height=1500dp")
@Composable
private fun OverviewExpandedPreview() = Frame(populated().copy(isBucketListExpanded = true))

// Income only: Spent is zero, this month's line is flat at zero, last month has spending.
@PreviewTest
@Preview(name = "Light", device = PHONE)
@Composable
private fun OverviewIncomeOnlyPreview() =
    Frame(
        populated(
            report =
                report(
                    income = 15_000_000,
                    expense = 0,
                    pace =
                        Pace(
                            percent = -100,
                            thisMonth = cumulative(TODAY_DAY, 0),
                            lastMonth = cumulative(TODAY_DAY, 250_000),
                        ),
                ),
        ),
    )

// Income only, and no spending last month either: a flat line, no sentence, no last-month line.
@PreviewTest
@Preview(name = "Light", device = PHONE)
@Composable
private fun OverviewNoBasisPreview() =
    Frame(
        populated(
            report =
                report(
                    income = 15_000_000,
                    expense = 0,
                    pace = Pace(percent = null, thisMonth = cumulative(TODAY_DAY, 0), lastMonth = emptyList()),
                ),
        ),
    )

@PreviewTest
@Preview(name = "Light", device = PHONE)
@Composable
private fun OverviewNoDataPreview() = Frame(populated().copy(hasTransactions = false, report = null))

@PreviewTest
@Preview(name = "Light", device = PHONE)
@Composable
private fun OverviewLoadingPreview() = Frame(OverviewState(month = NOVEMBER, currentMonth = NOVEMBER))

@PreviewTest
@Preview(name = "Light", device = PHONE)
@Composable
private fun OverviewErrorPreview() = Frame(populated().copy(report = null, hasError = true))

// Refunds above spending: the hero says so in words, a refunded bucket reads +amount in green and has no share.
@PreviewTest
@TallScreenPreviews
@Composable
private fun OverviewRefundPreview() =
    Frame(
        populated(
            report =
                report(
                    income = 1_000_000,
                    expense = -120_000,
                    buckets =
                        listOf(
                            BucketTotal(ReportFilter.ByCategory("sys_food"), 80_000),
                            BucketTotal(ReportFilter.Uncategorized, 30_000),
                            BucketTotal(ReportFilter.ByCategory("sys_shopping"), -230_000),
                        ),
                    merchants = emptyList(),
                    pace = Pace(percent = null, thisMonth = cumulative(TODAY_DAY, -8_000), lastMonth = emptyList()),
                ),
        ),
    )

// A past month: the next-month button is enabled.
@PreviewTest
@Preview(name = "Light", device = PHONE)
@Composable
private fun OverviewPastMonthPreview() =
    Frame(
        populated(report = report(month = NOVEMBER.minusMonths(1))).copy(month = NOVEMBER.minusMonths(1)),
    )

@PreviewTest
@Preview(name = "Light", device = PHONE)
@Composable
private fun OverviewFutureRowsPreview() = Frame(populated(report = report(hasFutureRows = true)))

@Composable
private fun Frame(state: OverviewState) {
    FinoteTheme(dynamicColor = false) {
        Box(Modifier.fillMaxSize().background(FinoteTheme.colors.background)) {
            OverviewScreen(
                state = state,
                onIntent = {},
                modifier = Modifier.statusBarsPadding(),
                contentPadding = PaddingValues(bottom = FinoteTheme.spacing.xxl),
            )
        }
    }
}

private fun populated(report: MonthReport = report()): OverviewState =
    OverviewState(
        isLoading = false,
        month = report.month,
        currentMonth = NOVEMBER,
        report = report,
        categoriesById = categories.associateBy { it.id },
    )

private fun cumulative(
    days: Int,
    perDay: Long,
): List<Long> = List(days) { (it + 1) * perDay }

private fun report(
    month: YearMonth = NOVEMBER,
    income: Long = 15_000_000,
    expense: Long = 8_450_000,
    buckets: List<BucketTotal> = defaultBuckets,
    merchants: List<MerchantTotal> = defaultMerchants,
    pace: Pace =
        Pace(
            percent = 12,
            thisMonth = cumulative(TODAY_DAY, 563_000),
            lastMonth = cumulative(LAST_MONTH_DAYS, 251_000),
        ),
    hasFutureRows: Boolean = false,
) = MonthReport(
    month = month,
    income = income,
    expense = expense,
    net = income - expense,
    buckets = buckets,
    merchants = merchants,
    expenseByCategory = emptyMap(),
    pace = pace,
    hasFutureRows = hasFutureRows,
)

private val defaultBuckets =
    listOf(
        BucketTotal(ReportFilter.ByCategory("sys_food"), 3_200_000),
        BucketTotal(ReportFilter.ByCategory("sys_transport"), 1_800_000),
        BucketTotal(ReportFilter.ByCategory("sys_housing"), 1_500_000),
        BucketTotal(ReportFilter.ByCategory("sys_shopping"), 900_000),
        BucketTotal(ReportFilter.ByCategory("sys_health"), 500_000),
        BucketTotal(ReportFilter.Uncategorized, 350_000),
        BucketTotal(ReportFilter.TransferFees, 200_000),
    )

private val defaultMerchants =
    listOf(
        MerchantTotal("Highlands Coffee", 450_000),
        MerchantTotal("Grab", 380_000),
        MerchantTotal("Circle K", 220_000),
    )

private val categories =
    listOf(
        category("sys_food", "restaurant", 0xFFE53935.toInt()),
        category("sys_transport", "directions_car", 0xFF1E88E5.toInt()),
        category("sys_housing", "home", 0xFF43A047.toInt()),
        category("sys_shopping", "shopping_bag", 0xFF8E24AA.toInt()),
        category("sys_health", "medical_services", 0xFFFB8C00.toInt()),
    )

private fun category(
    key: String,
    icon: String,
    color: Int,
) = Category(
    id = key,
    name = key,
    nameKey = key,
    icon = icon,
    color = color,
    type = CategoryType.EXPENSE,
    parentId = null,
    isSystemDefault = true,
    isHidden = false,
    sortOrder = 0,
    createdAt = 0,
    updatedAt = 0,
    deletedAt = null,
)
