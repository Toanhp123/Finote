package io.github.toanhp123.finote.data.transaction.local.entity

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index

// transactionId leads the primary key, so it needs no index of its own.
@Entity(
    tableName = "transaction_tag_cross_ref",
    primaryKeys = ["transactionId", "tagId"],
    foreignKeys =
        [
            ForeignKey(
                entity = TransactionEntity::class,
                parentColumns = ["id"],
                childColumns = ["transactionId"],
                onDelete = ForeignKey.CASCADE,
            ),
            ForeignKey(
                entity = TagEntity::class,
                parentColumns = ["id"],
                childColumns = ["tagId"],
                onDelete = ForeignKey.CASCADE,
            ),
        ],
    indices = [Index(value = ["tagId"])],
)
data class TransactionTagCrossRef(
    val transactionId: String,
    val tagId: String,
)
