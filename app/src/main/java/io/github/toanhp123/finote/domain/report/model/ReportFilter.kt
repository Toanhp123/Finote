package io.github.toanhp123.finote.domain.report.model

/** Which slice of a month's spending a report row list shows. Named apart from the `Category` domain model. */
sealed interface ReportFilter {
    /** [categoryId] is a top-level category id: its children roll into it. */
    data class ByCategory(
        val categoryId: String,
    ) : ReportFilter

    data object Uncategorized : ReportFilter

    data object TransferFees : ReportFilter

    data class ByMerchant(
        val name: String,
    ) : ReportFilter {
        // A merchant name must never reach a log or crash report.
        override fun toString() = "ByMerchant"
    }
}
