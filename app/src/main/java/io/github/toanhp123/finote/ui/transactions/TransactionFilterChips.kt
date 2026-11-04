package io.github.toanhp123.finote.ui.transactions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.toanhp123.finote.R
import io.github.toanhp123.finote.ui.common.component.FinoteChip
import io.github.toanhp123.finote.ui.common.format.displayName
import io.github.toanhp123.finote.ui.common.theme.FinoteTheme

/** One chip at a time: All, an account, or the reimbursements awaiting repayment, which always comes last. */
@Composable
internal fun TransactionFilterChips(
    state: TransactionListState,
    onIntent: (TransactionListIntent) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = FinoteTheme.spacing.lg),
        horizontalArrangement = Arrangement.spacedBy(FinoteTheme.spacing.sm),
    ) {
        item(key = "all") {
            FinoteChip(
                text = stringResource(R.string.transactions_all),
                selected = state.selectedAccountId == null && !state.isPendingOnly,
                onClick = { onIntent(TransactionListIntent.SelectAccount(null)) },
            )
        }
        items(state.accounts, key = { it.id }) { account ->
            FinoteChip(
                text = displayName(account.name, account.nameKey),
                selected = state.selectedAccountId == account.id,
                onClick = { onIntent(TransactionListIntent.SelectAccount(account.id)) },
            )
        }
        item(key = "pending") {
            FinoteChip(
                text = stringResource(R.string.reimbursement_pending),
                selected = state.isPendingOnly,
                onClick = { onIntent(TransactionListIntent.SelectPending) },
            )
        }
    }
}
