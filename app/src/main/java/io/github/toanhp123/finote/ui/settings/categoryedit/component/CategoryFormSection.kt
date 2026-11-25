package io.github.toanhp123.finote.ui.settings.categoryedit.component

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import io.github.toanhp123.finote.R
import io.github.toanhp123.finote.domain.category.model.Category
import io.github.toanhp123.finote.ui.common.component.control.FinoteTextField
import io.github.toanhp123.finote.ui.common.component.display.FinoteCard
import io.github.toanhp123.finote.ui.common.component.display.FinoteListRow
import io.github.toanhp123.finote.ui.common.component.preview.FinotePreviewSurface
import io.github.toanhp123.finote.ui.common.component.preview.FinotePreviews
import io.github.toanhp123.finote.ui.common.component.preview.PreviewRedArgb
import io.github.toanhp123.finote.ui.common.format.displayName
import io.github.toanhp123.finote.ui.common.theme.FinoteTheme
import io.github.toanhp123.finote.ui.common.theme.color.CategoryPalette
import io.github.toanhp123.finote.ui.settings.categoryedit.logic.CategoryDraft

private const val NAME_MAX_LENGTH = 40

/**
 * The fields of a category that the user edits: name, parent, icon, color. [stored] is null in create mode. A system
 * category shows its localized name until the user types; an untouched field sends the stored name back, so the
 * translation stays. [parents] are the choices of the parent field; with [isParentLocked] the field says why it
 * cannot change.
 */
@Composable
fun CategoryFormSection(
    draft: CategoryDraft,
    stored: Category?,
    parents: List<Category>,
    isParentLocked: Boolean,
    onDraftChange: (CategoryDraft) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(FinoteTheme.spacing.lg)) {
        val seeded = stored?.takeIf { it.nameKey != null && draft.name == it.name }
        FinoteTextField(
            value = seeded?.let { displayName(it.name, it.nameKey) } ?: draft.name,
            onValueChange = { onDraftChange(draft.copy(name = it)) },
            label = stringResource(R.string.category_edit_name),
            modifier = Modifier.fillMaxWidth(),
            maxLength = NAME_MAX_LENGTH,
        )
        ParentField(draft, stored, parents, isParentLocked, onDraftChange)
        Labeled(R.string.account_edit_icon) {
            CategoryIconPicker(draft.icon, onPick = { onDraftChange(draft.copy(icon = it)) })
        }
        Labeled(R.string.account_edit_color) { ColorPicker(draft.color) { onDraftChange(draft.copy(color = it)) } }
    }
}

@Composable
private fun ParentField(
    draft: CategoryDraft,
    stored: Category?,
    parents: List<Category>,
    isParentLocked: Boolean,
    onDraftChange: (CategoryDraft) -> Unit,
) {
    var isPickerOpen by rememberSaveable { mutableStateOf(false) }
    val parent = parents.firstOrNull { it.id == draft.parentId }
    val lockReason =
        when {
            !isParentLocked -> null
            stored?.isSystemDefault == true -> stringResource(R.string.category_parent_locked_system)
            else -> stringResource(R.string.category_parent_locked_children)
        }
    val parentTitle =
        parent?.let { displayName(it.name, it.nameKey) } ?: stringResource(R.string.category_edit_parent_none)
    Labeled(R.string.category_edit_parent) {
        FinoteCard(modifier = Modifier.fillMaxWidth()) {
            FinoteListRow(
                title = parentTitle,
                subtitle = lockReason,
                trailing = if (isParentLocked) null else ({ ChevronIcon() }),
                onClick = if (isParentLocked) null else ({ isPickerOpen = true }),
            )
        }
    }
    ParentPickerSheet(
        isVisible = isPickerOpen,
        parents = parents,
        selectedId = draft.parentId,
        onPick = {
            isPickerOpen = false
            onDraftChange(draft.copy(parentId = it))
        },
        onDismiss = { isPickerOpen = false },
    )
}

@Composable
private fun ChevronIcon() {
    Icon(
        painter = painterResource(R.drawable.ic_chevron_right),
        contentDescription = null,
        modifier = Modifier.size(FinoteTheme.sizes.iconMd),
        tint = FinoteTheme.scheme.onSurfaceVariant,
    )
}

@Composable
private fun Labeled(
    @StringRes labelRes: Int,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(FinoteTheme.spacing.xs)) {
        Text(
            text = stringResource(labelRes),
            color = FinoteTheme.scheme.onSurfaceVariant,
            style = FinoteTheme.typography.material.labelMedium,
        )
        content()
    }
}

@Composable
private fun ColorPicker(
    selected: Int,
    onPick: (Int) -> Unit,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(FinoteTheme.spacing.xs)) {
        CategoryPalette.forEachIndexed { index, color ->
            val argb = color.toArgb()
            val isSelected = argb == selected
            val description = stringResource(R.string.account_edit_color_cd, index + 1)
            Box(
                modifier =
                    Modifier
                        .size(FinoteTheme.sizes.touchTarget)
                        .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onPick(argb) })
                        .semantics { contentDescription = description },
                contentAlignment = Alignment.Center,
            ) {
                Swatch(color = color, isSelected = isSelected)
            }
        }
    }
}

/** The check and the ring tell the picked color apart without relying on color alone. */
@Composable
private fun Swatch(
    color: Color,
    isSelected: Boolean,
) {
    val ring = if (isSelected) FinoteTheme.scheme.onSurface else color
    Box(
        modifier =
            Modifier
                .size(FinoteTheme.sizes.avatar)
                .clip(FinoteTheme.shapes.full)
                .background(color)
                .border(FinoteTheme.sizes.border * 2, ring, FinoteTheme.shapes.full)
                .padding(FinoteTheme.spacing.xs),
        contentAlignment = Alignment.Center,
    ) {
        if (isSelected) {
            Icon(
                painter = painterResource(R.drawable.ic_check),
                contentDescription = null,
                modifier = Modifier.size(FinoteTheme.sizes.iconSm),
                tint = FinoteTheme.colors.hero.onHero,
            )
        }
    }
}

@FinotePreviews
@Composable
private fun CategoryFormSectionPreview() {
    FinotePreviewSurface {
        CategoryFormSection(
            draft = CategoryDraft(name = "Food", parentId = null, icon = "restaurant", color = PreviewRedArgb),
            stored = null,
            parents = emptyList(),
            isParentLocked = false,
            onDraftChange = {},
        )
    }
}
