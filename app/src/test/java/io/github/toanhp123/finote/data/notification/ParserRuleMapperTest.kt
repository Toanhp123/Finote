package io.github.toanhp123.finote.data.notification

import io.github.toanhp123.finote.domain.account.model.AccountType
import io.github.toanhp123.finote.domain.transaction.model.TransactionType
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
            ).toDomain()!!

        assertEquals(listOf("OTP", "ưu đãi"), rule.ignoreKeywords)
        assertEquals(listOf("txn"), rule.channelIdAllowlist)
    }

    @Test
    fun `empty or missing allowlist means no channel filter`() {
        assertNull(parserRuleEntity(channelIdAllowlist = "[]").toDomain()!!.channelIdAllowlist)
        assertNull(parserRuleEntity(channelIdAllowlist = null).toDomain()!!.channelIdAllowlist)
    }

    @Test
    fun `entity round trips through the domain model`() {
        val entity =
            parserRuleEntity(
                channelIdAllowlist = "[\"a\",\"b\"]",
                ignoreKeywords = "[\"x\",\"y\"]",
                regexPattern = "(?<amount>\\d+)",
                fallbackTransactionType = "INCOME",
                isActive = false,
            ).copy(dateFormatPattern = "dd/MM/yyyy", updatedAt = 7L)

        assertEquals(entity, checkNotNull(entity.toDomain()).toEntity())
    }

    @Test
    fun `bad allowlist json gives null`() {
        assertNull(parserRuleEntity(channelIdAllowlist = "[oops").toDomain())
    }

    @Test
    fun `bad keywords json gives null`() {
        assertNull(parserRuleEntity(ignoreKeywords = "not json").toDomain())
    }

    @Test
    fun `unknown fallback type gives null`() {
        assertNull(parserRuleEntity(fallbackTransactionType = "expense").toDomain())
        assertNull(parserRuleEntity(fallbackTransactionType = "TRANSFER").toDomain())
    }

    @Test
    fun `account type maps and bad value drops the row`() {
        assertEquals(AccountType.WALLET, parserRuleEntity(accountType = "WALLET").toDomain()?.accountType)
        assertNull(parserRuleEntity(accountType = null).toDomain()!!.accountType)
        assertNull(parserRuleEntity(accountType = "SPACESHIP").toDomain())
        assertEquals("WALLET", parserRuleEntity(accountType = "WALLET").toDomain()!!.toEntity().accountType)
    }

    @Test
    fun `fallback type maps to enum`() {
        val rule = parserRuleEntity(fallbackTransactionType = "EXPENSE").toDomain()

        assertEquals(TransactionType.EXPENSE, rule?.fallbackTransactionType)
        assertNull(parserRuleEntity(fallbackTransactionType = null).toDomain()!!.fallbackTransactionType)
    }
}
