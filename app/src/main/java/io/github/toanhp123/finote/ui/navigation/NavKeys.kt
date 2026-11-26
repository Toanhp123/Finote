package io.github.toanhp123.finote.ui.navigation

import androidx.navigation3.runtime.NavKey
import io.github.toanhp123.finote.domain.category.model.CategoryType
import kotlinx.serialization.Serializable

@Serializable
data object Overview : NavKey

@Serializable
data object Transactions : NavKey

@Serializable
data object Budget : NavKey

@Serializable
data object Settings : NavKey

/**
 * The notification access setup screen. [isFromOnboarding] is true on the first run, when it is the root of the stack,
 * and false when the listener-off banner pushed it on top of another screen.
 */
@Serializable
data class Setup(
    val isFromOnboarding: Boolean,
) : NavKey

/** The accounts page (bank groups, archive, reorder), pushed on top of [Settings]. */
@Serializable
data object AccountsSettings : NavKey

/**
 * The account create screen ([id] null) or edit screen, pushed on top of [AccountsSettings]. [mergeTargetId] opens the
 * merge dialog into that account on load (a list suggestion).
 */
@Serializable
data class EditAccount(
    val id: String? = null,
    val mergeTargetId: String? = null,
) : NavKey

/** The categories page (Expense and Income tabs, hide, reorder), pushed on top of [Settings]. */
@Serializable
data object CategoriesSettings : NavKey

/**
 * The category create screen ([id] null) or edit screen. [parentId] presets the parent of a new subcategory; [type] is
 * the type of a new category (an edit takes the stored type).
 */
@Serializable
data class EditCategory(
    val id: String? = null,
    val type: CategoryType = CategoryType.EXPENSE,
    val parentId: String? = null,
) : NavKey

/** The auto category rules page (keyword rules), pushed on top of [Settings]. */
@Serializable
data object KeywordRulesSettings : NavKey

/** The keyword rule create screen ([id] null) or edit screen, pushed on top of [KeywordRulesSettings]. */
@Serializable
data class EditKeywordRule(
    val id: String? = null,
) : NavKey

/** The notification capture page, pushed on top of [Settings]. */
@Serializable
data object NotificationCaptureSettings : NavKey

/** The notification log page, pushed on top of [Settings]. Registered only when tools are enabled. */
@Serializable
data object NotificationLogs : NavKey

/** The failed notifications inbox, pushed on top of [Settings]. */
@Serializable
data object FailedInbox : NavKey

/** The booking sheet of the inbox item [itemId], drawn over the inbox. */
@Serializable
data class BookInboxItem(
    val itemId: String,
) : NavKey

/** The cash entry sheet in edit mode for the transaction [id], drawn over the list below it. */
@Serializable
data class EditTransaction(
    val id: String,
) : NavKey

/** What a [ReportList] shows; [ReportList.key] is the category id or the merchant name for two of them. */
@Serializable
enum class ReportListKind { CATEGORY, UNCATEGORIZED, TRANSFER_FEES, MERCHANT }

/**
 * The rows behind one Overview amount, pushed on top of [Overview]. [month] is `yyyy-MM`. [key] is the category id
 * ([ReportListKind.CATEGORY]) or the merchant name ([ReportListKind.MERCHANT]) and is null for the other kinds.
 */
@Serializable
data class ReportList(
    val month: String,
    val kind: ReportListKind,
    val key: String? = null,
) : NavKey {
    // A merchant name must never reach a log or crash report.
    override fun toString() = "ReportList(month=$month, kind=$kind)"
}
