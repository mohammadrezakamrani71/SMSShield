package com.smsshield.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smsshield.engine.learning.InspectedToken
import com.smsshield.engine.learning.TokenSentiment

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TapToTeachDialog(
    messageBody: String,
    inspectedTokens: List<InspectedToken>,
    onConfirmAddKeywords: (keywords: List<String>, weight: Int) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedTokens by remember(messageBody) { mutableStateOf(setOf<String>()) }
    var customPhrase by remember(messageBody) { mutableStateOf("") }
    var selectedWeight by remember(messageBody) { mutableIntStateOf(25) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("آموزش کلمات کلیدی 🎯") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Text(
                        text = messageBody,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(10.dp)
                    )
                }

                Text(
                    text = "قرمز: تمایل به اسپم  •  سبز: تمایل به امن  •  خاکستری: خنثی",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (inspectedTokens.isEmpty()) {
                    Text(
                        text = "کلمه قابل استخراجی در این متن یافت نشد. می‌توانید عبارت دلخواه را پایین وارد کنید.",
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        inspectedTokens.forEach { item ->
                            val isSelected = item.token in selectedTokens
                            val chipColors = when (item.sentiment) {
                                TokenSentiment.SPAM_LEANING -> FilterChipDefaults.filterChipColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.65f),
                                    labelColor = MaterialTheme.colorScheme.onErrorContainer,
                                    selectedContainerColor = MaterialTheme.colorScheme.error,
                                    selectedLabelColor = MaterialTheme.colorScheme.onError
                                )
                                TokenSentiment.SAFE_LEANING -> FilterChipDefaults.filterChipColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                                    labelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                )
                                TokenSentiment.NEUTRAL -> FilterChipDefaults.filterChipColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    selectedTokens = if (isSelected) {
                                        selectedTokens - item.token
                                    } else {
                                        selectedTokens + item.token
                                    }
                                },
                                colors = chipColors,
                                label = {
                                    val suffix = if (item.isAlreadyCustomRule) " ✓" else ""
                                    Text("${item.token}$suffix")
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = customPhrase,
                    onValueChange = { customPhrase = it },
                    label = { Text("عبارت دوکلمه‌ای یا دلخواه (اختیاری)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "وزن کلیدواژه‌های انتخاب‌شده:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(15, 25, 35).forEach { weightOption ->
                        FilterChip(
                            selected = selectedWeight == weightOption,
                            onClick = { selectedWeight = weightOption },
                            label = { Text("+$weightOption") }
                        )
                    }
                }
            }
        },
        confirmButton = {
            val allKeywords = buildList {
                addAll(selectedTokens)
                val trimmed = customPhrase.trim()
                if (trimmed.isNotEmpty()) add(trimmed)
            }.distinct()

            Button(
                onClick = {
                    if (allKeywords.isNotEmpty()) {
                        onConfirmAddKeywords(allKeywords, selectedWeight)
                    }
                },
                enabled = allKeywords.isNotEmpty()
            ) {
                Text("افزودن کلمات انتخاب‌شده به کلیدواژه‌ها")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("انصراف")
            }
        }
    )
}
