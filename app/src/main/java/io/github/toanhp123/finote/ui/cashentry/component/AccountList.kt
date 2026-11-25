package io.github.toanhp123.finote.ui.cashentry.component

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.toanhp123.finote.domain.account.model.Account
import io.github.toanhp123.finote.ui.common.component.control.PickerRow
import io.github.toanhp123.finote.ui.common.format.displayName
import io.github.toanhp123.finote.ui.common.format.iconRes

@Composable
fun AccountList(
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
