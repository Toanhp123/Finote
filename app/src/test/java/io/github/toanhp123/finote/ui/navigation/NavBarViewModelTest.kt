package io.github.toanhp123.finote.ui.navigation

import io.github.toanhp123.finote.domain.notification.model.FailedInboxItem
import io.github.toanhp123.finote.domain.notification.usecase.FakeFailedInboxRepository
import io.github.toanhp123.finote.domain.notification.usecase.FakeInboxMarkerRepository
import io.github.toanhp123.finote.domain.notification.usecase.ObserveFailedInboxUseCase
import io.github.toanhp123.finote.domain.notification.usecase.ObserveInboxAlertsUseCase
import io.github.toanhp123.finote.ui.common.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NavBarViewModelTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    private val inbox = FakeFailedInboxRepository()
    private val markers = FakeInboxMarkerRepository()

    private fun item(id: String) =
        FailedInboxItem(
            id = id,
            logIds = listOf(id),
            packageName = "pkg",
            title = "t",
            content = "c",
            postTime = 1L,
            receivedAt = 1L,
            reason = "no_rule_matched",
            parsed = null,
            isHidden = false,
            isKeywordHidden = false,
        )

    @Test
    fun `the settings badge shows for an unseen item and clears when it is seen`() =
        runTest(mainRule.dispatcher) {
            val vm =
                NavBarViewModel(
                    ObserveInboxAlertsUseCase(ObserveFailedInboxUseCase(inbox), markers),
                    isDebuggable = true,
                )
            runCurrent()
            assertFalse(vm.state.value.hasSettingsBadge)

            inbox.items.value = listOf(item("a"))
            runCurrent()
            assertTrue(vm.state.value.hasSettingsBadge)

            markers.markSeen(setOf("a"))
            runCurrent()
            assertFalse(vm.state.value.hasSettingsBadge)
        }
}
