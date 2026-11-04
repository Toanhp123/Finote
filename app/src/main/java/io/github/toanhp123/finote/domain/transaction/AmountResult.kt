package io.github.toanhp123.finote.domain.transaction

sealed interface AmountResult {
    data object Empty : AmountResult

    data class Ok(
        val amount: Long,
    ) : AmountResult

    data class NotPositive(
        val value: Long,
    ) : AmountResult

    data class Invalid(
        val reason: InvalidReason,
    ) : AmountResult
}
