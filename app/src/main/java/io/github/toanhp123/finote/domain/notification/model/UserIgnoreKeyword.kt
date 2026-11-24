package io.github.toanhp123.finote.domain.notification.model

/** An ad keyword the user taught for one package. [keyword] is as typed; [normalizedKeyword] is what matches. */
data class UserIgnoreKeyword(
    val id: String,
    val packageName: String,
    val keyword: String,
    val normalizedKeyword: String,
    val createdAt: Long,
) {
    // The keyword is user text and may echo notification content: keep it out of logs and crash reports.
    override fun toString() = "UserIgnoreKeyword(id=$id, packageName=$packageName)"
}
