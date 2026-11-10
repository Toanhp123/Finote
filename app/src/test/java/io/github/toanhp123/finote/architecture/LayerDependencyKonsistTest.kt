package io.github.toanhp123.finote.architecture

import com.lemonappdev.konsist.api.declaration.KoClassDeclaration
import com.lemonappdev.konsist.api.ext.list.classes
import com.lemonappdev.konsist.api.ext.list.functions
import com.lemonappdev.konsist.api.verify.assertTrue
import org.junit.Assert
import org.junit.Test

class LayerDependencyKonsistTest {
    @Test
    fun `domain imports only allowed packages`() {
        val files = filesIn(DOMAIN)
        Assert.assertTrue(files.isNotEmpty())
        files.assertNoImports("domain imports only allowed packages") { import ->
            DOMAIN_ALLOWED_IMPORTS.none { import.isFrom(it) }
        }
    }

    @Test
    fun `automation and work do not use repositories`() {
        val files = filesIn(AUTOMATION) + filesIn(WORK)
        Assert.assertTrue(files.isNotEmpty())
        files.assertNoImports("automation and work do not use repositories") {
            it.name.substringAfterLast('.').endsWith("Repository") ||
                (it.isWildcard && it.name.hasSegment("repository"))
        }
    }

    @Test
    fun `di holds only bindings`() {
        val files = filesIn(DI)
        Assert.assertTrue(files.isNotEmpty())
        val types = files.topLevelTypes()
        types.assertTrue(strict = false, testName = "di holds only bindings, top-level types") { type ->
            type.hasAnnotationWithName("Module") ||
                (type is KoClassDeclaration && type.hasAnnotationModifier && type.hasAnnotationWithName("Qualifier"))
        }
        types
            .filter { it.hasAnnotationWithName("Module") }
            .functions(includeNested = false, includeLocal = false)
            .assertTrue(strict = false, testName = "di holds only bindings, module functions") {
                it.hasAnnotationWithName("Provides") || it.hasAnnotationWithName("Binds")
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
    fun `view models do not use repositories`() {
        val testName = "view models do not use repositories"
        productionFiles
            .filter { it.name.endsWith("ViewModel") }
            .assertNoImports(testName) { it.name.hasSegment("repository") }
        productionFiles
            .classes(includeNested = false, includeLocal = false)
            .filter { it.name.endsWith("ViewModel") }
            .assertTrue(strict = false, testName = "$testName, constructor") { viewModel ->
                viewModel.primaryConstructor
                    ?.parameters
                    .orEmpty()
                    .none { it.type.name.endsWith("Repository") }
            }
    }

    @Test
    fun `ui does not use the database`() {
        filesIn(UI).assertNoImports("ui does not use the database") {
            it.isFrom("android.database") || it.isFrom("androidx.sqlite")
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
        val testName = "nothing imports the root package"
        productionFiles.assertNoImports(testName) { import ->
            (import.name == ROOT && import.isWildcard) || import.name.isRootClassName()
        }
        productionFiles.assertNoQualifiedNames("$testName, fully qualified") { it.isRootClassName() }
    }

    private companion object {
        val DOMAIN_ALLOWED_IMPORTS =
            listOf(
                "kotlin",
                "kotlinx.coroutines",
                "java.time",
                "java.util",
                "java.text",
                "java.security",
                "java.math",
                "javax.inject",
                DOMAIN,
            )
    }
}
