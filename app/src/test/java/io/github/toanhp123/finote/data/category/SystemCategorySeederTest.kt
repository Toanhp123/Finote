package io.github.toanhp123.finote.data.category

import android.content.Context
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.toanhp123.finote.data.FinoteDatabase
import io.github.toanhp123.finote.data.InMemoryDatabaseRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock

@RunWith(AndroidJUnit4::class)
class SystemCategorySeederTest {
    @get:Rule
    val dbRule = InMemoryDatabaseRule(seedSystemCategories = true)

    @Test
    fun `new database holds every system category`() =
        runTest {
            val rows =
                dbRule.db
                    .categoryDao()
                    .observeCategories()
                    .first()
            assertEquals(SystemCategories.map { it.id }.toSet(), rows.map { it.id }.toSet())
            assertTrue(rows.all { it.isSystemDefault && !it.isHidden && it.deletedAt == null && it.nameKey == it.id })
            assertTrue(rows.all { it.createdAt == SEED_TIME && it.updatedAt == it.createdAt })
        }

    @Test
    fun `seeded rows match the seed data`() =
        runTest {
            val rows =
                dbRule.db
                    .categoryDao()
                    .observeCategories()
                    .first()
                    .associateBy { it.id }
            SystemCategories.forEach { seed ->
                val row = rows.getValue(seed.id)
                assertEquals(seed.name, row.name)
                assertEquals(seed.icon, row.icon)
                assertEquals(seed.colorArgb, row.color)
                assertEquals(seed.type, row.type)
                assertEquals(seed.parentId, row.parentId)
                assertEquals(seed.sortOrder, row.sortOrder)
            }
        }

    @Test
    fun `destructive migration from an old version seeds again`() =
        runTest {
            // user_version = 1 and no tables: Room sees 1 != 2, has no migration, drops and recreates.
            val context = ApplicationProvider.getApplicationContext<Context>()
            val file =
                context.getDatabasePath("seed-migration.db").apply {
                    parentFile?.mkdirs()
                    delete()
                }
            AndroidSQLiteDriver().open(file.path).apply {
                execSQL("PRAGMA user_version = 1")
                close()
            }
            val db =
                Room
                    .databaseBuilder<FinoteDatabase>(context, file.path)
                    .setDriver(AndroidSQLiteDriver())
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .addCallback(SystemCategorySeeder(Clock.systemUTC()))
                    .build()
            try {
                assertEquals(
                    SystemCategories.size,
                    db
                        .categoryDao()
                        .observeCategories()
                        .first()
                        .size,
                )
            } finally {
                db.close()
                file.delete()
            }
        }

    private companion object {
        // 2026-11-03T00:00:00Z, the fixed clock of InMemoryDatabaseRule.
        const val SEED_TIME = 1_793_664_000_000L
    }
}
