package io.github.toanhp123.finote.domain.notification.usecase

import io.github.toanhp123.finote.domain.notification.repository.FailedInboxRepository
import javax.inject.Inject

class DismissInboxItemUseCase
    @Inject
    constructor(
        private val repository: FailedInboxRepository,
    ) {
        /** False when the item was already handled meanwhile. */
        suspend operator fun invoke(
            itemId: String,
            logIds: List<String>,
        ): Boolean = repository.dismiss(itemId, logIds)
    }
