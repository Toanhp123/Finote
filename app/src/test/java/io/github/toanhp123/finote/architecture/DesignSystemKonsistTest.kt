package io.github.toanhp123.finote.architecture

import com.lemonappdev.konsist.api.declaration.KoFileDeclaration
import com.lemonappdev.konsist.api.verify.assertTrue
import org.junit.Test

class DesignSystemKonsistTest {
    private val rawStyle =
        Regex(
            """\bMaterialTheme\b|\bColor\(|\bColor\.(?!Transparent\b|Unspecified\b)[A-Z]\w*""" +
                """|\.dp\b|\.sp\b|\bTextStyle\(|\bRoundedCornerShape\(|\bBrush\.""",
        )
    private val manualFormat = Regex("""\bNumberFormat\b|\bDecimalFormat\b|\bString\.format\(|"\.format\(""")
    private val publicComposable = Regex("""@Composable\s+(?:public\s+)?fun\s+(?:<[^>]+>\s+)?[A-Z]""")
    private val allowedMaterial = setOf("androidx.compose.material3.Text", "androidx.compose.material3.Icon")
    private val wrappedLibraries = listOf("androidx.compose.material3", HAZE, UNSTYLED)

    private val featureUi = filesIn(UI).filterNot { it.packageName().inPackage(UI_COMMON) }

    @Test
    fun `features use only text and icon from material3 and no wrapped library`() {
        featureUi.assertNoImports("features use only text and icon from material3 and no wrapped library") { import ->
            import.name !in allowedMaterial && wrappedLibraries.any { import.name.inPackage(it) }
        }
    }

    @Test
    fun `only the theme writes raw style values`() {
        filesIn(UI)
            .filterNot { it.packageName().inPackage(UI_THEME) }
            .assertTrue(strict = false, testName = "only the theme writes raw style values") {
                !rawStyle.containsMatchIn(it.codeText())
            }
    }

    @Test
    fun `only the format package formats numbers`() {
        productionFiles
            .filterNot { it.packageName().inPackage(UI_FORMAT) }
            .assertTrue(strict = false, testName = "only the format package formats numbers") {
                !manualFormat.containsMatchIn(it.codeText())
            }
    }

    @Test
    fun `shared components carry finote previews`() {
        filesIn(UI_COMPONENT)
            .filter { publicComposable.containsMatchIn(it.text) }
            .assertTrue(strict = false, testName = "shared components carry finote previews") {
                it.text.contains("@FinotePreviews")
            }
    }

    @Test
    fun `shared components live in a role subpackage`() {
        val groups =
            listOf("control", "overlay", "layout", "display", "finance", "glass", "preview")
                .map { "$UI_COMPONENT.$it" }
        filesIn(UI_COMPONENT).assertTrue(testName = "shared components live in a role subpackage") { file ->
            groups.any { file.packageName() == it }
        }
    }

    /** File text without `package` and `import` lines; they name `dp` and `Color` legitimately. */
    private fun KoFileDeclaration.codeText(): String =
        text
            .lineSequence()
            .filterNot { line ->
                line.trimStart().let { it.startsWith("package ") || it.startsWith("import ") }
            }.joinToString("\n")

    private companion object {
        const val HAZE = "dev.chrisbanes.haze"
        const val UNSTYLED = "com.composeunstyled"
    }
}
