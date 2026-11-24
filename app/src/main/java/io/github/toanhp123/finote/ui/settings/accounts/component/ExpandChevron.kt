package io.github.toanhp123.finote.ui.settings.accounts.component

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import io.github.toanhp123.finote.R
import io.github.toanhp123.finote.ui.common.theme.FinoteTheme

/** A chevron that points right when closed and down when open. Its description names the action a tap does. */
@Composable
fun ExpandChevron(
    isExpanded: Boolean,
    modifier: Modifier = Modifier,
) {
    Icon(
        painter = painterResource(R.drawable.ic_chevron_right),
        contentDescription = stringResource(if (isExpanded) R.string.accounts_collapse else R.string.accounts_expand),
        modifier = modifier.size(FinoteTheme.sizes.iconMd).rotate(if (isExpanded) EXPANDED_DEGREES else 0f),
        tint = FinoteTheme.scheme.onSurfaceVariant,
    )
}

private const val EXPANDED_DEGREES = 90f
