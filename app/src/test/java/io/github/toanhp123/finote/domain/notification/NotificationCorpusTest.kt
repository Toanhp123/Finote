package io.github.toanhp123.finote.domain.notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset

class NotificationCorpusTest {
    @Test
    fun `corpus loads correctly and has at least 30 items`() {
        val items = NotificationCorpusFixtures.all
        assertTrue("Corpus size should be >= 30, was ${items.size}", items.size >= 30)
    }

    @Test
    fun `corpus covers Vietcombank, MB Bank, MoMo, and Techcombank`() {
        val vcbItems = NotificationCorpusFixtures.byPackage("com.VCB")
        val mbItems = NotificationCorpusFixtures.byPackage("com.mbmobile")
        val momoItems = NotificationCorpusFixtures.byPackage("com.mservice.momotransfer")
        val tcbItems = NotificationCorpusFixtures.byPackage("vn.com.techcombank.bb.app")

        assertTrue("VCB items should be >= 10, was ${vcbItems.size}", vcbItems.size >= 10)
        assertTrue("MB items should be >= 10, was ${mbItems.size}", mbItems.size >= 10)
        assertTrue("MoMo items should be >= 10, was ${momoItems.size}", momoItems.size >= 10)
        assertTrue("TCB items should be >= 10, was ${tcbItems.size}", tcbItems.size >= 10)
    }

    @Test
    fun `all items have unique ids and non-blank core fields`() {
        val items = NotificationCorpusFixtures.all
        val ids = items.map { it.id }
        assertEquals("All IDs must be unique", ids.distinct().size, ids.size)

        for (item in items) {
            assertTrue("ID must not be blank", item.id.isNotBlank())
            assertTrue("PackageName must not be blank", item.packageName.isNotBlank())
            assertTrue("Title must not be blank", item.title.isNotBlank())
            assertTrue("Content must not be blank", item.content.isNotBlank())
            assertTrue("postTime must be positive", item.postTime > 0L)
        }
    }

    @Test
    fun `financial items have positive amount, valid sign and transactionType`() {
        val financial = NotificationCorpusFixtures.financialTransactions()
        assertTrue("Financial items must not be empty", financial.isNotEmpty())

        val validSigns = setOf("+", "-")
        val validTypes = setOf("EXPENSE", "INCOME", "TRANSFER", "REFUND")

        for (item in financial) {
            assertFalse("Financial item should not be ignored", item.expected.shouldIgnore)
            val amount = item.expected.amount
            assertNotNull("Amount must not be null for financial item ${item.id}", amount)
            assertTrue("Amount must be > 0 for item ${item.id}", amount != null && amount > 0L)
            assertTrue(
                "Sign must be '+' or '-' for item ${item.id}, got ${item.expected.sign}",
                item.expected.sign in validSigns,
            )
            assertTrue(
                "TransactionType must be valid for item ${item.id}, got ${item.expected.transactionType}",
                item.expected.transactionType in validTypes,
            )
        }
    }

    @Test
    fun `ignored and spam items have valid ignore reason`() {
        val ignored = NotificationCorpusFixtures.ignoredOrSpam()
        assertTrue("Ignored/spam items must not be empty", ignored.isNotEmpty())

        for (item in ignored) {
            assertTrue("Item must be flagged shouldIgnore", item.expected.shouldIgnore)
            val reason = item.expected.ignoreReason
            assertNotNull("Ignore reason must not be null for item ${item.id}", reason)
            assertTrue("Ignore reason must not be blank for item ${item.id}", reason?.isNotBlank() == true)
        }
    }

    @Test
    fun `contains transfer pairing pair for VCB debit and MoMo topup`() {
        val vcbDebit = NotificationCorpusFixtures.all.firstOrNull { it.id == "vcb-pairing-debit-15" }
        val momoTopup = NotificationCorpusFixtures.all.firstOrNull { it.id == "momo-pairing-topup-06" }

        assertNotNull("VCB pairing debit item must exist", vcbDebit)
        assertNotNull("MoMo pairing topup item must exist", momoTopup)
        assertEquals(vcbDebit?.expected?.amount, momoTopup?.expected?.amount)
        assertEquals("TRANSFER", vcbDebit?.expected?.transactionType)
        assertEquals("TRANSFER", momoTopup?.expected?.transactionType)
    }

    @Test
    fun `balance chains are consistent per package and account mask`() {
        val groups =
            NotificationCorpusFixtures
                .financialTransactions()
                .filter { it.expected.accountMask != null }
                .groupBy { it.packageName to it.expected.accountMask }
        for ((key, group) in groups) {
            var prev: Long? = null
            for (item in group.sortedBy { it.postTime }) {
                val e = item.expected
                val balance = e.postBalance
                val delta = (e.amount ?: 0L) * if (e.sign == "-") -1L else 1L
                if (balance != null && prev != null) {
                    assertEquals("Balance chain broken at ${item.id} in $key", prev + delta, balance)
                }
                // Unknown balance breaks the chain: the next item has nothing to compare against.
                prev = balance
            }
        }
    }

    @Test
    fun `postTime matches the time in content or bigText to the minute in UTC+7`() {
        val timeRegex = Regex("""(\d{2})[-/](\d{2})[-/](\d{2,4}) (\d{2}):(\d{2})""")
        var checked = 0
        for (item in NotificationCorpusFixtures.all) {
            val m = timeRegex.find(item.bigText ?: item.content) ?: continue
            val g = m.groupValues.drop(1).map { it.toInt() }
            val year = if (g[2] < 100) g[2] + 2000 else g[2]
            val expected = LocalDateTime.of(year, g[1], g[0], g[3], g[4])
            val actual = LocalDateTime.ofEpochSecond(item.postTime / 1000, 0, ZoneOffset.ofHours(7))
            assertEquals("postTime minute mismatch for ${item.id}", expected, actual.withSecond(0))
            checked++
        }
        assertTrue("Expected to check many timed items, checked $checked", checked >= 20)
    }

    @Test
    fun `collapsed and expanded notifications exist for VCB, MB and MoMo`() {
        for (pkg in listOf("com.VCB", "com.mbmobile", "com.mservice.momotransfer")) {
            val expanded = NotificationCorpusFixtures.byPackage(pkg).filter { it.bigText != null }
            assertTrue("$pkg needs >= 2 items with bigText, was ${expanded.size}", expanded.size >= 2)
            for (item in expanded) {
                assertTrue("Collapsed content must end with an ellipsis: ${item.id}", item.content.endsWith("…"))
                assertTrue(
                    "bigText must extend content: ${item.id}",
                    item.bigText!!.startsWith(item.content.dropLast(1)),
                )
            }
        }
    }

    @Test
    fun `contains dedup pair for MoMo GrabFood and VCB debit`() {
        val momo = NotificationCorpusFixtures.all.firstOrNull { it.id == "momo-dedup-grabfood-04" }
        val vcb = NotificationCorpusFixtures.all.firstOrNull { it.id == "vcb-dedup-grabfood-16" }

        assertNotNull("MoMo dedup item must exist", momo)
        assertNotNull("VCB dedup item must exist", vcb)
        assertEquals(momo?.expected?.amount, vcb?.expected?.amount)
        assertEquals(momo?.expected?.accountMask, vcb?.expected?.accountMask)
        assertEquals(momo?.expected?.sign, vcb?.expected?.sign)
        val gapMs = kotlin.math.abs(momo!!.postTime - vcb!!.postTime)
        assertTrue("Dedup pair must be within 5 minutes, gap was $gapMs ms", gapMs <= 5 * 60_000L)
    }
}
