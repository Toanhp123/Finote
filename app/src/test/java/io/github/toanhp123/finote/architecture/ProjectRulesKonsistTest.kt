package io.github.toanhp123.finote.architecture

import com.lemonappdev.konsist.api.declaration.KoFileDeclaration
import com.lemonappdev.konsist.api.declaration.KoPropertyDeclaration
import com.lemonappdev.konsist.api.ext.list.classes
import com.lemonappdev.konsist.api.ext.list.functions
import com.lemonappdev.konsist.api.ext.list.properties
import com.lemonappdev.konsist.api.verify.assertTrue
import org.junit.Assert
import org.junit.Test

class ProjectRulesKonsistTest {
    private val loggingCall = Regex("""\bprintln\(|::println\b|\bprintStackTrace\b|\bSystem\.(out|err)\b""")
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
                        (it.isWildcard && it.name == "android.util") ||
                        it.isFrom("java.util.logging")
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
    fun `suppress only allowed detekt rules with a reason`() {
        productionFiles.assertTrue(
            strict = false,
            testName = "suppress only allowed detekt rules with a reason",
        ) { file ->
            file.text
                .lines()
                .filter { suppress.containsMatchIn(it) }
                .all { ALLOWED_SUPPRESS.containsMatchIn(it) }
        }
    }

    @Test
    fun `allowed suppress pattern`() {
        val ok =
            listOf(
                """@Suppress("LongMethod") // why: flat mapping of every enum entry""",
                """    @Suppress("LongMethod", "CyclomaticComplexMethod") // why: one state machine step""",
            )
        val bad =
            listOf(
                """@Suppress("LongMethod")""",
                """@Suppress("LongMethod") // why: short""",
                """@Suppress("konsist.layers") // why: needed for some reason""",
                """@file:Suppress("LongMethod") // why: whole file is a big table""",
                """@SuppressLint("NewApi") // why: checked at runtime already""",
                """@Suppress("MagicNumber") // why: numbers are fine right here""",
            )
        Assert.assertTrue(ok.all { ALLOWED_SUPPRESS.containsMatchIn(it) })
        Assert.assertTrue(bad.none { ALLOWED_SUPPRESS.containsMatchIn(it) })
    }

    @Test
    fun `only HttpParserRuleFeed uses network packages`() {
        productionFiles
            .filter { it.name != "HttpParserRuleFeed" }
            .assertNoImports("only HttpParserRuleFeed uses network packages") { import ->
                NETWORK_PACKAGES.any { import.isFrom(it) }
            }
    }

    @Test
    fun `notification text holders hide it`() {
        val files = filesIn("$DOMAIN.notification")
        Assert.assertTrue(files.isNotEmpty())
        val holders =
            files
                .classes(includeNested = true)
                .filter { it.hasDataModifier && it.properties().any { p -> p.name in TEXT_PROPERTIES } }
        Assert.assertTrue(holders.isNotEmpty())
        holders
            .assertTrue(strict = false, testName = "notification text holders hide it") { holder ->
                holder.functions().any { it.name == "toString" && it.hasOverrideModifier }
            }
    }

    @Test
    fun `write transactions open only in data database`() {
        productionFiles
            .filterNot { it.packageName().inPackage(DATA_DATABASE) }
            .assertTrue(strict = false, testName = "write transactions open only in data database") { file ->
                file.codeLines().none { it.contains("withWriteTransaction") }
            }
    }

    @Test
    fun `storage exceptions are created only in data database`() {
        productionFiles
            .filterNot { it.packageName().inPackage(DATA_DATABASE) }
            .filterNot { it.packageName() == DOMAIN_COMMON && it.name == "StorageException" }
            .assertTrue(strict = false, testName = "storage exceptions are created only in data database") { file ->
                file.codeLines().none { it.contains("StorageException(") }
            }
    }

    private fun KoFileDeclaration.codeLines() = text.lineSequence().filterNot { it.trimStart().startsWith("import ") }

    private fun String.isMoneyName() = nameWords().any { it in MONEY_WORDS || it.removeSuffix("s") in MONEY_WORDS }

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
        const val RULE = "(LongMethod|CyclomaticComplexMethod|LongParameterList)"

        // Only detekt size rules, never Konsist or Lint, and always with a reason of 10+ chars.
        val ALLOWED_SUPPRESS = Regex("""^\s*@Suppress\("$RULE"(, "$RULE")*\)\s*// why: \S.{9,}""")

        val TEXT_PROPERTIES = setOf("title", "content", "accountMask", "mask", "postBalance", "merchant", "note")

        val NETWORK_PACKAGES =
            listOf("java.net", "javax.net", "android.net.http", "android.webkit", "okhttp3", "io.ktor")

        val MONEY_WORDS =
            setOf(
                "amount",
                "balance",
                "limit",
                "fee",
                "price",
                "cost",
                "total",
                "debt",
                "income",
                "expense",
                "spent",
                "budget",
                "difference",
                "drift",
                "delta",
            )
    }
}
