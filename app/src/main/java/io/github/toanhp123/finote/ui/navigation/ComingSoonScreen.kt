package io.github.toanhp123.finote.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.toanhp123.finote.R
import io.github.toanhp123.finote.ui.common.component.FinoteEmptyState
import io.github.toanhp123.finote.ui.common.component.FinoteTopBar

/** Placeholder for a tab that has no screen yet. */
@Composable
fun ComingSoonScreen(
    @StringRes titleRes: Int,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        FinoteTopBar(title = stringResource(titleRes))
        FinoteEmptyState(iconRes = R.drawable.ic_stars, title = stringResource(R.string.coming_soon_title))
    }
}
