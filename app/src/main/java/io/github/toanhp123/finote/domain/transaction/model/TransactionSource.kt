package io.github.toanhp123.finote.domain.transaction.model

enum class TransactionSource {
    MANUAL,
    NOTIFICATION_PARSER,
    RECURRING,
    ADJUSTMENT,
    IMPORT,
}
