package io.github.toanhp123.finote.data.transaction.local.dao

import androidx.room3.Dao
import androidx.room3.Delete
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Update
import io.github.toanhp123.finote.data.transaction.local.entity.TagEntity
import io.github.toanhp123.finote.data.transaction.local.entity.TransactionTagCrossRef
import kotlinx.coroutines.flow.Flow

@Dao
interface TagDao {
    @Insert
    suspend fun insert(tag: TagEntity)

    @Update
    suspend fun update(tag: TagEntity)

    @Query("SELECT * FROM tags WHERE id = :id")
    suspend fun getById(id: String): TagEntity?

    @Query("SELECT * FROM tags WHERE deletedAt IS NULL ORDER BY name ASC")
    fun observeTags(): Flow<List<TagEntity>>

    @Insert
    suspend fun insertTagRef(ref: TransactionTagCrossRef)

    @Delete
    suspend fun deleteTagRef(ref: TransactionTagCrossRef)

    @Query("SELECT * FROM transaction_tag_cross_ref WHERE transactionId = :transactionId")
    suspend fun getTagRefs(transactionId: String): List<TransactionTagCrossRef>
}
