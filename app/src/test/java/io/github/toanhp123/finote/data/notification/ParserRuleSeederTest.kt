package io.github.toanhp123.finote.data.notification

import android.content.Context
import androidx.room3.Room
import androidx.room3.useWriterConnection
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.toanhp123.finote.data.FinoteDatabase
import io.github.toanhp123.finote.data.InMemoryDatabaseRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ParserRuleSeederTest {
    @get:Rule
    val dbRule = InMemoryDatabaseRule(seedParserRules = true)

    private val seedIds = setOf("vcb_filter_v1", "mb_filter_v1", "momo_filter_v1", "tcb_filter_v1")

    private fun seed(id: String) = SystemParserRules.first { it.id == id }

    @Test
    fun `new database holds exactly the four filter rules`() =
        runTest {
            val dao = dbRule.db.parserRuleDao()
            val stored = SystemParserRules.flatMap { dao.getByPackage(it.packageName) }

            assertEquals(seedIds, stored.map { it.id }.toSet())
            assertEquals(4, stored.size)
            assertEquals(SystemParserRules.toSet(), stored.toSet())
        }

    @Test
    fun `reopening keeps one row per rule and repairs only what is allowed`() =
        runTest {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val file = context.getDatabasePath("parser-seed.db").apply { parentFile?.mkdirs() }
            file.delete()
            try {
                withDb(context, file) { db ->
                    val dao = db.parserRuleDao()
                    // Older system rule: overwritten. Older user rule: kept. Equal version: kept.
                    dao.insert(seed("vcb_filter_v1").copy(version = 0, ignoreKeywords = "[\"old\"]"))
                    dao.insert(
                        seed("mb_filter_v1").copy(version = 0, ignoreKeywords = "[\"mine\"]", isUserCustom = true),
                    )
                    dao.insert(seed("momo_filter_v1").copy(ignoreKeywords = "[\"same\"]"))
                    db.useWriterConnection {
                        it.usePrepared(
                            "DELETE FROM parser_rules WHERE id = 'tcb_filter_v1'",
                        ) { s -> s.step() }
                    }
                }

                withDb(context, file) { db ->
                    val rows = SystemParserRules.map { it.packageName }.flatMap { db.parserRuleDao().getByPackage(it) }
                    val byId = rows.associateBy { it.id }

                    assertEquals(seedIds, byId.keys)
                    assertEquals(4, rows.size)
                    assertEquals(seed("vcb_filter_v1"), byId.getValue("vcb_filter_v1"))
                    assertEquals("[\"mine\"]", byId.getValue("mb_filter_v1").ignoreKeywords)
                    assertEquals(0, byId.getValue("mb_filter_v1").version)
                    assertEquals("[\"same\"]", byId.getValue("momo_filter_v1").ignoreKeywords)
                    assertEquals(seed("tcb_filter_v1"), byId.getValue("tcb_filter_v1"))
                }
            } finally {
                file.delete()
            }
        }

    /** Opens the file database with the seeder, runs [block], closes it. A second call is a reopen. */
    private suspend fun withDb(
        context: Context,
        file: File,
        block: suspend (FinoteDatabase) -> Unit,
    ) {
        val db =
            Room
                .databaseBuilder<FinoteDatabase>(context, file.path)
                .setDriver(AndroidSQLiteDriver())
                .addCallback(ParserRuleSeeder())
                .build()
        try {
            block(db)
        } finally {
            db.close()
        }
    }
}
