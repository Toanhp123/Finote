package io.github.toanhp123.finote.ui.overview

import io.github.toanhp123.finote.domain.category.model.Category
import io.github.toanhp123.finote.domain.report.model.MonthReport
import io.github.toanhp123.finote.domain.report.model.ReportFilter
import java.time.YearMonth

/**
 * [month] is the month shown; [report] belongs to it (null while loading or after an error). [currentMonth] is the
 * ViewModel clock's month: the screen never reads a clock, and the next-month button stops there.
 * [hasTransactions] is false only when the user has no live row at all.
 */
data class OverviewState(
    val isLoading: Boolean = true,
    val hasError: Boolean = false,
    val month: YearMonth,
    val currentMonth: YearMonth,
    val hasTransactions: Boolean = true,
    val report: MonthReport? = null,
    val categoriesById: Map<String, Category> = emptyMap(),
    val isBucketListExpanded: Boolean = false,
)

sealed interface OverviewIntent {
    /** The screen came to the front; the month may have changed while it was away. */
    data object Resumed : OverviewIntent

    data object PreviousMonth : OverviewIntent

    data object NextMonth : OverviewIntent

    data object ToggleBuckets : OverviewIntent

    data class BucketClicked(
        val filter: ReportFilter,
    ) : OverviewIntent

    data class MerchantClicked(
        val name: String,
    ) : OverviewIntent {
        // A merchant name must never reach a log or crash report.
        override fun toString() = "MerchantClicked"
    }
}

sealed interface OverviewEffect {
    data class OpenReport(
        val month: YearMonth,
        val filter: ReportFilter,
    ) : OverviewEffect
}
