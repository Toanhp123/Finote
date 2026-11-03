package io.github.toanhp123.finote.domain.transaction.model

/** Only the fields balance math needs. [amount] and [feeAmount] are VND; [timestamp] is epoch ms. */
data class Transaction(
    val id: String,
    val amount: Long,
    val feeAmount: Long,
    val type: TransactionType,
    val adjustmentDirection: AdjustmentDirection?,
    val accountId: String,
    val toAccountId: String?,
    val timestamp: Long,
    val deletedAt: Long?,
) {
    init {
        require(amount > 0) { "amount must be positive" }
        require(feeAmount >= 0) { "feeAmount must not be negative" }
        if (type == TransactionType.TRANSFER) {
            require(toAccountId != null && toAccountId != accountId) { "transfer needs a different destination" }
        }
        require((type == TransactionType.ADJUSTMENT) == (adjustmentDirection != null)) {
            "only an adjustment has a direction, and it must have one"
        }
    }
}
