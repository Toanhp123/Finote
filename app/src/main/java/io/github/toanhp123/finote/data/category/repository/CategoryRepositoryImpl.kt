package io.github.toanhp123.finote.data.category.repository

import io.github.toanhp123.finote.data.category.mapper.toDomain
import io.github.toanhp123.finote.data.category.mapper.toEntity
import io.github.toanhp123.finote.data.database.FinoteDatabase
import io.github.toanhp123.finote.data.database.mapStorageErrors
import io.github.toanhp123.finote.data.database.storage
import io.github.toanhp123.finote.data.database.storageWrite
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

        override suspend fun softDelete(
            id: String,
            deletedAt: Long,
        ) {
            db.storageWrite {
                val old = categories.getById(id) ?: return@storageWrite
                if (old.deletedAt != null) return@storageWrite
                categories.update(old.copy(deletedAt = deletedAt, updatedAt = deletedAt))
            }
        }
    }
