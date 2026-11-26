package io.github.toanhp123.finote.ui.overview.logic

import io.github.toanhp123.finote.domain.report.model.BucketTotal
import java.math.BigDecimal
import java.math.RoundingMode

private const val PERCENT = 100L

/** Sum of the buckets above zero: the base of every share, so no share passes 100%. */
fun positiveTotal(buckets: List<BucketTotal>): Long = buckets.sumOf { it.amount.coerceAtLeast(0) }

/** Whole percent of [amount] in [positiveTotal], rounded half up. Null for a zero or negative amount or base. */
fun share(
    amount: Long,
    positiveTotal: Long,
): Int? =
    if (amount <= 0 || positiveTotal <= 0) {
        null
    } else {
        BigDecimal(amount)
            .multiply(BigDecimal(PERCENT))
            .divide(BigDecimal(positiveTotal), 0, RoundingMode.HALF_UP)
            .toInt()
    }
