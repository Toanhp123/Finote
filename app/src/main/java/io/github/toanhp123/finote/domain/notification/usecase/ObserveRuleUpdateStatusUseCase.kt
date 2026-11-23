package io.github.toanhp123.finote.domain.notification.usecase

import io.github.toanhp123.finote.domain.notification.model.RuleUpdateStatus
import io.github.toanhp123.finote.domain.notification.repository.RuleUpdateSettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

class ObserveRuleUpdateStatusUseCase
    @Inject
    constructor(
        private val repository: RuleUpdateSettingsRepository,
    ) {
        operator fun invoke(): Flow<RuleUpdateStatus> =
            combine(repository.observeIsAutoUpdateEnabled(), repository.observeLastCheck(), ::RuleUpdateStatus)
    }
