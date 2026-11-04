package io.github.toanhp123.finote.ui.cashentry

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import io.github.toanhp123.finote.R
import io.github.toanhp123.finote.domain.account.model.Account
import io.github.toanhp123.finote.domain.category.model.Category
import io.github.toanhp123.finote.ui.common.component.CategoryIcon
import io.github.toanhp123.finote.ui.common.component.FinoteButton
import io.github.toanhp123.finote.ui.common.component.FinoteListRow
import io.github.toanhp123.finote.ui.common.component.FinoteTextField
import io.github.toanhp123.finote.ui.common.format.displayName
import io.github.toanhp123.finote.ui.common.format.iconRes
import io.github.toanhp123.finote.ui.common.theme.FinoteTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private const val CHILD_DEPTH = 1
private const val NOTE_MAX_LINES = 2

@Composable
internal fun AccountList(
    accounts: List<Account>,
    selectedId: String?,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        accounts.forEach { account ->
            PickerRow(
                title = displayName(account.name, account.nameKey),
                isSelected = account.id == selectedId,
                onClick = { onPick(account.id) },
                icon = iconRes(account.icon),
                colorArgb = account.color,
            )
        }
    }
}

/** A flat "no category" row, then each top-level category with its children indented below it. */
@Composable
internal fun CategoryList(
    categories: List<Category>,
    selectedId: String?,
    onPick: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rows = remember(categories) { categories.asTree() }
    Column(modifier = modifier) {
        PickerRow(
            title = stringResource(R.string.cash_entry_no_category),
            isSelected = selectedId == null,
            onClick = { onPick(null) },
        )
        rows.forEach { (category, depth) ->
            PickerRow(
                title = displayName(category.name, category.nameKey),
                isSelected = category.id == selectedId,
                onClick = { onPick(category.id) },
                icon = iconRes(category.icon),
                colorArgb = category.color,
                modifier = Modifier.padding(start = FinoteTheme.spacing.xl * depth),
            )
        }
    }
}

/** Roots first, each followed by its children. A category whose parent is missing counts as a root. */
private fun List<Category>.asTree(): List<Pair<Category, Int>> {
    val ids = mapTo(HashSet()) { it.id }
    val children = filter { it.parentId in ids }.groupBy { it.parentId }
    return filter { it.parentId == null || it.parentId !in ids }
        .flatMap { root -> listOf(root to 0) + children[root.id].orEmpty().map { it to CHILD_DEPTH } }
}

@Composable
private fun PickerRow(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: Int? = null,
    colorArgb: Int = 0,
) {
    FinoteListRow(
        title = title,
        modifier = modifier.semantics { selected = isSelected },
        leading = icon?.let { { CategoryIcon(iconRes = it, colorArgb = colorArgb, contentDescription = null) } },
        // The check is a glyph, so the selection does not rely on color alone.
        trailing =
            if (isSelected) {
                {
                    Text(
                        text = "✓",
                        color = FinoteTheme.scheme.primary,
                        style = FinoteTheme.typography.material.titleMedium,
                    )
                }
            } else {
                null
            },
        onClick = onClick,
    )
}

/** The system keyboard is up here, so the calculator keypad is not shown. */
@Composable
internal fun NotePage(
    note: String,
    onNoteChange: (String) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(focusRequester) { focusRequester.requestFocus() }
    Column(
        modifier = modifier.imePadding(),
        verticalArrangement = Arrangement.spacedBy(FinoteTheme.spacing.md),
    ) {
        FinoteTextField(
            value = note,
            onValueChange = onNoteChange,
            label = stringResource(R.string.cash_entry_note),
            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
            maxLength = CashEntryViewModel.MAX_NOTE_LENGTH,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onDone() }),
        )
        FinoteButton(
            text = stringResource(R.string.cash_entry_done),
            onClick = onDone,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Two rows of two cells: account and category, then date and note. Each opens its page or picker. */
@Composable
internal fun Selectors(
    state: CashEntryState,
    onIntent: (CashEntryIntent) -> Unit,
    onDateClick: () -> Unit,
) {
    val account = state.accounts.firstOrNull { it.id == state.selectedAccountId }
    val categoryId = state.selectedCategoryIds[state.type]
    val category = state.categories.firstOrNull { it.id == categoryId }
    val accountText =
        account?.let { displayName(it.name, it.nameKey) } ?: stringResource(R.string.cash_entry_no_account)
    val categoryText =
        category?.let { displayName(it.name, it.nameKey) } ?: stringResource(R.string.cash_entry_no_category)
    val gap = Arrangement.spacedBy(FinoteTheme.spacing.sm)
    Column(verticalArrangement = gap) {
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = gap) {
            SelectorRow(
                text = accountText,
                onClick = { onIntent(CashEntryIntent.PageOpened(CashEntryPage.ACCOUNT)) },
                modifier = Modifier.weight(1f).fillMaxHeight(),
                icon = account?.let { iconRes(it.icon) },
                colorArgb = account?.color ?: 0,
            )
            SelectorRow(
                text = categoryText,
                onClick = { onIntent(CashEntryIntent.PageOpened(CashEntryPage.CATEGORY)) },
                modifier = Modifier.weight(1f).fillMaxHeight(),
                icon = category?.let { iconRes(it.icon) },
                colorArgb = category?.color ?: 0,
            )
        }
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = gap) {
            SelectorRow(
                text = dateLabel(state.date, state.isToday),
                onClick = onDateClick,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            SelectorRow(
                text = state.note.ifBlank { stringResource(R.string.cash_entry_note) },
                onClick = { onIntent(CashEntryIntent.PageOpened(CashEntryPage.NOTE)) },
                modifier = Modifier.weight(1f).fillMaxHeight(),
                maxLines = NOTE_MAX_LINES,
            )
        }
    }
}

@Composable
private fun dateLabel(
    date: LocalDate,
    isToday: Boolean,
): String {
    val locale = LocalConfiguration.current.locales[0]
    return if (isToday) {
        stringResource(R.string.cash_entry_today)
    } else {
        date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))
    }
}

@Composable
private fun SelectorRow(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: Int? = null,
    colorArgb: Int = 0,
    maxLines: Int = Int.MAX_VALUE,
) {
    Row(
        modifier =
            modifier
                .heightIn(min = FinoteTheme.sizes.touchTarget)
                .clip(FinoteTheme.shapes.md)
                .background(FinoteTheme.colors.card)
                .border(FinoteTheme.sizes.border, FinoteTheme.colors.glass.stroke, FinoteTheme.shapes.md)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = FinoteTheme.spacing.md, vertical = FinoteTheme.spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(FinoteTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) CategoryIcon(iconRes = icon, colorArgb = colorArgb, contentDescription = null)
        Text(
            text = text,
            modifier = Modifier.weight(1f),
            color = FinoteTheme.scheme.onSurface,
            style = FinoteTheme.typography.material.bodyMedium,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
