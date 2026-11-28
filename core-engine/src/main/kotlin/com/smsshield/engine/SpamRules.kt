package com.smsshield.engine

import com.smsshield.engine.model.SpamCategory

data class CustomKeywordRule(
    val keyword: String,
    val weight: Int,
    val isEnabled: Boolean = true
)

data class PromoKeywordRule(
    val groupId: String,
    val label: String,
    val regex: Regex,
    val weight: Int,
    val category: SpamCategory = SpamCategory.SPAM_NUISANCE
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

    val PERCENT_DISCOUNT_REGEX =
        Regex("(?:\\b\\d{1,2}\\s*[%٪]|[%٪]\\s*\\d{1,2}\\b)")

    val PROMO_KEYWORD_RULES = listOf(
        // Offer / Promotion patterns
        PromoKeywordRule("black_friday", "تخفیف بلک فرایدی", Regex("تخفیف\\s+بلک\\s+فرایدی"), 25, SpamCategory.OFFER),
        PromoKeywordRule("black_friday", "بلک فرایدی", Regex("بلک\\s+فرایدی"), 25, SpamCategory.OFFER),
        PromoKeywordRule("black_friday", "جمعه سیاه", Regex("جمعه\\s+سیاه"), 25, SpamCategory.OFFER),
        PromoKeywordRule("foroush_vizhe", "فروش ویژه", Regex("فروش\\s+ویژه"), 25, SpamCategory.OFFER),
        PromoKeywordRule("foroush_vizhe", "فروش فوق العاده", Regex("فروش\\s+فوق\\s+العاده"), 25, SpamCategory.OFFER),
        PromoKeywordRule("pishnahad_vizhe", "پیشنهاد شگفت انگیز", Regex("پیشنهاد\\s+شگفت\\s+انگیز"), 25, SpamCategory.OFFER),
        PromoKeywordRule("pishnahad_vizhe", "پیشنهاد ویژه", Regex("پیشنهاد\\s+ویژه"), 25, SpamCategory.OFFER),
        PromoKeywordRule("takhfif", "کد تخفیف", Regex("کد\\s+تخفیف"), 25, SpamCategory.OFFER),
        PromoKeywordRule("takhfif", "بن تخفیف", Regex("بن\\s+تخفیف"), 25, SpamCategory.OFFER),
        PromoKeywordRule("takhfif", "تخفیف ویژه", Regex("تخفیف\\s+ویژه"), 25, SpamCategory.OFFER),
        PromoKeywordRule("takhfif", "تخفیف تا", Regex("تخفیف\\s+تا"), 20, SpamCategory.OFFER),
        PromoKeywordRule("takhfif", "تخفیف", Regex("تخفیف"), 20, SpamCategory.OFFER),
        PromoKeywordRule("gift_code", "کد هدیه", Regex("کد\\s+هدیه"), 25, SpamCategory.OFFER),
        PromoKeywordRule("jashnvare", "جشنواره فروش", Regex("جشنواره\\s+فروش"), 20, SpamCategory.OFFER),
        PromoKeywordRule("jashnvare", "جشنواره", Regex("جشنواره"), 20, SpamCategory.OFFER),
        PromoKeywordRule("haraj", "حراج", Regex("حراج(?:ی)?"), 20, SpamCategory.OFFER),
        PromoKeywordRule("first_purchase", "خرید اول", Regex("خرید\\s+اول"), 20, SpamCategory.OFFER),
        PromoKeywordRule("sabt_sefaresh", "ثبت سفارش", Regex("ثبت\\s+سفارش"), 20, SpamCategory.OFFER),
        PromoKeywordRule("kharid_online", "خرید آنلاین", Regex("خرید\\s+(?:انلاین|آنلاین)"), 20, SpamCategory.OFFER),

        // Nuisance / High-Risk Spam patterns
        PromoKeywordRule("beauty_clinic", "کلینیک زیبایی", Regex("کلینیک\\s+زیبایی"), 25, SpamCategory.SPAM_NUISANCE),
        PromoKeywordRule("laser_hair", "لیزر موهای زائد", Regex("لیزر\\s+موهای\\s+زائد"), 25, SpamCategory.SPAM_NUISANCE),
        PromoKeywordRule("botox", "بوتاکس", Regex("بوتاکس"), 25, SpamCategory.SPAM_NUISANCE),
        PromoKeywordRule("hair_transplant", "کاشت مو", Regex("کاشت\\s+مو"), 25, SpamCategory.SPAM_NUISANCE),
        PromoKeywordRule("eyebrow_transplant", "کاشت ابرو", Regex("کاشت\\s+ابرو"), 25, SpamCategory.SPAM_NUISANCE),
        PromoKeywordRule("instant_loan", "وام فوری", Regex("وام\\s+فوری"), 30, SpamCategory.SPAM_NUISANCE),
        PromoKeywordRule("no_guarantor_loan", "تسهیلات بدون ضامن", Regex("تسهیلات\\s+بدون\\s+ضامن"), 30, SpamCategory.SPAM_NUISANCE),
        PromoKeywordRule("official_guarantor", "ضامن رسمی", Regex("ضامن\\s+رسمی"), 25, SpamCategory.SPAM_NUISANCE),
        PromoKeywordRule("property_presale", "پیش فروش ملک", Regex("پیش\\s+فروش\\s+ملک"), 25, SpamCategory.SPAM_NUISANCE),
        PromoKeywordRule("crypto_signal", "سیگنال ارز", Regex("سیگنال\\s+ارز"), 30, SpamCategory.SPAM_NUISANCE),
        PromoKeywordRule("guaranteed_profit", "سود تضمینی", Regex("سود\\s+تضمینی"), 30, SpamCategory.SPAM_NUISANCE),
        PromoKeywordRule("exchange_promo", "صرافی", Regex("صرافی"), 20, SpamCategory.SPAM_NUISANCE),
        PromoKeywordRule("ghore_keshi", "قرعه کشی", Regex("قرعه\\s+کشی"), 25, SpamCategory.SPAM_NUISANCE),
        PromoKeywordRule("winner_scam", "برنده شدید", Regex("برنده\\s+شدید"), 30, SpamCategory.SPAM_NUISANCE),
        PromoKeywordRule("last_minute_tour", "تور لحظه آخری", Regex("تور\\s+لحظه\\s+(?:اخری|آخری)"), 25, SpamCategory.SPAM_NUISANCE),
        PromoKeywordRule("gift_charge", "شارژ هدیه", Regex("شارژ\\s+هدیه"), 25, SpamCategory.SPAM_NUISANCE),
        PromoKeywordRule("lottery_reg", "ثبت نام لاتاری", Regex("ثبت\\s+نام\\s+لاتاری"), 30, SpamCategory.SPAM_NUISANCE),
        PromoKeywordRule("bedoon_pish", "بدون پیش پرداخت", Regex("بدون\\s+پیش\\s+پرداخت"), 25, SpamCategory.SPAM_NUISANCE),
        PromoKeywordRule("aghsat", "فروش اقساطی", Regex("فروش\\s+اقساطی"), 20, SpamCategory.SPAM_NUISANCE),
        PromoKeywordRule("aghsat", "اقساط", Regex("اقساط(?:ی)?"), 20, SpamCategory.SPAM_NUISANCE),
        PromoKeywordRule("rayegan", "مشاوره رایگان", Regex("مشاوره\\s+رایگان"), 20, SpamCategory.SPAM_NUISANCE),
        PromoKeywordRule("rayegan", "ارسال رایگان", Regex("ارسال\\s+رایگان"), 15, SpamCategory.OFFER),
        PromoKeywordRule("rayegan", "رایگان", Regex("رایگان"), 15, SpamCategory.OFFER)
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

    val SERVICE_SAFE_KEYWORDS: List<String>
        get() = LEGITIMATE_SERVICE_WORDS
}
