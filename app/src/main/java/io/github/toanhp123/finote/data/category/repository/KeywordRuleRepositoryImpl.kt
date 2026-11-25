package io.github.toanhp123.finote.data.category.repository

import io.github.toanhp123.finote.data.category.mapper.toDomain
import io.github.toanhp123.finote.data.category.mapper.toEntity
import io.github.toanhp123.finote.data.database.FinoteDatabase
import io.github.toanhp123.finote.data.database.mapStorageErrors
import io.github.toanhp123.finote.data.database.storage
import io.github.toanhp123.finote.data.database.storageWrite
import io.github.toanhp123.finote.data.transaction.mapper.toDomain
import io.github.toanhp123.finote.domain.category.model.KeywordRule
import io.github.toanhp123.finote.domain.category.model.RuleMatch
import io.github.toanhp123.finote.domain.category.repository.KeywordRuleRepository
import io.github.toanhp123.finote.domain.common.normalizeKeyword
import io.github.toanhp123.finote.domain.transaction.model.Transaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class KeywordRuleRepositoryImpl
    @Inject
    constructor(
        private val db: FinoteDatabase,
    ) : KeywordRuleRepository {
        private val rules = db.keywordRuleDao()
        private val transactions = db.transactionDao()

        override fun observeRules(): Flow<List<KeywordRule>> =
            rules.observeAll().map { list -> list.map { it.toDomain() } }.mapStorageErrors()

        override suspend fun getRules(): List<KeywordRule> = storage { rules.getAll().map { it.toDomain() } }

        override suspend fun getRule(id: String): KeywordRule? = storage { rules.getById(id)?.toDomain() }

        override suspend fun upsertUserRule(rule: KeywordRule) {
            db.storageWrite {
                if (rules.getById(rule.id)?.isUserDefined == false) return@storageWrite
                rules.upsert(
                    rule.toEntity().copy(
                        normalizedKeyword = normalizeKeyword(rule.keyword),
                        priority = 0,
                        isUserDefined = true,
                        version = 0,
                    ),
                )
            }
        }

        override suspend fun deleteUserRule(id: String) {
            db.storageWrite { rules.deleteUser(id) }
        }

        override suspend fun setActive(
            id: String,
            active: Boolean,
            now: Long,
        ) {
            db.storageWrite { rules.setActive(id, active, now) }
        }

        override suspend fun getUncategorizedNotificationRows(): List<Transaction> =
            storage { transactions.getUncategorizedFromNotifications().map { it.toDomain() } }

        override suspend fun setCategory(rows: List<Pair<String, RuleMatch>>) {
            db.storageWrite {
                rows.forEach { (id, match) -> transactions.setCategory(id, match.categoryId, match.merchantName) }
            }
        }
    }
