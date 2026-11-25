package io.github.toanhp123.finote.ui.settings.keywordruleedit.logic

import io.github.toanhp123.finote.domain.category.model.Category
import io.github.toanhp123.finote.domain.category.service.shown

/** The shown categories, plus [currentId] when it is hidden, so a rule that points at it still shows where. */
fun pickerCategories(
    all: List<Category>,
    currentId: String?,
): List<Category> {
    val shown = all.shown()
    val current = all.firstOrNull { it.id == currentId && it !in shown }
    return if (current == null) shown else shown + current
}
