package io.github.toanhp123.finote.ui.setup

import android.content.Intent
import android.provider.Settings
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.toanhp123.finote.R
import io.github.toanhp123.finote.ui.common.ObserveEffects
import io.github.toanhp123.finote.ui.common.component.control.FinoteButton
import io.github.toanhp123.finote.ui.common.component.control.FinoteButtonStyle
import io.github.toanhp123.finote.ui.common.component.display.FinoteCard
import io.github.toanhp123.finote.ui.common.isNotificationListenerEnabled
import io.github.toanhp123.finote.ui.common.theme.FinoteTheme

@Composable
fun SetupRoute(
    viewModel: SetupViewModel,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // Every resume, so the screen notices the toggle the user flipped in the system settings.
    LifecycleResumeEffect(viewModel) {
        viewModel.onIntent(SetupIntent.Resumed(isNotificationListenerEnabled(context)))
        onPauseOrDispose {}
    }
    ObserveEffects(viewModel.effects) { effect ->
        when (effect) {
            SetupEffect.OpenListenerSettings -> {
                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }

            SetupEffect.Finished -> {
                onFinish()
            }
        }
    }
    SetupScreen(state, viewModel::onIntent, modifier)
}

@Composable
fun SetupScreen(
    state: SetupState,
    onIntent: (SetupIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = FinoteTheme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(FinoteTheme.spacing.lg),
        ) {
            // A heading in the scroll area, not a FinoteTopBar: its title is one line, and this one is long.
            Text(
                text = stringResource(R.string.setup_title),
                modifier = Modifier.padding(top = FinoteTheme.spacing.md).semantics { heading() },
                color = FinoteTheme.scheme.onSurface,
                style = FinoteTheme.typography.material.headlineSmall,
            )
            Text(
                text = stringResource(R.string.setup_body),
                color = FinoteTheme.scheme.onSurface,
                style = FinoteTheme.typography.material.bodyLarge,
            )
            FinoteCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(FinoteTheme.spacing.md)) {
                    Commitment(stringResource(R.string.setup_point_banks))
                    Commitment(stringResource(R.string.setup_point_otp))
                    Commitment(stringResource(R.string.setup_point_local))
                }
            }
            Text(
                text = stringResource(R.string.setup_howto),
                color = FinoteTheme.scheme.onSurface,
                style = FinoteTheme.typography.material.bodyLarge,
            )
            Text(
                text = stringResource(R.string.setup_restricted_hint),
                color = FinoteTheme.scheme.onSurfaceVariant,
                style = FinoteTheme.typography.material.bodySmall,
            )
        }
        SetupActions(state.step, onIntent)
    }
}

@Composable
private fun Commitment(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(FinoteTheme.spacing.md)) {
        Icon(
            painter = painterResource(R.drawable.ic_check),
            contentDescription = null,
            modifier = Modifier.size(FinoteTheme.sizes.iconMd),
            tint = FinoteTheme.scheme.primary,
        )
        Text(
            text = text,
            modifier = Modifier.weight(1f),
            color = FinoteTheme.scheme.onSurface,
            style = FinoteTheme.typography.material.bodyMedium,
        )
    }
}

@Composable
private fun SetupActions(
    step: SetupStep,
    onIntent: (SetupIntent) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(FinoteTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(FinoteTheme.spacing.sm),
    ) {
        if (step == SetupStep.GRANTED) {
            Row(
                modifier = Modifier.align(Alignment.CenterHorizontally),
                horizontalArrangement = Arrangement.spacedBy(FinoteTheme.spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_check),
                    contentDescription = null,
                    modifier = Modifier.size(FinoteTheme.sizes.iconMd),
                    tint = FinoteTheme.scheme.primary,
                )
                Text(
                    text = stringResource(R.string.setup_granted),
                    color = FinoteTheme.scheme.primary,
                    style = FinoteTheme.typography.material.titleMedium,
                )
            }
        } else {
            val enableLabel = if (step == SetupStep.RETRY) R.string.setup_retry else R.string.setup_enable
            FinoteButton(
                text = stringResource(enableLabel),
                onClick = { onIntent(SetupIntent.EnableClicked) },
                modifier = Modifier.fillMaxWidth(),
            )
            FinoteButton(
                text = stringResource(R.string.setup_later),
                onClick = { onIntent(SetupIntent.LaterClicked) },
                modifier = Modifier.fillMaxWidth(),
                style = FinoteButtonStyle.Ghost,
            )
        }
    }
}
