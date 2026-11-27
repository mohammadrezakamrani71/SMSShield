package com.smsshield.engine

data class CustomKeywordRule(
    val keyword: String,
    val weight: Int,
    val isEnabled: Boolean = true
)

data class PromoKeywordRule(
    val groupId: String,
    val label: String,
    val regex: Regex,
    val weight: Int
)

object SpamRules {

    const val DEFAULT_SPAM_THRESHOLD = 75
    const val SUSPICIOUS_OFFSET = 25
    const val PROMO_SCORE_CAP = 55
    const val MAX_TOTAL_SCORE = 100
    const val OPT_OUT_WEIGHT = 45
    const val BULK_SENDER_WEIGHT = 15
    const val URL_WEIGHT = 20
    const val PHONE_IN_BODY_WEIGHT = 15
    const val SERVICE_NEGATIVE_WEIGHT = -25

    val OTP_KEYWORDS = listOf(
        "رمز پویا",
        "کد تایید",
        "کد ورود",
        "رمز یکبار مصرف",
        "رمز یک بار مصرف",
        "otp",
        "verification code",
        "passcode"
    )

    val STRONG_OTP_KEYWORDS = listOf(
        "رمز پویا",
        "کد ورود"
    )

    val OTP_PROMO_GUARD_KEYWORDS = listOf(
        "کد تخفیف",
        "قرعه کشی",
        "جشنواره",
        "فروش ویژه"
    )

    val OTP_CODE_REGEX = Regex("\\b\\d{4,8}\\b")

    val BANK_TRANSACTION_VERBS = listOf(
        "برداشت",
        "واریز",
        "انتقال وجه",
        "کارت به کارت",
        "تراکنش"
    )

    val BANK_AMOUNT_CURRENCY_REGEX = Regex("\\d[\\d,]*\\s*(ریال|تومان)")

    val BANK_BALANCE_CONTEXT_KEYWORDS = listOf(
        "مانده",
        "موجودی",
        "حساب",
        "کارت"
    )

    val BANK_PROMO_GUARD_KEYWORDS = listOf(
        "جشنواره",
        "قرعه کشی",
        "تخفیف",
        "فروش ویژه"
    )

    val OPT_OUT_REGEX = Regex("لغو\\s*[=:]?\\s*11")

    val BULK_GATEWAY_SENDER_REGEX =
        Regex("^(\\+?98)?(1000|2000|3000|5000|9000)\\d*$")

    val URL_REGEX =
        Regex("(https?://|www\\.|b2n\\.ir|yun\\.ir|zil\\.ink|bit\\.ly|[a-z0-9-]+\\.(ir|com)/\\S+)")

    val PHONE_IN_BODY_REGEX =
        Regex("(09\\d{9}|021\\d{7,8}|0\\d{2,3}[- ]?\\d{7,8})")

    val PROMO_KEYWORD_RULES = listOf(
        PromoKeywordRule("foroush_vizhe", "فروش ویژه", Regex("فروش\\s+ویژه"), 25),
        PromoKeywordRule("pishnahad_vizhe", "پیشنهاد ویژه", Regex("پیشنهاد\\s+ویژه"), 25),
        PromoKeywordRule("ghore_keshi", "قرعه کشی", Regex("قرعه\\s+کشی"), 25),
        PromoKeywordRule("bedoon_pish", "بدون پیش پرداخت", Regex("بدون\\s+پیش\\s+پرداخت"), 25),
        PromoKeywordRule("takhfif", "کد تخفیف", Regex("کد\\s+تخفیف"), 25),
        PromoKeywordRule("takhfif", "تخفیف", Regex("تخفیف"), 20),
        PromoKeywordRule("jashnvare", "جشنواره", Regex("جشنواره"), 20),
        PromoKeywordRule("aghsat", "اقساط", Regex("اقساط(?:ی)?"), 20),
        PromoKeywordRule("sabt_sefaresh", "ثبت سفارش", Regex("ثبت\\s+سفارش"), 20),
        PromoKeywordRule("kharid_online", "خرید آنلاین", Regex("خرید\\s+(?:انلاین|آنلاین)"), 20),
        PromoKeywordRule("rayegan", "رایگان", Regex("رایگان"), 15)
    )

    val LEGITIMATE_SERVICE_WORDS = listOf(
        "سفارش",
        "مرسوله",
        "کد پیگیری",
        "تحویل",
        "قبض",
        "نوبت",
        "بیمه نامه"
    )
}
