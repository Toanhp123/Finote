package io.github.toanhp123.finote.data.category.mapper

import io.github.toanhp123.finote.data.category.local.entity.CategoryKeywordRuleEntity
import io.github.toanhp123.finote.domain.category.model.KeywordRule

fun CategoryKeywordRuleEntity.toDomain(): KeywordRule =
    KeywordRule(
        id = id,
        keyword = keyword,
        normalizedKeyword = normalizedKeyword,
        matchType = matchType,
        merchantName = normalizedMerchantName,
        categoryId = categoryId,
        priority = priority,
        isUserDefined = isUserDefined,
        isActive = isActive,
        version = version,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

/** A blank merchant name is stored as null. */
fun KeywordRule.toEntity(): CategoryKeywordRuleEntity =
    CategoryKeywordRuleEntity(
        id = id,
        keyword = keyword,
        normalizedKeyword = normalizedKeyword,
        matchType = matchType,
        normalizedMerchantName = merchantName?.takeIf { it.isNotBlank() },
        categoryId = categoryId,
        priority = priority,
        isUserDefined = isUserDefined,
        isActive = isActive,
        version = version,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
