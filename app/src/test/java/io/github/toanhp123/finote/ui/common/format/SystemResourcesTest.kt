package io.github.toanhp123.finote.ui.common.format

import io.github.toanhp123.finote.R
import io.github.toanhp123.finote.data.category.seed.SystemCategories
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemResourcesTest {
    @Test
    fun `every system key has a string`() {
        val keys = SystemCategories.map { it.nameKey } + "sys_cash"
        keys.forEach { assertNotNull("missing string for $it", systemNameRes(it)) }
    }

    @Test
    fun `every system icon has a drawable`() {
        val icons = (SystemCategories.map { it.icon } + "payments").distinct() - "more_horiz"
        icons.forEach { assertNotEquals("missing drawable for $it", R.drawable.ic_more_horiz, iconRes(it)) }
        assertEquals(R.drawable.ic_more_horiz, iconRes("more_horiz"))
    }

    @Test
    fun `unknown key returns null`() {
        assertNull(systemNameRes("sys_does_not_exist"))
    }

    @Test
    fun `unknown icon falls back to more_horiz`() {
        assertEquals(R.drawable.ic_more_horiz, iconRes("does_not_exist"))
    }

    @Test
    fun `every grouped icon resolves to its own drawable`() {
        val grouped = CategoryIconGroups.values.flatten() - "more_horiz"
        grouped.forEach { assertNotEquals("missing drawable for $it", R.drawable.ic_more_horiz, iconRes(it)) }
        assertEquals(grouped.size, grouped.map(::iconRes).distinct().size)
    }

    @Test
    fun `every seeded category icon is in a group`() {
        val grouped = CategoryIconGroups.values.flatten().toSet()
        SystemCategories.map { it.icon }.distinct().forEach { assertTrue("$it is in no group", it in grouped) }
    }

    @Test
    fun `no icon is in two groups`() {
        val all = CategoryIconGroups.values.flatten()
        assertEquals(
            all
                .groupingBy { it }
                .eachCount()
                .filterValues { it > 1 }
                .keys,
            emptySet<String>(),
        )
        assertEquals(CategoryIconGroup.entries.toSet(), CategoryIconGroups.keys)
        assertTrue("icon count ${all.size}", all.size in 60..80)
    }

    @Test
    fun `local_gas_station resolves`() {
        assertEquals(R.drawable.ic_local_gas_station, iconRes("local_gas_station"))
    }
}
