package io.github.toanhp123.finote.data.database

import android.database.SQLException
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.toanhp123.finote.domain.common.StorageException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StorageCallsTest {
    @get:Rule
    val dbRule = InMemoryDatabaseRule()

    @Test
    fun `storage maps SQLException`() =
        runTest {
            val db = dbRule.db
            dbRule.execSql("DROP TABLE accounts")
            val thrown =
                runCatching { storage { db.accountDao().getById("a") } }.exceptionOrNull()
            assertTrue(thrown is StorageException)
            assertTrue(thrown?.cause is SQLException)
        }

    @Test
    fun `storage lets other exceptions through`() =
        runTest {
            val thrown = runCatching { storage { error("boom") } }.exceptionOrNull()
            assertTrue(thrown is IllegalStateException)
            assertEquals("boom", thrown?.message)
        }

    @Test
    fun `storageWrite rolls back on failure`() =
        runTest {
            val db = dbRule.db
            runCatching {
                db.storageWrite {
                    db.accountDao().insert(account())
                    error("boom")
                }
            }
            assertNull(db.accountDao().getById("acc-1"))
        }

    @Test
    fun `flow maps SQLException`() =
        runTest {
            val db = dbRule.db
            dbRule.execSql("DROP TABLE accounts")
            val thrown =
                runCatching {
                    db
                        .accountDao()
                        .observeAccounts()
                        .mapStorageErrors()
                        .first()
                }.exceptionOrNull()
            assertTrue(thrown is StorageException)
        }
}
