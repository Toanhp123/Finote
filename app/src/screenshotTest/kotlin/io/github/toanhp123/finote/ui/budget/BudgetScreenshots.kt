package io.github.toanhp123.finote.ui.budget

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import io.github.toanhp123.finote.domain.budget.model.Budget
import io.github.toanhp123.finote.domain.budget.model.BudgetStatus
import io.github.toanhp123.finote.domain.category.model.Category
import io.github.toanhp123.finote.domain.category.model.CategoryType
import io.github.toanhp123.finote.ui.PHONE
import io.github.toanhp123.finote.ui.TallScreenPreviews
import io.github.toanhp123.finote.ui.common.theme.FinoteTheme
import java.time.LocalDate
import java.time.YearMonth
import io.github.toanhp123.finote.domain.budget.model.BudgetState as Level

private val NOVEMBER: YearMonth = YearMonth.of(2026, 11)
private val TODAY: LocalDate = LocalDate.of(2026, 11, 15)

@PreviewTest
@Preview(name = "Light", device = PHONE)
@Composable
private fun BudgetNormalPreview() =
    Frame(state(total = status("t", null, 10_000_000, 3_000_000), rows = listOf(food(spent = 900_000))))

@PreviewTest
@Preview(name = "Light", device = PHONE)
@Composable
private fun BudgetWarningPreview() = Frame(state(rows = listOf(food(spent = 2_900_000))))

@PreviewTest
@Preview(name = "Light", device = PHONE)
@Composable
private fun BudgetOverPreview() = Frame(state(rows = listOf(shopping(spent = 1_500_000))))

// Carry below zero: nothing is available, so the row says "Hết" and shows no left or over amount.
@PreviewTest
@Preview(name = "Light", device = PHONE)
@Composable
private fun BudgetNothingAvailablePreview() = Frame(state(rows = listOf(housing(spent = 300_000, carry = -1_200_000))))

@PreviewTest
@Preview(name = "Light", device = PHONE)
@Composable
private fun BudgetRolloverPreview() =
    Frame(state(rows = listOf(transport(spent = 800_000, carry = 500_000), health(spent = 400_000, carry = -200_000))))

@PreviewTest
@Preview(name = "Light", device = PHONE)
@Composable
private fun BudgetBannerPreview() = Frame(state(rows = listOf(food(spent = 900_000))), isBannerShown = true)

@PreviewTest
@Preview(name = "Light", device = PHONE)
@Composable
private fun BudgetEmptyPreview() = Frame(state(rows = emptyList(), total = null, isAddEnabled = true))

@PreviewTest
@Preview(name = "Light", device = PHONE)
@Composable
private fun BudgetLoadingPreview() = Frame(BudgetState(NOVEMBER, NOVEMBER, TODAY))

// A past month: no add button, no edit buttons, the next-month button is on.
@PreviewTest
@Preview(name = "Light", device = PHONE)
@Composable
private fun BudgetPastMonthPreview() =
    Frame(state(rows = listOf(food(spent = 2_900_000), shopping(spent = 1_500_000)), month = NOVEMBER.minusMonths(1)))

// Every state in one list, in light, dark and font scale 2.
@PreviewTest
@TallScreenPreviews
@Composable
private fun BudgetFullPreview() =
    Frame(
        state(
            rows =
                listOf(
                    food(spent = 2_900_000),
                    shopping(spent = 1_500_000),
                    housing(spent = 300_000, carry = -1_200_000),
                    transport(spent = 800_000, carry = 500_000),
                    health(spent = 100_000),
                ),
        ),
        isBannerShown = true,
    )

@Composable
private fun Frame(
    state: BudgetState,
    isBannerShown: Boolean = false,
) {
    FinoteTheme(dynamicColor = false) {
        Box(Modifier.fillMaxSize().background(FinoteTheme.colors.background)) {
            BudgetScreen(
                state = state,
                onIntent = {},
                modifier = Modifier.statusBarsPadding(),
                isBannerShown = isBannerShown,
                contentPadding = PaddingValues(bottom = FinoteTheme.spacing.xxl),
            )
        }
    }
}

private fun state(
    rows: List<BudgetRowUi>,
    total: BudgetStatus? = status("t", null, 10_000_000, 6_200_000),
    month: YearMonth = NOVEMBER,
    isAddEnabled: Boolean = month == NOVEMBER,
) = BudgetState(
    month = month,
    currentMonth = NOVEMBER,
    today = TODAY,
    total = total?.let { it.copy(budget = it.budget.copy(month = month)) },
    rows = rows.map { it.copy(status = it.status.copy(budget = it.status.budget.copy(month = month))) },
    isAddEnabled = isAddEnabled,
    isLoading = false,
)

private fun food(spent: Long) = row("sys_food", "restaurant", 0xFFE53935.toInt(), 3_000_000, spent)

private fun shopping(spent: Long) = row("sys_shopping", "shopping_bag", 0xFF8E24AA.toInt(), 1_000_000, spent)

private fun housing(
    spent: Long,
    carry: Long,
) = row("sys_housing", "home", 0xFF43A047.toInt(), 1_000_000, spent, carry)

private fun transport(
    spent: Long,
    carry: Long = 0,
) = row("sys_transport", "directions_car", 0xFF1E88E5.toInt(), 2_000_000, spent, carry)

private fun health(
    spent: Long,
    carry: Long = 0,
) = row("sys_health", "medical_services", 0xFFFB8C00.toInt(), 1_000_000, spent, carry)

private fun row(
    key: String,
    icon: String,
    color: Int,
    limit: Long,
    spent: Long,
    carry: Long = 0,
) = BudgetRowUi(
    status = status(key, key, limit, spent, carry),
    category =
        Category(
            id = key,
            name = key,
            nameKey = key,
            icon = icon,
            color = color,
            type = CategoryType.EXPENSE,
            parentId = null,
            isSystemDefault = true,
            isHidden = false,
            sortOrder = 0,
            createdAt = 0,
            updatedAt = 0,
            deletedAt = null,
        ),
)

// The warning mark is 80%. Percent and state follow the spec's integer rules.
private fun status(
    id: String,
    categoryId: String?,
    limit: Long,
    spent: Long,
    carry: Long = 0,
): BudgetStatus {
    val available = limit + carry
    val level =
        when {
            available <= 0 || spent >= available -> Level.OVER
            spent * 100 >= available * 80 -> Level.WARNING
            else -> Level.NORMAL
        }
    return BudgetStatus(
        budget = Budget(id, categoryId, limit, NOVEMBER, carry != 0L, 80, null, 0, 0),
        spent = spent,
        carry = carry,
        available = available,
        state = level,
        percent = if (available > 0) (spent * 100 / available).coerceAtLeast(0).toInt() else null,
    )
}
