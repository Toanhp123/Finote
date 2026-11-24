package io.github.toanhp123.finote.domain.notification.usecase

import io.github.toanhp123.finote.domain.account.repository.AccountRepository
import io.github.toanhp123.finote.domain.notification.model.InboxPrefill
import io.github.toanhp123.finote.domain.notification.repository.FailedInboxRepository
import io.github.toanhp123.finote.domain.notification.service.prefillFor
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class GetInboxPrefillUseCase
    @Inject
    constructor(
        private val inbox: FailedInboxRepository,
        private val accounts: AccountRepository,
    ) {
        /** Null when the item is gone, for example booked or removed since the list showed it. */
        suspend operator fun invoke(itemId: String): InboxPrefill? {
            val item = inbox.getItem(itemId) ?: return null
            return prefillFor(item, inbox.getIdentifiers(item.packageName), accounts.observeAccounts().first())
        }
    }
