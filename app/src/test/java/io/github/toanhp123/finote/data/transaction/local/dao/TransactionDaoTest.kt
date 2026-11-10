package io.github.toanhp123.finote.data.transaction.local.dao

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.toanhp123.finote.data.database.InMemoryDatabaseRule
import io.github.toanhp123.finote.data.database.account
import io.github.toanhp123.finote.data.database.category
import io.github.toanhp123.finote.data.database.tag
import io.github.toanhp123.finote.data.database.tagRef
import io.github.toanhp123.finote.data.database.transaction
import io.github.toanhp123.finote.data.notification.notificationLogEntity
import io.github.toanhp123.finote.domain.transaction.model.AdjustmentDirection
import io.github.toanhp123.finote.domain.transaction.model.TransactionSource
import io.github.toanhp123.finote.domain.transaction.model.TransactionType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TransactionDaoTest {
    @get:Rule
    val dbRule = InMemoryDatabaseRule()

    private val dao get() = dbRule.db.transactionDao()

    private suspend fun seedAccount() = dbRule.db.accountDao().insert(account())

    private suspend fun failureMessage(block: suspend () -> Unit): String {
        val message = runCatching { block() }.exceptionOrNull()?.message
        assertNotNull(message)
        return message.orEmpty()
    }

    @Test
    fun `transaction round-trips every column`() =
        runTest {
            seedAccount()
            dbRule.db.accountDao().insert(account(id = "acc-2"))
            dbRule.db.categoryDao().insert(category(id = "cat-1"))
            val transfer = transaction(toAccountId = "acc-2", categoryId = "cat-1", type = TransactionType.TRANSFER)
            dao.insert(transfer)
            assertEquals(transfer, dao.getById("tx-1"))

            val adjustment =
                transaction(id = "tx-2", type = TransactionType.ADJUSTMENT)
                    .copy(adjustmentDirection = AdjustmentDirection.OUT, merchantName = null)
            dao.insert(adjustment)
            assertEquals(adjustment, dao.getById("tx-2"))
        }

    @Test
    fun `post balance and log link round trip`() =
        runTest {
            seedAccount()
            val log = notificationLogEntity()
            dbRule.db.notificationLogDao().insert(log)
            val tx = transaction(postBalance = 1_500_000L, notificationLogId = log.id)
            dao.insert(tx)
            assertEquals(tx, dao.getById("tx-1"))
        }

    @Test
    fun `deleting the log clears the link`() =
        runTest {
            seedAccount()
            dbRule.db.notificationLogDao().insert(notificationLogEntity(id = "log-1"))
            dao.insert(transaction(postBalance = 1_500_000L, notificationLogId = "log-1"))
            dbRule.execSql("DELETE FROM notification_logs WHERE id = 'log-1'")
            val read = dao.getById("tx-1")
            assertNull(read?.notificationLogId)
            assertEquals(1_500_000L, read?.postBalance)
        }

    @Test
    fun `insert with unknown notificationLogId fails`() =
        runTest {
            seedAccount()
            val message = failureMessage { dao.insert(transaction(notificationLogId = "missing")) }
            assertTrue(message, message.contains("FOREIGN KEY constraint failed"))
        }

    @Test
    fun `insert with unknown accountId fails`() =
        runTest {
            val message = failureMessage { dao.insert(transaction(accountId = "missing")) }
            assertTrue(message, message.contains("FOREIGN KEY constraint failed"))
        }

    @Test
    fun `insert with unknown toAccountId fails`() =
        runTest {
            seedAccount()
            val message = failureMessage { dao.insert(transaction(toAccountId = "missing")) }
            assertTrue(message, message.contains("FOREIGN KEY constraint failed"))
        }

    @Test
    fun `hard-deleting an account with transactions fails`() =
        runTest {
            seedAccount()
            dao.insert(transaction())
            val message = failureMessage { dbRule.execSql("DELETE FROM accounts WHERE id = 'acc-1'") }
            assertTrue(message, message.contains("FOREIGN KEY constraint failed"))
            assertNotNull(dbRule.db.accountDao().getById("acc-1"))
        }

    @Test
    fun `observeTransactions hides soft-deleted rows but getById returns them`() =
        runTest {
            seedAccount()
            dao.insert(transaction(id = "tx-live"))
            dao.insert(transaction(id = "tx-gone").copy(deletedAt = 9_000L))
            assertEquals(listOf("tx-live"), dao.observeTransactions(null).first().map { it.id })
            assertNotNull(dao.getById("tx-gone"))
        }

    @Test
    fun `observeTransactions orders by timestamp then createdAt`() =
        runTest {
            seedAccount()
            dao.insert(transaction(id = "tx-old", timestamp = 1_000L).copy(createdAt = 9_000L))
            dao.insert(transaction(id = "tx-tie-early", timestamp = 2_000L).copy(createdAt = 1_000L))
            dao.insert(transaction(id = "tx-tie-late", timestamp = 2_000L).copy(createdAt = 2_000L))
            val ids = dao.observeTransactions(null).first().map { it.id }
            assertEquals(listOf("tx-tie-late", "tx-tie-early", "tx-old"), ids)
        }

    @Test
    fun `observeTransactions breaks ties by id`() =
        runTest {
            seedAccount()
            dao.insert(transaction(id = "t-old", timestamp = 1_000L))
            dao.insert(transaction(id = "t-a", timestamp = 2_000L))
            dao.insert(transaction(id = "t-b", timestamp = 2_000L))
            assertEquals(listOf("t-b", "t-a", "t-old"), dao.observeTransactions(null).first().map { it.id })
        }

    @Test
    fun `observeTransactions by account returns source rows and incoming transfers`() =
        runTest {
            seedAccount()
            dbRule.db.accountDao().insert(account(id = "acc-2"))
            dao.insert(transaction(id = "tx-out", accountId = "acc-1", timestamp = 1_000L))
            dao.insert(
                transaction(
                    id = "tx-in",
                    accountId = "acc-2",
                    toAccountId = "acc-1",
                    type = TransactionType.TRANSFER,
                    timestamp = 2_000L,
                ),
            )
            dao.insert(transaction(id = "tx-other", accountId = "acc-2", timestamp = 3_000L))
            assertEquals(listOf("tx-in", "tx-out"), dao.observeTransactions("acc-1").first().map { it.id })
        }

    @Test
    fun `observeTransactions by account hides soft-deleted rows`() =
        runTest {
            seedAccount()
            dao.insert(transaction(id = "tx-live"))
            dao.insert(transaction(id = "tx-gone").copy(deletedAt = 9_000L))
            assertEquals(listOf("tx-live"), dao.observeTransactions("acc-1").first().map { it.id })
        }

    @Test
    fun `observeTransactions by account orders by timestamp, createdAt, then id`() =
        runTest {
            seedAccount()
            dao.insert(transaction(id = "t-old", timestamp = 1_000L))
            dao.insert(transaction(id = "t-a", timestamp = 2_000L))
            dao.insert(transaction(id = "t-b", timestamp = 2_000L))
            dao.insert(transaction(id = "t-c", timestamp = 2_000L).copy(createdAt = 1_700_000_000_001L))
            assertEquals(
                listOf("t-c", "t-b", "t-a", "t-old"),
                dao.observeTransactions("acc-1").first().map { it.id },
            )
        }

    @Test
    fun `hard-deleting a transaction removes its tag refs`() =
        runTest {
            seedAccount()
            dao.insert(transaction())
            dbRule.db.tagDao().insert(tag())
            dbRule.db.tagDao().insertTagRef(tagRef())
            assertEquals(
                1,
                dbRule.db
                    .tagDao()
                    .getTagRefs("tx-1")
                    .size,
            )
            dbRule.execSql("DELETE FROM transactions WHERE id = 'tx-1'")
            assertTrue(
                dbRule.db
                    .tagDao()
                    .getTagRefs("tx-1")
                    .isEmpty(),
            )
        }

    @Test
    fun `deleteTagRef removes only that link`() =
        runTest {
            seedAccount()
            dao.insert(transaction())
            dbRule.db.tagDao().insert(tag(id = "tag-1"))
            dbRule.db.tagDao().insert(tag(id = "tag-2"))
            dbRule.db.tagDao().insertTagRef(tagRef(tagId = "tag-1"))
            dbRule.db.tagDao().insertTagRef(tagRef(tagId = "tag-2"))
            dbRule.db.tagDao().deleteTagRef(tagRef(tagId = "tag-1"))
            assertEquals(listOf(tagRef(tagId = "tag-2")), dbRule.db.tagDao().getTagRefs("tx-1"))
        }

    @Test
    fun `getActiveByAccount returns source and destination rows and skips deleted and others`() =
        runTest {
            seedAccount()
            dbRule.db.accountDao().insert(account(id = "acc-2"))
            dbRule.db.accountDao().insert(account(id = "acc-3"))
            dao.insert(transaction(id = "tx-out", accountId = "acc-1"))
            val incoming =
                transaction(id = "tx-in", accountId = "acc-2", toAccountId = "acc-1", type = TransactionType.TRANSFER)
            dao.insert(incoming)
            dao.insert(transaction(id = "tx-gone", accountId = "acc-1").copy(deletedAt = 9_000L))
            dao.insert(transaction(id = "tx-other", accountId = "acc-3"))
            val ids = dao.getActiveByAccount("acc-1").map { it.id }
            assertEquals(listOf("tx-in", "tx-out"), ids.sorted())
        }

    @Test
    fun `countActiveByAccount counts source and destination and skips deleted`() =
        runTest {
            seedAccount()
            dbRule.db.accountDao().insert(account(id = "acc-2"))
            dao.insert(transaction(id = "tx-exp", accountId = "acc-1"))
            dao.insert(
                transaction(
                    id = "tx-xfer",
                    accountId = "acc-1",
                    toAccountId = "acc-2",
                    type = TransactionType.TRANSFER,
                ),
            )
            assertEquals(1, dao.countActiveByAccount("acc-2"))

            dao.update(dao.getById("tx-xfer")!!.copy(deletedAt = 9_000L))
            assertEquals(0, dao.countActiveByAccount("acc-2"))
            assertEquals(1, dao.countActiveByAccount("acc-1"))
        }

    @Test
    fun `last manual account skips deleted and non manual rows`() =
        runTest {
            seedAccount()
            dbRule.db.accountDao().insert(account(id = "acc-2"))
            dbRule.db.accountDao().insert(account(id = "acc-3"))
            dao.insert(transaction(id = "tx-1", accountId = "acc-1").copy(createdAt = 1L))
            dao.insert(transaction(id = "tx-2", accountId = "acc-2").copy(createdAt = 2L, deletedAt = 9_000L))
            dao.insert(
                transaction(id = "tx-3", accountId = "acc-3")
                    .copy(createdAt = 3L, source = TransactionSource.NOTIFICATION_PARSER),
            )
            assertEquals("acc-1", dao.getLastManualAccountId())
        }

    @Test
    fun `last manual category skips null category`() =
        runTest {
            seedAccount()
            dbRule.db.categoryDao().insert(category(id = "cat-1"))
            dao.insert(transaction(id = "tx-1", categoryId = "cat-1").copy(createdAt = 1L))
            dao.insert(transaction(id = "tx-2", categoryId = null).copy(createdAt = 2L))
            assertEquals("cat-1", dao.getLastManualCategoryId(TransactionType.EXPENSE))
        }

    @Test
    fun `last manual category filters by type`() =
        runTest {
            seedAccount()
            dbRule.db.categoryDao().insert(category(id = "cat-exp"))
            dbRule.db.categoryDao().insert(category(id = "cat-inc"))
            dao.insert(
                transaction(id = "tx-1", categoryId = "cat-inc", type = TransactionType.INCOME).copy(createdAt = 1L),
            )
            dao.insert(transaction(id = "tx-2", categoryId = "cat-exp").copy(createdAt = 2L))
            assertEquals("cat-inc", dao.getLastManualCategoryId(TransactionType.INCOME))
        }

    @Test
    fun `last manual account is the newest createdAt not the last inserted`() =
        runTest {
            seedAccount()
            dbRule.db.accountDao().insert(account(id = "acc-2"))
            dao.insert(transaction(id = "tx-1", accountId = "acc-1").copy(createdAt = 5L))
            dao.insert(transaction(id = "tx-2", accountId = "acc-2").copy(createdAt = 1L))
            assertEquals("acc-1", dao.getLastManualAccountId())
        }

    @Test
    fun `last manual category is the newest createdAt not the last inserted`() =
        runTest {
            seedAccount()
            dbRule.db.categoryDao().insert(category(id = "cat-1"))
            dbRule.db.categoryDao().insert(category(id = "cat-2"))
            dao.insert(transaction(id = "tx-1", categoryId = "cat-1").copy(createdAt = 5L))
            dao.insert(transaction(id = "tx-2", categoryId = "cat-2").copy(createdAt = 1L))
            assertEquals("cat-1", dao.getLastManualCategoryId(TransactionType.EXPENSE))
        }

    @Test
    fun `last manual queries return null on empty table`() =
        runTest {
            assertNull(dao.getLastManualAccountId())
            assertNull(dao.getLastManualCategoryId(TransactionType.EXPENSE))
        }
}
