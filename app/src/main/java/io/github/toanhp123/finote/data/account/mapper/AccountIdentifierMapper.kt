package io.github.toanhp123.finote.data.account.mapper

import io.github.toanhp123.finote.data.account.local.entity.AccountIdentifierEntity
import io.github.toanhp123.finote.domain.account.model.AccountIdentifier

fun AccountIdentifierEntity.toDomain(): AccountIdentifier = AccountIdentifier(packageName, kind, mask, accountId)

fun AccountIdentifier.toEntity(): AccountIdentifierEntity = AccountIdentifierEntity(packageName, kind, mask, accountId)
