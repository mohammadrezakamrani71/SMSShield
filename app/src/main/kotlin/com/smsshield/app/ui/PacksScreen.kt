package com.smsshield.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smsshield.app.data.StarterPacks
import com.smsshield.app.data.database.BlockedSenderEntity
import com.smsshield.app.data.database.CustomRuleEntity
import com.smsshield.engine.BlockedSenderItem
import com.smsshield.engine.KeywordRuleItem
import com.smsshield.engine.PersianNormalizer
import com.smsshield.engine.RulePack
import com.smsshield.engine.RulePackCodec
import com.smsshield.engine.model.SenderMatchType

private val PACK_CATEGORIES = listOf(
    "بانکی و جشنواره‌ای",
    "فروشگاهی",
    "کلینیک و خدمات",
    "املاک، وام و سرمایه‌گذاری",
    "عمومی"
)

fun shieldContributorRank(totalHits: Int): String {
    return when {
        totalHits >= 100 -> "سپر طلایی 🏆"
        totalHits >= 25 -> "شکارچی تبلیغات 🎯"
        totalHits >= 5 -> "دیده‌بان پیامک 👁️"
        else -> "نگهبان تازه‌کار 🛡️"
    }
}

@Composable
fun PacksScreen(
    totalUserHits: Int,
    customRules: List<CustomRuleEntity>,
    blockedSenders: List<BlockedSenderEntity>,
    pendingPreviewPack: RulePack?,
    packStatusMessage: String?,
    onOpenPackFile: () -> Unit,
    onSelectPackForPreview: (RulePack) -> Unit,
    onDismissPreview: () -> Unit,
    onConfirmImportSelected: (List<KeywordRuleItem>, List<BlockedSenderItem>) -> Unit,
    onSavePackJson: (fileName: String, jsonContent: String) -> Unit,
    onCopyToClipboard: (label: String, text: String, feedbackMessage: String) -> Unit,
    onOpenCommunityDiscussions: () -> Unit,
    onReportStatus: (String) -> Unit
) {
    var showPasteDialog by remember { mutableStateOf(false) }
    var pasteInput by remember { mutableStateOf("") }
    var showStudioDialog by remember { mutableStateOf(false) }

    val existingNormalizedKeywords = remember(customRules) {
        customRules.map { PersianNormalizer.normalize(it.keyword) }.toSet()
    }
    val existingNormalizedSenders = remember(blockedSenders) {
        blockedSenders.map { it.normalizedPattern }.toSet()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (!packStatusMessage.isNullOrBlank()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Text(
                    text = packStatusMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        // 1. Gamified "استودیو پک من" (My Shield Studio)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "استودیو پک من",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "رتبه فعلی شما: ${shieldContributorRank(totalUserHits)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "مجموع شکار کلیدواژه‌ها و سرشماره‌های شما: $totalUserHits پیامک تبلیغاتی",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = { showStudioDialog = true },
                    enabled = customRules.isNotEmpty() || blockedSenders.isNotEmpty()
                ) {
                    Text("ساخت و انتشار پک من")
                }
                if (customRules.isEmpty() && blockedSenders.isEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "برای ساخت پک، ابتدا حداقل یک کلیدواژه سفارشی یا سرشماره مسدودشده در تنظیمات اضافه کنید.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        // 2. Import Pack Card
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "وارد کردن پک کلیدواژه",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "پیش از افزودن هر پک، تمام کلیدواژه‌ها و سرشماره‌ها را با تیک انتخاب بررسی می‌کنید.",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onOpenPackFile,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("باز کردن فایل پک")
                    }
                    OutlinedButton(
                        onClick = {
                            pasteInput = ""
                            showPasteDialog = true
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("جایگذاری کد پک")
                    }
                }
            }
        }

        // 3. Community Popular Packs Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "مرور پک‌های محبوب کاربران (بیشترین لایک 👍)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "پک‌های ساخته‌شده توسط کاربران ایرانی را در GitHub Discussions ببینید، کد آن‌ها را کپی کنید و به پک‌های مفید لایک بدهید.",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(10.dp))
                Button(onClick = onOpenCommunityDiscussions) {
                    Text("باز کردن صفحه پک‌های کاربران")
                }
            }
        }

        // 4. Curated Offline Starter Packs
        Text(
            text = "پک‌های آماده آفلاین",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        StarterPacks.CURATED_PACKS.forEach { pack ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = pack.packName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        AssistChip(
                            onClick = {},
                            label = { Text(pack.category) }
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = pack.description,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${pack.keywords.size} کلیدواژه • ${pack.blockedSenders.size} سرشماره",
                            style = MaterialTheme.typography.labelMedium
                        )
                        OutlinedButton(onClick = { onSelectPackForPreview(pack) }) {
                            Text("پیش‌نمایش و انتخاب")
                        }
                    }
                }
            }
        }
    }

    // Paste Share Code / JSON Dialog
    if (showPasteDialog) {
        AlertDialog(
            onDismissRequest = { showPasteDialog = false },
            title = { Text("جایگذاری کد پک یا JSON") },
            text = {
                OutlinedTextField(
                    value = pasteInput,
                    onValueChange = { pasteInput = it },
                    label = { Text("کد SMSSHIELD-PACK:v1:... یا متن JSON") },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val decoded = RulePackCodec.decodePackFromShareCodeOrJson(pasteInput)
                        decoded.onSuccess { pack ->
                            showPasteDialog = false
                            onSelectPackForPreview(pack)
                        }.onFailure { err ->
                            onReportStatus("خطا در خواندن کد پک: ${err.message ?: "فرمت نامعتبر"}")
                        }
                    }
                ) {
                    Text("بررسی و پیش‌نمایش")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPasteDialog = false }) {
                    Text("انصراف")
                }
            }
        )
    }

    // Mandatory Selective Checkbox Preview Dialog
    if (pendingPreviewPack != null) {
        PackSelectivePreviewDialog(
            pack = pendingPreviewPack,
            existingNormalizedKeywords = existingNormalizedKeywords,
            existingNormalizedSenders = existingNormalizedSenders,
            onDismiss = onDismissPreview,
            onConfirmImport = onConfirmImportSelected
        )
    }

    // Gamified Pack Creation & Publishing Studio Dialog
    if (showStudioDialog) {
        PackStudioDialog(
            totalUserHits = totalUserHits,
            customRules = customRules,
            blockedSenders = blockedSenders,
            onDismiss = { showStudioDialog = false },
            onSavePackJson = { fileName, json ->
                showStudioDialog = false
                onSavePackJson(fileName, json)
            },
            onCopyShareCode = { code ->
                onCopyToClipboard("SMSShield Pack Code", code, "کد اشتراک‌گذاری پک در حافظه کپی شد.")
            },
            onPublishToCommunity = { markdownPost ->
                onCopyToClipboard(
                    "SMSShield Community Post",
                    markdownPost,
                    "متن انتشار پک کپی شد؛ صفحه GitHub Discussions باز می‌شود."
                )
                showStudioDialog = false
                onOpenCommunityDiscussions()
            }
        )
    }
}

