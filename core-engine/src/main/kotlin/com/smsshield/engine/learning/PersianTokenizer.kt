package com.smsshield.engine.learning

import com.smsshield.engine.PersianNormalizer
import com.smsshield.engine.SpamRules

object PersianTokenizer {

    const val BEHAVIOR_HAS_URL = "__HAS_URL__"
    const val BEHAVIOR_HAS_PHONE = "__HAS_PHONE__"
    const val BEHAVIOR_HAS_LAGHV11 = "__HAS_LAGHV11__"
    const val BEHAVIOR_BULK_SENDER = "__BULK_SENDER__"
    const val BEHAVIOR_PROMO_LONG_MSG = "__PROMO_LONG_MSG__"

    val STOP_WORDS: Set<String> = setOf(
        "از", "به", "در", "با", "برای", "این", "آن", "است", "هست", "بود",
        "شد", "شود", "می", "را", "که", "تا", "یا", "هم", "اگر", "اما",
        "ولی", "شما", "ما", "خود", "یک", "دو", "های", "ترین", "نیز", "بر",
        "پس", "هر", "چه", "چون", "باید", "دارد", "کرد", "کنید", "سلام",
        "عزیز", "گرامی", "مشتری"
    )

    private val TOKEN_SPLIT_REGEX = Regex("""[^\p{L}\p{N}]+""")
    private val URL_NOISE_TOKENS = setOf("http", "https", "www", "com", "ir")

    fun tokenize(body: String): List<String> {
        val normalizedBody = PersianNormalizer.normalize(body)
        if (normalizedBody.isEmpty()) return emptyList()

        return TOKEN_SPLIT_REGEX.split(normalizedBody)
            .asSequence()
            .map { it.trim() }
            .filter { token ->
                token.length >= 3 &&
                    !token.all { ch -> ch.isDigit() } &&
                    token !in STOP_WORDS &&
                    token !in URL_NOISE_TOKENS
            }
            .toList()
    }

    /**
     * Extracts unique per-document features (document-frequency counting).
     */
    fun extractFeatures(sender: String, body: String): Map<String, FeatureType> {
        val normalizedSender = PersianNormalizer.normalizeSender(sender)
        val normalizedBody = PersianNormalizer.normalize(body)
        val features = LinkedHashMap<String, FeatureType>()

        val tokens = tokenize(normalizedBody)
        for (token in tokens) {
            features.putIfAbsent(token, FeatureType.UNIGRAM)
        }
        for (i in 0 until tokens.size - 1) {
            val bigram = "${tokens[i]} ${tokens[i + 1]}"
            features.putIfAbsent(bigram, FeatureType.BIGRAM)
        }

        if (SpamRules.URL_REGEX.containsMatchIn(normalizedBody)) {
            features[BEHAVIOR_HAS_URL] = FeatureType.BEHAVIOR
        }
        if (SpamRules.PHONE_IN_BODY_REGEX.containsMatchIn(normalizedBody)) {
            features[BEHAVIOR_HAS_PHONE] = FeatureType.BEHAVIOR
        }
        if (SpamRules.OPT_OUT_REGEX.containsMatchIn(normalizedBody)) {
            features[BEHAVIOR_HAS_LAGHV11] = FeatureType.BEHAVIOR
        }
        if (normalizedSender.isNotEmpty() &&
            SpamRules.BULK_GATEWAY_SENDER_REGEX.matches(normalizedSender)
        ) {
            features[BEHAVIOR_BULK_SENDER] = FeatureType.BEHAVIOR
        }
        if (normalizedBody.length >= 140) {
            features[BEHAVIOR_PROMO_LONG_MSG] = FeatureType.BEHAVIOR
        }

        return features
    }
}
