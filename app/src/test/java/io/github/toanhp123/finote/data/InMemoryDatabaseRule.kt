package io.github.toanhp123.finote.data

import android.content.Context
import androidx.room3.Room
import androidx.room3.useWriterConnection
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import org.junit.rules.TestWatcher
import org.junit.runner.Description

class InMemoryDatabaseRule : TestWatcher() {
    lateinit var db: FinoteDatabase
        private set

    override fun starting(description: Description) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db =
            Room
                .inMemoryDatabaseBuilder<FinoteDatabase>(context)
                .setDriver(AndroidSQLiteDriver())
                .build()
    }

    override fun finished(description: Description) {
        db.close()
    }

    /** Runs raw SQL on the writer connection. Tests use it for hard deletes, which no DAO offers. */
    suspend fun execSql(sql: String) {
        db.useWriterConnection { connection -> connection.usePrepared(sql) { it.step() } }
    }
}
