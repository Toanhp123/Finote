package io.github.toanhp123.finote.ui.common.component.overlay

import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
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

    private var dismissedCount by mutableIntStateOf(0)

    private fun showSheet(initial: Boolean = true) {
        isVisible = initial
        rule.setContent {
            FinoteTheme(dynamicColor = false) {
                FinoteBottomSheet(
                    isVisible = isVisible,
                    onDismiss = {
                        dismissCount++
                        isVisible = false
                    },
                    onDismissComplete = { dismissedCount++ },
                ) { Text("Sheet") }
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

    @Test
    fun `caller hide calls onDismissComplete after the animation`() {
        showSheet()

        isVisible = false
        rule.waitForIdle()

        assertEquals(0, dismissCount)
        assertEquals(1, dismissedCount)
    }

    @Test
    fun `user dismiss calls onDismiss then onDismissComplete`() {
        showSheet()

        Espresso.pressBack()
        rule.waitForIdle()

        assertEquals(1, dismissCount)
        assertEquals(1, dismissedCount)
    }

    @Test
    fun `scrim tap during the exit still calls onDismissComplete once`() {
        showSheet()

        isVisible = false
        rule.mainClock.autoAdvance = false
        repeat(3) { rule.mainClock.advanceTimeByFrame() }
        rule.onNodeWithText("Sheet").performTouchInput { click(Offset(centerX, -200f)) }
        rule.mainClock.autoAdvance = true
        rule.waitForIdle()

        assertEquals(1, dismissedCount)
    }

    @Test
    fun `re-show during the exit does not call onDismissComplete`() {
        showSheet()

        isVisible = false
        rule.mainClock.autoAdvance = false
        repeat(3) { rule.mainClock.advanceTimeByFrame() }
        isVisible = true
        rule.mainClock.autoAdvance = true
        rule.waitForIdle()

        assertEquals(0, dismissedCount)
    }

    @Test
    fun `first composition hidden calls onDismissComplete`() {
        showSheet(initial = false)

        assertEquals(1, dismissedCount)
        assertEquals(0, dismissCount)
    }
}
