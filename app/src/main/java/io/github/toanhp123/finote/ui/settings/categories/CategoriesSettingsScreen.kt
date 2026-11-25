package io.github.toanhp123.finote.ui.settings.categories

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.toanhp123.finote.R
import io.github.toanhp123.finote.domain.category.model.Category
import io.github.toanhp123.finote.domain.category.model.CategoryType
import io.github.toanhp123.finote.ui.common.ObserveEffects
import io.github.toanhp123.finote.ui.common.component.control.FinoteIconButton
import io.github.toanhp123.finote.ui.common.component.control.FinoteSegmented
import io.github.toanhp123.finote.ui.common.component.display.FinoteCard
import io.github.toanhp123.finote.ui.common.component.display.FinoteEmptyState
import io.github.toanhp123.finote.ui.common.component.finance.CategoryReorderList
import io.github.toanhp123.finote.ui.common.component.layout.FinoteTopBar
import io.github.toanhp123.finote.ui.common.component.overlay.FinoteSnackbarState
import io.github.toanhp123.finote.ui.common.component.preview.FinotePreviewSurface
import io.github.toanhp123.finote.ui.common.component.preview.FinotePreviews
import io.github.toanhp123.finote.ui.common.component.preview.PreviewRedArgb
import io.github.toanhp123.finote.ui.common.theme.FinoteTheme
import io.github.toanhp123.finote.ui.navigation.EditCategory
import io.github.toanhp123.finote.ui.settings.categories.component.CategoryChildRow
import io.github.toanhp123.finote.ui.settings.categories.component.CategoryParentRow
import io.github.toanhp123.finote.ui.settings.categories.component.HiddenSection
import io.github.toanhp123.finote.ui.settings.categories.logic.CategoryNode
import io.github.toanhp123.finote.ui.settings.categories.logic.CategoryTree
import kotlinx.coroutines.launch

@Composable
fun CategoriesSettingsRoute(
    viewModel: CategoriesSettingsViewModel,
    snackbarState: FinoteSnackbarState,
    onOpenCategory: (EditCategory) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val failed = stringResource(R.string.category_snack_failed)
    ObserveEffects(viewModel.effects) { effect ->
        when (effect) {
            is CategoriesSettingsEffect.OpenEditCategory -> onOpenCategory(effect.key)
            CategoriesSettingsEffect.ActionFailed -> scope.launch { snackbarState.show(failed) }
        }
    }
    // Back leaves the reorder mode first, without saving.
    BackHandler(enabled = state.reorder != null) { viewModel.onIntent(CategoriesSettingsIntent.ReorderCancelled) }
    CategoriesSettingsScreen(state, viewModel::onIntent, onClose, modifier)
}

