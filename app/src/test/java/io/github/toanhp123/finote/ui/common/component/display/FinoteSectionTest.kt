package io.github.toanhp123.finote.ui.common.component.display

import androidx.compose.material3.Text
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.toanhp123.finote.ui.common.theme.FinoteTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [37])
class FinoteSectionTest {
    @get:Rule
    val rule = createComposeRule()

    private fun showSection() {
        rule.setContent {
            FinoteTheme(dynamicColor = false) {
                FinoteSection(title = "Accounts") { Text("Body") }
            }
        }
    }

    @Test
    fun `the title is a heading`() {
        showSection()

        rule.onNodeWithText("Accounts").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
    }

    @Test
    fun `the content is displayed`() {
        showSection()

        rule.onNodeWithText("Body").assertIsDisplayed()
    }
}
