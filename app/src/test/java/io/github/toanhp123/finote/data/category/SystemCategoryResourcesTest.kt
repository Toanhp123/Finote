package io.github.toanhp123.finote.data.category

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class SystemCategoryResourcesTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun localized(tag: String): Context {
        val config = Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag(tag)) }
        return context.createConfigurationContext(config)
    }

    @Test
    fun `every icon has a drawable`() {
        SystemCategories.forEach {
            val id = context.resources.getIdentifier("ic_${it.icon}", "drawable", context.packageName)
            assertTrue(it.icon, id != 0)
        }
    }

    @Test
    fun `every nameKey resolves in all four locales`() {
        listOf("vi", "en", "zh-CN", "ja").forEach { tag ->
            val localizedContext = localized(tag)
            SystemCategories.forEach {
                val id = localizedContext.resources.getIdentifier(it.nameKey, "string", context.packageName)
                assertTrue("${it.nameKey} missing", id != 0)
                assertTrue("${it.nameKey} empty in $tag", localizedContext.getString(id).isNotEmpty())
            }
        }
    }

    @Test
    fun `locales do not fall back to the default strings`() {
        val en = localized("en").getString(stringId("sys_food"))
        val ja = localized("ja").getString(stringId("sys_food"))
        assertNotEquals(en, ja)
    }

    private fun stringId(name: String) = context.resources.getIdentifier(name, "string", context.packageName)
}
