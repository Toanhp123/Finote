package io.github.toanhp123.finote.ui.common.component.overlay

import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.toanhp123.finote.ui.common.theme.FinoteTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [37])
class FinoteBottomSheetTest {
    @get:Rule
    val rule = createComposeRule()

    private var isVisible by mutableStateOf(true)
    private var dismissCount by mutableIntStateOf(0)

    private fun showSheet() {
        rule.setContent {
            FinoteTheme(dynamicColor = false) {
                FinoteBottomSheet(isVisible = isVisible, onDismiss = { dismissCount++ }) { Text("Sheet") }
            }
        }
        rule.waitForIdle()
    }

    @Test
    fun `caller hide does not call onDismiss`() {
        showSheet()

        isVisible = false
        rule.waitForIdle()

        assertEquals(0, dismissCount)
    }

    @Test
    fun `back press calls onDismiss while visible`() {
        showSheet()

        Espresso.pressBack()
        rule.waitForIdle()

        assertEquals(1, dismissCount)
    }
}
