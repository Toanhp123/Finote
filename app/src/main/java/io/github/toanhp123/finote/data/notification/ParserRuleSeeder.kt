package io.github.toanhp123.finote.data.notification

import androidx.room3.RoomDatabase
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.execSQL

private const val UPSERT_SQL =
    "INSERT OR REPLACE INTO parser_rules (id, packageName, bankName, channelIdAllowlist, ignoreKeywords, " +
        "priority, isUserCustom, version) VALUES (?, ?, ?, ?, ?, ?, ?, ?)"

/**
 * Keeps [SystemParserRules] in the database, rule by rule. Unlike the category seeder it runs on every open:
 * a rule is written when it is missing, or when the stored copy is a system rule with a lower version. A user rule
 * and a rule of the same or a newer version stay as they are. That lets a keyword fix ship by raising a version.
 */
class ParserRuleSeeder : RoomDatabase.Callback() {
    override suspend fun onCreate(connection: SQLiteConnection) = write(connection, SystemParserRules)

    override suspend fun onOpen(connection: SQLiteConnection) {
        val stored = storedRules(connection)
        val outdated =
            SystemParserRules.filter { seed ->
                val old = stored[seed.id]
                old == null || (!old.isUserCustom && old.version < seed.version)
            }
        if (outdated.isEmpty()) return
        // Room runs onOpen outside a transaction, so a failed seed must not leave half of the rows behind.
        connection.execSQL("BEGIN IMMEDIATE TRANSACTION")
        var isCommitted = false
        try {
            write(connection, outdated)
            connection.execSQL("COMMIT")
            isCommitted = true
        } finally {
            // A failing ROLLBACK must not hide the seed error that is already propagating.
            if (!isCommitted) runCatching { connection.execSQL("ROLLBACK") }
        }
    }

    private class Stored(
        val version: Int,
        val isUserCustom: Boolean,
    )

    private fun storedRules(connection: SQLiteConnection): Map<String, Stored> =
        connection.prepare("SELECT id, version, isUserCustom FROM parser_rules").use { statement ->
            buildMap {
                while (statement.step()) {
                    put(statement.getText(0), Stored(statement.getLong(1).toInt(), statement.getLong(2) != 0L))
                }
            }
        }

    private fun write(
        connection: SQLiteConnection,
        rules: List<ParserRuleEntity>,
    ) {
        connection.prepare(UPSERT_SQL).use { statement ->
            rules.forEach { rule ->
                statement.bindRule(rule)
                statement.step()
                statement.reset()
            }
        }
    }

    private fun SQLiteStatement.bindRule(rule: ParserRuleEntity) {
        var index = 0
        bindText(++index, rule.id)
        bindText(++index, rule.packageName)
        bindText(++index, rule.bankName)
        val allowlist = rule.channelIdAllowlist
        if (allowlist == null) bindNull(++index) else bindText(++index, allowlist)
        bindText(++index, rule.ignoreKeywords)
        bindLong(++index, rule.priority.toLong())
        bindLong(++index, if (rule.isUserCustom) 1L else 0L)
        bindLong(++index, rule.version.toLong())
    }
}
