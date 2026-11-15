package io.github.toanhp123.finote.ui.cashentry.component

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import io.github.toanhp123.finote.R
import io.github.toanhp123.finote.domain.account.model.Account
import io.github.toanhp123.finote.domain.category.model.Category
import io.github.toanhp123.finote.domain.transaction.model.TransactionType
import io.github.toanhp123.finote.ui.cashentry.CashEntryIntent
import io.github.toanhp123.finote.ui.cashentry.CashEntryPage
import io.github.toanhp123.finote.ui.cashentry.CashEntryState
import io.github.toanhp123.finote.ui.cashentry.CashEntryViewModel
import io.github.toanhp123.finote.ui.common.component.control.FinoteButton
import io.github.toanhp123.finote.ui.common.component.control.FinoteTextField
import io.github.toanhp123.finote.ui.common.component.display.FinoteListRow
import io.github.toanhp123.finote.ui.common.component.finance.CategoryIcon
import io.github.toanhp123.finote.ui.common.format.MoneyKind
import io.github.toanhp123.finote.ui.common.format.displayName
import io.github.toanhp123.finote.ui.common.format.formatMediumDate
import io.github.toanhp123.finote.ui.common.format.formatMoney
import io.github.toanhp123.finote.ui.common.format.iconRes
import io.github.toanhp123.finote.ui.common.theme.FinoteTheme
import java.time.LocalDate

private const val CHILD_DEPTH = 1
private const val NOTE_MAX_LINES = 2
private const val NO_CATEGORY_KEY = "no_category"

