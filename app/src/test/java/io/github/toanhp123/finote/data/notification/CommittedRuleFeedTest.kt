package io.github.toanhp123.finote.data.notification

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class CommittedRuleFeedTest {
    private val feed get() = File(COMMITTED_FEED_PATH).readBytes()
    private val key get() = publicKeyFromBase64(RULE_FEED_PUBLIC_KEY)

    @Test
    fun `committed feed verifies with the app key`() {
        decodeRuleFeed(feed, key)
    }

    @Test
    fun `committed feed equals SystemParserRules`() {
        assertEquals(SystemParserRules.mapNotNull { it.toDomain() }, decodeRuleFeed(feed, key))
    }
}
