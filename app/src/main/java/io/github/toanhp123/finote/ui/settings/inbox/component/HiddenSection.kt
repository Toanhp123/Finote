package io.github.toanhp123.finote.ui.settings.inbox.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import io.github.toanhp123.finote.R
import io.github.toanhp123.finote.ui.common.theme.FinoteTheme

private const val EXPANDED_CHEVRON_DEGREES = 90f

/**
 * The header of the hidden items: "Hidden (N)" and a chevron that turns when [isExpanded]. The list draws the hidden
 * rows itself, one lazy item each, because an ad-heavy package can hide hundreds.
 */
@Composable
fun HiddenSection(
    count: Int,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = FinoteTheme.sizes.touchTarget)
                .clickable(role = Role.Button, onClick = onToggle),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(FinoteTheme.spacing.sm),
    ) {
        Text(
            text = stringResource(R.string.inbox_hidden_header, count),
            modifier = Modifier.weight(1f).semantics { heading() },
            color = FinoteTheme.scheme.onSurfaceVariant,
            style = FinoteTheme.typography.material.labelMedium,
        )
        Icon(
            painter = painterResource(R.drawable.ic_chevron_right),
            contentDescription = null,
            modifier = Modifier.size(FinoteTheme.sizes.iconMd).rotate(if (isExpanded) EXPANDED_CHEVRON_DEGREES else 0f),
            tint = FinoteTheme.scheme.onSurfaceVariant,
        )
    }
}
