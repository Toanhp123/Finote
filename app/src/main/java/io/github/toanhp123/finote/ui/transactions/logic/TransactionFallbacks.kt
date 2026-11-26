package io.github.toanhp123.finote.ui.transactions.logic

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import io.github.toanhp123.finote.R
import io.github.toanhp123.finote.domain.transaction.model.TransactionType

/** The title of a row with no category. */
@StringRes
fun TransactionType.fallbackTitle(): Int =
    when (this) {
        TransactionType.TRANSFER -> R.string.transactions_transfer
        TransactionType.ADJUSTMENT -> R.string.transactions_adjustment
        else -> R.string.transactions_uncategorized
    }

/** The icon of a row with no category. */
@DrawableRes
fun TransactionType.fallbackIcon(): Int =
    when (this) {
        TransactionType.TRANSFER -> R.drawable.ic_swap_horiz
        TransactionType.ADJUSTMENT -> R.drawable.ic_tune
        else -> R.drawable.ic_more_horiz
    }
