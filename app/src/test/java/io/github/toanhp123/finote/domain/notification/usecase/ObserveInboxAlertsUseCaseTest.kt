package io.github.toanhp123.finote.domain.notification.usecase

import app.cash.turbine.test
import io.github.toanhp123.finote.domain.notification.model.FailedInboxItem
import io.github.toanhp123.finote.domain.notification.model.InboxAlerts
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

private fun item(
    id: String,
    isHidden: Boolean = false,
) = FailedInboxItem(
    id = id,
    logIds = listOf(id),
    packageName = "com.test.bank",
    title = "t",
    content = "c",
    postTime = 1L,
    receivedAt = 1L,
    reason = "no_rule_matched",
    parsed = null,
    isHidden = isHidden,
    isKeywordHidden = false,
)

class ObserveInboxAlertsUseCaseTest {
    private val inbox = FakeFailedInboxRepository()
    private val markers = FakeInboxMarkerRepository()
    private val observeAlerts = ObserveInboxAlertsUseCase(ObserveFailedInboxUseCase(inbox), markers)
    private val markSeen = MarkInboxSeenUseCase(markers)
    private val dismissBanner = DismissInboxBannerUseCase(markers)

    @Test
    fun `empty inbox has no dot and no banner`() =
        runTest {
            observeAlerts().test {
                assertEquals(InboxAlerts(shownCount = 0, hasUnseen = false, isBannerVisible = false), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `hidden items are not counted and raise nothing`() =
        runTest {
            inbox.items.value = listOf(item("a", isHidden = true))

            observeAlerts().test {
                assertEquals(InboxAlerts(shownCount = 0, hasUnseen = false, isBannerVisible = false), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `an unseen shown item shows the dot and the banner`() =
        runTest {
            inbox.items.value = listOf(item("a"), item("b"), item("h", isHidden = true))

            observeAlerts().test {
                assertEquals(InboxAlerts(shownCount = 2, hasUnseen = true, isBannerVisible = true), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `opening clears the dot`() =
        runTest {
            inbox.items.value = listOf(item("a"))

            observeAlerts().test {
                assertEquals(true, awaitItem().hasUnseen)
                markSeen(setOf("a"))
                assertEquals(InboxAlerts(shownCount = 1, hasUnseen = false, isBannerVisible = true), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `new item after seen shows the dot`() =
        runTest {
            inbox.items.value = listOf(item("a"))
            markSeen(setOf("a"))

            observeAlerts().test {
                assertEquals(false, awaitItem().hasUnseen)
                inbox.items.value = listOf(item("b"), item("a"))
                assertEquals(InboxAlerts(shownCount = 2, hasUnseen = true, isBannerVisible = true), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `banner hidden until a new item`() =
        runTest {
            inbox.items.value = listOf(item("a"))
            dismissBanner(setOf("a"))

            observeAlerts().test {
                assertEquals(InboxAlerts(shownCount = 1, hasUnseen = true, isBannerVisible = false), awaitItem())
                inbox.items.value = listOf(item("b"), item("a"))
                assertEquals(InboxAlerts(shownCount = 2, hasUnseen = true, isBannerVisible = true), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `an item leaving the inbox keeps the banner closed`() =
        runTest {
            inbox.items.value = listOf(item("a"), item("b"))
            dismissBanner(setOf("a", "b"))

            observeAlerts().test {
                assertEquals(false, awaitItem().isBannerVisible)
                inbox.items.value = listOf(item("a"))
                assertEquals(InboxAlerts(shownCount = 1, hasUnseen = true, isBannerVisible = false), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }
}
