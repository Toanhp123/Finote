package io.github.toanhp123.finote.ui.report

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import io.github.toanhp123.finote.domain.account.model.Account
import io.github.toanhp123.finote.domain.account.model.AccountType
import io.github.toanhp123.finote.domain.category.model.Category
import io.github.toanhp123.finote.domain.category.model.CategoryType
import io.github.toanhp123.finote.domain.report.model.ReportFilter
import io.github.toanhp123.finote.domain.report.model.ReportRow
import io.github.toanhp123.finote.domain.transaction.model.Transaction
import io.github.toanhp123.finote.domain.transaction.model.TransactionType
import io.github.toanhp123.finote.ui.PHONE
import io.github.toanhp123.finote.ui.ScreenPreviews
import io.github.toanhp123.finote.ui.common.component.preview.PreviewGreenArgb
import io.github.toanhp123.finote.ui.common.component.preview.PreviewRedArgb
import io.github.toanhp123.finote.ui.common.theme.FinoteTheme
import java.time.Instant
import java.time.YearMonth

private val NOVEMBER: YearMonth = YearMonth.of(2026, 11)
private const val DAY_MILLIS = 86_400_000L
private val FIRST = Instant.parse("2026-11-03T05:00:00Z").toEpochMilli()

// Food: three spends and a refund. The refund reads +amount in green and the total is the sum.
@PreviewTest
@ScreenPreviews
@Composable
private fun ReportListCategoryPreview() =
    Frame(
        loaded(
            ReportFilter.ByCategory("sys_food"),
            spend("a", 45_000, 0, "Highlands Coffee"),
            spend("b", 250_000, 1, "Pho 24"),
            refund("c", 120_000, 2),
            spend("d", 85_000, 3, "Circle K", account = archived),
        ),
    )

// Refunds above spending: the total reads +amount in green, like the bucket on Overview.
@PreviewTest
@Preview(name = "Light", device = PHONE)
@Composable
private fun ReportListNetRefundPreview() =
    Frame(loaded(ReportFilter.ByCategory("sys_food"), refund("a", 230_000, 0), spend("b", 30_000, 1, "Pho 24")))

// A fee list: each row's amount is its fee, with no second fee line.
@PreviewTest
@Preview(name = "Light", device = PHONE)
@Composable
private fun ReportListFeesPreview() =
    Frame(loaded(ReportFilter.TransferFees, fee("a", 7_000, 0), fee("b", 7_000, 1), fee("c", 11_000, 2)))

@PreviewTest
@Preview(name = "Light", device = PHONE)
@Composable
private fun ReportListMerchantPreview() =
    Frame(
        loaded(
            ReportFilter.ByMerchant("Highlands Coffee"),
            spend("a", 45_000, 0, "Highlands Coffee"),
            spend("b", 62_000, 1, "Highlands Coffee"),
        ),
    )

@PreviewTest
@Preview(name = "Light", device = PHONE)
@Composable
private fun ReportListEmptyPreview() = Frame(loaded(ReportFilter.ByCategory("sys_food")))

@Composable
private fun Frame(state: ReportListState) {
    FinoteTheme(dynamicColor = false) {
        Box(Modifier.fillMaxSize().background(FinoteTheme.colors.background)) {
            ReportListScreen(state = state, onIntent = {}, onBack = {}, modifier = Modifier.statusBarsPadding())
        }
    }
}

private fun loaded(
    filter: ReportFilter,
    vararg rows: ReportRow,
) = ReportListState(
    isLoading = false,
    month = NOVEMBER,
    filter = filter,
    rows = rows.toList(),
    total = rows.sumOf { it.amount },
    accountsById = listOf(bank, cash, archived).associateBy { it.id },
    categoriesById = listOf(food).associateBy { it.id },
    openableAccountIds = setOf(bank.id),
)

private fun spend(
    id: String,
    amount: Long,
    daysAfter: Long,
    merchant: String,
    account: Account = bank,
) = ReportRow(transaction(id, TransactionType.EXPENSE, amount, daysAfter, account, merchant, "sys_food"), amount)

private fun refund(
    id: String,
    amount: Long,
    daysAfter: Long,
) = ReportRow(transaction(id, TransactionType.REFUND, amount, daysAfter, bank, "Pho 24", "sys_food"), -amount)

private fun fee(
    id: String,
    fee: Long,
    daysAfter: Long,
) = ReportRow(
    transaction(id, TransactionType.TRANSFER, 2_000_000, daysAfter, bank, null, null, to = cash.id, fee = fee),
    fee,
)

private fun transaction(
    id: String,
    type: TransactionType,
    amount: Long,
    daysAfter: Long,
    account: Account,
    merchant: String?,
    category: String?,
    to: String? = null,
    fee: Long = 0,
): Transaction {
    val at = FIRST + daysAfter * DAY_MILLIS
    return Transaction(
        id = id,
        amount = amount,
        feeAmount = fee,
        type = type,
        adjustmentDirection = null,
        accountId = account.id,
        toAccountId = to,
        timestamp = at,
        deletedAt = null,
        createdAt = at,
        updatedAt = at,
        categoryId = category,
        merchantName = merchant,
    )
}

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

private val bank = account("acc-bank", "Vietcombank")
private val cash = account("acc-cash", "Cash")
private val archived = account("acc-old", "MoMo", isArchived = true)

private val food =
    Category(
        id = "sys_food",
        name = "sys_food",
        nameKey = "sys_food",
        icon = "restaurant",
        color = PreviewRedArgb,
        type = CategoryType.EXPENSE,
        parentId = null,
        isSystemDefault = true,
        isHidden = false,
        sortOrder = 0,
        createdAt = 0,
        updatedAt = 0,
        deletedAt = null,
    )
