package io.github.toanhp123.finote.data.category.seed

import io.github.toanhp123.finote.domain.category.model.CategoryType

/**
 * One seeded category. [id] is a stable contract with keyword rules, OTA rules and `strings.xml`: change it only
 * through a migration. [name] is the English fallback; the UI reads the string resource named [nameKey].
 * [icon] is the drawable name without the `ic_` prefix.
 */
data class SystemCategory(
    val id: String,
    val name: String,
    val icon: String,
    val colorArgb: Int,
    val type: CategoryType,
    val parentId: String?,
    val sortOrder: Int,
) {
    val nameKey: String get() = id
}

// Literals of CategoryPalette: the data layer cannot import ui. SystemCategoriesTest fails on any drift.
private const val RED = 0xFFC62828.toInt()
private const val PINK = 0xFFAD1457.toInt()
private const val PURPLE = 0xFF6A1B9A.toInt()
private const val DEEP_PURPLE = 0xFF4527A0.toInt()
private const val INDIGO = 0xFF283593.toInt()
private const val BLUE = 0xFF1565C0.toInt()
private const val LIGHT_BLUE = 0xFF0277BD.toInt()
private const val CYAN = 0xFF00838F.toInt()
private const val TEAL = 0xFF00695C.toInt()
private const val GREEN = 0xFF2E7D32.toInt()
private const val LIME = 0xFF558B2F.toInt()
private const val ORANGE = 0xFFE65100.toInt()
private const val BROWN = 0xFF4E342E.toInt()
private const val SLATE = 0xFF37474F.toInt()

/** A top-level category and its children. Children take the icon and color of the parent. */
private class Branch(
    val id: String,
    val name: String,
    val icon: String,
    val color: Int,
    val children: List<Pair<String, String>>,
)

private fun branch(
    id: String,
    name: String,
    icon: String,
    color: Int,
    vararg children: Pair<String, String>,
) = Branch(id, name, icon, color, children.toList())

private fun List<Branch>.toRows(type: CategoryType): List<SystemCategory> =
    flatMapIndexed { order, parent ->
        listOf(SystemCategory(parent.id, parent.name, parent.icon, parent.color, type, null, order)) +
            parent.children.mapIndexed { index, (id, name) ->
                SystemCategory(id, name, parent.icon, parent.color, type, parent.id, index)
            }
    }

private val ExpenseBranches =
    listOf(
        branch(
            "sys_food",
            "Food & Drinks",
            "restaurant",
            ORANGE,
            "sys_food_groceries" to "Groceries",
            "sys_food_restaurants" to "Restaurants",
            "sys_food_coffee" to "Coffee & Snacks",
        ),
        branch(
            "sys_transport",
            "Transport",
            "directions_car",
            BLUE,
            "sys_transport_fuel" to "Fuel",
            "sys_transport_parking" to "Parking & Tolls",
            "sys_transport_taxi" to "Taxi & Ride-hailing",
            "sys_transport_transit" to "Public Transit",
            "sys_transport_maintenance" to "Vehicle Maintenance",
        ),
        branch(
            "sys_housing",
            "Housing",
            "home",
            BROWN,
            "sys_housing_rent" to "Rent",
            "sys_housing_utilities" to "Utilities",
            "sys_housing_internet" to "Internet & Phone",
            "sys_housing_maintenance" to "Maintenance",
        ),
        branch(
            "sys_shopping",
            "Shopping",
            "shopping_bag",
            PINK,
            "sys_shopping_clothing" to "Clothing",
            "sys_shopping_electronics" to "Electronics",
            "sys_shopping_household" to "Household",
        ),
        branch(
            "sys_health",
            "Health",
            "medical_services",
            RED,
            "sys_health_medical" to "Medical",
            "sys_health_pharmacy" to "Pharmacy",
            "sys_health_insurance" to "Insurance",
        ),
        branch(
            "sys_education",
            "Education",
            "school",
            INDIGO,
            "sys_education_tuition" to "Tuition",
            "sys_education_courses" to "Books & Courses",
        ),
        branch(
            "sys_entertainment",
            "Entertainment",
            "movie",
            PURPLE,
            "sys_entertainment_media" to "Movies & Music",
            "sys_entertainment_travel" to "Travel",
            "sys_entertainment_sports" to "Sports & Hobbies",
        ),
        branch(
            "sys_family",
            "Family & Personal",
            "family_restroom",
            CYAN,
            "sys_family_children" to "Children",
            "sys_family_personal_care" to "Personal Care",
            "sys_family_pets" to "Pets",
        ),
        branch(
            "sys_gifts",
            "Gifts & Donations",
            "redeem",
            DEEP_PURPLE,
            "sys_gifts_gifts" to "Gifts",
            "sys_gifts_charity" to "Charity",
        ),
        branch(
            "sys_fees",
            "Fees & Charges",
            "receipt_long",
            SLATE,
            "sys_fees_bank" to "Bank Fees",
            "sys_fees_taxes" to "Taxes",
        ),
        branch("sys_other", "Other", "more_horiz", SLATE),
    )

private val IncomeBranches =
    listOf(
        branch("sys_income_salary", "Salary", "work", GREEN),
        branch("sys_income_bonus", "Bonus", "stars", LIME),
        branch("sys_income_business", "Business", "storefront", TEAL),
        branch("sys_income_investment", "Investment & Interest", "trending_up", LIGHT_BLUE),
        branch("sys_income_gifts", "Gifts Received", "card_giftcard", DEEP_PURPLE),
        branch("sys_income_other", "Other", "more_horiz", SLATE),
    )

/** All 47 seeded rows. A parent always comes before its children. */
val SystemCategories: List<SystemCategory> =
    ExpenseBranches.toRows(CategoryType.EXPENSE) + IncomeBranches.toRows(CategoryType.INCOME)
