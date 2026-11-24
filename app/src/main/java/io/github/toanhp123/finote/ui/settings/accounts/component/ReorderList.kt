package io.github.toanhp123.finote.ui.settings.accounts.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import io.github.toanhp123.finote.R
import io.github.toanhp123.finote.ui.common.component.display.FinoteCard
import io.github.toanhp123.finote.ui.common.component.display.FinoteListRow
import io.github.toanhp123.finote.ui.common.component.finance.MoneyText
import io.github.toanhp123.finote.ui.common.format.MoneyKind
import io.github.toanhp123.finote.ui.common.format.displayName
import io.github.toanhp123.finote.ui.common.theme.FinoteTheme
import io.github.toanhp123.finote.ui.settings.accounts.AccountsSettingsIntent
import io.github.toanhp123.finote.ui.settings.accounts.logic.AccountListItem
import sh.calvin.reorderable.ReorderableCollectionItemScope
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * The top-level rows of the accounts list, each with a drag handle. A bank group is one row and moves with its
 * members. Without dragging, the row's accessibility actions move it up or down.
 */
@Composable
fun ReorderList(
    items: List<AccountListItem>,
    onIntent: (AccountsSettingsIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val reorderState =
        rememberReorderableLazyListState(listState) { from, to ->
            onIntent(AccountsSettingsIntent.ReorderMoved(from.index, to.index))
        }
    LazyColumn(
        modifier = modifier,
        state = listState,
        contentPadding =
            PaddingValues(
                start = FinoteTheme.spacing.lg,
                end = FinoteTheme.spacing.lg,
                bottom = FinoteTheme.spacing.lg,
            ),
        verticalArrangement = Arrangement.spacedBy(FinoteTheme.spacing.sm),
    ) {
        itemsIndexed(items, key = { _, item -> item.key }) { index, item ->
            ReorderableItem(reorderState, key = item.key) { isDragging ->
                ReorderRow(item, index, isLast = index == items.lastIndex, isDragging, onIntent)
            }
        }
    }
}

@Composable
private fun ReorderableCollectionItemScope.ReorderRow(
    item: AccountListItem,
    index: Int,
    isLast: Boolean,
    isDragging: Boolean,
    onIntent: (AccountsSettingsIntent) -> Unit,
) {
    val title = item.title()
    val moveUp = stringResource(R.string.accounts_move_up)
    val moveDown = stringResource(R.string.accounts_move_down)
    val count = (item as? AccountListItem.Group)?.members?.size
    FinoteCard(
        modifier =
            Modifier
                .fillMaxWidth()
                .alpha(if (isDragging) FinoteTheme.colors.mutedAlpha else 1f)
                .semantics(mergeDescendants = true) {
                    customActions =
                        buildList {
                            val move = { to: Int -> onIntent(AccountsSettingsIntent.ReorderMoved(index, to)) }
                            if (index > 0) add(moveAction(moveUp) { move(index - 1) })
                            if (!isLast) add(moveAction(moveDown) { move(index + 1) })
                        }
                },
    ) {
        FinoteListRow(
            title = title,
            subtitle = count?.let { pluralStringResource(R.plurals.accounts_group_count, it, it) },
            trailing = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MoneyText(amount = item.balance(), kind = MoneyKind.Neutral)
                    DragHandle(title)
                }
            },
        )
    }
}

@Composable
private fun ReorderableCollectionItemScope.DragHandle(title: String) {
    Box(
        modifier = Modifier.size(FinoteTheme.sizes.touchTarget).draggableHandle(),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_drag_handle),
            contentDescription = stringResource(R.string.accounts_drag_handle, title),
            modifier = Modifier.size(FinoteTheme.sizes.iconMd),
            tint = FinoteTheme.scheme.onSurfaceVariant,
        )
    }
}

private fun moveAction(
    label: String,
    move: () -> Unit,
) = CustomAccessibilityAction(label) {
    move()
    true
}

private fun AccountListItem.balance(): Long =
    when (this) {
        is AccountListItem.Single -> account.currentBalance
        is AccountListItem.Group -> total
    }

@Composable
private fun AccountListItem.title(): String =
    when (this) {
        is AccountListItem.Single -> displayName(account.name, account.nameKey)
        is AccountListItem.Group -> bankName
    }
