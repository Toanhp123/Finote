package io.github.toanhp123.finote.data.account.local.entity

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import io.github.toanhp123.finote.domain.account.model.IdentifierKind

/** One masked number a bank app shows, owned by one account. An owner that is deleted leaves the key free. */
@Entity(
    tableName = "account_identifiers",
    primaryKeys = ["packageName", "kind", "mask"],
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index(value = ["accountId"])],
)
data class AccountIdentifierEntity(
    val packageName: String,
    val kind: IdentifierKind,
    val mask: String,
    val accountId: String,
) {
    // The mask must never reach a log or crash report.
    override fun toString() = "AccountIdentifierEntity(packageName=$packageName, kind=$kind, accountId=$accountId)"
}
