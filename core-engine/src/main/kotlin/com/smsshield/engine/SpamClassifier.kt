package com.smsshield.engine

import com.smsshield.engine.learning.LearnedModelSnapshot
import com.smsshield.engine.model.BlockedSenderRule
import com.smsshield.engine.model.ClassificationResult
import com.smsshield.engine.model.SenderMatchType
import com.smsshield.engine.model.SmsInput
import com.smsshield.engine.model.SmsVerdict
import com.smsshield.engine.model.SpamCategory

class SpamClassifier(
    private val scoringEngine: ScoringEngine = ScoringEngine()
) {

    fun classify(
        input: SmsInput,
        whitelistedSenders: Set<String> = emptySet(),
        customRules: List<CustomKeywordRule> = emptyList(),
        spamThreshold: Int = SpamRules.DEFAULT_SPAM_THRESHOLD,
        blockedSenders: List<BlockedSenderRule> = emptyList(),
        learnedModel: LearnedModelSnapshot? = null
    ): ClassificationResult {
        val normalizedSender = PersianNormalizer.normalizeSender(input.sender)
        val normalizedBody = PersianNormalizer.normalize(input.body)

        // Precedence 1: Manual user whitelist
        val normalizedWhitelist = whitelistedSenders
            .map { PersianNormalizer.normalizeSender(it) }
            .filter { it.isNotEmpty() }
            .toSet()

        if (normalizedSender.isNotEmpty() && normalizedSender in normalizedWhitelist) {
            return ClassificationResult(
                verdict = SmsVerdict.SAFE,
                score = 0,
                reasons = listOf("استثنای ایمن: فرستنده در لیست مجاز کاربر است"),
                category = SpamCategory.NONE,
                extractedDiscountCode = null
            )
        }

        // Precedence 2: Manual user sender blacklist (applies even if body is truncated)
        if (normalizedSender.isNotEmpty()) {
            for (rule in blockedSenders) {
                val normalizedPattern = PersianNormalizer.normalizeSender(
                    rule.pattern.trim().removeSuffix("*")
                )
                if (normalizedPattern.isEmpty()) continue
                val matched = when (rule.matchType) {
                    SenderMatchType.EXACT -> normalizedSender == normalizedPattern
                    SenderMatchType.PREFIX -> normalizedSender.startsWith(normalizedPattern)
                }
                if (matched) {
                    return ClassificationResult(
                        verdict = SmsVerdict.SPAM,
                        score = SpamRules.MAX_TOTAL_SCORE,
                        reasons = listOf("سرشماره مسدودشده توسط کاربر (${rule.pattern})"),
                        matchedBlockedSenderPattern = normalizedPattern,
                        category = SpamCategory.SPAM_NUISANCE,
                        extractedDiscountCode = null
                    )
                }
            }
        }

        // Precedence 3: Truncation safety guard (never overridden by learnedModel)
        if (input.isPossiblyTruncated) {
            return ClassificationResult(
                verdict = SmsVerdict.SAFE,
                score = 0,
                reasons = listOf("استثنای ایمن: متن پیام احتمالاً ناقص است"),
                category = SpamCategory.NONE,
                extractedDiscountCode = null
            )
        }

        // Precedence 4: OTP Exception (never overridden by learnedModel)
        if (isSafeOtp(normalizedBody)) {
            return ClassificationResult(
                verdict = SmsVerdict.SAFE,
                score = 0,
                reasons = listOf("استثنای ایمن: پیامک رمز پویا یا کد تایید"),
                category = SpamCategory.NONE,
                extractedDiscountCode = null
            )
        }

        // Precedence 5: Bank Transaction Exception (never overridden by learnedModel)
        if (isSafeBankTransaction(normalizedBody)) {
            return ClassificationResult(
                verdict = SmsVerdict.SAFE,
                score = 0,
                reasons = listOf("استثنای ایمن: پیامک تراکنش بانکی"),
                category = SpamCategory.NONE,
                extractedDiscountCode = null
            )
        }

        // Layer 3 & 4: Weighted scoring + optional adaptive learning delta
        val breakdown = scoringEngine.evaluate(
            normalizedSender = normalizedSender,
            normalizedBody = normalizedBody,
            customRules = customRules,
            learnedModel = learnedModel
        )

        val suspiciousThreshold = maxOf(1, spamThreshold - SpamRules.SUSPICIOUS_OFFSET)
        val verdict = when {
            breakdown.totalScore >= spamThreshold -> SmsVerdict.SPAM
            breakdown.totalScore >= suspiciousThreshold -> SmsVerdict.SUSPICIOUS
            else -> SmsVerdict.SAFE
        }

        val category = if (verdict == SmsVerdict.SAFE) {
            SpamCategory.NONE
        } else {
            val hasOfferSignal = breakdown.matchedOfferRule ||
                SpamRules.PERCENT_DISCOUNT_REGEX.containsMatchIn(normalizedBody)
            if (hasOfferSignal && !breakdown.matchedNuisanceRule) {
                SpamCategory.OFFER
            } else {
                SpamCategory.SPAM_NUISANCE
            }
        }

        val discountCode = if (category == SpamCategory.OFFER) {
            DiscountCodeExtractor.extract(input.body)
        } else {
            null
        }

        return ClassificationResult(
            verdict = verdict,
            score = breakdown.totalScore,
            reasons = breakdown.reasons,
            matchedCustomKeywords = breakdown.matchedCustomKeywords,
            category = category,
            extractedDiscountCode = discountCode
        )
    }

    private fun isSafeOtp(normalizedBody: String): Boolean {
        val hasOtpKeyword = SpamRules.OTP_KEYWORDS.any { normalizedBody.contains(it) }
        if (!hasOtpKeyword || !SpamRules.OTP_CODE_REGEX.containsMatchIn(normalizedBody)) {
            return false
        }

        val hasStrongOtp = SpamRules.STRONG_OTP_KEYWORDS.any { normalizedBody.contains(it) }
        val hasPromoGuard = SpamRules.OPT_OUT_REGEX.containsMatchIn(normalizedBody) ||
            SpamRules.OTP_PROMO_GUARD_KEYWORDS.any { normalizedBody.contains(it) }

        return hasStrongOtp || !hasPromoGuard
    }

    private fun isSafeBankTransaction(normalizedBody: String): Boolean {
        val hasBankVerb = SpamRules.BANK_TRANSACTION_VERBS.any { normalizedBody.contains(it) }
        if (!hasBankVerb) return false

        val hasAmountWithCurrency = SpamRules.BANK_AMOUNT_CURRENCY_REGEX.containsMatchIn(normalizedBody)
        val hasBalanceContext = SpamRules.BANK_BALANCE_CONTEXT_KEYWORDS.any { normalizedBody.contains(it) }
        if (!hasAmountWithCurrency && !hasBalanceContext) return false

        val hasBankPromoGuard = SpamRules.OPT_OUT_REGEX.containsMatchIn(normalizedBody) ||
            SpamRules.BANK_PROMO_GUARD_KEYWORDS.any { normalizedBody.contains(it) }

        return !hasBankPromoGuard
    }
}
