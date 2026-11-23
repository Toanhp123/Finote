package io.github.toanhp123.finote.domain.account.model

import java.security.MessageDigest

/** Identity of one drift: the bank time of the newest balance row and the app's ledger balance through it. */
data class DriftKey(
    val time: Long,
    val ledgerBalance: Long,
) {
    // Balances must never reach a log or crash report.
    override fun toString() = "DriftKey"
}

private const val HEX_RADIX = 16

/** SHA-256 hex of the key and [accountId]. What is stored on disk instead of a balance. */
fun DriftKey.digest(accountId: String): String =
    MessageDigest
        .getInstance("SHA-256")
        .digest("$accountId:$time:$ledgerBalance".toByteArray())
        .joinToString("") { it.toUByte().toString(HEX_RADIX).padStart(2, '0') }
