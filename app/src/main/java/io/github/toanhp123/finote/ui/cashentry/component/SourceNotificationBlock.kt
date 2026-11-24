package io.github.toanhp123.finote.ui.cashentry.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import io.github.toanhp123.finote.R
import io.github.toanhp123.finote.ui.cashentry.SourceText
import io.github.toanhp123.finote.ui.common.component.display.FinoteCard
import io.github.toanhp123.finote.ui.common.theme.FinoteTheme

private const val COLLAPSED_LINES = 1
private const val EXPANDED_LINES = 8

/**
 * The original notification above the from-log form, so the user can check amount, card digits and direction. One
 * line of the title and of the text until the card is tapped; a tap shows more, another tap folds it again.
 */
@Composable
fun SourceNotificationBlock(
    source: SourceText,
    modifier: Modifier = Modifier,
) {
    var isExpanded by rememberSaveable { mutableStateOf(false) }
    val lines = if (isExpanded) EXPANDED_LINES else COLLAPSED_LINES
    val toggle = if (isExpanded) R.string.cash_entry_source_collapse else R.string.cash_entry_source_expand
    val muted = FinoteTheme.scheme.onSurfaceVariant
    FinoteCard(modifier = modifier.fillMaxWidth(), onClick = { isExpanded = !isExpanded }) {
        Text(
            text = stringResource(R.string.cash_entry_source_title),
            color = muted,
            style = FinoteTheme.typography.material.labelMedium,
        )
        if (source.title.isNotBlank()) {
            Text(
                text = source.title,
                color = FinoteTheme.scheme.onSurface,
                style = FinoteTheme.typography.material.titleSmall,
                maxLines = lines,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = source.content,
            color = muted,
            style = FinoteTheme.typography.material.bodySmall,
            maxLines = lines,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = stringResource(toggle),
            color = FinoteTheme.scheme.primary,
            style = FinoteTheme.typography.material.labelMedium,
        )
    }
}
