package io.github.toanhp123.finote.ui.common.component

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import io.github.toanhp123.finote.R
import io.github.toanhp123.finote.ui.common.theme.FinoteTheme

/** A title row. A back button shows when [onBack] is set. [actions] sit at the end. */
@Composable
fun FinoteTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val spacing = FinoteTheme.spacing
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = FinoteTheme.sizes.touchTarget)
                .padding(horizontal = spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            FinoteIconButton(
                iconRes = R.drawable.ic_arrow_back,
                contentDescription = stringResource(R.string.cd_back),
                onClick = onBack,
            )
        }
        Text(
            text = title,
            modifier =
                Modifier
                    .weight(1f)
                    .padding(horizontal = if (onBack == null) spacing.md else spacing.xs)
                    .semantics { heading() },
            color = FinoteTheme.scheme.onSurface,
            style = FinoteTheme.typography.material.titleLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        actions()
    }
}

@FinotePreviews
@Composable
private fun FinoteTopBarPreview() {
    FinotePreviewSurface {
        FinoteTopBar(
            title = "Transactions",
            onBack = {},
            actions = {
                FinoteIconButton(
                    iconRes = R.drawable.ic_settings,
                    contentDescription = "Settings",
                    onClick = {},
                )
            },
        )
    }
}
