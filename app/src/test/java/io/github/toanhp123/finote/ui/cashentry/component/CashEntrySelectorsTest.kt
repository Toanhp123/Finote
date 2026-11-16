package io.github.toanhp123.finote.ui.cashentry.component

import android.content.Context
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.toanhp123.finote.R
import io.github.toanhp123.finote.ui.cashentry.CashEntryState
import io.github.toanhp123.finote.ui.common.theme.FinoteTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
@Config(sdk = [37])
class CashEntrySelectorsTest {
    @get:Rule
    val rule = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun string(id: Int) = context.getString(id)

    private fun showSelectors(note: String = "") {
        rule.setContent {
            FinoteTheme(dynamicColor = false) {
                Selectors(
                    state = CashEntryState(date = LocalDate.of(2026, 11, 15), note = note),
                    onIntent = {},
                    onDateClick = {},
                )
            }
        }
    }

    @Test
    fun `each selector reads its label`() {
        showSelectors()

        listOf(R.string.cash_entry_account, R.string.cash_entry_category, R.string.cash_entry_date).forEach {
            rule
                .onNode(hasContentDescription("${string(it)}, ", substring = true))
                .assertHasClickAction()
        }
    }

    @Test
    fun `a blank note reads the label once`() {
        showSelectors()

        rule
            .onNodeWithContentDescription(string(R.string.cash_entry_note))
            .assertHasClickAction()
    }

    @Test
    fun `a filled note reads label then text`() {
        showSelectors(note = "lunch")

        rule
            .onNodeWithContentDescription("${string(R.string.cash_entry_note)}, lunch")
            .assertHasClickAction()
    }

    @Test
    fun `a selector is read once`() {
        showSelectors()

        rule.onAllNodesWithText(string(R.string.cash_entry_no_account)).assertCountEquals(0)
        rule.onAllNodesWithText(string(R.string.cash_entry_no_category)).assertCountEquals(0)
        rule.onAllNodesWithText(string(R.string.cash_entry_today)).assertCountEquals(0)
    }
}
