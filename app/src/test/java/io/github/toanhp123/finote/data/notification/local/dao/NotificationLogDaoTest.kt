package io.github.toanhp123.finote.data.notification.local.dao

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.toanhp123.finote.data.database.InMemoryDatabaseRule
import io.github.toanhp123.finote.data.notification.notificationLogEntity
import io.github.toanhp123.finote.domain.notification.model.NotificationStatus
import kotlinx.coroutines.flow.first
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
    fun `getReparseCandidates returns no_rule_matched and keyword-hidden logs oldest first`() =
        runTest {
            val base = notificationLogEntity(id = "a", contentHash = "a", status = NotificationStatus.FAILED)
            dao.insert(base.copy(failureReason = "no_rule_matched", receivedAt = 3L))
            dao.insert(base.copy(id = "b", contentHash = "b", failureReason = "account_archived"))
            val hidden = base.copy(status = NotificationStatus.IGNORED_SPAM, receivedAt = 2L)
            dao.insert(hidden.copy(id = "c", contentHash = "c", failureReason = "user_keyword:moi ban"))
            dao.insert(hidden.copy(id = "d", contentHash = "d", failureReason = "keyword:x"))
            dao.insert(hidden.copy(id = "e", contentHash = "e", failureReason = null))
            dao.insert(base.copy(id = "f", contentHash = "f", failureReason = "no_rule_matched", packageName = "other"))

            assertEquals(listOf("c", "a"), dao.getReparseCandidates(setOf("com.test.bank")).map { it.id })
        }

    @Test
    fun `updateFailedOutcome guard accepts no_rule_matched and keyword-hidden rows only`() =
        runTest {
            val base = notificationLogEntity(id = "a", contentHash = "a", status = NotificationStatus.FAILED)
            dao.insert(base.copy(failureReason = "no_rule_matched"))
            dao.insert(base.copy(id = "b", contentHash = "b", failureReason = "account_archived"))
            val hidden = base.copy(status = NotificationStatus.IGNORED_SPAM)
            dao.insert(hidden.copy(id = "c", contentHash = "c", failureReason = "user_keyword:moi ban"))
            dao.insert(hidden.copy(id = "d", contentHash = "d", failureReason = "keyword:x"))

            val moved =
                listOf("a", "b", "c", "d").map { id ->
                    dao.updateFailedOutcome(
                        id,
                        false,
                        NotificationStatus.PENDING,
                        null,
                        "r",
                        1L,
                        "EXPENSE",
                        null,
                        null,
                        null,
                        null,
                    )
                }

            assertEquals(listOf(1, 0, 1, 0), moved)
        }

    @Test
    fun `same contentHash is ignored even with a new id`() =
        runTest {
            dao.insert(notificationLogEntity(id = "log-1", contentHash = "same"))

            assertEquals(-1L, dao.insert(notificationLogEntity(id = "log-2", contentHash = "same")))
            assertEquals(1, dao.getAll().size)
        }

    @Test
    fun `observeRecent returns newest first and stops at the limit`() =
        runTest {
            val base = notificationLogEntity()
            listOf("a" to 1L, "b" to 3L, "c" to 2L, "d" to 3L).forEach { (id, at) ->
                dao.insert(base.copy(id = id, contentHash = id, receivedAt = at))
            }

            assertEquals(listOf("d", "b", "c"), dao.observeRecent(limit = 3).first().map { it.id })
        }
}
