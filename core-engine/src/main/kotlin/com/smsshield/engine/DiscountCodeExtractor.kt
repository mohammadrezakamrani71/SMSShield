package com.smsshield.engine

import java.text.Normalizer

object DiscountCodeExtractor {

    private val INVISIBLE_AND_DIACRITICS_REGEX =
        Regex("[\\u200B\\u200E\\u200F\\uFEFF\\u0610-\\u061A\\u064B-\\u065F\\u0670\\u06D6-\\u06ED]")

    private val EXPLICIT_DISCOUNT_CUE_REGEX =
        Regex("""(?:کد\s+تخفیف|بن\s+تخفیف)\s*[:=\-«"']*\s*([A-Za-z0-9_-]{3,20})""")

    private val SECONDARY_CODE_CUE_REGEX =
        Regex(
            """(?:کد\s+هدیه|promo\s+code|discount\s+code|\bcode)\s*[:=\-«"']*\s*([A-Za-z0-9_-]{3,20})""",
            RegexOption.IGNORE_CASE
        )

    private val PURE_4_TO_8_DIGITS_REGEX = Regex("^\\d{4,8}$")

    /**
     * Extracts a promo/discount code from a message already classified as SpamCategory.OFFER.
     * Preserves original Latin casing (e.g. "BF40") while normalizing Arabic/Persian letters and digits.
     */
    fun extract(rawBody: String): String? {
        if (rawBody.isBlank()) return null
        val prepared = preparePreservingCase(rawBody)

        EXPLICIT_DISCOUNT_CUE_REGEX.find(prepared)?.let { match ->
            val candidate = match.groupValues[1].trim('-', '_', '.', ',', '»', '"', '\'')
            if (isValidCandidate(candidate, allowPure4To8Digits = true)) {
                return candidate
            }
        }

        SECONDARY_CODE_CUE_REGEX.find(prepared)?.let { match ->
            val candidate = match.groupValues[1].trim('-', '_', '.', ',', '»', '"', '\'')
            if (isValidCandidate(candidate, allowPure4To8Digits = false)) {
                return candidate
            }
        }

        return null
    }

    private fun isValidCandidate(candidate: String, allowPure4To8Digits: Boolean): Boolean {
        if (candidate.length !in 3..20) return false
        if (!allowPure4To8Digits && PURE_4_TO_8_DIGITS_REGEX.matches(candidate)) {
            return false
        }
        return true
    }

    private fun preparePreservingCase(input: String): String {
        val nfkc = Normalizer.normalize(input, Normalizer.Form.NFKC)
        val cleaned = INVISIBLE_AND_DIACRITICS_REGEX.replace(nfkc, "")
        val sb = StringBuilder(cleaned.length)
        for (ch in cleaned) {
            when (ch) {
                '\u200C' -> sb.append(' ')
                '\u064A', '\u0649', '\u0626' -> sb.append('\u06CC')
                '\u0643' -> sb.append('\u06A9')
                '\u0629' -> sb.append('\u0647')
                '\u0623', '\u0625' -> sb.append('\u0627')
                in '\u06F0'..'\u06F9' -> sb.append(('0'.code + (ch.code - '\u06F0'.code)).toChar())
                in '\u0660'..'\u0669' -> sb.append(('0'.code + (ch.code - '\u0660'.code)).toChar())
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }
}
