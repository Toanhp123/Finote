package io.github.toanhp123.finote.ui.transactions.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.toanhp123.finote.R
import io.github.toanhp123.finote.ui.common.component.display.FinoteCard
import io.github.toanhp123.finote.ui.common.theme.FinoteTheme

/** Tells the user that automatic capture is off. A tap opens the setup screen. It has no close button. */
@Composable
fun ListenerOffBanner(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FinoteCard(modifier = modifier.fillMaxWidth(), onClick = onClick) {
        Text(
            text = stringResource(R.string.transactions_listener_off),
            color = FinoteTheme.scheme.onSurface,
            style = FinoteTheme.typography.material.bodyMedium,
        )
    }
}
