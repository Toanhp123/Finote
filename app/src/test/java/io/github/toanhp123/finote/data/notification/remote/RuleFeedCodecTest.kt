package io.github.toanhp123.finote.data.notification.remote

import io.github.toanhp123.finote.domain.account.model.AccountType
import io.github.toanhp123.finote.domain.notification.error.RuleFeedError
import io.github.toanhp123.finote.domain.notification.model.ParserRule
import io.github.toanhp123.finote.domain.transaction.model.TransactionType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec
import java.util.Base64

class RuleFeedCodecTest {
    private val keys: KeyPair =
        KeyPairGenerator.getInstance("EC").run {
            initialize(ECGenParameterSpec("secp256r1"))
            generateKeyPair()
        }

    private val good =
        ParserRule(
            id = "vcb_account_v1",
            packageName = "com.VCB",
            bankName = "Vietcombank",
            channelIdAllowlist = listOf("tx"),
            ignoreKeywords = listOf("OTP"),
            priority = 0,
            isUserCustom = false,
            version = 2,
            regexPattern = "(?<sign>[+-])(?<amount>\\d+)",
            fallbackTransactionType = null,
            dateFormatPattern = "dd/MM/yyyy",
            isActive = true,
            updatedAt = 0L,
        )

    private fun feed(vararg rules: ParserRule) = signRuleFeed(rules.toList(), keys.private)

    private fun assertInvalid(envelope: ByteArray) {
        val e = assertThrows(RuleFeedFailure::class.java) { decodeRuleFeed(envelope, keys.public) }
        assertEquals(RuleFeedError.INVALID, e.error)
    }

    private fun assertInvalidRule(rule: ParserRule) = assertInvalid(feed(rule))

    private fun parse(bytes: ByteArray) = Json.parseToJsonElement(bytes.toString(Charsets.UTF_8)).jsonObject

    private fun JsonObject.text(key: String) = (getValue(key) as JsonPrimitive).content

    private fun b64(bytes: ByteArray) = Base64.getEncoder().encodeToString(bytes)

    /** Re-signs a feed whose payload JSON is edited by [edit]. */
    private fun resigned(
        rules: List<ParserRule>,
        edit: (JsonObject) -> JsonObject,
    ): ByteArray {
        val payload = Base64.getDecoder().decode(parse(signRuleFeed(rules, keys.private)).text("payload"))
        return signPayload(edit(parse(payload)).toString().toByteArray(), keys.private)
    }

    @Test
    fun `valid envelope round trips`() {
        val rules =
            decodeRuleFeed(
                feed(good, good.copy(id = "b", fallbackTransactionType = TransactionType.EXPENSE)),
                keys.public,
            )
        assertEquals(listOf(good, good.copy(id = "b", fallbackTransactionType = TransactionType.EXPENSE)), rules)
        assertEquals(false, rules.first().isUserCustom)
    }

    @Test
    fun `feed without accountType decodes to null`() {
        val envelope =
            resigned(listOf(good)) { payload ->
                val rules = payload.getValue("rules") as JsonArray
                val stripped = rules.map { JsonObject((it as JsonObject).filterKeys { key -> key != "accountType" }) }
                JsonObject(payload + ("rules" to JsonArray(stripped)))
            }
        assertEquals(listOf(null), decodeRuleFeed(envelope, keys.public).map { it.accountType })
    }

    @Test
    fun `feed with accountType decodes it`() {
        val rules = decodeRuleFeed(feed(good.copy(accountType = AccountType.WALLET)), keys.public)
        assertEquals(AccountType.WALLET, rules.single().accountType)
    }

    @Test
    fun `feed with unknown accountType decodes to null`() {
        val envelope =
            resigned(listOf(good.copy(accountType = AccountType.WALLET))) { payload ->
                val rules = payload.getValue("rules") as JsonArray
                val unknown = JsonPrimitive("SPACESHIP")
                val edited = rules.map { JsonObject((it as JsonObject) + ("accountType" to unknown)) }
                JsonObject(payload + ("rules" to JsonArray(edited)))
            }
        assertEquals(listOf(null), decodeRuleFeed(envelope, keys.public).map { it.accountType })
    }

