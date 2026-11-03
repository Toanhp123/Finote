package io.github.toanhp123.finote.domain.account.model

/** One month of one account. [monthYear] is "YYYY-MM". Money is VND. */
data class MonthlyBalance(
    val accountId: String,
    val monthYear: String,
    val openingBalance: Long,
    val closingBalance: Long,
    val totalIncome: Long,
    val totalExpense: Long,
    val totalTransferIn: Long,
    val totalTransferOut: Long,
)
