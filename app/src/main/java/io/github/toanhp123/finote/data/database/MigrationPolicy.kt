package io.github.toanhp123.finote.data.database

import androidx.room3.RoomDatabase
import androidx.room3.migration.Migration

/** First version that may hold real data. Versions below it never left a developer's phone. */
private const val FIRST_KEPT_VERSION = 8

/** Hand-written migrations. Simple changes use `AutoMigration` on [FinoteDatabase] instead. */
val FINOTE_MIGRATIONS: List<Migration> = listOf(MIGRATION_8_9, MIGRATION_9_10)

/**
 * Versions below [FIRST_KEPT_VERSION] may be dropped. From it on, a missing migration or a downgrade throws and
 * keeps the file: a crash is recoverable with a fixed build, a wiped ledger is not.
 */
fun RoomDatabase.Builder<FinoteDatabase>.applyMigrationPolicy(): RoomDatabase.Builder<FinoteDatabase> =
    apply {
        // One call per item: Room's builder accumulates both lists, and a loop avoids a vararg spread.
        FINOTE_MIGRATIONS.forEach { addMigrations(it) }
        for (version in 1 until FIRST_KEPT_VERSION) fallbackToDestructiveMigrationFrom(dropAllTables = true, version)
    }
