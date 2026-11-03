package io.github.toanhp123.finote.ui.common.format

import java.text.NumberFormat
import java.util.Currency
import java.util.Locale
import kotlin.math.abs

private const val PLUS = "+"
private const val MINUS = "\u2212"

/** Formats a VND amount for [locale]. The sign goes first: `+` for income, U+2212 for expense or any negative. */
fun formatMoney(
    amount: Long,
    kind: MoneyKind,
    locale: Locale,
): String {
    require(amount != Long.MIN_VALUE) { "abs(Long.MIN_VALUE) overflows" }
    val sign =
        when {
            amount < 0 -> MINUS
            amount > 0 && kind == MoneyKind.Income -> PLUS
            amount > 0 && kind == MoneyKind.Expense -> MINUS
            else -> ""
        }
    val format =
        NumberFormat.getCurrencyInstance(locale).apply {
            currency = Currency.getInstance("VND")
            minimumFractionDigits = 0
            maximumFractionDigits = 0
        }
    return sign + format.format(abs(amount))
}
