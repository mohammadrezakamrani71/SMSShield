package com.smsshield.engine

import com.smsshield.engine.model.SmsInput
import com.smsshield.engine.model.SmsVerdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsShieldEngineTest {

    private val classifier = SpamClassifier()

    @Test
    fun `normalizes Arabic letters, Persian and Arabic digits, ZWNJ, diacritics, and spaces`() {
        val raw = "  فروش\u200Cويژه\u064E كالا\u200B با \u0665\u0660٪ تخفيف أينستاگرام؟   لغو=۱۱  "
        val normalized = PersianNormalizer.normalize(raw)

        assertEquals("فروش ویژه کالا با 50٪ تخفیف اینستاگرام؟ لغو=11", normalized)
    }

    @Test
    fun `normalizes Latin uppercase and invisible directional marks`() {
        val raw = "\u200FOTP\u200E Code: \uFEFF۱۲۳۴۵۶  for  B2N.IR"
        val normalized = PersianNormalizer.normalize(raw)

        assertEquals("otp code: 123456 for b2n.ir", normalized)
    }

    @Test
    fun `normalizes Arabic presentation forms using Unicode compatibility normalization`() {
        assertEquals("لا", PersianNormalizer.normalize("ﻻ"))
    }

    @Test
    fun `otp exception keeps verification SMS safe even with bulk sender and opt-out`() {
        val sms = SmsInput(
            sender = "30001234",
            body = "کد تایید شما: ۴۸۲۹۱۰\nلغو 11"
        )
        val result = classifier.classify(sms)

        assertEquals(SmsVerdict.SAFE, result.verdict)
        assertEquals(0, result.score)
    }

    @Test
    fun `bank transaction exception keeps transaction SMS safe without auto-whitelisting sender`() {
        val bankTx = SmsInput(
            sender = "30009999",
            body = "بانک نمونه\nبرداشت ۵۰۰,۰۰۰ ریال از حساب\nمانده: ۱۲,۰۰۰,۰۰۰ ریال\nلغو 11"
        )
        val txResult = classifier.classify(bankTx)
        assertEquals(SmsVerdict.SAFE, txResult.verdict)
        assertEquals(0, txResult.score)

        val promoLink = "https:" + "//shop.example.ir/win"
        val bankPromo = SmsInput(
            sender = "30009999",
            body = "جشنواره فروش ویژه و قرعه کشی با کد تخفیف! $promoLink لغو11"
        )
        val promoResult = classifier.classify(bankPromo)
        assertEquals(SmsVerdict.SPAM, promoResult.verdict)
    }

    @Test
    fun `opt-out 11 combined with legitimate service words stays SAFE`() {
        val sms = SmsInput(
            sender = "20001234",
            body = "مشتری گرامی، سفارش شما آماده تحویل است. کد پیگیری: 987654\nلغو 11"
        )
        val result = classifier.classify(sms)

        assertEquals(35, result.score)
        assertEquals(SmsVerdict.SAFE, result.verdict)
    }

    @Test
    fun `overlapping roots aghsat and aghsati are not double counted`() {
        val sms = SmsInput(
            sender = "Shop",
            body = "خرید اقساط و فروش اقساطی بدون ضامن"
        )
        val result = classifier.classify(sms)

        assertEquals(20, result.score)
        assertEquals(SmsVerdict.SAFE, result.verdict)
    }

    @Test
    fun `overlapping takhfif and code takhfif are not double counted`() {
        val sms = SmsInput(
            sender = "Shop",
            body = "کد تخفیف ویژه با تخفیف استثنایی"
        )
        val result = classifier.classify(sms)

        assertEquals(25, result.score)
    }

    @Test
    fun `promotional keyword score is capped at 55`() {
        val sms = SmsInput(
            sender = "Brand",
            body = "فروش ویژه و پیشنهاد ویژه در جشنواره بزرگ قرعه کشی بدون پیش پرداخت با کد تخفیف و ارسال رایگان و خرید آنلاین"
        )
        val result = classifier.classify(sms)

        assertEquals(55, result.score)
        assertEquals(SmsVerdict.SUSPICIOUS, result.verdict)
    }

    @Test
    fun `truncation safety guard always returns SAFE with score 0`() {
        val sms = SmsInput(
            sender = "50001234",
            body = "فروش ویژه جشنواره قرعه کشی b2n.ir/abc تماس 09121234567 لغو 11...",
            isPossiblyTruncated = true
        )
        val result = classifier.classify(sms)

        assertEquals(SmsVerdict.SAFE, result.verdict)
        assertEquals(0, result.score)
    }

    @Test
    fun `manual whitelist overrides spam signals`() {
        val sms = SmsInput(
            sender = "+98 3000 5555",
            body = "فروش ویژه با کد تخفیف b2n.ir/test تماس 02188881234 لغو 11"
        )
        val result = classifier.classify(
            input = sms,
            whitelistedSenders = setOf("+9830005555")
        )

        assertEquals(SmsVerdict.SAFE, result.verdict)
        assertEquals(0, result.score)
    }

    @Test
    fun `custom user keywords contribute to score without exceeding the global score ceiling`() {
        val sms = SmsInput(
            sender = "50001122",
            body = "ثبت نام کلاس ارز دیجیتال با لینک b2n.ir/crypto لغو11"
        )
        val result = classifier.classify(
            input = sms,
            customRules = listOf(
                CustomKeywordRule(keyword = "ارز دیجیتال", weight = 30, isEnabled = true)
            )
        )

        assertEquals(100, result.score)
        assertEquals(SmsVerdict.SPAM, result.verdict)
        assertTrue(result.reasons.any { it.contains("ارز دیجیتال") })
    }
}
