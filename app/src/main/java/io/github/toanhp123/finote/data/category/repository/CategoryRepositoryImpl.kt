package io.github.toanhp123.finote.data.category.repository

import io.github.toanhp123.finote.data.category.mapper.toDomain
import io.github.toanhp123.finote.data.category.mapper.toEntity
import io.github.toanhp123.finote.data.database.FinoteDatabase
import io.github.toanhp123.finote.data.database.mapStorageErrors
import io.github.toanhp123.finote.data.database.storage
import io.github.toanhp123.finote.data.database.storageWrite
import io.github.toanhp123.finote.domain.category.model.Category
import io.github.toanhp123.finote.domain.category.model.CategoryType
import io.github.toanhp123.finote.domain.category.repository.CategoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class CategoryRepositoryImpl
    @Inject
    constructor(
        private val db: FinoteDatabase,
    ) : CategoryRepository {
        private val categories = db.categoryDao()
        private val rules = db.keywordRuleDao()
        private val budgets = db.budgetDao()

        override suspend fun getCategory(id: String): Category? = storage { categories.getById(id)?.toDomain() }

        override fun observeCategories(): Flow<List<Category>> =
            categories.observeCategories().map { list -> list.map { it.toDomain() } }.mapStorageErrors()

        override suspend fun countLiveChildren(id: String): Int = storage { categories.countLiveChildren(id) }

        override suspend fun add(category: Category) {
            db.storageWrite {
                categories.insert(category.toEntity().copy(isSystemDefault = false, nameKey = null, isHidden = false))
            }
        }

        override suspend fun update(category: Category) {
            db.storageWrite {
                val old = categories.getById(category.id) ?: return@storageWrite
                categories.update(
                    category.toEntity().copy(
                        isSystemDefault = old.isSystemDefault,
                        isHidden = old.isHidden,
                        createdAt = old.createdAt,
                        deletedAt = old.deletedAt,
                        nameKey = if (category.name == old.name) old.nameKey else null,
                    ),
                )
                if (old.parentId == null && category.parentId != null) moveBudgets(setOf(old.id), category.parentId)
            }
        }

        override suspend fun setHidden(
            id: String,
            hidden: Boolean,
            updatedAt: Long,
        ) {
            db.storageWrite {
                val old = categories.getById(id) ?: return@storageWrite
                categories.update(old.copy(isHidden = hidden, updatedAt = updatedAt))
            }
        }

        override suspend fun getLevel(
            type: CategoryType,
            parentId: String?,
        ): List<Category> = storage { categories.getLevel(type, parentId).map { it.toDomain() } }

        override suspend fun countLiveTransactions(categoryIds: Set<String>): Int =
            storage { categories.countLiveTransactions(categoryIds) }

        override suspend fun countRules(categoryIds: Set<String>): Int =
            storage { rules.countByCategories(categoryIds) }

        override suspend fun countBudgets(categoryIds: Set<String>): Int =
            storage { budgets.countByCategories(categoryIds) }

        override suspend fun deleteAndMove(
            categoryIds: Set<String>,
            targetId: String?,
            deletedAt: Long,
        ) {
            db.storageWrite {
                categories.moveTransactions(categoryIds, targetId)
                if (targetId == null) {
                    rules.deleteByCategories(categoryIds)
                } else {
                    rules.deleteDuplicatesOf(categoryIds, targetId)
                    rules.moveToCategory(categoryIds, targetId)
                }
                moveBudgets(categoryIds, targetId?.let { categories.getById(it)?.parentId ?: it })
                categories.softDeleteAll(categoryIds, deletedAt)
            }
        }

        /** Null [to] deletes the budgets. A month [to] already budgets keeps its own row; the moved one is dropped. */
        private suspend fun moveBudgets(
            from: Set<String>,
            to: String?,
        ) {
            val moving = budgets.getByCategories(from)
            val taken = to?.let { budgets.getByCategories(setOf(it)).mapTo(mutableSetOf()) { b -> b.monthYear } }
            val (kept, dropped) = moving.partition { taken?.add(it.monthYear) == true }
            budgets.deleteByIds(dropped.map { it.id })
            kept.forEach { budgets.update(it.copy(categoryId = to)) }
        }

        override suspend fun reorder(
            orderedIds: List<String>,
            now: Long,
        ) {
            db.storageWrite { orderedIds.forEachIndexed { index, id -> categories.setSortOrder(id, index, now) } }
        }
    }
