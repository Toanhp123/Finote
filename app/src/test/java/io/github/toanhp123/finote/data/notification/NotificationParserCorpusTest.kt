package io.github.toanhp123.finote.data.notification

import io.github.toanhp123.finote.domain.notification.ExpectedNotification
import io.github.toanhp123.finote.domain.notification.NotificationCorpusFixtures
import io.github.toanhp123.finote.domain.notification.NotificationCorpusItem
import io.github.toanhp123.finote.domain.notification.model.CaptureResult
import io.github.toanhp123.finote.domain.notification.model.NotificationLog
import io.github.toanhp123.finote.domain.notification.model.ParseResult
import io.github.toanhp123.finote.domain.notification.model.ParsedFields
import io.github.toanhp123.finote.domain.notification.model.RawNotification
import io.github.toanhp123.finote.domain.notification.parseNotification
import io.github.toanhp123.finote.domain.notification.usecase.CaptureNotificationUseCase
import io.github.toanhp123.finote.domain.notification.usecase.FakeNotificationCaptureRepository
import io.github.toanhp123.finote.domain.transaction.model.TransactionType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * The gate for the seed regex rules. Each financial item runs through capture on its stored text. An item with
 * `bigText` runs twice: on the truncated `content` (relaxed check) and on the expanded text (strict check).
 * Only labelled fields are compared. Failure messages list item ids, variants and field names, never text.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NotificationParserCorpusTest {
    private val clock = Clock.fixed(Instant.parse("2026-11-07T00:00:00Z"), ZoneOffset.UTC)
    private val techcombank = "vn.com.techcombank.bb.app"
    private val momoPackage = "com.mservice.momotransfer"

    private class Run(
        val variant: String,
        val text: String,
        val collapsed: Boolean,
    )

    private fun runsOf(item: NotificationCorpusItem): List<Run> =
        item.bigText
            ?.let { listOf(Run("collapsed", item.content, true), Run("expanded", it, false)) }
            ?: listOf(Run("full", item.content, false))

    // A fresh repository per run: the corpus reuses postTime values, and a shared one would report Duplicate.
    private suspend fun capture(
        item: NotificationCorpusItem,
        text: String,
    ): Pair<CaptureResult, NotificationLog> {
        val repo = FakeNotificationCaptureRepository(SystemParserRules.mapNotNull { it.toDomain() })
        val raw = RawNotification(item.packageName, item.channelId, item.title, text, item.postTime)
        val result = CaptureNotificationUseCase(repo, clock, UnconfinedTestDispatcher())(raw)
        return result to repo.logs.single()
    }

    private fun differs(
        label: Any?,
        value: Any?,
        nullAllowed: Boolean,
    ) = label != null && value != label && !(nullAllowed && value == null)

    private fun mismatches(
        expected: ExpectedNotification,
        parsed: ParsedFields?,
        collapsed: Boolean,
    ): List<String> {
        if (parsed == null) return listOf("unparsed")
        val type =
            when (expected.sign) {
                "+" -> TransactionType.INCOME
                "-" -> TransactionType.EXPENSE
                else -> null
            }
        return buildList {
            if (differs(type, parsed.type, false)) add("sign")
            if (differs(expected.amount, parsed.amount, false)) add("amount")
            if (differs(expected.accountMask, parsed.accountMask, collapsed)) add("accountMask")
            if (differs(expected.postBalance, parsed.postBalance, collapsed)) add("postBalance")
            // A card limit is never a balance.
            if (expected.availableCredit != null && parsed.postBalance != null) add("postBalance")
            if (differs(expected.merchant, parsed.merchant, collapsed)) add("merchant")
            if (differs(expected.note, parsed.note, collapsed)) add("note")
        }
    }

    private suspend fun failures(items: List<NotificationCorpusItem>): List<String> =
        items.flatMap { item ->
            runsOf(item).mapNotNull { run ->
                val fields = mismatches(item.expected, capture(item, run.text).second.parsed, run.collapsed)
                "${item.id}/${run.variant}: $fields".takeIf { fields.isNotEmpty() }
            }
        }

    @Test
    fun `vcb mb and momo parse every sample correctly`() =
        runTest {
            val items =
                NotificationCorpusFixtures.financialTransactions().filter { it.packageName != techcombank }

            assertEquals(emptyList<String>(), failures(items))
        }

    @Test
    fun `techcombank parses nothing wrong`() =
        runTest {
            val items = NotificationCorpusFixtures.byPackage(techcombank).filterNot { it.expected.shouldIgnore }
            val wrong = failures(items).filterNot { it.endsWith("[unparsed]") }

            assertEquals(emptyList<String>(), wrong)
        }

    // Capture only runs the rules of the posting package; so does this.
    private fun seededParse(
        packageName: String,
        title: String,
        content: String,
    ) = parseNotification(
        title,
        content,
        SystemParserRules.filter { it.packageName == packageName }.mapNotNull { it.toDomain() },
    )

    private fun merchantOf(
        packageName: String,
        content: String,
    ) = (seededParse(packageName, "", content) as? ParseResult.Parsed)?.fields?.merchant

    // Messages carry no notification text: assertTrue with a fixed label only.
    @Test
    fun `refunded or failed momo payment is never an expense`() {
        val texts =
            listOf(
                "Hoàn tiền thành công" to
                    "Bạn đã thanh toán 35.000đ cho Highlands Coffee đã được hoàn lại vào Ví MoMo (090***1234).",
                "Thanh toán không thành công" to "Bạn đã thanh toán 35.000đ cho Highlands Coffee không thành công.",
                "Thanh toán không thành công" to
                    "Bạn đã thanh toán 35.000đ cho Highlands Coffee từ Ví MoMo (090***1234) không thành công.",
            ).map { (title, content) -> seededParse(momoPackage, title, content) }
        val expenses = texts.filter { (it as? ParseResult.Parsed)?.fields?.type == TransactionType.EXPENSE }

        assertTrue("an expense was parsed", expenses.isEmpty())
    }

    @Test
    fun `momo received with a changed tail is not parsed`() {
        val text = "Bạn đã nhận được 50.000đ từ NGUYEN VAN A (090***1234) đã bị hủy."
        val result = seededParse(momoPackage, "MoMo", text)

        assertEquals(ParseResult.NoMatch, result)
    }

    @Test
    fun `stop words never cut a merchant short`() {
        val tcb = "TK 19031234567890 GD: -350,000 VND lúc 04/11/2026 09:00:00. Số dư: 3,000,000 VND. ND: "
        val mb = "TK 034123456789 GD: -680,000VND 05/11/26 20:10. SD: 4,785,200VND. ND: "
        val momo = "Bạn đã thanh toán 50.000đ cho Quỹ từ thiện ABC từ Ví MoMo (090***1234). Số dư ví: 420.000đ."
        val collapsed = "Bạn đã thanh toán 50.000đ cho Quỹ từ thiện ABC từ Ví Mo…"
        val cases =
            listOf(
                merchantOf(techcombank, tcb + "Thanh toan QR tai Quan Thang Loi") to "Quan Thang Loi",
                merchantOf("com.mbmobile", mb + "THANH TOAN HOA DON TIEN DIEN EVN HN THANG 10") to "EVN HN",
                merchantOf(momoPackage, momo) to "Quỹ từ thiện ABC",
                merchantOf(momoPackage, collapsed) to "Quỹ từ thiện ABC",
            )

        val wrong = cases.indices.filter { cases[it].first != null && cases[it].first != cases[it].second }

        assertEquals("a merchant was cut wrong at indexes $wrong", emptyList<Int>(), wrong)
        assertTrue("merchant 0 missing", cases[0].first == cases[0].second)
        assertTrue("merchant 2 missing", cases[2].first == cases[2].second)
    }

    @Test
    fun `ignored samples are never parsed`() =
        runTest {
            val parsed =
                NotificationCorpusFixtures.ignoredOrSpam().filter { item ->
                    val (result, log) = capture(item, item.bigText ?: item.content)
                    result !is CaptureResult.Ignored || log.parsed != null
                }

            assertEquals(emptyList<String>(), parsed.map { it.id })
        }
}
