package com.smsshield.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
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
import androidx.compose.runtime.rememberCoroutineScope
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
import com.smsshield.app.ui.PacksScreen
import com.smsshield.app.ui.QuarantineScreen
import com.smsshield.app.ui.SettingsScreen
import com.smsshield.app.ui.ShieldViewModel
import com.smsshield.engine.RulePack
import com.smsshield.engine.RulePackCodec
import com.smsshield.engine.model.SenderMatchType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val COMMUNITY_PACKS_DISCUSSIONS_URL =
    "https://github.com/mohammadrezakamrani71/SMSShield/discussions/categories/keyword-packs"

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
                        },
                        onOpenCommunityDiscussions = {
                            runCatching {
                                startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(COMMUNITY_PACKS_DISCUSSIONS_URL))
                                )
                            }
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
    onOpenNotificationSettings: () -> Unit,
    onOpenCommunityDiscussions: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

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
    var backupStatusMessage by remember { mutableStateOf<String?>(null) }
    var packStatusMessage by remember { mutableStateOf<String?>(null) }
    var pendingPreviewPack by remember { mutableStateOf<RulePack?>(null) }
    var pendingPackJsonToSave by remember { mutableStateOf<String?>(null) }

    val exportBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = runCatching {
                val json = viewModel.exportBackupJson()
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(json.toByteArray(Charsets.UTF_8))
                    } ?: error("دسترسی به فایل ممکن نشد")
                }
            }
            backupStatusMessage = result.fold(
                onSuccess = { "پشتیبان‌گیری با موفقیت ذخیره شد." },
                onFailure = { "خطا در ذخیره پشتیبان: ${it.message}" }
            )
        }
    }

    val importBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val raw = runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)
                        ?.use { it.readText() }
                        ?: error("خواندن فایل ممکن نشد")
                }
            }
            raw.onSuccess { content ->
                viewModel.importBackupJson(content).onSuccess { summary ->
                    backupStatusMessage = "بازیابی انجام شد: ${summary.addedKeywords} کلیدواژه، " +
                        "${summary.addedBlockedSenders} سرشماره مسدود و ${summary.addedWhitelist} لیست مجاز اضافه شد."
                }.onFailure { err ->
                    backupStatusMessage = "فایل پشتیبان نامعتبر است: ${err.message}"
                }
            }.onFailure { err ->
                backupStatusMessage = "خطا در خواندن فایل: ${err.message}"
            }
        }
    }

    val exportPackLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        val content = pendingPackJsonToSave
        pendingPackJsonToSave = null
        if (uri == null || content == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(content.toByteArray(Charsets.UTF_8))
                    } ?: error("ذخیره فایل ممکن نشد")
                }
            }
            packStatusMessage = result.fold(
                onSuccess = { "فایل پک با موفقیت ذخیره شد." },
                onFailure = { "خطا در ذخیره پک: ${it.message}" }
            )
        }
    }

    val importPackLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val raw = runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)
                        ?.use { it.readText() }
                        ?: error("خواندن فایل ممکن نشد")
                }
            }
            raw.onSuccess { content ->
                RulePackCodec.decodePackFromShareCodeOrJson(content).onSuccess { pack ->
                    pendingPreviewPack = pack
                }.onFailure { err ->
                    packStatusMessage = "فایل پک نامعتبر است: ${err.message}"
                }
            }.onFailure { err ->
                packStatusMessage = "خطا در باز کردن فایل پک: ${err.message}"
            }
        }
    }

    val isActiveMode by viewModel.isActiveMode.collectAsStateWithLifecycle()
    val spamThreshold by viewModel.spamThreshold.collectAsStateWithLifecycle()
    val blockedToday by viewModel.blockedTodayCount.collectAsStateWithLifecycle()
    val totalBlocked by viewModel.totalBlockedCount.collectAsStateWithLifecycle()
    val suspiciousLogged by viewModel.suspiciousCount.collectAsStateWithLifecycle()
    val quarantineMessages by viewModel.quarantineMessages.collectAsStateWithLifecycle()
    val whitelistedSenders by viewModel.whitelistedSenders.collectAsStateWithLifecycle()
    val blockedSenders by viewModel.blockedSenders.collectAsStateWithLifecycle()
    val customRules by viewModel.customRules.collectAsStateWithLifecycle()
    val totalUserHits by viewModel.totalUserHits.collectAsStateWithLifecycle()

    val titles = listOf("داشبورد", "قرنطینه", "پک‌ها", "تنظیمات")

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
                    icon = { Icon(Icons.Default.Star, contentDescription = "پک‌ها") },
                    label = { Text("پک‌ها") }
                )
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
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
                    onAddToWhitelist = { viewModel.whitelistSender(it) },
                    onBlockSender = { viewModel.addBlockedSender(it, SenderMatchType.EXACT) }
                )
                2 -> PacksScreen(
                    totalUserHits = totalUserHits,
                    customRules = customRules,
                    blockedSenders = blockedSenders,
                    pendingPreviewPack = pendingPreviewPack,
                    packStatusMessage = packStatusMessage,
                    onOpenPackFile = {
                        importPackLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                    },
                    onSelectPackForPreview = { pack -> pendingPreviewPack = pack },
                    onDismissPreview = { pendingPreviewPack = null },
                    onConfirmImportSelected = { keywords, senders ->
                        pendingPreviewPack = null
                        scope.launch {
                            val summary = viewModel.importSelectedPackItems(keywords, senders)
                            packStatusMessage = "${summary.totalAdded} مورد جدید به قوانین شما اضافه شد " +
                                "(${summary.addedKeywords} کلیدواژه و ${summary.addedBlockedSenders} سرشماره)."
                        }
                    },
                    onSavePackJson = { fileName, json ->
                        pendingPackJsonToSave = json
                        exportPackLauncher.launch(fileName)
                    },
                    onCopyToClipboard = { label, text, feedback ->
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
                        packStatusMessage = feedback
                    },
                    onOpenCommunityDiscussions = onOpenCommunityDiscussions,
                    onReportStatus = { msg -> packStatusMessage = msg }
                )
                3 -> SettingsScreen(
                    spamThreshold = spamThreshold,
                    whitelistedSenders = whitelistedSenders,
                    blockedSenders = blockedSenders,
                    customRules = customRules,
                    backupStatusMessage = backupStatusMessage,
                    onThresholdChange = viewModel::setSpamThreshold,
                    onAddWhitelist = { viewModel.whitelistSender(it) },
                    onRemoveWhitelist = viewModel::removeWhitelistedSender,
                    onAddBlockedSender = viewModel::addBlockedSender,
                    onRemoveBlockedSender = viewModel::removeBlockedSender,
                    onAddCustomRule = viewModel::addCustomRule,
                    onRemoveCustomRule = viewModel::removeCustomRule,
                    onExportBackup = {
                        exportBackupLauncher.launch("smsshield-backup.json")
                    },
                    onImportBackup = {
                        importBackupLauncher.launch(arrayOf("application/json", "*/*"))
                    }
                )
            }
        }
    }
}
