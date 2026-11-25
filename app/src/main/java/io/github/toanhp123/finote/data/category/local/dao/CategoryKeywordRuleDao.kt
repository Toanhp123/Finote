package io.github.toanhp123.finote.data.category.local.dao

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert
import io.github.toanhp123.finote.data.category.local.entity.CategoryKeywordRuleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryKeywordRuleDao {
    @Query("SELECT * FROM category_keyword_rules ORDER BY id ASC")
    suspend fun getAll(): List<CategoryKeywordRuleEntity>

    @Query("SELECT * FROM category_keyword_rules ORDER BY id ASC")
    fun observeAll(): Flow<List<CategoryKeywordRuleEntity>>

    @Query("SELECT * FROM category_keyword_rules WHERE id = :id")
    suspend fun getById(id: String): CategoryKeywordRuleEntity?

    @Upsert
    suspend fun upsert(rule: CategoryKeywordRuleEntity)

    /** A system rule is never deleted, only switched off. */
    @Query("DELETE FROM category_keyword_rules WHERE id = :id AND isUserDefined = 1")
    suspend fun deleteUser(id: String): Int

    @Query("UPDATE category_keyword_rules SET isActive = :active, updatedAt = :now WHERE id = :id")
    suspend fun setActive(
        id: String,
        active: Boolean,
        now: Long,
    )

    /** User rules only: a system rule points at a `sys_*` category, which the user can hide but never delete. */
    @Query("UPDATE category_keyword_rules SET categoryId = :to WHERE isUserDefined = 1 AND categoryId IN (:ids)")
    suspend fun moveToCategory(
        ids: Collection<String>,
        to: String,
    )

    /** Drops user rules of [ids] whose keyword a user rule of [targetId] already has, so a move leaves no duplicate. */
    @Query(
        "DELETE FROM category_keyword_rules WHERE isUserDefined = 1 AND categoryId IN (:ids) " +
            "AND normalizedKeyword IN (SELECT normalizedKeyword FROM category_keyword_rules " +
            "WHERE isUserDefined = 1 AND categoryId = :targetId)",
    )
    suspend fun deleteDuplicatesOf(
        ids: Collection<String>,
        targetId: String,
    )

    @Query("DELETE FROM category_keyword_rules WHERE isUserDefined = 1 AND categoryId IN (:ids)")
    suspend fun deleteByCategories(ids: Collection<String>)

    @Query("SELECT COUNT(*) FROM category_keyword_rules WHERE isUserDefined = 1 AND categoryId IN (:ids)")
    suspend fun countByCategories(ids: Collection<String>): Int
}
