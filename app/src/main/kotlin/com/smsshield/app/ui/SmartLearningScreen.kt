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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smsshield.app.data.database.LearnedFeatureEntity
import com.smsshield.app.data.database.TrainingSampleEntity
import com.smsshield.app.data.database.TrainingSampleSource
import com.smsshield.app.util.PersianDateFormatter
import com.smsshield.engine.learning.InspectedToken
import com.smsshield.engine.learning.LearningReport
import com.smsshield.engine.learning.SampleLabel
import com.smsshield.engine.learning.TokenSentiment
import com.smsshield.engine.model.ClassificationResult
import com.smsshield.engine.model.SmsVerdict
import com.smsshield.engine.model.SpamCategory
import kotlinx.coroutines.launch

fun intelligenceLevelLabel(totalSamples: Int): String {
    return when {
        totalSamples >= 25 -> "استاد تشخیص 🧠"
        totalSamples >= 8 -> "هوشیار 👁️"
        else -> "در حال آشنایی 🌱"
    }
}

private fun sourceDisplayLabel(source: String): String {
    return when (source) {
        TrainingSampleSource.SHARED -> "اشتراک‌گذاری‌شده"
        TrainingSampleSource.QUARANTINE_CONFIRM,
        TrainingSampleSource.QUARANTINE_FALSE_POSITIVE -> "قرنطینه"
        else -> "دستی"
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SmartLearningScreen(
    samples: List<TrainingSampleEntity>,
    learnedFeatures: List<LearnedFeatureEntity>,
    report: LearningReport?,
    pendingSharedTexts: List<String>,
    learningStatusMessage: String?,
    spamThreshold: Int = 75,
    simulatorOpenTrigger: Int = 0,
    onConsumePendingShared: () -> Unit,
    onAddSamples: (bodies: List<String>, sender: String, label: SampleLabel, source: String) -> Unit,
    onDeleteSample: (Long) -> Unit,
    onTrainNow: () -> Unit,
    onPromoteKeyword: (String) -> Unit,
    onBuildPackFromLearned: (List<String>) -> Unit,
    onSimulateSms: suspend (sender: String, body: String) -> ClassificationResult = { _, _ ->
        ClassificationResult(SmsVerdict.SAFE, 0, emptyList())
    },
    onInspectTokens: suspend (body: String) -> List<InspectedToken> = { emptyList() },
    onOpenTapToTeach: (String) -> Unit = {},
    onCopyDiscountCode: (String) -> Unit = {}
) {
    val scope = rememberCoroutineScope()

    var showAddDialog by remember { mutableStateOf(false) }
    var dialogSender by remember { mutableStateOf("") }
    var dialogBody by remember { mutableStateOf("") }
    var dialogLabel by remember { mutableStateOf(SampleLabel.SPAM) }
    var dialogSource by remember { mutableStateOf(TrainingSampleSource.MANUAL) }

    // Live Message Simulator state
    var simulatorExpanded by remember { mutableStateOf(false) }
    var simSender by remember { mutableStateOf("") }
    var simBody by remember { mutableStateOf("") }
    var simResult by remember { mutableStateOf<ClassificationResult?>(null) }
    var simTokens by remember { mutableStateOf<List<InspectedToken>>(emptyList()) }

    LaunchedEffect(simulatorOpenTrigger) {
        if (simulatorOpenTrigger > 0) {
            simulatorExpanded = true
        }
    }

    // Automatically open pre-filled dialog when SMS text is shared from native Messages app
    LaunchedEffect(pendingSharedTexts) {
        if (pendingSharedTexts.isNotEmpty()) {
            dialogSender = ""
            dialogBody = pendingSharedTexts.joinToString("\n---\n")
            dialogLabel = SampleLabel.SPAM
            dialogSource = TrainingSampleSource.SHARED
            showAddDialog = true
            onConsumePendingShared()
        }
    }

    val spamCount = samples.count { it.label == SampleLabel.SPAM.name }
    val safeCount = samples.count { it.label == SampleLabel.SAFE.name }
    val activePatternCount = learnedFeatures.count { kotlin.math.abs(it.weight) >= 0.2 }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (!learningStatusMessage.isNullOrBlank()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Text(
                    text = learningStatusMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        // Live Message Simulator Card («آزمایش پیامک»)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "آزمایش پیامک (شبیه‌ساز تشخیص) 🧪",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { simulatorExpanded = !simulatorExpanded }) {
                        Text(if (simulatorExpanded) "بستن" else "باز کردن")
                    }
                }

                if (simulatorExpanded) {
                    OutlinedTextField(
                        value = simSender,
                        onValueChange = { simSender = it },
                        label = { Text("فرستنده / سرشماره (اختیاری)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = simBody,
                        onValueChange = { simBody = it },
                        label = { Text("متن پیامک آزمایشی") },
                        minLines = 3,
                        maxLines = 6,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = {
                            scope.launch {
                                simResult = onSimulateSms(simSender, simBody)
                                simTokens = onInspectTokens(simBody)
                            }
                        },
                        enabled = simBody.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("بررسی و شبیه‌سازی")
                    }

                    val result = simResult
                    if (result != null) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val verdictLabel = when (result.verdict) {
                                    SmsVerdict.SPAM -> "تبلیغاتی"
                                    SmsVerdict.SUSPICIOUS -> "مشکوک"
                                    SmsVerdict.SAFE -> "امن"
                                }
                                val categoryLabel = when (result.category) {
                                    SpamCategory.OFFER -> "تخفیف"
                                    SpamCategory.SPAM_NUISANCE -> "مزاحم"
                                    SpamCategory.NONE -> "امن"
                                }
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    AssistChip(
                                        onClick = {},
                                        colors = AssistChipDefaults.assistChipColors(
                                            containerColor = when (result.verdict) {
                                                SmsVerdict.SPAM -> MaterialTheme.colorScheme.errorContainer
                                                SmsVerdict.SUSPICIOUS -> MaterialTheme.colorScheme.tertiaryContainer
                                                SmsVerdict.SAFE -> MaterialTheme.colorScheme.secondaryContainer
                                            }
                                        ),
                                        label = { Text("وضعیت: $verdictLabel") }
                                    )
                                    AssistChip(
                                        onClick = {},
                                        label = { Text("دسته: $categoryLabel") }
                                    )
                                    AssistChip(
                                        onClick = {},
                                        label = {
                                            Text(
                                                "امتیاز ${PersianDateFormatter.toPersianDigits(result.score.toString())} از آستانه ${PersianDateFormatter.toPersianDigits(spamThreshold.toString())}"
                                            )
                                        }
                                    )
                                }

                                if (result.category == SpamCategory.OFFER && !result.extractedDiscountCode.isNullOrBlank()) {
                                    AssistChip(
                                        onClick = { onCopyDiscountCode(result.extractedDiscountCode!!) },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.ContentCopy,
                                                contentDescription = "کپی کد تخفیف"
                                            )
                                        },
                                        label = { Text("کپی کد تخفیف: ${result.extractedDiscountCode}") }
                                    )
                                }

                                if (result.reasons.isNotEmpty()) {
                                    Text(
                                        text = "دلایل امتیازدهی:",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    result.reasons.forEach { reason ->
                                        Text(
                                            text = "• $reason",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                } else {
                                    Text(
                                        text = "هیچ نشانه تبلیغاتی در این پیامک یافت نشد.",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }

                                if (simTokens.isNotEmpty()) {
                                    HorizontalDivider()
                                    Text(
                                        text = "کلمات تشخیص‌داده‌شده (برای افزودن سریع بزنید):",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        simTokens.take(12).forEach { tokenInfo ->
                                            val chipColors = when (tokenInfo.sentiment) {
                                                TokenSentiment.SPAM_LEANING -> FilterChipDefaults.filterChipColors(
                                                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.65f),
                                                    labelColor = MaterialTheme.colorScheme.onErrorContainer
                                                )
                                                TokenSentiment.SAFE_LEANING -> FilterChipDefaults.filterChipColors(
                                                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                                                    labelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                                )
                                                TokenSentiment.NEUTRAL -> FilterChipDefaults.filterChipColors(
                                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            FilterChip(
                                                selected = tokenInfo.isAlreadyCustomRule,
                                                onClick = { onPromoteKeyword(tokenInfo.token) },
                                                colors = chipColors,
                                                label = { Text(tokenInfo.token) }
                                            )
                                        }
                                    }
                                }

                                HorizontalDivider()
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TextButton(onClick = { onOpenTapToTeach(simBody) }) {
                                        Text("آموزش کلمات 🎯")
                                    }
                                    TextButton(
                                        onClick = {
                                            onAddSamples(
                                                listOf(simBody),
                                                simSender,
                                                SampleLabel.SPAM,
                                                TrainingSampleSource.MANUAL
                                            )
                                        }
                                    ) {
                                        Text("ذخیره به‌عنوان تبلیغاتی")
                                    }
                                    TextButton(
                                        onClick = {
                                            onAddSamples(
                                                listOf(simBody),
                                                simSender,
                                                SampleLabel.SAFE,
                                                TrainingSampleSource.MANUAL
                                            )
                                        }
                                    ) {
                                        Text("ذخیره به‌عنوان امن")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Hero Status Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "یادگیری هوشمند آفلاین",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = AppIdentity.APP_SLOGAN_FA,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "سطح هوشمندی: ${intelligenceLevelLabel(samples.size)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "نمونه تبلیغاتی: $spamCount  •  نمونه امن: $safeCount  •  الگوهای فعال: $activePatternCount",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        // Import / Add Samples Section
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "افزودن نمونه پیامک برای آموزش",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "داخل پیام‌رسان گوشی روی پیامک تبلیغاتی بزنید ← اشتراک‌گذاری ← سپر پیامک",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            dialogSender = ""
                            dialogBody = ""
                            dialogLabel = SampleLabel.SPAM
                            dialogSource = TrainingSampleSource.MANUAL
                            showAddDialog = true
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("افزودن دستی / چندتایی")
                    }
                    Button(
                        onClick = onTrainNow,
                        enabled = samples.isNotEmpty(),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("تحلیل و آموزش هوشمند 🧠")
                    }
                }
            }
        }

        // Live Insights Card
        if (report != null && (report.topSpamPhrases.isNotEmpty() || report.behavioralInsights.isNotEmpty())) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "الگوهای پرتکرار تبلیغاتی کشف‌شده",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    if (report.topSpamPhrases.isEmpty()) {
                        Text(
                            text = "حداقل ۲ پیامک تبلیغاتی مشابه اضافه کنید تا الگوهای پرتکرار کشف شوند.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else {
                        report.topSpamPhrases.take(8).forEach { pattern ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${pattern.phrase} (${pattern.spamCount} بار)",
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = { onPromoteKeyword(pattern.phrase) }) {
                                    Text("افزودن به کلیدواژه‌ها")
                                }
                            }
                        }
                    }

                    if (report.behavioralInsights.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "الگوهای رفتاری شناسایی‌شده",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        report.behavioralInsights.forEach { insight ->
                            Text(
                                text = "• $insight",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    if (report.suggestedKeywords.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = { onBuildPackFromLearned(report.suggestedKeywords) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("ساخت پک از آموخته‌های من")
                        }
                    }
                }
            }
        }

        // Saved Training Samples List
        Text(
            text = "نمونه‌های ذخیره‌شده (${samples.size})",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        if (samples.isEmpty()) {
            Text(
                text = "هنوز نمونه‌ای ثبت نشده است. از پیام‌رسان گوشی اشتراک بگذارید یا به‌صورت دستی اضافه کنید.",
                style = MaterialTheme.typography.bodySmall
            )
        } else {
            samples.forEach { sample ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                AssistChip(
                                    onClick = {},
                                    label = {
                                        Text(
                                            if (sample.label == SampleLabel.SPAM.name) "تبلیغاتی" else "امن"
                                        )
                                    }
                                )
                                AssistChip(
                                    onClick = {},
                                    label = { Text(sourceDisplayLabel(sample.source)) }
                                )
                            }
                            IconButton(onClick = { onDeleteSample(sample.id) }) {
                                Icon(
                                    imageVector = Icons.Default.DeleteOutline,
                                    contentDescription = "حذف نمونه"
                                )
                            }
                        }
                        if (sample.sender.isNotBlank()) {
                            Text(
                                text = "فرستنده: ${sample.sender}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                        }
                        Text(
                            text = sample.body,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        TextButton(onClick = { onOpenTapToTeach(sample.body) }) {
                            Text("آموزش کلمات 🎯")
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("افزودن نمونه پیامک") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = dialogLabel == SampleLabel.SPAM,
                            onClick = { dialogLabel = SampleLabel.SPAM },
                            label = { Text("تبلیغاتی") }
                        )
                        FilterChip(
                            selected = dialogLabel == SampleLabel.SAFE,
                            onClick = { dialogLabel = SampleLabel.SAFE },
                            label = { Text("امن") }
                        )
                    }
                    OutlinedTextField(
                        value = dialogSender,
                        onValueChange = { dialogSender = it },
                        label = { Text("فرستنده (اختیاری)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = dialogBody,
                        onValueChange = { dialogBody = it },
                        label = { Text("متن پیامک (برای چند پیامک با --- یا خط خالی جدا کنید)") },
                        minLines = 4,
                        maxLines = 8,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val parts = splitBatchSamples(dialogBody)
                        if (parts.isNotEmpty()) {
                            onAddSamples(parts, dialogSender, dialogLabel, dialogSource)
                            showAddDialog = false
                        }
                    },
                    enabled = dialogBody.isNotBlank()
                ) {
                    Text("ذخیره نمونه")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("انصراف")
                }
            }
        )
    }
}

private fun splitBatchSamples(raw: String): List<String> {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return emptyList()
    val separatorRegex = Regex("""(?:\r?\n\s*---\s*\r?\n|\r?\n\s*\r?\n)""")
    return separatorRegex.split(trimmed)
        .map { it.trim() }
        .filter { it.isNotEmpty() }
}
