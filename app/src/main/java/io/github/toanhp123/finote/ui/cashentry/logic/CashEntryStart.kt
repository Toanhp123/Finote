package io.github.toanhp123.finote.ui.cashentry.logic

import io.github.toanhp123.finote.domain.notification.usecase.GetInboxItemUseCase
import io.github.toanhp123.finote.domain.notification.usecase.GetInboxPrefillUseCase
import io.github.toanhp123.finote.domain.transaction.model.ManualChoices
import io.github.toanhp123.finote.domain.transaction.model.Transaction
import io.github.toanhp123.finote.domain.transaction.usecase.GetLastManualChoicesUseCase
import io.github.toanhp123.finote.domain.transaction.usecase.GetTransactionUseCase
import io.github.toanhp123.finote.domain.transaction.usecase.IsAutoPairedUseCase
import javax.inject.Inject

/**
 * What the first read gives: the last choices in create mode, the stored row (null if gone) and whether
 * auto-pairing made it in edit mode, the inbox item and its prefill (null if gone) in from-log mode.
 */
internal class CashEntryStart(
    val choices: ManualChoices,
    val row: Transaction?,
    val isAutoPaired: Boolean = false,
    val inbox: InboxSource? = null,
) {
    // The row and the inbox item hold notes and amounts; they stay out of logs.
    override fun toString() = "CashEntryStart"
}

/** Reads what the cash entry sheet starts with, once per sheet. */
class CashEntryStartReader
    @Inject
    constructor(
        private val getLastManualChoices: GetLastManualChoicesUseCase,
        private val getTransaction: GetTransactionUseCase,
        private val isAutoPaired: IsAutoPairedUseCase,
        private val getInboxItem: GetInboxItemUseCase,
        private val getInboxPrefill: GetInboxPrefillUseCase,
    ) {
        /** Create mode reads the last choices, edit mode the stored row, from-log mode the inbox item. */
        internal suspend fun read(
            transactionId: String?,
            inboxItemId: String?,
        ): CashEntryStart =
            when {
                inboxItemId != null -> CashEntryStart(NO_CHOICES, null, inbox = readInbox(inboxItemId))
                transactionId == null -> CashEntryStart(getLastManualChoices(), null)
                else -> CashEntryStart(NO_CHOICES, getTransaction(transactionId), isAutoPaired(transactionId))
            }

        // Null when the item was handled since the inbox showed it.
        private suspend fun readInbox(id: String): InboxSource? {
            val item = getInboxItem(id) ?: return null
            return getInboxPrefill(id)?.let { InboxSource(item, it) }
        }

        private companion object {
            val NO_CHOICES = ManualChoices(null, emptyMap())
        }
    }
