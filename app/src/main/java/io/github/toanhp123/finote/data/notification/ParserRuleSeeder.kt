package io.github.toanhp123.finote.data.notification

import androidx.room3.RoomDatabase
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.execSQL

private const val UPSERT_SQL =
    "INSERT OR REPLACE INTO parser_rules (id, packageName, bankName, channelIdAllowlist, ignoreKeywords, " +
        "priority, isUserCustom, version, regexPattern, fallbackTransactionType, dateFormatPattern, isActive, " +
        "updatedAt, accountType) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"

/**
 * Keeps [SystemParserRules] in the database, rule by rule. Unlike the category seeder it runs on every open:
 * a rule is written when it is missing, or when the stored copy is a system rule with a lower version. A user rule
 * and a rule of the same or a newer version stay as they are. That lets a keyword fix ship by raising a version.
 */
class ParserRuleSeeder : RoomDatabase.Callback() {
    override suspend fun onCreate(connection: SQLiteConnection) = write(connection, SystemParserRules)

    override suspend fun onOpen(connection: SQLiteConnection) {
        // Cheap read outside the write lock: the common reopen has nothing to seed and takes no lock.
        if (outdatedRules(storedRules(connection), SystemParserRules).isEmpty()) return
        // Room runs onOpen outside a transaction, so a failed seed must not leave half of the rows behind.
        connection.execSQL("BEGIN IMMEDIATE TRANSACTION")
        var isCommitted = false
        try {
            // Read again under the write lock: another connection may have seeded or edited since the check above.
            write(connection, outdatedRules(storedRules(connection), SystemParserRules))
            connection.execSQL("COMMIT")
            isCommitted = true
        } finally {
            // A failing ROLLBACK must not hide the seed error that is already propagating.
            if (!isCommitted) runCatching { connection.execSQL("ROLLBACK") }
        }
    }

    private fun storedRules(connection: SQLiteConnection): Map<String, StoredRule> =
        connection.prepare("SELECT id, version, isUserCustom FROM parser_rules").use { statement ->
            buildMap {
                while (statement.step()) {
                    put(statement.getText(0), StoredRule(statement.getLong(1).toInt(), statement.getLong(2) != 0L))
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
        bindOptionalText(++index, rule.channelIdAllowlist)
        bindText(++index, rule.ignoreKeywords)
        bindLong(++index, rule.priority.toLong())
        bindLong(++index, if (rule.isUserCustom) 1L else 0L)
        bindLong(++index, rule.version.toLong())
        bindText(++index, rule.regexPattern)
        bindOptionalText(++index, rule.fallbackTransactionType)
        bindOptionalText(++index, rule.dateFormatPattern)
        bindLong(++index, if (rule.isActive) 1L else 0L)
        bindLong(++index, rule.updatedAt)
        bindOptionalText(++index, rule.accountType)
    }

    private fun SQLiteStatement.bindOptionalText(
        index: Int,
        value: String?,
    ) {
        if (value == null) bindNull(index) else bindText(index, value)
    }
}

internal class StoredRule(
    val version: Int,
    val isUserCustom: Boolean,
)

/** Seeds that are missing, or whose stored copy is a system rule with a lower version. */
internal fun outdatedRules(
    stored: Map<String, StoredRule>,
    seeds: List<ParserRuleEntity>,
): List<ParserRuleEntity> =
    seeds.filter { seed ->
        val old = stored[seed.id]
        old == null || (!old.isUserCustom && old.version < seed.version)
    }
