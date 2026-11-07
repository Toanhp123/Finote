package io.github.toanhp123.finote.data.notification

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

// Spam, promo and alert phrases from the ignored samples of the notification corpus. Secrets (OTP and the like) are
// not here: they are SECRET_KEYWORDS in the domain and always drop the text. A row blocked by these keeps its text,
// so a phrase must be unlikely in a person's transfer note. Whole words after accent folding. No bare broad word
// ("thiết bị", "cảnh báo", "voucher"), and no "khuyến mãi": a MoMo promo refund is a real credit.
// SystemParserRulesCorpusTest is the judge.
private val IGNORE_KEYWORDS =
    listOf(
        "ưu đãi đặc quyền",
        "ưu đãi sinh nhật",
        "cảnh báo bảo mật",
        "đăng nhập trên thiết bị",
        "liên kết thành công",
        "chúc mừng sinh nhật quý khách",
        "chúc mừng quý khách",
        "momo tặng bạn",
        "nhận gói voucher",
        "nhắc nợ",
        "nhắc hạn thanh toán",
        "nhanh tay mở",
        "mở ngay trên app",
        "phê duyệt hạn mức",
        "rinh quà",
        "đổi quà ngay",
        "đổi quà hấp dẫn",
        "sinh lời mỗi ngày",
    )

private fun filterRule(
    id: String,
    packageName: String,
    bankName: String,
) = ParserRuleEntity(
    id = id,
    packageName = packageName,
    bankName = bankName,
    // Null: accept every channel. Fill it from real channel ids during dogfooding, then raise the version.
    channelIdAllowlist = null,
    ignoreKeywords = Json.encodeToString(IGNORE_KEYWORDS),
    priority = 0,
    isUserCustom = false,
    version = 2,
)

/** Filter-only rules for the supported apps. Raise a rule's `version` to roll a change out to existing installs. */
val SystemParserRules: List<ParserRuleEntity> =
    listOf(
        filterRule("vcb_filter_v1", "com.VCB", "Vietcombank"),
        filterRule("mb_filter_v1", "com.mbmobile", "MB Bank"),
        filterRule("momo_filter_v1", "com.mservice.momotransfer", "MoMo"),
        filterRule("tcb_filter_v1", "vn.com.techcombank.bb.app", "Techcombank"),
    )