@Composable
internal fun AccountList(
    accounts: List<Account>,
    selectedId: String?,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier) {
        items(accounts, key = { it.id }) { account ->
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
    LazyColumn(modifier = modifier) {
        item(key = NO_CATEGORY_KEY) {
            PickerRow(
                title = stringResource(R.string.cash_entry_no_category),
                isSelected = selectedId == null,
                onClick = { onPick(null) },
            )
        }
        items(rows, key = { (category, _) -> category.id }) { (category, depth) ->
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
        // The check is an icon, so the selection does not rely on color alone.
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

/** The system keyboard is up here, so the calculator keypad is not shown. */
@Composable
internal fun NotePage(
    note: String,
    onNoteChange: (String) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TextPage(
        value = note,
        onValueChange = onNoteChange,
        label = stringResource(R.string.cash_entry_note),
        onDone = onDone,
        modifier = modifier,
        maxLength = CashEntryViewModel.MAX_NOTE_LENGTH,
    )
}

/** One digits-only field on the system number keyboard. The ViewModel keeps the digits and the cap. */
@Composable
internal fun FeePage(
    fee: Long,
    onFeeChange: (String) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TextPage(
        value = if (fee > 0) fee.toString() else "",
        onValueChange = onFeeChange,
        label = stringResource(R.string.cash_entry_fee),
        onDone = onDone,
        modifier = modifier,
        keyboardType = KeyboardType.Number,
    )
}

@Composable
private fun TextPage(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    maxLength: Int? = null,
    keyboardType: KeyboardType = KeyboardType.Unspecified,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(focusRequester) { focusRequester.requestFocus() }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(FinoteTheme.spacing.md)) {
        FinoteTextField(
            value = value,
            onValueChange = onValueChange,
            label = label,
            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
            maxLength = maxLength,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onDone() }),
        )
        FinoteButton(
            text = stringResource(R.string.cash_entry_done),
            onClick = onDone,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * Two rows of two cells: account and category (a transfer: destination), then date and note.
 * A transfer adds a third row with the fee. Each cell opens its page or picker.
 */
@Composable
internal fun Selectors(
    state: CashEntryState,
    onIntent: (CashEntryIntent) -> Unit,
    onDateClick: () -> Unit,
) {
    val isTransfer = state.type == TransactionType.TRANSFER
    val gap = Arrangement.spacedBy(FinoteTheme.spacing.sm)
    Column(verticalArrangement = gap) {
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = gap) {
            AccountSelector(state, onIntent, Modifier.weight(1f).fillMaxHeight())
            if (isTransfer) {
                DestinationSelector(state, onIntent, Modifier.weight(1f).fillMaxHeight())
            } else {
                CategorySelector(state, onIntent, Modifier.weight(1f).fillMaxHeight())
            }
        }
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = gap) {
            SelectorRow(
                label = stringResource(R.string.cash_entry_date),
                text = dateLabel(state.date, state.isToday),
                onClick = onDateClick,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            SelectorRow(
                label = stringResource(R.string.cash_entry_note),
                text = state.note.ifBlank { stringResource(R.string.cash_entry_note) },
                onClick = { onIntent(CashEntryIntent.PageOpened(CashEntryPage.NOTE)) },
                modifier = Modifier.weight(1f).fillMaxHeight(),
                maxLines = NOTE_MAX_LINES,
            )
        }
        if (isTransfer) FeeSelector(state.fee, onClick = { onIntent(CashEntryIntent.PageOpened(CashEntryPage.FEE)) })
    }
}

@Composable
private fun AccountSelector(
    state: CashEntryState,
    onIntent: (CashEntryIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val account = state.accounts.firstOrNull { it.id == state.selectedAccountId }
    SelectorRow(
        label = stringResource(R.string.cash_entry_account),
        text = account?.let { displayName(it.name, it.nameKey) } ?: stringResource(R.string.cash_entry_no_account),
        onClick = { onIntent(CashEntryIntent.PageOpened(CashEntryPage.ACCOUNT)) },
        modifier = modifier,
        icon = account?.let { iconRes(it.icon) },
        colorArgb = account?.color ?: 0,
    )
}

@Composable
private fun CategorySelector(
    state: CashEntryState,
    onIntent: (CashEntryIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val categoryId = state.selectedCategoryIds[state.type]
    val category = state.categories.firstOrNull { it.id == categoryId }
    SelectorRow(
        label = stringResource(R.string.cash_entry_category),
        text = category?.let { displayName(it.name, it.nameKey) } ?: stringResource(R.string.cash_entry_no_category),
        onClick = { onIntent(CashEntryIntent.PageOpened(CashEntryPage.CATEGORY)) },
        modifier = modifier,
        icon = category?.let { iconRes(it.icon) },
        colorArgb = category?.color ?: 0,
    )
}

@Composable
private fun DestinationSelector(
    state: CashEntryState,
    onIntent: (CashEntryIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val destination = state.destinationChoices.firstOrNull { it.id == state.destinationId }
    val label = stringResource(R.string.cash_entry_to_account)
    val name = destination?.let { displayName(it.name, it.nameKey) }
    val empty = stringResource(R.string.cash_entry_choose_account)
    SelectorRow(
        label = label,
        // "To Bank" shows the direction; the reader hears "To account, Bank".
        text = name?.let { stringResource(R.string.cash_entry_to_value, it) } ?: empty,
        onClick = { onIntent(CashEntryIntent.PageOpened(CashEntryPage.DESTINATION)) },
        modifier = modifier,
        icon = destination?.let { iconRes(it.icon) },
        colorArgb = destination?.color ?: 0,
        description = "$label, ${name ?: empty}",
    )
}

/** Shows "Fee ₫1,100" once the fee is above 0: a bare amount would not say what it is. */
@Composable
private fun FeeSelector(
    fee: Long,
    onClick: () -> Unit,
) {
    val label = stringResource(R.string.cash_entry_fee)
    val money = formatMoney(fee, MoneyKind.Neutral, LocalConfiguration.current.locales[0])
    SelectorRow(
        label = label,
        text = if (fee > 0) stringResource(R.string.cash_entry_fee_value, money) else label,
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        description = if (fee > 0) "$label, $money" else label,
    )
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
        formatMediumDate(date, locale)
    }
}

@Composable
private fun SelectorRow(
    label: String,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: Int? = null,
    colorArgb: Int = 0,
    maxLines: Int = Int.MAX_VALUE,
    // One node that reads "label, value"; an empty note shows the label as its text, so read it once.
    // A cell whose visible [text] already holds words around the value passes its own.
    description: String = if (text == label) label else "$label, $text",
) {
    Row(
        modifier =
            modifier
                .heightIn(min = FinoteTheme.sizes.touchTarget)
                .clip(FinoteTheme.shapes.md)
                .background(FinoteTheme.colors.card)
                .border(FinoteTheme.sizes.border, FinoteTheme.colors.glass.stroke, FinoteTheme.shapes.md)
                .clickable(role = Role.Button, onClick = onClick)
                .clearAndSetSemantics { contentDescription = description }
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
