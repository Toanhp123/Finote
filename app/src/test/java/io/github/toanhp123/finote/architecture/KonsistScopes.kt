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
internal const val UI = "$ROOT.ui"
internal const val UI_COMMON = "$ROOT.ui.common"
internal const val AUTOMATION = "$ROOT.automation"
internal const val WORK = "$ROOT.work"
internal const val DI = "$ROOT.di"

/**
 * Production sources only. No path filter on purpose: `scopeFromProduction()` does not reach `app/build/generated`
 * (proved in task 4), and a `/build/` filter would empty every rule for a clone that sits under a `build` folder.
 */
internal val productionFiles: List<KoFileDeclaration> = Konsist.scopeFromProduction().files

/** True when the dotted name is [pkg] itself or lives below it. */
internal fun String.inPackage(pkg: String): Boolean = this == pkg || startsWith("$pkg.")

/** True when [segment] is one whole dotted segment of the name. */
internal fun String.hasSegment(segment: String): Boolean = ".$this.".contains(".$segment.")

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

/** Fails every file in [files] that imports any of the project layers in [forbiddenLayers]. */
internal fun assertLayerDoesNotImport(
    testName: String,
    files: List<KoFileDeclaration>,
    vararg forbiddenLayers: String,
) = files.assertNoImports(testName) { import -> forbiddenLayers.any { import.isFrom(it) } }
