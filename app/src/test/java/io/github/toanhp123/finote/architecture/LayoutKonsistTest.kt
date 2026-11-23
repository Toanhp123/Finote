package io.github.toanhp123.finote.architecture

import com.lemonappdev.konsist.api.declaration.KoBaseDeclaration
import com.lemonappdev.konsist.api.declaration.KoClassDeclaration
import com.lemonappdev.konsist.api.declaration.KoFileDeclaration
import com.lemonappdev.konsist.api.declaration.KoInterfaceDeclaration
import com.lemonappdev.konsist.api.declaration.KoObjectDeclaration
import com.lemonappdev.konsist.api.ext.list.classes
import com.lemonappdev.konsist.api.verify.assertTrue
import org.junit.Test

class LayoutKonsistTest {
    private val domainRolePackage = featureRolePackage(DOMAIN, DOMAIN_ROLES)
    private val dataRolePackage = featureRolePackage(DATA, DATA_ROLES)
    private val modelPackage = Regex("""^${Regex.escape(DOMAIN)}\.[^.]+\.model$""")
    private val portPackage = Regex("""^${Regex.escape(DOMAIN)}\.[^.]+\.port$""")
    private val servicePackage = Regex("""^${Regex.escape(DOMAIN)}\.[^.]+\.service$""")
    private val errorPackage = Regex("""^${Regex.escape(DOMAIN)}\.[^.]+\.error$""")

    @Test
    fun `domain feature files sit in a role folder`() {
        filesIn(DOMAIN)
            .filterNot { it.packageName().inPackage(DOMAIN_COMMON) }
            .assertTrue(strict = false, testName = "domain feature files sit in a role folder") {
                domainRolePackage.matches(it.packageName())
            }
    }

    @Test
    fun `data feature files sit in a role folder`() {
        filesIn(DATA)
            .filterNot { it.packageName().inPackage(DATA_DATABASE) || it.packageName().inPackage(DATA_SECURITY) }
            .assertTrue(strict = false, testName = "data feature files sit in a role folder") {
                dataRolePackage.matches(it.packageName())
            }
    }

    @Test
    fun `models declare no throwables`() {
        productionFiles
            .filter { modelPackage.matches(it.packageName()) }
            .classes(includeNested = false, includeLocal = false)
            .assertTrue(strict = false, testName = "models declare no throwables") { type ->
                type.parents().none { it.name.endsWith("Exception") || it.name.endsWith("Throwable") }
            }
    }

    @Test
    fun `ports are interfaces`() {
        productionFiles
            .filter { portPackage.matches(it.packageName()) }
            .topLevelDeclarations()
            .assertTrue(strict = false, testName = "ports are interfaces") { it is KoInterfaceDeclaration }
    }

    @Test
    fun `error folders hold sealed errors`() {
        productionFiles
            .filter { errorPackage.matches(it.packageName()) }
            .assertTrue(strict = false, testName = "error folders hold sealed errors") { it.holdsOnlySealedErrors() }
    }

    @Test
    fun `services do not reach repositories or ports`() {
        val services = productionFiles.filter { servicePackage.matches(it.packageName()) }
        services.assertNoImports("services do not reach repositories or ports") {
            it.name.hasSegment("repository") || it.name.hasSegment("port")
        }
        services.assertNoQualifiedNames("services do not reach repositories or ports, fully qualified") {
            it.hasSegment("repository") || it.hasSegment("port")
        }
    }

    @Test
    fun `data roles hold their suffixes`() {
        assertTypesIn("data roles hold their suffixes, dao", "local.dao") { it.endsWith("Dao") }
        assertTypesIn("data roles hold their suffixes, entity", "local.entity") { name ->
            name.endsWith("Entity") || name == "TransactionTagCrossRef"
        }
        assertTypesIn("data roles hold their suffixes, repository", "repository") { it.endsWith("RepositoryImpl") }
        assertTypesIn("data roles hold their suffixes, seed", "seed") { it.endsWith("Seeder") }
        productionFiles
            .filter { it.name.endsWith("Mapper") }
            .assertTrue(strict = false, testName = "data roles hold their suffixes, mapper") {
                featureRolePackage(DATA, setOf("mapper")).matches(it.packageName())
            }
    }

    /**
     * Top level: a sealed interface or class, or an enum class. Any other type must extend a sealed type of the same
     * file. A function, property or alias there fails.
     */
    private fun KoFileDeclaration.holdsOnlySealedErrors(): Boolean {
        val types = listOf(this).topLevelDeclarations().map { it.asErrorType() ?: return false }
        val sealedNames = types.filter { it.isSealedOrEnum }.map { it.name }.toSet()
        return types.all { it.isSealedOrEnum || it.parents.any { parent -> parent in sealedNames } }
    }

    private class ErrorType(
        val name: String,
        val isSealedOrEnum: Boolean,
        val parents: List<String>,
    )

    private fun KoBaseDeclaration.asErrorType(): ErrorType? =
        when (this) {
            is KoInterfaceDeclaration -> {
                ErrorType(name, hasSealedModifier, parents().map { it.name.baseTypeName() })
            }

            is KoClassDeclaration -> {
                ErrorType(name, hasSealedModifier || hasEnumModifier, parents().map { it.name.baseTypeName() })
            }

            is KoObjectDeclaration -> {
                ErrorType(name, false, parents().map { it.name.baseTypeName() })
            }

            else -> {
                null
            }
        }

    /** Every top-level type accepted by [hasSuffix] must sit in `data.<feature>.<role>`. One-way check. */
    private fun assertTypesIn(
        testName: String,
        role: String,
        hasSuffix: (String) -> Boolean,
    ) {
        val rolePackage = featureRolePackage(DATA, setOf(role))
        productionFiles
            .topLevelTypes()
            .filter { hasSuffix(it.name) }
            .assertTrue(strict = false, testName = testName) { rolePackage.matches(it.containingFile.packageName()) }
    }

    private fun featureRolePackage(
        layer: String,
        roles: Set<String>,
    ) = Regex("""^${Regex.escape(layer)}\.[^.]+\.(${roles.joinToString("|") { Regex.escape(it) }})$""")
}
