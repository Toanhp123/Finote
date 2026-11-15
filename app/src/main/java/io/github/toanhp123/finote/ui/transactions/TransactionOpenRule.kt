package io.github.toanhp123.finote.ui.transactions

import io.github.toanhp123.finote.domain.transaction.model.Transaction

/** Every account the row touches (source, and destination of a transfer) is in [liveAccountIds]. */
fun Transaction.isOpenable(liveAccountIds: Set<String>): Boolean =
    accountId in liveAccountIds && (toAccountId == null || toAccountId in liveAccountIds)
