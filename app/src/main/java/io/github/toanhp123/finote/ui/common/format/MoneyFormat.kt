package io.github.toanhp123.finote.ui.common.format

import java.text.NumberFormat
import java.util.Currency
import java.util.Locale
import kotlin.math.abs

private const val PLUS = "+"
private const val MINUS = "\u2212"

enum class MoneyKind { Income, Expense, Transfer, Neutral }

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

/**
 * An amount that may be a net refund, split for [MoneyText]: a spend (> 0) is an expense, a refund (< 0) is its
 * absolute value as income, so it reads `+...` everywhere. Zero is neutral, so an empty total is not red.
 */
fun moneyOf(amount: Long): Pair<Long, MoneyKind> =
    when {
        amount > 0 -> amount to MoneyKind.Expense
        amount < 0 -> Math.negateExact(amount) to MoneyKind.Income
        else -> 0L to MoneyKind.Neutral
    }
