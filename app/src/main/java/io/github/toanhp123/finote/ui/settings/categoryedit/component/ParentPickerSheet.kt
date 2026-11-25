package io.github.toanhp123.finote.ui.settings.categoryedit.component

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import io.github.toanhp123.finote.R
import io.github.toanhp123.finote.domain.category.model.Category
import io.github.toanhp123.finote.domain.category.model.CategoryType
import io.github.toanhp123.finote.ui.common.component.display.FinoteListRow
import io.github.toanhp123.finote.ui.common.component.finance.CategoryIcon
import io.github.toanhp123.finote.ui.common.component.overlay.FinoteBottomSheet
import io.github.toanhp123.finote.ui.common.component.preview.FinotePreviewSurface
import io.github.toanhp123.finote.ui.common.component.preview.FinotePreviews
import io.github.toanhp123.finote.ui.common.component.preview.PreviewGreenArgb
import io.github.toanhp123.finote.ui.common.format.displayName
import io.github.toanhp123.finote.ui.common.format.iconRes
import io.github.toanhp123.finote.ui.common.theme.FinoteTheme

/**
 * The parents a category may take, plus "None" for the top level. [selectedId] is the draft's parent; the row of it
 * shows a check. A pick calls [onPick] with the parent id or null.
 */
@Composable
fun ParentPickerSheet(
    isVisible: Boolean,
    parents: List<Category>,
    selectedId: String?,
    onPick: (String?) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FinoteBottomSheet(isVisible = isVisible, onDismiss = onDismiss, modifier = modifier) {
        Text(
            text = stringResource(R.string.category_edit_parent),
            modifier = Modifier.padding(bottom = FinoteTheme.spacing.sm).semantics { heading() },
            color = FinoteTheme.scheme.onSurface,
            style = FinoteTheme.typography.material.titleLarge,
        )
        ParentRow(
            title = stringResource(R.string.category_edit_parent_none),
            isSelected = selectedId == null,
            onClick = { onPick(null) },
        )
        parents.forEach { parent ->
            ParentRow(
                title = displayName(parent.name, parent.nameKey),
                isSelected = parent.id == selectedId,
                onClick = { onPick(parent.id) },
                subtitle = if (parent.isHidden) stringResource(R.string.category_hidden_label) else null,
                leading = { CategoryIcon(iconRes(parent.icon), parent.color, contentDescription = null) },
            )
        }
    }
}

@Composable
private fun ParentRow(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
) {
    FinoteListRow(
        title = title,
        modifier = Modifier.semantics { selected = isSelected },
        subtitle = subtitle,
        leading = leading,
        trailing =
            if (isSelected) {
                {
                    Icon(
                        painter = painterResource(R.drawable.ic_check),
                        contentDescription = null,
                        modifier = Modifier.size(FinoteTheme.sizes.iconMd),
                        tint = FinoteTheme.scheme.primary,
                    )
                }
            } else {
                null
            },
        onClick = onClick,
    )
}

@FinotePreviews
@Composable
private fun ParentPickerSheetPreview() {
    FinotePreviewSurface {
        ParentPickerSheet(
            isVisible = true,
            parents = listOf(previewParent("a", "Food", false), previewParent("b", "Transport", true)),
            selectedId = "a",
            onPick = {},
            onDismiss = {},
        )
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
    color = PreviewGreenArgb,
    type = CategoryType.EXPENSE,
    parentId = null,
    isSystemDefault = false,
    isHidden = isHidden,
    sortOrder = 0,
    createdAt = 0L,
    updatedAt = 0L,
    deletedAt = null,
)
