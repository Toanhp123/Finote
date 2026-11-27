package io.github.toanhp123.finote.ui.budgetedit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import io.github.toanhp123.finote.domain.budget.error.BudgetError
import io.github.toanhp123.finote.domain.category.model.Category
import io.github.toanhp123.finote.domain.category.model.CategoryType
import io.github.toanhp123.finote.ui.PHONE
import io.github.toanhp123.finote.ui.budgetedit.component.DeleteBudgetDialog
import io.github.toanhp123.finote.ui.common.component.overlay.FinoteBottomSheet
import io.github.toanhp123.finote.ui.common.theme.FinoteTheme

private const val TALL_PHONE = "spec:width=360dp,height=800dp"
private const val LARGE_FONT_PHONE = "spec:width=360dp,height=1300dp"

// Create: the total and two free categories, the first one picked, nothing typed yet.
@PreviewTest
@Preview(name = "Light", device = TALL_PHONE)
@Composable
private fun BudgetEditCreatePreview() = Sheet(createState())

// Edit: the category is locked, the limit is empty, so the field shows its error.
@PreviewTest
@Preview(name = "Light", device = TALL_PHONE)
@Composable
private fun BudgetEditErrorPreview() =
    Sheet(
        editState().copy(limitText = "", limitError = BudgetError.InvalidLimit),
    )

// A refused save: the message sits above Save, inside the sheet.
@PreviewTest
@Preview(name = "Light", device = TALL_PHONE)
@Composable
private fun BudgetEditFormErrorPreview() = Sheet(createState().copy(formError = BudgetEditError.DUPLICATE))

@PreviewTest
@Preview(name = "Large font", device = LARGE_FONT_PHONE, fontScale = 2f)
@Composable
private fun BudgetEditFormErrorLargeFontPreview() = Sheet(editState().copy(formError = BudgetEditError.FAILED))

@PreviewTest
@Preview(name = "Light", device = TALL_PHONE)
@Composable
private fun BudgetEditDeletePreview() = Sheet(editState().copy(isDeleteDialogShown = true))

@PreviewTest
@Preview(name = "Dark", device = TALL_PHONE, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun BudgetEditDarkPreview() = Sheet(editState())

@PreviewTest
@Preview(name = "Large font", device = LARGE_FONT_PHONE, fontScale = 2f)
@Composable
private fun BudgetEditLargeFontPreview() = Sheet(editState().copy(isRollover = true, threshold = 90))

@PreviewTest
@Preview(name = "Light", device = PHONE)
@Composable
private fun BudgetEditLoadingPreview() = Sheet(BudgetEditState(id = "b1"))

@Composable
private fun Sheet(state: BudgetEditState) {
    FinoteTheme(dynamicColor = false) {
        Box(Modifier.fillMaxSize().background(FinoteTheme.colors.background)) {
            FinoteBottomSheet(isVisible = true, onDismiss = {}) {
                BudgetEditScreen(state = state, onIntent = {})
            }
            // Called last, as the route does, so its inline scrim covers the sheet.
            DeleteBudgetDialog(isVisible = state.isDeleteDialogShown, onConfirm = {}, onDismiss = {})
        }
    }
}

private fun createState() =
    BudgetEditState(
        choices = listOf(null, category("sys_food", "restaurant"), category("sys_shopping", "shopping_bag")),
        limitText = "3000000",
        isLoading = false,
    )

private fun editState() =
    BudgetEditState(
        id = "b1",
        categoryId = "sys_food",
        isCategoryLocked = true,
        choices = listOf(category("sys_food", "restaurant")),
        limitText = "3000000",
        isLoading = false,
    )

private fun category(
    key: String,
    icon: String,
) = Category(
    id = key,
    name = key,
    nameKey = key,
    icon = icon,
    color = 0xFFE53935.toInt(),
    type = CategoryType.EXPENSE,
    parentId = null,
    isSystemDefault = true,
    isHidden = false,
    sortOrder = 0,
    createdAt = 0,
    updatedAt = 0,
    deletedAt = null,
)
