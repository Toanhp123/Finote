package io.github.toanhp123.finote.data.notification.local.dao

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.toanhp123.finote.data.database.InMemoryDatabaseRule
import io.github.toanhp123.finote.data.notification.notificationLogEntity
import io.github.toanhp123.finote.domain.notification.model.NotificationStatus
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotificationLogDaoTest {
    @get:Rule
    val dbRule = InMemoryDatabaseRule()

    private val dao get() = dbRule.db.notificationLogDao()

    @Test
    fun `insert returns a row id and round-trips the row`() =
        runTest {
            val log = notificationLogEntity()

            assertTrue(dao.insert(log) > 0)
            assertEquals(listOf(log), dao.getAll())
        }

    @Test
    fun `getFailed returns only logs no rule matched`() =
        runTest {
            val noRule = notificationLogEntity(id = "a", contentHash = "a", status = NotificationStatus.FAILED)
            dao.insert(noRule.copy(failureReason = "no_rule_matched"))
            dao.insert(noRule.copy(id = "b", contentHash = "b", failureReason = "account_archived"))

            assertEquals(listOf("a"), dao.getFailed(setOf("com.test.bank")).map { it.id })
        }

    @Test
    fun `same contentHash is ignored even with a new id`() =
        runTest {
            dao.insert(notificationLogEntity(id = "log-1", contentHash = "same"))

            assertEquals(-1L, dao.insert(notificationLogEntity(id = "log-2", contentHash = "same")))
            assertEquals(1, dao.getAll().size)
        }
}
