package io.github.toanhp123.finote.ui.report.logic

import io.github.toanhp123.finote.domain.report.model.ReportFilter
import io.github.toanhp123.finote.ui.navigation.ReportList
import io.github.toanhp123.finote.ui.navigation.ReportListKind
import java.time.YearMonth

fun ReportFilter.toNavKey(month: YearMonth): ReportList {
    val text = month.toString()
    return when (this) {
        is ReportFilter.ByCategory -> ReportList(text, ReportListKind.CATEGORY, categoryId)
        ReportFilter.Uncategorized -> ReportList(text, ReportListKind.UNCATEGORIZED)
        ReportFilter.TransferFees -> ReportList(text, ReportListKind.TRANSFER_FEES)
        is ReportFilter.ByMerchant -> ReportList(text, ReportListKind.MERCHANT, name)
        ReportFilter.AllSpending -> ReportList(text, ReportListKind.ALL_SPENDING)
    }
}

/** A key that does not fit its kind fails loudly: it can only come from a bug, never from the user. */
fun ReportList.toFilter(): ReportFilter =
    when (kind) {
        ReportListKind.CATEGORY -> {
            ReportFilter.ByCategory(requireNotNull(key) { "a category list needs its id" })
        }

        ReportListKind.MERCHANT -> {
            ReportFilter.ByMerchant(requireNotNull(key) { "a merchant list needs its name" })
        }

        ReportListKind.UNCATEGORIZED -> {
            require(key == null) { "this list takes no key" }
            ReportFilter.Uncategorized
        }

        ReportListKind.TRANSFER_FEES -> {
            require(key == null) { "this list takes no key" }
            ReportFilter.TransferFees
        }

        ReportListKind.ALL_SPENDING -> {
            require(key == null) { "this list takes no key" }
            ReportFilter.AllSpending
        }
    }

fun ReportList.yearMonth(): YearMonth = YearMonth.parse(month)

/**
 * The nav entry's content key. `toString()` hides [ReportList.key], so the default key would make two lists of one
 * month and kind share a ViewModelStore. The key is never logged: nothing in the app prints a content key.
 */
fun ReportList.contentKey(): String = "report:$month:$kind:$key"
