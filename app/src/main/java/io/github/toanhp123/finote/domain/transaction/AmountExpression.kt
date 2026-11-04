package io.github.toanhp123.finote.domain.transaction

/** Cash-entry keypad calculator. Operators are stored as '+', '−', '×', '÷'. All money is whole VND. */
object AmountExpression {
    const val MAX_DIGITS = 13
    private const val GROUP_ZEROS = 3
    private val TOKEN = Regex("""\d+|[+−×÷]""")

    private class DivideByZero : RuntimeException()

    /** Returns the new expression text. */
    fun press(
        expression: String,
        key: CalcKey,
    ): String =
        when (key) {
            CalcKey.BACKSPACE -> expression.dropLast(1)
            CalcKey.PLUS -> withOperator(expression, '+')
            CalcKey.MINUS -> withOperator(expression, '−')
            CalcKey.TIMES -> withOperator(expression, '×')
            CalcKey.DIVIDE -> withOperator(expression, '÷')
            CalcKey.TRIPLE_ZERO -> withTripleZero(expression)
            else -> key.digit?.let { withDigit(expression, it) } ?: expression
        }

    /**
     * [expression] must be text built by [press]; other text is not validated.
     * `×÷` bind tighter than `+−`; each division rounds half up to whole VND.
     */
    fun evaluate(expression: String): AmountResult {
        val all = TOKEN.findAll(expression).map { it.value }.toList()
        val tokens = if (all.size % 2 == 0) all.dropLast(1) else all // drop trailing operator
        if (tokens.isEmpty()) return AmountResult.Empty
        return try {
            toResult(sum(tokens))
        } catch (_: DivideByZero) {
            AmountResult.Invalid(InvalidReason.DIVIDE_BY_ZERO)
        } catch (_: ArithmeticException) {
            AmountResult.Invalid(InvalidReason.OVERFLOW)
        }
    }

    private fun toResult(value: Long): AmountResult =
        if (value > 0) AmountResult.Ok(value) else AmountResult.NotPositive(value)

    private fun sum(tokens: List<String>): Long {
        var total = 0L
        var term = tokens[0].toLong()
        var sign = 1L
        for (i in 1 until tokens.size step 2) {
            val operand = tokens[i + 1].toLong()
            when (tokens[i]) {
                "×" -> {
                    term = Math.multiplyExact(term, operand)
                }

                "÷" -> {
                    term = divideHalfUp(term, operand)
                }

                else -> {
                    total = Math.addExact(total, sign * term)
                    term = operand
                    sign = if (tokens[i] == "+") 1L else -1L
                }
            }
        }
        return Math.addExact(total, sign * term)
    }

    // a, b >= 0 here: terms only hold non-negative values.
    private fun divideHalfUp(
        a: Long,
        b: Long,
    ): Long {
        if (b == 0L) throw DivideByZero()
        val quotient = a / b
        val remainder = a % b
        // Half up without computing 2a, which could overflow for a valid term.
        return if (remainder >= b - remainder) quotient + 1 else quotient
    }

    private fun withOperator(
        expression: String,
        operator: Char,
    ): String =
        when {
            expression.isEmpty() -> expression
            expression.last().isDigit() -> expression + operator
            else -> expression.dropLast(1) + operator
        }

    private fun withDigit(
        expression: String,
        digit: Char,
    ): String {
        val tail = expression.takeLastWhile { it.isDigit() }
        return when {
            tail == "0" -> expression.dropLast(1) + digit
            tail.length >= MAX_DIGITS -> expression
            else -> expression + digit
        }
    }

    private fun withTripleZero(expression: String): String {
        val tail = expression.takeLastWhile { it.isDigit() }
        return when (tail) {
            "" -> expression + "0"
            "0" -> expression
            else -> expression + "0".repeat(minOf(GROUP_ZEROS, MAX_DIGITS - tail.length))
        }
    }
}
