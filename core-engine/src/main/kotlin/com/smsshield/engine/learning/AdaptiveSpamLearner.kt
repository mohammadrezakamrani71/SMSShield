package com.smsshield.engine.learning

import com.smsshield.engine.CustomKeywordRule
import com.smsshield.engine.PersianNormalizer
import com.smsshield.engine.SpamRules
import com.smsshield.engine.model.SmsInput
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.roundToInt

object AdaptiveSpamLearner {

    const val MIN_SCORE_DELTA = -25
    const val MAX_SCORE_DELTA = 35
    const val MIN_SPAM_GATE = 3
    const val MIN_SAFE_GATE = 2

    private val MIXED_SENDER_KEYWORDS = listOf(
        "bank",
        "بانک",
        "snapp",
        "digikala",
        "tapsi",
        "irancell",
        "mci",
        "torob",
        "basalam",
        "divar"
    )

    private data class FeatureCounters(
        val type: FeatureType,
        var spamDocCount: Int = 0,
        var safeDocCount: Int = 0
    )

    fun train(
        samples: List<TrainingSampleInput>,
        existingCustomKeywords: Set<String> = emptySet()
    ): Pair<LearnedModelSnapshot, LearningReport> {
        var totalSpam = 0
        var totalSafe = 0
        val counters = LinkedHashMap<String, FeatureCounters>()

        for (sample in samples) {
            val docFeatures = PersianTokenizer.extractFeatures(sample.sender, sample.body)
            if (sample.label == SampleLabel.SPAM) {
                totalSpam++
            } else {
                totalSafe++
            }
            for ((key, type) in docFeatures) {
                val entry = counters.getOrPut(key) { FeatureCounters(type) }
                if (sample.label == SampleLabel.SPAM) {
                    entry.spamDocCount++
                } else {
                    entry.safeDocCount++
                }
            }
        }

        val learnedFeatures = LinkedHashMap<String, LearnedFeature>()
        for ((key, entry) in counters) {
            val weight = computeWeight(
                spamDocCount = entry.spamDocCount,
                safeDocCount = entry.safeDocCount,
                totalSpam = totalSpam,
                totalSafe = totalSafe
            )
            learnedFeatures[key] = LearnedFeature(
                key = key,
                type = entry.type,
                spamDocCount = entry.spamDocCount,
                safeDocCount = entry.safeDocCount,
                weight = weight
            )
        }

        val snapshot = LearnedModelSnapshot(
            totalSpamSamples = totalSpam,
            totalSafeSamples = totalSafe,
            features = learnedFeatures
        )

        val report = buildReport(
            snapshot = snapshot,
            existingCustomKeywords = existingCustomKeywords
        )

        return snapshot to report
    }

    fun evaluate(
        input: SmsInput,
        model: LearnedModelSnapshot
    ): AdaptiveEvaluation {
        if (model.totalSpamSamples < MIN_SPAM_GATE && model.totalSafeSamples < MIN_SAFE_GATE) {
            return AdaptiveEvaluation(scoreDelta = 0, matchedPatterns = emptyList())
        }

        val docFeatures = PersianTokenizer.extractFeatures(input.sender, input.body)
        if (docFeatures.isEmpty()) {
            return AdaptiveEvaluation(scoreDelta = 0, matchedPatterns = emptyList())
        }

        val matchedBigrams = mutableListOf<LearnedFeature>()
        val matchedUnigrams = mutableListOf<LearnedFeature>()
        val matchedBehaviors = mutableListOf<LearnedFeature>()

        for ((key, _) in docFeatures) {
            val learned = model.features[key] ?: continue
            if (abs(learned.weight) < 0.15) continue
            when (learned.type) {
                FeatureType.BIGRAM -> matchedBigrams += learned
                FeatureType.UNIGRAM -> matchedUnigrams += learned
                FeatureType.BEHAVIOR -> matchedBehaviors += learned
            }
        }

        // Prioritize bigrams over their overlapping constituent unigrams
        val coveredTokens = mutableSetOf<String>()
        val selected = mutableListOf<LearnedFeature>()

        for (bigram in matchedBigrams.sortedByDescending { abs(it.weight) }) {
            selected += bigram
            coveredTokens += bigram.key.split(" ")
        }
        for (unigram in matchedUnigrams.sortedByDescending { abs(it.weight) }) {
            if (unigram.key !in coveredTokens) {
                selected += unigram
            }
        }
        for (behavior in matchedBehaviors.sortedByDescending { abs(it.weight) }) {
            selected += behavior
        }

        val topContributors = selected
            .sortedByDescending { abs(it.weight) }
            .take(6)

        if (topContributors.isEmpty()) {
            return AdaptiveEvaluation(scoreDelta = 0, matchedPatterns = emptyList())
        }

        val rawSum = topContributors.sumOf { feature ->
            val typeMultiplier = when (feature.type) {
                FeatureType.BIGRAM -> 1.25
                FeatureType.UNIGRAM -> 1.0
                FeatureType.BEHAVIOR -> 0.85
            }
            feature.weight * typeMultiplier
        }

        val clampedDelta = (rawSum * 6.5).roundToInt().coerceIn(MIN_SCORE_DELTA, MAX_SCORE_DELTA)
        val matchedPatterns = topContributors
            .filter { (clampedDelta > 0 && it.weight > 0) || (clampedDelta < 0 && it.weight < 0) }
            .take(3)
            .map { formatFeatureForDisplay(it.key) }

        return AdaptiveEvaluation(
            scoreDelta = clampedDelta,
            matchedPatterns = matchedPatterns
        )
    }

