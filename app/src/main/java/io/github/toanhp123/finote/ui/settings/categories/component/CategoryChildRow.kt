package io.github.toanhp123.finote.ui.settings.categories.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import io.github.toanhp123.finote.R
import io.github.toanhp123.finote.domain.category.model.Category
import io.github.toanhp123.finote.domain.category.model.CategoryType
import io.github.toanhp123.finote.ui.common.component.control.FinoteIconButton
import io.github.toanhp123.finote.ui.common.component.display.FinoteListRow
import io.github.toanhp123.finote.ui.common.component.finance.CategoryIcon
import io.github.toanhp123.finote.ui.common.component.preview.FinotePreviewSurface
import io.github.toanhp123.finote.ui.common.component.preview.FinotePreviews
import io.github.toanhp123.finote.ui.common.component.preview.PreviewRedArgb
import io.github.toanhp123.finote.ui.common.format.displayName
import io.github.toanhp123.finote.ui.common.format.iconRes
import io.github.toanhp123.finote.ui.common.theme.FinoteTheme
import io.github.toanhp123.finote.ui.settings.categories.CategoriesSettingsIntent

/**
 * A subcategory, indented under its parent. It is dimmed when it is hidden or when its parent is ([isParentHidden]);
 * its eye icon follows its own flag only.
 */
@Composable
fun CategoryChildRow(
    category: Category,
    isParentHidden: Boolean,
    onIntent: (CategoriesSettingsIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val name = displayName(category.name, category.nameKey)
    val isDimmed = category.isHidden || isParentHidden
    FinoteListRow(
        title = name,
        modifier =
            modifier
                .padding(
                    start = FinoteTheme.spacing.lg,
                ).alpha(if (isDimmed) FinoteTheme.colors.mutedAlpha else 1f),
        leading = { CategoryIcon(iconRes(category.icon), category.color, contentDescription = null) },
        trailing = {
            FinoteIconButton(
                iconRes = if (category.isHidden) R.drawable.ic_visibility_off else R.drawable.ic_visibility,
                contentDescription =
                    stringResource(
                        if (category.isHidden) R.string.categories_show_cd else R.string.categories_hide_cd,
                        name,
                    ),
                onClick = { onIntent(CategoriesSettingsIntent.VisibilityToggled(category.id)) },
            )
        },
        onClick = { onIntent(CategoriesSettingsIntent.CategoryClicked(category.id)) },
    )
}

@FinotePreviews
@Composable
private fun CategoryChildRowPreview() {
    FinotePreviewSurface {
        Column {
            CategoryChildRow(previewChild("a", "Breakfast", isHidden = false), isParentHidden = false, onIntent = {})
            CategoryChildRow(previewChild("b", "Coffee", isHidden = true), isParentHidden = false, onIntent = {})
            CategoryChildRow(previewChild("c", "Lunch", isHidden = false), isParentHidden = true, onIntent = {})
        }
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
