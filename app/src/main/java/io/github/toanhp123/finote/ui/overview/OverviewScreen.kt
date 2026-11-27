package io.github.toanhp123.finote.ui.overview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.toanhp123.finote.R
import io.github.toanhp123.finote.domain.report.model.MonthReport
import io.github.toanhp123.finote.domain.report.model.ReportFilter
import io.github.toanhp123.finote.ui.common.ObserveEffects
import io.github.toanhp123.finote.ui.common.component.control.MonthSwitcher
import io.github.toanhp123.finote.ui.common.component.display.FinoteEmptyState
import io.github.toanhp123.finote.ui.common.component.display.FinoteSkeleton
import io.github.toanhp123.finote.ui.common.component.finance.BalanceHeroCard
import io.github.toanhp123.finote.ui.common.component.layout.FinoteTopBar
import io.github.toanhp123.finote.ui.common.theme.FinoteTheme
import io.github.toanhp123.finote.ui.overview.component.BucketList
import io.github.toanhp123.finote.ui.overview.component.MerchantList
import io.github.toanhp123.finote.ui.overview.component.PaceCard
import java.time.YearMonth

private const val SKELETON_ROWS = 3

@Composable
fun OverviewRoute(
    viewModel: OverviewViewModel,
    onOpenReport: (YearMonth, ReportFilter) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ObserveEffects(viewModel.effects) { effect ->
        when (effect) {
            is OverviewEffect.OpenReport -> onOpenReport(effect.month, effect.filter)
        }
    }
    // The month can change while the app is away; the ViewModel reads its clock again on each resume.
    LifecycleResumeEffect(viewModel) {
        viewModel.onIntent(OverviewIntent.Resumed)
        onPauseOrDispose {}
    }
    OverviewScreen(state = state, onIntent = viewModel::onIntent, modifier = modifier, contentPadding = contentPadding)
}

/** [contentPadding] keeps the last block clear of the floating nav bar. */
@Composable
fun OverviewScreen(
    state: OverviewState,
    onIntent: (OverviewIntent) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val report = state.report
    Column(modifier = modifier.fillMaxSize()) {
        FinoteTopBar(title = stringResource(R.string.tab_overview))
        if (!state.isLoading && !state.hasTransactions) {
            FinoteEmptyState(R.drawable.ic_receipt_long, stringResource(R.string.transactions_empty))
        } else {
            // The header stays while loading and on an error: the month is known, and another month retries.
            MonthSwitcher(
                month = state.month,
                isNextEnabled = state.month < state.currentMonth,
                onPrevious = { onIntent(OverviewIntent.PreviousMonth) },
                onNext = { onIntent(OverviewIntent.NextMonth) },
            )
            when {
                state.isLoading -> {
                    LoadingBlocks()
                }

                report == null -> {
                    FinoteEmptyState(R.drawable.ic_receipt_long, stringResource(R.string.transactions_error))
                }

                else -> {
                    ReportBlocks(report, state, onIntent, contentPadding)
                }
            }
        }
    }
}

@Composable
private fun ReportBlocks(
    report: MonthReport,
    state: OverviewState,
    onIntent: (OverviewIntent) -> Unit,
    contentPadding: PaddingValues,
) {
    val sidePadding = Modifier.padding(horizontal = FinoteTheme.spacing.lg)
    LazyColumn(
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(FinoteTheme.spacing.lg),
    ) {
        item(key = "hero") {
            // Refunds above spending read as their own words, never as a plain expense.
            val isRefund = report.expense < 0
            BalanceHeroCard(
                label = stringResource(if (isRefund) R.string.overview_spent_refund else R.string.overview_spent),
                amount = if (isRefund) Math.negateExact(report.expense) else report.expense,
                income = report.income,
                net = report.net,
                modifier = sidePadding,
            )
        }
        item(key = "pace") {
            PaceCard(report.pace, report.month.lengthOfMonth(), report.hasFutureRows, sidePadding)
        }
        item(key = "buckets") {
            BucketList(
                buckets = report.buckets,
                categoriesById = state.categoriesById,
                hasShares = report.expense > 0,
                isExpanded = state.isBucketListExpanded,
                onToggle = { onIntent(OverviewIntent.ToggleBuckets) },
                onBucketClick = { onIntent(OverviewIntent.BucketClicked(it)) },
                modifier = sidePadding,
            )
        }
        if (report.merchants.isNotEmpty()) {
            item(key = "merchants") {
                MerchantList(report.merchants, { onIntent(OverviewIntent.MerchantClicked(it)) }, sidePadding)
            }
        }
    }
}

@Composable
private fun LoadingBlocks() {
    Column(
        modifier = Modifier.padding(horizontal = FinoteTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(FinoteTheme.spacing.md),
    ) {
        FinoteSkeleton(Modifier.fillMaxWidth().height(FinoteTheme.sizes.chartHeight))
        repeat(SKELETON_ROWS) { FinoteSkeleton(Modifier.fillMaxWidth().height(FinoteTheme.sizes.touchTarget)) }
    }
}
