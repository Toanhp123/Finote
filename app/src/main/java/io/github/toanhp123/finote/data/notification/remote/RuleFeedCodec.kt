package io.github.toanhp123.finote.data.notification.remote

import io.github.toanhp123.finote.domain.account.model.AccountType
import io.github.toanhp123.finote.domain.notification.error.RuleFeedError
import io.github.toanhp123.finote.domain.notification.error.RuleFeedException
import io.github.toanhp123.finote.domain.notification.model.ParserRule
import io.github.toanhp123.finote.domain.notification.service.regexGroupNames
import io.github.toanhp123.finote.domain.transaction.model.TransactionType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.GeneralSecurityException
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.time.format.DateTimeFormatter
import java.util.Base64

private const val SCHEMA_VERSION = 1

@Serializable
private class EnvelopeDto(
    val payload: String,
    val signature: String,
)

@Serializable
private class PayloadDto(
    val schemaVersion: Int,
    val rules: List<RuleDto>,
)

@Serializable
@Suppress("LongParameterList") // why: DTO mirrors the feed schema one field per key
private class RuleDto(
    val id: String,
    val packageName: String,
    val bankName: String,
    val channelIdAllowlist: List<String>?,
    val ignoreKeywords: List<String>,
    val priority: Int,
    val version: Int,
    val regexPattern: String,
    val fallbackTransactionType: String?,
    val dateFormatPattern: String?,
    val isActive: Boolean,
    val accountType: String? = null,
)

// The payload is signed, so extra fields are trusted; an additive field must not make old installs reject the feed.
private val feedJson = Json { ignoreUnknownKeys = true }

private fun invalid(code: String): Nothing = throw RuleFeedException(RuleFeedError.INVALID, code)

/** Reads the base64 X.509 (SubjectPublicKeyInfo) form of an EC public key. */
fun publicKeyFromBase64(x509: String): PublicKey =
    java.security.KeyFactory
        .getInstance("EC")
        .generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(x509)))

/**
 * Decodes a signed rule feed. Checks run in order: envelope, signature (over the decoded payload bytes,
 * before the payload is parsed), schema, rules. Any failure throws [RuleFeedException] with INVALID.
 */
fun decodeRuleFeed(
    envelope: ByteArray,
    publicKey: PublicKey,
): List<ParserRule> {
    val payloadBytes = verifiedPayload(envelope, publicKey)
    val payload =
        try {
            feedJson.decodeFromString<PayloadDto>(payloadBytes.toString(Charsets.UTF_8))
        } catch (_: IllegalArgumentException) {
            // SerializationException is a subclass
            invalid("bad_schema")
        }
    if (payload.schemaVersion != SCHEMA_VERSION || payload.rules.isEmpty()) invalid("bad_schema")

    val seenIds = HashSet<String>()
    return payload.rules.map { dto ->
        if (!seenIds.add(dto.id)) invalid("bad_rule")
        dto.toRule()
    }
}

private fun verifiedPayload(
    envelope: ByteArray,
    publicKey: PublicKey,
): ByteArray {
    val payloadBytes: ByteArray
    val signature: ByteArray
    try {
        val dto = feedJson.decodeFromString<EnvelopeDto>(envelope.toString(Charsets.UTF_8))
        payloadBytes = Base64.getDecoder().decode(dto.payload)
        signature = Base64.getDecoder().decode(dto.signature)
    } catch (_: IllegalArgumentException) {
        // bad JSON, bad base64
        invalid("bad_envelope")
    }
    val isValid =
        try {
            Signature.getInstance("SHA256withECDSA").run {
                initVerify(publicKey)
                update(payloadBytes)
                verify(signature)
            }
        } catch (_: GeneralSecurityException) {
            false
        }
    if (!isValid) invalid("bad_signature")
    return payloadBytes
}

private fun RuleDto.checkFields() {
    val isBlankField = id.isBlank() || packageName.isBlank() || bankName.isBlank()
    val hasBlankFilter = ignoreKeywords.any { it.isBlank() } || channelIdAllowlist.orEmpty().any { it.isBlank() }
    if (isBlankField || version < 1 || hasBlankFilter) invalid("bad_rule")
    if (dateFormatPattern != null) {
        try {
            DateTimeFormatter.ofPattern(dateFormatPattern)
        } catch (_: IllegalArgumentException) {
            invalid("bad_rule")
        }
    }
}

private fun RuleDto.checkRegex(fallback: TransactionType?) {
    val groups =
        try {
            Regex(regexPattern)
            regexGroupNames(regexPattern)
        } catch (_: IllegalArgumentException) {
            invalid("bad_rule")
        }
    if ("amount" !in groups || ("sign" !in groups && fallback == null)) invalid("bad_rule")
}

private fun RuleDto.toRule(): ParserRule {
    checkFields()
    val fallback = fallbackTransactionType?.let { name -> TransactionType.entries.firstOrNull { it.name == name } }
    if (fallbackTransactionType != null && fallback != TransactionType.INCOME && fallback != TransactionType.EXPENSE) {
        invalid("bad_rule")
    }
    // An unknown value (a newer feed) falls back to BANK (null) instead of rejecting the whole feed.
    val type = accountType?.let { name -> AccountType.entries.firstOrNull { it.name == name } }
    checkRegex(fallback)
    return ParserRule(
        id = id,
        packageName = packageName,
        bankName = bankName,
        channelIdAllowlist = channelIdAllowlist,
        ignoreKeywords = ignoreKeywords,
        priority = priority,
        isUserCustom = false,
        version = version,
        regexPattern = regexPattern,
        fallbackTransactionType = fallback,
        dateFormatPattern = dateFormatPattern,
        isActive = isActive,
        updatedAt = 0L,
        accountType = type,
    )
}
