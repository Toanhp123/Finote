package io.github.toanhp123.finote.ui.settings.inbox

import app.cash.turbine.test
import io.github.toanhp123.finote.domain.common.FakeTransactionRunner
import io.github.toanhp123.finote.domain.notification.model.AdKeywordRefusal
import io.github.toanhp123.finote.domain.notification.model.FailedInboxItem
import io.github.toanhp123.finote.domain.notification.model.NotificationStatus
import io.github.toanhp123.finote.domain.notification.model.UserIgnoreKeyword
import io.github.toanhp123.finote.domain.notification.testLog
import io.github.toanhp123.finote.domain.notification.testRule
import io.github.toanhp123.finote.domain.notification.usecase.DeleteAdKeywordUseCase
import io.github.toanhp123.finote.domain.notification.usecase.DismissInboxItemUseCase
import io.github.toanhp123.finote.domain.notification.usecase.FakeFailedInboxRepository
import io.github.toanhp123.finote.domain.notification.usecase.FakeInboxMarkerRepository
import io.github.toanhp123.finote.domain.notification.usecase.FakeNotificationCaptureRepository
import io.github.toanhp123.finote.domain.notification.usecase.FakeParserRuleRepository
import io.github.toanhp123.finote.domain.notification.usecase.FakeSettleScheduler
import io.github.toanhp123.finote.domain.notification.usecase.FakeUserKeywordRepository
import io.github.toanhp123.finote.domain.notification.usecase.LearnAdKeywordUseCase
import io.github.toanhp123.finote.domain.notification.usecase.MarkInboxSeenUseCase
import io.github.toanhp123.finote.domain.notification.usecase.ObserveAdKeywordsUseCase
import io.github.toanhp123.finote.domain.notification.usecase.ObserveFailedInboxUseCase
import io.github.toanhp123.finote.domain.notification.usecase.ObserveParserRulesUseCase
import io.github.toanhp123.finote.domain.notification.usecase.PreviewAdKeywordUseCase
import io.github.toanhp123.finote.domain.notification.usecase.ReparseFailedLogsUseCase
import io.github.toanhp123.finote.ui.common.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

private const val PKG = "com.test.bank"
private const val PHRASE = "Mời bạn ly cà phê"

private fun item(
    id: String,
    logIds: List<String> = listOf(id),
    reason: String = "no_rule_matched",
    isHidden: Boolean = false,
    isKeywordHidden: Boolean = false,
    title: String = "title $id",
) = FailedInboxItem(
    id = id,
    logIds = logIds,
    packageName = PKG,
    title = title,
    content = "content $id",
    postTime = 1_000L,
    receivedAt = 1_000L,
    reason = reason,
    parsed = null,
    isHidden = isHidden || isKeywordHidden,
    isKeywordHidden = isKeywordHidden,
)

private fun keyword(
    id: String,
    normalized: String,
) = UserIgnoreKeyword(
    id = id,
    packageName = PKG,
    keyword = normalized,
    normalizedKeyword = normalized,
    createdAt = 1L,
)

private fun unparsed(
    id: String,
    title: String,
    content: String,
) = testLog(
    id = id,
    packageName = PKG,
    title = title,
    content = content,
    status = NotificationStatus.FAILED,
    failureReason = "no_rule_matched",
)

