package io.github.toanhp123.finote.data.category.seed

import androidx.room3.RoomDatabase
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.execSQL
import java.time.Clock

private const val INSERT_SQL =
    "INSERT INTO categories (id, name, nameKey, icon, color, type, parentId, isSystemDefault, isHidden, " +
        "sortOrder, createdAt, updatedAt, deletedAt) VALUES (?, ?, ?, ?, ?, ?, ?, 1, 0, ?, ?, ?, NULL)"

/**
 * Writes [SystemCategories] into a database that has just been created or recreated.
 *
 * A new database seeds in [onCreate], inside the transaction that creates the schema. A version change without a
 * migration takes Room's destructive path, which drops the tables, calls `onDestructiveMigration` while they do not
 * exist yet, and recreates them without calling [onCreate]. That path seeds in [onOpen] instead, which runs right after
 * the schema is committed. [onOpen] also repairs a process that died between the two steps.
 */
class SystemCategorySeeder(
    private val clock: Clock,
) : RoomDatabase.Callback() {
    override suspend fun onCreate(connection: SQLiteConnection) = seed(connection)

    override suspend fun onOpen(connection: SQLiteConnection) {
        if (hasSystemCategories(connection)) return
        // Room runs onOpen outside a transaction, so a failed seed must not leave half of the rows behind.
        connection.execSQL("BEGIN IMMEDIATE TRANSACTION")
        var isCommitted = false
        try {
            seed(connection)
            connection.execSQL("COMMIT")
            isCommitted = true
        } finally {
            // A failing ROLLBACK must not hide the seed error that is already propagating.
            if (!isCommitted) runCatching { connection.execSQL("ROLLBACK") }
        }
    }

    private fun hasSystemCategories(connection: SQLiteConnection): Boolean =
        connection.prepare("SELECT 1 FROM categories WHERE isSystemDefault = 1 LIMIT 1").use { it.step() }

    private fun seed(connection: SQLiteConnection) {
        val now = clock.millis()
        connection.prepare(INSERT_SQL).use { statement ->
            // Parents come first in the list, so the parentId foreign key always resolves.
            SystemCategories.forEach { row ->
                statement.bindRow(row, now)
                statement.step()
                statement.reset()
            }
        }
    }

    private fun SQLiteStatement.bindRow(
        row: SystemCategory,
        now: Long,
    ) {
        var index = 0
        bindText(++index, row.id)
        bindText(++index, row.name)
        bindText(++index, row.nameKey)
        bindText(++index, row.icon)
        bindLong(++index, row.colorArgb.toLong())
        bindText(++index, row.type.name)
        val parentId = row.parentId
        if (parentId == null) bindNull(++index) else bindText(++index, parentId)
        bindLong(++index, row.sortOrder.toLong())
        bindLong(++index, now)
        bindLong(++index, now)
    }
}
