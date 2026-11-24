package io.github.toanhp123.finote.domain.notification.usecase

import io.github.toanhp123.finote.domain.notification.model.FailedInboxItem
import io.github.toanhp123.finote.domain.notification.repository.FailedInboxRepository
import javax.inject.Inject

class GetInboxItemUseCase
    @Inject
    constructor(
        private val repository: FailedInboxRepository,
    ) {
        /** The item with its logs and original text, or null when it is gone, for example booked meanwhile. */
        suspend operator fun invoke(itemId: String): FailedInboxItem? = repository.getItem(itemId)
    }
