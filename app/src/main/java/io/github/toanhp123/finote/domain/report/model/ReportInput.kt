package io.github.toanhp123.finote.domain.report.model

import io.github.toanhp123.finote.domain.category.model.Category
import io.github.toanhp123.finote.domain.transaction.model.Transaction
import java.time.ZoneId

/** Everything a report reads. [ignoredAccountIds] are accounts the user left out of reports; archived ones are not. */
data class ReportInput(
    val transactions: List<Transaction>,
    val ignoredAccountIds: Set<String>,
    val categories: List<Category>,
    val zone: ZoneId,
) {
    // Counts only: rows carry money and merchant names.
    override fun toString() =
        "ReportInput(transactions=${transactions.size}, ignoredAccounts=${ignoredAccountIds.size}, " +
            "categories=${categories.size})"
}
