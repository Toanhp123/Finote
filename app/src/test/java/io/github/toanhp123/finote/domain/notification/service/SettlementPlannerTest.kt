package io.github.toanhp123.finote.domain.notification.service

import io.github.toanhp123.finote.domain.account.model.Account
import io.github.toanhp123.finote.domain.account.model.AccountIdentifier
import io.github.toanhp123.finote.domain.account.model.AccountType
import io.github.toanhp123.finote.domain.account.model.IdentifierKind
import io.github.toanhp123.finote.domain.account.testAccount
import io.github.toanhp123.finote.domain.notification.model.AccountTarget
import io.github.toanhp123.finote.domain.notification.model.NotificationLog
import io.github.toanhp123.finote.domain.notification.model.NotificationStatus
import io.github.toanhp123.finote.domain.notification.model.ParsedFields
import io.github.toanhp123.finote.domain.notification.model.ParserRule
import io.github.toanhp123.finote.domain.notification.model.SettlementDecision
import io.github.toanhp123.finote.domain.notification.model.SettlementDecision.Book
import io.github.toanhp123.finote.domain.notification.model.SettlementDecision.Fail
import io.github.toanhp123.finote.domain.notification.model.SettlementDecision.Ignore
import io.github.toanhp123.finote.domain.notification.model.SettlementDecision.Repost
import io.github.toanhp123.finote.domain.notification.testLog
import io.github.toanhp123.finote.domain.notification.testRule
import io.github.toanhp123.finote.domain.transaction.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettlementPlannerTest {
    private val vcb = "com.vcb"
    private val momo = "com.momo"
    private val t0 = 1_800_000_000_000L // 2027-01-15, inside the allowed time range
    private val now = t0 + 10_000_000L
    private val rules = mapOf("r-vcb" to rule("r-vcb", vcb, null), "r-momo" to rule("r-momo", momo, AccountType.WALLET))
    private val newBank = AccountTarget.New(vcb, null, vcb, AccountType.BANK, IdentifierKind.ACCOUNT)
    private var seq = 0

    private fun rule(
        id: String,
        pkg: String,
        type: AccountType?,
    ) = testRule(
        id = id,
        packageName = pkg,
        bankName = pkg,
        updatedAt = 1L,
        accountType = type,
    )

    @Suppress("LongParameterList") // why: test helper, every field varies across tests
    private fun log(
        pkg: String,
        postTime: Long,
        ruleId: String? = if (pkg == vcb) "r-vcb" else "r-momo",
        amount: Long = 50_000L,
        type: TransactionType = TransactionType.EXPENSE,
        mask: String? = null,
        balance: Long? = null,
        merchant: String? = null,
        text: String = "t${seq++}",
        parsed: ParsedFields? = ParsedFields(amount, type, mask, balance, merchant, null),
        status: NotificationStatus = NotificationStatus.PENDING,
        key: String? = null,
        content: String = text,
        linkedTx: String? = null,
    ) = testLog(
        id = "id${seq++}",
        packageName = pkg,
        title = text,
        content = content,
        contentHash = text,
        postTime = postTime,
        status = status,
        matchedRuleId = ruleId,
        parsed = parsed,
        linkedTransactionId = linkedTx,
        notificationKey = key,
    )

    private fun plan(
        vararg pending: NotificationLog,
        settled: List<NotificationLog> = emptyList(),
        accounts: List<Account> = emptyList(),
        identifiers: List<AccountIdentifier> = emptyList(),
        at: Long = now,
        ruleMap: Map<String, ParserRule> = rules,
    ) = planSettlement(pending.toList(), settled, accounts, identifiers, ruleMap, at)

    private fun old(offset: Long = 0) = now - 3 * 3_600_000L + offset

    @Test
    fun `bank then wallet pair`() {
        val bank = log(vcb, old(0), merchant = "Bank shop")
        val wallet = log(momo, old(5_000), merchant = "Wallet shop")
        val book = plan(bank, wallet).single() as Book
        assertEquals(bank, book.bankSide)
        assertEquals(wallet, book.walletSide)
        assertEquals("Wallet shop", book.merchant)
    }

    @Test
    fun `wallet then bank pair`() {
        val wallet = log(momo, old(0), merchant = "Wallet shop")
        val bank = log(vcb, old(5_000), merchant = "Bank shop")
        val book = plan(wallet, bank).single() as Book
        assertEquals(bank, book.bankSide)
        assertEquals(wallet, book.walletSide)
        assertEquals("Wallet shop", book.merchant)
    }

    @Test
    fun `wallet without merchant falls back to the bank values`() {
        val bank = log(vcb, old(0), merchant = "Bank shop")
        val wallet = log(momo, old(1_000))
        assertEquals("Bank shop", (plan(bank, wallet).single() as Book).merchant)
    }

    @Test
    fun `same package twice stays two`() {
        val decisions = plan(log(vcb, old(0)), log(vcb, old(5_000)))
        assertEquals(2, decisions.size)
        assertTrue(decisions.all { it is Book && it.walletSide == null })
    }

    @Test
    fun `31 s apart no pair`() {
        val decisions = plan(log(vcb, old(0)), log(momo, old(31_000)))
        assertEquals(listOf(null, null), decisions.map { (it as Book).walletSide })
    }

    @Test
    fun `30 s apart pairs`() {
        assertEquals(1, plan(log(vcb, old(0)), log(momo, old(30_000))).size)
    }

    @Test
    fun `bank and bank no pair`() {
        val other = "com.tcb"
        val decisions = plan(log(vcb, old(0)), log(other, old(1_000), ruleId = null))
        assertEquals(2, decisions.size)
    }

    @Test
    fun `opposite direction no pair`() {
        val decisions = plan(log(vcb, old(0)), log(momo, old(1_000), type = TransactionType.INCOME))
        assertEquals(2, decisions.size)
    }

    @Test
    fun `nearest pair wins`() {
        val first = log(vcb, t0)
        val second = log(vcb, t0 + 20_000)
        val wallet = log(momo, t0 + 21_000)
        val decisions = plan(first, second, wallet, at = t0 + 60_000)
        assertEquals(
            listOf(
                Book(first, null, newBank, null, null, rules["r-vcb"]),
                Book(second, wallet, newBank, null, null, rules["r-vcb"]),
            ),
            decisions,
        )
    }

    @Test
    fun `losing a pair does not burn the other log`() {
        val b1 = log(vcb, t0)
        val b2 = log(vcb, t0 + 2_000)
        val w1 = log(momo, t0 + 3_000)
        val w2 = log(momo, t0 + 10_000)
        val books = plan(b1, b2, w1, w2, at = t0 + 100_000).map { it as Book }
        assertEquals(
            setOf(b2 to w1, b1 to w2),
            books.map { it.bankSide to it.walletSide }.toSet(),
        )
        assertEquals(2, books.size)
    }

    @Test
    fun `equal distance picks the older bank`() {
        val older = log(vcb, t0)
        val wallet = log(momo, t0 + 10_000)
        val newer = log(vcb, t0 + 20_000)
        val books = plan(older, wallet, newer, at = t0 + 100_000).map { it as Book }
        assertEquals(listOf(older to wallet, newer to null), books.map { it.bankSide to it.walletSide })
    }

    @Test
    fun `not due waits`() {
        assertTrue(plan(log(vcb, now - 10_000)).isEmpty())
    }

    @Test
    fun `due log pairs with a not due partner`() {
        val bank = log(vcb, now - 35_000)
        val wallet = log(momo, now - 10_000)
        val book = plan(bank, wallet).single() as Book
        assertEquals(wallet, book.walletSide)
    }

    @Test
    fun `repost with balance within 10 min`() {
        val original = log(vcb, old(0), balance = 1L, text = "same")
        val again = log(vcb, old(600_000), balance = 1L, text = "same")
        val decisions = plan(original, again)
        assertEquals(listOf(original, again), listOf((decisions[0] as Book).bankSide, (decisions[1] as Repost).log))
        assertEquals(original, (decisions[1] as Repost).original)
    }

    @Test
    fun `repost with balance after 10 min booked`() {
        val first = log(vcb, old(0), balance = 1L, text = "same")
        val decisions = plan(first, log(vcb, old(600_001), balance = 1L, text = "same"))
        assertTrue(decisions.all { it is Book })
    }

    @Test
    fun `no balance within 3 s repost`() {
        val decisions = plan(log(vcb, old(0), text = "same"), log(vcb, old(3_000), text = "same"))
        assertTrue(decisions[0] is Book && decisions[1] is Repost)
    }

    @Test
    fun `no balance 4 s booked`() {
        val decisions = plan(log(vcb, old(0), text = "same"), log(vcb, old(4_000), text = "same"))
        assertTrue(decisions.all { it is Book })
    }

    @Test
    fun `repost of a settled original`() {
        val settled = log(vcb, old(0), text = "same", status = NotificationStatus.CONFIRMED)
        val again = log(vcb, old(1_000), text = "same")
        assertEquals(listOf(Repost(again, settled)), plan(again, settled = listOf(settled)))
    }

    @Test
    fun `repost of a failed settled original`() {
        val settled = log(vcb, old(0), text = "same", status = NotificationStatus.FAILED)
        val again = log(vcb, old(1_000), text = "same")
        assertEquals(listOf(Repost(again, settled)), plan(again, settled = listOf(settled)))
    }

    @Test
    fun `repost of a not due original waits`() {
        val original = log(vcb, now - 10_000, text = "same")
        val again = log(vcb, now - 9_000, text = "same")
        assertTrue(plan(original, again).isEmpty())
    }

    @Test
    fun `repost chain points at the first original`() {
        val first = log(vcb, old(0), text = "same")
        val second = log(vcb, old(1_000), text = "same")
        val third = log(vcb, old(2_000), text = "same")
        val decisions = plan(third, first, second)
        assertEquals(listOf(Repost(second, first), Repost(third, first)), decisions.drop(1))
    }

    @Test
    fun `not due repost cannot pair`() {
        val bank = log(vcb, t0, balance = 1L, text = "same")
        val wallet = log(momo, t0 + 10_000)
        val again = log(vcb, t0 + 19_000, balance = 1L, text = "same")
        val decisions = plan(bank, wallet, again, at = t0 + 40_000)
        assertEquals(bank, (decisions[0] as Book).bankSide)
        assertEquals(wallet, (decisions[0] as Book).walletSide)
        assertEquals(listOf(Repost(again, bank)), decisions.drop(1))
    }

    @Test
    fun `pair with unmappable bank fails both`() {
        val accounts =
            listOf("a", "b").map {
                testAccount(id = it).copy(bankPackageName = vcb)
            }
        val bank = log(vcb, old(0))
        val wallet = log(momo, old(1_000))
        assertEquals(listOf(Fail(bank, wallet, FAIL_AMBIGUOUS_ACCOUNT)), plan(bank, wallet, accounts = accounts))
    }

    private fun ignoredAccount() = testAccount(id = "ign").copy(bankPackageName = vcb, isIgnored = true)

    @Test
    fun `ignored account gives Ignore`() {
        val bank = log(vcb, old(0), mask = "1234")
        assertEquals(listOf(Ignore(bank, null)), plan(bank, accounts = listOf(ignoredAccount())))
    }

    @Test
    fun `wallet and bank pair on an ignored account ignores both`() {
        val bank = log(vcb, old(0), mask = "1234")
        val wallet = log(momo, old(1_000))
        assertEquals(listOf(Ignore(bank, wallet)), plan(bank, wallet, accounts = listOf(ignoredAccount())))
    }

    @Test
    fun `repost of an ignored log is a Repost`() {
        val first = log(vcb, old(0), mask = "1234", text = "same")
        val again = log(vcb, old(1_000), mask = "1234", text = "same")
        assertEquals(
            listOf(Ignore(first, null), Repost(again, first)),
            plan(first, again, accounts = listOf(ignoredAccount())),
        )
    }

    @Test
    fun `invalid amount fails`() {
        val zero = log(vcb, old(0), amount = 0L)
        val unparsed = log(vcb, old(1_000), parsed = null)
        assertEquals(
            listOf<SettlementDecision>(
                Fail(zero, null, FAIL_INVALID_AMOUNT),
                Fail(unparsed, null, FAIL_INVALID_AMOUNT),
            ),
            plan(zero, unparsed),
        )
    }

    @Test
    fun `amount at the cap books`() {
        val capped = log(vcb, old(0), amount = MAX_AMOUNT)
        assertEquals(capped, (plan(capped).single() as Book).bankSide)
    }

    @Test
    fun `amount over the cap or with 19 digits fails`() {
        val over = log(vcb, old(0), amount = MAX_AMOUNT + 1)
        val huge = log(vcb, old(1_000), amount = Long.MAX_VALUE)
        assertEquals(
            listOf<SettlementDecision>(Fail(over, null, FAIL_INVALID_AMOUNT), Fail(huge, null, FAIL_INVALID_AMOUNT)),
            plan(over, huge),
        )
    }

    @Test
    fun `missing rule books as bank named after the package`() {
        val log = log(vcb, old(0), ruleId = null)
        val book = plan(log, ruleMap = emptyMap()).single() as Book
        assertEquals(newBank, book.account)
        assertNull(book.rule)
    }

    @Test
    fun `old rescanned logs pair at once`() {
        val decisions = plan(log(vcb, now - 3 * 3_600_000L), log(momo, now - 3 * 3_600_000L + 2_000))
        assertEquals(1, decisions.size)
        assertTrue((decisions.single() as Book).walletSide != null)
    }

    @Test
    fun `far future post time fails at once`() {
        val far = log(vcb, now + 400L * 86_400_000L)
        assertEquals(listOf(Fail(far, null, FAIL_INVALID_TIME)), plan(far))
    }

    @Test
    fun `post time before 2000 fails at once`() {
        val ancient = log(vcb, 946_684_800_000L - 1)
        assertEquals(listOf(Fail(ancient, null, FAIL_INVALID_TIME)), plan(ancient))
    }

    @Test
    fun `invalid time log never pairs with a valid one`() {
        val bank = log(vcb, old(0))
        val wallet = log(momo, now + 400L * 86_400_000L)
        val decisions = plan(bank, wallet)
        assertEquals(listOf(Fail(wallet, null, FAIL_INVALID_TIME)), decisions.filterIsInstance<Fail>())
        assertEquals(null, (decisions.single { it is Book } as Book).walletSide)
    }

    private val k = "k"
    private val min = 60_000L

    private fun booked(decisions: List<SettlementDecision>) = decisions.filterIsInstance<Book>().map { it.bankSide }

    private fun reposts(decisions: List<SettlementDecision>) =
        decisions.filterIsInstance<Repost>().map { it.log to it.original }

    @Test
    fun `key repost with an extra line books once`() {
        val a = log(vcb, old(0), balance = 1000L, key = k, text = "x")
        val b = log(vcb, old(2_000), balance = 1000L, key = k, text = "x", content = "x\nmore")
        val decisions = plan(a, b)
        assertEquals(listOf(a), booked(decisions))
        assertEquals(listOf(b to a), reposts(decisions))
    }

    @Test
    fun `first without balance then with balance books once`() {
        val a = log(vcb, old(0), key = k)
        val b = log(vcb, old(2_000), balance = 1000L, key = k)
        val decisions = plan(a, b)
        assertEquals(listOf(a), booked(decisions))
        assertEquals(listOf(b to a), reposts(decisions))
    }

    @Test
    fun `no-balance repost after 3 s books once`() {
        val a = log(vcb, old(0), key = k)
        val b = log(vcb, old(5_000), key = k)
        val decisions = plan(a, b)
        assertEquals(listOf(a), booked(decisions))
        assertEquals(listOf(b to a), reposts(decisions))
    }

    @Test
    fun `different balances with the same key book twice`() {
        val a = log(vcb, old(0), balance = 1000L, key = k)
        val b = log(vcb, old(2_000), balance = 950L, key = k)
        assertEquals(listOf(a, b), booked(plan(a, b)))
    }

    @Test
    fun `same key after 10 min books twice`() {
        val a = log(vcb, old(0), key = k)
        val b = log(vcb, old(10 * min + 1), key = k)
        assertEquals(listOf(a, b), booked(plan(a, b)))
    }

    @Test
    fun `different amount or mask with the same key books twice`() {
        val a = log(vcb, old(0), mask = "1234", key = k)
        val amount = log(vcb, old(2_000), mask = "1234", amount = 60_000L, key = k)
        val mask = log(vcb, old(4_000), mask = "9999", key = k)
        assertEquals(listOf(a, amount, mask), booked(plan(a, amount, mask)))
    }

    @Test
    fun `a later parsed version of a log booked from the inbox is a repost whatever its mask`() {
        val inbox = log(vcb, old(0), ruleId = null, key = k, status = NotificationStatus.CONFIRMED, linkedTx = "tx")
        val update = log(vcb, old(2_000), mask = "1234", balance = 1000L, key = k)
        assertEquals(listOf(Repost(update, inbox)), plan(update, settled = listOf(inbox)))
    }

    @Test
    fun `a ruled root without a mask still needs an equal mask`() {
        val a = log(vcb, old(0), key = k, status = NotificationStatus.CONFIRMED, linkedTx = "tx")
        val b = log(vcb, old(2_000), mask = "1234", key = k)
        assertEquals(listOf(b), booked(plan(b, settled = listOf(a))))
    }

    @Test
    fun `null keys use only the text rule`() {
        val a = log(vcb, old(0))
        val b = log(vcb, old(1_000))
        assertEquals(listOf(a, b), booked(plan(a, b)))
        val c = log(vcb, old(0), text = "same")
        val d = log(vcb, old(2_000), text = "same")
        assertEquals(listOf(d to c), reposts(plan(c, d)))
    }

    @Test
    fun `unparsed first version is never a root`() {
        val a = log(vcb, old(0), key = k, parsed = null, status = NotificationStatus.FAILED)
        val b = log(vcb, old(2_000), key = k)
        assertEquals(listOf(b), booked(plan(b, settled = listOf(a))))
    }

    @Test
    fun `reparsed first version joins the later booked update`() {
        val b = log(vcb, old(2_000), key = k, status = NotificationStatus.CONFIRMED, linkedTx = "tx")
        val a = log(vcb, old(0), key = k)
        assertEquals(listOf(Repost(a, b)), plan(a, settled = listOf(b)))
    }

    @Test
    fun `settled same key one day later does not match`() {
        val b = log(vcb, old(86_400_000L), key = k, status = NotificationStatus.CONFIRMED)
        val a = log(vcb, old(0), key = k)
        assertEquals(listOf(a), booked(plan(a, settled = listOf(b))))
    }

    @Test
    fun `null balance does not bridge two balances`() {
        val a = log(vcb, old(0), balance = 1000L, key = k)
        val b = log(vcb, old(2 * min), key = k)
        val c = log(vcb, old(4 * min), balance = 950L, key = k)
        val decisions = plan(a, b, c)
        assertEquals(listOf(a, c), booked(decisions))
        assertEquals(listOf(b to a), reposts(decisions))
    }

    @Test
    fun `window is measured from the root`() {
        val a = log(vcb, old(0), key = k)
        val b = log(vcb, old(8 * min), key = k)
        val c = log(vcb, old(16 * min), key = k)
        val decisions = plan(a, b, c)
        assertEquals(listOf(a, c), booked(decisions))
        assertEquals(listOf(b to a), reposts(decisions))
    }

    @Test
    fun `nearest root wins`() {
        val before = log(vcb, old(-5 * min), key = k, status = NotificationStatus.CONFIRMED)
        val after = log(vcb, old(min), key = k, status = NotificationStatus.CONFIRMED)
        val a = log(vcb, old(0), key = k)
        assertEquals(listOf(Repost(a, after)), plan(a, settled = listOf(before, after)))
    }

    @Test
    fun `deduplicated sibling balance blocks a conflicting update`() {
        val root = log(vcb, old(0), key = k, status = NotificationStatus.CONFIRMED, linkedTx = "tx")
        val sibling =
            log(vcb, old(1_000), balance = 1000L, key = k, status = NotificationStatus.DEDUPLICATED, linkedTx = "tx")
        val same = log(vcb, old(2_000), balance = 1000L, key = k)
        val other = log(vcb, old(3_000), balance = 950L, key = k)
        assertEquals(listOf(Repost(same, root)), plan(same, settled = listOf(root, sibling)))
        assertEquals(listOf(other), booked(plan(other, settled = listOf(root, sibling))))
    }

    @Test
    fun `update of a paired wallet notification is a repost`() {
        val bank = log(vcb, old(0), key = "kb", status = NotificationStatus.CONFIRMED, linkedTx = "tx")
        val wallet = log(momo, old(1_000), key = "kw", status = NotificationStatus.DEDUPLICATED, linkedTx = "tx")
        val update = log(momo, old(2_000), key = "kw")
        assertEquals(listOf(Repost(update, wallet)), plan(update, settled = listOf(bank, wallet)))
    }

    @Test
    fun `window of a paired wallet cluster counts from the wallet, not its repost`() {
        val bank = log(vcb, old(0), key = "kb", status = NotificationStatus.CONFIRMED, linkedTx = "tx")
        val wallet = log(momo, old(0), key = "kw", status = NotificationStatus.DEDUPLICATED, linkedTx = "tx")
        val repost = log(momo, old(8 * min), key = "kw", status = NotificationStatus.DEDUPLICATED, linkedTx = "tx")
        val next = log(momo, old(16 * min), key = "kw")
        // 16 min after the wallet: a new payment, even though it is 8 min after the repost.
        assertEquals(0, reposts(plan(next, settled = listOf(bank, wallet, repost))).size)
    }

    @Test
    fun `key repost of a deduplicated log still points at its root`() {
        val root = log(vcb, old(0), key = k, status = NotificationStatus.CONFIRMED, linkedTx = "tx")
        val repost = log(vcb, old(1_000), key = k, status = NotificationStatus.DEDUPLICATED, linkedTx = "tx")
        val update = log(vcb, old(1_500), key = k)
        assertEquals(listOf(Repost(update, root)), plan(update, settled = listOf(root, repost)))
    }

    @Test
    fun `different keys with identical text use the text rule`() {
        val a = log(vcb, old(0), key = "k1", text = "same")
        val b = log(vcb, old(2_000), key = "k2", text = "same")
        assertEquals(listOf(b to a), reposts(plan(a, b)))
    }
}
