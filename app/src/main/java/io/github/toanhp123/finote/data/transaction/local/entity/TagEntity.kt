package io.github.toanhp123.finote.data.transaction.local.entity

import androidx.room3.Entity
import androidx.room3.PrimaryKey

@Entity(tableName = "tags")
data class TagEntity(
    @PrimaryKey val id: String,
    val name: String,
    val color: Int,
    val isArchived: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
)
