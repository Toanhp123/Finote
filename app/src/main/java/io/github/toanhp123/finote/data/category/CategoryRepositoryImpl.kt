package io.github.toanhp123.finote.data.category

import androidx.room3.withWriteTransaction
import io.github.toanhp123.finote.data.FinoteDatabase
import io.github.toanhp123.finote.domain.category.model.Category
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

        override suspend fun getCategory(id: String): Category? = categories.getById(id)?.toDomain()

        override fun observeCategories(): Flow<List<Category>> =
            categories.observeCategories().map { list -> list.map { it.toDomain() } }

        override suspend fun add(category: Category) {
            db.withWriteTransaction {
                requireValidParent(category)
                categories.insert(category.toEntity().copy(isSystemDefault = false, nameKey = null, isHidden = false))
            }
        }

        override suspend fun update(category: Category) {
            db.withWriteTransaction {
                val old = requireLive(category.id)
                require(category.type == old.type) { "category type cannot change" }
                if (old.isSystemDefault) {
                    require(category.parentId == old.parentId) { "system category cannot change its parent" }
                }
                requireValidParent(category)
                require(category.parentId == null || categories.countLiveChildren(category.id) == 0) {
                    "category still has children"
                }
                categories.update(
                    category.toEntity().copy(
                        isSystemDefault = old.isSystemDefault,
                        isHidden = old.isHidden,
                        createdAt = old.createdAt,
                        deletedAt = old.deletedAt,
                        nameKey = if (category.name == old.name) old.nameKey else null,
                    ),
                )
            }
        }

        override suspend fun setHidden(
            id: String,
            hidden: Boolean,
            updatedAt: Long,
        ) {
            db.withWriteTransaction {
                categories.update(requireLive(id).copy(isHidden = hidden, updatedAt = updatedAt))
            }
        }

        override suspend fun softDelete(
            id: String,
            deletedAt: Long,
        ) {
            db.withWriteTransaction {
                val old = requireNotNull(categories.getById(id)) { "category does not exist" }
                if (old.deletedAt != null) return@withWriteTransaction
                require(!old.isSystemDefault) { "system category cannot be deleted" }
                require(categories.countLiveChildren(id) == 0) { "category still has children" }
                categories.update(old.copy(deletedAt = deletedAt, updatedAt = deletedAt))
            }
        }

        private suspend fun requireLive(id: String): CategoryEntity {
            val old = requireNotNull(categories.getById(id)) { "category does not exist" }
            require(old.deletedAt == null) { "category is deleted" }
            return old
        }

        /** A parent must be live, top level, of the same type, and not the category itself. */
        private suspend fun requireValidParent(category: Category) {
            val parentId = category.parentId ?: return
            require(parentId != category.id) { "category cannot be its own parent" }
            val parent = requireLive(parentId)
            require(parent.parentId == null) { "parent must be a top-level category" }
            require(parent.type == category.type) { "parent must have the same type" }
        }
    }