@Composable
fun CategoriesSettingsScreen(
    state: CategoriesSettingsState,
    onIntent: (CategoriesSettingsIntent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val draft = state.reorder
    Column(modifier = modifier.fillMaxSize()) {
        CategoriesTopBar(isReorder = draft != null, canReorder = state.tree.shown.size > 1, onIntent, onBack)
        if (draft != null) {
            CategoryReorderList(
                rows = draft,
                onMove = { from, to -> onIntent(CategoriesSettingsIntent.ReorderMoved(from, to)) },
                modifier = Modifier.weight(1f).padding(horizontal = FinoteTheme.spacing.lg),
            )
        } else {
            CategoryList(state, onIntent, Modifier.weight(1f))
        }
    }
}

@Composable
private fun CategoriesTopBar(
    isReorder: Boolean,
    canReorder: Boolean,
    onIntent: (CategoriesSettingsIntent) -> Unit,
    onBack: () -> Unit,
) {
    if (isReorder) {
        FinoteTopBar(
            title = stringResource(R.string.categories_reorder),
            actions = {
                FinoteIconButton(
                    iconRes = R.drawable.ic_close,
                    contentDescription = stringResource(R.string.categories_reorder_cancel),
                    onClick = { onIntent(CategoriesSettingsIntent.ReorderCancelled) },
                )
                FinoteIconButton(
                    iconRes = R.drawable.ic_check,
                    contentDescription = stringResource(R.string.categories_reorder_done),
                    onClick = { onIntent(CategoriesSettingsIntent.ReorderDone) },
                )
            },
        )
    } else {
        FinoteTopBar(
            title = stringResource(R.string.settings_categories_title),
            onBack = onBack,
            actions = {
                if (canReorder) {
                    FinoteIconButton(
                        iconRes = R.drawable.ic_swap_vert,
                        contentDescription = stringResource(R.string.categories_reorder),
                        onClick = { onIntent(CategoriesSettingsIntent.ReorderClicked) },
                    )
                }
                FinoteIconButton(
                    iconRes = R.drawable.ic_add,
                    contentDescription = stringResource(R.string.categories_add),
                    onClick = { onIntent(CategoriesSettingsIntent.AddClicked) },
                )
            },
        )
    }
}

@Composable
private fun CategoryList(
    state: CategoriesSettingsState,
    onIntent: (CategoriesSettingsIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = FinoteTheme.spacing
    val tabLabels =
        mapOf(
            CategoryType.EXPENSE to stringResource(R.string.categories_tab_expense),
            CategoryType.INCOME to stringResource(R.string.categories_tab_income),
        )
    Column(
        modifier =
            modifier
                .verticalScroll(rememberScrollState())
                .padding(start = spacing.lg, end = spacing.lg, bottom = spacing.lg),
        verticalArrangement = Arrangement.spacedBy(spacing.lg),
    ) {
        FinoteSegmented(
            options = CategoryType.entries,
            selected = state.type,
            onSelect = { onIntent(CategoriesSettingsIntent.TabSelected(it)) },
            label = { tabLabels.getValue(it) },
            modifier = Modifier.fillMaxWidth(),
        )
        if (state.tree.shown.isEmpty()) {
            FinoteEmptyState(
                iconRes = R.drawable.ic_category,
                title = stringResource(R.string.categories_empty_title),
                message = stringResource(R.string.categories_empty_message),
            )
        } else {
            FinoteCard(modifier = Modifier.fillMaxWidth()) {
                state.tree.shown.forEach { NodeRows(it, state, onIntent) }
            }
        }
        // Shown even when every category of the tab is hidden, or the user could never get one back.
        if (state.tree.hidden.isNotEmpty()) {
            HiddenSection(
                count = state.tree.hidden.size,
                isExpanded = state.isHiddenExpanded,
                onToggle = { onIntent(CategoriesSettingsIntent.HiddenSectionToggled) },
            ) {
                state.tree.hidden.forEach { NodeRows(it, state, onIntent) }
            }
        }
    }
}

/** The row of a top-level category and, while it is open, its children indented under it. */
@Composable
private fun NodeRows(
    node: CategoryNode,
    state: CategoriesSettingsState,
    onIntent: (CategoriesSettingsIntent) -> Unit,
) {
    val parent = node.category
    Column {
        CategoryParentRow(
            category = parent,
            childCount = node.children.size,
            isExpanded = parent.id in state.expanded,
            onIntent = onIntent,
        )
        if (parent.id in state.expanded) {
            node.children.forEach { CategoryChildRow(it, parent.isHidden, onIntent) }
        }
    }
}

@FinotePreviews
@Composable
private fun CategoriesSettingsScreenPreview() {
    val food = previewCategory("food", "Food & drink", parentId = null)
    val coffee = previewCategory("coffee", "Coffee", parentId = "food", isHidden = true)
    val home = previewCategory("home", "Home", parentId = null, isHidden = true)
    FinotePreviewSurface {
        CategoriesSettingsScreen(
            state =
                CategoriesSettingsState(
                    tree =
                        CategoryTree(
                            shown =
                                listOf(
                                    CategoryNode(food, listOf(previewCategory("bk", "Breakfast", "food"), coffee)),
                                    CategoryNode(previewCategory("tr", "Transport", null), emptyList()),
                                ),
                            hidden = listOf(CategoryNode(home, emptyList())),
                        ),
                    expanded = setOf("food"),
                ),
            onIntent = {},
            onBack = {},
        )
    }
}

private fun previewCategory(
    id: String,
    name: String,
    parentId: String?,
    isHidden: Boolean = false,
) = Category(
    id = id,
    name = name,
    nameKey = null,
    icon = "restaurant",
    color = PreviewRedArgb,
    type = CategoryType.EXPENSE,
    parentId = parentId,
    isSystemDefault = false,
    isHidden = isHidden,
    sortOrder = 0,
    createdAt = 0L,
    updatedAt = 0L,
    deletedAt = null,
)