@Composable
private fun PackSelectivePreviewDialog(
    pack: RulePack,
    existingNormalizedKeywords: Set<String>,
    existingNormalizedSenders: Set<String>,
    onDismiss: () -> Unit,
    onConfirmImport: (List<KeywordRuleItem>, List<BlockedSenderItem>) -> Unit
) {
    val keywordSelections = remember(pack) {
        mutableStateMapOf<Int, Boolean>().apply {
            pack.keywords.forEachIndexed { idx, item ->
                val isExisting = PersianNormalizer.normalize(item.keyword) in existingNormalizedKeywords
                put(idx, !isExisting)
            }
        }
    }
    val senderSelections = remember(pack) {
        mutableStateMapOf<Int, Boolean>().apply {
            pack.blockedSenders.forEachIndexed { idx, item ->
                val norm = PersianNormalizer.normalizeSender(item.pattern.removeSuffix("*"))
                val isExisting = norm in existingNormalizedSenders
                put(idx, !isExisting)
            }
        }
    }

    val selectedCount = keywordSelections.values.count { it } + senderSelections.values.count { it }
    val selectableTotal = pack.keywords.indices.count { idx ->
        PersianNormalizer.normalize(pack.keywords[idx].keyword) !in existingNormalizedKeywords
    } + pack.blockedSenders.indices.count { idx ->
        PersianNormalizer.normalizeSender(pack.blockedSenders[idx].pattern.removeSuffix("*")) !in existingNormalizedSenders
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(pack.packName, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "سازنده: ${pack.authorName} • دسته: ${pack.category}",
                    style = MaterialTheme.typography.labelLarge
                )
                if (pack.caughtCount > 0) {
                    Text(
                        text = "آمار شکار سازنده: ${pack.caughtCount} پیامک (${shieldContributorRank(pack.caughtCount)})",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (pack.description.isNotBlank()) {
                    Text(
                        text = pack.description,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            val selectAll = selectedCount < selectableTotal
                            pack.keywords.forEachIndexed { idx, item ->
                                val exists = PersianNormalizer.normalize(item.keyword) in existingNormalizedKeywords
                                keywordSelections[idx] = !exists && selectAll
                            }
                            pack.blockedSenders.forEachIndexed { idx, item ->
                                val norm = PersianNormalizer.normalizeSender(item.pattern.removeSuffix("*"))
                                val exists = norm in existingNormalizedSenders
                                senderSelections[idx] = !exists && selectAll
                            }
                        }
                    ) {
                        Text(if (selectedCount < selectableTotal) "انتخاب همه" else "لغو همه")
                    }
                }

                if (pack.keywords.isNotEmpty()) {
                    Text(
                        text = "کلیدواژه‌ها (${pack.keywords.size})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    pack.keywords.forEachIndexed { idx, item ->
                        val isExisting = PersianNormalizer.normalize(item.keyword) in existingNormalizedKeywords
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = keywordSelections[idx] == true,
                                onCheckedChange = { checked ->
                                    if (!isExisting) keywordSelections[idx] = checked
                                },
                                enabled = !isExisting
                            )
                            Text(
                                text = "${item.keyword} (وزن: ${item.weight})" +
                                    if (isExisting) " — موجود در اپ" else "",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }

                if (pack.blockedSenders.isNotEmpty()) {
                    Text(
                        text = "سرشماره‌های مسدود (${pack.blockedSenders.size})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    pack.blockedSenders.forEachIndexed { idx, item ->
                        val norm = PersianNormalizer.normalizeSender(item.pattern.removeSuffix("*"))
                        val isExisting = norm in existingNormalizedSenders
                        val typeLabel = if (item.matchType == SenderMatchType.PREFIX) "پیشوند" else "دقیق"
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = senderSelections[idx] == true,
                                onCheckedChange = { checked ->
                                    if (!isExisting) senderSelections[idx] = checked
                                },
                                enabled = !isExisting
                            )
                            Text(
                                text = "${item.pattern} ($typeLabel)" +
                                    if (isExisting) " — موجود در اپ" else "",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val chosenKeywords = pack.keywords.filterIndexed { idx, _ ->
                        keywordSelections[idx] == true
                    }
                    val chosenSenders = pack.blockedSenders.filterIndexed { idx, _ ->
                        senderSelections[idx] == true
                    }
                    onConfirmImport(chosenKeywords, chosenSenders)
                },
                enabled = selectedCount > 0
            ) {
                Text("افزودن $selectedCount مورد انتخاب‌شده")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("انصراف")
            }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PackStudioDialog(
    totalUserHits: Int,
    customRules: List<CustomRuleEntity>,
    blockedSenders: List<BlockedSenderEntity>,
    onDismiss: () -> Unit,
    onSavePackJson: (fileName: String, jsonContent: String) -> Unit,
    onCopyShareCode: (String) -> Unit,
    onPublishToCommunity: (String) -> Unit
) {
    var packName by remember { mutableStateOf("") }
    var authorName by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(PACK_CATEGORIES.first()) }
    var description by remember { mutableStateOf("") }

    val selectedRuleIds = remember(customRules) {
        mutableStateMapOf<Long, Boolean>().apply {
            customRules.forEach { put(it.id, true) }
        }
    }
    val selectedSenderIds = remember(blockedSenders) {
        mutableStateMapOf<Long, Boolean>().apply {
            blockedSenders.forEach { put(it.id, true) }
        }
    }

    fun buildPackOrNull(): RulePack? {
        val keywords = customRules
            .filter { selectedRuleIds[it.id] == true }
            .map { KeywordRuleItem(it.keyword, it.weight) }
        val senders = blockedSenders
            .filter { selectedSenderIds[it.id] == true }
            .map {
                val matchType = runCatching { SenderMatchType.valueOf(it.matchType) }
                    .getOrDefault(SenderMatchType.EXACT)
                BlockedSenderItem(it.displayPattern, matchType)
            }
        if (packName.isBlank() || (keywords.isEmpty() && senders.isEmpty())) return null
        return RulePack(
            schemaVersion = 1,
            packName = packName.trim(),
            authorName = authorName.trim().ifEmpty { "کاربر سپر پیامک" },
            description = description.trim(),
            category = selectedCategory,
            caughtCount = totalUserHits,
            keywords = keywords,
            blockedSenders = senders
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ساخت و انتشار پک من", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 440.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = packName,
                    onValueChange = { packName = it },
                    label = { Text("عنوان پک") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = authorName,
                    onValueChange = { authorName = it },
                    label = { Text("نام مستعار سازنده") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("دسته‌بندی:", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PACK_CATEGORIES.forEach { category ->
                        FilterChip(
                            selected = selectedCategory == category,
                            onClick = { selectedCategory = category },
                            label = { Text(category) }
                        )
                    }
                }
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("توضیحات کوتاه") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )

                if (customRules.isNotEmpty()) {
                    Text("انتخاب کلیدواژه‌ها:", style = MaterialTheme.typography.titleSmall)
                    customRules.forEach { rule ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = selectedRuleIds[rule.id] == true,
                                onCheckedChange = { selectedRuleIds[rule.id] = it }
                            )
                            Text("${rule.keyword} (${rule.weight})")
                        }
                    }
                }
                if (blockedSenders.isNotEmpty()) {
                    Text("انتخاب سرشماره‌های مسدود:", style = MaterialTheme.typography.titleSmall)
                    blockedSenders.forEach { sender ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = selectedSenderIds[sender.id] == true,
                                onCheckedChange = { selectedSenderIds[sender.id] = it }
                            )
                            Text(sender.displayPattern)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                val candidatePack = buildPackOrNull()

                Button(
                    onClick = {
                        val pack = buildPackOrNull() ?: return@Button
                        val shareCode = RulePackCodec.encodePackToShareCode(pack)
                        val kwList = pack.keywords.joinToString("، ") { "${it.keyword} (${it.weight})" }
                        val markdown = buildString {
                            appendLine("### ${pack.packName}")
                            appendLine("- **سازنده / Author:** ${pack.authorName} (${shieldContributorRank(pack.caughtCount)} — ${pack.caughtCount} شکار)")
                            appendLine("- **دسته‌بندی / Category:** ${pack.category}")
                            if (pack.description.isNotBlank()) {
                                appendLine("- **توضیحات:** ${pack.description}")
                            }
                            if (kwList.isNotBlank()) {
                                appendLine("- **کلیدواژه‌ها:** $kwList")
                            }
                            appendLine()
                            appendLine("**کد اشتراک‌گذاری پک (کپی و در تب پک‌ها جای‌گذاری کنید):**")
                            appendLine("```")
                            appendLine(shareCode)
                            appendLine("```")
                        }
                        onPublishToCommunity(markdown)
                    },
                    enabled = candidatePack != null,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("انتشار عمومی برای دریافت لایک کاربران 👍")
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val pack = buildPackOrNull() ?: return@OutlinedButton
                            onCopyShareCode(RulePackCodec.encodePackToShareCode(pack))
                        },
                        enabled = candidatePack != null,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("کپی کد پک")
                    }
                    OutlinedButton(
                        onClick = {
                            val pack = buildPackOrNull() ?: return@OutlinedButton
                            val safeName = pack.packName
                                .replace(Regex("[^a-zA-Z0-9\\u0600-\\u06FF_-]+"), "-")
                                .trim('-')
                                .ifEmpty { "my-pack" }
                            onSavePackJson("$safeName.smsshield.json", RulePackCodec.encodePackToJson(pack))
                        },
                        enabled = candidatePack != null,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("ذخیره فایل JSON")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("بستن")
            }
        }
    )
}
