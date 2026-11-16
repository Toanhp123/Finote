package io.github.toanhp123.finote.ui.settings.capture

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.toanhp123.finote.R
import io.github.toanhp123.finote.domain.notification.model.RuleUpdateCheck
import io.github.toanhp123.finote.domain.notification.model.RuleUpdateResult
import io.github.toanhp123.finote.ui.common.ObserveEffects
import io.github.toanhp123.finote.ui.common.ReportListenerAccess
import io.github.toanhp123.finote.ui.common.component.control.FinoteButton
import io.github.toanhp123.finote.ui.common.component.control.FinoteButtonStyle
import io.github.toanhp123.finote.ui.common.component.control.FinoteSwitchRow
import io.github.toanhp123.finote.ui.common.component.display.FinoteListRow
import io.github.toanhp123.finote.ui.common.component.display.FinoteSection
import io.github.toanhp123.finote.ui.common.component.layout.FinoteTopBar
import io.github.toanhp123.finote.ui.common.format.formatShortDateTime
import io.github.toanhp123.finote.ui.common.openListenerSettings
import io.github.toanhp123.finote.ui.common.theme.FinoteTheme
import io.github.toanhp123.finote.ui.settings.logic.BankSummary
import java.time.ZoneId

@Composable
fun NotificationCaptureSettingsRoute(
    viewModel: NotificationCaptureSettingsViewModel,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    ReportListenerAccess { viewModel.onIntent(NotificationCaptureSettingsIntent.Resumed(it)) }
    ObserveEffects(viewModel.effects) { effect ->
        when (effect) {
            NotificationCaptureSettingsEffect.OpenListenerSettings -> {
                openListenerSettings(context)
            }

            NotificationCaptureSettingsEffect.Close -> {
                onClose()
            }
        }
    }
    NotificationCaptureSettingsScreen(state, viewModel::onIntent, modifier)
}

@Composable
fun NotificationCaptureSettingsScreen(
    state: NotificationCaptureSettingsState,
    onIntent: (NotificationCaptureSettingsIntent) -> Unit,
    modifier: Modifier = Modifier,
    zone: ZoneId = ZoneId.systemDefault(),
) {
    Column(modifier = modifier.fillMaxSize()) {
        FinoteTopBar(
            title = stringResource(R.string.settings_capture_title),
            onBack = { onIntent(NotificationCaptureSettingsIntent.BackClicked) },
        )
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = FinoteTheme.spacing.lg)
                    .padding(top = FinoteTheme.spacing.sm),
            verticalArrangement = Arrangement.spacedBy(FinoteTheme.spacing.lg),
        ) {
            AccessSection(state.isAccessEnabled, onIntent)
            PatternsSection(state, onIntent, zone)
            if (state.banks.isNotEmpty()) BanksSection(state.banks)
        }
    }
}

@Composable
private fun AccessSection(
    isEnabled: Boolean,
    onIntent: (NotificationCaptureSettingsIntent) -> Unit,
) {
    val accessLabel = if (isEnabled) R.string.settings_access_on else R.string.settings_access_off
    FinoteSection(stringResource(R.string.capture_group_access)) {
        FinoteListRow(
            title = stringResource(R.string.capture_access_row),
            trailing = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(accessLabel),
                        color = FinoteTheme.scheme.onSurfaceVariant,
                        style = FinoteTheme.typography.material.bodyMedium,
                    )
                    Icon(
                        painter = painterResource(R.drawable.ic_chevron_right),
                        contentDescription = null,
                        modifier = Modifier.size(FinoteTheme.sizes.iconMd),
                        tint = FinoteTheme.scheme.onSurfaceVariant,
                    )
                }
            },
            onClick = { onIntent(NotificationCaptureSettingsIntent.AccessClicked) },
        )
    }
}

@Composable
private fun PatternsSection(
    state: NotificationCaptureSettingsState,
    onIntent: (NotificationCaptureSettingsIntent) -> Unit,
    zone: ZoneId,
) {
    FinoteSection(stringResource(R.string.capture_group_patterns)) {
        Column(verticalArrangement = Arrangement.spacedBy(FinoteTheme.spacing.xs)) {
            FinoteSwitchRow(
                title = stringResource(R.string.capture_auto_update),
                checked = state.isAutoUpdateEnabled,
                onCheckedChange = { onIntent(NotificationCaptureSettingsIntent.AutoUpdateToggled(it)) },
            )
            Text(
                text = stringResource(R.string.capture_privacy),
                color = FinoteTheme.scheme.onSurfaceVariant,
                style = FinoteTheme.typography.material.bodySmall,
            )
            // The button gives the spinner and the disabled look while checking; the design system has no bare spinner.
            FinoteButton(
                text = stringResource(R.string.capture_check_now),
                onClick = { onIntent(NotificationCaptureSettingsIntent.CheckNowClicked) },
                modifier = Modifier.fillMaxWidth(),
                style = FinoteButtonStyle.Ghost,
                isLoading = state.isChecking,
            )
            state.lastCheck?.let { LastCheckLine(it, zone) }
        }
    }
}

@Composable
private fun LastCheckLine(
    check: RuleUpdateCheck,
    zone: ZoneId,
) {
    val locale = LocalConfiguration.current.locales[0]
    val time = remember(check.checkedAt, zone, locale) { formatShortDateTime(check.checkedAt, zone, locale) }
    Text(
        text = stringResource(R.string.capture_last_check, time, resultText(check.result)),
        color = FinoteTheme.scheme.onSurfaceVariant,
        style = FinoteTheme.typography.material.bodySmall,
    )
}

@Composable
private fun BanksSection(banks: List<BankSummary>) {
    FinoteSection(stringResource(R.string.capture_group_banks)) {
        Column {
            banks.forEach { bank ->
                FinoteListRow(title = bank.bankName, subtitle = bankSubtitle(bank))
            }
        }
    }
}

@Composable
private fun resultText(result: RuleUpdateResult): String =
    when (result) {
        is RuleUpdateResult.Updated -> {
            val count = result.count
            pluralStringResource(R.plurals.capture_result_updated, count, count)
        }

        RuleUpdateResult.UpToDate -> {
            stringResource(R.string.capture_result_up_to_date)
        }

        RuleUpdateResult.Network -> {
            stringResource(R.string.capture_result_network)
        }

        RuleUpdateResult.Invalid -> {
            stringResource(R.string.capture_result_invalid)
        }

        RuleUpdateResult.Error -> {
            stringResource(R.string.capture_result_error)
        }
    }

@Composable
private fun bankSubtitle(bank: BankSummary): String {
    val count = bank.activeRuleCount
    val summary = pluralStringResource(R.plurals.capture_bank_summary, count, count, bank.highestVersion)
    return if (bank.hasUserEdits) "$summary · ${stringResource(R.string.capture_bank_edited)}" else summary
}
