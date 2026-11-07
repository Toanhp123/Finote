package io.github.toanhp123.finote.data

import android.content.Context
import androidx.room3.Room
import androidx.room3.useWriterConnection
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import io.github.toanhp123.finote.data.account.SystemAccountSeeder
import io.github.toanhp123.finote.data.category.SystemCategorySeeder
import io.github.toanhp123.finote.data.notification.ParserRuleSeeder
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/** Off by default, so tests that count rows do not depend on the seed. Seed tests and tree-rule tests turn it on. */
class InMemoryDatabaseRule(
    private val seedSystemCategories: Boolean = false,
    private val seedSystemAccount: Boolean = false,
    private val seedParserRules: Boolean = false,
    private val clock: Clock = Clock.fixed(Instant.parse("2026-11-03T00:00:00Z"), ZoneOffset.UTC),
) : TestWatcher() {
    lateinit var db: FinoteDatabase
        private set

    override fun starting(description: Description) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val builder = Room.inMemoryDatabaseBuilder<FinoteDatabase>(context).setDriver(AndroidSQLiteDriver())
        if (seedSystemCategories) builder.addCallback(SystemCategorySeeder(clock))
        if (seedSystemAccount) builder.addCallback(SystemAccountSeeder(clock))
        if (seedParserRules) builder.addCallback(ParserRuleSeeder())
        db = builder.build()
    }

    override fun finished(description: Description) {
        db.close()
    }

    /** Runs raw SQL on the writer connection. Tests use it for hard deletes, which no DAO offers. */
    suspend fun execSql(sql: String) {
        db.useWriterConnection { connection -> connection.usePrepared(sql) { it.step() } }
    }
}
