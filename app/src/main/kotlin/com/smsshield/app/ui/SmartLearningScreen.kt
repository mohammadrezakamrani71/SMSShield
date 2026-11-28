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
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smsshield.app.data.database.LearnedFeatureEntity
import com.smsshield.app.data.database.TrainingSampleEntity
import com.smsshield.app.data.database.TrainingSampleSource
import com.smsshield.engine.learning.LearningReport
import com.smsshield.engine.learning.SampleLabel

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
    onConsumePendingShared: () -> Unit,
    onAddSamples: (bodies: List<String>, sender: String, label: SampleLabel, source: String) -> Unit,
    onDeleteSample: (Long) -> Unit,
    onTrainNow: () -> Unit,
    onPromoteKeyword: (String) -> Unit,
    onBuildPackFromLearned: (List<String>) -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var dialogSender by remember { mutableStateOf("") }
    var dialogBody by remember { mutableStateOf("") }
    var dialogLabel by remember { mutableStateOf(SampleLabel.SPAM) }
    var dialogSource by remember { mutableStateOf(TrainingSampleSource.MANUAL) }

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
