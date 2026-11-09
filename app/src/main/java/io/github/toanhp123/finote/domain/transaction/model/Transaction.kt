package io.github.toanhp123.finote.domain.transaction.model

/** One ledger entry. [amount] and [feeAmount] are VND; every other `Long` time field is epoch ms. */
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
    val createdAt: Long,
    val updatedAt: Long,
    val categoryId: String? = null,
    val merchantName: String? = null,
    val note: String = "",
    val source: TransactionSource = TransactionSource.MANUAL,
    val isExcludedFromAnalytics: Boolean = false,
    val reimbursementStatus: ReimbursementStatus = ReimbursementStatus.NONE,
    /** VND balance the bank printed after this transaction; null for a manual one. */
    val postBalance: Long? = null,
    val notificationLogId: String? = null,
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
