package io.github.toanhp123.finote.ui.transactions

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.toanhp123.finote.R
import io.github.toanhp123.finote.domain.category.model.Category
import io.github.toanhp123.finote.domain.transaction.TransactionDay
import io.github.toanhp123.finote.domain.transaction.model.Transaction
import io.github.toanhp123.finote.domain.transaction.model.TransactionType
import io.github.toanhp123.finote.ui.common.component.FinoteChip
import io.github.toanhp123.finote.ui.common.component.FinoteEmptyState
import io.github.toanhp123.finote.ui.common.component.FinoteSkeleton
import io.github.toanhp123.finote.ui.common.component.FinoteTopBar
import io.github.toanhp123.finote.ui.common.component.MoneyText
import io.github.toanhp123.finote.ui.common.component.TransactionRow
import io.github.toanhp123.finote.ui.common.format.MoneyKind
import io.github.toanhp123.finote.ui.common.format.displayName
import io.github.toanhp123.finote.ui.common.format.iconRes
import io.github.toanhp123.finote.ui.common.theme.FinoteTheme
import java.time.LocalDate

private const val SKELETON_ROWS = 6

@Composable
fun TransactionListRoute(
    viewModel: TransactionListViewModel,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    TransactionListScreen(state, viewModel::onIntent, modifier, contentPadding)
}

/** [contentPadding] keeps the last row clear of the floating nav bar. */
@Composable
fun TransactionListScreen(
    state: TransactionListState,
    onIntent: (TransactionListIntent) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    Column(modifier = modifier.fillMaxSize()) {
        FinoteTopBar(title = stringResource(R.string.tab_transactions))
        if (state.isLoading) {
            LoadingRows()
        } else {
            // The chips stay on an error: picking another account retries.
            AccountChips(state, onIntent)
            when {
                state.hasError -> {
                    FinoteEmptyState(R.drawable.ic_receipt_long, stringResource(R.string.transactions_error))
                }

                state.days.isEmpty() -> {
                    val title =
                        if (state.selectedAccountId == null) {
                            R.string.transactions_empty
                        } else {
                            R.string.transactions_empty_account
                        }
                    FinoteEmptyState(R.drawable.ic_receipt_long, stringResource(title))
                }

                else -> {
                    DayList(state, contentPadding)
                }
            }
        }
    }
}

@Composable
private fun LoadingRows() {
    Column(
        modifier = Modifier.padding(horizontal = FinoteTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(FinoteTheme.spacing.sm),
    ) {
        repeat(SKELETON_ROWS) { FinoteSkeleton(Modifier.fillMaxWidth().height(FinoteTheme.sizes.touchTarget)) }
    }
}

@Composable
private fun AccountChips(
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
                selected = state.selectedAccountId == null,
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
    }
}

@Composable
private fun DayList(
    state: TransactionListState,
    contentPadding: PaddingValues,
) {
    val resources = LocalResources.current
    val accountNames =
        remember(state.accountsById, resources) {
            state.accountsById.mapValues { resources.displayName(it.value.name, it.value.nameKey) }
        }
    LazyColumn(
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(FinoteTheme.spacing.sm),
    ) {
        state.days.forEach { day ->
            item(key = "day-${day.date}") { DayHeader(day, state.today) }
            items(day.transactions, key = { it.id }) { transaction ->
                TransactionItem(
                    transaction = transaction,
                    category = transaction.categoryId?.let(state.categoriesById::get),
                    accountNames = accountNames,
                )
            }
        }
    }
}

@Composable
private fun DayHeader(
    day: TransactionDay,
    today: LocalDate,
) {
    val label =
        when (val label = dayLabel(day.date, today, LocalConfiguration.current.locales[0])) {
            DayLabel.Today -> stringResource(R.string.transactions_today)
            DayLabel.Yesterday -> stringResource(R.string.transactions_yesterday)
            is DayLabel.Date -> label.text
        }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = FinoteTheme.spacing.lg, vertical = FinoteTheme.spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(FinoteTheme.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f).semantics { heading() },
            color = FinoteTheme.scheme.onSurfaceVariant,
            style = FinoteTheme.typography.material.titleSmall,
        )
        DayNet(day.net)
    }
}

// MoneyText writes the sign itself, so a negative net goes in as its absolute value with the Expense kind.
@Composable
private fun DayNet(net: Long) {
    when {
        net > 0 -> MoneyText(net, MoneyKind.Income)
        net < 0 -> MoneyText(Math.negateExact(net), MoneyKind.Expense)
        else -> MoneyText(0, MoneyKind.Neutral)
    }
}

@Composable
private fun TransactionItem(
    transaction: Transaction,
    category: Category?,
    accountNames: Map<String, String>,
) {
    val deletedLabel = stringResource(R.string.transactions_deleted_account)
    val title = category?.let { displayName(it.name, it.nameKey) } ?: stringResource(transaction.type.fallbackTitle())
    TransactionRow(
        title = title,
        subtitle = rowSubtitle(transaction, accountNames::get, deletedLabel),
        amount = transaction.amount,
        kind = moneyKindOf(transaction),
        iconRes = category?.let { iconRes(it.icon) } ?: transaction.type.fallbackIcon(),
        colorArgb = category?.color ?: FinoteTheme.scheme.onSurfaceVariant.toArgb(),
        modifier = Modifier.padding(horizontal = FinoteTheme.spacing.lg),
        onClick = null,
    )
}

@StringRes
private fun TransactionType.fallbackTitle(): Int =
    when (this) {
        TransactionType.TRANSFER -> R.string.transactions_transfer
        TransactionType.ADJUSTMENT -> R.string.transactions_adjustment
        else -> R.string.transactions_uncategorized
    }

@DrawableRes
private fun TransactionType.fallbackIcon(): Int =
    when (this) {
        TransactionType.TRANSFER -> R.drawable.ic_swap_horiz
        TransactionType.ADJUSTMENT -> R.drawable.ic_tune
        else -> R.drawable.ic_more_horiz
    }
