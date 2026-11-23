package io.github.toanhp123.finote.architecture

import com.lemonappdev.konsist.api.declaration.KoInterfaceDeclaration
import com.lemonappdev.konsist.api.ext.list.functions
import com.lemonappdev.konsist.api.ext.list.properties
import com.lemonappdev.konsist.api.verify.assertTrue
import org.junit.Test

class NamingKonsistTest {
    private val useCasePackage = Regex("""^${Regex.escape(DOMAIN)}\.[^.]+\.usecase(\..*)?$""")
    private val repositoryPackage = Regex("""^${Regex.escape(DOMAIN)}\.[^.]+\.repository(\..*)?$""")
    private val booleanName = Regex("""^(is|has|should)[A-Z0-9].*""")

    @Test
    fun `use cases live in usecase`() {
        productionFiles
            .topLevelTypes()
            .assertTrue(strict = false, testName = "use cases live in usecase") {
                it.name.endsWith("UseCase") == useCasePackage.matches(it.containingFile.packageName())
            }
    }

    @Test
    fun `repository package holds only repository interfaces`() {
        productionFiles
            .filter { repositoryPackage.matches(it.packageName()) }
            .topLevelDeclarations()
            .assertTrue(strict = false, testName = "repository package holds only repository interfaces") {
                it is KoInterfaceDeclaration && it.name.endsWith("Repository")
            }
    }

    @Test
    fun `repositories are interfaces in domain repository`() {
        productionFiles
            .topLevelTypes()
            .filter { it.name.endsWith("Repository") }
            .assertTrue(strict = false, testName = "repositories are interfaces in domain repository") {
                it is KoInterfaceDeclaration && repositoryPackage.matches(it.containingFile.packageName())
            }
    }

    @Test
    fun `repository impls live in data`() {
        assertSuffixLivesIn("repository impls live in data", DATA, "RepositoryImpl")
    }

    @Test
    fun `entities and daos live in data`() {
        assertSuffixLivesIn("entities and daos live in data", DATA, "Entity", "Dao")
    }

    @Test
    fun `view models live in ui`() {
        assertSuffixLivesIn("view models live in ui", UI, "ViewModel")
    }

    @Test
    fun `workers live in work`() {
        assertSuffixLivesIn("workers live in work", WORK, "Worker")
    }

    @Test
    fun `mappers live in data`() {
        assertFileSuffixLivesIn("mappers live in data", DATA, "Mapper")
    }

    @Test
    fun `contracts and screens live in ui`() {
        assertFileSuffixLivesIn("contracts and screens live in ui", UI, "Contract", "Screen")
    }

    @Test
    fun `hilt modules live in di`() {
        // The Module suffix and @Module go together and belong in di; other types in di, such as a @Qualifier, pass.
        productionFiles
            .topLevelTypes()
            .assertTrue(strict = false, testName = "hilt modules live in di") {
                val inDi = it.containingFile.packageName().inPackage(DI)
                val hasSuffix = it.name.endsWith("Module")
                val hasAnnotation = it.hasAnnotationWithName("Module")
                (!hasSuffix && !hasAnnotation) || (hasSuffix && hasAnnotation && inDi)
            }
    }

    @Test
    fun `flow functions start with observe`() {
        // A use case exposes its flow through `operator fun invoke`, so invoke is exempt.
        // A Flow extension operator transforms a stream, it is not a source, so it is exempt too.
        (filesIn(DOMAIN) + filesIn(DATA))
            .functions(includeNested = true, includeLocal = false)
            .filter {
                it.name != "invoke" &&
                    it.receiverType?.name?.baseTypeName() != "Flow" &&
                    it.returnType?.name?.baseTypeName() == "Flow"
            }.assertTrue(strict = false, testName = "flow functions start with observe") {
                it.name.startsWith("observe")
            }
    }

    @Test
    fun `boolean properties use a prefix`() {
        // An override cannot be renamed, so overrides are left to the declaration they implement.
        productionFiles
            .properties(includeNested = true)
            .filter { !it.hasOverrideModifier && !it.hasConstModifier && it.type?.name?.baseTypeName() == "Boolean" }
            .assertTrue(strict = false, testName = "boolean properties use a prefix") {
                booleanName.matches(it.name)
            }
    }

    private fun assertFileSuffixLivesIn(
        testName: String,
        pkg: String,
        vararg suffixes: String,
    ) {
        productionFiles
            .filter { file -> suffixes.any { file.name.endsWith(it) } }
            .assertTrue(strict = false, testName = testName) { it.packageName().inPackage(pkg) }
    }

    private fun assertSuffixLivesIn(
        testName: String,
        pkg: String,
        vararg suffixes: String,
    ) {
        productionFiles
            .topLevelTypes()
            .filter { type -> suffixes.any { type.name.endsWith(it) } }
            .assertTrue(strict = false, testName = testName) { it.containingFile.packageName().inPackage(pkg) }
    }
}
