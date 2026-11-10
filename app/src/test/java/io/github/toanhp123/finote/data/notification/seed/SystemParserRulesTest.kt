package io.github.toanhp123.finote.data.notification.seed

import org.junit.Assert.assertEquals
import org.junit.Test

class SystemParserRulesTest {
    @Test
    fun `account types`() {
        val cards = setOf("mb_card_v1", "tcb_card_v1")
        SystemParserRules.forEach { rule ->
            val expected =
                when {
                    rule.id.startsWith("momo_") -> "WALLET"
                    rule.id in cards -> "CREDIT_CARD"
                    else -> null
                }
            assertEquals(rule.id, expected, rule.accountType)
        }
        assertEquals(cards, SystemParserRules.filter { it.accountType == "CREDIT_CARD" }.map { it.id }.toSet())
    }
}
