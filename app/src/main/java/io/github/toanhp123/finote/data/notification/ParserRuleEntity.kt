package io.github.toanhp123.finote.data.notification

import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey

@Entity(tableName = "parser_rules", indices = [Index(value = ["packageName"])])
data class ParserRuleEntity(
    @PrimaryKey val id: String,
    val packageName: String,
    val bankName: String,
    /** JSON array of channel ids. Null means every channel is accepted. */
    val channelIdAllowlist: String?,
    /** JSON array of keywords. */
    val ignoreKeywords: String,
    val priority: Int,
    val isUserCustom: Boolean,
    val version: Int,
    /** Named groups: sign, amount, mask, balance, merchant, note, time. Flags are inline. */
    val regexPattern: String,
    /** `INCOME` or `EXPENSE`. */
    val fallbackTransactionType: String?,
    val dateFormatPattern: String?,
    val isActive: Boolean,
    val updatedAt: Long,
)
