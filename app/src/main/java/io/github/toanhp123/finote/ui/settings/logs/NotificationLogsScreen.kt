package io.github.toanhp123.finote.ui.settings.logs

import android.content.ClipData
import android.content.ClipDescription
import android.os.Build
import android.os.PersistableBundle
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.toClipEntry
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.toanhp123.finote.R
import io.github.toanhp123.finote.domain.notification.model.NotificationLog
import io.github.toanhp123.finote.domain.transaction.model.TransactionType
import io.github.toanhp123.finote.ui.common.ObserveEffects
import io.github.toanhp123.finote.ui.common.component.display.FinoteEmptyState
import io.github.toanhp123.finote.ui.common.component.finance.MoneyText
import io.github.toanhp123.finote.ui.common.component.layout.FinoteTopBar
import io.github.toanhp123.finote.ui.common.component.overlay.FinoteSnackbarState
import io.github.toanhp123.finote.ui.common.format.MoneyKind
import io.github.toanhp123.finote.ui.common.format.formatShortDateTime
import io.github.toanhp123.finote.ui.common.theme.FinoteTheme
import io.github.toanhp123.finote.ui.settings.logs.component.LogDetailSheet
import kotlinx.coroutines.launch
import java.time.ZoneId

@Composable
fun NotificationLogsRoute(
    viewModel: NotificationLogsViewModel,
    snackbarState: FinoteSnackbarState,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboard.current
    val copied = stringResource(R.string.logs_copied)
    ObserveEffects(viewModel.effects) { effect ->
        when (effect) {
            NotificationLogsEffect.Close -> {
                onClose()
            }

            is NotificationLogsEffect.CopySample -> {
                scope.launch {
                    clipboard.setClipEntry(sensitiveClip(effect.json).toClipEntry())
                    snackbarState.show(copied)
                }
            }
        }
    }
    NotificationLogsScreen(state, viewModel::onIntent, modifier)
}

@Composable
fun NotificationLogsScreen(
    state: NotificationLogsState,
    onIntent: (NotificationLogsIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            FinoteTopBar(
                title = stringResource(R.string.settings_logs_title),
                onBack = { onIntent(NotificationLogsIntent.BackClicked) },
            )
            if (state.logs.isEmpty()) {
                FinoteEmptyState(iconRes = R.drawable.ic_notifications, title = stringResource(R.string.logs_empty))
            } else {
                LogList(state, onIntent)
            }
        }
        LogDetailSheet(state, onIntent)
    }
}

@Composable
private fun LogList(
    state: NotificationLogsState,
    onIntent: (NotificationLogsIntent) -> Unit,
) {
    LazyColumn(contentPadding = PaddingValues(horizontal = FinoteTheme.spacing.lg)) {
        items(state.logs, key = { it.id }) { log ->
            LogRow(log, state.bankNames[log.packageName] ?: log.packageName) {
                onIntent(NotificationLogsIntent.LogClicked(log.id))
            }
        }
    }
}

@Composable
private fun LogRow(
    log: NotificationLog,
    sourceName: String,
    onClick: () -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    val time = remember(log.receivedAt, locale) { formatShortDateTime(log.receivedAt, ZoneId.systemDefault(), locale) }
    val muted = FinoteTheme.scheme.onSurfaceVariant
    val small = FinoteTheme.typography.material.bodySmall
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = FinoteTheme.sizes.touchTarget)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(vertical = FinoteTheme.spacing.sm),
        verticalArrangement = Arrangement.spacedBy(FinoteTheme.spacing.xs),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(FinoteTheme.spacing.md),
        ) {
            Text(
                text = sourceName,
                modifier = Modifier.weight(1f),
                color = FinoteTheme.scheme.onSurface,
                style = FinoteTheme.typography.material.bodyLarge,
            )
            log.parsed?.let { MoneyText(amount = it.amount, kind = moneyKindOf(it.type)) }
        }
        Text(text = stringResource(R.string.logs_row_meta, time, log.status.name), color = muted, style = small)
        log.failureReason?.let { Text(text = it, color = muted, style = small) }
        if (log.content.isNotBlank()) {
            Text(text = log.content, color = muted, style = small, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private fun moneyKindOf(type: TransactionType) =
    when (type) {
        TransactionType.INCOME, TransactionType.REFUND -> MoneyKind.Income
        TransactionType.EXPENSE -> MoneyKind.Expense
        TransactionType.TRANSFER -> MoneyKind.Transfer
        TransactionType.ADJUSTMENT -> MoneyKind.Neutral
    }

/** A clip the keyboard and the system toast hide: API 33 and up honor the sensitive flag, lower versions ignore it. */
private fun sensitiveClip(text: String): ClipData =
    ClipData.newPlainText("", text).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            description.extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
        }
    }
