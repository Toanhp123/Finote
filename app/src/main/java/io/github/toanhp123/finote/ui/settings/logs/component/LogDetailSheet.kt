package io.github.toanhp123.finote.ui.settings.logs.component

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import io.github.toanhp123.finote.R
import io.github.toanhp123.finote.domain.notification.model.NotificationLog
import io.github.toanhp123.finote.ui.common.component.control.FinoteButton
import io.github.toanhp123.finote.ui.common.component.finance.MoneyText
import io.github.toanhp123.finote.ui.common.component.overlay.FinoteBottomSheet
import io.github.toanhp123.finote.ui.common.format.MoneyKind
import io.github.toanhp123.finote.ui.common.format.formatShortDateTime
import io.github.toanhp123.finote.ui.common.rememberLastNonNull
import io.github.toanhp123.finote.ui.common.theme.FinoteTheme
import io.github.toanhp123.finote.ui.settings.logs.NotificationLogsIntent
import io.github.toanhp123.finote.ui.settings.logs.NotificationLogsState
import java.time.ZoneId
import java.util.Locale

@Composable
fun LogDetailSheet(
    state: NotificationLogsState,
    onIntent: (NotificationLogsIntent) -> Unit,
) {
    // The sheet keeps its content while it slides out.
    val shownLog = rememberLastNonNull(state.selected)
    FinoteBottomSheet(
        isVisible = state.selected != null,
        onDismiss = { onIntent(NotificationLogsIntent.SheetDismissed) },
    ) {
        shownLog?.let { LogDetailContent(it, state.isCopyEnabled, onIntent) }
    }
}

@Composable
private fun ColumnScope.LogDetailContent(
    log: NotificationLog,
    isCopyEnabled: Boolean,
    onIntent: (NotificationLogsIntent) -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    val none = stringResource(R.string.logs_none)
    Text(
        text = log.title.ifBlank { none },
        modifier = Modifier.padding(bottom = FinoteTheme.spacing.sm).semantics { heading() },
        color = FinoteTheme.scheme.onSurface,
        style = FinoteTheme.typography.material.titleLarge,
    )
    Field(R.string.logs_field_content, log.content.ifBlank { none })
    Field(R.string.logs_field_status, log.status.name)
    Field(R.string.logs_field_failure, log.failureReason ?: none)
    Field(R.string.logs_field_post_time, rememberTime(log.postTime, locale))
    Field(R.string.logs_field_received_at, rememberTime(log.receivedAt, locale))
    Field(R.string.logs_field_channel, log.channelId ?: none)
    Field(R.string.logs_field_hash, log.contentHash)
    Field(R.string.logs_field_rule, log.matchedRuleId ?: none)
    ParsedSection(log, none)
    Field(R.string.logs_field_transaction, log.linkedTransactionId ?: none)
    FinoteButton(
        text = stringResource(R.string.logs_copy),
        onClick = { onIntent(NotificationLogsIntent.CopyClicked) },
        modifier = Modifier.fillMaxWidth().padding(top = FinoteTheme.spacing.md),
        enabled = isCopyEnabled,
    )
    Text(
        text = stringResource(R.string.logs_copy_note),
        modifier = Modifier.padding(top = FinoteTheme.spacing.sm),
        color = FinoteTheme.scheme.onSurfaceVariant,
        style = FinoteTheme.typography.material.bodySmall,
    )
}

@Composable
private fun ParsedSection(
    log: NotificationLog,
    none: String,
) {
    val parsed = log.parsed
    Column {
        Field(R.string.logs_field_type, parsed?.type?.name ?: none)
        FieldRow(R.string.logs_field_amount) { parsed?.let { MoneyAmount(it.amount) } ?: ValueText(none) }
        FieldRow(R.string.logs_field_balance) { parsed?.postBalance?.let { MoneyAmount(it) } ?: ValueText(none) }
        Field(R.string.logs_field_mask, parsed?.accountMask ?: none)
        Field(R.string.logs_field_merchant, parsed?.merchant ?: none)
        Field(R.string.logs_field_note, parsed?.note ?: none)
    }
}

@Composable
private fun Field(
    @StringRes labelRes: Int,
    value: String,
) {
    FieldRow(labelRes) { ValueText(value) }
}

@Composable
private fun ValueText(text: String) {
    Text(text = text, color = FinoteTheme.scheme.onSurface, style = FinoteTheme.typography.material.bodyMedium)
}

@Composable
private fun MoneyAmount(amount: Long) {
    MoneyText(amount = amount, kind = MoneyKind.Neutral, style = FinoteTheme.typography.material.bodyMedium)
}

@Composable
private fun FieldRow(
    @StringRes labelRes: Int,
    value: @Composable () -> Unit,
) {
    Column(modifier = Modifier.padding(vertical = FinoteTheme.spacing.xs)) {
        Text(
            text = stringResource(labelRes),
            color = FinoteTheme.scheme.onSurfaceVariant,
            style = FinoteTheme.typography.material.labelMedium,
        )
        value()
    }
}

@Composable
private fun rememberTime(
    epochMillis: Long,
    locale: Locale,
): String = remember(epochMillis, locale) { formatShortDateTime(epochMillis, ZoneId.systemDefault(), locale) }
