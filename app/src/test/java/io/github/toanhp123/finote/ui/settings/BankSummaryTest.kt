package io.github.toanhp123.finote.ui.settings

import io.github.toanhp123.finote.domain.notification.model.ParserRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BankSummaryTest {
    private fun rule(
        id: String,
        packageName: String = "pkg.a",
        bankName: String = "Bank A",
        version: Int = 1,
        isActive: Boolean = true,
        isUserCustom: Boolean = false,
    ) = ParserRule(
        id = id,
        packageName = packageName,
        bankName = bankName,
        channelIdAllowlist = null,
        ignoreKeywords = emptyList(),
        priority = 0,
        isUserCustom = isUserCustom,
        version = version,
        regexPattern = "(?<amount>\\d+)",
        fallbackTransactionType = null,
        dateFormatPattern = null,
        isActive = isActive,
        updatedAt = 0L,
    )

    @Test
    fun `counts active rules only`() {
        val result = summarizeBanks(listOf(rule("1"), rule("2"), rule("3", isActive = false, version = 9)))

        assertEquals(1, result.size)
        assertEquals(2, result.single().activeRuleCount)
        assertEquals(1, result.single().highestVersion)
    }

    @Test
    fun `groups by package and names from highest version`() {
        val result =
            summarizeBanks(
                listOf(
                    rule("1", bankName = "VCB", version = 1),
                    rule("2", bankName = "Vietcombank", version = 2),
                ),
            )

        assertEquals(1, result.size)
        assertEquals("Vietcombank", result.single().bankName)
        assertEquals(2, result.single().highestVersion)
    }

    @Test
    fun `hides a package with no active rule`() {
        val result = summarizeBanks(listOf(rule("1", isActive = false), rule("2", packageName = "pkg.b")))

        assertEquals(listOf("pkg.b"), result.map { it.packageName })
    }

    @Test
    fun `flags user edits`() {
        val result =
            summarizeBanks(
                listOf(
                    rule("1", packageName = "pkg.a", isUserCustom = true),
                    rule("2", packageName = "pkg.b"),
                ),
            )

        assertTrue(result.first { it.packageName == "pkg.a" }.hasUserEdits)
        assertFalse(result.first { it.packageName == "pkg.b" }.hasUserEdits)
    }

    @Test
    fun `summarizeBanks lists a feed-only package`() {
        val result = summarizeBanks(listOf(rule("1", packageName = "pkg.z", bankName = "Zed Bank")))

        assertEquals(
            listOf(BankSummary("pkg.z", "Zed Bank", activeRuleCount = 1, highestVersion = 1, hasUserEdits = false)),
            result,
        )
    }

    @Test
    fun `sorts by bank name`() {
        val result =
            summarizeBanks(listOf(rule("1", packageName = "p2", bankName = "MoMo"), rule("2", bankName = "Acb")))

        assertEquals(listOf("Acb", "MoMo"), result.map { it.bankName })
    }
}
