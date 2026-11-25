package io.github.toanhp123.finote.ui.settings.categoryedit.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import io.github.toanhp123.finote.R
import io.github.toanhp123.finote.domain.category.model.Category
import io.github.toanhp123.finote.domain.category.model.CategoryType
import io.github.toanhp123.finote.ui.common.component.display.FinoteListRow
import io.github.toanhp123.finote.ui.common.component.display.FinoteSection
import io.github.toanhp123.finote.ui.common.component.finance.CategoryIcon
import io.github.toanhp123.finote.ui.common.component.preview.FinotePreviewSurface
import io.github.toanhp123.finote.ui.common.component.preview.FinotePreviews
import io.github.toanhp123.finote.ui.common.component.preview.PreviewRedArgb
import io.github.toanhp123.finote.ui.common.format.displayName
import io.github.toanhp123.finote.ui.common.format.iconRes
import io.github.toanhp123.finote.ui.common.theme.FinoteTheme

/**
 * The live children of a category, hidden ones dimmed, with "Add subcategory" and, from two children on, a reorder
 * row. [onReorder] only asks to enter reorder mode: the screen owns the order while it is dragged.
 */
@Composable
fun SubcategorySection(
    children: List<Category>,
    onOpen: (Category) -> Unit,
    onAdd: () -> Unit,
    onReorder: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FinoteSection(title = stringResource(R.string.category_edit_children), modifier = modifier) {
        children.forEach { child ->
            FinoteListRow(
                title = displayName(child.name, child.nameKey),
                modifier = Modifier.alpha(if (child.isHidden) FinoteTheme.colors.mutedAlpha else 1f),
                subtitle = if (child.isHidden) stringResource(R.string.category_hidden_label) else null,
                leading = { CategoryIcon(iconRes(child.icon), child.color, contentDescription = null) },
                trailing = { RowIcon(R.drawable.ic_chevron_right) },
                onClick = { onOpen(child) },
            )
        }
        if (children.size > 1) {
            FinoteListRow(
                title = stringResource(R.string.accounts_reorder),
                leading = { RowIcon(R.drawable.ic_swap_vert) },
                onClick = onReorder,
            )
        }
        FinoteListRow(
            title = stringResource(R.string.category_edit_add_child),
            leading = { RowIcon(R.drawable.ic_add) },
            onClick = onAdd,
        )
    }
}

@Composable
private fun RowIcon(
    @DrawableRes iconRes: Int,
) {
    Icon(
        painter = painterResource(iconRes),
        contentDescription = null,
        modifier = Modifier.size(FinoteTheme.sizes.iconMd),
        tint = FinoteTheme.scheme.onSurfaceVariant,
    )
}

@FinotePreviews
@Composable
private fun SubcategorySectionPreview() {
    FinotePreviewSurface {
        SubcategorySection(
            children = listOf(previewChild("a", "Groceries", false), previewChild("b", "Eating out", true)),
            onOpen = {},
            onAdd = {},
            onReorder = {},
        )
    }
}

private fun previewChild(
    id: String,
    name: String,
    isHidden: Boolean,
) = Category(
    id = id,
    name = name,
    nameKey = null,
    icon = "restaurant",
    color = PreviewRedArgb,
    type = CategoryType.EXPENSE,
    parentId = "p",
    isSystemDefault = false,
    isHidden = isHidden,
    sortOrder = 0,
    createdAt = 0L,
    updatedAt = 0L,
    deletedAt = null,
)
