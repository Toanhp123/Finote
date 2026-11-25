package io.github.toanhp123.finote.ui.settings.categoryedit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.toanhp123.finote.R
import io.github.toanhp123.finote.domain.category.model.Category
import io.github.toanhp123.finote.ui.common.ObserveEffects
import io.github.toanhp123.finote.ui.common.component.control.FinoteButton
import io.github.toanhp123.finote.ui.common.component.control.FinoteButtonStyle
import io.github.toanhp123.finote.ui.common.component.display.FinoteCard
import io.github.toanhp123.finote.ui.common.component.display.FinoteListRow
import io.github.toanhp123.finote.ui.common.component.finance.CategoryReorderList
import io.github.toanhp123.finote.ui.common.component.layout.FinoteTopBar
import io.github.toanhp123.finote.ui.common.component.overlay.FinoteSnackbarState
import io.github.toanhp123.finote.ui.common.format.displayName
import io.github.toanhp123.finote.ui.common.theme.FinoteTheme
import io.github.toanhp123.finote.ui.navigation.EditCategory
import io.github.toanhp123.finote.ui.settings.categoryedit.component.CategoryFormSection
import io.github.toanhp123.finote.ui.settings.categoryedit.component.DeleteCategoryDialog
import io.github.toanhp123.finote.ui.settings.categoryedit.component.DeleteMoveSheet
import io.github.toanhp123.finote.ui.settings.categoryedit.component.SubcategorySection
import io.github.toanhp123.finote.ui.settings.categoryedit.logic.CategoryDraft
import io.github.toanhp123.finote.ui.settings.categoryedit.logic.messageRes
import kotlinx.coroutines.launch

/**
 * [onClose] runs when the screen should leave: after a save or a delete, when the category is gone, or on Back.
 * [onOpenCategory] opens another category screen (a new subcategory, or a child of this one).
 */
@Composable
fun CategoryEditRoute(
    viewModel: CategoryEditViewModel,
    snackbarState: FinoteSnackbarState,
    onClose: () -> Unit,
    onOpenCategory: (EditCategory) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val resources = LocalResources.current
    ObserveEffects(viewModel.effects) { effect ->
        when (effect) {
            CategoryEditEffect.Close -> {
                onClose()
            }

            is CategoryEditEffect.OpenEditCategory -> {
                onOpenCategory(effect.key)
            }

            is CategoryEditEffect.ActionFailed -> {
                scope.launch { snackbarState.show(resources.getString(effect.error.messageRes())) }
            }
        }
    }
    CategoryEditScreen(
        state = state,
        onIntent = viewModel::onIntent,
        onBack = onClose,
        onOpenChild = { onOpenCategory(EditCategory(id = it.id, type = it.type)) },
        modifier = modifier,
    )
}

@Composable
fun CategoryEditScreen(
    state: CategoryEditState,
    onIntent: (CategoryEditIntent) -> Unit,
    onBack: () -> Unit,
    onOpenChild: (Category) -> Unit,
    modifier: Modifier = Modifier,
) {
    val draft = state.draft
    var isReordering by rememberSaveable { mutableStateOf(false) }
    // The overlays are drawn after the body in the same Box, so an inline overlay (preview mode) covers the top bar.
    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().imePadding()) {
            val isCreate = state.stored == null && draft != null
            FinoteTopBar(
                title =
                    stringResource(
                        if (isCreate) R.string.category_edit_title_new else R.string.category_edit_title_edit,
                    ),
                onBack = onBack,
            )
            if (draft != null && isReordering) {
                ReorderBody(state.children, onIntent) { isReordering = false }
            } else if (draft != null) {
                FormBody(state, draft, onIntent, onOpenChild, onReorder = { isReordering = true })
                FinoteButton(
                    text = stringResource(R.string.cash_entry_save),
                    onClick = { onIntent(CategoryEditIntent.SaveClicked) },
                    modifier = Modifier.fillMaxWidth().padding(FinoteTheme.spacing.lg),
                    enabled = state.isSaveEnabled,
                    isLoading = state.isSaving,
                )
            }
        }
        DeleteOverlays(state, onIntent)
    }
}

