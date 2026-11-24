package io.github.toanhp123.finote.domain.notification.repository

import io.github.toanhp123.finote.domain.account.model.AccountIdentifier
import io.github.toanhp123.finote.domain.notification.model.AdjustmentToken
import io.github.toanhp123.finote.domain.notification.model.BookInboxResult
import io.github.toanhp123.finote.domain.notification.model.FailedInboxItem
import io.github.toanhp123.finote.domain.notification.model.InboxBooking
import kotlinx.coroutines.flow.Flow

interface FailedInboxRepository {
    /** The inbox items, newest first. Re-emits when a log, a parser rule or a booked log changes. */
    fun observeInbox(): Flow<List<FailedInboxItem>>

    /** The inbox item with [id] as the list shows it, or null when it is not an item now. */
    suspend fun getItem(id: String): FailedInboxItem?

    /** Every identifier of [packageName], for the account lookup of a prefill. */
    suspend fun getIdentifiers(packageName: String): List<AccountIdentifier>

    /**
     * Books [booking] in one write: checks that every log of the item is still a failed inbox log, then the adjustment
     * rules, then inserts a `MANUAL` transaction (null post balance) and confirms the logs. Any result but
     * [BookInboxResult.Booked] has written nothing. Run it inside `TransactionRunner.write`.
     */
    suspend fun book(
        booking: InboxBooking,
        confirmation: AdjustmentToken?,
        transactionId: String,
        now: Long,
    ): BookInboxResult

    /**
     * Gives every log of the item reason `user_dismissed`; the status stays `FAILED`. [logIds] are the logs the user
     * saw. False when any of them was no longer a failed inbox log, with nothing written.
     */
    suspend fun dismiss(
        itemId: String,
        logIds: List<String>,
    ): Boolean
}
