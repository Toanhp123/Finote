package io.github.toanhp123.finote.data.notification

import io.github.toanhp123.finote.domain.notification.model.ParserRule
import io.github.toanhp123.finote.domain.notification.model.RuleFeedError
import io.github.toanhp123.finote.domain.notification.model.RuleFeedException
import io.github.toanhp123.finote.domain.notification.repository.ParserRuleFeed
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.PublicKey
import javax.inject.Inject

/** Base64 X.509 public key (P-256) that verifies the signed rule feed. Public data. */
const val RULE_FEED_PUBLIC_KEY =
    "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEPpTs22HAb0mKF+s0pYiMhfrXmk2oGuQjsJDH68fNcA1bdCYpYTn1rMUHpiNQAJIpMSes2r5dTIk5Iz7p48TiaA=="

private const val FEED_URL = "https://raw.githubusercontent.com/Toanhp123/Finote/main/rules/v1/feed.json"
private const val TIMEOUT_MILLIS = 10_000
private const val MAX_BODY_BYTES = 256 * 1024
private const val BUFFER_BYTES = 8 * 1024

/**
 * The only network code in the app: one plain GET of the signed rule feed, no query, no body.
 * The `User-Agent` is fixed so the default one does not leak the device model.
 */
class HttpParserRuleFeed internal constructor(
    private val url: String,
    private val publicKey: PublicKey,
) : ParserRuleFeed {
    @Inject
    constructor() : this(FEED_URL, publicKeyFromBase64(RULE_FEED_PUBLIC_KEY))

    // shortcut: blocking IO on the caller's dispatcher and not cancellable mid-read, the worker is the only caller.
    override suspend fun fetch(): List<ParserRule> = decodeRuleFeed(download(), publicKey)

    private fun download(): ByteArray {
        var connection: HttpURLConnection? = null
        try {
            connection = URL(url).openConnection() as HttpURLConnection
            connection.useCaches = false
            connection.connectTimeout = TIMEOUT_MILLIS
            connection.readTimeout = TIMEOUT_MILLIS
            connection.setRequestProperty("User-Agent", "Finote")
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw RuleFeedException(RuleFeedError.NETWORK, "http_${connection.responseCode}")
            }
            return connection.inputStream.use { readCapped(it, MAX_BODY_BYTES) }
        } catch (_: IOException) {
            throw RuleFeedException(RuleFeedError.NETWORK, "io")
        } finally {
            connection?.disconnect()
        }
    }
}

/** Reads the whole stream, and throws INVALID `too_large` at the first byte past [maxBytes]. */
internal fun readCapped(
    input: InputStream,
    maxBytes: Int,
): ByteArray {
    val out = ByteArrayOutputStream()
    val buffer = ByteArray(BUFFER_BYTES)
    var read = input.read(buffer)
    while (read >= 0) {
        if (out.size() + read > maxBytes) throw RuleFeedException(RuleFeedError.INVALID, "too_large")
        out.write(buffer, 0, read)
        read = input.read(buffer)
    }
    return out.toByteArray()
}
