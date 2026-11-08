package io.github.toanhp123.finote.ui.settings

import io.github.toanhp123.finote.domain.notification.model.ParserRule

/** One supported bank: the active rules of one package. */
data class BankSummary(
    val packageName: String,
    val bankName: String,
    val activeRuleCount: Int,
    val highestVersion: Int,
    val hasUserEdits: Boolean,
)

/**
 * Groups active [rules] by package, so two spellings of one bank stay one row.
 * The name comes from the active rule with the highest version. A package with no active rule is left out.
 */
fun summarizeBanks(rules: List<ParserRule>): List<BankSummary> =
    rules
        .filter { it.isActive }
        .groupBy { it.packageName }
        .map { (packageName, group) ->
            val newest = group.maxBy { it.version }
            BankSummary(
                packageName = packageName,
                bankName = newest.bankName,
                activeRuleCount = group.size,
                highestVersion = newest.version,
                hasUserEdits = group.any { it.isUserCustom },
            )
        }.sortedBy { it.bankName }
