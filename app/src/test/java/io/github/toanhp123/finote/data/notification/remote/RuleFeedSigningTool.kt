package io.github.toanhp123.finote.data.notification.remote

import io.github.toanhp123.finote.data.notification.mapper.toDomain
import io.github.toanhp123.finote.data.notification.seed.SystemParserRules
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.security.KeyFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64

/** Opt-in tool, not a check: regenerates the committed feed with `-PsignRuleFeed=true`. Skipped otherwise. */
class RuleFeedSigningTool {
    @Test
    fun writeFeed() {
        assumeTrue(System.getProperty("finote.signRuleFeed") == "true")
        val pem = File(System.getProperty("user.home"), ".finote/finote-rules.pem").readText()
        val der = Base64.getMimeDecoder().decode(pem.lines().filterNot { it.startsWith("-----") }.joinToString(""))
        val key = KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(der))
        val feed = signRuleFeed(SystemParserRules.mapNotNull { it.toDomain() }, key)
        File(COMMITTED_FEED_PATH).apply { parentFile?.mkdirs() }.writeBytes(feed)
    }
}

/** Unit tests run in `app/`, so the feed sits one level up. */
internal const val COMMITTED_FEED_PATH = "../rules/v1/feed.json"
