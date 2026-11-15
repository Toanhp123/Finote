package io.github.toanhp123.finote.ui.cashentry.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.toanhp123.finote.R
import io.github.toanhp123.finote.domain.transaction.model.TransactionType
import io.github.toanhp123.finote.ui.common.component.control.FinoteIconButton
import io.github.toanhp123.finote.ui.common.component.control.FinoteSegmented
import io.github.toanhp123.finote.ui.common.theme.FinoteTheme

private val TypeOptions = listOf(TransactionType.EXPENSE, TransactionType.INCOME, TransactionType.TRANSFER)

/** The type selector. Editing a stored transaction adds a trash button at the end, off while a save runs. */
@Composable
internal fun TypeRow(
    type: TransactionType,
    onTypeChange: (TransactionType) -> Unit,
    isEditing: Boolean,
    isSaving: Boolean,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val expense = stringResource(R.string.cash_entry_expense)
    val income = stringResource(R.string.cash_entry_income)
    val transfer = stringResource(R.string.cash_entry_transfer)
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(FinoteTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FinoteSegmented(
            options = TypeOptions,
            selected = type,
            onSelect = onTypeChange,
            label = {
                when (it) {
                    TransactionType.INCOME -> income
                    TransactionType.TRANSFER -> transfer
                    else -> expense
                }
            },
            modifier = Modifier.weight(1f),
        )
        if (isEditing) {
            FinoteIconButton(
                iconRes = R.drawable.ic_delete,
                contentDescription = stringResource(R.string.cash_entry_delete_cd),
                onClick = onDelete,
                enabled = !isSaving,
            )
        }
    }
}