@Composable
private fun ColumnScope.FormBody(
    state: CategoryEditState,
    draft: CategoryDraft,
    onIntent: (CategoryEditIntent) -> Unit,
    onOpenChild: (Category) -> Unit,
    onReorder: () -> Unit,
) {
    val stored = state.stored
    Column(
        modifier =
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = FinoteTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(FinoteTheme.spacing.xl),
    ) {
        CategoryFormSection(
            draft = draft,
            stored = stored,
            parents = state.parents,
            isParentLocked = state.isParentLocked,
            onDraftChange = { onIntent(CategoryEditIntent.DraftChanged(it)) },
        )
        if (stored != null) {
            // A child has no children, and a parent change that is not saved yet would make the list lie.
            if (stored.parentId == null && draft.parentId == null) {
                SubcategorySection(
                    children = state.children,
                    onOpen = onOpenChild,
                    onAdd = { onIntent(CategoryEditIntent.AddSubcategoryClicked) },
                    onReorder = onReorder,
                )
            }
            HideRow(stored, state.isHiddenByParent, onIntent)
            // A system category cannot be deleted; a category being created has nothing to delete.
            if (!stored.isSystemDefault) {
                FinoteButton(
                    text = stringResource(R.string.category_edit_delete),
                    onClick = { onIntent(CategoryEditIntent.DeleteClicked) },
                    modifier = Modifier.fillMaxWidth(),
                    style = FinoteButtonStyle.Danger,
                )
            }
        }
    }
}

@Composable
private fun HideRow(
    stored: Category,
    isHiddenByParent: Boolean,
    onIntent: (CategoryEditIntent) -> Unit,
) {
    val iconRes = if (stored.isHidden) R.drawable.ic_visibility else R.drawable.ic_visibility_off
    FinoteCard(modifier = Modifier.fillMaxWidth()) {
        FinoteListRow(
            title = stringResource(if (stored.isHidden) R.string.category_edit_show else R.string.category_edit_hide),
            subtitle = if (isHiddenByParent) stringResource(R.string.category_edit_hidden_by_parent) else null,
            leading = {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(FinoteTheme.sizes.iconMd),
                    tint = FinoteTheme.scheme.onSurfaceVariant,
                )
            },
            onClick = { onIntent(CategoryEditIntent.HiddenToggled) },
        )
    }
}

/** The children in a list the user drags. The order lives here until Done sends it; Cancel drops it. */
@Composable
private fun ColumnScope.ReorderBody(
    children: List<Category>,
    onIntent: (CategoryEditIntent) -> Unit,
    onFinish: () -> Unit,
) {
    var order by remember { mutableStateOf(children) }
    CategoryReorderList(
        rows = order,
        onMove = { from, to -> order = order.toMutableList().apply { add(to, removeAt(from)) } },
        modifier = Modifier.weight(1f).padding(horizontal = FinoteTheme.spacing.lg),
    )
    Column(
        modifier = Modifier.padding(FinoteTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(FinoteTheme.spacing.sm),
    ) {
        FinoteButton(
            text = stringResource(R.string.accounts_reorder_done),
            onClick = {
                onIntent(CategoryEditIntent.ChildrenReordered(order.map { it.id }))
                onFinish()
            },
            modifier = Modifier.fillMaxWidth(),
        )
        FinoteButton(
            text = stringResource(R.string.accounts_reorder_cancel),
            onClick = onFinish,
            modifier = Modifier.fillMaxWidth(),
            style = FinoteButtonStyle.Ghost,
        )
    }
}

/** A category nobody uses gets a plain confirm; one with transactions asks where they go. */
@Composable
private fun DeleteOverlays(
    state: CategoryEditState,
    onIntent: (CategoryEditIntent) -> Unit,
) {
    val stored = state.stored
    val name = stored?.let { displayName(it.name, it.nameKey) }.orEmpty()
    val hasChildren = stored != null && state.categories.any { it.parentId == stored.id }
    val delete = state.delete
    DeleteCategoryDialog(
        isVisible = delete?.transactionCount == 0,
        name = name,
        hasChildren = hasChildren,
        onConfirm = { onIntent(CategoryEditIntent.DeleteConfirmed) },
        onDismiss = { onIntent(CategoryEditIntent.DeleteDismissed) },
    )
    DeleteMoveSheet(
        sheet = delete?.takeIf { it.transactionCount > 0 },
        name = name,
        hasChildren = hasChildren,
        onPick = { onIntent(CategoryEditIntent.DeleteChoicePicked(it)) },
        onConfirm = { onIntent(CategoryEditIntent.DeleteConfirmed) },
        onDismiss = { onIntent(CategoryEditIntent.DeleteDismissed) },
    )
}
