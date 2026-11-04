package io.github.toanhp123.finote.ui.common.format

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.toanhp123.finote.R

// Explicit tables: Resources.getIdentifier is flagged by lint DiscouragedApi (warningsAsErrors).
private val SystemNames: Map<String, Int> =
    mapOf(
        "sys_food" to R.string.sys_food,
        "sys_food_groceries" to R.string.sys_food_groceries,
        "sys_food_restaurants" to R.string.sys_food_restaurants,
        "sys_food_coffee" to R.string.sys_food_coffee,
        "sys_transport" to R.string.sys_transport,
        "sys_transport_fuel" to R.string.sys_transport_fuel,
        "sys_transport_parking" to R.string.sys_transport_parking,
        "sys_transport_taxi" to R.string.sys_transport_taxi,
        "sys_transport_transit" to R.string.sys_transport_transit,
        "sys_transport_maintenance" to R.string.sys_transport_maintenance,
        "sys_housing" to R.string.sys_housing,
        "sys_housing_rent" to R.string.sys_housing_rent,
        "sys_housing_utilities" to R.string.sys_housing_utilities,
        "sys_housing_internet" to R.string.sys_housing_internet,
        "sys_housing_maintenance" to R.string.sys_housing_maintenance,
        "sys_shopping" to R.string.sys_shopping,
        "sys_shopping_clothing" to R.string.sys_shopping_clothing,
        "sys_shopping_electronics" to R.string.sys_shopping_electronics,
        "sys_shopping_household" to R.string.sys_shopping_household,
        "sys_health" to R.string.sys_health,
        "sys_health_medical" to R.string.sys_health_medical,
        "sys_health_pharmacy" to R.string.sys_health_pharmacy,
        "sys_health_insurance" to R.string.sys_health_insurance,
        "sys_education" to R.string.sys_education,
        "sys_education_tuition" to R.string.sys_education_tuition,
        "sys_education_courses" to R.string.sys_education_courses,
        "sys_entertainment" to R.string.sys_entertainment,
        "sys_entertainment_media" to R.string.sys_entertainment_media,
        "sys_entertainment_travel" to R.string.sys_entertainment_travel,
        "sys_entertainment_sports" to R.string.sys_entertainment_sports,
        "sys_family" to R.string.sys_family,
        "sys_family_children" to R.string.sys_family_children,
        "sys_family_personal_care" to R.string.sys_family_personal_care,
        "sys_family_pets" to R.string.sys_family_pets,
        "sys_gifts" to R.string.sys_gifts,
        "sys_gifts_gifts" to R.string.sys_gifts_gifts,
        "sys_gifts_charity" to R.string.sys_gifts_charity,
        "sys_fees" to R.string.sys_fees,
        "sys_fees_bank" to R.string.sys_fees_bank,
        "sys_fees_taxes" to R.string.sys_fees_taxes,
        "sys_other" to R.string.sys_other,
        "sys_income_salary" to R.string.sys_income_salary,
        "sys_income_bonus" to R.string.sys_income_bonus,
        "sys_income_business" to R.string.sys_income_business,
        "sys_income_investment" to R.string.sys_income_investment,
        "sys_income_gifts" to R.string.sys_income_gifts,
        "sys_income_other" to R.string.sys_income_other,
        "sys_cash" to R.string.sys_cash,
    )

private val SystemIcons: Map<String, Int> =
    mapOf(
        "restaurant" to R.drawable.ic_restaurant,
        "directions_car" to R.drawable.ic_directions_car,
        "home" to R.drawable.ic_home,
        "shopping_bag" to R.drawable.ic_shopping_bag,
        "medical_services" to R.drawable.ic_medical_services,
        "school" to R.drawable.ic_school,
        "movie" to R.drawable.ic_movie,
        "family_restroom" to R.drawable.ic_family_restroom,
        "redeem" to R.drawable.ic_redeem,
        "receipt_long" to R.drawable.ic_receipt_long,
        "more_horiz" to R.drawable.ic_more_horiz,
        "work" to R.drawable.ic_work,
        "stars" to R.drawable.ic_stars,
        "storefront" to R.drawable.ic_storefront,
        "trending_up" to R.drawable.ic_trending_up,
        "card_giftcard" to R.drawable.ic_card_giftcard,
        "payments" to R.drawable.ic_payments,
    )

/** String resource of a system name key (`sys_*`), or null for an unknown key. */
@StringRes
fun systemNameRes(nameKey: String): Int? = SystemNames[nameKey]

/** Drawable of an icon name without the `ic_` prefix. An unknown name falls back to `ic_more_horiz`. */
@DrawableRes
fun iconRes(icon: String): Int = SystemIcons[icon] ?: R.drawable.ic_more_horiz

/** Localized name for a system row, or [name] for a user row or an unknown key. */
@Composable
fun displayName(
    name: String,
    nameKey: String?,
): String = nameKey?.let(::systemNameRes)?.let { stringResource(it) } ?: name
