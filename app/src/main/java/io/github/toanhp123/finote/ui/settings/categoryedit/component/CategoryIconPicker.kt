package io.github.toanhp123.finote.ui.settings.categoryedit.component

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import io.github.toanhp123.finote.R
import io.github.toanhp123.finote.ui.common.component.preview.FinotePreviewSurface
import io.github.toanhp123.finote.ui.common.component.preview.FinotePreviews
import io.github.toanhp123.finote.ui.common.format.CategoryIconGroup
import io.github.toanhp123.finote.ui.common.format.CategoryIconGroups
import io.github.toanhp123.finote.ui.common.format.iconRes
import io.github.toanhp123.finote.ui.common.theme.FinoteTheme

/**
 * Every icon a category may take, in the sections of [CategoryIconGroups]. A [selected] name outside the lists shows
 * no tile as picked; picking replaces it.
 */
@Composable
fun CategoryIconPicker(
    selected: String,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(FinoteTheme.spacing.sm)) {
        CategoryIconGroups.forEach { (group, names) ->
            Text(
                text = stringResource(group.labelRes()),
                modifier = Modifier.semantics { heading() },
                color = FinoteTheme.scheme.onSurfaceVariant,
                style = FinoteTheme.typography.material.labelSmall,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(FinoteTheme.spacing.xs),
                verticalArrangement = Arrangement.spacedBy(FinoteTheme.spacing.xs),
            ) {
                names.forEachIndexed { index, name ->
                    IconTile(name = name, number = index + 1, isSelected = name == selected, onPick = onPick)
                }
            }
        }
    }
}

@Composable
private fun IconTile(
    name: String,
    number: Int,
    isSelected: Boolean,
    onPick: (String) -> Unit,
) {
    val description = stringResource(R.string.account_edit_icon_cd, number)
    val scheme = FinoteTheme.scheme
    Box(
        modifier =
            Modifier
                .size(FinoteTheme.sizes.touchTarget)
                .clip(FinoteTheme.shapes.sm)
                .background(if (isSelected) scheme.primaryContainer else FinoteTheme.colors.card)
                .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onPick(name) })
                .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconRes(name)),
            contentDescription = null,
            modifier = Modifier.size(FinoteTheme.sizes.iconMd),
            tint = if (isSelected) scheme.onPrimaryContainer else scheme.onSurface,
        )
    }
}

@StringRes
private fun CategoryIconGroup.labelRes(): Int =
    when (this) {
        CategoryIconGroup.FOOD -> R.string.category_icon_group_food
        CategoryIconGroup.TRANSPORT -> R.string.category_icon_group_transport
        CategoryIconGroup.HOME -> R.string.category_icon_group_home
        CategoryIconGroup.SHOPPING -> R.string.category_icon_group_shopping
        CategoryIconGroup.HEALTH -> R.string.category_icon_group_health
        CategoryIconGroup.FUN -> R.string.category_icon_group_fun
        CategoryIconGroup.STUDY -> R.string.category_icon_group_study
        CategoryIconGroup.FAMILY -> R.string.category_icon_group_family
        CategoryIconGroup.MONEY -> R.string.category_icon_group_money
        CategoryIconGroup.OTHER -> R.string.category_icon_group_other
    }

@FinotePreviews
@Composable
private fun CategoryIconPickerPreview() {
    FinotePreviewSurface { CategoryIconPicker(selected = "restaurant", onPick = {}) }
}
