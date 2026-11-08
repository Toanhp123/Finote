package io.github.toanhp123.finote.domain.notification.usecase

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SetRuleAutoUpdateUseCaseTest {
    private val settings = FakeRuleUpdateSettingsRepository()
    private val scheduler = FakeRuleUpdateScheduler()
    private val setAutoUpdate = SetRuleAutoUpdateUseCase(settings, scheduler)

    @Test
    fun `enable saves and schedules`() =
        runTest {
            setAutoUpdate(false)

            setAutoUpdate(true)

            assertTrue(settings.observeIsAutoUpdateEnabled().first())
            assertTrue(scheduler.isPeriodicEnabled)
        }

    @Test
    fun `disable saves and cancels`() =
        runTest {
            setAutoUpdate(true)

            setAutoUpdate(false)

            assertFalse(settings.observeIsAutoUpdateEnabled().first())
            assertFalse(scheduler.isPeriodicEnabled)
        }

    @Test
    fun `last toggle wins`() =
        runTest {
            setAutoUpdate(true)
            setAutoUpdate(false)
            setAutoUpdate(true)

            assertTrue(settings.observeIsAutoUpdateEnabled().first())
            assertTrue(scheduler.isPeriodicEnabled)
        }
}
