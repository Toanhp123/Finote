package io.github.toanhp123.finote.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.declaration.KoFileDeclaration
import com.lemonappdev.konsist.api.declaration.KoImportDeclaration
import com.lemonappdev.konsist.api.declaration.KoPackageDeclaration
import com.lemonappdev.konsist.api.ext.list.classesAndInterfacesAndObjects
import com.lemonappdev.konsist.api.ext.list.declarations
import com.lemonappdev.konsist.api.verify.assertTrue

internal const val ROOT = "io.github.toanhp123.finote"
internal const val DOMAIN = "$ROOT.domain"
internal const val DATA = "$ROOT.data"
internal const val DOMAIN_COMMON = "$ROOT.domain.common"
internal const val DATA_DATABASE = "$ROOT.data.database"
internal const val DATA_SECURITY = "$ROOT.data.security"
internal const val UI = "$ROOT.ui"
internal const val UI_COMMON = "$ROOT.ui.common"
internal const val UI_THEME = "$ROOT.ui.common.theme"
internal const val UI_COMPONENT = "$ROOT.ui.common.component"
internal const val UI_FORMAT = "$ROOT.ui.common.format"
internal const val AUTOMATION = "$ROOT.automation"
internal const val WORK = "$ROOT.work"
internal const val DI = "$ROOT.di"

/** Role folders allowed below a feature: `domain/<feature>/<role>` and `data/<feature>/<role>`. */
internal val DOMAIN_ROLES = setOf("model", "error", "repository", "port", "service", "usecase")
internal val DATA_ROLES = setOf("local.dao", "local.entity", "mapper", "repository", "seed", "remote")

/**
 * Production sources only. No path filter on purpose: `scopeFromProduction()` does not reach `app/build/generated`
 * (proved in task 4), and a `/build/` filter would empty every rule for a clone that sits under a `build` folder.
 */
internal val productionFiles: List<KoFileDeclaration> = Konsist.scopeFromProduction().files

/** True when the dotted name is [pkg] itself or lives below it. */
internal fun String.inPackage(pkg: String): Boolean = this == pkg || startsWith("$pkg.")

/** True when [segment] is one whole dotted segment of the name. */
internal fun String.hasSegment(segment: String): Boolean = ".$this.".contains(".$segment.")

/** Lower case words of a camelCase, PascalCase or SCREAMING_SNAKE name. A digit starts a word; `VNDAmount` is two. */
internal fun String.nameWords(): List<String> = split(wordBoundary).filter { it.isNotEmpty() }.map { it.lowercase() }

private val wordBoundary =
    Regex("""_|(?<=[a-z])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])|(?<=[A-Za-z])(?=\d)|(?<=\d)(?=[A-Z])""")

/** Number of real lines. A trailing line break does not open a new line. */
internal fun String.lineCount(): Int = lines().let { if (it.last().isEmpty()) it.size - 1 else it.size }

internal fun KoFileDeclaration.packageName(): String = packagee?.name.orEmpty()

internal fun filesIn(pkg: String): List<KoFileDeclaration> = productionFiles.filter { it.packageName().inPackage(pkg) }

/** Classes, interfaces and objects declared directly in the files, nested ones excluded. */
internal fun List<KoFileDeclaration>.topLevelTypes() =
    classesAndInterfacesAndObjects(includeNested = false, includeLocal = false)

/** Everything declared at the top of the files. Konsist also lists the package statement and imports; drop them. */
internal fun List<KoFileDeclaration>.topLevelDeclarations() =
    declarations(includeNested = false, includeLocal = false).filterNot {
        it is KoPackageDeclaration ||
            it is KoImportDeclaration
    }

/** Type name without type arguments, nullability marker or qualifier: `kotlinx.Flow<Int>?` becomes `Flow`. */
internal fun String.baseTypeName(): String = substringBefore('<').removeSuffix("?").substringAfterLast('.')

internal fun KoImportDeclaration.isFrom(pkg: String): Boolean = name.inPackage(pkg)

/** Fails every file in the list that has an import accepted by [isForbidden]. */
internal fun List<KoFileDeclaration>.assertNoImports(
    testName: String,
    isForbidden: (KoImportDeclaration) -> Boolean,
) = assertTrue(strict = false, testName = testName) { file -> file.imports.none(isForbidden) }

/** Project names written in code (`io.github.toanhp123.finote.data.Foo`), without `package` and `import` lines. */
internal fun KoFileDeclaration.qualifiedProjectNames(): List<String> =
    text
        .lineSequence()
        .filterNot { line -> line.trimStart().let { it.startsWith("package ") || it.startsWith("import ") } }
        .flatMap { line -> qualifiedProjectName.findAll(line).map { it.value } }
        .toList()

private val qualifiedProjectName = Regex(Regex.escape(ROOT) + """(?:\.\w+)*""")

/** Fails every file in the list that writes a project name accepted by [isForbidden] fully qualified in code. */
internal fun List<KoFileDeclaration>.assertNoQualifiedNames(
    testName: String,
    isForbidden: (String) -> Boolean,
) = assertTrue(strict = false, testName = testName) { file -> file.qualifiedProjectNames().none(isForbidden) }

/** Fails every file in [files] that imports, or writes fully qualified, any project layer in [forbiddenLayers]. */
internal fun assertLayerDoesNotImport(
    testName: String,
    files: List<KoFileDeclaration>,
    vararg forbiddenLayers: String,
) {
    files.assertNoImports(testName) { import -> forbiddenLayers.any { import.isFrom(it) } }
    files.assertNoQualifiedNames("$testName, fully qualified") { name -> forbiddenLayers.any { name.inPackage(it) } }
}

/** True for a root-package class other than generated `R` and `BuildConfig`: `ROOT.MainActivity`, not `ROOT.R.id`. */
internal fun String.isRootClassName(): Boolean {
    val rest = removePrefix("$ROOT.")
    return rest != this && rest.first().isUpperCase() && rest.substringBefore('.') !in GENERATED_CLASSES
}

private val GENERATED_CLASSES = setOf("R", "BuildConfig")
