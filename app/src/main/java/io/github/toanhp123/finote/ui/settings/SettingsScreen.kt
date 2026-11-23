package io.github.toanhp123.finote.ui.settings

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.toanhp123.finote.R
import io.github.toanhp123.finote.ui.common.ObserveEffects
import io.github.toanhp123.finote.ui.common.component.display.FinoteListRow
import io.github.toanhp123.finote.ui.common.component.display.FinoteSection
import io.github.toanhp123.finote.ui.common.component.layout.FinoteTopBar
import io.github.toanhp123.finote.ui.common.rememberNotificationListenerEnabled
import io.github.toanhp123.finote.ui.common.theme.FinoteTheme

@Composable
fun SettingsRoute(
    viewModel: SettingsViewModel,
    onOpenCapture: () -> Unit,
    onOpenAccounts: () -> Unit,
    onOpenLogs: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ObserveEffects(viewModel.effects) { effect ->
        when (effect) {
            SettingsEffect.OpenCaptureSettings -> onOpenCapture()
            SettingsEffect.OpenAccountsSettings -> onOpenAccounts()
            SettingsEffect.OpenNotificationLogs -> onOpenLogs()
        }
    }
    SettingsScreen(state, rememberNotificationListenerEnabled(), viewModel::onIntent, modifier)
}

/** [isAccessEnabled] is read in composition, not in the ViewModel, so the first frame already shows the real value. */
@Composable
fun SettingsScreen(
    state: SettingsState,
    isAccessEnabled: Boolean,
    onIntent: (SettingsIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        FinoteTopBar(title = stringResource(R.string.tab_settings))
        Column(
            modifier = Modifier.padding(horizontal = FinoteTheme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(FinoteTheme.spacing.sm),
        ) {
            FinoteSection(title = stringResource(R.string.settings_group_accounts)) {
                FinoteListRow(
                    title = stringResource(R.string.settings_accounts_title),
                    subtitle = stringResource(R.string.settings_accounts_subtitle),
                    leading = { RowIcon(R.drawable.ic_payments, FinoteTheme.scheme.primary) },
                    trailing = { RowIcon(R.drawable.ic_chevron_right, FinoteTheme.scheme.onSurfaceVariant) },
                    onClick = { onIntent(SettingsIntent.AccountsClicked) },
                )
            }
            FinoteSection(title = stringResource(R.string.settings_group_capture)) {
                FinoteListRow(
                    title = stringResource(R.string.settings_capture_title),
                    subtitle = captureSubtitle(state.bankCount, isAccessEnabled),
                    leading = { RowIcon(R.drawable.ic_notifications, FinoteTheme.scheme.primary) },
                    trailing = { RowIcon(R.drawable.ic_chevron_right, FinoteTheme.scheme.onSurfaceVariant) },
                    onClick = { onIntent(SettingsIntent.CaptureClicked) },
                )
                if (state.isToolsEnabled) {
                    FinoteListRow(
                        title = stringResource(R.string.settings_logs_title),
                        subtitle = stringResource(R.string.settings_logs_subtitle),
                        leading = { RowIcon(R.drawable.ic_receipt_long, FinoteTheme.scheme.primary) },
                        trailing = { RowIcon(R.drawable.ic_chevron_right, FinoteTheme.scheme.onSurfaceVariant) },
                        onClick = { onIntent(SettingsIntent.LogsClicked) },
                    )
                }
            }
        }
    }
}

@Composable
private fun RowIcon(
    @DrawableRes iconRes: Int,
    tint: Color,
) {
    Icon(
        painter = painterResource(iconRes),
        contentDescription = null,
        modifier = Modifier.size(FinoteTheme.sizes.iconMd),
        tint = tint,
    )
}

@Composable
private fun captureSubtitle(
    bankCount: Int,
    isAccessEnabled: Boolean,
): String {
    val access = if (isAccessEnabled) R.string.settings_access_on else R.string.settings_access_off
    return pluralStringResource(
        R.plurals.settings_capture_subtitle,
        bankCount,
        stringResource(access),
        bankCount,
    )
}
