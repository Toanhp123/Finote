package io.github.toanhp123.finote.ui.report

import io.github.toanhp123.finote.domain.account.model.Account
import io.github.toanhp123.finote.domain.category.model.Category
import io.github.toanhp123.finote.domain.report.model.ReportFilter
import io.github.toanhp123.finote.domain.report.model.ReportRow
import java.time.YearMonth

/**
 * The rows behind [filter] in [month], newest first, and their [total] in VND (negative for a net refund). The
 * screen takes the title from [filter] and [categoriesById]. A row opens only when [openableAccountIds] holds every
 * account it touches: an archived account's row stays read-only.
 */
data class ReportListState(
    val isLoading: Boolean = true,
    val hasError: Boolean = false,
    val month: YearMonth,
    val filter: ReportFilter,
    val rows: List<ReportRow> = emptyList(),
    val total: Long = 0,
    val accountsById: Map<String, Account> = emptyMap(),
    val categoriesById: Map<String, Category> = emptyMap(),
    val openableAccountIds: Set<String> = emptySet(),
) {
    // The total is money: it must never reach a log or crash report.
    override fun toString() = "ReportListState(month=$month, filter=$filter, rows=${rows.size})"
}

sealed interface ReportListIntent {
    data class RowClicked(
        val id: String,
    ) : ReportListIntent
}

sealed interface ReportListEffect {
    data class OpenEditor(
        val id: String,
    ) : ReportListEffect
}