    /**
     * Anti-overblocking guardrail: detects whether a sender likely sends both important service/OTP
     * messages and promotional SMS so the UI can warn the user before blocking the entire sender.
     */
    fun isLikelyMixedSender(
        sender: String,
        samples: List<TrainingSampleInput> = emptyList()
    ): Boolean {
        val normalized = PersianNormalizer.normalizeSender(sender)
        if (normalized.isEmpty()) return false

        // 1. Appears in BOTH SPAM and SAFE training samples
        var hasSpam = false
        var hasSafe = false
        for (sample in samples) {
            if (PersianNormalizer.normalizeSender(sample.sender) == normalized) {
                if (sample.label == SampleLabel.SPAM) hasSpam = true
                if (sample.label == SampleLabel.SAFE) hasSafe = true
                if (hasSpam && hasSafe) return true
            }
        }

        // 2. Alphanumeric brand/service sender (e.g., Digikala, Snapp, Mellat)
        if (normalized.any { it.isLetter() }) {
            return true
        }

        // 3. Short service code (4-6 digits)
        if (normalized.all { it.isDigit() } && normalized.length in 4..6) {
            return true
        }

        // 4. Contains bank/service keywords
        val normalizedText = PersianNormalizer.normalize(sender)
        if (MIXED_SENDER_KEYWORDS.any { normalizedText.contains(it) }) {
            return true
        }

        return false
    }

    /**
     * Inspects tokens (and high-value bigrams) in a message body for interactive Tap-to-Teach UI.
     */
    fun inspectMessageTokens(
        body: String,
        model: LearnedModelSnapshot? = null,
        customRules: List<CustomKeywordRule> = emptyList()
    ): List<InspectedToken> {
        val tokens = PersianTokenizer.tokenize(body)
        if (tokens.isEmpty()) return emptyList()

        val normalizedCustomKeywords = customRules
            .filter { it.isEnabled }
            .map { PersianNormalizer.normalize(it.keyword) }
            .filter { it.isNotEmpty() }
            .toSet()

        val normalizedSafeKeywords = SpamRules.SERVICE_SAFE_KEYWORDS
            .map { PersianNormalizer.normalize(it) }
            .filter { it.isNotEmpty() }

        val candidates = LinkedHashSet<String>()

        // Include high-value bigrams if present in custom rules, built-in promo rules, or learned model
        for (i in 0 until tokens.size - 1) {
            val bigram = "${tokens[i]} ${tokens[i + 1]}"
            val inCustom = bigram in normalizedCustomKeywords
            val inModel = abs(model?.features?.get(bigram)?.weight ?: 0.0) > 0.2
            val inBuiltIn = SpamRules.PROMO_KEYWORD_RULES.any { it.regex.matches(bigram) }
            if (inCustom || inModel || inBuiltIn) {
                candidates += bigram
            }
        }

        for (token in tokens) {
            candidates += token
        }

        return candidates.map { phrase ->
            val isCustom = phrase in normalizedCustomKeywords
            val modelWeight = model?.features?.get(phrase)?.weight ?: 0.0
            val inBuiltInPromo = SpamRules.PROMO_KEYWORD_RULES.any { it.regex.containsMatchIn(phrase) }
            val inServiceSafe = normalizedSafeKeywords.any { safeWord ->
                phrase == safeWord || safeWord.split(" ").contains(phrase)
            }

            val sentiment = when {
                isCustom || inBuiltInPromo || modelWeight > 0.2 -> TokenSentiment.SPAM_LEANING
                inServiceSafe || modelWeight < -0.2 -> TokenSentiment.SAFE_LEANING
                else -> TokenSentiment.NEUTRAL
            }

            InspectedToken(
                token = phrase,
                sentiment = sentiment,
                weight = modelWeight,
                isAlreadyCustomRule = isCustom
            )
        }
    }

