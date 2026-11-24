package io.github.toanhp123.finote.ui.settings.inbox

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.android.tools.screenshot.PreviewTest
import io.github.toanhp123.finote.domain.notification.model.AdKeywordPreview
import io.github.toanhp123.finote.domain.notification.model.AdKeywordRefusal
import io.github.toanhp123.finote.domain.notification.model.FailedInboxItem
import io.github.toanhp123.finote.domain.notification.model.ParsedFields
import io.github.toanhp123.finote.domain.transaction.model.TransactionType
import io.github.toanhp123.finote.ui.ScreenPreviews
import io.github.toanhp123.finote.ui.common.theme.FinoteTheme

private const val PKG = "com.test.bank"
private const val POST_TIME = 1_795_000_000_000L

private fun item(
    id: String,
    title: String,
    reason: String = "no_rule_matched",
    parsed: ParsedFields? = null,
    isHidden: Boolean = false,
    isKeywordHidden: Boolean = false,
) = FailedInboxItem(
    id = id,
    logIds = listOf(id),
    packageName = PKG,
    title = title,
    content = "",
    postTime = POST_TIME,
    receivedAt = POST_TIME,
    reason = reason,
    parsed = parsed,
    isHidden = isHidden || isKeywordHidden,
    isKeywordHidden = isKeywordHidden,
)

private val settlementFailure =
    item(
        id = "failed",
        title = "Thanh toán thẻ",
        reason = "ambiguous_account",
        parsed =
            ParsedFields(
                amount = 150_000L,
                type = TransactionType.EXPENSE,
                accountMask = "1111",
                postBalance = null,
                merchant = "Coffee",
                note = null,
            ),
    )

private val unread = item(id = "plain", title = "Card spend abroad VND 1,111,111")
private val promo = item(id = "promo", title = "Mời bạn ly cà phê đầu tuần", isHidden = true)
private val keywordHidden =
    item(id = "kw", title = "Ưu đãi cuối tuần", reason = "user_keyword:uu dai", isKeywordHidden = true)

private val bankNames = mapOf(PKG to "Test Bank")

private val shown = FailedInboxState(items = listOf(settlementFailure, unread), bankNames = bankNames, isLoaded = true)

@PreviewTest
@ScreenPreviews
@Composable
private fun InboxShownPreview() = Frame(shown)

@PreviewTest
@ScreenPreviews
@Composable
private fun InboxHiddenCollapsedPreview() = Frame(shown.copy(items = shown.items + listOf(promo, keywordHidden)))

@PreviewTest
@ScreenPreviews
@Composable
private fun InboxHiddenExpandedPreview() =
    Frame(shown.copy(items = shown.items + listOf(promo, keywordHidden), isHiddenExpanded = true))

@PreviewTest
@ScreenPreviews
@Composable
private fun InboxEmptyPreview() = Frame(FailedInboxState(isLoaded = true))

@PreviewTest
@ScreenPreviews
@Composable
private fun InboxAdSheetPreview() =
    Frame(
        shown.copy(
            adSheet = AdSheet("plain", "Card spend abroad", AdKeywordPreview(refusal = null, otherHiddenCount = 3)),
        ),
    )

@PreviewTest
@ScreenPreviews
@Composable
private fun InboxAdSheetNoOthersPreview() =
    Frame(
        shown.copy(
            adSheet = AdSheet("plain", "Card spend abroad", AdKeywordPreview(refusal = null, otherHiddenCount = 0)),
        ),
    )

@PreviewTest
@ScreenPreviews
@Composable
private fun InboxAdSheetRefusedPreview() =
    Frame(
        shown.copy(
            adSheet =
                AdSheet(
                    "plain",
                    "Card spend",
                    AdKeywordPreview(AdKeywordRefusal.IN_REAL_TRANSACTION, otherHiddenCount = 0),
                ),
        ),
    )

@Composable
private fun Frame(state: FailedInboxState) {
    FinoteTheme(dynamicColor = false) {
        Box(Modifier.fillMaxSize().background(FinoteTheme.colors.background).statusBarsPadding()) {
            FailedInboxScreen(state = state, onIntent = {})
        }
    }
}
