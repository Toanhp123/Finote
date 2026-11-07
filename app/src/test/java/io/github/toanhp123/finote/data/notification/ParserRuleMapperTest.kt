package io.github.toanhp123.finote.data.notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ParserRuleMapperTest {
    @Test
    fun `json arrays decode to lists`() {
        val rule =
            parserRuleEntity(
                ignoreKeywords = "[\"OTP\",\"ưu đãi\"]",
                channelIdAllowlist = "[\"txn\"]",
            ).toDomain()

        assertEquals(listOf("OTP", "ưu đãi"), rule.ignoreKeywords)
        assertEquals(listOf("txn"), rule.channelIdAllowlist)
    }

    @Test
    fun `empty or missing allowlist means no channel filter`() {
        assertNull(parserRuleEntity(channelIdAllowlist = "[]").toDomain().channelIdAllowlist)
        assertNull(parserRuleEntity(channelIdAllowlist = null).toDomain().channelIdAllowlist)
    }

    @Test
    fun `entity round trips through the domain model`() {
        val entity = parserRuleEntity(channelIdAllowlist = "[\"a\",\"b\"]", ignoreKeywords = "[\"x\",\"y\"]")

        assertEquals(entity, entity.toDomain().toEntity())
    }
}