    @Test
    fun `public key round trips through base64`() {
        val key = publicKeyFromBase64(b64(keys.public.encoded))
        assertEquals(1, decodeRuleFeed(feed(good), key).size)
    }

    @Test
    fun `flipped payload byte is INVALID`() {
        val envelope = parse(feed(good))
        val payload = Base64.getDecoder().decode(envelope.text("payload"))
        payload[payload.size / 2] = (payload[payload.size / 2].toInt() xor 1).toByte()
        assertInvalid(JsonObject(envelope + ("payload" to JsonPrimitive(b64(payload)))).toString().toByteArray())
    }

    @Test
    fun `flipped signature byte is INVALID`() {
        val envelope = parse(feed(good))
        val signature = Base64.getDecoder().decode(envelope.text("signature"))
        signature[signature.size - 1] = (signature.last().toInt() xor 1).toByte()
        assertInvalid(JsonObject(envelope + ("signature" to JsonPrimitive(b64(signature)))).toString().toByteArray())
    }

    @Test
    fun `not json is INVALID`() = assertInvalid("nope".toByteArray())

    @Test
    fun `bad base64 is INVALID`() = assertInvalid("""{"payload":"!!!","signature":"???"}""".toByteArray())

    @Test
    fun `wrong schemaVersion is INVALID`() =
        assertInvalid(resigned(listOf(good)) { JsonObject(it + ("schemaVersion" to JsonPrimitive(2))) })

    @Test
    fun `unknown payload field is ignored`() {
        val envelope = resigned(listOf(good)) { JsonObject(it + ("futureField" to JsonPrimitive("x"))) }
        assertEquals(listOf(good), decodeRuleFeed(envelope, keys.public))
    }

    @Test
    fun `empty rules is INVALID`() = assertInvalid(feed())

    @Test
    fun `regex that does not compile is INVALID`() =
        assertInvalidRule(good.copy(regexPattern = "(?<sign>[+-])(?<amount>\\d+"))

    @Test
    fun `missing amount group is INVALID`() = assertInvalidRule(good.copy(regexPattern = "(?<sign>[+-])\\d+"))

    @Test
    fun `no sign group and no fallback is INVALID`() = assertInvalidRule(good.copy(regexPattern = "(?<amount>\\d+)"))

    @Test
    fun `fallback replaces the sign group`() {
        val rule = good.copy(regexPattern = "(?<amount>\\d+)", fallbackTransactionType = TransactionType.INCOME)
        assertEquals(listOf(rule), decodeRuleFeed(feed(rule), keys.public))
    }

    @Test
    fun `bad fallback type is INVALID`() =
        assertInvalidRule(good.copy(fallbackTransactionType = TransactionType.TRANSFER))

    @Test
    fun `duplicate id is INVALID`() = assertInvalid(feed(good, good))

    @Test
    fun `blank id, package or bank name is INVALID`() {
        assertInvalidRule(good.copy(id = " "))
        assertInvalidRule(good.copy(packageName = ""))
        assertInvalidRule(good.copy(bankName = " "))
    }

    @Test
    fun `version zero is INVALID`() = assertInvalidRule(good.copy(version = 0))

    @Test
    fun `blank ignore keyword is INVALID`() {
        assertInvalidRule(good.copy(ignoreKeywords = listOf("")))
        assertInvalidRule(good.copy(ignoreKeywords = listOf(" ")))
    }

    @Test
    fun `blank channel id is INVALID`() = assertInvalidRule(good.copy(channelIdAllowlist = listOf(" ")))

    @Test
    fun `bad dateFormatPattern is INVALID`() = assertInvalidRule(good.copy(dateFormatPattern = "qqqqq{"))

    @Test
    fun `exception message is a reason code only`() {
        val e =
            assertThrows(RuleFeedFailure::class.java) { decodeRuleFeed(feed(good.copy(version = 0)), keys.public) }
        assertEquals("bad_rule", e.message)
    }
}
