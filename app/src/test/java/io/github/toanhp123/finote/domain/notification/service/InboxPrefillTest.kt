package io.github.toanhp123.finote.domain.notification.service

import io.github.toanhp123.finote.domain.account.model.Account
import io.github.toanhp123.finote.domain.account.model.AccountIdentifier
import io.github.toanhp123.finote.domain.account.model.IdentifierKind
import io.github.toanhp123.finote.domain.account.testAccount
import io.github.toanhp123.finote.domain.category.model.Category
import io.github.toanhp123.finote.domain.category.model.KeywordMatchType
import io.github.toanhp123.finote.domain.category.model.KeywordRule
import io.github.toanhp123.finote.domain.category.testCategory
import io.github.toanhp123.finote.domain.common.normalizeKeyword
import io.github.toanhp123.finote.domain.notification.model.FailedInboxItem
import io.github.toanhp123.finote.domain.notification.model.ParsedFields
import io.github.toanhp123.finote.domain.transaction.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private const val PKG = "fake.bank"
private const val ABROAD_TITLE = "- KRW 11,111/VND 1,111,111"
private const val ABROAD_CONTENT =
    "1% Foreign Transaction Fee (Conditions Apply)\nCredit card: 1 .... .... 1111\n" +
        "Converted amount: VND 1,111,111\nAvailable limit: VND 111,111\n" +
        "Payment at (JOO)GIMCHISEUKOOL on 11/11/1111 at 11:11:11"

private fun item(
    reason: String = FAIL_NO_RULE_MATCHED,
    title: String = ABROAD_TITLE,
    content: String = ABROAD_CONTENT,
    parsed: ParsedFields? = null,
    postTime: Long = 5_000L,
) = FailedInboxItem(
    id = "i",
    logIds = listOf("i"),
    packageName = PKG,
    title = title,
    content = content,
    postTime = postTime,
    receivedAt = postTime + 5,
    reason = reason,
    parsed = parsed,
    isHidden = false,
    isKeywordHidden = false,
)

private fun parsed(
    amount: Long = 150_000L,
    type: TransactionType = TransactionType.EXPENSE,
    mask: String? = "1234",
    merchant: String? = "Cafe",
    note: String? = null,
) = ParsedFields(amount, type, mask, null, merchant, note)

private fun identifier(
    accountId: String,
    mask: String,
    kind: IdentifierKind = IdentifierKind.ACCOUNT,
    packageName: String = PKG,
) = AccountIdentifier(packageName, kind, mask, accountId)

private fun account(
    id: String,
    archived: Boolean = false,
    ignored: Boolean = false,
    deletedAt: Long? = null,
): Account =
    testAccount(id = id, name = "Name $id").copy(
        bankPackageName = PKG,
        isArchived = archived,
        isIgnored = ignored,
        deletedAt = deletedAt,
    )

private fun prefill(
    item: FailedInboxItem,
    identifiers: List<AccountIdentifier>,
    accounts: List<Account>,
    rules: List<KeywordRule> = emptyList(),
    categories: Map<String, Category> = emptyMap(),
) = prefillFor(item, identifiers, accounts, rules, categories)

private fun rule(
    keyword: String,
    merchantName: String? = null,
) = KeywordRule(
    id = "r-$keyword",
    keyword = keyword,
    normalizedKeyword = normalizeKeyword(keyword),
    matchType = KeywordMatchType.CONTAINS,
    merchantName = merchantName,
    categoryId = "food",
    priority = 0,
    isUserDefined = false,
    isActive = true,
    version = 0,
    createdAt = 0,
    updatedAt = 0,
)

private val categories = listOf(testCategory("food")).associateBy { it.id }

class InboxPrefillTest {
    @Test
    fun `abroad alert prefills expense 1111111 and merchant`() {
        val prefill = prefill(item(), emptyList(), emptyList())

        assertEquals(TransactionType.EXPENSE, prefill.type)
        assertEquals(1_111_111L, prefill.amount)
        assertEquals("(JOO)GIMCHISEUKOOL", prefill.note)
        assertEquals(5_000L, prefill.timestamp)
        assertNull(prefill.accountId)
    }

    @Test
    fun `card mask with no card account gives no account`() {
        val prefill = prefill(item(), listOf(identifier("a", "9999")), listOf(account("a")))

        assertNull(prefill.accountId)
        assertNull(prefill.archivedAccountName)
    }

    @Test
    fun `card mask with a card identifier gives that account`() {
        val ids = listOf(identifier("a", "9999"), identifier("c", "1111", IdentifierKind.CARD))

        val prefill = prefill(item(), ids, listOf(account("a"), account("c")))

        assertEquals("c", prefill.accountId)
    }

    @Test
    fun `a lone account of the package is not a fallback`() {
        assertNull(prefill(item(), emptyList(), listOf(account("a"))).accountId)
    }

    @Test
    fun `an identifier of another package does not match`() {
        val ids = listOf(identifier("a", "1111", packageName = "other.bank"))

        assertNull(prefill(item(), ids, listOf(account("a"))).accountId)
    }

    @Test
    fun `two owners give no account`() {
        val ids = listOf(identifier("a", "1111"), identifier("c", "1111", IdentifierKind.CARD))

        val prefill = prefill(item(), ids, listOf(account("a"), account("c")))

        assertNull(prefill.accountId)
        assertNull(prefill.archivedAccountName)
    }

    @Test
    fun `two kinds of one owner are one owner`() {
        val ids = listOf(identifier("a", "1111"), identifier("a", "1111", IdentifierKind.CARD))

        assertEquals("a", prefill(item(), ids, listOf(account("a"))).accountId)
    }

