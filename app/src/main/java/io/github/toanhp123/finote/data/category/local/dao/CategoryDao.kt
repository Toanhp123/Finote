package io.github.toanhp123.finote.data.category.local.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Update
import io.github.toanhp123.finote.data.category.local.entity.CategoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Insert
    suspend fun insert(category: CategoryEntity)

    @Update
    suspend fun update(category: CategoryEntity)

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getById(id: String): CategoryEntity?

    @Query("SELECT COUNT(*) FROM categories WHERE parentId = :parentId AND deletedAt IS NULL")
    suspend fun countLiveChildren(parentId: String): Int

    @Query("SELECT * FROM categories WHERE deletedAt IS NULL ORDER BY sortOrder ASC, createdAt ASC")
    fun observeCategories(): Flow<List<CategoryEntity>>
}
