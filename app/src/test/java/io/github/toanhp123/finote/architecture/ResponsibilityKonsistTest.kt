package io.github.toanhp123.finote.architecture

import com.lemonappdev.konsist.api.declaration.KoFileDeclaration
import com.lemonappdev.konsist.api.declaration.KoFunctionDeclaration
import com.lemonappdev.konsist.api.ext.list.functions
import com.lemonappdev.konsist.api.provider.KoNameProvider
import com.lemonappdev.konsist.api.verify.assertTrue
import org.junit.Test

class ResponsibilityKonsistTest {
    @Test
    fun `use cases expose only invoke`() {
        productionFiles
            .topLevelTypes()
            .filter { it.name.endsWith("UseCase") }
            .assertTrue(strict = false, testName = "use cases expose only invoke") { useCase ->
                val publicFunctions =
                    useCase
                        .functions(includeNested = false, includeLocal = false)
                        .filter { it.hasPublicOrDefaultModifier }
                publicFunctions.singleOrNull()?.let { it.name == "invoke" && it.hasOperatorModifier } == true
            }
    }

    @Test
    fun `mapper files hold only mapping extensions`() {
        val mappingNames = setOf("toDomain", "toEntity")
        productionFiles
            .named("Mapper.kt")
            .topLevelDeclarations()
            .assertTrue(strict = false, testName = "mapper files hold only mapping extensions") {
                it is KoFunctionDeclaration && it.isExtension && it.name in mappingNames
            }
    }

    @Test
    fun `contract files declare state intent effect`() {
        productionFiles
            .named("Contract.kt")
            .assertTrue(strict = false, testName = "contract files declare state intent effect") { file ->
                val names =
                    listOf(file)
                        .topLevelDeclarations()
                        .map { (it as? KoNameProvider)?.name.orEmpty() }
                listOf("State", "Intent", "Effect").all { suffix ->
                    names.count { it.endsWith(suffix) } == 1
                }
            }
    }

    @Test
    fun `screens do not touch use cases`() {
        productionFiles
            .named("Screen.kt")
            .assertNoImports("screens do not touch use cases") { it.name.hasSegment("usecase") }
    }

    @Test
    fun `view models do not import compose ui`() {
        productionFiles
            .named("ViewModel.kt")
            .assertNoImports("view models do not import compose ui") { it.isFrom("androidx.compose") }
    }

    @Test
    fun `files stay under 400 lines`() {
        productionFiles.assertTrue(strict = false, testName = "files stay under 400 lines") {
            it.text.lineCount() <= MAX_FILE_LINES
        }
    }

    @Test
    fun `composables stay under 80 lines and screens under 120`() {
        productionFiles
            .functions(includeNested = true, includeLocal = false)
            .filter { it.hasAnnotationWithName("Composable") }
            .assertTrue(strict = false, testName = "composables stay under 80 lines and screens under 120") {
                val limit = if (it.name.endsWith("Screen")) MAX_SCREEN_LINES else MAX_COMPOSABLE_LINES
                it.text.lineCount() <= limit
            }
    }

    @Test
    fun `core files hold one public type`() {
        productionFiles
            .filter { file -> CORE_SUFFIXES.any { file.name.endsWith(it) } }
            .assertTrue(strict = false, testName = "core files hold one public type") { file ->
                listOf(file).topLevelTypes().count { it.hasPublicOrDefaultModifier } <= 1
            }
    }

    private fun List<KoFileDeclaration>.named(suffix: String) = filter { it.nameWithExtension.endsWith(suffix) }

    private companion object {
        const val MAX_FILE_LINES = 400
        const val MAX_COMPOSABLE_LINES = 80
        const val MAX_SCREEN_LINES = 120

        // Files that own one role. Other files may co-locate closely related types.
        val CORE_SUFFIXES = listOf("UseCase", "RepositoryImpl", "ViewModel", "Screen", "Dao", "Entity")
    }
}
