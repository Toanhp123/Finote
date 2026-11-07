package io.github.toanhp123.finote.data.notification

import io.github.toanhp123.finote.domain.notification.model.ParserRule
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** An empty allowlist becomes null, so a bad row cannot block a whole bank. */
fun ParserRuleEntity.toDomain(): ParserRule =
    ParserRule(
        id = id,
        packageName = packageName,
        bankName = bankName,
        channelIdAllowlist = channelIdAllowlist?.let { Json.decodeFromString<List<String>>(it) }?.ifEmpty { null },
        ignoreKeywords = Json.decodeFromString<List<String>>(ignoreKeywords),
        priority = priority,
        isUserCustom = isUserCustom,
        version = version,
    )

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
    )
