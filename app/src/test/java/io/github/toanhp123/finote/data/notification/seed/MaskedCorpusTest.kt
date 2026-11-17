package io.github.toanhp123.finote.data.notification.seed

import io.github.toanhp123.finote.data.notification.mapper.toDomain
import io.github.toanhp123.finote.domain.notification.NotificationCorpusFixtures
import io.github.toanhp123.finote.domain.notification.NotificationCorpusItem
import io.github.toanhp123.finote.domain.notification.model.Classification
import io.github.toanhp123.finote.domain.notification.model.RawNotification
import io.github.toanhp123.finote.domain.notification.service.classifyNotification
import io.github.toanhp123.finote.domain.notification.service.maskDigits
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Masking every digit to `1` must not change which rule matches or what type it parses to, so a masked
 * sample is still a valid regression case. Failure messages list item ids only, never notification text.
 */
class MaskedCorpusTest {
    private val allRules = SystemParserRules.mapNotNull { it.toDomain() }

    private fun classify(
        item: NotificationCorpusItem,
        mask: Boolean,
    ): Classification {
        val text = item.bigText ?: item.content
        val raw =
            RawNotification(
                item.packageName,
                item.channelId,
                if (mask) maskDigits(item.title) else item.title,
                if (mask) maskDigits(text) else text,
                item.postTime,
            )
        return classifyNotification(raw, allRules.filter { it.packageName == item.packageName })
    }

    private fun Classification.signature() = parsed?.ruleId to parsed?.fields?.type

    @Test
    fun `masking digits keeps the matched rule and type`() {
        val financial = NotificationCorpusFixtures.financialTransactions()
        assertTrue("no item matched a rule", financial.any { classify(it, mask = false).parsed != null })

        val changed =
            financial
                .filter { classify(it, mask = false).signature() != classify(it, mask = true).signature() }
                .map { it.id }

        assertEquals("mask changed match: $changed", emptyList<String>(), changed)
    }
}
