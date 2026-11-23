package io.github.toanhp123.finote.data.database

import android.database.SQLException
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.toanhp123.finote.domain.common.Outcome
import io.github.toanhp123.finote.domain.common.StorageException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Test 4 checks that an inner [storageWrite] joins the outer `write` transaction on Room 3,
 * so no `inTransaction()` guard is needed in [storageWrite].
 */
@RunWith(AndroidJUnit4::class)
class RoomTransactionRunnerTest {
    @get:Rule
    val dbRule = InMemoryDatabaseRule()

    private val runner by lazy { RoomTransactionRunner(dbRule.db) }

    @Test
    fun `err rolls back writes`() =
        runTest {
            val db = dbRule.db
            val result =
                runner.write<Unit, String> {
                    db.accountDao().insert(account())
                    Outcome.Err("no")
                }
            assertEquals(Outcome.Err("no"), result)
            assertNull(db.accountDao().getById("acc-1"))
        }

    @Test
    fun `exception rolls back and is rethrown`() =
        runTest {
            val db = dbRule.db
            val thrown =
                runCatching {
                    runner.write<Unit, String> {
                        db.accountDao().insert(account())
                        error("boom")
                    }
                }.exceptionOrNull()
            assertTrue(thrown is IllegalStateException)
            assertEquals("boom", thrown?.message)
            assertNull(db.accountDao().getById("acc-1"))
        }

    @Test
    fun `sql failure becomes StorageException`() =
        runTest {
            val db = dbRule.db
            val thrown =
                runCatching {
                    runner.write<Unit, String> {
                        db.accountDao().insert(account())
                        db.accountDao().insert(account())
                        Outcome.Ok(Unit)
                    }
                }.exceptionOrNull()
            assertTrue(thrown is StorageException)
            assertTrue(thrown?.cause is SQLException)
            assertNull(db.accountDao().getById("acc-1"))
        }

    @Test
    fun `inner storageWrite joins the outer transaction when outer returns Err`() =
        runTest {
            val db = dbRule.db
            val result =
                runner.write<Unit, String> {
                    db.accountDao().insert(account(id = "acc-1"))
                    db.storageWrite { db.accountDao().insert(account(id = "acc-2")) }
                    Outcome.Err("no")
                }
            assertEquals(Outcome.Err("no"), result)
            assertNull(db.accountDao().getById("acc-1"))
            assertNull(db.accountDao().getById("acc-2"))
        }

    @Test
    fun `inner storageWrite joins the outer transaction when inner throws`() =
        runTest {
            val db = dbRule.db
            val thrown =
                runCatching {
                    runner.write<Unit, String> {
                        db.accountDao().insert(account(id = "acc-1"))
                        db.storageWrite {
                            db.accountDao().insert(account(id = "acc-2"))
                            error("inner")
                        }
                    }
                }.exceptionOrNull()
            assertEquals("inner", thrown?.message)
            assertNull(db.accountDao().getById("acc-1"))
            assertNull(db.accountDao().getById("acc-2"))
        }

    @Test
    fun `ok commits`() =
        runTest {
            val db = dbRule.db
            val result =
                runner.write<Int, String> {
                    db.accountDao().insert(account())
                    Outcome.Ok(1)
                }
            assertEquals(Outcome.Ok(1), result)
            assertNotNull(db.accountDao().getById("acc-1"))
        }
}
