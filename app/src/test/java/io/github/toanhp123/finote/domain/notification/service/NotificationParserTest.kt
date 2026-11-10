package io.github.toanhp123.finote.domain.notification.service

import io.github.toanhp123.finote.domain.notification.model.ParseResult
import io.github.toanhp123.finote.domain.notification.model.ParsedFields
import io.github.toanhp123.finote.domain.notification.model.ParserRule
import io.github.toanhp123.finote.domain.notification.testRule
import io.github.toanhp123.finote.domain.transaction.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NotificationParserTest {
    private fun rule(
        id: String,
        priority: Int,
        pattern: String,
        fallback: TransactionType? = null,
        isActive: Boolean = true,
    ) = testRule(
        id = id,
        priority = priority,
        regexPattern = pattern,
        fallbackTransactionType = fallback,
        isActive = isActive,
    )

    private fun parse(
        content: String,
        vararg rules: ParserRule,
        title: String = "",
    ) = parseNotification(title, content, rules.toList())

    private fun parsedFields(result: ParseResult) = (result as ParseResult.Parsed).fields

    @Test
    fun `parseMoney reads grouped amounts`() {
        listOf(
            "1.500.000",
            "1,500,000",
            "1 500 000 VND",
            "1\u00A0500\u00A0000 VND",
            "1,500,000.00 VND",
            "1.500.000,00đ",
        ).forEach { assertEquals(it, 1_500_000L, parseMoney(it)) }
        assertEquals(50_000L, parseMoney("50.000đ"))
    }

    @Test
    fun `parseMoney keeps zero`() {
        assertEquals(0L, parseMoney("0 VND"))
        assertEquals(0L, parseMoney("0đ"))
    }

    @Test
    fun `parseMoney rejects junk and overflow`() {
        assertNull(parseMoney("abc"))
        assertNull(parseMoney(""))
        assertNull(parseMoney("12345678901234567890"))
    }

    @Test
    fun `credit with sign`() {
        val result = parse("TK 1234 +1,500,000 VND", rule("r1", 1, "(?<sign>[+-])(?<amount>[\\d,.]+) VND"))
        assertEquals(
            ParseResult.Parsed("r1", ParsedFields(1_500_000L, TransactionType.INCOME, null, null, null, null)),
            result,
        )
    }

    @Test
    fun `zero balance is kept`() {
        val pattern = "(?<sign>[+-])(?<amount>[\\d,.]+) VND.*SD (?<balance>[\\d,.]+ ?VND)"
        val fields = parsedFields(parse("-100,000 VND SD 0 VND", rule("r1", 1, pattern)))
        assertEquals(0L, fields.postBalance)
        assertEquals(TransactionType.EXPENSE, fields.type)
    }

    @Test
    fun `higher priority wins and equal priority orders by id`() {
        val pattern = "(?<sign>[+-])(?<amount>[\\d,.]+) VND"
        val text = "+1,000 VND"
        assertEquals("b", (parse(text, rule("a", 1, pattern), rule("b", 5, pattern)) as ParseResult.Parsed).ruleId)
        assertEquals("a", (parse(text, rule("b", 3, pattern), rule("a", 3, pattern)) as ParseResult.Parsed).ruleId)
    }

    @Test
    fun `fallback type when no sign group`() {
        val rule = rule("r1", 1, "Thanh toan (?<amount>[\\d.]+)d", TransactionType.EXPENSE)
        val result = parse("Thanh toan 20.000d", rule)
        assertEquals(TransactionType.EXPENSE, parsedFields(result).type)
    }

    @Test
    fun `unknown sign rejects the rule even with a fallback`() {
        val pattern = "(?<sign>[+x-])(?<amount>[\\d,.]+) VND"
        val rejecting = rule("r1", 5, pattern, TransactionType.INCOME)
        val text = "x1,000 VND"
        assertEquals(ParseResult.NoMatch, parse(text, rejecting))
        val second = rule("r2", 1, "(?<amount>[\\d,.]+) VND", TransactionType.EXPENSE)
        assertEquals("r2", (parse(text, rejecting, second) as ParseResult.Parsed).ruleId)
    }

    @Test
    fun `no sign and no fallback rejects`() {
        assertEquals(ParseResult.NoMatch, parse("1,000 VND", rule("r1", 1, "(?<amount>[\\d,.]+) VND")))
    }

    @Test
    fun `zero amount falls through to the next rule`() {
        val zero = rule("r1", 5, "(?<amount>0) VND", TransactionType.INCOME)
        val next = rule("r2", 1, "(?<amount>[1-9]\\d*) VND", TransactionType.EXPENSE)
        assertEquals("r2", (parse("0 VND, 7 VND", zero, next) as ParseResult.Parsed).ruleId)
        assertEquals(ParseResult.NoMatch, parse("0 VND", zero))
    }

    @Test
    fun `bad pattern is skipped`() {
        val bad = rule("r1", 5, "(?<amount>[0-9")
        val good = rule("r2", 1, "(?<amount>[\\d,.]+) VND", TransactionType.INCOME)
        assertEquals("r2", (parse("5,000 VND", bad, good) as ParseResult.Parsed).ruleId)
    }

    @Test
    fun `inactive rule is not used`() {
        val inactive = rule("r1", 1, "(?<amount>[\\d,.]+) VND", TransactionType.INCOME, isActive = false)
        assertEquals(ParseResult.NoMatch, parse("5,000 VND", inactive))
    }

    @Test
    fun `missing mask group gives null mask`() {
        val fields = parsedFields(parse("5,000 VND", rule("r1", 1, "(?<amount>[\\d,.]+) VND", TransactionType.INCOME)))
        assertNull(fields.accountMask)
    }

    @Test
    fun `blank merchant becomes null and others are trimmed`() {
        val pattern = "(?<amount>[\\d,.]+) VND\\|(?<mask>[^|]*)\\|(?<merchant>[^|]*)\\|(?<note>.*)"
        val rule = rule("r1", 1, pattern, TransactionType.EXPENSE)
        val fields = parsedFields(parse("5,000 VND| 12 |   |  an trua ", rule))
        assertEquals("12", fields.accountMask)
        assertNull(fields.merchant)
        assertEquals("an trua", fields.note)
    }

    @Test
    fun `group name that is only pattern text does not throw`() {
        // `\(?<balance>` is an optional "(" plus text; `[(?<merchant>]` is a character class. Neither is a group.
        val escaped = rule("r1", 1, "(?<sign>[+-])(?<amount>\\d+) \\(?<balance>\\d+")
        val fields = parsedFields(parse("-500 <balance>7", escaped))
        assertEquals(500L, fields.amount)
        assertNull(fields.postBalance)

        val bracketed = rule("r2", 1, "(?<sign>[+-])(?<amount>\\d+) [(?<merchant>]+")
        val second = parsedFields(parse("-500 (", bracketed))
        assertEquals(500L, second.amount)
        assertNull(second.merchant)
    }

    @Test
    fun `multi line content`() {
        val pattern =
            "(?s)So tien: (?<sign>[+-])(?<amount>[\\d,.]+) VND.*?" +
                "So du: (?<balance>[\\d,.]+) VND.*?ND: (?<note>.+)"
        val content = "So tien: +200,000 VND\nSo du: 1,000,000 VND\nND: an trua"
        val fields = parsedFields(parse(content, rule("r1", 1, pattern)))
        assertEquals(200_000L, fields.amount)
        assertEquals(1_000_000L, fields.postBalance)
        assertEquals("an trua", fields.note)
    }

    @Test
    fun `title carries the sign`() {
        val pattern = "(?s)Nhận tiền.*?Số tiền (?<amount>[\\d.]+)đ"
        val rule = rule("r1", 1, pattern, TransactionType.INCOME)
        val fields = parsedFields(parse("Số tiền 50.000đ", rule, title = "Nhận tiền thành công"))
        assertEquals(TransactionType.INCOME, fields.type)
        assertEquals(50_000L, fields.amount)
    }
}
