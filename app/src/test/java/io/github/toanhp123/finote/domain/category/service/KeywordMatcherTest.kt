package io.github.toanhp123.finote.domain.category.service

import io.github.toanhp123.finote.domain.category.model.Category
import io.github.toanhp123.finote.domain.category.model.CategoryType
import io.github.toanhp123.finote.domain.category.model.KeywordMatchType
import io.github.toanhp123.finote.domain.category.model.KeywordRule
import io.github.toanhp123.finote.domain.common.normalizeKeyword
import io.github.toanhp123.finote.domain.transaction.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KeywordMatcherTest {
    @Suppress("LongParameterList") // why: test helper, one defaulted argument per rule field the tests vary
    private fun rule(
        keyword: String,
        categoryId: String = "food",
        matchType: KeywordMatchType = KeywordMatchType.CONTAINS,
        merchantName: String? = null,
        priority: Int = 0,
        isUserDefined: Boolean = false,
        isActive: Boolean = true,
        updatedAt: Long = 0,
        id: String = "r-$keyword",
    ) = KeywordRule(
        id = id,
        keyword = keyword,
        normalizedKeyword = normalizeKeyword(keyword),
        matchType = matchType,
        merchantName = merchantName,
        categoryId = categoryId,
        priority = priority,
        isUserDefined = isUserDefined,
        isActive = isActive,
        version = 0,
        createdAt = 0,
        updatedAt = updatedAt,
    )

    private fun category(
        id: String,
        type: CategoryType = CategoryType.EXPENSE,
        isHidden: Boolean = false,
        deletedAt: Long? = null,
    ) = Category(
        id = id,
        name = id,
        nameKey = null,
        icon = "",
        color = 0,
        type = type,
        parentId = null,
        isSystemDefault = false,
        isHidden = isHidden,
        sortOrder = 0,
        createdAt = 0,
        updatedAt = 0,
        deletedAt = deletedAt,
    )

    private val categories =
        listOf(
            category("food"),
            category("ride"),
            category("salary", CategoryType.INCOME),
            category("hidden", isHidden = true),
            category("gone", deletedAt = 1),
        ).associateBy { it.id }

    private fun classifyRow(
        note: String?,
        vararg rules: KeywordRule,
        merchant: String? = null,
        type: TransactionType = TransactionType.EXPENSE,
    ) = classify(merchant, note, type, rules.toList(), categories)

    @Test
    fun `contains matches a whole word without accents`() {
        assertTrue(rule("grab").matches("GD THE TAI GRAB HCM"))
        assertTrue(rule("cà phê").matches("CA PHE MUOI"))
    }

    @Test
    fun `contains does not match inside a word`() {
        assertFalse(rule("be").matches("BEP NHA"))
        assertFalse(rule("an").matches("BANH MI"))
    }

    @Test
    fun `fields are matched one by one`() {
        assertNull(classifyRow("XYZ", rule("abc xyz"), merchant = "ABC"))
        assertEquals("r-abc", classifyRow("XYZ", rule("abc"), merchant = "ABC")?.rule?.id)
    }

    @Test
    fun `exact equals one field`() {
        val exact = rule("Highlands", matchType = KeywordMatchType.EXACT)
        assertTrue(exact.matches("  HIGHLANDS "))
        assertFalse(exact.matches("Highlands Coffee"))
    }

    @Test
    fun `regex is case insensitive`() {
        assertTrue(rule("^grab\\s+\\w+", matchType = KeywordMatchType.REGEX).matches("GRAB Food"))
    }

    @Test
    fun `invalid regex never matches`() {
        assertFalse(rule("(", matchType = KeywordMatchType.REGEX).matches("("))
    }

    @Test
    fun `only rules of the row type match`() {
        val income = rule("lương", categoryId = "salary")
        assertNull(classifyRow("LUONG THANG 5", income))
        assertEquals("salary", classifyRow("LUONG THANG 5", income, type = TransactionType.INCOME)?.categoryId)
    }

    @Test
    fun `inactive rule, hidden or deleted category never match`() {
        assertNull(classifyRow("grab", rule("grab", isActive = false)))
        assertNull(classifyRow("grab", rule("grab", categoryId = "hidden")))
        assertNull(classifyRow("grab", rule("grab", categoryId = "gone")))
        assertNull(classifyRow("grab", rule("grab", categoryId = "unknown")))
    }

    @Test
    fun `user rule beats system rule`() {
        val system = rule("grab food", categoryId = "food", id = "sys")
        val user = rule("grab", categoryId = "ride", isUserDefined = true, id = "usr")
        assertEquals("usr", classifyRow("GRAB FOOD", system, user)?.rule?.id)
    }

    @Test
    fun `longer keyword wins inside a group`() {
        val general = rule("grab", categoryId = "ride", updatedAt = 99)
        val specific = rule("grab food", categoryId = "food", updatedAt = 1)
        assertEquals("food", classifyRow("GRAB FOOD HCM", general, specific)?.categoryId)
    }

    @Test
    fun `system priority then updatedAt then id`() {
        val low = rule("grab", id = "a", priority = 1)
        val high = rule("grab", id = "b", priority = 2)
        assertEquals("b", classifyRow("grab", low, high)?.rule?.id)
        val old = rule("grab", id = "a", updatedAt = 1)
        val new = rule("grab", id = "b", updatedAt = 2)
        assertEquals("b", classifyRow("grab", old, new)?.rule?.id)
        val first = rule("grab", id = "a")
        val second = rule("grab", id = "b")
        assertEquals("a", classifyRow("grab", second, first)?.rule?.id)
    }

    @Test
    fun `transfer, adjustment and refund get no match`() {
        val r = rule("grab")
        listOf(TransactionType.TRANSFER, TransactionType.ADJUSTMENT, TransactionType.REFUND).forEach {
            assertNull(classifyRow("grab", r, type = it))
        }
    }

    @Test
    fun `rule name wins, else merchant stays`() {
        assertEquals("Grab", classifyRow("x", rule("x", merchantName = "Grab"), merchant = "GRAB VN")?.merchantName)
        assertEquals("GRAB VN", classifyRow("x", rule("x"), merchant = "GRAB VN")?.merchantName)
        assertNull(classifyRow("x", rule("x"))?.merchantName)
    }

    @Test
    fun `rule toString hides keyword and name`() {
        assertEquals("KeywordRule(id=r1)", rule("secretword", merchantName = "Secret Shop", id = "r1").toString())
    }

    @Test
    fun `rule match toString hides the merchant name`() {
        val match = classifyRow("x", rule("x", id = "r1"), merchant = "SECRET SHOP")
        assertEquals("RuleMatch(ruleId=r1, categoryId=food)", match.toString())
    }

    @Test
    fun `guessKeyword takes the merchant`() {
        assertEquals("MOMO HO CHI MINH", guessKeyword("MOMO HO CHI MINH", "anything"))
    }

    @Test
    fun `guessKeyword takes the words before a transfer phrase`() {
        assertEquals("NGUYEN VAN A", guessKeyword(null, "NGUYEN VAN A chuyen tien"))
        assertEquals("Nguyen Van A", guessKeyword(null, "Nguyen Van A chuyển khoản"))
    }

    @Test
    fun `guessKeyword cuts a long note at a space`() {
        val guess = guessKeyword(null, "thanh toan hoa don dien thang nam cho nha tro")!!
        assertTrue(guess.length <= 30)
        assertEquals("thanh toan hoa don dien thang", guess)
    }

    @Test
    fun `guessKeyword is null when both are blank`() {
        assertNull(guessKeyword(null, null))
        assertNull(guessKeyword("  ", " \n"))
    }
}
