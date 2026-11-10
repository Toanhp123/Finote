package io.github.toanhp123.finote.data.database

import androidx.room3.withWriteTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FinoteDatabaseTransactionTest {
    @get:Rule
    val dbRule = InMemoryDatabaseRule()

    @Test
    fun `failed write transaction leaves nothing behind`() =
        runTest {
            val db = dbRule.db
            val result =
                runCatching {
                    db.withWriteTransaction {
                        db.accountDao().insert(account())
                        error("boom")
                    }
                }
            assertTrue(result.isFailure)
            assertEquals("boom", result.exceptionOrNull()?.message)
            assertNull(db.accountDao().getById("acc-1"))
        }
}
