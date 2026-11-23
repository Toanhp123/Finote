package io.github.toanhp123.finote.domain.notification.usecase

import io.github.toanhp123.finote.domain.notification.model.NotificationLog
import io.github.toanhp123.finote.domain.notification.repository.NotificationCaptureRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

// shortcut: newest 500 only, add paging if dogfood logs grow past it
private const val LOG_LIMIT = 500

class ObserveNotificationLogsUseCase
    @Inject
    constructor(
        private val repository: NotificationCaptureRepository,
    ) {
        operator fun invoke(): Flow<List<NotificationLog>> = repository.observeRecentLogs(LOG_LIMIT)
    }
