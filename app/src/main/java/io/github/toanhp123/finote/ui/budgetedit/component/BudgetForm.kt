package io.github.toanhp123.finote.ui.budgetedit.component

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import io.github.toanhp123.finote.R
import io.github.toanhp123.finote.domain.category.model.Category
import io.github.toanhp123.finote.ui.budgetedit.BudgetEditError
import io.github.toanhp123.finote.ui.budgetedit.BudgetEditIntent
import io.github.toanhp123.finote.ui.budgetedit.BudgetEditState
import io.github.toanhp123.finote.ui.common.component.control.FinoteButton
import io.github.toanhp123.finote.ui.common.component.control.FinoteButtonStyle
import io.github.toanhp123.finote.ui.common.component.control.FinoteChip
import io.github.toanhp123.finote.ui.common.component.control.FinoteSwitchRow
import io.github.toanhp123.finote.ui.common.component.control.FinoteTextField
import io.github.toanhp123.finote.ui.common.format.displayName
import io.github.toanhp123.finote.ui.common.format.iconRes
import io.github.toanhp123.finote.ui.common.theme.FinoteTheme

/** The create and edit form of one budget. The category is picked in create mode and shown locked in edit mode. */
@Composable
fun BudgetForm(
    state: BudgetEditState,
    onIntent: (BudgetEditIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    // The limit is the only budget error with a field; the view model keeps it at InvalidLimit.
    val limitError = stringResource(R.string.budget_error_limit).takeIf { state.limitError != null }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(FinoteTheme.spacing.md)) {
        Text(
            text = stringResource(if (state.isEditing) R.string.budget_edit_title else R.string.budget_add),
            color = FinoteTheme.scheme.onSurface,
            style = FinoteTheme.typography.material.titleLarge,
        )
        CategoryChoices(state = state, onPick = { onIntent(BudgetEditIntent.CategoryPicked(it)) })
        FinoteTextField(
            value = state.limitText,
            onValueChange = { onIntent(BudgetEditIntent.LimitChanged(it)) },
            label = stringResource(R.string.budget_edit_limit),
            modifier = Modifier.fillMaxWidth(),
            errorText = limitError,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
        )
        FinoteSwitchRow(
            title = stringResource(R.string.budget_edit_rollover),
            checked = state.isRollover,
            onCheckedChange = { onIntent(BudgetEditIntent.RolloverChanged(it)) },
        )
        ThresholdChips(selected = state.threshold, onPick = { onIntent(BudgetEditIntent.ThresholdPicked(it)) })
        state.formError?.let {
            // Above Save, inside the sheet: the sheet is its own window, so nothing outside it can be seen.
            Text(
                text = stringResource(it.messageRes()),
                color = FinoteTheme.colors.expense.color,
                style = FinoteTheme.typography.material.bodySmall,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        FinoteButton(
            text = stringResource(R.string.budget_edit_save),
            onClick = { onIntent(BudgetEditIntent.SaveClicked) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.isSaving,
            isLoading = state.isSaving,
        )
        if (state.isEditing) {
            FinoteButton(
                text = stringResource(R.string.action_delete),
                onClick = { onIntent(BudgetEditIntent.DeleteClicked) },
                modifier = Modifier.fillMaxWidth(),
                style = FinoteButtonStyle.Secondary,
                enabled = !state.isSaving,
            )
        }
    }
}

// A null category is the total budget.
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryChoices(
    state: BudgetEditState,
    onPick: (String?) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(FinoteTheme.spacing.xs)) {
        Text(
            text = stringResource(R.string.cash_entry_category),
            color = FinoteTheme.scheme.onSurfaceVariant,
            style = FinoteTheme.typography.material.labelMedium,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(FinoteTheme.spacing.sm)) {
            state.choices.forEach { category ->
                FinoteChip(
                    text = nameOf(category),
                    selected = category?.id == state.categoryId,
                    // A locked category is only shown, so its chip does nothing.
                    onClick = { if (!state.isCategoryLocked) onPick(category?.id) },
                    iconRes = category?.let { iconRes(it.icon) },
                )
            }
        }
    }
}

@Composable
private fun nameOf(category: Category?): String =
    if (category == null) {
        stringResource(R.string.budget_total_title)
    } else {
        displayName(category.name, category.nameKey)
    }

@StringRes
private fun BudgetEditError.messageRes(): Int =
    when (this) {
        BudgetEditError.DUPLICATE -> R.string.budget_error_duplicate
        BudgetEditError.INVALID_CATEGORY -> R.string.budget_error_invalid_category
        BudgetEditError.FAILED -> R.string.budget_error_failed
    }
