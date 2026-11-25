package io.github.toanhp123.finote.ui.settings.keywordruleedit.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.toanhp123.finote.R
import io.github.toanhp123.finote.domain.category.model.Category
import io.github.toanhp123.finote.domain.category.model.CategoryType
import io.github.toanhp123.finote.ui.common.component.control.FinoteSegmented
import io.github.toanhp123.finote.ui.common.component.finance.CategoryList
import io.github.toanhp123.finote.ui.common.theme.FinoteTheme

/**
 * The category the rule sets: an Expense or Income tab over the [categories] of that type. The tab starts on the type
 * of the selected category. A rule needs a category, so the list has no "none" row.
 */
@Composable
fun RuleCategoryPicker(
    categories: List<Category>,
    selectedId: String?,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var type by rememberSaveable {
        mutableStateOf(categories.firstOrNull { it.id == selectedId }?.type ?: CategoryType.EXPENSE)
    }
    val tabLabels =
        mapOf(
            CategoryType.EXPENSE to stringResource(R.string.categories_tab_expense),
            CategoryType.INCOME to stringResource(R.string.categories_tab_income),
        )
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(FinoteTheme.spacing.sm)) {
        Text(
            text = stringResource(R.string.keyword_rule_edit_category),
            color = FinoteTheme.scheme.onSurfaceVariant,
            style = FinoteTheme.typography.material.labelMedium,
        )
        FinoteSegmented(
            options = CategoryType.entries,
            selected = type,
            onSelect = { type = it },
            label = { tabLabels.getValue(it) },
            modifier = Modifier.fillMaxWidth(),
        )
        CategoryList(
            categories = categories.filter { it.type == type },
            selectedId = selectedId,
            onPick = { id -> id?.let(onPick) },
            modifier = Modifier.weight(1f),
            isNoneShown = false,
        )
    }
}
