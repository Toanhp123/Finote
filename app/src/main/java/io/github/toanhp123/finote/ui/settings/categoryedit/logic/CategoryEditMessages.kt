package io.github.toanhp123.finote.ui.settings.categoryedit.logic

import androidx.annotation.StringRes
import io.github.toanhp123.finote.R
import io.github.toanhp123.finote.domain.category.error.CategoryError

/** The message for a refused change. A null [CategoryError] is a database failure. */
@StringRes
fun CategoryError?.messageRes(): Int =
    when (this) {
        null -> R.string.category_snack_failed
        CategoryError.NotFound -> R.string.category_error_not_found
        CategoryError.BlankName -> R.string.category_error_blank_name
        CategoryError.TypeChanged -> R.string.category_error_type_changed
        CategoryError.SystemParentChanged -> R.string.category_parent_locked_system
        CategoryError.SystemCategory -> R.string.category_error_system
        CategoryError.HasChildren -> R.string.category_parent_locked_children
        CategoryError.InvalidMoveTarget -> R.string.category_error_move_target
        CategoryError.InvalidParent -> R.string.category_error_parent
    }
