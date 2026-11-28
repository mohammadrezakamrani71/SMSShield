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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun DashboardScreen(
    hasNotificationAccess: Boolean,
    isActiveMode: Boolean,
    blockedToday: Int,
    totalBlocked: Int,
    suspiciousLogged: Int,
    onOpenNotificationSettings: () -> Unit,
    onToggleActiveMode: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Community Slogan Banner
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer
            )
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = AppIdentity.APP_SLOGAN_FA,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "صددرصد آفلاین، بدون دسترسی پیامک و اینترنت — با یادگیری هوشمند روی گوشی شما.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
        }

        // Notification Access Status Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (hasNotificationAccess) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    MaterialTheme.colorScheme.errorContainer
                }
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = if (hasNotificationAccess) {
                        "دسترسی به اعلان‌ها فعال است"
                    } else {
                        "دسترسی به اعلان‌ها غیرفعال است"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (hasNotificationAccess) {
                        "سپر پیامک به‌صورت آفلاین اعلان پیامک‌های ورودی را بررسی می‌کند."
                    } else {
                        "برای فیلتر اعلان پیامک‌های تبلیغاتی بدون دسترسی به صندوق پیامک، دسترسی اعلان را فعال کنید."
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(onClick = onOpenNotificationSettings) {
                    Text(
                        text = if (hasNotificationAccess) {
                            "مدیریت دسترسی اعلان‌ها"
                        } else {
                            "فعال‌سازی دسترسی اعلان‌ها"
                        }
                    )
                }
            }
        }

        // Learning Mode vs Active Mode Toggle Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isActiveMode) "سپر فعال" else "حالت یادگیری",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (isActiveMode) {
                            "اعلان پیامک‌های تبلیغاتی قطعی به‌طور خودکار حذف و در قرنطینه ثبت می‌شوند."
                        } else {
                            "هیچ اعلانی حذف نمی‌شود؛ پیامک‌های تبلیغاتی و مشکوک فقط در قرنطینه ثبت می‌شوند."
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Switch(
                    checked = isActiveMode,
                    onCheckedChange = onToggleActiveMode
                )
            }
        }

        // Counters Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StatCard(
                title = "مسدودی امروز",
                value = blockedToday.toString(),
                modifier = Modifier.weight(1f)
            )
            StatCard(
                title = "کل مسدودی‌ها",
                value = totalBlocked.toString(),
                modifier = Modifier.weight(1f)
            )
            StatCard(
                title = "مشکوک ثبت‌شده",
                value = suspiciousLogged.toString(),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun StatCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}
