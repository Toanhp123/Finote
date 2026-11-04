package io.github.toanhp123.finote.ui.cashentry

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import io.github.toanhp123.finote.domain.account.model.Account
import io.github.toanhp123.finote.domain.account.model.AccountType
import io.github.toanhp123.finote.domain.category.model.Category
import io.github.toanhp123.finote.domain.category.model.CategoryType
import io.github.toanhp123.finote.domain.transaction.AmountResult
import io.github.toanhp123.finote.domain.transaction.InvalidReason
import io.github.toanhp123.finote.domain.transaction.model.TransactionType
import io.github.toanhp123.finote.ui.common.component.PreviewGreenArgb
import io.github.toanhp123.finote.ui.common.component.PreviewRedArgb
import io.github.toanhp123.finote.ui.common.component.SheetSurface
import io.github.toanhp123.finote.ui.common.theme.FinoteTheme
import java.time.LocalDate

private const val SCRIM_ALPHA = 0.6f

@PreviewTest
@CashEntryPreviews
@Composable
private fun CashEntryKeypadPreview() = SheetOverScrim(keypadState())

@PreviewTest
@CashEntryPreviews
@Composable
private fun CashEntryCategoryPreview() = SheetOverScrim(keypadState().copy(page = CashEntryPage.CATEGORY))

@PreviewTest
@CashEntryPreviews
@Composable
private fun CashEntryNotePreview() = SheetOverScrim(keypadState().copy(page = CashEntryPage.NOTE, note = "Lunch"))

@PreviewTest
@Preview(name = "Light", device = PHONE)
@Composable
private fun CashEntryNotPositivePreview() =
    SheetOverScrim(
        keypadState().copy(expression = "10000−45000", result = AmountResult.NotPositive(-35_000)),
    )

@PreviewTest
@Preview(name = "Light", device = PHONE)
@Composable
private fun CashEntryDivideByZeroPreview() =
    SheetOverScrim(
        keypadState().copy(
            type = TransactionType.INCOME,
            expression = "5000÷0",
            result = AmountResult.Invalid(InvalidReason.DIVIDE_BY_ZERO),
        ),
    )

/** layoutlib has no second window: the sheet is drawn as its content over a scrim, like the other overlays. */
@Composable
private fun SheetOverScrim(state: CashEntryState) {
    FinoteTheme(dynamicColor = false) {
        Box(
            modifier = Modifier.fillMaxSize().background(FinoteTheme.scheme.scrim.copy(alpha = SCRIM_ALPHA)),
            contentAlignment = Alignment.BottomCenter,
        ) {
            val spacing = FinoteTheme.spacing
            SheetSurface(contentPadding = PaddingValues(start = spacing.lg, end = spacing.lg, bottom = spacing.lg)) {
                CashEntrySheet(state = state, onIntent = {})
            }
        }
    }
}

// A past date: "today" would read as the word "Today" only on that day and change the image.
private fun keypadState() =
    CashEntryState(
        date = LocalDate.of(2025, 3, 3),
        isToday = false,
        expression = "20000+15000",
        result = AmountResult.Ok(35_000),
        accounts = listOf(cash),
        selectedAccountId = cash.id,
        categories = categories,
        selectedCategoryIds = mapOf(TransactionType.EXPENSE to "sys_food_coffee"),
    )

private val cash =
    Account(
        id = "sys_cash",
        name = "Cash",
        nameKey = "sys_cash",
        type = AccountType.CASH,
        initialBalance = 0,
        currentBalance = 0,
        currency = "VND",
        icon = "payments",
        color = PreviewGreenArgb,
        sortOrder = 0,
        isExcludedFromNetWorth = false,
        isArchived = false,
        isInitialized = false,
        createdAt = 0,
        updatedAt = 0,
        deletedAt = null,
    )

private val categories =
    listOf(
        category("sys_food", parentId = null, icon = "restaurant", sortOrder = 0),
        category("sys_food_groceries", parentId = "sys_food", icon = "restaurant", sortOrder = 1),
        category("sys_food_coffee", parentId = "sys_food", icon = "restaurant", sortOrder = 2),
        category("sys_transport", parentId = null, icon = "directions_car", sortOrder = 3),
    )

private fun category(
    key: String,
    parentId: String?,
    icon: String,
    sortOrder: Int,
) = Category(
    id = key,
    name = key,
    nameKey = key,
    icon = icon,
    color = PreviewRedArgb,
    type = CategoryType.EXPENSE,
    parentId = parentId,
    isSystemDefault = true,
    isHidden = false,
    sortOrder = sortOrder,
    createdAt = 0,
    updatedAt = 0,
    deletedAt = null,
)
