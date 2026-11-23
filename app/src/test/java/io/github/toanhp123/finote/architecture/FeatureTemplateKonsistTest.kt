package io.github.toanhp123.finote.architecture

import com.lemonappdev.konsist.api.declaration.KoBaseDeclaration
import com.lemonappdev.konsist.api.declaration.KoFileDeclaration
import com.lemonappdev.konsist.api.declaration.KoFunctionDeclaration
import com.lemonappdev.konsist.api.ext.list.classes
import com.lemonappdev.konsist.api.ext.list.functions
import com.lemonappdev.konsist.api.provider.KoContainingFileProvider
import com.lemonappdev.konsist.api.provider.KoNameProvider
import com.lemonappdev.konsist.api.provider.modifier.KoVisibilityModifierProvider
import com.lemonappdev.konsist.api.verify.assertTrue
import org.junit.Test

/** The layout of a `ui/<feature>/` package. The rules are in `docs/architecture.md`, section "Feature template". */
class FeatureTemplateKonsistTest {
    private val featureFiles = productionFiles.filter { it.packageName().isFeaturePackage() }
    private val screenFiles = featureFiles.filter { it.name.endsWith("Screen") }
    private val componentFiles = uiFiles { it.endsWith(".component") }
    private val logicFiles = uiFiles { it.endsWith(".logic") }

    @Test
    fun `feature roots hold only fixed roles`() {
        featureFiles.assertTrue(strict = false, testName = "feature roots hold only fixed roles") { file ->
            ROOT_SUFFIXES.any { file.name.endsWith(it) }
        }
    }

    @Test
    fun `screen files expose only the screen and its route`() {
        screenFiles
            .topLevelDeclarations()
            .assertTrue(strict = false, testName = "screen files expose only the screen and its route") {
                it.isPrivateDeclaration() || it.declarationName() in setOf(it.fileName(), it.routeName())
            }
    }

    @Test
    fun `dialogs and sheets live in component`() {
        featureFiles
            .topLevelComposables()
            .assertTrue(strict = false, testName = "dialogs and sheets live in component") {
                !it.name.endsWith("Dialog") && !it.name.endsWith("Sheet")
            }
    }

    @Test
    fun `component files declare one composable named like the file`() {
        componentFiles.assertTrue(
            strict = false,
            testName = "component files declare one composable named like the file",
        ) {
            listOf(it).topLevelComposables().filterNot { fn -> fn.isPrivateDeclaration() }.map { fn -> fn.name } ==
                listOf(it.name)
        }
    }

    @Test
    fun `logic files hold no compose`() {
        logicFiles.assertTrue(strict = false, testName = "logic files hold no compose") { file ->
            file.imports.none { it.name.startsWith("androidx.compose") } &&
                listOf(file).functions(includeNested = true, includeLocal = true).none {
                    it.hasAnnotationWithName("Composable")
                }
        }
    }

    @Test
    fun `feature composables are not internal`() {
        (screenFiles + componentFiles)
            .topLevelComposables()
            .assertTrue(strict = false, testName = "feature composables are not internal") { !it.hasInternalModifier }
    }

    @Test
    fun `view models extend BaseViewModel`() {
        filesIn(UI)
            .classes(includeNested = false, includeLocal = false)
            .filter { it.name.endsWith("ViewModel") && it.name != "BaseViewModel" }
            .assertTrue(strict = false, testName = "view models extend BaseViewModel") { type ->
                type.parents().any { it.name.baseTypeName() == "BaseViewModel" }
            }
    }

    @Test
    fun `feature ui starts no activity`() {
        (screenFiles + componentFiles)
            .assertTrue(strict = false, testName = "feature ui starts no activity") {
                !it.text.contains("startActivity") && !it.text.contains("Activity::class")
            }
    }

    private fun uiFiles(packageFilter: (String) -> Boolean) =
        productionFiles.filter {
            val pkg = it.packageName()
            pkg.inPackage(UI) && !pkg.inPackage(UI_COMMON) && !pkg.inPackage(UI_NAVIGATION) && packageFilter(pkg)
        }

    /** In `ui`, below it, outside `common` and `navigation`, and not a `component` or `logic` folder. */
    private fun String.isFeaturePackage() =
        inPackage(UI) &&
            this != UI &&
            !inPackage(UI_COMMON) &&
            !inPackage(UI_NAVIGATION) &&
            substringAfterLast('.') !in setOf("component", "logic")

    private fun List<KoFileDeclaration>.topLevelComposables() =
        topLevelDeclarations()
            .filterIsInstance<KoFunctionDeclaration>()
            .filter { it.hasAnnotationWithName("Composable") }

    private fun KoBaseDeclaration.declarationName() = (this as? KoNameProvider)?.name.orEmpty()

    private fun KoBaseDeclaration.isPrivateDeclaration() =
        (this as? KoVisibilityModifierProvider)?.hasPrivateModifier == true

    private fun KoBaseDeclaration.fileName() = (this as KoContainingFileProvider).containingFile.name

    /** `CashEntryScreen` is routed by `CashEntryRoute`. */
    private fun KoBaseDeclaration.routeName() = fileName().removeSuffix("Screen") + "Route"

    private companion object {
        val ROOT_SUFFIXES = listOf("Contract", "ViewModel", "Screen", "Activity", "TileService")
    }
}
