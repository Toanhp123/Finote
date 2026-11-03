package io.github.toanhp123.finote.domain.category.repository

import io.github.toanhp123.finote.domain.category.model.Category
import kotlinx.coroutines.flow.Flow

/**
 * Every write is one atomic unit that also checks the two-level tree rules. Business errors are
 * [IllegalArgumentException] and leave the stored row unchanged.
 */
interface CategoryRepository {
    /** Includes a soft-deleted category. Null when the id is unknown. */
    suspend fun getCategory(id: String): Category?

    /** Categories that are not soft-deleted, hidden ones included. The UI filters hidden ones. */
    fun observeCategories(): Flow<List<Category>>

    /**
     * Stores the category as a user category: forces `isSystemDefault = false`, `nameKey = null`, `isHidden = false`.
     * A `parentId` must point to a live, top-level category of the same type.
     */
    suspend fun add(category: Category)

    /**
     * Keeps `isSystemDefault`, `isHidden`, `createdAt` and `deletedAt` of the stored row. Rejects a type change,
     * a `parentId` change on a system category, a parent that breaks the tree rules, and giving a parent to a
     * category that still has live children. A missing or deleted row is rejected.
     *
     * Contract for `name`: pass the stored `name` (the English fallback) unchanged unless the user edited the name
     * field. A different `name` clears `nameKey`. Passing the translated string the UI shows is a different `name`,
     * so it clears `nameKey` and the category loses its localization.
     */
    suspend fun update(category: Category)

    /** Flips `isHidden` only, system categories included. A missing or deleted category is rejected. */
    suspend fun setHidden(
        id: String,
        hidden: Boolean,
        updatedAt: Long,
    )

    /**
     * Soft delete. A missing id, a system category and a category with live children are rejected; deleting twice
     * does nothing. A category with transactions can be deleted: the transactions keep their `categoryId`.
     */
    suspend fun softDelete(
        id: String,
        deletedAt: Long,
    )
}
