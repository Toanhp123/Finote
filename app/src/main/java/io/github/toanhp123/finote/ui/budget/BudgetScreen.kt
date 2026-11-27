package io.github.toanhp123.finote.ui.budget

import android.Manifest
import android.os.Build
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.app.ActivityCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.toanhp123.finote.R
import io.github.toanhp123.finote.ui.budget.component.BudgetRow
import io.github.toanhp123.finote.ui.budget.component.BudgetTotalCard
import io.github.toanhp123.finote.ui.budget.component.NotificationOffBanner
import io.github.toanhp123.finote.ui.common.ObserveEffects
import io.github.toanhp123.finote.ui.common.component.control.FinoteButton
import io.github.toanhp123.finote.ui.common.component.control.FinoteIconButton
import io.github.toanhp123.finote.ui.common.component.control.MonthSwitcher
import io.github.toanhp123.finote.ui.common.component.display.FinoteEmptyState
import io.github.toanhp123.finote.ui.common.component.display.FinoteSkeleton
import io.github.toanhp123.finote.ui.common.component.finance.CategoryIcon
import io.github.toanhp123.finote.ui.common.component.layout.FinoteTopBar
import io.github.toanhp123.finote.ui.common.format.displayName
import io.github.toanhp123.finote.ui.common.format.iconRes
import io.github.toanhp123.finote.ui.common.notification.areBudgetAlertsEnabled
import io.github.toanhp123.finote.ui.common.notification.openAppNotificationSettings
import io.github.toanhp123.finote.ui.common.theme.FinoteTheme
import io.github.toanhp123.finote.ui.navigation.EditBudget
import io.github.toanhp123.finote.ui.navigation.ReportList

private const val SKELETON_ROWS = 3

@Composable
fun BudgetRoute(
    viewModel: BudgetViewModel,
    onOpenReport: (ReportList) -> Unit,
    onOpenEditor: (EditBudget) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = LocalActivity.current
    var isAlertsEnabled by remember { mutableStateOf(areBudgetAlertsEnabled(context)) }
    ObserveEffects(viewModel.effects) { effect ->
        when (effect) {
            is BudgetEffect.OpenReport -> onOpenReport(effect.key)
            is BudgetEffect.OpenEditor -> onOpenEditor(effect.key)
        }
    }
    // The day can change and the user can flip the alert setting while the app is away.
    LifecycleResumeEffect(viewModel) {
        viewModel.onIntent(BudgetIntent.Resumed)
        isAlertsEnabled = areBudgetAlertsEnabled(context)
        onPauseOrDispose {}
    }
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            isAlertsEnabled = areBudgetAlertsEnabled(context)
        }
    BudgetScreen(
        state = state,
        onIntent = viewModel::onIntent,
        isBannerShown = !isAlertsEnabled && (state.total != null || state.rows.isNotEmpty()),
        onEnableAlerts = {
            // The first ask was at the first save: when Android no longer shows the dialog, only the settings help.
            val isAskable =
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    activity != null &&
                    ActivityCompat.shouldShowRequestPermissionRationale(
                        activity,
                        Manifest.permission.POST_NOTIFICATIONS,
                    )
            if (isAskable) {
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                openAppNotificationSettings(context)
            }
        },
        modifier = modifier,
        contentPadding = contentPadding,
    )
}

/** [contentPadding] keeps the last row clear of the floating nav bar. [isBannerShown] shows the alerts-off banner. */
@Composable
fun BudgetScreen(
    state: BudgetState,
    onIntent: (BudgetIntent) -> Unit,
    modifier: Modifier = Modifier,
    isBannerShown: Boolean = false,
    onEnableAlerts: () -> Unit = {},
    contentPadding: PaddingValues = PaddingValues(),
) {
    Column(modifier = modifier.fillMaxSize()) {
        FinoteTopBar(
            title = stringResource(R.string.tab_budget),
            actions = {
                if (state.isAddEnabled) {
                    FinoteIconButton(
                        iconRes = R.drawable.ic_add,
                        contentDescription = stringResource(R.string.budget_add),
                        onClick = { onIntent(BudgetIntent.AddClicked) },
                    )
                }
            },
        )
        if (isBannerShown) {
            NotificationOffBanner(
                onEnable = onEnableAlerts,
                modifier = Modifier.padding(horizontal = FinoteTheme.spacing.lg, vertical = FinoteTheme.spacing.sm),
            )
        }
        // The header stays while loading and on an error: the month is known, and another month retries.
        MonthSwitcher(
            month = state.month,
            isNextEnabled = !state.isCurrentMonth,
            onPrevious = { onIntent(BudgetIntent.PreviousMonth) },
            onNext = { onIntent(BudgetIntent.NextMonth) },
        )
        when {
            state.isLoading -> LoadingBlocks()
            state.hasError -> FinoteEmptyState(R.drawable.ic_pie_chart, stringResource(R.string.transactions_error))
            state.total == null && state.rows.isEmpty() -> EmptyMonth(state, onIntent)
            else -> BudgetList(state, onIntent, contentPadding)
        }
    }
}

@Composable
private fun EmptyMonth(
    state: BudgetState,
    onIntent: (BudgetIntent) -> Unit,
) {
    FinoteEmptyState(
        iconRes = R.drawable.ic_pie_chart,
        title = stringResource(R.string.budget_empty),
        action =
            if (state.isAddEnabled) {
                {
                    FinoteButton(
                        text = stringResource(R.string.budget_add),
                        onClick = { onIntent(BudgetIntent.AddClicked) },
                        leadingIcon = R.drawable.ic_add,
                    )
                }
            } else {
                null
            },
    )
}

@Composable
private fun BudgetList(
    state: BudgetState,
    onIntent: (BudgetIntent) -> Unit,
    contentPadding: PaddingValues,
) {
    val side = Modifier.padding(horizontal = FinoteTheme.spacing.lg)
    LazyColumn(
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(FinoteTheme.spacing.md),
    ) {
        state.total?.let { total ->
            item(key = "total") {
                BudgetTotalCard(
                    status = total,
                    today = state.today,
                    onClick = { onIntent(BudgetIntent.TotalClicked) },
                    modifier = side,
                    onEdit =
                        if (state.isCurrentMonth) {
                            (
                                {
                                    onIntent(
                                        BudgetIntent.EditClicked(total.budget.id),
                                    )
                                }
                            )
                        } else {
                            null
                        },
                )
            }
        }
        items(state.rows, key = { it.status.budget.id }) { row ->
            val category = row.category
            BudgetRow(
                status = row.status,
                title = displayName(category.name, category.nameKey),
                onClick = { onIntent(BudgetIntent.RowClicked(category.id)) },
                modifier = side,
                leading = {
                    CategoryIcon(
                        iconRes = iconRes(category.icon),
                        colorArgb = category.color,
                        contentDescription = null,
                    )
                },
                onEdit =
                    if (state.isCurrentMonth) ({ onIntent(BudgetIntent.EditClicked(row.status.budget.id)) }) else null,
            )
        }
    }
}

@Composable
private fun LoadingBlocks() {
    Column(
        modifier = Modifier.padding(horizontal = FinoteTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(FinoteTheme.spacing.md),
    ) {
        repeat(SKELETON_ROWS) { FinoteSkeleton(Modifier.fillMaxWidth().height(FinoteTheme.sizes.touchTarget)) }
    }
}
