package com.smsshield.engine

import com.smsshield.engine.model.ClassificationResult
import com.smsshield.engine.model.SmsInput
import com.smsshield.engine.model.SmsVerdict

class SpamClassifier(
    private val scoringEngine: ScoringEngine = ScoringEngine()
) {

    fun classify(
        input: SmsInput,
        whitelistedSenders: Set<String> = emptySet(),
        customRules: List<CustomKeywordRule> = emptyList(),
        spamThreshold: Int = SpamRules.DEFAULT_SPAM_THRESHOLD
    ): ClassificationResult {
        if (input.isPossiblyTruncated) {
            return ClassificationResult(
                verdict = SmsVerdict.SAFE,
                score = 0,
                reasons = listOf("استثنای ایمن: متن پیام احتمالاً ناقص است")
            )
        }

        val normalizedSender = PersianNormalizer.normalizeSender(input.sender)
        val normalizedBody = PersianNormalizer.normalize(input.body)

        val normalizedWhitelist = whitelistedSenders
            .map { PersianNormalizer.normalizeSender(it) }
            .filter { it.isNotEmpty() }
            .toSet()

        if (normalizedSender.isNotEmpty() && normalizedSender in normalizedWhitelist) {
            return ClassificationResult(
                verdict = SmsVerdict.SAFE,
                score = 0,
                reasons = listOf("استثنای ایمن: فرستنده در لیست مجاز کاربر است")
            )
        }

        val hasOtpKeyword = SpamRules.OTP_KEYWORDS.any { normalizedBody.contains(it) }
        if (hasOtpKeyword && SpamRules.OTP_CODE_REGEX.containsMatchIn(normalizedBody)) {
            return ClassificationResult(
                verdict = SmsVerdict.SAFE,
                score = 0,
                reasons = listOf("استثنای ایمن: پیامک رمز پویا یا کد تایید")
            )
        }

        val hasBankKeyword = SpamRules.BANK_TRANSACTION_KEYWORDS.any { normalizedBody.contains(it) }
        val hasCurrency = SpamRules.CURRENCY_KEYWORDS.any { normalizedBody.contains(it) }
        if (hasBankKeyword && hasCurrency) {
            return ClassificationResult(
                verdict = SmsVerdict.SAFE,
                score = 0,
                reasons = listOf("استثنای ایمن: پیامک تراکنش بانکی")
            )
        }

        val breakdown = scoringEngine.evaluate(
            normalizedSender = normalizedSender,
            normalizedBody = normalizedBody,
            customRules = customRules
        )

        val suspiciousThreshold = maxOf(1, spamThreshold - SpamRules.SUSPICIOUS_OFFSET)
        val verdict = when {
            breakdown.totalScore >= spamThreshold -> SmsVerdict.SPAM
            breakdown.totalScore >= suspiciousThreshold -> SmsVerdict.SUSPICIOUS
            else -> SmsVerdict.SAFE
        }

        return ClassificationResult(
            verdict = verdict,
            score = breakdown.totalScore,
            reasons = breakdown.reasons
        )
    }
}
