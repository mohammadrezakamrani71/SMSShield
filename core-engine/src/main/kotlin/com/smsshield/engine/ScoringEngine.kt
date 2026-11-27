package com.smsshield.engine

data class ScoreBreakdown(
    val totalScore: Int,
    val reasons: List<String>
)

class ScoringEngine {

    fun evaluate(
        normalizedSender: String,
        normalizedBody: String,
        customRules: List<CustomKeywordRule> = emptyList()
    ): ScoreBreakdown {
        var score = 0
        val reasons = mutableListOf<String>()

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

        val flooredScore = maxOf(0, score)
        return ScoreBreakdown(
            totalScore = flooredScore,
            reasons = reasons
        )
    }
}
