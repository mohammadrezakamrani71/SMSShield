package com.smsshield.engine

import java.util.Locale

object PersianNormalizer {

    private val INVISIBLE_AND_DIACRITICS_REGEX =
        Regex("[\\u200B\\u200E\\u200F\\uFEFF\\u064B-\\u0652]")

    private val MULTI_SPACE_REGEX = Regex("\\s+")

    fun normalize(input: String): String {
        if (input.isEmpty()) return ""

        val cleaned = INVISIBLE_AND_DIACRITICS_REGEX.replace(input, "")
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
