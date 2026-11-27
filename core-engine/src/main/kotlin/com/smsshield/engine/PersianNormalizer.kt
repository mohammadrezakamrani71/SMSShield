package com.smsshield.engine

import java.text.Normalizer
import java.util.Locale

object PersianNormalizer {

    private val INVISIBLE_AND_DIACRITICS_REGEX =
        Regex("[\\u200B\\u200E\\u200F\\uFEFF\\u0610-\\u061A\\u064B-\\u065F\\u0670\\u06D6-\\u06ED]")

    private val MULTI_SPACE_REGEX = Regex("\\s+")

    fun normalize(input: String): String {
        if (input.isEmpty()) return ""

        // NFKC folds Arabic presentation forms and other compatibility variants
        // before the explicit Persian/Arabic character mapping below.
        val compatibilityNormalized = Normalizer.normalize(input, Normalizer.Form.NFKC)
        val cleaned = INVISIBLE_AND_DIACRITICS_REGEX.replace(compatibilityNormalized, "")
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

        return MULTI_SPACE_REGEX
            .replace(sb.toString().lowercase(Locale.ROOT), " ")
            .trim()
    }

    fun normalizeSender(sender: String): String {
        return normalize(sender).replace(" ", "")
    }
}
