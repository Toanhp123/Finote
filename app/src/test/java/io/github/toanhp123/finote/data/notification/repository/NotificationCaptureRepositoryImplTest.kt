package io.github.toanhp123.finote.data.notification.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.toanhp123.finote.data.database.InMemoryDatabaseRule
import io.github.toanhp123.finote.data.notification.notificationLogEntity
import io.github.toanhp123.finote.data.notification.parserRuleEntity
import io.github.toanhp123.finote.domain.notification.model.Classification
import io.github.toanhp123.finote.domain.notification.model.NotificationLog
import io.github.toanhp123.finote.domain.notification.model.NotificationStatus
import io.github.toanhp123.finote.domain.notification.model.ParseResult
import io.github.toanhp123.finote.domain.notification.model.ParsedFields
import io.github.toanhp123.finote.domain.transaction.model.TransactionType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotificationCaptureRepositoryImplTest {
    @get:Rule
    val dbRule = InMemoryDatabaseRule()

    private lateinit var repo: NotificationCaptureRepositoryImpl

    @Before
    fun setUp() {
        repo = NotificationCaptureRepositoryImpl(dbRule.db)
    }

    private fun log(
        id: String,
        hash: String,
    ) = NotificationLog(id, "com.test.bank", null, "t", "c", hash, 1L, 2L, NotificationStatus.PENDING, null)

    @Test
    fun `insertLog is true once and false for the same hash`() =
        runTest {
            assertTrue(repo.insertLog(log("a", "h")))
            assertFalse(repo.insertLog(log("b", "h")))
            assertEquals(
                1,
                dbRule.db
                    .notificationLogDao()
                    .getAll()
                    .size,
            )
        }

    @Test
    fun `getRules returns one package sorted by priority then id`() =
        runTest {
            val dao = dbRule.db.parserRuleDao()
            dao.insert(parserRuleEntity(id = "c", priority = 2))
            dao.insert(parserRuleEntity(id = "b", priority = 1))
            dao.insert(parserRuleEntity(id = "a", priority = 1))
            dao.insert(parserRuleEntity(id = "other", packageName = "com.other"))

            assertEquals(listOf("a", "b", "c"), repo.getRules("com.test.bank").map { it.id })
        }

    @Test
    fun `a broken rule does not hide the other rules of the package`() =
        runTest {
            val dao = dbRule.db.parserRuleDao()
            dao.insert(parserRuleEntity(id = "bad", ignoreKeywords = "x"))
            dao.insert(parserRuleEntity(id = "good"))

            assertEquals(listOf("good"), repo.getRules("com.test.bank").map { it.id })
        }

    private suspend fun seedLog(
        id: String,
        status: NotificationStatus,
        packageName: String = "com.test.bank",
    ) = dbRule.db.notificationLogDao().insert(
        notificationLogEntity(id = id, contentHash = "h-$id", status = status).copy(
            packageName = packageName,
            failureReason = if (status == NotificationStatus.FAILED) "no_rule_matched" else null,
        ),
    )

    private suspend fun storedLog(id: String) =
        dbRule.db
            .notificationLogDao()
            .getAll()
            .single { it.id == id }

    @Test
    fun `getFailedLogs returns only FAILED rows of the given packages`() =
        runTest {
            seedLog("failed", NotificationStatus.FAILED)
            seedLog("pending", NotificationStatus.PENDING)
            seedLog("other", NotificationStatus.FAILED, packageName = "com.other")

            assertEquals(listOf("failed"), repo.getFailedLogs(setOf("com.test.bank")).map { it.id })
            assertTrue(repo.getFailedLogs(emptySet()).isEmpty())
        }

    @Test
    fun `updateFailedOutcome moves FAILED to PENDING with parsed fields`() =
        runTest {
            seedLog("a", NotificationStatus.FAILED)
            val parsed = ParsedFields(1_500_000L, TransactionType.EXPENSE, "1234", 9L, "Shop", "note")

            val changed =
                repo.updateFailedOutcome(
                    "a",
                    Classification(NotificationStatus.PENDING, null, ParseResult.Parsed("r", parsed), false),
                )

            assertTrue(changed)
            val row = storedLog("a")
            assertEquals(NotificationStatus.PENDING, row.status)
            assertEquals("r", row.matchedRuleId)
            assertEquals(1_500_000L, row.parsedAmount)
            assertEquals("EXPENSE", row.parsedType)
            assertEquals("1234", row.parsedAccountMask)
            assertEquals(9L, row.parsedPostBalance)
            assertEquals("Shop", row.parsedMerchant)
            assertEquals("note", row.parsedNote)
        }

    @Test
    fun `updateFailedOutcome keeps the text of a keyword block`() =
        runTest {
            seedLog("a", NotificationStatus.FAILED)

            repo.updateFailedOutcome("a", Classification(NotificationStatus.IGNORED_SPAM, "keyword:x", null, false))

            val row = storedLog("a")
            assertEquals(NotificationStatus.IGNORED_SPAM, row.status)
            assertEquals("keyword:x", row.failureReason)
            assertEquals("content", row.content)
            assertEquals(null, row.parsedAmount)
        }

    @Test
    fun `updateFailedOutcome blanks title and content of a secret outcome`() =
        runTest {
            seedLog("a", NotificationStatus.FAILED)

            repo.updateFailedOutcome("a", Classification(NotificationStatus.IGNORED_SPAM, "secret:OTP", null, true))

            val row = storedLog("a")
            assertEquals(NotificationStatus.IGNORED_SPAM, row.status)
            assertEquals("secret:OTP", row.failureReason)
            assertEquals("", row.title)
            assertEquals("", row.content)
        }

    @Test
    fun `updateFailedOutcome ignores a row that is no longer FAILED`() =
        runTest {
            seedLog("a", NotificationStatus.CONFIRMED)

            val changed =
                repo.updateFailedOutcome("a", Classification(NotificationStatus.PENDING, null, null, false))

            assertFalse(changed)
            assertEquals(NotificationStatus.CONFIRMED, storedLog("a").status)
        }

    @Test
    fun `parsed fields round trip`() =
        runTest {
            val parsed = ParsedFields(1_500_000L, TransactionType.EXPENSE, "1234", 0L, "Shop", "note")
            repo.insertLog(log("a", "h").copy(matchedRuleId = "r", parsed = parsed))

            val stored =
                dbRule.db
                    .notificationLogDao()
                    .getAll()
                    .single()
            assertEquals("r", stored.matchedRuleId)
            assertEquals(1_500_000L, stored.parsedAmount)
            assertEquals("EXPENSE", stored.parsedType)
            assertEquals("1234", stored.parsedAccountMask)
            assertEquals(0L, stored.parsedPostBalance)
            assertEquals("Shop", stored.parsedMerchant)
            assertEquals("note", stored.parsedNote)
        }
}
