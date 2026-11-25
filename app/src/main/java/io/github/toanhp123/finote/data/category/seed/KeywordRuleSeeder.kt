package io.github.toanhp123.finote.data.category.seed

import androidx.room3.RoomDatabase
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import io.github.toanhp123.finote.data.category.local.entity.CategoryKeywordRuleEntity
import io.github.toanhp123.finote.data.database.immediateTransaction

private const val INSERT_SQL =
    "INSERT INTO category_keyword_rules (id, keyword, normalizedKeyword, matchType, normalizedMerchantName, " +
        "categoryId, priority, isUserDefined, isActive, version, createdAt, updatedAt) " +
        "VALUES (?, ?, ?, ?, ?, ?, ?, 0, 1, ?, ?, ?)"

// Never isActive or createdAt: a rule the user switched off stays off. Not INSERT OR REPLACE for the same reason.
private const val UPDATE_SQL =
    "UPDATE category_keyword_rules SET keyword = ?, normalizedKeyword = ?, matchType = ?, " +
        "normalizedMerchantName = ?, categoryId = ?, priority = ?, version = ?, updatedAt = ? " +
        "WHERE id = ? AND isUserDefined = 0 AND version < ?"

/**
 * Keeps [SystemKeywordRules] in the database, rule by rule, like `ParserRuleSeeder`: a missing rule is inserted, a
 * stored system rule with a lower version is updated, and a user rule or a rule of the same or a newer version is not
 * touched. Register it after `SystemCategorySeeder`: the foreign key needs the `sys_*` rows.
 */
class KeywordRuleSeeder : RoomDatabase.Callback() {
    override suspend fun onCreate(connection: SQLiteConnection) = write(connection, emptyMap())

    override suspend fun onOpen(connection: SQLiteConnection) {
        // Cheap read outside the write lock: the common reopen has nothing to seed and takes no lock.
        if (isCurrent(storedVersions(connection))) return
        // Room runs onOpen outside a transaction, so a failed seed must not leave half of the rows behind.
        connection.immediateTransaction {
            // Read again under the write lock: another connection may have seeded since the check above.
            write(connection, storedVersions(connection))
        }
    }

    private fun isCurrent(stored: Map<String, Int>) = SystemKeywordRules.all { (stored[it.id] ?: -1) >= it.version }

    private fun storedVersions(connection: SQLiteConnection): Map<String, Int> =
        connection.prepare("SELECT id, version FROM category_keyword_rules").use { statement ->
            buildMap {
                while (statement.step()) put(statement.getText(0), statement.getLong(1).toInt())
            }
        }

    private fun write(
        connection: SQLiteConnection,
        stored: Map<String, Int>,
    ) {
        val (existing, missing) = SystemKeywordRules.partition { it.id in stored }
        connection.prepare(INSERT_SQL).use { statement ->
            missing.forEach { rule ->
                statement.bindInsert(rule)
                statement.step()
                statement.reset()
            }
        }
        connection.prepare(UPDATE_SQL).use { statement ->
            // The WHERE clause skips a user row and a stored row that is not older.
            existing.forEach { rule ->
                statement.bindUpdate(rule)
                statement.step()
                statement.reset()
            }
        }
    }

    private fun SQLiteStatement.bindInsert(rule: CategoryKeywordRuleEntity) {
        var index = 0
        bindText(++index, rule.id)
        bindText(++index, rule.keyword)
        bindText(++index, rule.normalizedKeyword)
        bindText(++index, rule.matchType.name)
        bindOptionalText(++index, rule.normalizedMerchantName)
        bindText(++index, rule.categoryId)
        bindLong(++index, rule.priority.toLong())
        bindLong(++index, rule.version.toLong())
        bindLong(++index, rule.createdAt)
        bindLong(++index, rule.updatedAt)
    }

    private fun SQLiteStatement.bindUpdate(rule: CategoryKeywordRuleEntity) {
        var index = 0
        bindText(++index, rule.keyword)
        bindText(++index, rule.normalizedKeyword)
        bindText(++index, rule.matchType.name)
        bindOptionalText(++index, rule.normalizedMerchantName)
        bindText(++index, rule.categoryId)
        bindLong(++index, rule.priority.toLong())
        bindLong(++index, rule.version.toLong())
        bindLong(++index, rule.updatedAt)
        bindText(++index, rule.id)
        bindLong(++index, rule.version.toLong())
    }

    private fun SQLiteStatement.bindOptionalText(
        index: Int,
        value: String?,
    ) {
        if (value == null) bindNull(index) else bindText(index, value)
    }
}
