package io.github.toanhp123.finote.data.category.local.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Update
import io.github.toanhp123.finote.data.category.local.entity.CategoryEntity
import io.github.toanhp123.finote.domain.category.model.CategoryType
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Insert
    suspend fun insert(category: CategoryEntity)

    @Update
    suspend fun update(category: CategoryEntity)

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getById(id: String): CategoryEntity?

    @Query("SELECT * FROM categories WHERE deletedAt IS NULL")
    suspend fun getLive(): List<CategoryEntity>

    @Query("SELECT COUNT(*) FROM categories WHERE parentId = :parentId AND deletedAt IS NULL")
    suspend fun countLiveChildren(parentId: String): Int

    @Query("SELECT * FROM categories WHERE deletedAt IS NULL ORDER BY sortOrder ASC, createdAt ASC, id ASC")
    fun observeCategories(): Flow<List<CategoryEntity>>

    /** Live rows of one level, hidden ones included. A null [parentId] is the top level. */
    @Query(
        "SELECT * FROM categories WHERE type = :type AND parentId IS :parentId AND deletedAt IS NULL " +
            "ORDER BY sortOrder ASC, createdAt ASC, id ASC",
    )
    suspend fun getLevel(
        type: CategoryType,
        parentId: String?,
    ): List<CategoryEntity>

    @Query("SELECT COUNT(*) FROM transactions WHERE deletedAt IS NULL AND categoryId IN (:ids)")
    suspend fun countLiveTransactions(ids: Collection<String>): Int

    /**
     * Every transaction in [ids], deleted and paired-away ones too, so a restore lands on [to]. `updatedAt` stays: a
     * move is not a user edit, and a touched notification row would stop being auto-pairable.
     */
    @Query("UPDATE transactions SET categoryId = :to WHERE categoryId IN (:ids)")
    suspend fun moveTransactions(
        ids: Collection<String>,
        to: String?,
    )

    @Query("UPDATE categories SET deletedAt = :now, updatedAt = :now WHERE id IN (:ids) AND deletedAt IS NULL")
    suspend fun softDeleteAll(
        ids: Collection<String>,
        now: Long,
    )

    @Query("UPDATE categories SET sortOrder = :sortOrder, updatedAt = :now WHERE id = :id")
    suspend fun setSortOrder(
        id: String,
        sortOrder: Int,
        now: Long,
    )
}
