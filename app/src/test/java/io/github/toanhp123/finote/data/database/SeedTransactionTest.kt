package io.github.toanhp123.finote.data.database

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Room's `useWriterConnection` hands out a `Transactor`, not a raw connection, so the test opens one directly. */
@RunWith(AndroidJUnit4::class)
class SeedTransactionTest {
    private lateinit var connection: SQLiteConnection

    @Before
    fun setUp() {
        connection = AndroidSQLiteDriver().open(":memory:")
        connection.execSQL("CREATE TABLE categories (id TEXT NOT NULL PRIMARY KEY)")
    }

    @After
    fun tearDown() {
        connection.close()
    }

    @Test
    fun `commits on success`() {
        connection.immediateTransaction { connection.execSQL("INSERT INTO categories (id) VALUES ('c1')") }
        assertEquals(1, countCategories())
    }

    @Test
    fun `rolls back on failure`() {
        val thrown =
            runCatching {
                connection.immediateTransaction {
                    connection.execSQL("INSERT INTO categories (id) VALUES ('c1')")
                    error("boom")
                }
            }.exceptionOrNull()
        assertEquals("boom", thrown?.message)
        assertEquals(0, countCategories())
    }

    private fun countCategories(): Int =
        connection.prepare("SELECT COUNT(*) FROM categories").use { statement ->
            statement.step()
            statement.getLong(0).toInt()
        }
}
