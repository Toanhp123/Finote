package io.github.toanhp123.finote.data.notification.remote

import io.github.toanhp123.finote.domain.notification.error.RuleFeedError
import io.github.toanhp123.finote.domain.notification.error.RuleFeedException
import io.github.toanhp123.finote.domain.notification.model.ParserRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec
import java.util.concurrent.CompletableFuture
import kotlin.concurrent.thread

class HttpParserRuleFeedTest {
    private val keys: KeyPair =
        KeyPairGenerator.getInstance("EC").run {
            initialize(ECGenParameterSpec("secp256r1"))
            generateKeyPair()
        }

    private val rule =
        ParserRule(
            id = "vcb_account_v1",
            packageName = "com.VCB",
            bankName = "Vietcombank",
            channelIdAllowlist = null,
            ignoreKeywords = emptyList(),
            priority = 0,
            isUserCustom = false,
            version = 1,
            regexPattern = "(?<sign>[+-])(?<amount>\\d+)",
            fallbackTransactionType = null,
            dateFormatPattern = null,
            isActive = true,
            updatedAt = 0L,
        )

    /** Serves one response on 127.0.0.1 and completes with the raw request head. */
    private fun serveOnce(
        status: String,
        body: ByteArray,
    ): Pair<ServerSocket, CompletableFuture<String>> {
        val server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        val request = CompletableFuture<String>()
        thread(isDaemon = true) {
            server.use { s ->
                s.accept().use { socket ->
                    val reader = socket.getInputStream().bufferedReader()
                    val head = generateSequence { reader.readLine() }.takeWhile { it.isNotEmpty() }.joinToString("\n")
                    request.complete(head)
                    socket.getOutputStream().run {
                        write(
                            "HTTP/1.1 $status\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n"
                                .toByteArray(),
                        )
                        write(body)
                        flush()
                    }
                }
            }
        }
        return server to request
    }

    private fun feedAt(server: ServerSocket) =
        HttpParserRuleFeed("http://127.0.0.1:${server.localPort}/feed.json", keys.public)

    @Test
    fun `readCapped stops at the cap when length is unknown`() {
        val e =
            assertThrows(RuleFeedException::class.java) {
                readCapped(ByteArrayInputStream(ByteArray(256 * 1024 + 1)), 256 * 1024)
            }

        assertEquals(RuleFeedError.INVALID, e.reason)
        assertEquals("too_large", e.message)
    }

    @Test
    fun `readCapped returns a body under the cap`() {
        val body = ByteArray(20_000) { it.toByte() }

        assertTrue(body.contentEquals(readCapped(ByteArrayInputStream(body), 256 * 1024)))
    }

    @Test
    fun `request has User-Agent Finote and no query`() =
        runTest {
            val (server, request) = serveOnce("200 OK", signRuleFeed(listOf(rule), keys.private))

            val rules = feedAt(server).fetch()

            val lines = request.get().lines()
            assertEquals("GET /feed.json HTTP/1.1", lines.first())
            assertTrue(lines.any { it.equals("User-Agent: Finote", ignoreCase = true) })
            assertEquals(listOf(rule.id), rules.map { it.id })
        }

    @Test
    fun `non 200 is NETWORK`() =
        runTest {
            val (server, _) = serveOnce("404 Not Found", ByteArray(0))

            val e = runCatching { feedAt(server).fetch() }.exceptionOrNull()

            assertEquals(RuleFeedError.NETWORK, (e as RuleFeedException).reason)
        }

    @Test
    fun `refused connection is NETWORK`() =
        runTest {
            val closed = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).also { it.close() }

            val e = runCatching { feedAt(closed).fetch() }.exceptionOrNull()

            assertEquals(RuleFeedError.NETWORK, (e as RuleFeedException).reason)
        }
}
