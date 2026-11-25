package io.github.toanhp123.finote.data.category.seed

import io.github.toanhp123.finote.data.category.local.entity.CategoryKeywordRuleEntity
import io.github.toanhp123.finote.domain.category.model.KeywordMatchType
import io.github.toanhp123.finote.domain.common.normalizeKeyword

/**
 * A system rule. The id is `kw_` plus the normalized keyword, so it stays stable while the display text is edited.
 * Times stay 0: a seed is data, not an event, and a user switch writes its own `updatedAt`.
 */
private fun rule(
    keyword: String,
    merchantName: String,
    categoryId: String,
): CategoryKeywordRuleEntity {
    val normalized = normalizeKeyword(keyword)
    return CategoryKeywordRuleEntity(
        id = "kw_${normalized.replace(' ', '_')}",
        keyword = keyword,
        normalizedKeyword = normalized,
        matchType = KeywordMatchType.CONTAINS,
        normalizedMerchantName = merchantName,
        categoryId = categoryId,
        priority = 0,
        isUserDefined = false,
        isActive = true,
        version = 1,
        createdAt = 0L,
        updatedAt = 0L,
    )
}

/**
 * Brands that sell one kind of thing, so the category is rarely wrong. Wallets and marketplaces are not here
 * (`momo`, `zalopay`, `shopee`, `lazada`, `tiki`), and neither is a keyword that is also a common given name
 * (`phuc long`, `long chau`, `mai linh`): a transfer to a person must not match. Every keyword has 3+ characters.
 * To change a rule, edit it and raise its `version`.
 */
val SystemKeywordRules: List<CategoryKeywordRuleEntity> =
    listOf(
        rule("highlands", "Highlands Coffee", "sys_food_coffee"),
        rule("the coffee house", "The Coffee House", "sys_food_coffee"),
        rule("cong ca phe", "Cộng Cà Phê", "sys_food_coffee"),
        rule("starbucks", "Starbucks", "sys_food_coffee"),
        rule("kfc", "KFC", "sys_food_restaurants"),
        rule("lotteria", "Lotteria", "sys_food_restaurants"),
        rule("jollibee", "Jollibee", "sys_food_restaurants"),
        rule("bach hoa xanh", "Bách Hóa Xanh", "sys_food_groceries"),
        rule("winmart", "WinMart", "sys_food_groceries"),
        rule("xanh sm", "Xanh SM", "sys_transport_taxi"),
        rule("vinasun", "Vinasun", "sys_transport_taxi"),
        rule("petrolimex", "Petrolimex", "sys_transport_fuel"),
        rule("pvoil", "PVOIL", "sys_transport_fuel"),
        rule("netflix", "Netflix", "sys_entertainment_media"),
        rule("spotify", "Spotify", "sys_entertainment_media"),
        rule("cgv", "CGV", "sys_entertainment_media"),
        rule("pharmacity", "Pharmacity", "sys_health_pharmacy"),
        rule("the gioi di dong", "Thế Giới Di Động", "sys_shopping_electronics"),
        rule("dien may xanh", "Điện Máy Xanh", "sys_shopping_electronics"),
    )
