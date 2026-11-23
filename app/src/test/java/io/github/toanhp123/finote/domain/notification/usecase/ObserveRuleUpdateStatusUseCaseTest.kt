package io.github.toanhp123.finote.domain.notification.usecase

import io.github.toanhp123.finote.domain.notification.model.RuleUpdateCheck
import io.github.toanhp123.finote.domain.notification.model.RuleUpdateResult
import io.github.toanhp123.finote.domain.notification.model.RuleUpdateStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ObserveRuleUpdateStatusUseCaseTest {
    private val settings = FakeRuleUpdateSettingsRepository()
    private val observeStatus = ObserveRuleUpdateStatusUseCase(settings)

    @Test
    fun `combines both values`() =
        runTest {
            settings.setAutoUpdateEnabled(false)
            val check = RuleUpdateCheck(checkedAt = 7L, result = RuleUpdateResult.UpToDate)
            settings.saveLastCheck(check)

            assertEquals(RuleUpdateStatus(isAutoUpdateEnabled = false, lastCheck = check), observeStatus().first())
        }
}
