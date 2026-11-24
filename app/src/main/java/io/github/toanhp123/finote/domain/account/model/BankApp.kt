package io.github.toanhp123.finote.domain.account.model

/** A bank or wallet app that has a parser rule. */
data class BankApp(
    val packageName: String,
    val bankName: String,
)
