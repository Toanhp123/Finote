package io.github.toanhp123.finote.ui.common.format

/**
 * Icon names (without `ic_`) that a category may take, by picker section. Each name is in exactly one group and
 * resolves through [iconRes]; `SystemResourcesTest` guards both and that every seeded category icon is listed.
 */
val CategoryIconGroups: Map<CategoryIconGroup, List<String>> =
    mapOf(
        CategoryIconGroup.FOOD to
            listOf(
                "restaurant",
                "local_cafe",
                "rice_bowl",
                "local_pizza",
                "bakery_dining",
                "local_bar",
                "icecream",
                "local_grocery_store",
            ),
        CategoryIconGroup.TRANSPORT to
            listOf(
                "directions_car",
                "local_gas_station",
                "two_wheeler",
                "local_parking",
                "directions_bus",
                "flight",
                "local_taxi",
                "train",
                "build",
            ),
        CategoryIconGroup.HOME to
            listOf(
                "home",
                "bolt",
                "opacity",
                "wifi",
                "phone",
                "apartment",
                "weekend",
                "cleaning_services",
                "local_laundry_service",
            ),
        CategoryIconGroup.SHOPPING to
            listOf("shopping_bag", "storefront", "shopping_cart", "checkroom", "devices", "watch"),
        CategoryIconGroup.HEALTH to
            listOf("medical_services", "local_pharmacy", "fitness_center", "spa", "healing", "content_cut"),
        CategoryIconGroup.FUN to
            listOf(
                "movie",
                "sports_esports",
                "luggage",
                "music_note",
                "sports_soccer",
                "beach_access",
                "camera_alt",
                "hotel",
            ),
        CategoryIconGroup.STUDY to listOf("school", "menu_book", "science", "language"),
        CategoryIconGroup.FAMILY to
            listOf(
                "family_restroom",
                "child_care",
                "pets",
                "redeem",
                "card_giftcard",
                "volunteer_activism",
                "cake",
                "toys",
            ),
        CategoryIconGroup.MONEY to
            listOf(
                "payments",
                "local_atm",
                "local_offer",
                "account_balance",
                "account_balance_wallet",
                "credit_card",
                "attach_money",
                "trending_up",
                "receipt_long",
                "work",
                "stars",
            ),
        CategoryIconGroup.OTHER to listOf("more_horiz", "category", "label", "bookmark"),
    )
