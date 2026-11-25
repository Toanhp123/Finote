package io.github.toanhp123.finote.ui.settings.categories.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import io.github.toanhp123.finote.R
import io.github.toanhp123.finote.domain.category.model.Category
import io.github.toanhp123.finote.domain.category.model.CategoryType
import io.github.toanhp123.finote.ui.common.component.control.FinoteIconButton
import io.github.toanhp123.finote.ui.common.component.display.ExpandChevron
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
 * A top-level category: icon, name, the number of its children, an eye button and, with children, an arrow. A tap on
 * the row opens the edit screen; the arrow opens or closes the children. A hidden category is dimmed.
 */
@Composable
fun CategoryParentRow(
    category: Category,
    childCount: Int,
    isExpanded: Boolean,
    onIntent: (CategoriesSettingsIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val name = displayName(category.name, category.nameKey)
    FinoteListRow(
        title = if (childCount > 0) "$name · $childCount" else name,
        modifier = modifier.alpha(if (category.isHidden) FinoteTheme.colors.mutedAlpha else 1f),
        leading = { CategoryIcon(iconRes(category.icon), category.color, contentDescription = null) },
        trailing = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(FinoteTheme.spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FinoteIconButton(
                    iconRes = if (category.isHidden) R.drawable.ic_visibility_off else R.drawable.ic_visibility,
                    contentDescription =
                        stringResource(
                            if (category.isHidden) R.string.categories_show_cd else R.string.categories_hide_cd,
                            name,
                        ),
                    onClick = { onIntent(CategoriesSettingsIntent.VisibilityToggled(category.id)) },
                )
                if (childCount > 0) {
                    Box(
                        modifier =
                            Modifier
                                .size(FinoteTheme.sizes.touchTarget)
                                .clip(FinoteTheme.shapes.full)
                                .clickable(role = Role.Button) {
                                    onIntent(CategoriesSettingsIntent.ExpandToggled(category.id))
                                },
                        contentAlignment = Alignment.Center,
                    ) {
                        ExpandChevron(isExpanded)
                    }
                }
            }
        },
        onClick = { onIntent(CategoriesSettingsIntent.CategoryClicked(category.id)) },
    )
}

@FinotePreviews
@Composable
private fun CategoryParentRowPreview() {
    FinotePreviewSurface {
        Column {
            CategoryParentRow(
                previewParent("a", "Food & drink", false),
                childCount = 2,
                isExpanded = true,
                onIntent = {},
            )
            CategoryParentRow(previewParent("b", "Transport", false), childCount = 0, isExpanded = false, onIntent = {})
            CategoryParentRow(previewParent("c", "Home", true), childCount = 3, isExpanded = false, onIntent = {})
        }
    }
}

private fun previewParent(
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
    parentId = null,
    isSystemDefault = false,
    isHidden = isHidden,
    sortOrder = 0,
    createdAt = 0L,
    updatedAt = 0L,
    deletedAt = null,
)
