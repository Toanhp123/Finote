package io.github.toanhp123.finote.data.notification

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

// Spam, promo and alert phrases from the ignored samples of the notification corpus, one list per bank. Secrets (OTP
// and the like) are not here: they are SECRET_KEYWORDS in the domain and always drop the text. A row blocked by these
// keeps its text, so a phrase must be unlikely in a person's transfer note. Whole words after accent folding. No bare
// broad word ("thiết bị", "cảnh báo", "voucher"), and no "khuyến mãi": a MoMo promo refund is a real credit.
// A phrase that two or more banks need stays shared. SystemParserRulesCorpusTest is the judge.
private val SHARED_KEYWORDS =
    listOf(
        "cảnh báo bảo mật",
        "đăng nhập trên thiết bị",
        "nhắc nợ",
    )

private val VCB_KEYWORDS =
    SHARED_KEYWORDS +
        listOf(
            "ưu đãi đặc quyền",
            "chúc mừng sinh nhật quý khách",
        )

private val MB_KEYWORDS =
    SHARED_KEYWORDS +
        listOf(
            "liên kết thành công",
            "chúc mừng quý khách",
            "nhanh tay mở",
            "phê duyệt hạn mức",
        )

private val MOMO_KEYWORDS =
    SHARED_KEYWORDS +
        listOf(
            "momo tặng bạn",
            "nhận gói voucher",
            "rinh quà",
            "đổi quà hấp dẫn",
        )

private val TCB_KEYWORDS =
    SHARED_KEYWORDS +
        listOf(
            "ưu đãi sinh nhật",
            "mở ngay trên app",
            "đổi quà ngay",
            "sinh lời mỗi ngày",
            "nhắc hạn thanh toán",
        )

// Regex building blocks. The parser reads named groups only: sign, amount, mask, balance, merchant, note.
private const val MONEY = """[\d.,]+"""

// Last four digits of an account number. A masked card or wallet number gives the same four.
private const val ACCOUNT_MASK = """\d*(?<mask>\d{4})"""

/**
 * The `ND:` text of a bank alert. Whole text goes to `note`. A merchant is read only after a known phrase
 * ([merchantStart], case-insensitive) and only when it runs up to [merchantStop] or the end; otherwise it stays null.
 * A stop is case-sensitive unless it carries its own `(?i:…)`, and a stop that is a common word needs a digit after it.
 * A text cut by "…" (collapsed notification) gives no note: a cut note is never a note.
 */
private fun noteGroup(
    merchantStart: String,
    merchantStop: String,
) = """(?<note>(?:(?i:$merchantStart)(?<merchant>[^.…]+?)(?=$merchantStop|\s*\z))?[^…]*?)\s*\z"""

private val VCB_NOTE =
    noteGroup("Thanh toan QR tai |NAP TIEN VAO |(?=\\S+ thanh toan don hang)", "(?i: thanh toan don hang)")
private val MB_NOTE = noteGroup("THANH TOAN HOA DON TIEN DIEN ", " MA KH| THANG \\d")
private val TCB_NOTE = noteGroup("Thanh toan QR tai |Thanh toan hoa don Internet ", " thang \\d")

// MoMo wallet or source in brackets: `(090***1234)` or `(***4567)`.
private const val MOMO_MASK = """\((?:\d*\*+)?(?<mask>\d{4})\)"""
private const val MOMO_BALANCE = """(?:\. Số dư(?: ví)?: (?<balance>$MONEY)đ)?"""

// What follows the payee of a MoMo payment: ` từ <source> (mask)` or `. Nguồn tiền: <source> (mask)`, then the end.
// The source never holds " từ ", so a payee such as "Quỹ từ thiện ABC" is not cut at its own " từ ". A text cut by "…"
// has no mask: it needs a `Ví` source or `Nguồn tiền`, so a cut payee name is not read as a payee plus a source.
// Anything else after the payee ("đã được hoàn lại", "không thành công") is no match: a refund is never an expense.
private const val MOMO_PAY_SOURCE =
    """(?:(?: từ |\. Nguồn tiền: )(?:(?! từ )[^(…])*$MOMO_MASK$MOMO_BALANCE\.?\s*\z""" +
        """|(?: từ Ví |\. Nguồn tiền: )(?:(?! từ )[^…])*…\s*\z)"""

private class Bank(
    private val idPrefix: String,
    private val packageName: String,
    private val bankName: String,
    keywords: List<String>,
) {
    private val keywordsJson = Json.encodeToString(keywords)

    /** Every rule of a bank carries the bank's keywords. Null [fallbackType]: the pattern has a `sign` group. */
    fun rule(
        shape: String,
        pattern: String,
        fallbackType: String? = null,
    ) = ParserRuleEntity(
        id = "${idPrefix}_${shape}_v1",
        packageName = packageName,
        bankName = bankName,
        // Null: accept every channel. Fill it from real channel ids during dogfooding, then raise the version.
        channelIdAllowlist = null,
        ignoreKeywords = keywordsJson,
        priority = 0,
        isUserCustom = false,
        version = 1,
        regexPattern = pattern,
        fallbackTransactionType = fallbackType,
        dateFormatPattern = null,
        isActive = true,
        updatedAt = 0L,
    )
}

private val vcb = Bank("vcb", "com.VCB", "Vietcombank", VCB_KEYWORDS)
private val mb = Bank("mb", "com.mbmobile", "MB Bank", MB_KEYWORDS)
private val momo = Bank("momo", "com.mservice.momotransfer", "MoMo", MOMO_KEYWORDS)
private val tcb = Bank("tcb", "vn.com.techcombank.bb.app", "Techcombank", TCB_KEYWORDS)