    @Test
    fun `archived owner gives no account and its name`() {
        val prefill = prefill(item(), listOf(identifier("a", "1111")), listOf(account("a", archived = true)))

        assertNull(prefill.accountId)
        assertEquals("Name a", prefill.archivedAccountName)
    }

    @Test
    fun `ignored or deleted owner gives no account and no name`() {
        val ids = listOf(identifier("a", "1111"))

        listOf(account("a", ignored = true), account("a", deletedAt = 1L)).forEach {
            val prefill = prefill(item(), ids, listOf(it))
            assertNull(prefill.accountId)
            assertNull(prefill.archivedAccountName)
        }
    }

    @Test
    fun `masks match on the last 4 digits of both sides`() {
        val failed = item(FAIL_AMBIGUOUS_ACCOUNT, parsed = parsed(mask = "9871234"))

        assertEquals("a", prefill(failed, listOf(identifier("a", "1234")), listOf(account("a"))).accountId)
        assertEquals("b", prefill(failed, listOf(identifier("b", "5551234")), listOf(account("b"))).accountId)
    }

    @Test
    fun `a long parsed mask with a 4 digit and a long identifier of two accounts gives no account`() {
        val failed = item(FAIL_AMBIGUOUS_ACCOUNT, parsed = parsed(mask = "9871234"))
        val ids = listOf(identifier("a", "1234"), identifier("b", "5551234"))

        assertNull(prefill(failed, ids, listOf(account("a"), account("b"))).accountId)
    }

    @Test
    fun `invalid_amount with null parsed amount falls back to text`() {
        val prefill = prefill(item(FAIL_INVALID_AMOUNT), emptyList(), emptyList())

        assertEquals(1_111_111L, prefill.amount)
    }

    @Test
    fun `a parsed amount of 0 falls back to text`() {
        val prefill = prefill(item(FAIL_INVALID_AMOUNT, parsed = parsed(amount = 0L)), emptyList(), emptyList())

        assertEquals(1_111_111L, prefill.amount)
    }

    @Test
    fun `invalid_time gives no timestamp`() {
        val prefill = prefill(item(FAIL_INVALID_TIME, parsed = parsed()), emptyList(), emptyList())

        assertNull(prefill.timestamp)
    }

    @Test
    fun `settlement failure uses parsed fields`() {
        val failed = item(FAIL_ACCOUNT_ARCHIVED, parsed = parsed(type = TransactionType.INCOME))

        val prefill = prefill(failed, listOf(identifier("a", "1234")), listOf(account("a")))

        assertEquals(TransactionType.INCOME, prefill.type)
        assertEquals(150_000L, prefill.amount)
        assertEquals("a", prefill.accountId)
        assertEquals("Cafe", prefill.note)
        assertEquals(5_000L, prefill.timestamp)
    }

    @Test
    fun `settlement failure never reads the mask from text`() {
        val failed = item(FAIL_AMBIGUOUS_ACCOUNT, parsed = parsed(mask = null))

        assertNull(prefill(failed, listOf(identifier("a", "1111")), listOf(account("a"))).accountId)
    }

    @Test
    fun `note falls back from merchant to note to text to empty`() {
        val noMerchant = parsed(merchant = null, note = "Lunch")

        assertEquals("Lunch", prefill(item(FAIL_INVALID_TIME, parsed = noMerchant), emptyList(), emptyList()).note)
        assertEquals("", prefill(item(content = "Chi 150,000d"), emptyList(), emptyList()).note)
    }

    @Test
    fun `unsigned text gives expense`() {
        val prefill = prefill(item(title = "Thong bao", content = "GD 150,000 VND"), emptyList(), emptyList())

        assertEquals(TransactionType.EXPENSE, prefill.type)
    }

    @Test
    fun `plus sign gives a known income`() {
        val prefill = prefill(item(title = "+150,000 VND", content = "GD"), emptyList(), emptyList())

        assertEquals(TransactionType.INCOME, prefill.type)
    }

    @Test
    fun `prefill suggests the category of a matching rule`() {
        val failed = item(FAIL_INVALID_TIME, parsed = parsed(merchant = "Cafe Cong"))
        val rules = listOf(rule("cafe", merchantName = "Cong Ca Phe"))

        val prefill = prefill(failed, emptyList(), emptyList(), rules, categories)

        assertEquals("food", prefill.categoryId)
        assertEquals("Cong Ca Phe", prefill.merchantName)
    }

    @Test
    fun `without a matching rule the merchant is the parsed one and there is no category`() {
        val failed = item(FAIL_INVALID_TIME, parsed = parsed(merchant = "Cafe Cong"))

        val prefill = prefill(failed, emptyList(), emptyList(), listOf(rule("grab")), categories)

        assertNull(prefill.categoryId)
        assertEquals("Cafe Cong", prefill.merchantName)
    }

    @Test
    fun `no_rule_matched item matches on title and text`() {
        val failed = item(title = "Thong bao", content = "GD 150,000 VND tai GRAB HCM")

        val prefill = prefill(failed, emptyList(), emptyList(), listOf(rule("grab")), categories)

        assertEquals("food", prefill.categoryId)
        assertNull(prefill.merchantName)
    }

    @Test
    fun `a rule of the other type gives no category`() {
        val failed = item(title = "+150,000 VND", content = "GD tai GRAB")

        val prefill = prefill(failed, emptyList(), emptyList(), listOf(rule("grab")), categories)

        assertEquals(TransactionType.INCOME, prefill.type)
        assertNull(prefill.categoryId)
    }

    @Test
    fun `prefill keeps the note as today`() {
        val failed = item(FAIL_INVALID_TIME, parsed = parsed(merchant = "Cafe Cong"))
        val rules = listOf(rule("cafe", merchantName = "Other"))

        val withRule = prefill(failed, emptyList(), emptyList(), rules, categories)

        assertEquals("Cafe Cong", withRule.note)
    }
}
