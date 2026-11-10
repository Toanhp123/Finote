package io.github.toanhp123.finote.domain.notification.usecase

import io.github.toanhp123.finote.domain.notification.repository.RuleUpdateScheduler
import javax.inject.Inject

class CheckRulesNowUseCase
    @Inject
    constructor(
        private val scheduler: RuleUpdateScheduler,
    ) {
        /** Starts one parser rule update now, with no network constraint. */
        operator fun invoke() = scheduler.checkNow()
    }
