package com.smsshield.engine

import com.smsshield.engine.learning.AdaptiveSpamLearner
import com.smsshield.engine.learning.LearnedModelSnapshot
import com.smsshield.engine.model.SmsInput

data class ScoreBreakdown(
    val totalScore: Int,
    val reasons: List<String>,
    val matchedCustomKeywords: List<String> = emptyList()
)

class ScoringEngine {

    fun evaluate(
        normalizedSender: String,
        normalizedBody: String,
        customRules: List<CustomKeywordRule> = emptyList(),
        learnedModel: LearnedModelSnapshot? = null
    ): ScoreBreakdown {
        var score = 0
        val reasons = mutableListOf<String>()
        val matchedCustomKeywords = mutableListOf<String>()

        if (SpamRules.OPT_OUT_REGEX.containsMatchIn(normalizedBody)) {
            score += SpamRules.OPT_OUT_WEIGHT
            reasons += "الگوی لغو تبلیغات (لغو 11) (+${SpamRules.OPT_OUT_WEIGHT})"
        }

        if (SpamRules.BULK_GATEWAY_SENDER_REGEX.matches(normalizedSender)) {
            score += SpamRules.BULK_SENDER_WEIGHT
            reasons += "سرشماره ارسال انبوه پیامک (+${SpamRules.BULK_SENDER_WEIGHT})"
        }

        if (SpamRules.URL_REGEX.containsMatchIn(normalizedBody)) {
            score += SpamRules.URL_WEIGHT
            reasons += "وجود لینک یا کوتاه‌کننده آدرس (+${SpamRules.URL_WEIGHT})"
        }

        if (SpamRules.PHONE_IN_BODY_REGEX.containsMatchIn(normalizedBody)) {
            score += SpamRules.PHONE_IN_BODY_WEIGHT
            reasons += "وجود شماره تماس در متن (+${SpamRules.PHONE_IN_BODY_WEIGHT})"
        }

        var promoScore = 0
        val matchedPromoGroups = mutableSetOf<String>()
        val matchedPromoLabels = mutableListOf<String>()

        for (rule in SpamRules.PROMO_KEYWORD_RULES) {
            if (rule.groupId in matchedPromoGroups) continue
            if (rule.regex.containsMatchIn(normalizedBody)) {
                matchedPromoGroups += rule.groupId
                promoScore += rule.weight
                matchedPromoLabels += "${rule.label} (+${rule.weight})"
            }
        }

        if (promoScore > 0) {
            val cappedPromoScore = minOf(promoScore, SpamRules.PROMO_SCORE_CAP)
            score += cappedPromoScore
            val capSuffix = if (promoScore > SpamRules.PROMO_SCORE_CAP) {
                " [سقف ${SpamRules.PROMO_SCORE_CAP}]"
            } else {
                ""
            }
            reasons += "کلیدواژه‌های تبلیغاتی: ${matchedPromoLabels.joinToString("، ")}$capSuffix"
        }

        for (customRule in customRules) {
            if (!customRule.isEnabled) continue
            val normalizedKeyword = PersianNormalizer.normalize(customRule.keyword)
            if (normalizedKeyword.isNotEmpty() && normalizedBody.contains(normalizedKeyword)) {
                score += customRule.weight
                matchedCustomKeywords += customRule.keyword
                val sign = if (customRule.weight >= 0) "+" else ""
                reasons += "قانون سفارشی «${customRule.keyword}» ($sign${customRule.weight})"
            }
        }

        val matchedServiceWord = SpamRules.LEGITIMATE_SERVICE_WORDS.firstOrNull { word ->
            normalizedBody.contains(word)
        }
        if (matchedServiceWord != null) {
            score += SpamRules.SERVICE_NEGATIVE_WEIGHT
            reasons += "کلیدواژه خدماتی معتبر «$matchedServiceWord» (${SpamRules.SERVICE_NEGATIVE_WEIGHT})"
        }

        if (learnedModel != null) {
            val adaptive = AdaptiveSpamLearner.evaluate(
                input = SmsInput(sender = normalizedSender, body = normalizedBody),
                model = learnedModel
            )
            if (adaptive.scoreDelta != 0) {
                score += adaptive.scoreDelta
                val patternText = adaptive.matchedPatterns.joinToString("، ")
                reasons += if (adaptive.scoreDelta > 0) {
                    if (patternText.isNotEmpty()) {
                        "یادگیری هوشمند (+${adaptive.scoreDelta}): $patternText"
                    } else {
                        "یادگیری هوشمند (+${adaptive.scoreDelta})"
                    }
                } else {
                    if (patternText.isNotEmpty()) {
                        "اصلاح یادگیری هوشمند (${adaptive.scoreDelta}): $patternText"
                    } else {
                        "اصلاح یادگیری هوشمند (${adaptive.scoreDelta}): الگوی پیام امن"
                    }
                }
            }
        }

        val boundedScore = score.coerceIn(0, SpamRules.MAX_TOTAL_SCORE)
        if (score > SpamRules.MAX_TOTAL_SCORE) {
            reasons += "سقف امتیاز کل اعمال شد (${SpamRules.MAX_TOTAL_SCORE})"
        }

        return ScoreBreakdown(
            totalScore = boundedScore,
            reasons = reasons,
            matchedCustomKeywords = matchedCustomKeywords
        )
    }
}
