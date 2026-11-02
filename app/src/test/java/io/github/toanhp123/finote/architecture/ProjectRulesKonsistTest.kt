package io.github.toanhp123.finote.architecture

import com.lemonappdev.konsist.api.declaration.KoPropertyDeclaration
import com.lemonappdev.konsist.api.ext.list.classes
import com.lemonappdev.konsist.api.ext.list.functions
import com.lemonappdev.konsist.api.ext.list.properties
import com.lemonappdev.konsist.api.verify.assertTrue
import org.junit.Test

class ProjectRulesKonsistTest {
    private val loggingCall = Regex("""\bprintln\(|::println\b|\bprintStackTrace\b""")
    private val suppress = Regex("""\bSuppress(Lint|Warnings)?\b""")
    private val floatingType = Regex("""\b(Double|Float)\b""")
    private val initializer = Regex("""\b(?:val|var)\b[^=]*=(.*)""", RegexOption.DOT_MATCHES_ALL)
    private val floatingLiteral = """\d[\d_]*\.\d[\d_]*([eE][-+]?\d+)?[fF]?|\d[\d_]*[eE][-+]?\d+[fF]?|\d[\d_]*[fF]"""
    private val floatingInitializer = Regex("""(?s)[-+]?($floatingLiteral)|.*\.to(Double|Float)\(\)""")

    @Test
    fun `production code never logs`() {
        productionFiles.assertTrue(strict = false, testName = "production code never logs") { file ->
            val importsLog =
                file.imports.any {
                    it.isFrom("android.util.Log") ||
                        (it.isWildcard && it.name == "android.util")
                }
            !importsLog && !file.hasTextContaining("android.util.Log") && !loggingCall.containsMatchIn(file.text)
        }
    }

    @Test
    fun `money is never floating point`() {
        productionFiles
            .properties(includeNested = true)
            .filter { it.name.isMoneyName() }
            .assertTrue(strict = false, testName = "money is never floating point") { !it.isFloating() }
    }

    // A separate test: assertTrue throws on the first failing list, which would hide the other list.
    @Test
    fun `money parameters are never floating point`() {
        val functionParameters =
            productionFiles
                .functions(includeNested = true, includeLocal = true)
                .flatMap { it.parameters }
        val constructorParameters =
            productionFiles
                .classes(includeNested = true, includeLocal = true)
                .flatMap { it.constructors }
                .flatMap { it.parameters }
        (functionParameters + constructorParameters)
            .filter { it.name.isMoneyName() }
            .assertTrue(strict = false, testName = "money parameters are never floating point") {
                !floatingType.containsMatchIn(it.type.name)
            }
    }

    @Test
    fun `no floating point type aliases`() {
        productionFiles
            .flatMap { it.typeAliases }
            .assertTrue(strict = false, testName = "no floating point type aliases") {
                !floatingType.containsMatchIn(it.text.substringAfter('='))
            }
    }

    @Test
    fun `suppress is forbidden`() {
        productionFiles.assertTrue(strict = false, testName = "suppress is forbidden") {
            !suppress.containsMatchIn(it.text)
        }
    }

    private fun String.isMoneyName() = MONEY_WORDS.any { contains(it, ignoreCase = true) }

    /** A declared type is checked with its type arguments; without one, the initializer must not be floating. */
    private fun KoPropertyDeclaration.isFloating(): Boolean {
        val declared = type
        return if (declared != null) {
            floatingType.containsMatchIn(declared.name)
        } else {
            floatingInitializer.matches(initializerText())
        }
    }

    /** Konsist's `value` drops the quotes of a string, so `"1.5"` would look like a float. Read the source text. */
    private fun KoPropertyDeclaration.initializerText(): String =
        initializer
            .find(text)
            ?.groupValues
            ?.get(1)
            ?.substringBefore("//")
            ?.trim()
            ?.trimEnd(';')
            .orEmpty()

    private companion object {
        val MONEY_WORDS = listOf("amount", "balance", "limit", "fee")
    }
}
