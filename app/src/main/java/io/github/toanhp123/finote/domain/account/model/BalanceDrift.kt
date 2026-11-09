package io.github.toanhp123.finote.domain.account.model

/** The bank balance minus the app's balance, both at the newest bank-reported transaction. Never zero. */
data class BalanceDrift(
    val accountId: String,
    val difference: Long,
)
