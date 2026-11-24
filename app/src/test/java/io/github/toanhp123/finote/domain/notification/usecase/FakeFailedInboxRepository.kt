package io.github.toanhp123.finote.domain.notification.usecase

import io.github.toanhp123.finote.domain.account.model.AccountIdentifier
import io.github.toanhp123.finote.domain.notification.model.AdjustmentToken
import io.github.toanhp123.finote.domain.notification.model.BookInboxResult
import io.github.toanhp123.finote.domain.notification.model.FailedInboxItem
import io.github.toanhp123.finote.domain.notification.model.InboxBooking
import io.github.toanhp123.finote.domain.notification.repository.FailedInboxRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Copies from the real repository: the inbox is observed.
 * Does not copy: reads by id and booking; they fail loudly so a test cannot rely on them by mistake. Dismissing only
 * records the call and answers [dismissResult]; the inbox does not change.
 */
class FakeFailedInboxRepository : FailedInboxRepository {
    val items = MutableStateFlow<List<FailedInboxItem>>(emptyList())

    /** Every dismiss call as item id to log ids. */
    val dismissCalls = mutableListOf<Pair<String, List<String>>>()
    var dismissResult = true

    override fun observeInbox(): Flow<List<FailedInboxItem>> = items

    override suspend fun getItem(id: String): FailedInboxItem? = error("not faked")

    override suspend fun getIdentifiers(packageName: String): List<AccountIdentifier> = error("not faked")

    override suspend fun book(
        booking: InboxBooking,
        confirmation: AdjustmentToken?,
        transactionId: String,
        now: Long,
    ): BookInboxResult = error("not faked")

    override suspend fun dismiss(
        itemId: String,
        logIds: List<String>,
    ): Boolean {
        dismissCalls += itemId to logIds
        return dismissResult
    }
}
