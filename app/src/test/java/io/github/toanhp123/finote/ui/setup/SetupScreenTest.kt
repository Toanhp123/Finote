package io.github.toanhp123.finote.ui.setup

import android.content.Context
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.toanhp123.finote.R
import io.github.toanhp123.finote.ui.common.theme.FinoteTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [37])
class SetupScreenTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `the granted row is a polite live region`() {
        val granted = ApplicationProvider.getApplicationContext<Context>().getString(R.string.setup_granted)
        rule.setContent {
            FinoteTheme(dynamicColor = false) {
                SetupScreen(state = SetupState(step = SetupStep.GRANTED), onIntent = {})
            }
        }

        rule
            .onNodeWithText(granted)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
    }
}
