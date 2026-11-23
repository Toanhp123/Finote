package io.github.toanhp123.finote.domain.notification.usecase

import io.github.toanhp123.finote.domain.notification.port.RuleUpdateScheduler
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveRuleCheckingUseCase
    @Inject
    constructor(
        private val scheduler: RuleUpdateScheduler,
    ) {
        /** True while a manual check waits or runs, or while the periodic one runs. */
        operator fun invoke(): Flow<Boolean> = scheduler.observeIsChecking()
    }
