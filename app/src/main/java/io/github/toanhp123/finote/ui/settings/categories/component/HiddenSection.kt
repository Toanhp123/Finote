package io.github.toanhp123.finote.ui.settings.categories.component

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.toanhp123.finote.R
import io.github.toanhp123.finote.ui.common.component.display.ExpandChevron
import io.github.toanhp123.finote.ui.common.component.display.FinoteCard
import io.github.toanhp123.finote.ui.common.component.display.FinoteListRow
import io.github.toanhp123.finote.ui.common.component.preview.FinotePreviewSurface
import io.github.toanhp123.finote.ui.common.component.preview.FinotePreviews

/** The hidden top-level categories: a header with their [count] that opens and closes [content], the rows. */
@Composable
fun HiddenSection(
    count: Int,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    FinoteCard(modifier = modifier.fillMaxWidth()) {
        FinoteListRow(
            title = stringResource(R.string.categories_hidden_title, count),
            trailing = { ExpandChevron(isExpanded) },
            onClick = onToggle,
        )
        if (isExpanded) content()
    }
}

@FinotePreviews
@Composable
private fun HiddenSectionPreview() {
    FinotePreviewSurface {
        HiddenSection(count = 2, isExpanded = true, onToggle = {}) { Text(text = "Rows") }
    }
}
