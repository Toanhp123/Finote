package io.github.toanhp123.finote.ui.cashentry.logic

import io.github.toanhp123.finote.domain.category.error.KeywordRuleError
import io.github.toanhp123.finote.domain.category.model.CategoryType
import io.github.toanhp123.finote.domain.category.service.guessKeyword
import io.github.toanhp123.finote.domain.notification.model.InboxBooking
import io.github.toanhp123.finote.domain.transaction.model.Transaction
import io.github.toanhp123.finote.domain.transaction.model.TransactionType
import io.github.toanhp123.finote.ui.cashentry.CashEntrySaveError
import io.github.toanhp123.finote.ui.cashentry.CashEntryState

/** The stored short name of a row opened for edit and its start category: the keys to the rule that gave the name. */
data class RowName(
    val merchantName: String,
    val categoryId: String?,
)

/**
 * The "Apply to similar?" step after a save: a keyword rule for [categoryId] of [type]. [existingRuleId] is the user
 * rule with this keyword and type, which Save updates instead of creating one. [error] is why the last save failed.
 * [renamedBy] is set in edit mode, where the keyword guess is the rule's short name, not the raw merchant: the look-up
 * swaps the guess for the keyword of the rule that gave the name.
 */
data class RuleOfferState(
    val keyword: String,
    val merchantName: String,
    val categoryId: String,
    val type: CategoryType,
    val existingRuleId: String?,
    val isSaving: Boolean,
    val error: KeywordRuleError?,
    val renamedBy: RowName? = null,
) {
    // Keyword and name come from the user's notifications; they must not reach a log.
    override fun toString() = "RuleOfferState(type=$type, existingRuleId=$existingRuleId, isSaving=$isSaving)"
}

/**
 * The step to offer after a save with the form values, or null. Only a notification row ([notificationLogId]) whose
 * new [pickedCategoryId] is set and differs from [startCategoryId] (the row's or the inbox suggestion) gets one, and
 * only when [merchant] or [note] gives a keyword. A transfer saves no category, so its pick is null.
 */
internal fun ruleOfferFor(
    notificationLogId: String?,
    type: TransactionType,
    merchant: String?,
    note: String,
    startCategoryId: String?,
    pickedCategoryId: String?,
): RuleOfferState? {
    val keyword = guessKeyword(merchant, note)
    val isChanged = notificationLogId != null && pickedCategoryId != startCategoryId
    if (!isChanged || pickedCategoryId == null || keyword == null) return null
    val name = keyword.lowercase().split(' ').joinToString(" ") { it.replaceFirstChar(Char::titlecase) }
    return RuleOfferState(keyword, name, pickedCategoryId, type.toCategoryType(), null, false, null)
}

/** Edit mode: the offer for this stored row after a save of [type], [note] and [pickedCategoryId]. */
internal fun Transaction.ruleOffer(
    type: TransactionType,
    note: String,
    pickedCategoryId: String?,
): RuleOfferState? =
    ruleOfferFor(notificationLogId, type, merchantName, note, categoryId, pickedCategoryId)
        ?.copy(renamedBy = merchantName?.trim()?.takeIf { it.isNotEmpty() }?.let { RowName(it, categoryId) })

/**
 * From-log mode: the offer after [booking]; the start category is the rule's suggestion. The keyword is guessed from
 * the raw parsed merchant: the prefill name is a rule's short name, which no later raw notification contains.
 */
internal fun InboxSource.ruleOffer(booking: InboxBooking): RuleOfferState? =
    ruleOfferFor(item.id, booking.type, item.parsed?.merchant, booking.note, prefill.categoryId, booking.categoryId)

/** A rule write runs: the step is busy, an earlier error and the bulk apply question are gone. */
internal fun CashEntryState.ruleWriting(offer: RuleOfferState) =
    copy(ruleOffer = offer.copy(isSaving = true, error = null), bulkApplyCount = null, saveError = null)

/** A rule write failed in storage: the step is back as it was, with the save error. */
internal fun CashEntryState.ruleWriteFailed(offer: RuleOfferState) =
    copy(ruleOffer = offer, saveError = CashEntrySaveError.GENERIC)

/** The user typed [text] as keyword: the old look-up answer and error no longer hold. */
internal fun RuleOfferState.withKeyword(text: String) = copy(keyword = text, existingRuleId = null, error = null)

/** The user typed [name] as the rule's short name. */
internal fun CashEntryState.withRuleName(name: String) = copy(ruleOffer = ruleOffer?.copy(merchantName = name))

/** The look-up that started from [from] found [keyword] and [id]; dropped when the user has changed the keyword. */
internal fun RuleOfferState.withLookedUp(
    from: String,
    keyword: String,
    id: String?,
): RuleOfferState = if (from == this.keyword) copy(keyword = keyword, existingRuleId = id) else this
