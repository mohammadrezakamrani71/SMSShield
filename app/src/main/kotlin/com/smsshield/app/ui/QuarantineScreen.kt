package com.smsshield.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import org.json.JSONArray

private enum class QuarantineFilter(val label: String) {
    ALL("همه"),
    SPAM("تبلیغاتی"),
    SUSPICIOUS("مشکوک")
}

@Composable
fun QuarantineScreen(
    messages: List<QuarantineEntity>,
    onMarkNotSpam: (Long) -> Unit,
    onAddToWhitelist: (String) -> Unit
) {
    var selectedFilter by remember { mutableStateOf(QuarantineFilter.ALL) }

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
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            QuarantineFilter.entries.forEach { filter ->
                FilterChip(
                    selected = selectedFilter == filter,
                    onClick = { selectedFilter = filter },
                    label = { Text(filter.label) }
                )
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
                        onMarkNotSpam = { onMarkNotSpam(item.id) },
                        onAddToWhitelist = { onAddToWhitelist(item.sender) }
                    )
                }
            }
        }
    }
}

@Composable
private fun QuarantineCard(
    item: QuarantineEntity,
    onMarkNotSpam: () -> Unit,
    onAddToWhitelist: () -> Unit
) {
    val reasons = remember(item.reasonsJson) { parseReasons(item.reasonsJson) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (item.isFalsePositive) {
                MaterialTheme.colorScheme.surfaceVariant
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.sender.ifBlank { "فرستنده ناشناس" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                AssistChip(
                    onClick = {},
                    label = {
                        val statusText = if (item.verdict == "SPAM") "تبلیغاتی" else "مشکوک"
                        Text("$statusText • امتیاز ${item.score}")
                    }
                )
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

            if (item.isFalsePositive) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "علامت‌گذاری‌شده به‌عنوان غیرتبلیغاتی",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onMarkNotSpam,
                    enabled = !item.isFalsePositive
                ) {
                    Text("اسپم نیست")
                }
                TextButton(onClick = onAddToWhitelist) {
                    Text("افزودن به لیست مجاز")
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
