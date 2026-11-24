package io.github.toanhp123.finote.ui.settings.accounts.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.toanhp123.finote.R
import io.github.toanhp123.finote.domain.account.model.Account
import io.github.toanhp123.finote.ui.common.component.display.FinoteCard
import io.github.toanhp123.finote.ui.common.component.display.FinoteListRow
import io.github.toanhp123.finote.ui.settings.accounts.AccountsSettingsIntent

/** The archived accounts: a header with their count that opens and closes the list. */
@Composable
fun ArchivedSection(
    archived: List<Account>,
    isExpanded: Boolean,
    onIntent: (AccountsSettingsIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    FinoteCard(modifier = modifier.fillMaxWidth()) {
        FinoteListRow(
            title = stringResource(R.string.accounts_archived_title, archived.size),
            trailing = { ExpandChevron(isExpanded) },
            onClick = { onIntent(AccountsSettingsIntent.ArchivedToggled) },
        )
        if (isExpanded) {
            archived.forEach { account -> AccountRow(account, onIntent) }
        }
    }
}
