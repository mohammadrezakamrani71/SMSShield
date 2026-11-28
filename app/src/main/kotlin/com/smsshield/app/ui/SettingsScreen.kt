package com.smsshield.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smsshield.app.data.database.BlockedSenderEntity
import com.smsshield.app.data.database.CustomRuleEntity
import com.smsshield.app.data.database.ThemeMode
import com.smsshield.app.data.database.WhitelistEntity
import com.smsshield.engine.model.SenderMatchType
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    spamThreshold: Int,
    themeMode: ThemeMode,
    whitelistedSenders: List<WhitelistEntity>,
    blockedSenders: List<BlockedSenderEntity>,
    customRules: List<CustomRuleEntity>,
    backupStatusMessage: String?,
    onThresholdChange: (Int) -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    onAddWhitelist: (String) -> Unit,
    onRemoveWhitelist: (WhitelistEntity) -> Unit,
    onAddBlockedSender: (String, SenderMatchType) -> Unit,
    onRemoveBlockedSender: (BlockedSenderEntity) -> Unit,
    onAddCustomRule: (String, Int) -> Unit,
    onRemoveCustomRule: (CustomRuleEntity) -> Unit,
    onExportBackup: () -> Unit,
    onImportBackup: () -> Unit
) {
    var newSender by remember { mutableStateOf("") }
    var newBlockedPattern by remember { mutableStateOf("") }
    var selectedMatchType by remember { mutableStateOf(SenderMatchType.EXACT) }
    var newKeyword by remember { mutableStateOf("") }
    var newWeightText by remember { mutableStateOf("25") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Theme Selector (System / Light / Dark)
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "پوسته ظاهری برنامه",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = themeMode == ThemeMode.SYSTEM,
                        onClick = { onThemeModeChange(ThemeMode.SYSTEM) },
                        label = { Text("خودکار سیستم") }
                    )
                    FilterChip(
                        selected = themeMode == ThemeMode.LIGHT,
                        onClick = { onThemeModeChange(ThemeMode.LIGHT) },
                        label = { Text("روشن") }
                    )
                    FilterChip(
                        selected = themeMode == ThemeMode.DARK,
                        onClick = { onThemeModeChange(ThemeMode.DARK) },
                        label = { Text("تاریک") }
                    )
                }
            }
        }

        // Sensitivity Threshold Slider
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "آستانه حساسیت تشخیص اسپم: $spamThreshold",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "مقدار کمتر = حساسیت بیشتر (پیش‌فرض ۷۵). پیامک‌های با امتیاز حداقل ${spamThreshold - 25} به‌عنوان مشکوک ثبت می‌شوند.",
                    style = MaterialTheme.typography.bodySmall
                )
                Slider(
                    value = spamThreshold.toFloat(),
                    onValueChange = { onThresholdChange(it.roundToInt()) },
                    valueRange = 50f..100f,
                    steps = 9
                )
            }
        }

        // Blocked Senders
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "بلاک سرشماره",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = selectedMatchType == SenderMatchType.EXACT,
                        onClick = { selectedMatchType = SenderMatchType.EXACT },
                        label = { Text("تطابق دقیق (550021)") }
                    )
                    FilterChip(
                        selected = selectedMatchType == SenderMatchType.PREFIX,
                        onClick = { selectedMatchType = SenderMatchType.PREFIX },
                        label = { Text("پیشوند / شروع با (*)") }
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = newBlockedPattern,
                        onValueChange = { newBlockedPattern = it },
                        label = { Text("سرشماره یا پیشوند") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = {
                            if (newBlockedPattern.isNotBlank()) {
                                onAddBlockedSender(newBlockedPattern, selectedMatchType)
                                newBlockedPattern = ""
                            }
                        }
                    ) {
                        Text("بلاک")
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                if (blockedSenders.isEmpty()) {
                    Text(
                        text = "هنوز سرشماره‌ای مسدود نشده است.",
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    blockedSenders.forEach { blocked ->
                        val typeLabel = if (blocked.matchType == SenderMatchType.PREFIX.name) {
                            "پیشوند"
                        } else {
                            "دقیق"
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${blocked.displayPattern} ($typeLabel • ${blocked.hitCount} شکار)",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            IconButton(onClick = { onRemoveBlockedSender(blocked) }) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "حذف"
                                )
                            }
                        }
                    }
                }
            }
        }

        // Whitelisted Senders
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "لیست مجاز فرستنده‌ها",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = newSender,
                        onValueChange = { newSender = it },
                        label = { Text("شماره یا نام فرستنده") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = {
                            if (newSender.isNotBlank()) {
                                onAddWhitelist(newSender)
                                newSender = ""
                            }
                        }
                    ) {
                        Text("افزودن")
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                if (whitelistedSenders.isEmpty()) {
                    Text(
                        text = "هنوز فرستنده‌ای در لیست مجاز ثبت نشده است.",
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    whitelistedSenders.forEach { sender ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = sender.displayName,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            IconButton(onClick = { onRemoveWhitelist(sender) }) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "حذف"
                                )
                            }
                        }
                    }
                }
            }
        }

        // Custom Keywords
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "کلیدواژه‌های سفارشی",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = newKeyword,
                        onValueChange = { newKeyword = it },
                        label = { Text("کلیدواژه") },
                        singleLine = true,
                        modifier = Modifier.weight(1.4f)
                    )
                    OutlinedTextField(
                        value = newWeightText,
                        onValueChange = { newWeightText = it },
                        label = { Text("وزن") },
                        singleLine = true,
                        modifier = Modifier.weight(0.7f)
                    )
                    Button(
                        onClick = {
                            val weight = newWeightText.toIntOrNull() ?: 25
                            if (newKeyword.isNotBlank()) {
                                onAddCustomRule(newKeyword, weight)
                                newKeyword = ""
                            }
                        }
                    ) {
                        Text("ثبت")
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                if (customRules.isEmpty()) {
                    Text(
                        text = "کلیدواژه سفارشی ثبت نشده است.",
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    customRules.forEach { rule ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${rule.keyword} (وزن: ${rule.weight} • ${rule.hitCount} شکار)",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            IconButton(onClick = { onRemoveCustomRule(rule) }) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "حذف"
                                )
                            }
                        }
                    }
                }
            }
        }

        // Local Backup & Restore
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "پشتیبان‌گیری و بازیابی تنظیمات",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "تمام کلیدواژه‌ها، سرشماره‌های مسدودشده، لیست مجاز، نمونه‌های یادگیری و تنظیمات را به‌صورت فایل JSON ذخیره یا بازیابی کنید.",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onExportBackup,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("خروجی پشتیبان")
                    }
                    OutlinedButton(
                        onClick = onImportBackup,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("بازیابی پشتیبان")
                    }
                }
                if (!backupStatusMessage.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = backupStatusMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // Phantom Ding Info Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "راهنمای صدای کوتاه اعلان (Phantom Ding)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "به‌دلیل محدودیت‌های اندروید، سپر پیامک اعلان‌های تبلیغاتی را بلافاصله پس از دریافت حذف می‌کند و ممکن است صدای کوتاه اعلان پخش شود. برای حذف کامل این صدا، می‌توانید کانال اعلان برنامه اصلی پیامک گوشی را بی‌صدا (Mute) کنید.\n\nهشدار مهم: بی‌صدا کردن کانال اعلان برنامه پیامک، صدای اعلان پیامک‌های عادی و مهم (مانند رمز پویا و پیام‌های شخصی) را نیز قطع می‌کند.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
