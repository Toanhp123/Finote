package io.github.toanhp123.finote.ui.cashentry.component

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.toanhp123.finote.R
import io.github.toanhp123.finote.domain.category.model.Category
import io.github.toanhp123.finote.ui.common.format.displayName
import io.github.toanhp123.finote.ui.common.format.iconRes
import io.github.toanhp123.finote.ui.common.theme.FinoteTheme

private const val CHILD_DEPTH = 1
private const val NO_CATEGORY_KEY = "no_category"

/** A flat "no category" row, then each top-level category with its children indented below it. */
@Composable
fun CategoryList(
    categories: List<Category>,
    selectedId: String?,
    onPick: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rows = remember(categories) { categories.asTree() }
    LazyColumn(modifier = modifier) {
        item(key = NO_CATEGORY_KEY) {
            PickerRow(
                title = stringResource(R.string.cash_entry_no_category),
                isSelected = selectedId == null,
                onClick = { onPick(null) },
            )
        }
        items(rows, key = { (category, _) -> category.id }) { (category, depth) ->
            PickerRow(
                title = displayName(category.name, category.nameKey),
                isSelected = category.id == selectedId,
                onClick = { onPick(category.id) },
                icon = iconRes(category.icon),
                colorArgb = category.color,
                modifier = Modifier.padding(start = FinoteTheme.spacing.xl * depth),
            )
        }
    }
}

/** Roots first, each followed by its children. A category whose parent is missing counts as a root. */
private fun List<Category>.asTree(): List<Pair<Category, Int>> {
    val ids = mapTo(HashSet()) { it.id }
    val children = filter { it.parentId in ids }.groupBy { it.parentId }
    return filter { it.parentId == null || it.parentId !in ids }
        .flatMap { root -> listOf(root to 0) + children[root.id].orEmpty().map { it to CHILD_DEPTH } }
}
