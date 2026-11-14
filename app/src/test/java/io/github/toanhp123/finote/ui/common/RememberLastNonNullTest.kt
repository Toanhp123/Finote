package io.github.toanhp123.finote.ui.common

import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
class RememberLastNonNullTest {
    @get:Rule
    val rule = createComposeRule()

    private var value by mutableStateOf<String?>(null)

    private fun showText() {
        rule.setContent {
            FinoteTheme(dynamicColor = false) {
                Text(rememberLastNonNull(value) ?: "none")
            }
        }
        rule.waitForIdle()
    }

    @Test
    fun `starts null`() {
        showText()

        rule.onNodeWithText("none").assertExists()
    }

    @Test
    fun `keeps the last value after null`() {
        value = "A"
        showText()

        value = null
        rule.waitForIdle()

        rule.onNodeWithText("A").assertExists()
    }

    @Test
    fun `switches to the new value`() {
        value = "A"
        showText()

        value = null
        rule.waitForIdle()
        value = "B"
        rule.waitForIdle()

        rule.onNodeWithText("B").assertExists()
        rule.onNodeWithText("A").assertDoesNotExist()
    }
}
