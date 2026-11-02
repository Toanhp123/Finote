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
        val isUiFeature = { name: String -> name.inPackage(UI) && !name.inPackage(UI_COMMON) }
        val testName = "ui common does not import ui features"
        filesIn(UI_COMMON).assertNoImports(testName) { isUiFeature(it.name) }
        filesIn(UI_COMMON).assertNoQualifiedNames("$testName, fully qualified", isUiFeature)
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
        val testName = "nothing imports the root package"
        productionFiles.assertNoImports(testName) { import ->
            (import.name == ROOT && import.isWildcard) || import.name.isRootClassName()
        }
        productionFiles.assertNoQualifiedNames("$testName, fully qualified") { it.isRootClassName() }
    }
}
