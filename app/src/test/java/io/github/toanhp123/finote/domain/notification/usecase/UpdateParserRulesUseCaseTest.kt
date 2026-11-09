package io.github.toanhp123.finote.domain.notification.usecase

import io.github.toanhp123.finote.domain.notification.model.NotificationLog
import io.github.toanhp123.finote.domain.notification.model.NotificationStatus
import io.github.toanhp123.finote.domain.notification.model.ParserRule
import io.github.toanhp123.finote.domain.notification.model.RuleFeedError
import io.github.toanhp123.finote.domain.notification.model.RuleFeedException
import io.github.toanhp123.finote.domain.notification.model.RuleUpdateCheck
import io.github.toanhp123.finote.domain.notification.model.RuleUpdateResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class UpdateParserRulesUseCaseTest {
    private val now = Instant.parse("2026-11-08T01:00:00Z")

    private fun rule(pkg: String) =
        ParserRule(
            id = "r-$pkg",
            packageName = pkg,
            bankName = "Bank",
            channelIdAllowlist = null,
            ignoreKeywords = emptyList(),
            priority = 0,
            isUserCustom = false,
            version = 1,
            regexPattern = "(?<sign>[+-])(?<amount>[\\d,]+)",
            fallbackTransactionType = null,
            dateFormatPattern = null,
            isActive = true,
            updatedAt = 0L,
        )

    private fun failedLog(pkg: String) =
        NotificationLog(
            id = "log-$pkg",
            packageName = pkg,
            channelId = "ch",
            title = "Biến động số dư",
            content = "SD TK -50,000",
            contentHash = "hash-$pkg",
            postTime = 1_000L,
            receivedAt = 2_000L,
            status = NotificationStatus.FAILED,
            failureReason = "no_rule_matched",
        )

    private class Env(
        val feed: FakeParserRuleFeed,
        val rules: FakeParserRuleRepository,
        val capture: FakeNotificationCaptureRepository,
        val settings: FakeRuleUpdateSettingsRepository,
        val useCase: UpdateParserRulesUseCase,
    )

    private fun env(
        feedRules: List<ParserRule> = listOf(rule(PKG)),
        written: Int = 1,
    ): Env {
        val feed = FakeParserRuleFeed(feedRules)
        val rules = FakeParserRuleRepository(written)
        val capture = FakeNotificationCaptureRepository(feedRules)
        val settings = FakeRuleUpdateSettingsRepository()
        val reparse = ReparseFailedLogsUseCase(capture, UnconfinedTestDispatcher(), FakeSettleScheduler())
        val clock = Clock.fixed(now, ZoneOffset.UTC)
        return Env(feed, rules, capture, settings, UpdateParserRulesUseCase(feed, rules, reparse, settings, clock))
    }

    @Test
    fun `new rules give Updated and are saved as last check`() =
        runTest {
            val env = env(written = 3)

            val result = env.useCase()

            assertEquals(RuleUpdateResult.Updated(3), result)
            assertEquals(RuleUpdateCheck(now.toEpochMilli(), result), env.settings.lastCheck.value)
        }

    @Test
    fun `nothing newer gives UpToDate`() =
        runTest {
            val env = env(written = 0)

            val result = env.useCase()

            assertEquals(RuleUpdateResult.UpToDate, result)
            assertEquals(RuleUpdateCheck(now.toEpochMilli(), result), env.settings.lastCheck.value)
        }

    @Test
    fun `reparse runs when nothing was written`() =
        runTest {
            val env = env(written = 0)
            env.capture.logs += failedLog(PKG)

            env.useCase()

            assertEquals(
                NotificationStatus.PENDING,
                env.capture.logs
                    .single()
                    .status,
            )
        }

    @Test
    fun `reparse covers every package in the feed`() =
        runTest {
            val env = env(feedRules = listOf(rule(PKG), rule(OTHER_PKG)))
            env.capture.logs += failedLog(PKG)
            env.capture.logs += failedLog(OTHER_PKG)

            env.useCase()

            assertTrue(env.capture.logs.all { it.status == NotificationStatus.PENDING })
        }

    @Test
    fun `network error gives Network and writes no rule`() =
        runTest {
            val env = env()
            env.feed.error = RuleFeedException(RuleFeedError.NETWORK, "timeout")

            val result = env.useCase()

            assertEquals(RuleUpdateResult.Network, result)
            assertTrue(env.rules.applied.isEmpty())
            assertEquals(RuleUpdateCheck(now.toEpochMilli(), result), env.settings.lastCheck.value)
        }

    @Test
    fun `invalid feed gives Invalid and writes no rule`() =
        runTest {
            val env = env()
            env.feed.error = RuleFeedException(RuleFeedError.INVALID, "bad_signature")

            val result = env.useCase()

            assertEquals(RuleUpdateResult.Invalid, result)
            assertTrue(env.rules.applied.isEmpty())
            assertEquals(RuleUpdateCheck(now.toEpochMilli(), result), env.settings.lastCheck.value)
        }

    @Test
    fun `applyRules failure gives Error`() =
        runTest {
            val env = env()
            env.rules.error = IllegalStateException("db")

            val result = env.useCase()

            assertEquals(RuleUpdateResult.Error, result)
            assertEquals(RuleUpdateCheck(now.toEpochMilli(), result), env.settings.lastCheck.value)
        }

    private companion object {
        const val PKG = "com.bank.app"
        const val OTHER_PKG = "com.other.bank"
    }
}
