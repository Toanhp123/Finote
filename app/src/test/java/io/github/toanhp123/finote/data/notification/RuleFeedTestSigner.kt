package io.github.toanhp123.finote.data.notification

import io.github.toanhp123.finote.domain.notification.model.ParserRule
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.security.PrivateKey
import java.security.Signature
import java.util.Base64

/** Test helper, also used by the feed signing tool: the compact envelope for [rules], signed with [privateKey]. */
fun signRuleFeed(
    rules: List<ParserRule>,
    privateKey: PrivateKey,
): ByteArray =
    signPayload(
        JsonObject(
            mapOf(
                "schemaVersion" to JsonPrimitive(1),
                "rules" to JsonArray(rules.map(::ruleJson)),
            ),
        ).toString().toByteArray(),
        privateKey,
    )

/** Signs raw payload bytes; lets a test build a payload that [signRuleFeed] would not produce. */
fun signPayload(
    payload: ByteArray,
    privateKey: PrivateKey,
): ByteArray {
    val signature =
        Signature.getInstance("SHA256withECDSA").run {
            initSign(privateKey)
            update(payload)
            sign()
        }
    val encoder = Base64.getEncoder()
    return JsonObject(
        mapOf(
            "payload" to JsonPrimitive(encoder.encodeToString(payload)),
            "signature" to JsonPrimitive(encoder.encodeToString(signature)),
        ),
    ).toString().toByteArray()
}

private fun ruleJson(rule: ParserRule) =
    JsonObject(
        mapOf(
            "id" to JsonPrimitive(rule.id),
            "packageName" to JsonPrimitive(rule.packageName),
            "bankName" to JsonPrimitive(rule.bankName),
            "channelIdAllowlist" to
                (rule.channelIdAllowlist?.let { list -> JsonArray(list.map(::JsonPrimitive)) } ?: JsonNull),
            "ignoreKeywords" to JsonArray(rule.ignoreKeywords.map(::JsonPrimitive)),
            "priority" to JsonPrimitive(rule.priority),
            "version" to JsonPrimitive(rule.version),
            "regexPattern" to JsonPrimitive(rule.regexPattern),
            "fallbackTransactionType" to (rule.fallbackTransactionType?.let { JsonPrimitive(it.name) } ?: JsonNull),
            "dateFormatPattern" to (rule.dateFormatPattern?.let(::JsonPrimitive) ?: JsonNull),
            "isActive" to JsonPrimitive(rule.isActive),
            "accountType" to (rule.accountType?.let { JsonPrimitive(it.name) } ?: JsonNull),
        ),
    )
