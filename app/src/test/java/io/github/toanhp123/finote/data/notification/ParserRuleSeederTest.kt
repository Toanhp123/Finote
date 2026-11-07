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

    private val seedIds = SystemParserRules.map { it.id }.toSet()
    private val packages = SystemParserRules.map { it.packageName }.distinct()

    private fun seed(id: String) = SystemParserRules.first { it.id == id }

    @Test
    fun `new database holds exactly the seed regex rules`() =
        runTest {
            val dao = dbRule.db.parserRuleDao()
            val stored = packages.flatMap { dao.getByPackage(it) }

            assertEquals(seedIds, stored.map { it.id }.toSet())
            assertEquals(SystemParserRules.size, stored.size)
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
                    dao.insert(seed("vcb_account_v1").copy(version = 0, ignoreKeywords = "[\"old\"]"))
                    dao.insert(
                        seed("mb_account_v1").copy(version = 0, ignoreKeywords = "[\"mine\"]", isUserCustom = true),
                    )
                    dao.insert(seed("momo_pay_v1").copy(ignoreKeywords = "[\"same\"]"))
                    db.useWriterConnection {
                        it.usePrepared(
                            "DELETE FROM parser_rules WHERE id = 'tcb_account_v1'",
                        ) { s -> s.step() }
                    }
                }

                withDb(context, file) { db ->
                    val rows = packages.flatMap { db.parserRuleDao().getByPackage(it) }
                    val byId = rows.associateBy { it.id }

                    assertEquals(seedIds, byId.keys)
                    assertEquals(SystemParserRules.size, rows.size)
                    assertEquals(seed("vcb_account_v1"), byId.getValue("vcb_account_v1"))
                    assertEquals("[\"mine\"]", byId.getValue("mb_account_v1").ignoreKeywords)
                    assertEquals(0, byId.getValue("mb_account_v1").version)
                    assertEquals("[\"same\"]", byId.getValue("momo_pay_v1").ignoreKeywords)
                    assertEquals(seed("tcb_account_v1"), byId.getValue("tcb_account_v1"))
                }
            } finally {
                file.delete()
            }
        }

    @Test
    fun `outdatedRules keeps user rules and same versions`() {
        val vcb = seed("vcb_account_v1")
        val mb = seed("mb_account_v1")
        val momo = seed("momo_pay_v1")
        val tcb = seed("tcb_account_v1")
        val stored =
            mapOf(
                vcb.id to StoredRule(version = vcb.version - 1, isUserCustom = false),
                mb.id to StoredRule(version = mb.version - 1, isUserCustom = true),
                momo.id to StoredRule(version = momo.version, isUserCustom = false),
            )

        val outdated = outdatedRules(stored, listOf(vcb, mb, momo, tcb))

        assertEquals(listOf(vcb.id, tcb.id), outdated.map { it.id })
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
