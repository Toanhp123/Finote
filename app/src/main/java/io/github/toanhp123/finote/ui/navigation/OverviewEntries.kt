package io.github.toanhp123.finote.ui.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import io.github.toanhp123.finote.ui.overview.OverviewRoute
import io.github.toanhp123.finote.ui.report.ReportListRoute
import io.github.toanhp123.finote.ui.report.ReportListViewModel
import io.github.toanhp123.finote.ui.report.logic.contentKey
import io.github.toanhp123.finote.ui.report.logic.toNavKey

/** The Overview tab. A tap on a bucket or merchant opens the rows behind that number. */
internal fun EntryProviderScope<NavKey>.overviewEntry(
    backStack: MutableList<NavKey>,
    padding: PaddingValues,
) {
    entry<Overview> {
        OverviewRoute(
            viewModel = hiltViewModel(),
            // A second tap while a report list is on top does nothing, so a double tap cannot stack it.
            onOpenReport = { month, filter ->
                if (backStack.lastOrNull() !is ReportList) backStack.add(filter.toNavKey(month))
            },
            contentPadding = padding,
        )
    }
}

/** The rows behind one Overview amount, pushed on top of Overview; a row opens the edit sheet. */
internal fun EntryProviderScope<NavKey>.reportListEntry(
    backStack: MutableList<NavKey>,
    padding: PaddingValues,
) {
    entry<ReportList>(clazzContentKey = { it.contentKey() }) { key ->
        ReportListRoute(
            viewModel =
                hiltViewModel<ReportListViewModel, ReportListViewModel.Factory>(
                    creationCallback = { it.create(key) },
                ),
            onOpenEditor = { id -> openEditor(backStack, id) },
            onClose = { backStack.remove(key) },
            modifier = Modifier.padding(padding),
        )
    }
}
