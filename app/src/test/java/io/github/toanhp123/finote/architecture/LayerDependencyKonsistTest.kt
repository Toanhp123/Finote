package io.github.toanhp123.finote.architecture

import com.lemonappdev.konsist.api.verify.assertTrue
import org.junit.Test

class LayerDependencyKonsistTest {
    @Test
    fun `domain has no framework imports`() {
        filesIn(DOMAIN).assertNoImports("domain has no framework imports") { import ->
            import.isFrom("android") ||
                import.isFrom("androidx") ||
                import.isFrom("dagger") ||
                import.name.hasSegment("room")
        }
    }

    @Test
    fun `domain does not import outer layers`() {
        assertLayerDoesNotImport("domain does not import outer layers", filesIn(DOMAIN), DATA, UI, AUTOMATION, WORK, DI)
    }

    @Test
    fun `every package belongs to a layer`() {
        val layers = listOf(DOMAIN, DATA, UI, AUTOMATION, WORK, DI)
        productionFiles.assertTrue(strict = false, testName = "every package belongs to a layer") { file ->
            val pkg = file.packageName()
            pkg == ROOT || layers.any { pkg.inPackage(it) }
        }
    }

    @Test
    fun `data does not import outer layers`() {
        assertLayerDoesNotImport("data does not import outer layers", filesIn(DATA), UI, AUTOMATION, WORK)
    }

    @Test
    fun `ui does not import data, automation or work`() {
        assertLayerDoesNotImport("ui does not import data, automation or work", filesIn(UI), DATA, AUTOMATION, WORK)
    }

    @Test
    fun `automation does not import ui, data or work`() {
        assertLayerDoesNotImport("automation does not import ui, data or work", filesIn(AUTOMATION), UI, DATA, WORK)
    }

    @Test
    fun `work does not import ui, data or automation`() {
        assertLayerDoesNotImport("work does not import ui, data or automation", filesIn(WORK), UI, DATA, AUTOMATION)
    }

    @Test
    fun `ui common does not import ui features`() {
        filesIn(UI_COMMON).assertNoImports("ui common does not import ui features") { import ->
            import.isFrom(UI) && !import.isFrom(UI_COMMON)
        }
    }

    @Test
    fun `root package holds only app entry points`() {
        val entryPoints = setOf("MainActivity.kt", "FinoteApplication.kt")
        productionFiles
            .filter { it.packageName() == ROOT }
            .assertTrue(strict = false, testName = "root package holds only app entry points") {
                it.nameWithExtension in entryPoints
            }
    }

    @Test
    fun `nothing imports the root package`() {
        // R and BuildConfig are generated into the root package, so importing them is fine.
        val generated = setOf("R", "BuildConfig")
        productionFiles.assertNoImports("nothing imports the root package") { import ->
            val rest = import.name.removePrefix("$ROOT.")
            val importsRootWildcard = import.name == ROOT && import.isWildcard
            val importsRootClass =
                import.name != rest && rest.first().isUpperCase() &&
                    rest.substringBefore('.') !in generated
            importsRootWildcard || importsRootClass
        }
    }
}
