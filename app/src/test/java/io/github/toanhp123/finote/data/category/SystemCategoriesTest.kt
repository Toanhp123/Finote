package io.github.toanhp123.finote.data.category

import androidx.compose.ui.graphics.toArgb
import io.github.toanhp123.finote.domain.category.model.CategoryType
import io.github.toanhp123.finote.ui.common.theme.CategoryPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemCategoriesTest {
    private val byId = SystemCategories.associateBy { it.id }

    @Test
    fun `there are 47 rows with unique ids`() {
        assertEquals(47, SystemCategories.size)
        assertEquals(47, SystemCategories.map { it.id }.toSet().size)
    }

    @Test
    fun `ids are lowercase snake case with sys prefix`() {
        val pattern = Regex("^sys_[a-z_]+$")
        assertTrue(SystemCategories.all { pattern.matches(it.id) })
    }

    @Test
    fun `every parent exists, is top level, appears earlier and has the same type`() {
        SystemCategories.forEachIndexed { index, row ->
            val parentId = row.parentId ?: return@forEachIndexed
            val parent = byId.getValue(parentId)
            assertTrue(row.id, SystemCategories.indexOf(parent) < index)
            assertEquals(row.id, null, parent.parentId)
            assertEquals(row.id, parent.type, row.type)
        }
    }

    @Test
    fun `income rows are top level`() {
        assertTrue(SystemCategories.filter { it.type == CategoryType.INCOME }.all { it.parentId == null })
    }

    @Test
    fun `sortOrder is 0 based within siblings`() {
        // Top-level rows are siblings per type; children are siblings per parent.
        SystemCategories.groupBy { it.parentId ?: it.type.name }.forEach { (_, rows) ->
            assertEquals(rows.indices.toList(), rows.map { it.sortOrder })
        }
    }

    @Test
    fun `children share icon and color of their parent`() {
        SystemCategories.filter { it.parentId != null }.forEach { child ->
            val parent = byId.getValue(child.parentId.orEmpty())
            assertEquals(child.id, parent.icon, child.icon)
            assertEquals(child.id, parent.colorArgb, child.colorArgb)
        }
    }

    @Test
    fun `every color is in CategoryPalette`() {
        val palette = CategoryPalette.map { it.toArgb() }.toSet()
        SystemCategories.forEach { assertTrue(it.id, it.colorArgb in palette) }
    }

    @Test
    fun `spot check matches spec`() {
        val taxi = byId.getValue("sys_transport_taxi")
        assertEquals("Taxi & Ride-hailing", taxi.name)
        assertEquals("sys_transport", taxi.parentId)
        val investment = byId.getValue("sys_income_investment")
        assertEquals("trending_up", investment.icon)
        assertEquals(0xFF0277BD.toInt(), investment.colorArgb)
    }
}
