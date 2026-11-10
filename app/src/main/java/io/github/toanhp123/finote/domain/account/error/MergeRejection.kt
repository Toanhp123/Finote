package io.github.toanhp123.finote.domain.account.error

/** Why an account merge was refused. Nothing is written when one of these applies. */
enum class MergeRejection {
    SAME_ACCOUNT,
    ACCOUNT_GONE,
    DIFFERENT_BANK,
    HAS_TRANSFER,
}
