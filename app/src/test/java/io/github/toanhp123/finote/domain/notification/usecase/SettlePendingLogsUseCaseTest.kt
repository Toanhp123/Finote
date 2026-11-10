package io.github.toanhp123.finote.domain.notification.usecase

import io.github.toanhp123.finote.domain.account.model.Account
import io.github.toanhp123.finote.domain.account.model.AccountType
import io.github.toanhp123.finote.domain.account.testAccount
import io.github.toanhp123.finote.domain.notification.model.NotificationLog
import io.github.toanhp123.finote.domain.notification.model.NotificationStatus
import io.github.toanhp123.finote.domain.notification.model.ParsedFields
import io.github.toanhp123.finote.domain.notification.testLog
import io.github.toanhp123.finote.domain.notification.testRule
import io.github.toanhp123.finote.domain.transaction.model.TransactionType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class SettlePendingLogsUseCaseTest {
    private val now = 10_000_000L
    private val old = now - 3_600_000L
    private val clock = Clock.fixed(Instant.ofEpochMilli(now), ZoneOffset.UTC)

    private fun rule(pkg: String) =
        testRule(
            id = "r-$pkg",
            packageName = pkg,
            bankName = pkg,
            updatedAt = 1L,
            accountType = if (pkg == OTHER) AccountType.WALLET else null,
        )

    private fun log(
        id: String,
        pkg: String,
        postTime: Long,
        amount: Long = 50_000L,
        text: String = id,
    ) = testLog(
        id = id,
        packageName = pkg,
        title = text,
        content = text,
        postTime = postTime,
        matchedRuleId = "r-$pkg",
        parsed = ParsedFields(amount, TransactionType.EXPENSE, null, null, null, null),
    )

    private fun repo(
        vararg logs: NotificationLog,
        accounts: List<Account> = emptyList(),
    ) = FakeNotificationSettlementRepository(
        logs = logs.toMutableList(),
        accounts = accounts,
        rules = listOf(BANK, OTHER).associate { "r-$it" to rule(it) },
    )

    private fun useCase(repository: FakeNotificationSettlementRepository) = SettlePendingLogsUseCase(repository, clock)

    @Test
    fun `settle books due logs with no scheduler involvement`() =
        runTest {
            val repository = repo(log("a", BANK, old))
            assertFalse(useCase(repository)())
            assertEquals(setOf("a"), repository.booked.keys)
            assertEquals(listOf(now), repository.bookTimes)
            assertEquals(NotificationStatus.CONFIRMED, repository.logs.single().status)
        }

    @Test
    fun `repost in the same run links to the new transaction`() =
        runTest {
            val repository = repo(log("a", BANK, old, text = "same"), log("b", BANK, old + 1_000, text = "same"))
            assertFalse(useCase(repository)())
            assertNotNull(repository.booked["a"])
            assertEquals(repository.booked["a"], repository.reposted["b"])
            assertEquals(NotificationStatus.DEDUPLICATED, repository.logs.first { it.id == "b" }.status)
        }

    @Test
    fun `repost of a paired wallet log links to the booked transaction`() =
        runTest {
            val repository =
                repo(
                    log("bank", BANK, old, text = "x"),
                    log("wallet", OTHER, old + 1_000, text = "w"),
                    log("again", OTHER, old + 2_000, text = "w"),
                )
            assertFalse(useCase(repository)())
            assertNotNull(repository.booked["bank"])
            assertEquals(repository.booked["bank"], repository.reposted["again"])
        }

    @Test
    fun `repost of a failed original is still marked with no link`() =
        runTest {
            val repository =
                repo(
                    log("a", BANK, old, amount = 0L, text = "same"),
                    log("b", BANK, old + 1_000, amount = 0L, text = "same"),
                )
            assertFalse(useCase(repository)())
            assertTrue("a" in repository.failed)
            assertTrue("b" in repository.reposted)
            assertNull(repository.reposted["b"])
        }

    @Test
    fun `Ignore calls ignore and marks both written`() =
        runTest {
            val ignoredBank = testAccount(id = "ign").copy(bankPackageName = BANK, isIgnored = true)
            val repository =
                repo(
                    log("bank", BANK, old, text = "x"),
                    log("wallet", OTHER, old + 1_000, text = "w"),
                    log("again", OTHER, old + 2_000, text = "w"),
                    accounts = listOf(ignoredBank),
                )
            assertFalse(useCase(repository)())
            assertEquals(listOf("bank", "wallet"), repository.ignored)
            assertTrue(repository.booked.isEmpty())
            assertEquals(NotificationStatus.IGNORED_ACCOUNT, repository.logs.first { it.id == "bank" }.status)
            assertEquals(NotificationStatus.IGNORED_ACCOUNT, repository.logs.first { it.id == "wallet" }.status)
            // The repost of the paired wallet log is written because both ids count as written.
            assertTrue("again" in repository.reposted)
            assertNull(repository.reposted["again"])
        }

    @Test
    fun `a throwing book does not stop the next decision`() =
        runTest {
            val repository = repo(log("a", BANK, old), log("b", BANK, old + 60_000, text = "other"))
            repository.bookErrors["a"] = IllegalStateException()
            assertTrue(useCase(repository)())
            assertEquals(setOf("b"), repository.booked.keys)
            assertEquals(NotificationStatus.PENDING, repository.logs.first { it.id == "a" }.status)
        }

    @Test
    fun `a throwing book older than one hour fails the pair with settle_error`() =
        runTest {
            val stuck = old - 1
            val repository = repo(log("bank", BANK, stuck, text = "x"), log("wallet", OTHER, stuck + 1_000, text = "w"))
            repository.bookErrors["bank"] = IllegalStateException()
            assertFalse(useCase(repository)())
            assertEquals(mapOf("bank" to "settle_error"), repository.failed)
            assertTrue(
                repository.logs.all {
                    it.status == NotificationStatus.FAILED &&
                        it.failureReason == "settle_error"
                },
            )
        }

    @Test
    fun `a throwing book of a young log stays pending`() =
        runTest {
            val repository = repo(log("a", BANK, old))
            repository.bookErrors["a"] = IllegalStateException()
            assertTrue(useCase(repository)())
            assertTrue(repository.failed.isEmpty())
        }

    @Test
    fun `a failing fallback leaves the log pending and later decisions still run`() =
        runTest {
            val repository = repo(log("a", BANK, old - 1), log("b", BANK, old + 60_000, text = "other"))
            repository.bookErrors["a"] = IllegalStateException()
            repository.failErrors["a"] = IllegalStateException()
            assertTrue(useCase(repository)())
            assertEquals(NotificationStatus.PENDING, repository.logs.first { it.id == "a" }.status)
            assertEquals(setOf("b"), repository.booked.keys)
        }

    @Test
    fun `repost of an original whose book threw stays pending`() =
        runTest {
            val repository = repo(log("a", BANK, old, text = "same"), log("b", BANK, old + 1_000, text = "same"))
            repository.bookErrors["a"] = IllegalStateException()
            assertTrue(useCase(repository)())
            assertTrue(repository.reposted.isEmpty())
            assertTrue(repository.logs.all { it.status == NotificationStatus.PENDING })
        }

    @Test
    fun `returns false when nothing is left`() = runTest { assertFalse(useCase(repo())()) }

    @Test
    fun `returns true when a log is not due`() =
        runTest {
            val repository = repo(log("a", BANK, now - 1_000))
            assertTrue(useCase(repository)())
            assertTrue(repository.booked.isEmpty())
        }

    @Test
    fun `cancellation propagates`() =
        runTest {
            val repository = repo(log("a", BANK, old), log("b", BANK, old + 60_000, text = "other"))
            repository.bookErrors["a"] = CancellationException("stop")
            try {
                useCase(repository)()
                fail("expected CancellationException")
            } catch (_: CancellationException) {
                assertTrue(repository.booked.isEmpty())
            }
        }

    private companion object {
        const val BANK = "com.bank"
        const val OTHER = "com.other"
    }
}
