package io.github.toanhp123.finote.data.notification

import io.github.toanhp123.finote.domain.notification.model.ParserRule
import io.github.toanhp123.finote.domain.transaction.model.TransactionType
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Null when the row is unusable: bad JSON, or a fallback type that is not exactly `INCOME` or `EXPENSE`.
 * The repository drops such a row, so one bad rule cannot hide the rest of its package.
 * An empty allowlist becomes null, so a bad row cannot block a whole bank.
 */
fun ParserRuleEntity.toDomain(): ParserRule? {
    val fallback =
        fallbackTransactionType?.let { raw ->
            listOf(TransactionType.INCOME, TransactionType.EXPENSE).firstOrNull { it.name == raw }
        }
    if (fallbackTransactionType != null && fallback == null) return null
    return runCatching {
        ParserRule(
            id = id,
            packageName = packageName,
            bankName = bankName,
            channelIdAllowlist = channelIdAllowlist?.let { Json.decodeFromString<List<String>>(it) }?.ifEmpty { null },
            ignoreKeywords = Json.decodeFromString<List<String>>(ignoreKeywords),
            priority = priority,
            isUserCustom = isUserCustom,
            version = version,
            regexPattern = regexPattern,
            fallbackTransactionType = fallback,
            dateFormatPattern = dateFormatPattern,
            isActive = isActive,
            updatedAt = updatedAt,
        )
    }.getOrNull()
}

fun ParserRule.toEntity(): ParserRuleEntity =
    ParserRuleEntity(
        id = id,
        packageName = packageName,
        bankName = bankName,
        channelIdAllowlist = channelIdAllowlist?.let { Json.encodeToString(it) },
        ignoreKeywords = Json.encodeToString(ignoreKeywords),
        priority = priority,
        isUserCustom = isUserCustom,
        version = version,
        regexPattern = regexPattern,
        fallbackTransactionType = fallbackTransactionType?.name,
        dateFormatPattern = dateFormatPattern,
        isActive = isActive,
        updatedAt = updatedAt,
    )
