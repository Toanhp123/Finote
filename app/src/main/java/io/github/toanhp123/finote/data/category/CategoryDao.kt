package io.github.toanhp123.finote.data.category

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Insert
    suspend fun insert(category: CategoryEntity)

    @Update
    suspend fun update(category: CategoryEntity)

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getById(id: String): CategoryEntity?

    @Query("SELECT * FROM categories WHERE deletedAt IS NULL ORDER BY sortOrder ASC, createdAt ASC")
    fun observeCategories(): Flow<List<CategoryEntity>>
}