private val vcbRules =
    listOf(
        // `SD TK 0011001234567 -50,000 VND lúc …. Số dư … VND. Ref …. ND: …`; the tail is cut in a collapsed text.
        vcb.rule(
            "account",
            """SD TK $ACCOUNT_MASK (?<sign>[+-])(?<amount>$MONEY) VND l[úu]c [^.]+""" +
                """(?:\. S[ốo] d[ưu]:? (?<balance>$MONEY) VND(?:(?:\. Ref \S+?)?\. ND: $VCB_NOTE)?)?""",
        ),
        vcb.rule(
            "alert",
            """Bi[ếe]n [đd][ộo]ng s[ốo] d[ưu]: TK $ACCOUNT_MASK (?<sign>[+-])(?<amount>$MONEY) VND v[àa]o [^.]+""" +
                """(?:\. S[ốo] d[ưu]:? (?<balance>$MONEY) VND(?:\. Giao d[ịi]ch: $VCB_NOTE)?)?""",
        ),
        // Card spend (`GD -… tại SHOP`) and card refund (`hoàn tiền +… từ SHOP`).
        vcb.rule(
            "card",
            """Th[ẻe] VCB [^.]*\.\.\.(?<mask>\d{4}) (?:GD|ho[àa]n ti[ềe]n) (?<sign>[+-])(?<amount>$MONEY) VND """ +
                """(?:t[ạa]i|t[ừu]) (?<merchant>.+?) l[úu]c [^.]+\. S[ốo] d[ưu] kh[ảa] d[ụu]ng: """ +
                """(?<balance>$MONEY) VND""",
        ),
    )

private val mbRules =
    listOf(
        // `TK 034123456789 GD: -100,000VND 06/11/26 11:20. SD: …VND. ND: …`; the tail is cut in a collapsed text.
        mb.rule(
            "account",
            """TK $ACCOUNT_MASK GD: (?<sign>[+-])(?<amount>$MONEY) ?VND [^.]+""" +
                """(?:\. SD: (?<balance>$MONEY) ?VND(?:\. ND: $MB_NOTE)?)?""",
        ),
        // The card line gives the remaining limit, which is not a balance, so there is no balance group.
        mb.rule(
            "card",
            """Th[ẻe] t[íi]n d[ụu]ng MB [^.]*\.\.\.(?<mask>\d{4}) (?:GD|hoan tien) thanh cong: """ +
                """(?<sign>[+-])(?<amount>$MONEY) VND (?:tai|tu) (?<merchant>[^.]+?)\. """ +
                """Han muc con lai: $MONEY VND""",
        ),
    )

private val momoRules =
    listOf(
        // `đã thanh toán 35.000đ cho SHOP từ Ví MoMo (090***1234). Số dư ví: …` or `… cho Đơn hàng X. Nguồn tiền: …`.
        // A collapsed text is cut inside the source part.
        momo.rule(
            "pay",
            """Bạn đã thanh toán (?<amount>$MONEY)đ cho (?:Đơn hàng )?(?<merchant>[^.]+?)$MOMO_PAY_SOURCE""",
            "EXPENSE",
        ),
        // The bracket after the sender is the sender's phone, not the user's account: no mask.
        momo.rule(
            "received",
            """Bạn đã nhận được (?<amount>$MONEY)đ từ (?<merchant>[^(]+?) \((?:\d*\*+)?\d{4}\)""" +
                """(?:\. Lời nhắn: (?:(?<note>[^…]*?)\.?\s*\z)?|\.?\s*\z)""",
            "INCOME",
        ),
        momo.rule(
            "refund",
            """Bạn đã nhận được hoàn tiền (?<amount>$MONEY)đ từ (?<merchant>[^.]+?)(?: cho [^.]+?)? """ +
                """vào Ví MoMo $MOMO_MASK$MOMO_BALANCE""",
            "INCOME",
        ),
        momo.rule(
            "topup_bank",
            """Bạn vừa nạp (?<amount>$MONEY)đ vào Ví MoMo $MOMO_MASK từ Ngân hàng """ +
                """(?<merchant>[^(]+?) \([^)]*\)$MOMO_BALANCE""",
            "INCOME",
        ),
        momo.rule(
            "topup_phone",
            """Bạn vừa nạp (?<amount>$MONEY)đ cho số thuê bao \d+ qua Ví MoMo $MOMO_MASK$MOMO_BALANCE""",
            "EXPENSE",
        ),
    )

private val tcbRules =
    listOf(
        // Two phrasings of one shape: `Giao dịch thành công: TK …` and `TK … GD: …`.
        tcb.rule(
            "account",
            """TK $ACCOUNT_MASK (?:GD: )?(?<sign>[+-])(?<amount>$MONEY) VND l[úu]c [^.]+\. """ +
                """S[ốo] d[ưu](?: kh[ảa] d[ụu]ng)?: (?<balance>$MONEY) VND(?:\. ND: $TCB_NOTE)?""",
        ),
        tcb.rule(
            "card",
            """Th[ẻe] t[íi]n d[ụu]ng [^(]*\(\.{3}(?<mask>\d{4})\) (?:GD th[àa]nh c[ôo]ng|ho[àa]n ti[ềe]n): """ +
                """(?<sign>[+-])(?<amount>$MONEY) VND (?:t[ạa]i|t[ừu]) (?<merchant>.+?) l[úu]c [^.]+\. """ +
                """H[ạa]n m[ứu]c kh[ảa] d[ụu]ng: $MONEY VND""",
        ),
    )

/** Regex rules for the supported apps. Raise a rule's `version` to roll a change out to existing installs. */
val SystemParserRules: List<ParserRuleEntity> = vcbRules + mbRules + momoRules + tcbRules
