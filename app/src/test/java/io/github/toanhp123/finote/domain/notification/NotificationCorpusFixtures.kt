package io.github.toanhp123.finote.domain.notification

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Parser-scored fields: sign, amount, accountMask, postBalance, availableCredit, merchant, note, reference.
 * [merchant] and [note] are raw substrings of the text (bigText when present), case preserved; normalization is later.
 * [availableCredit] is a credit-card remaining limit; it is never a balance, so [postBalance] stays null for it.
 * [transactionType] is the end-to-end pipeline label (pairing, refund detection); do not use it to score the parser.
 */
@Serializable
data class ExpectedNotification(
    val shouldIgnore: Boolean,
    val ignoreReason: String? = null,
    val sign: String? = null,
    val amount: Long? = null,
    val accountMask: String? = null,
    val postBalance: Long? = null,
    val availableCredit: Long? = null,
    val merchant: String? = null,
    val note: String? = null,
    val reference: String? = null,
    val transactionType: String? = null,
)

@Serializable
data class NotificationCorpusItem(
    val id: String,
    val packageName: String,
    val postTime: Long,
    val channelId: String? = null,
    val title: String,
    val content: String,
    /** Expanded text (EXTRA_BIG_TEXT / EXTRA_TEXT_LINES). When set, [content] is the truncated collapsed text. */
    val bigText: String? = null,
    val expected: ExpectedNotification,
)

/**
 * Synthetic corpus (D4): hand-written, not real bank data; real samples come from Failed Inbox during dogfooding.
 */
object NotificationCorpusFixtures {
    private val json = Json { ignoreUnknownKeys = true }

    val all: List<NotificationCorpusItem> by lazy {
        val stream =
            NotificationCorpusFixtures::class.java.classLoader?.getResourceAsStream("fixtures/notification_corpus.json")
                ?: error("fixtures/notification_corpus.json not found on test classpath")
        val content = stream.bufferedReader().use { it.readText() }
        json.decodeFromString<List<NotificationCorpusItem>>(content)
    }

    fun byPackage(packageName: String): List<NotificationCorpusItem> = all.filter { it.packageName == packageName }

    fun financialTransactions(): List<NotificationCorpusItem> = all.filterNot { it.expected.shouldIgnore }

    fun ignoredOrSpam(): List<NotificationCorpusItem> = all.filter { it.expected.shouldIgnore }
}
