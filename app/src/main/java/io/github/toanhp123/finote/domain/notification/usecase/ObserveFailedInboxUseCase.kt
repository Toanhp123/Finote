package io.github.toanhp123.finote.domain.notification.usecase

import io.github.toanhp123.finote.domain.notification.model.FailedInboxItem
import io.github.toanhp123.finote.domain.notification.repository.FailedInboxRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveFailedInboxUseCase
    @Inject
    constructor(
        private val repository: FailedInboxRepository,
    ) {
        operator fun invoke(): Flow<List<FailedInboxItem>> = repository.observeInbox()
    }
