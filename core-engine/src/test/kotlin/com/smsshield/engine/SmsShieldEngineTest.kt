package com.smsshield.engine

import com.smsshield.engine.model.BlockedSenderRule
import com.smsshield.engine.model.SenderMatchType
import com.smsshield.engine.model.SmsInput
import com.smsshield.engine.model.SmsVerdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    fun `normalizes Arabic presentation forms using Unicode compatibility normalization`() {
        val raw = "\uFEFB \uFEF7 \uFB8E\uFB90"
        val normalized = PersianNormalizer.normalize(raw)

        assertTrue(normalized.contains("لا"))
        assertTrue(normalized.contains("ک"))
    }

    @Test
    fun `normalizes Latin uppercase and invisible directional marks`() {
        val raw = "\u200FOTP\u200E Code: \uFEFF۱۲۳۴۵۶  for  B2N.IR"
        val normalized = PersianNormalizer.normalize(raw)

        assertEquals("otp code: 123456 for b2n.ir", normalized)
    }

    @Test
    fun `normalizes Iranian sender formats consistently`() {
        val expected = "550021"
        assertEquals(expected, PersianNormalizer.normalizeSender("550021"))
        assertEquals(expected, PersianNormalizer.normalizeSender("98550021"))
        assertEquals(expected, PersianNormalizer.normalizeSender("+98550021"))
        assertEquals(expected, PersianNormalizer.normalizeSender("0098550021"))
        assertEquals(expected, PersianNormalizer.normalizeSender("۵۵۰۰۲۱"))
        assertEquals(expected, PersianNormalizer.normalizeSender("+98 (550)-021"))
    }

    @Test
    fun `genuine otp verification SMS stays safe`() {
        val sms = SmsInput(
            sender = "30001234",
            body = "کد تایید شما: ۴۸۲۹۱۰"
        )
        val result = classifier.classify(sms)

        assertEquals(SmsVerdict.SAFE, result.verdict)
        assertEquals(0, result.score)
    }

    @Test
    fun `strong otp phrase stays safe even if gateway appends opt-out 11`() {
        val sms = SmsInput(
            sender = "30001234",
            body = "رمز پویا جهت خرید اینترنتی: ۴۸۲۹۱۰\nلغو 11"
        )
        val result = classifier.classify(sms)

        assertEquals(SmsVerdict.SAFE, result.verdict)
        assertEquals(0, result.score)
    }

    @Test
    fun `passcode otp phrase with standalone digits stays safe`() {
        val sms = SmsInput(
            sender = "Verify",
            body = "Your login passcode is 938412"
        )
        val result = classifier.classify(sms)

        assertEquals(SmsVerdict.SAFE, result.verdict)
        assertEquals(0, result.score)
    }

    @Test
    fun `promotional SMS with verification-like wording and opt-out is NOT bypassed as OTP`() {
        val sms = SmsInput(
            sender = "50001234",
            body = "کد تایید شرکت در جشنواره فروش ویژه: 123456 با کد تخفیف! b2n.ir/win لغو 11"
        )
        val result = classifier.classify(sms)

        assertEquals(SmsVerdict.SPAM, result.verdict)
        assertTrue(result.score >= 75)
    }

    @Test
    fun `bank transaction exception keeps transaction SMS safe without auto-whitelisting sender`() {
        val bankTx = SmsInput(
            sender = "30009999",
            body = "بانک نمونه\nبرداشت ۵۰۰,۰۰۰ ریال از حساب\nمانده: ۱۲,۰۰۰,۰۰۰ ریال"
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
    fun `festival ad mentioning toman and account is NOT bypassed as bank transaction`() {
        val sms = SmsInput(
            sender = "30008888",
            body = "جشنواره بزرگ! با افتتاح حساب ۵۰۰,۰۰۰ تومان جایزه و واریز هدیه بگیرید b2n.ir/gift لغو 11"
        )
        val result = classifier.classify(sms)

        assertEquals(SmsVerdict.SPAM, result.verdict)
        assertTrue(result.score >= 75)
    }

    @Test
    fun `bank campaign SMS with deposit verb and opt-out 11 is NOT bypassed`() {
        val sms = SmsInput(
            sender = "30007777",
            body = "واریز 100000 تومان اعتبار هدیه خرید آنلاین در فروش ویژه b2n.ir/bank لغو 11"
        )
        val result = classifier.classify(sms)

        assertEquals(SmsVerdict.SPAM, result.verdict)
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
    fun `truncation safety guard always returns SAFE with score 0 when sender is not blocked`() {
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
    fun `manual whitelist overrides spam signals and sender blacklist`() {
        val sms = SmsInput(
            sender = "+98 3000 5555",
            body = "فروش ویژه با کد تخفیف b2n.ir/test تماس 02188881234 لغو 11"
        )
        val result = classifier.classify(
            input = sms,
            whitelistedSenders = setOf("+9830005555"),
            blockedSenders = listOf(BlockedSenderRule("30005555", SenderMatchType.EXACT))
        )

        assertEquals(SmsVerdict.SAFE, result.verdict)
        assertEquals(0, result.score)
        assertNull(result.matchedBlockedSenderPattern)
    }

    @Test
    fun `exact and prefix sender blocking triggers SPAM 100 even when body is truncated`() {
        val exactRule = BlockedSenderRule("550021", SenderMatchType.EXACT)
        val exactResult = classifier.classify(
            input = SmsInput(sender = "+98550021", body = "سلام...", isPossiblyTruncated = true),
            blockedSenders = listOf(exactRule)
        )
        assertEquals(SmsVerdict.SPAM, exactResult.verdict)
        assertEquals(100, exactResult.score)
        assertEquals("550021", exactResult.matchedBlockedSenderPattern)
        assertTrue(exactResult.reasons.first().contains("550021"))

        val prefixRule = BlockedSenderRule("550021*", SenderMatchType.PREFIX)
        val prefixResult = classifier.classify(
            input = SmsInput(sender = "0098550021999", body = "متن کوتاه…", isPossiblyTruncated = true),
            blockedSenders = listOf(prefixRule)
        )
        assertEquals(SmsVerdict.SPAM, prefixResult.verdict)
        assertEquals(100, prefixResult.score)
        assertEquals("550021", prefixResult.matchedBlockedSenderPattern)
    }

    @Test
    fun `custom user keywords contribute to score, attribute hits, and respect global ceiling of 100`() {
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
        assertEquals(listOf("ارز دیجیتال"), result.matchedCustomKeywords)
        assertTrue(result.reasons.any { it.contains("ارز دیجیتال") })
    }

    @Test
    fun `RulePackCodec round-trips JSON and share code, clamps weights, and rejects malformed payloads`() {
        val original = RulePack(
            packName = "پک تست تبلیغات",
            authorName = "علی",
            description = "پک نمونه",
            category = "عمومی",
            caughtCount = 42,
            keywords = listOf(
                KeywordRuleItem("کلینیک زیبایی", weight = 150),
                KeywordRuleItem("وام فوری", weight = 1),
                KeywordRuleItem("   ", weight = 25)
            ),
            blockedSenders = listOf(
                BlockedSenderItem("550021*", SenderMatchType.PREFIX),
                BlockedSenderItem("   ", SenderMatchType.EXACT)
            )
        )

        val jsonStr = RulePackCodec.encodePackToJson(original)
        val decodedJson = RulePackCodec.decodePackFromJson(jsonStr).getOrThrow()
        assertEquals("پک تست تبلیغات", decodedJson.packName)
        assertEquals(2, decodedJson.keywords.size)
        assertEquals(100, decodedJson.keywords[0].weight)
        assertEquals(5, decodedJson.keywords[1].weight)
        assertEquals(1, decodedJson.blockedSenders.size)
        assertEquals(SenderMatchType.PREFIX, decodedJson.blockedSenders[0].matchType)

        val shareCode = RulePackCodec.encodePackToShareCode(original)
        assertTrue(shareCode.startsWith(RulePackCodec.SHARE_CODE_PREFIX))
        val decodedCode = RulePackCodec.decodePackFromShareCodeOrJson(shareCode).getOrThrow()
        assertEquals(decodedJson, decodedCode)

        assertTrue(RulePackCodec.decodePackFromShareCodeOrJson("not-valid-json").isFailure)
        assertTrue(RulePackCodec.decodePackFromShareCodeOrJson("SMSSHIELD-PACK:v1:!!!bad!!!").isFailure)
        assertTrue(RulePackCodec.decodePackFromJson("{}").isFailure)
    }
}
