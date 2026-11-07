package io.github.toanhp123.finote.data.notification

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.toanhp123.finote.data.InMemoryDatabaseRule
import io.github.toanhp123.finote.domain.notification.model.NotificationLog
import io.github.toanhp123.finote.domain.notification.model.NotificationStatus
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