@OptIn(ExperimentalCoroutinesApi::class)
class FailedInboxViewModelTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    private val inbox = FakeFailedInboxRepository()
    private val markers = FakeInboxMarkerRepository()
    private val ruleRepo = FakeParserRuleRepository()
    private val keywordRepo = FakeUserKeywordRepository()

    private fun viewModel(): FailedInboxViewModel {
        val reparse =
            ReparseFailedLogsUseCase(FakeNotificationCaptureRepository(), mainRule.dispatcher, FakeSettleScheduler())
        return FailedInboxViewModel(
            ObserveFailedInboxUseCase(inbox),
            ObserveParserRulesUseCase(ruleRepo),
            ObserveAdKeywordsUseCase(keywordRepo),
            MarkInboxSeenUseCase(markers),
            DismissInboxItemUseCase(inbox),
            DeleteAdKeywordUseCase(keywordRepo, FakeTransactionRunner(), reparse),
            PreviewAdKeywordUseCase(keywordRepo),
            LearnAdKeywordUseCase(
                keywordRepo,
                FakeTransactionRunner(),
                Clock.fixed(Instant.parse("2026-11-23T00:00:00Z"), ZoneOffset.UTC),
            ),
            isDebuggable = true,
        )
    }

    /** The selected log is "Mời bạn ly cà phê!"; a second unparsed promo carries the same phrase. */
    private fun seedAd() {
        keywordRepo.logs +=
            listOf(
                unparsed("a", "$PHRASE!", "Nhận ngay ưu đãi"),
                unparsed("b", "Hôm nay", "$PHRASE miễn phí"),
            )
        inbox.items.value = listOf(item("a", title = "🎉 $PHRASE!"))
    }

    @Test
    fun `opening marks the shown items seen and not the hidden ones`() =
        runTest(mainRule.dispatcher) {
            inbox.items.value = listOf(item("a"), item("b"), item("h", isHidden = true))
            viewModel()
            runCurrent()

            assertEquals(setOf("a", "b"), markers.observe().first().seenIds)
        }

    @Test
    fun `an item that arrives while the screen is open is marked seen too`() =
        runTest(mainRule.dispatcher) {
            inbox.items.value = listOf(item("a"))
            viewModel()
            runCurrent()

            inbox.items.value = listOf(item("a"), item("b"))
            runCurrent()

            assertEquals(setOf("a", "b"), markers.observe().first().seenIds)
        }

    @Test
    fun `state splits shown and hidden and names banks from rules`() =
        runTest(mainRule.dispatcher) {
            ruleRepo.stored.value = listOf(testRule(packageName = PKG, bankName = "Test Bank"))
            inbox.items.value = listOf(item("a"), item("h", isHidden = true), item("k", isKeywordHidden = true))
            val vm = viewModel()
            runCurrent()

            val state = vm.state.value
            assertTrue(state.isLoaded)
            assertEquals(listOf("a"), state.shown.map { it.id })
            assertEquals(listOf("h", "k"), state.hidden.map { it.id })
            assertEquals("Test Bank", state.bankNames[PKG])
        }

    @Test
    fun `hidden section starts collapsed and toggles`() =
        runTest(mainRule.dispatcher) {
            val vm = viewModel()
            runCurrent()
            assertFalse(vm.state.value.isHiddenExpanded)

            vm.onIntent(FailedInboxIntent.HiddenToggled)
            assertTrue(vm.state.value.isHiddenExpanded)

            vm.onIntent(FailedInboxIntent.HiddenToggled)
            assertFalse(vm.state.value.isHiddenExpanded)
        }

    @Test
    fun `dismiss sends the item id and every log id`() =
        runTest(mainRule.dispatcher) {
            inbox.items.value = listOf(item("a", logIds = listOf("a", "a-copy")))
            val vm = viewModel()
            runCurrent()

            vm.effects.test {
                vm.onIntent(FailedInboxIntent.DismissClicked("a"))
                runCurrent()

                expectNoEvents()
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(listOf("a" to listOf("a", "a-copy")), inbox.dismissCalls)
        }

    @Test
    fun `dismiss of a handled item shows the snackbar`() =
        runTest(mainRule.dispatcher) {
            inbox.items.value = listOf(item("a"))
            inbox.dismissResult = false
            val vm = viewModel()
            runCurrent()

            vm.effects.test {
                vm.onIntent(FailedInboxIntent.DismissClicked("a"))
                runCurrent()

                assertEquals(FailedInboxEffect.ShowAlreadyHandled, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `restore deletes the keyword that hid the item`() =
        runTest(mainRule.dispatcher) {
            keywordRepo.keywords.value = listOf(keyword("k1", "ly ca phe"), keyword("k2", "khuyen mai"))
            inbox.items.value = listOf(item("a", reason = "user_keyword:ly ca phe", isKeywordHidden = true))
            val vm = viewModel()
            runCurrent()

            vm.onIntent(FailedInboxIntent.RestoreClicked("a"))
            runCurrent()

            assertEquals(listOf("k1"), keywordRepo.removedIds)
        }

    @Test
    fun `restore after the keyword is gone shows the snackbar`() =
        runTest(mainRule.dispatcher) {
            inbox.items.value = listOf(item("a", reason = "user_keyword:ly ca phe", isKeywordHidden = true))
            val vm = viewModel()
            runCurrent()

            vm.effects.test {
                vm.onIntent(FailedInboxIntent.RestoreClicked("a"))
                runCurrent()

                assertEquals(FailedInboxEffect.ShowAlreadyHandled, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `book opens its sheet and the ad sheet opens only where the item allows it`() =
        runTest(mainRule.dispatcher) {
            inbox.items.value =
                listOf(
                    item("plain"),
                    item("failed", reason = "ambiguous_account"),
                    item("kw", reason = "user_keyword:x", isKeywordHidden = true),
                )
            val vm = viewModel()
            runCurrent()

            vm.effects.test {
                vm.onIntent(FailedInboxIntent.ItemClicked("plain"))
                vm.onIntent(FailedInboxIntent.ItemClicked("failed"))
                vm.onIntent(FailedInboxIntent.ItemClicked("kw"))
                runCurrent()

                assertEquals(FailedInboxEffect.OpenBooking("plain"), awaitItem())
                assertEquals(FailedInboxEffect.OpenBooking("failed"), awaitItem())
                expectNoEvents()
                cancelAndIgnoreRemainingEvents()
            }

            vm.onIntent(FailedInboxIntent.AdClicked("failed"))
            vm.onIntent(FailedInboxIntent.AdClicked("kw"))
            assertNull(vm.state.value.adSheet)

            vm.onIntent(FailedInboxIntent.AdClicked("plain"))
            assertEquals(
                "plain",
                vm.state.value.adSheet
                    ?.itemId,
            )
        }

    @Test
    fun `sheet opens with the suggestion`() =
        runTest(mainRule.dispatcher) {
            seedAd()
            val vm = viewModel()
            runCurrent()

            vm.onIntent(FailedInboxIntent.AdClicked("a"))
            runCurrent()
            assertEquals(
                PHRASE,
                vm.state.value.adSheet
                    ?.keyword,
            )
            assertNull(
                vm.state.value.adSheet
                    ?.preview,
            )

            advanceTimeBy(150)
            runCurrent()
            assertEquals(
                1,
                vm.state.value.adSheet
                    ?.preview
                    ?.otherHiddenCount,
            )
            assertNull(
                vm.state.value.adSheet
                    ?.preview
                    ?.refusal,
            )
        }

    @Test
    fun `preview waits until typing pauses and keeps only the last read`() =
        runTest(mainRule.dispatcher) {
            seedAd()
            val vm = viewModel()
            runCurrent()
            vm.onIntent(FailedInboxIntent.AdClicked("a"))
            advanceTimeBy(150)
            runCurrent()

            vm.onIntent(FailedInboxIntent.AdKeywordChanged("ab"))
            advanceTimeBy(100)
            vm.onIntent(FailedInboxIntent.AdKeywordChanged("Mời bạn"))
            advanceTimeBy(149)
            runCurrent()
            assertEquals(
                "Mời bạn",
                vm.state.value.adSheet
                    ?.keyword,
            )
            assertNull(
                vm.state.value.adSheet
                    ?.preview
                    ?.refusal,
            )

            vm.onIntent(FailedInboxIntent.AdKeywordChanged("ab"))
            advanceTimeBy(150)
            runCurrent()
            assertEquals(
                AdKeywordRefusal.TOO_SHORT,
                vm.state.value.adSheet
                    ?.preview
                    ?.refusal,
            )
        }

    @Test
    fun `refusal shows its message and keeps the sheet`() =
        runTest(mainRule.dispatcher) {
            seedAd()
            val vm = viewModel()
            runCurrent()
            vm.onIntent(FailedInboxIntent.AdClicked("a"))
            vm.onIntent(FailedInboxIntent.AdKeywordChanged("ab"))
            advanceTimeBy(150)
            runCurrent()

            vm.effects.test {
                vm.onIntent(FailedInboxIntent.AdSaveClicked)
                runCurrent()

                assertEquals(
                    AdKeywordRefusal.TOO_SHORT,
                    vm.state.value.adSheet
                        ?.preview
                        ?.refusal,
                )
                expectNoEvents()
                cancelAndIgnoreRemainingEvents()
            }
            assertTrue(keywordRepo.added.isEmpty())
        }

    @Test
    fun `a refusal found at save keeps the sheet`() =
        runTest(mainRule.dispatcher) {
            seedAd()
            val vm = viewModel()
            runCurrent()
            vm.onIntent(FailedInboxIntent.AdClicked("a"))
            advanceTimeBy(150)
            runCurrent()
            keywordRepo.logs +=
                testLog(id = "m", packageName = PKG, content = PHRASE, status = NotificationStatus.CONFIRMED)

            vm.onIntent(FailedInboxIntent.AdSaveClicked)
            runCurrent()

            assertEquals(
                AdKeywordRefusal.IN_REAL_TRANSACTION,
                vm.state.value.adSheet
                    ?.preview
                    ?.refusal,
            )
            assertTrue(keywordRepo.added.isEmpty())
        }

    @Test
    fun `save closes and offers undo`() =
        runTest(mainRule.dispatcher) {
            seedAd()
            val vm = viewModel()
            runCurrent()
            vm.onIntent(FailedInboxIntent.AdClicked("a"))
            advanceTimeBy(150)
            runCurrent()

            vm.effects.test {
                vm.onIntent(FailedInboxIntent.AdSaveClicked)
                runCurrent()

                val taught = keywordRepo.added.single().first
                assertEquals(FailedInboxEffect.ShowAdLearned(taught.id), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            assertNull(vm.state.value.adSheet)
            assertEquals(listOf("a", "b"), keywordRepo.added.single().second)
        }

    @Test
    fun `undo deletes the keyword`() =
        runTest(mainRule.dispatcher) {
            seedAd()
            val vm = viewModel()
            runCurrent()
            vm.onIntent(FailedInboxIntent.AdClicked("a"))
            advanceTimeBy(150)
            runCurrent()
            vm.onIntent(FailedInboxIntent.AdSaveClicked)
            runCurrent()
            val taught = keywordRepo.added.single().first

            vm.onIntent(FailedInboxIntent.AdUndoClicked(taught.id))
            runCurrent()

            assertEquals(listOf(taught.id), keywordRepo.removedIds)
        }

    @Test
    fun `dismissing the sheet drops it and its pending preview`() =
        runTest(mainRule.dispatcher) {
            seedAd()
            val vm = viewModel()
            runCurrent()
            vm.onIntent(FailedInboxIntent.AdClicked("a"))

            vm.onIntent(FailedInboxIntent.AdSheetDismissed)
            advanceTimeBy(150)
            runCurrent()

            assertNull(vm.state.value.adSheet)
        }

    @Test
    fun `back closes the screen`() =
        runTest(mainRule.dispatcher) {
            val vm = viewModel()

            vm.effects.test {
                vm.onIntent(FailedInboxIntent.BackClicked)
                runCurrent()

                assertEquals(FailedInboxEffect.Close, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }
}
