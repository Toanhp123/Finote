package io.github.toanhp123.finote.ui.report

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.toanhp123.finote.R
import io.github.toanhp123.finote.domain.report.model.ReportFilter
import io.github.toanhp123.finote.domain.report.model.ReportRow
import io.github.toanhp123.finote.ui.common.ObserveEffects
import io.github.toanhp123.finote.ui.common.component.display.FinoteEmptyState
import io.github.toanhp123.finote.ui.common.component.display.FinoteSkeleton
import io.github.toanhp123.finote.ui.common.component.finance.MoneyText
import io.github.toanhp123.finote.ui.common.component.finance.TransactionRow
import io.github.toanhp123.finote.ui.common.component.layout.FinoteTopBar
import io.github.toanhp123.finote.ui.common.format.displayName
import io.github.toanhp123.finote.ui.common.format.formatMonthYear
import io.github.toanhp123.finote.ui.common.format.iconRes
import io.github.toanhp123.finote.ui.common.format.moneyOf
import io.github.toanhp123.finote.ui.common.theme.FinoteTheme
import io.github.toanhp123.finote.ui.transactions.logic.fallbackIcon
import io.github.toanhp123.finote.ui.transactions.logic.fallbackTitle
import io.github.toanhp123.finote.ui.transactions.logic.isOpenable
import io.github.toanhp123.finote.ui.transactions.logic.rowSubtitle

private const val SKELETON_ROWS = 4

@Composable
fun ReportListRoute(
    viewModel: ReportListViewModel,
    onOpenEditor: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ObserveEffects(viewModel.effects) { effect ->
        when (effect) {
            is ReportListEffect.OpenEditor -> {
                onOpenEditor(effect.id)
            }
        }
    }
    ReportListScreen(state = state, onIntent = viewModel::onIntent, onBack = onClose, modifier = modifier)
}

@Composable
fun ReportListScreen(
    state: ReportListState,
    onIntent: (ReportListIntent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        FinoteTopBar(title = state.title(), onBack = onBack)
        Summary(state)
        when {
            state.isLoading -> {
                LoadingRows()
            }

            state.hasError -> {
                FinoteEmptyState(R.drawable.ic_receipt_long, stringResource(R.string.transactions_error))
            }

            state.rows.isEmpty() -> {
                FinoteEmptyState(R.drawable.ic_receipt_long, stringResource(R.string.report_list_empty))
            }

            else -> {
                RowList(state, onIntent)
            }
        }
    }
}

@Composable
private fun ReportListState.title(): String =
    when (val shown = filter) {
        is ReportFilter.ByCategory -> {
            // Until the categories load there is no name yet; the uncategorized fallback would read as a wrong title.
            if (isLoading) {
                ""
            } else {
                categoriesById[shown.categoryId]?.let { displayName(it.name, it.nameKey) }
                    ?: stringResource(R.string.transactions_uncategorized)
            }
        }

        ReportFilter.Uncategorized -> {
            stringResource(R.string.transactions_uncategorized)
        }

        ReportFilter.TransferFees -> {
            stringResource(R.string.overview_bucket_fees)
        }

        is ReportFilter.ByMerchant -> {
            shown.name
        }

        ReportFilter.AllSpending -> {
            stringResource(R.string.overview_spent)
        }
    }

/** The month under the title, and the total that equals the number tapped on Overview. */
@Composable
private fun Summary(state: ReportListState) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = FinoteTheme.spacing.lg, vertical = FinoteTheme.spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(FinoteTheme.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = formatMonthYear(state.month, LocalConfiguration.current.locales[0]),
            modifier = Modifier.weight(1f),
            color = FinoteTheme.scheme.onSurfaceVariant,
            style = FinoteTheme.typography.material.bodyMedium,
        )
        if (!state.isLoading && !state.hasError) {
            val (amount, kind) = moneyOf(state.total)
            MoneyText(amount = amount, kind = kind, style = FinoteTheme.typography.amountMedium)
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
private fun RowList(
    state: ReportListState,
    onIntent: (ReportListIntent) -> Unit,
) {
    val resources = LocalResources.current
    val accountNames =
        remember(state.accountsById, resources) {
            state.accountsById.mapValues { resources.displayName(it.value.name, it.value.nameKey) }
        }
    LazyColumn(
        contentPadding = PaddingValues(horizontal = FinoteTheme.spacing.lg, vertical = FinoteTheme.spacing.sm),
        verticalArrangement = Arrangement.spacedBy(FinoteTheme.spacing.sm),
    ) {
        items(state.rows, key = { it.transaction.id }) { row ->
            ReportRowItem(
                row = row,
                state = state,
                accountNames = accountNames,
                onClick =
                    if (row.transaction.isOpenable(state.openableAccountIds)) {
                        { onIntent(ReportListIntent.RowClicked(row.transaction.id)) }
                    } else {
                        null
                    },
            )
        }
    }
}

// The amount is the row's share (the fee in a fee list), so the fee line stays off: it would show twice.
@Composable
private fun ReportRowItem(
    row: ReportRow,
    state: ReportListState,
    accountNames: Map<String, String>,
    onClick: (() -> Unit)?,
) {
    val transaction = row.transaction
    val category = transaction.categoryId?.let(state.categoriesById::get)
    val (amount, kind) = moneyOf(row.amount)
    TransactionRow(
        title = category?.let { displayName(it.name, it.nameKey) } ?: stringResource(transaction.type.fallbackTitle()),
        subtitle =
            rowSubtitle(
                transaction,
                accountNames::get,
                stringResource(R.string.transactions_deleted_account),
                pendingLabel = stringResource(R.string.reimbursement_pending),
                settledLabel = stringResource(R.string.reimbursement_settled),
            ),
        amount = amount,
        kind = kind,
        iconRes = category?.let { iconRes(it.icon) } ?: transaction.type.fallbackIcon(),
        colorArgb = category?.color ?: FinoteTheme.scheme.onSurfaceVariant.toArgb(),
        onClick = onClick,
    )
}
