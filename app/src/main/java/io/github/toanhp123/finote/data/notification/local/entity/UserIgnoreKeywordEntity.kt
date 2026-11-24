package io.github.toanhp123.finote.data.notification.local.entity

import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey

/** An ad keyword the user taught for one package. [normalizedKeyword] is what matching and uniqueness use. */
@Entity(
    tableName = "user_ignore_keywords",
    indices =
        [
            Index(value = ["packageName"]),
            Index(value = ["packageName", "normalizedKeyword"], unique = true),
        ],
)
data class UserIgnoreKeywordEntity(
    @PrimaryKey val id: String,
    val packageName: String,
    val keyword: String,
    val normalizedKeyword: String,
    val createdAt: Long,
) {
    // The keyword is user text and may echo notification content: keep it out of logs and crash reports.
    override fun toString() = "UserIgnoreKeywordEntity(id=$id, packageName=$packageName)"
}
