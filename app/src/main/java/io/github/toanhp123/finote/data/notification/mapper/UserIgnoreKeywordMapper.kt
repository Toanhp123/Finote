package io.github.toanhp123.finote.data.notification.mapper

import io.github.toanhp123.finote.data.notification.local.entity.UserIgnoreKeywordEntity
import io.github.toanhp123.finote.domain.notification.model.UserIgnoreKeyword

fun UserIgnoreKeyword.toEntity() = UserIgnoreKeywordEntity(id, packageName, keyword, normalizedKeyword, createdAt)

fun UserIgnoreKeywordEntity.toDomain() = UserIgnoreKeyword(id, packageName, keyword, normalizedKeyword, createdAt)
