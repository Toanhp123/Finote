package io.github.toanhp123.finote.data.category.local.entity

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey
import io.github.toanhp123.finote.domain.category.model.KeywordMatchType

/**
 * A keyword that sets a category. [normalizedKeyword] is `normalizeKeyword(keyword)`; matching and dedup use it.
 * [normalizedMerchantName] is the short name the row keeps, null when blank.
 */
@Entity(
    tableName = "category_keyword_rules",
    foreignKeys =
        [
            ForeignKey(
                entity = CategoryEntity::class,
                parentColumns = ["id"],
                childColumns = ["categoryId"],
                onDelete = ForeignKey.RESTRICT,
            ),
        ],
    indices = [Index(value = ["categoryId"]), Index(value = ["normalizedKeyword"])],
)
data class CategoryKeywordRuleEntity(
    @PrimaryKey val id: String,
    val keyword: String,
    val normalizedKeyword: String,
    val matchType: KeywordMatchType,
    val normalizedMerchantName: String?,
    val categoryId: String,
    val priority: Int,
    val isUserDefined: Boolean,
    val isActive: Boolean,
    val version: Int,
    val createdAt: Long,
    val updatedAt: Long,
) {
    // The keyword and the name come from the user's notifications: keep them out of logs and crash reports.
    override fun toString() = "CategoryKeywordRuleEntity(id=$id)"
}
