package io.github.toanhp123.finote.domain.transaction

import io.github.toanhp123.finote.domain.transaction.model.Transaction
import java.time.LocalDate

/** One local day of the list. [net] is VND: positive means more came in than went out. */
data class TransactionDay(
    val date: LocalDate,
    val net: Long,
    val transactions: List<Transaction>,
)
