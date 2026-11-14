package io.github.toanhp123.finote.ui.common.component.control

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.toanhp123.finote.ui.common.theme.FinoteTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [37])
class FinoteSwitchRowTest {
    @get:Rule
    val rule = createComposeRule()

    private var reported: Boolean? = null

    private fun showRow(checked: Boolean) {
        rule.setContent {
            FinoteTheme(dynamicColor = false) {
                FinoteSwitchRow(title = "Trash", checked = checked, onCheckedChange = { reported = it })
            }
        }
    }

    @Test
    fun `tapping the row reports the opposite state`() {
        showRow(checked = false)

        rule.onNodeWithText("Trash").performClick()

        assertEquals(true, reported)
    }

    @Test
    fun `the row is one switch node`() {
        showRow(checked = false)

        rule.onAllNodes(hasText("Trash")).assertCountEquals(1)
        rule
            .onNodeWithText("Trash")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))
            .assertIsOff()
    }
}
