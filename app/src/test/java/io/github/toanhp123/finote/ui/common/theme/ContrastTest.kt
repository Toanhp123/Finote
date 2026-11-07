package io.github.toanhp123.finote.ui.common.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import io.github.toanhp123.finote.ui.common.theme.color.CategoryPalette
import io.github.toanhp123.finote.ui.common.theme.color.DarkBrandScheme
import io.github.toanhp123.finote.ui.common.theme.color.LightBrandScheme
import io.github.toanhp123.finote.ui.common.theme.color.categoryIconColors
import io.github.toanhp123.finote.ui.common.theme.color.finoteColors
import org.junit.Assert.assertTrue
import org.junit.Test

class ContrastTest {
    private val modes = listOf(false to LightBrandScheme, true to DarkBrandScheme)

    @Test
    fun `money colors read on cards`() =
        modes.forEach { (isDark, scheme) ->
            val colors = finoteColors(scheme, isDark)
            mapOf("income" to colors.income, "expense" to colors.expense, "transfer" to colors.transfer)
                .forEach { (name, role) -> assertContrast(TEXT, role.color, colors.card, "$name dark=$isDark") }
        }

    @Test
    fun `hero text reads on both gradient ends`() =
        modes.forEach { (isDark, scheme) ->
            val hero = finoteColors(scheme, isDark).hero
            assertContrast(TEXT, hero.onHero, hero.start, "hero start dark=$isDark")
            assertContrast(TEXT, hero.onHero, hero.end, "hero end dark=$isDark")
        }

    @Test
    fun `category icons read on their tiles`() =
        modes.forEach { (isDark, scheme) ->
            val card = finoteColors(scheme, isDark).card
            CategoryPalette.forEachIndexed { index, color ->
                val tile = categoryIconColors(color.toArgb(), isDark)
                assertContrast(GRAPHIC, tile.icon, tile.container.compositeOver(card), "palette[$index] dark=$isDark")
            }
        }

    @Test
    fun `palette holds 12 to 16 distinct colors`() {
        assertTrue(CategoryPalette.size in 12..16)
        assertTrue(CategoryPalette.distinct().size == CategoryPalette.size)
    }

    private fun assertContrast(
        min: Float,
        fg: Color,
        bg: Color,
        label: String,
    ) {
        val (hi, lo) = listOf(fg.luminance() + 0.05f, bg.luminance() + 0.05f).sortedDescending()
        val ratio = hi / lo
        assertTrue("$label contrast $ratio < $min", ratio >= min)
    }

    private companion object {
        const val TEXT = 4.5f
        const val GRAPHIC = 3f
    }
}
