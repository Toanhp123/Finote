package io.github.toanhp123.finote.domain.account.model

/**
 * The account's balance may differ from the bank's. The app never computes by how much: the user types the real
 * balance.
 */
data class BalanceDrift(
    val accountId: String,
)
