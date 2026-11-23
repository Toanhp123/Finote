package io.github.toanhp123.finote.ui.common.component.display

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import io.github.toanhp123.finote.ui.common.component.preview.FinotePreviewSurface
import io.github.toanhp123.finote.ui.common.component.preview.FinotePreviews
import io.github.toanhp123.finote.ui.common.theme.FinoteTheme

/** A heading with a [FinoteCard] under it. The title is announced as a heading. */
@Composable
fun FinoteSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(FinoteTheme.spacing.sm)) {
        Text(
            text = title,
            modifier = Modifier.semantics { heading() },
            color = FinoteTheme.scheme.onSurfaceVariant,
            style = FinoteTheme.typography.material.labelMedium,
        )
        FinoteCard(modifier = Modifier.fillMaxWidth()) { content() }
    }
}

@FinotePreviews
@Composable
private fun FinoteSectionPreview() {
    FinotePreviewSurface {
        FinoteSection(title = "Accounts") { Text(text = "Body") }
    }
}
