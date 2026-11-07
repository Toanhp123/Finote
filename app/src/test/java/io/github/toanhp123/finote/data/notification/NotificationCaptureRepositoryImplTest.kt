package io.github.toanhp123.finote.data.notification

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.toanhp123.finote.data.InMemoryDatabaseRule
import io.github.toanhp123.finote.domain.notification.model.NotificationLog
import io.github.toanhp123.finote.domain.notification.model.NotificationStatus
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
}
