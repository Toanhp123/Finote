package io.github.toanhp123.finote.data.notification.repository

import io.github.toanhp123.finote.data.database.FinoteDatabase
import io.github.toanhp123.finote.data.database.mapStorageErrors
import io.github.toanhp123.finote.data.database.storageWrite
import io.github.toanhp123.finote.data.notification.mapper.toDomain
import io.github.toanhp123.finote.data.notification.mapper.toEntity
import io.github.toanhp123.finote.data.notification.seed.StoredRule
import io.github.toanhp123.finote.data.notification.seed.outdatedRules
import io.github.toanhp123.finote.domain.notification.model.ParserRule
import io.github.toanhp123.finote.domain.notification.repository.ParserRuleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import javax.inject.Inject

class ParserRuleRepositoryImpl
    @Inject
    constructor(
        private val db: FinoteDatabase,
        private val clock: Clock,
    ) : ParserRuleRepository {
        private val dao = db.parserRuleDao()

        override suspend fun applyRules(rules: List<ParserRule>): Int =
            db.storageWrite {
                val stored = dao.getAll().associate { it.id to StoredRule(it.version, it.isUserCustom) }
                val now = clock.millis()
                val toWrite = outdatedRules(stored, rules.map { it.toEntity().copy(updatedAt = now) })
                dao.insertAll(toWrite)
                toWrite.size
            }

        override fun observeRules(): Flow<List<ParserRule>> =
            dao.observeAll().map { list -> list.mapNotNull { it.toDomain() } }.mapStorageErrors()
    }
