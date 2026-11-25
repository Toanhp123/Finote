package io.github.toanhp123.finote.ui.settings.categoryedit.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import io.github.toanhp123.finote.R
import io.github.toanhp123.finote.domain.category.model.Category
import io.github.toanhp123.finote.domain.category.model.CategoryType
import io.github.toanhp123.finote.domain.category.model.DeleteImpact
import io.github.toanhp123.finote.ui.common.component.control.FinoteButton
import io.github.toanhp123.finote.ui.common.component.control.FinoteButtonStyle
import io.github.toanhp123.finote.ui.common.component.display.FinoteListRow
import io.github.toanhp123.finote.ui.common.component.finance.CategoryIcon
import io.github.toanhp123.finote.ui.common.component.overlay.FinoteBottomSheet
import io.github.toanhp123.finote.ui.common.component.preview.FinotePreviewSurface
import io.github.toanhp123.finote.ui.common.component.preview.FinotePreviews
import io.github.toanhp123.finote.ui.common.component.preview.PreviewGreenArgb
import io.github.toanhp123.finote.ui.common.format.displayName
import io.github.toanhp123.finote.ui.common.format.iconRes
import io.github.toanhp123.finote.ui.common.rememberLastNonNull
import io.github.toanhp123.finote.ui.common.theme.FinoteTheme
import io.github.toanhp123.finote.ui.settings.categoryedit.logic.DeleteChoice
import io.github.toanhp123.finote.ui.settings.categoryedit.logic.DeleteSheet

/**
 * Where the transactions and keyword rules of the category [name] go before it is deleted. A null [sheet] hides it; it
 * keeps its rows while it leaves. [hasChildren] adds the note that the subcategories go too. The delete button waits
 * for a pick.
 */
@Composable
fun DeleteMoveSheet(
    sheet: DeleteSheet?,
    name: String,
    hasChildren: Boolean,
    onPick: (DeleteChoice) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shown = rememberLastNonNull(sheet)
    FinoteBottomSheet(isVisible = sheet != null, onDismiss = onDismiss, modifier = modifier) {
        if (shown == null) return@FinoteBottomSheet
        Text(
            text = stringResource(R.string.category_delete_title, name),
            modifier = Modifier.padding(bottom = FinoteTheme.spacing.sm).semantics { heading() },
            color = FinoteTheme.scheme.onSurface,
            style = FinoteTheme.typography.material.titleLarge,
        )
        val impact = shown.impact
        if (impact.transactions > 0) {
            Text(
                text =
                    pluralStringResource(R.plurals.category_delete_move_text, impact.transactions, impact.transactions),
                color = FinoteTheme.scheme.onSurfaceVariant,
                style = FinoteTheme.typography.material.bodyMedium,
            )
        }
        if (impact.rules > 0) {
            Text(
                text = pluralStringResource(R.plurals.category_delete_rules_text, impact.rules, impact.rules),
                color = FinoteTheme.scheme.onSurfaceVariant,
                style = FinoteTheme.typography.material.bodyMedium,
            )
        }
        if (hasChildren) {
            Text(
                text = stringResource(R.string.category_delete_branch_note),
                color = FinoteTheme.scheme.onSurfaceVariant,
                style = FinoteTheme.typography.material.bodyMedium,
            )
        }
        TargetRow(
            title = stringResource(R.string.category_delete_none_option),
            isSelected = shown.choice == DeleteChoice.Uncategorized,
            onClick = { onPick(DeleteChoice.Uncategorized) },
        )
        shown.targets.forEach { target ->
            val parent = shown.targets.firstOrNull { it.id == target.parentId }
            TargetRow(
                title = displayName(target.name, target.nameKey),
                isSelected = shown.choice == DeleteChoice.Into(target.id),
                onClick = { onPick(DeleteChoice.Into(target.id)) },
                // A child alone is ambiguous ("Gas" under which parent?), so it names its parent.
                subtitle = parent?.let { displayName(it.name, it.nameKey) },
                leading = { CategoryIcon(iconRes(target.icon), target.color, contentDescription = null) },
            )
        }
        FinoteButton(
            text = stringResource(R.string.action_delete),
            onClick = onConfirm,
            modifier = Modifier.fillMaxWidth().padding(top = FinoteTheme.spacing.lg),
            style = FinoteButtonStyle.Danger,
            enabled = shown.choice != DeleteChoice.Unset,
        )
    }
}

@Composable
private fun TargetRow(
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
private fun DeleteMoveSheetPreview() {
    FinotePreviewSurface {
        DeleteMoveSheet(
            sheet =
                DeleteSheet(
                    impact = DeleteImpact(transactions = 12, rules = 3),
                    targets = listOf(previewTarget("a", "Food"), previewTarget("b", "Transport")),
                    choice = DeleteChoice.Into("a"),
                ),
            name = "Shopping",
            hasChildren = true,
            onPick = {},
            onConfirm = {},
            onDismiss = {},
        )
    }
}

private fun previewTarget(
    id: String,
    name: String,
) = Category(
    id = id,
    name = name,
    nameKey = null,
    icon = "restaurant",
    color = PreviewGreenArgb,
    type = CategoryType.EXPENSE,
    parentId = null,
    isSystemDefault = false,
    isHidden = false,
    sortOrder = 0,
    createdAt = 0L,
    updatedAt = 0L,
    deletedAt = null,
)
