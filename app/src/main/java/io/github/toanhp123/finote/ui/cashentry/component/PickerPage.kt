package io.github.toanhp123.finote.ui.cashentry.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import io.github.toanhp123.finote.R
import io.github.toanhp123.finote.ui.common.component.control.FinoteIconButton
import io.github.toanhp123.finote.ui.common.theme.FinoteTheme

/** A page with a header that slides with it: a back button, a centered title, then [content] in the rest. */
@Composable
fun PickerPage(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Box(modifier = Modifier.fillMaxWidth().heightIn(min = FinoteTheme.sizes.touchTarget)) {
            FinoteIconButton(
                iconRes = R.drawable.ic_arrow_back,
                contentDescription = stringResource(R.string.cd_back),
                onClick = onBack,
                modifier = Modifier.align(Alignment.CenterStart),
            )
            Text(
                text = title,
                modifier =
                    Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = FinoteTheme.sizes.touchTarget)
                        .semantics { heading() },
                color = FinoteTheme.scheme.onSurface,
                style = FinoteTheme.typography.material.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) { content() }
    }
}
