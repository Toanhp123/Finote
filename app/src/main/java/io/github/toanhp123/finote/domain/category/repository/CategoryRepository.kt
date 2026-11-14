package io.github.toanhp123.finote.domain.category.repository

import io.github.toanhp123.finote.domain.category.model.Category
import kotlinx.coroutines.flow.Flow

/**
 * Every write is one atomic unit. The repository checks no business rule: use cases decide with `CategoryRules`
 * inside `TransactionRunner.write` before they call it.
 */
interface CategoryRepository {
    /** Includes a soft-deleted category. Null when the id is unknown. */
    suspend fun getCategory(id: String): Category?

    /** Categories that are not soft-deleted, hidden ones included. The UI filters hidden ones. */
    fun observeCategories(): Flow<List<Category>>

    /** Live (not soft-deleted) categories whose `parentId` is [id]. */
    suspend fun countLiveChildren(id: String): Int

    /** Stores a user category: forces `isSystemDefault = false`, `nameKey = null`, `isHidden = false`. */
    suspend fun add(category: Category)

    /**
     * Keeps `isSystemDefault`, `isHidden`, `createdAt` and `deletedAt` of the stored row. A missing row is ignored.
     *
     * Contract for `name`: pass the stored `name` (the English fallback) unchanged unless the user edited the name
     * field. A different `name` clears `nameKey`. Passing the translated string the UI shows is a different `name`,
     * so it clears `nameKey` and the category loses its localization.
     */
    suspend fun update(category: Category)

    /** Flips `isHidden` only, system categories included. A missing row is ignored. */
    suspend fun setHidden(
        id: String,
        hidden: Boolean,
        updatedAt: Long,
    )

    /**
     * Soft delete. A missing id or an already deleted row is ignored. A category with transactions can be deleted:
     * the transactions keep their `categoryId`.
     */
    suspend fun softDelete(
        id: String,
        deletedAt: Long,
    )
}
