package com.smsshield.app

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.smsshield.app.ui.DashboardScreen
import com.smsshield.app.ui.QuarantineScreen
import com.smsshield.app.ui.SettingsScreen
import com.smsshield.app.ui.ShieldViewModel

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as SmsShieldApplication

        setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    val viewModel: ShieldViewModel = viewModel(
                        factory = ShieldViewModel.Factory(app.repository, app.preferences)
                    )
                    SmsShieldRoot(
                        viewModel = viewModel,
                        onOpenNotificationSettings = {
                            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SmsShieldRoot(
    viewModel: ShieldViewModel,
    onOpenNotificationSettings: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasAccess by remember {
        mutableStateOf(
            NotificationManagerCompat.getEnabledListenerPackages(context)
                .contains(context.packageName)
        )
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasAccess = NotificationManagerCompat.getEnabledListenerPackages(context)
                    .contains(context.packageName)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var selectedTab by remember { mutableIntStateOf(0) }

    val isActiveMode by viewModel.isActiveMode.collectAsStateWithLifecycle()
    val spamThreshold by viewModel.spamThreshold.collectAsStateWithLifecycle()
    val blockedToday by viewModel.blockedTodayCount.collectAsStateWithLifecycle()
    val totalBlocked by viewModel.totalBlockedCount.collectAsStateWithLifecycle()
    val suspiciousLogged by viewModel.suspiciousCount.collectAsStateWithLifecycle()
    val quarantineMessages by viewModel.quarantineMessages.collectAsStateWithLifecycle()
    val whitelistedSenders by viewModel.whitelistedSenders.collectAsStateWithLifecycle()
    val customRules by viewModel.customRules.collectAsStateWithLifecycle()

    val titles = listOf("داشبورد", "قرنطینه", "تنظیمات")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("سپر پیامک • ${titles[selectedTab]}") }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Home, contentDescription = "داشبورد") },
                    label = { Text("داشبورد") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.List, contentDescription = "قرنطینه") },
                    label = { Text("قرنطینه") }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "تنظیمات") },
                    label = { Text("تنظیمات") }
                )
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when (selectedTab) {
                0 -> DashboardScreen(
                    hasNotificationAccess = hasAccess,
                    isActiveMode = isActiveMode,
                    blockedToday = blockedToday,
                    totalBlocked = totalBlocked,
                    suspiciousLogged = suspiciousLogged,
                    onOpenNotificationSettings = onOpenNotificationSettings,
                    onToggleActiveMode = viewModel::setActiveMode
                )
                1 -> QuarantineScreen(
                    messages = quarantineMessages,
                    onMarkNotSpam = viewModel::markNotSpam,
                    onAddToWhitelist = { viewModel.whitelistSender(it) }
                )
                2 -> SettingsScreen(
                    spamThreshold = spamThreshold,
                    whitelistedSenders = whitelistedSenders,
                    customRules = customRules,
                    onThresholdChange = viewModel::setSpamThreshold,
                    onAddWhitelist = { viewModel.whitelistSender(it) },
                    onRemoveWhitelist = viewModel::removeWhitelistedSender,
                    onAddCustomRule = viewModel::addCustomRule,
                    onRemoveCustomRule = viewModel::removeCustomRule
                )
            }
        }
    }
}
