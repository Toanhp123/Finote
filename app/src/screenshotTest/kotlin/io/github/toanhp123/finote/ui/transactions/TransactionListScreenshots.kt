package io.github.toanhp123.finote.ui.transactions

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import io.github.toanhp123.finote.domain.account.model.Account
import io.github.toanhp123.finote.domain.account.model.AccountType
import io.github.toanhp123.finote.domain.category.model.Category
import io.github.toanhp123.finote.domain.category.model.CategoryType
import io.github.toanhp123.finote.domain.transaction.groupByDay
import io.github.toanhp123.finote.domain.transaction.model.AdjustmentDirection
import io.github.toanhp123.finote.domain.transaction.model.ReimbursementStatus
import io.github.toanhp123.finote.domain.transaction.model.Transaction
import io.github.toanhp123.finote.domain.transaction.model.TransactionType
import io.github.toanhp123.finote.ui.common.component.PreviewGreenArgb
import io.github.toanhp123.finote.ui.common.component.PreviewRedArgb
import io.github.toanhp123.finote.ui.common.component.SheetSurface
import io.github.toanhp123.finote.ui.common.theme.FinoteTheme
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

private const val PHONE = "spec:width=360dp,height=640dp"
private const val SCRIM_ALPHA = 0.6f

@Preview(name = "Light", device = PHONE)
@Preview(name = "Dark", device = PHONE, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Large font", device = PHONE, fontScale = 2f)
private annotation class ScreenPreviews

@PreviewTest
@ScreenPreviews
@Composable
private fun TransactionListPreview() = Frame(populated())

@PreviewTest
@Preview(name = "Light", device = PHONE)
@Composable
private fun TransactionListEmptyPreview() = Frame(populated().copy(days = emptyList()))

@PreviewTest
@Preview(name = "Light", device = PHONE)
@Composable
private fun TransactionListEmptyAccountPreview() =
    Frame(populated().copy(days = emptyList(), selectedAccountId = bank.id))

@PreviewTest
@Preview(name = "Light", device = PHONE)
@Composable
private fun TransactionListErrorPreview() = Frame(populated().copy(days = emptyList(), hasError = true))

@PreviewTest
@Preview(name = "Light", device = PHONE)
@Composable
private fun TransactionListLoadingPreview() = Frame(TransactionListState(today = TODAY))

@PreviewTest
@Preview(name = "Light", device = PHONE)
@Composable
private fun TransactionListReimbursementPreview() = Frame(populated(withReimbursement = true))

@PreviewTest
@Preview(name = "Light", device = PHONE)
@Composable
private fun TransactionListPendingPreview() = Frame(pendingMode(populated(withReimbursement = true)))

@PreviewTest
@Preview(name = "Light", device = PHONE)
@Composable
private fun TransactionListPendingEmptyPreview() =
    Frame(pendingMode(populated(withReimbursement = true)).copy(days = emptyList()))

@PreviewTest
@Preview(name = "Light", device = PHONE)
@Preview(name = "Dark", device = PHONE, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ReimbursementSheetPreview() = SheetOverList(ReimbursementChoice.PENDING, hasSaveError = false)

@PreviewTest
@Preview(name = "Light", device = PHONE)
@Composable
private fun ReimbursementSheetErrorPreview() = SheetOverList(ReimbursementChoice.NORMAL, hasSaveError = true)

/** layoutlib has no second window: the sheet is drawn as its content over a scrim, like the other overlays. */
@Composable
private fun SheetOverList(
    selected: ReimbursementChoice,
    hasSaveError: Boolean,
) {
    Box {
        Frame(populated(withReimbursement = true))
        FinoteTheme(dynamicColor = false) {
            Box(
                modifier = Modifier.fillMaxSize().background(FinoteTheme.scheme.scrim.copy(alpha = SCRIM_ALPHA)),
                contentAlignment = Alignment.BottomCenter,
            ) {
                val spacing = FinoteTheme.spacing
                SheetSurface(
                    contentPadding = PaddingValues(start = spacing.lg, end = spacing.lg, bottom = spacing.lg),
                ) {
                    ReimbursementSheetContent(selected = selected, hasSaveError = hasSaveError, onPick = {})
                }
            }
        }
    }
}

@Composable
private fun Frame(state: TransactionListState) {
    FinoteTheme(dynamicColor = false) {
        Box(Modifier.fillMaxSize().background(FinoteTheme.colors.background).statusBarsPadding()) {
            TransactionListScreen(
                state = state,
                onIntent = {},
                contentPadding = PaddingValues(bottom = FinoteTheme.spacing.xxl),
            )
        }
    }
}

// A fixed "today": the day labels must not move with the date the test runs.
private val TODAY: LocalDate = LocalDate.of(2026, 11, 4)
private val zone = ZoneId.of("Asia/Ho_Chi_Minh")

private fun at(
    date: LocalDate,
    hour: Int,
): Long =
    LocalDateTime
        .of(date, LocalTime.of(hour, 0))
        .atZone(zone)
        .toInstant()
        .toEpochMilli()

private fun pendingMode(all: TransactionListState): TransactionListState {
    val pending =
        all.days
            .flatMap { it.transactions }
            .filter { it.reimbursementStatus == ReimbursementStatus.PENDING }
    return all.copy(selectedAccountId = null, isPendingOnly = true, days = groupByDay(pending, zone, null))
}

// withReimbursement marks two expenses; the plain list keeps the references of the list screenshots unchanged.
private fun populated(withReimbursement: Boolean = false): TransactionListState {
    val yesterday = TODAY.minusDays(1)
    val older = LocalDate.of(2026, 10, 29)
    val rows =
        listOf(
            row("t1", TransactionType.INCOME, 15_000_000, at(TODAY, 9), category = "sys_income_salary"),
            row(
                "t2",
                TransactionType.EXPENSE,
                45_000,
                at(TODAY, 8),
                category = "sys_food_coffee",
                note = "Latte",
                status = if (withReimbursement) ReimbursementStatus.PENDING else ReimbursementStatus.NONE,
            ),
            row(
                "t3",
                TransactionType.TRANSFER,
                2_000_000,
                at(yesterday, 20),
                to = momo.id,
                note = "Top up",
                fee = 1_000,
            ),
            row("t4", TransactionType.ADJUSTMENT, 120_000, at(yesterday, 10), direction = AdjustmentDirection.OUT),
            row(
                "t5",
                TransactionType.EXPENSE,
                80_000,
                at(older, 12),
                category = "sys_food_coffee",
                status = if (withReimbursement) ReimbursementStatus.SETTLED else ReimbursementStatus.NONE,
            ),
            row("t6", TransactionType.EXPENSE, 30_000, at(older, 9)),
        ) +
            if (withReimbursement) {
                listOf(row("t7", TransactionType.EXPENSE, 60_000, at(older, 8), status = ReimbursementStatus.PENDING))
            } else {
                emptyList()
            }
    return TransactionListState(
        isLoading = false,
        today = TODAY,
        accounts = listOf(cash, bank),
        days = groupByDay(rows, zone, null),
        accountsById = listOf(cash, bank, momo).associateBy { it.id },
        categoriesById = categories.associateBy { it.id },
    )
}

private fun row(
    id: String,
    type: TransactionType,
    amount: Long,
    at: Long,
    category: String? = null,
    to: String? = null,
    note: String = "",
    fee: Long = 0,
    direction: AdjustmentDirection? = null,
    status: ReimbursementStatus = ReimbursementStatus.NONE,
) = Transaction(
    id = id,
    amount = amount,
    feeAmount = fee,
    type = type,
    adjustmentDirection = direction,
    accountId = bank.id,
    toAccountId = to,
    timestamp = at,
    deletedAt = null,
    createdAt = at,
    updatedAt = at,
    categoryId = category,
    note = note,
    isExcludedFromAnalytics = status != ReimbursementStatus.NONE,
    reimbursementStatus = status,
)

private fun account(
    id: String,
    name: String,
    isArchived: Boolean = false,
) = Account(
    id = id,
    name = name,
    type = AccountType.BANK,
    initialBalance = 0,
    currentBalance = 0,
    currency = "VND",
    icon = "payments",
    color = PreviewGreenArgb,
    sortOrder = 0,
    isExcludedFromNetWorth = false,
    isArchived = isArchived,
    isInitialized = false,
    createdAt = 0,
    updatedAt = 0,
    deletedAt = null,
)

private val cash = account("acc-cash", "Cash")
private val bank = account("acc-bank", "Vietcombank")
private val momo = account("acc-momo", "MoMo", isArchived = true)

private val categories =
    listOf(
        category("sys_food_coffee", CategoryType.EXPENSE, "restaurant", PreviewRedArgb),
        category("sys_income_salary", CategoryType.INCOME, "work", PreviewGreenArgb),
    )

private fun category(
    key: String,
    type: CategoryType,
    icon: String,
    color: Int,
) = Category(
    id = key,
    name = key,
    nameKey = key,
    icon = icon,
    color = color,
    type = type,
    parentId = null,
    isSystemDefault = true,
    isHidden = false,
    sortOrder = 0,
    createdAt = 0,
    updatedAt = 0,
    deletedAt = null,
)
