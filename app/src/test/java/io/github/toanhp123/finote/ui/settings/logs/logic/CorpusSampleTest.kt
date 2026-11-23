package io.github.toanhp123.finote.ui.settings.logs.logic

import io.github.toanhp123.finote.domain.notification.NotificationCorpusItem
import io.github.toanhp123.finote.domain.notification.testLog
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class CorpusSampleTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `json decodes as a corpus item with masked text`() {
        val log =
            testLog(id = "abcdef123456", packageName = "com.VCB", title = "VCB", content = "SD TK 0123 +50,000 VND")

        val item = json.decodeFromString<NotificationCorpusItem>(corpusSampleJson(log))

        assertEquals("real_abcdef12", item.id)
        assertEquals("com.VCB", item.packageName)
        assertEquals("SD TK 1111 +11,111 VND", item.content)
        assertFalse(item.expected.shouldIgnore)
    }

    @Test
    fun `json keeps line breaks and quotes`() {
        val log = testLog(content = "a \"b\"\nc")

        assertEquals("a \"b\"\nc", json.decodeFromString<NotificationCorpusItem>(corpusSampleJson(log)).content)
    }

    @Test
    fun `json holds only the sample keys and omits a null channel`() {
        val log = testLog(channelId = null, contentHash = "secret-hash", failureReason = "secret-reason")

        val keys = json.parseToJsonElement(corpusSampleJson(log)).jsonObject.keys

        assertEquals(listOf("id", "packageName", "postTime", "title", "content", "expected"), keys.toList())
    }
}
