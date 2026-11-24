package io.github.toanhp123.finote.ui.settings.accounts.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import io.github.toanhp123.finote.R
import io.github.toanhp123.finote.ui.common.component.display.FinoteListRow
import io.github.toanhp123.finote.ui.common.component.finance.MoneyText
import io.github.toanhp123.finote.ui.common.format.MoneyKind
import io.github.toanhp123.finote.ui.common.theme.FinoteTheme
import io.github.toanhp123.finote.ui.settings.accounts.AccountsSettingsIntent
import io.github.toanhp123.finote.ui.settings.accounts.logic.AccountListItem

/**
 * The row of one bank with several accounts: its name, how many accounts it holds and their total. A tap opens or
 * closes the group. [hasSuggestion] adds a line in text, so the marker never relies on color alone.
 */
@Composable
fun AccountGroupRow(
    group: AccountListItem.Group,
    isExpanded: Boolean,
    hasSuggestion: Boolean,
    onIntent: (AccountsSettingsIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        FinoteListRow(
            title = group.bankName,
            subtitle = pluralStringResource(R.plurals.accounts_group_count, group.members.size, group.members.size),
            trailing = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(FinoteTheme.spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MoneyText(amount = group.total, kind = MoneyKind.Neutral)
                    ExpandChevron(isExpanded)
                }
            },
            onClick = { onIntent(AccountsSettingsIntent.GroupToggled(group.packageName)) },
        )
        if (hasSuggestion) {
            Text(
                text = stringResource(R.string.accounts_group_suggested),
                color = FinoteTheme.scheme.primary,
                style = FinoteTheme.typography.material.bodySmall,
            )
        }
    }
}
