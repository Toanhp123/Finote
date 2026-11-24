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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import io.github.toanhp123.finote.R
import io.github.toanhp123.finote.domain.transaction.model.TransactionType
import io.github.toanhp123.finote.ui.cashentry.CashEntryIntent
import io.github.toanhp123.finote.ui.cashentry.CashEntryPage
import io.github.toanhp123.finote.ui.cashentry.CashEntryState
import io.github.toanhp123.finote.ui.common.component.finance.CategoryIcon
import io.github.toanhp123.finote.ui.common.format.MoneyKind
import io.github.toanhp123.finote.ui.common.format.displayName
import io.github.toanhp123.finote.ui.common.format.formatMediumDate
import io.github.toanhp123.finote.ui.common.format.formatMoney
import io.github.toanhp123.finote.ui.common.format.iconRes
import io.github.toanhp123.finote.ui.common.theme.FinoteTheme
import java.time.LocalDate

private const val NOTE_MAX_LINES = 2

/**
 * Two rows of two cells: account and category (a transfer: destination), then date and note.
 * A transfer adds a third row with the fee. Each cell opens its page or picker.
 */
@Composable
fun Selectors(
    state: CashEntryState,
    onIntent: (CashEntryIntent) -> Unit,
    onDateClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isTransfer = state.type == TransactionType.TRANSFER
    val gap = Arrangement.spacedBy(FinoteTheme.spacing.sm)
    Column(modifier = modifier, verticalArrangement = gap) {
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
                text =
                    if (state.isDateMissing) {
                        stringResource(R.string.cash_entry_no_date)
                    } else {
                        dateLabel(state.date, state.isToday)
                    },
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
