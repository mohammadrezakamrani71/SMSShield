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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smsshield.app.data.database.QuarantineEntity
import com.smsshield.app.data.database.QuarantineFeedbackState
import com.smsshield.app.util.PersianDateFormatter
import org.json.JSONArray

private enum class QuarantineFilter(val label: String) {
    ALL("همه"),
    SPAM("تبلیغاتی"),
    SUSPICIOUS("مشکوک")
}

@Composable
fun QuarantineScreen(
    messages: List<QuarantineEntity>,
    onToggleFeedback: (QuarantineEntity, String) -> Unit,
    onAddToWhitelist: (String) -> Unit,
    onBlockSender: (String) -> Unit,
    onDeleteMessage: (Long) -> Unit,
    onClearAll: () -> Unit
) {
    var selectedFilter by remember { mutableStateOf(QuarantineFilter.ALL) }
    var showClearConfirm by remember { mutableStateOf(false) }

    val filteredMessages = remember(messages, selectedFilter) {
        when (selectedFilter) {
            QuarantineFilter.ALL -> messages
            QuarantineFilter.SPAM -> messages.filter { it.verdict == "SPAM" }
            QuarantineFilter.SUSPICIOUS -> messages.filter { it.verdict == "SUSPICIOUS" }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QuarantineFilter.entries.forEach { filter ->
                    FilterChip(
                        selected = selectedFilter == filter,
                        onClick = { selectedFilter = filter },
                        label = { Text(filter.label) }
                    )
                }
            }
            if (messages.isNotEmpty()) {
                TextButton(onClick = { showClearConfirm = true }) {
                    Text("پاکسازی لیست", color = MaterialTheme.colorScheme.error)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (filteredMessages.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "پیامی در این بخش ثبت نشده است.",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filteredMessages, key = { it.id }) { item ->
                    QuarantineCard(
                        item = item,
                        onToggleConfirmSpam = {
                            onToggleFeedback(item, QuarantineFeedbackState.CONFIRMED_SPAM)
                        },
                        onToggleFalsePositive = {
                            onToggleFeedback(item, QuarantineFeedbackState.FALSE_POSITIVE)
                        },
                        onAddToWhitelist = { onAddToWhitelist(item.sender) },
                        onBlockSender = { onBlockSender(item.sender) },
                        onDelete = { onDeleteMessage(item.id) }
                    )
                }
            }
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("پاکسازی کامل قرنطینه") },
            text = {
                Text("آیا مطمئن هستید که می‌خواهید تمام پیام‌های ثبت‌شده در لیست قرنطینه را حذف کنید؟ قوانین و نمونه‌های یادگیری شما حفظ می‌شوند.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showClearConfirm = false
                        onClearAll()
                    }
                ) {
                    Text("پاکسازی")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text("انصراف")
                }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuarantineCard(
    item: QuarantineEntity,
    onToggleConfirmSpam: () -> Unit,
    onToggleFalsePositive: () -> Unit,
    onAddToWhitelist: () -> Unit,
    onBlockSender: () -> Unit,
    onDelete: () -> Unit
) {
    val reasons = remember(item.reasonsJson) { parseReasons(item.reasonsJson) }
    val formattedDate = remember(item.timestamp) {
        PersianDateFormatter.formatTimestamp(item.timestamp)
    }

    val isConfirmedSpam = item.feedbackState == QuarantineFeedbackState.CONFIRMED_SPAM
    val isFalsePos = item.feedbackState == QuarantineFeedbackState.FALSE_POSITIVE || item.isFalsePositive

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isFalsePos -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
                isConfirmedSpam -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
                else -> MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.sender.ifBlank { "فرستنده ناشناس" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = formattedDate,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AssistChip(
                        onClick = {},
                        label = {
                            val statusText = if (item.verdict == "SPAM") "تبلیغاتی" else "مشکوک"
                            Text("$statusText • امتیاز ${item.score}")
                        }
                    )
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "حذف پیام"
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = item.body,
                style = MaterialTheme.typography.bodyMedium
            )

            if (reasons.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                reasons.forEach { reason ->
                    Text(
                        text = "• $reason",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Decoupled Reversible Learning Feedback Row
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = isConfirmedSpam,
                    onClick = onToggleConfirmSpam,
                    label = { Text("درست بود 👍") }
                )
                FilterChip(
                    selected = isFalsePos,
                    onClick = onToggleFalsePositive,
                    label = { Text("اسپم نیست ❌") }
                )
            }

            Spacer(modifier = Modifier.height(6.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(4.dp))

            // Independent Sender List Action Row (ALWAYS clickable regardless of feedback state)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(
                    onClick = onAddToWhitelist,
                    enabled = item.sender.isNotBlank()
                ) {
                    Text("لیست مجاز")
                }
                TextButton(
                    onClick = onBlockSender,
                    enabled = item.sender.isNotBlank()
                ) {
                    Text(
                        text = "بلاک سرشماره",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

private fun parseReasons(reasonsJson: String): List<String> {
    return runCatching {
        val arr = JSONArray(reasonsJson)
        List(arr.length()) { index -> arr.optString(index) }.filter { it.isNotBlank() }
    }.getOrDefault(emptyList())
}
