package io.github.toanhp123.finote.ui.common.component.finance

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import io.github.toanhp123.finote.R
import io.github.toanhp123.finote.domain.category.model.Category
import io.github.toanhp123.finote.domain.category.model.CategoryType
import io.github.toanhp123.finote.ui.common.component.display.FinoteCard
import io.github.toanhp123.finote.ui.common.component.display.FinoteListRow
import io.github.toanhp123.finote.ui.common.component.preview.FinotePreviewSurface
import io.github.toanhp123.finote.ui.common.component.preview.FinotePreviews
import io.github.toanhp123.finote.ui.common.component.preview.PreviewRedArgb
import io.github.toanhp123.finote.ui.common.format.displayName
import io.github.toanhp123.finote.ui.common.format.iconRes
import io.github.toanhp123.finote.ui.common.theme.FinoteTheme
import sh.calvin.reorderable.ReorderableCollectionItemScope
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * [rows] (one level of categories) in order, each with a drag handle. [onMove] runs for every step of a drag with the
 * row positions; the caller reorders its own list and passes it back. Without dragging, the row's accessibility
 * actions move it up or down. It scrolls itself, so give it a bounded height.
 */
@Composable
fun CategoryReorderList(
    rows: List<Category>,
    onMove: (from: Int, to: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState) { from, to -> onMove(from.index, to.index) }
    LazyColumn(
        modifier = modifier,
        state = listState,
        verticalArrangement = Arrangement.spacedBy(FinoteTheme.spacing.sm),
    ) {
        itemsIndexed(rows, key = { _, row -> row.id }) { index, row ->
            ReorderableItem(reorderState, key = row.id) { isDragging ->
                ReorderRow(row, index, isLast = index == rows.lastIndex, isDragging, onMove)
            }
        }
    }
}

@Composable
private fun ReorderableCollectionItemScope.ReorderRow(
    row: Category,
    index: Int,
    isLast: Boolean,
    isDragging: Boolean,
    onMove: (from: Int, to: Int) -> Unit,
) {
    val title = displayName(row.name, row.nameKey)
    val moveUp = stringResource(R.string.category_move_up)
    val moveDown = stringResource(R.string.category_move_down)
    FinoteCard(
        modifier =
            Modifier
                .fillMaxWidth()
                .alpha(if (isDragging || row.isHidden) FinoteTheme.colors.mutedAlpha else 1f)
                .semantics(mergeDescendants = true) {
                    customActions =
                        buildList {
                            if (index > 0) add(moveAction(moveUp) { onMove(index, index - 1) })
                            if (!isLast) add(moveAction(moveDown) { onMove(index, index + 1) })
                        }
                },
    ) {
        FinoteListRow(
            title = title,
            leading = { CategoryIcon(iconRes(row.icon), row.color, contentDescription = null) },
            trailing = { DragHandle(title) },
        )
    }
}

@Composable
private fun ReorderableCollectionItemScope.DragHandle(title: String) {
    Box(
        modifier = Modifier.size(FinoteTheme.sizes.touchTarget).draggableHandle(),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_drag_handle),
            contentDescription = stringResource(R.string.category_drag_handle, title),
            modifier = Modifier.size(FinoteTheme.sizes.iconMd),
            tint = FinoteTheme.scheme.onSurfaceVariant,
        )
    }
}

private fun moveAction(
    label: String,
    move: () -> Unit,
) = CustomAccessibilityAction(label) {
    move()
    true
}

@FinotePreviews
@Composable
private fun CategoryReorderListPreview() {
    FinotePreviewSurface {
        CategoryReorderList(
            rows =
                listOf(
                    previewRow("a", "Food", "restaurant", isHidden = false),
                    previewRow("b", "Transport", "directions_car", isHidden = true),
                ),
            onMove = { _, _ -> },
        )
    }
}

private fun previewRow(
    id: String,
    name: String,
    icon: String,
    isHidden: Boolean,
) = Category(
    id = id,
    name = name,
    nameKey = null,
    icon = icon,
    color = PreviewRedArgb,
    type = CategoryType.EXPENSE,
    parentId = null,
    isSystemDefault = false,
    isHidden = isHidden,
    sortOrder = 0,
    createdAt = 0L,
    updatedAt = 0L,
    deletedAt = null,
)
