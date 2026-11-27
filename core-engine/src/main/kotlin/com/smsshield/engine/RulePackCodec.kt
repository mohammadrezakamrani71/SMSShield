package com.smsshield.engine

import com.smsshield.engine.model.SenderMatchType
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

@Serializable
data class KeywordRuleItem(
    val keyword: String,
    val weight: Int = 20
)

@Serializable
data class BlockedSenderItem(
    val pattern: String,
    val matchType: SenderMatchType = SenderMatchType.EXACT
)

@Serializable
data class RulePack(
    val schemaVersion: Int = 1,
    val packName: String,
    val authorName: String,
    val description: String,
    val category: String,
    val caughtCount: Int = 0,
    val keywords: List<KeywordRuleItem>,
    val blockedSenders: List<BlockedSenderItem> = emptyList()
)

@Serializable
data class FullBackupData(
    val schemaVersion: Int = 1,
    val exportedAt: Long,
    val spamThreshold: Int,
    val customRules: List<KeywordRuleItem>,
    val blockedSenders: List<BlockedSenderItem>,
    val whitelistSenders: List<String>
)

object RulePackCodec {

    const val SHARE_CODE_PREFIX = "SMSSHIELD-PACK:v1:"
    const val MIN_WEIGHT = 5
    const val MAX_WEIGHT = 100
    const val MAX_PAYLOAD_BYTES = 256 * 1024

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    private val compactJson = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    fun encodePackToJson(pack: RulePack): String {
        return json.encodeToString(sanitizePack(pack))
    }

    fun decodePackFromJson(rawJson: String): Result<RulePack> {
        return runCatching {
            val trimmed = rawJson.trim()
            require(trimmed.isNotEmpty()) { "Empty pack payload" }
            require(trimmed.toByteArray(Charsets.UTF_8).size <= MAX_PAYLOAD_BYTES) {
                "Pack payload exceeds maximum allowed size"
            }
            val parsed = json.decodeFromString<RulePack>(trimmed)
            val sanitized = sanitizePack(parsed)
            require(sanitized.packName.isNotEmpty()) { "Pack name is required" }
            require(sanitized.keywords.isNotEmpty() || sanitized.blockedSenders.isNotEmpty()) {
                "Pack must contain at least one keyword or blocked sender"
            }
            sanitized
        }
    }

    fun encodePackToShareCode(pack: RulePack): String {
        val raw = compactJson.encodeToString(sanitizePack(pack)).toByteArray(Charsets.UTF_8)
        val compressed = ByteArrayOutputStream().use { baos ->
            GZIPOutputStream(baos).use { gzip -> gzip.write(raw) }
            baos.toByteArray()
        }
        val encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(compressed)
        return SHARE_CODE_PREFIX + encoded
    }

    fun decodePackFromShareCodeOrJson(input: String): Result<RulePack> {
        return runCatching {
            val trimmed = input.trim()
            require(trimmed.isNotEmpty()) { "Empty input" }
            require(trimmed.toByteArray(Charsets.UTF_8).size <= MAX_PAYLOAD_BYTES) {
                "Input exceeds maximum allowed size"
            }
            if (trimmed.startsWith(SHARE_CODE_PREFIX)) {
                val base64Part = trimmed.removePrefix(SHARE_CODE_PREFIX).trim()
                require(base64Part.isNotEmpty()) { "Missing encoded pack payload" }
                val compressed = try {
                    Base64.getUrlDecoder().decode(base64Part)
                } catch (_: IllegalArgumentException) {
                    Base64.getDecoder().decode(base64Part)
                }
                val decompressed = gunzipBounded(compressed, MAX_PAYLOAD_BYTES)
                decodePackFromJson(decompressed.toString(Charsets.UTF_8)).getOrThrow()
            } else {
                decodePackFromJson(trimmed).getOrThrow()
            }
        }
    }

    fun encodeBackupToJson(backup: FullBackupData): String {
        return json.encodeToString(sanitizeBackup(backup))
    }

    fun decodeBackupFromJson(rawJson: String): Result<FullBackupData> {
        return runCatching {
            val trimmed = rawJson.trim()
            require(trimmed.isNotEmpty()) { "Empty backup payload" }
            require(trimmed.toByteArray(Charsets.UTF_8).size <= MAX_PAYLOAD_BYTES) {
                "Backup payload exceeds maximum allowed size"
            }
            val parsed = json.decodeFromString<FullBackupData>(trimmed)
            sanitizeBackup(parsed)
        }
    }

    private fun sanitizePack(pack: RulePack): RulePack {
        return pack.copy(
            schemaVersion = maxOf(1, pack.schemaVersion),
            packName = pack.packName.trim(),
            authorName = pack.authorName.trim().ifEmpty { "ناشناس" },
            description = pack.description.trim(),
            category = pack.category.trim().ifEmpty { "عمومی" },
            caughtCount = maxOf(0, pack.caughtCount),
            keywords = sanitizeKeywords(pack.keywords),
            blockedSenders = sanitizeBlockedSenders(pack.blockedSenders)
        )
    }

    private fun sanitizeBackup(backup: FullBackupData): FullBackupData {
        val seenWhitelist = mutableSetOf<String>()
        val cleanWhitelist = backup.whitelistSenders
            .map { it.trim() }
            .filter {
                val norm = PersianNormalizer.normalizeSender(it)
                norm.isNotEmpty() && seenWhitelist.add(norm)
            }
        return backup.copy(
            schemaVersion = maxOf(1, backup.schemaVersion),
            spamThreshold = backup.spamThreshold.coerceIn(50, 100),
            customRules = sanitizeKeywords(backup.customRules),
            blockedSenders = sanitizeBlockedSenders(backup.blockedSenders),
            whitelistSenders = cleanWhitelist
        )
    }

    private fun sanitizeKeywords(items: List<KeywordRuleItem>): List<KeywordRuleItem> {
        val seen = mutableSetOf<String>()
        val result = mutableListOf<KeywordRuleItem>()
        for (item in items) {
            val cleaned = item.keyword.trim()
            val normalized = PersianNormalizer.normalize(cleaned)
            if (normalized.isEmpty() || !seen.add(normalized)) continue
            result += KeywordRuleItem(
                keyword = cleaned,
                weight = item.weight.coerceIn(MIN_WEIGHT, MAX_WEIGHT)
            )
        }
        return result
    }

    private fun sanitizeBlockedSenders(items: List<BlockedSenderItem>): List<BlockedSenderItem> {
        val seen = mutableSetOf<String>()
        val result = mutableListOf<BlockedSenderItem>()
        for (item in items) {
            val cleaned = item.pattern.trim()
            val normalized = PersianNormalizer.normalizeSender(cleaned.removeSuffix("*"))
            if (normalized.isEmpty() || !seen.add(normalized)) continue
            result += BlockedSenderItem(
                pattern = cleaned,
                matchType = item.matchType
            )
        }
        return result
    }

    private fun gunzipBounded(compressed: ByteArray, maxBytes: Int): ByteArray {
        val out = ByteArrayOutputStream()
        GZIPInputStream(ByteArrayInputStream(compressed)).use { gzip ->
            val buffer = ByteArray(4096)
            var total = 0
            while (true) {
                val read = gzip.read(buffer)
                if (read == -1) break
                total += read
                require(total <= maxBytes) { "Decompressed payload exceeds maximum allowed size" }
                out.write(buffer, 0, read)
            }
        }
        return out.toByteArray()
    }
}