    fun formatFeatureForDisplay(key: String): String {
        return when (key) {
            PersianTokenizer.BEHAVIOR_HAS_URL -> "لینک تبلیغاتی"
            PersianTokenizer.BEHAVIOR_HAS_PHONE -> "شماره تماس"
            PersianTokenizer.BEHAVIOR_HAS_LAGHV11 -> "الگوی لغو ۱۱"
            PersianTokenizer.BEHAVIOR_BULK_SENDER -> "سرشماره انبوه"
            PersianTokenizer.BEHAVIOR_PROMO_LONG_MSG -> "متن طولانی"
            PersianTokenizer.BEHAVIOR_SENDER_GATEWAY_1000 -> "سرشماره ۱۰۰۰"
            PersianTokenizer.BEHAVIOR_SENDER_GATEWAY_2000 -> "سرشماره ۲۰۰۰"
            PersianTokenizer.BEHAVIOR_SENDER_GATEWAY_3000 -> "سرشماره ۳۰۰۰"
            PersianTokenizer.BEHAVIOR_SENDER_GATEWAY_5000 -> "سرشماره ۵۰۰۰"
            PersianTokenizer.BEHAVIOR_SENDER_GATEWAY_9000 -> "سرشماره ۹۰۰۰"
            PersianTokenizer.BEHAVIOR_SENDER_SHORTCODE -> "کد کوتاه خدماتی"
            PersianTokenizer.BEHAVIOR_SENDER_MOBILE -> "شماره موبایل"
            PersianTokenizer.BEHAVIOR_SENDER_ALPHANUMERIC -> "فرستنده متنی/برند"
            else -> key
        }
    }

    private fun computeWeight(
        spamDocCount: Int,
        safeDocCount: Int,
        totalSpam: Int,
        totalSafe: Int
    ): Double {
        if (totalSpam == 0 && totalSafe == 0) return 0.0

        // Cold-start protection when no SAFE samples exist yet
        if (totalSafe == 0) {
            if (spamDocCount < 2 || totalSpam == 0) return 0.0
            val prevalence = spamDocCount.toDouble() / totalSpam.toDouble()
            val supportFactor = 0.5 + 0.5 * minOf(1.0, spamDocCount / 4.0)
            return prevalence * 2.5 * supportFactor
        }

        // Two-class smoothed Naive Bayes Log-Odds
        val pSpam = (spamDocCount + 0.5) / (totalSpam + 1.0)
        val pSafe = (safeDocCount + 0.5) / (totalSafe + 1.0)
        val totalSupport = spamDocCount + safeDocCount
        val supportFactor = 0.65 + 0.35 * minOf(1.0, totalSupport / 4.0)
        return ln(pSpam / pSafe) * supportFactor
    }

