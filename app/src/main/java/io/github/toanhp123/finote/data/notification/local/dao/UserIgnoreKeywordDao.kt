package io.github.toanhp123.finote.data.notification.local.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import io.github.toanhp123.finote.data.notification.local.entity.UserIgnoreKeywordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserIgnoreKeywordDao {
    /** Throws when the package already has this normalized keyword; the caller treats that as "already taught". */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(keyword: UserIgnoreKeywordEntity)

    @Query("SELECT * FROM user_ignore_keywords WHERE packageName = :packageName ORDER BY createdAt ASC, id ASC")
    suspend fun getByPackage(packageName: String): List<UserIgnoreKeywordEntity>

    @Query("SELECT * FROM user_ignore_keywords WHERE id = :id")
    suspend fun getById(id: String): UserIgnoreKeywordEntity?

    @Query("SELECT * FROM user_ignore_keywords ORDER BY packageName ASC, createdAt ASC, id ASC")
    fun observeAll(): Flow<List<UserIgnoreKeywordEntity>>

    @Query("DELETE FROM user_ignore_keywords WHERE id = :id")
    suspend fun delete(id: String): Int
}
