package io.github.toanhp123.finote.domain.notification.usecase

import io.github.toanhp123.finote.domain.notification.repository.RuleUpdateScheduler
import io.github.toanhp123.finote.domain.notification.repository.RuleUpdateSettingsRepository
import javax.inject.Inject

class SetRuleAutoUpdateUseCase
    @Inject
    constructor(
        private val settings: RuleUpdateSettingsRepository,
        private val scheduler: RuleUpdateScheduler,
    ) {
        /** Saves the choice first, then starts or cancels the daily update. */
        suspend operator fun invoke(isEnabled: Boolean) {
            settings.setAutoUpdateEnabled(isEnabled)
            if (isEnabled) scheduler.enablePeriodic() else scheduler.disablePeriodic()
        }
    }