    private fun buildReport(
        snapshot: LearnedModelSnapshot,
        existingCustomKeywords: Set<String>
    ): LearningReport {
        val textFeatures = snapshot.features.values
            .filter { it.type == FeatureType.UNIGRAM || it.type == FeatureType.BIGRAM }

        val topSpamPhrases = textFeatures
            .filter { it.weight > 0.2 && it.spamDocCount >= if (snapshot.totalSafeSamples == 0) 2 else 1 }
            .sortedWith(
                compareByDescending<LearnedFeature> { it.weight }
                    .thenByDescending { it.spamDocCount }
            )
            .take(12)
            .map {
                DiscoveredPattern(
                    phrase = it.key,
                    type = it.type,
                    spamCount = it.spamDocCount,
                    safeCount = it.safeDocCount,
                    confidenceScore = it.weight
                )
            }

        val topSafePhrases = textFeatures
            .filter { it.weight < -0.2 && it.safeDocCount >= 1 }
            .sortedWith(
                compareBy<LearnedFeature> { it.weight }
                    .thenByDescending { it.safeDocCount }
            )
            .take(8)
            .map {
                DiscoveredPattern(
                    phrase = it.key,
                    type = it.type,
                    spamCount = it.spamDocCount,
                    safeCount = it.safeDocCount,
                    confidenceScore = abs(it.weight)
                )
            }

        val normalizedKnown = existingCustomKeywords
            .map { PersianNormalizer.normalize(it) }
            .filter { it.isNotEmpty() }
            .toMutableSet()
        SpamRules.PROMO_KEYWORD_RULES.forEach { rule ->
            normalizedKnown += PersianNormalizer.normalize(rule.label)
        }

        val suggestedKeywords = topSpamPhrases
            .map { it.phrase }
            .filter { phrase ->
                val norm = PersianNormalizer.normalize(phrase)
                norm.isNotEmpty() && norm !in normalizedKnown
            }
            .take(8)

        val insights = buildBehavioralInsights(snapshot)

        return LearningReport(
            totalSpamSamples = snapshot.totalSpamSamples,
            totalSafeSamples = snapshot.totalSafeSamples,
            topSpamPhrases = topSpamPhrases,
            topSafePhrases = topSafePhrases,
            behavioralInsights = insights,
            suggestedKeywords = suggestedKeywords
        )
    }

    private fun buildBehavioralInsights(snapshot: LearnedModelSnapshot): List<String> {
        val totalSpam = snapshot.totalSpamSamples
        if (totalSpam <= 0) return emptyList()

        val insights = mutableListOf<String>()

        fun addInsightIfPresent(key: String, template: (String) -> String) {
            val count = snapshot.features[key]?.spamDocCount ?: 0
            if (count > 0) {
                val percent = ((count * 100.0) / totalSpam).roundToInt()
                insights += template(toPersianDigits(percent.toString()))
            }
        }

        addInsightIfPresent(PersianTokenizer.BEHAVIOR_HAS_URL) { pct ->
            "$pct٪ پیامک‌های تبلیغاتی آموزش‌داده‌شده دارای لینک هستند"
        }
        addInsightIfPresent(PersianTokenizer.BEHAVIOR_HAS_LAGHV11) { pct ->
            "$pct٪ پیامک‌های تبلیغاتی دارای الگوی لغو ۱۱ هستند"
        }
        addInsightIfPresent(PersianTokenizer.BEHAVIOR_BULK_SENDER) { pct ->
            "$pct٪ از سرشماره‌های ارسال انبوه ارسال شده‌اند"
        }
        addInsightIfPresent(PersianTokenizer.BEHAVIOR_HAS_PHONE) { pct ->
            "$pct٪ دارای شماره تماس داخل متن هستند"
        }
        addInsightIfPresent(PersianTokenizer.BEHAVIOR_PROMO_LONG_MSG) { pct ->
            "$pct٪ پیامک‌های طولانی تبلیغاتی (بیش از ۱۴۰ کاراکتر) هستند"
        }

        // Top sender-pattern stats
        val senderPatternCounts = PersianTokenizer.SENDER_BEHAVIOR_KEYS
            .mapNotNull { key ->
                val count = snapshot.features[key]?.spamDocCount ?: 0
                if (count > 0) key to count else null
            }
            .sortedByDescending { it.second }
            .take(2)

        for ((key, count) in senderPatternCounts) {
            val percent = ((count * 100.0) / totalSpam).roundToInt()
            val pctFa = toPersianDigits(percent.toString())
            val label = formatFeatureForDisplay(key)
            insights += "$pctFa٪ پیامک‌های تبلیغاتی از $label بوده‌اند"
        }

        return insights
    }

    private fun toPersianDigits(input: String): String {
        val sb = StringBuilder(input.length)
        for (ch in input) {
            if (ch in '0'..'9') {
                sb.append(('۰'.code + (ch.code - '0'.code)).toChar())
            } else {
                sb.append(ch)
            }
        }
        return sb.toString()
    }
}
