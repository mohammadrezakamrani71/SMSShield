package com.smsshield.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.smsshield.app.data.database.ThemeMode
import com.smsshield.app.ui.DashboardScreen
import com.smsshield.app.ui.PacksScreen
import com.smsshield.app.ui.QuarantineScreen
import com.smsshield.app.ui.SettingsScreen
import com.smsshield.app.ui.ShieldViewModel
import com.smsshield.app.ui.SmartLearningScreen
import com.smsshield.app.ui.TapToTeachDialog
import com.smsshield.app.ui.theme.SMSShieldTheme
import com.smsshield.engine.RulePack
import com.smsshield.engine.RulePackCodec
import com.smsshield.engine.learning.InspectedToken
import com.smsshield.engine.model.SenderMatchType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val COMMUNITY_PACKS_DISCUSSIONS_URL =
    "https://github.com/mohammadrezakamrani71/SMSShield/discussions/categories/keyword-packs"

class MainActivity : ComponentActivity() {

    private val incomingSharedTexts = MutableStateFlow<List<String>>(emptyList())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleShareIntent(intent)

        val app = application as SmsShieldApplication

        setContent {
            val viewModel: ShieldViewModel = viewModel(
                factory = ShieldViewModel.Factory(app.repository, app.preferences)
            )
            val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
            val pendingShared by incomingSharedTexts.collectAsStateWithLifecycle()

            SMSShieldTheme(themeMode = themeMode) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    SmsShieldRoot(
                        viewModel = viewModel,
                        themeMode = themeMode,
                        pendingSharedTexts = pendingShared,
                        onConsumePendingShared = { incomingSharedTexts.value = emptyList() },
                        onOpenNotificationSettings = {
                            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                        },
                        onOpenBatteryOptimizationSettings = {
                            runCatching {
                                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                            }.onFailure {
                                runCatching {
                                    startActivity(
                                        Intent(
                                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                            Uri.fromParts("package", packageName, null)
                                        )
                                    )
                                }
                            }
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShareIntent(intent)
    }

    private fun handleShareIntent(intent: Intent?) {
        if (intent == null) return
        val extracted = when (intent.action) {
            Intent.ACTION_SEND -> {
                val text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()?.trim()
                if (!text.isNullOrEmpty()) listOf(text) else emptyList()
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                val list = intent.getCharSequenceArrayListExtra(Intent.EXTRA_TEXT)
                    ?.mapNotNull { it?.toString()?.trim()?.takeIf { s -> s.isNotEmpty() } }
                    ?: intent.getStringArrayListExtra(Intent.EXTRA_TEXT)
                        ?.mapNotNull { it?.trim()?.takeIf { s -> s.isNotEmpty() } }
                    ?: emptyList()
                list
            }
            else -> emptyList()
        }
        if (extracted.isNotEmpty()) {
            incomingSharedTexts.value = extracted
        }
    }
}

private fun checkIgnoringBatteryOptimizations(context: Context): Boolean {
    return runCatching {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        pm?.isIgnoringBatteryOptimizations(context.packageName) == true
    }.getOrDefault(false)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SmsShieldRoot(
    viewModel: ShieldViewModel,
    themeMode: ThemeMode,
    pendingSharedTexts: List<String>,
    onConsumePendingShared: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onOpenBatteryOptimizationSettings: () -> Unit,
    onOpenCommunityDiscussions: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var hasAccess by remember {
        mutableStateOf(
            NotificationManagerCompat.getEnabledListenerPackages(context)
                .contains(context.packageName)
        )
    }
    var isIgnoringBattery by remember {
        mutableStateOf(checkIgnoringBatteryOptimizations(context))
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasAccess = NotificationManagerCompat.getEnabledListenerPackages(context)
                    .contains(context.packageName)
                isIgnoringBattery = checkIgnoringBatteryOptimizations(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var selectedTab by remember { mutableIntStateOf(0) }
    var simulatorOpenTrigger by remember { mutableIntStateOf(0) }
    var backupStatusMessage by remember { mutableStateOf<String?>(null) }
    var packStatusMessage by remember { mutableStateOf<String?>(null) }
    var learningStatusMessage by remember { mutableStateOf<String?>(null) }
    var pendingPreviewPack by remember { mutableStateOf<RulePack?>(null) }
    var pendingPackJsonToSave by remember { mutableStateOf<String?>(null) }
    var prefilledStudioKeywords by remember { mutableStateOf<List<String>>(emptyList()) }

    // Reusable Tap-to-Teach Dialog state
    var tapToTeachBody by remember { mutableStateOf<String?>(null) }
    var tapToTeachTokens by remember { mutableStateOf<List<InspectedToken>>(emptyList()) }

    val openTapToTeach: (String) -> Unit = { body ->
        scope.launch {
            tapToTeachTokens = viewModel.inspectMessageTokens(body)
            tapToTeachBody = body
        }
    }

    val copyDiscountCode: (String) -> Unit = { code ->
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("SMSShield Discount Code", code))
        scope.launch {
            snackbarHostState.showSnackbar("کد تخفیف $code کپی شد.")
        }
    }

    LaunchedEffect(pendingSharedTexts) {
        if (pendingSharedTexts.isNotEmpty()) {
            selectedTab = 2
        }
    }

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
                        "${summary.addedBlockedSenders} سرشماره، ${summary.addedWhitelist} لیست مجاز و " +
                        "${summary.addedTrainingSamples} نمونه یادگیری اضافه شد."
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
    val diagnostics by viewModel.diagnostics.collectAsStateWithLifecycle()
    val blockedToday by viewModel.blockedTodayCount.collectAsStateWithLifecycle()
    val totalBlocked by viewModel.totalBlockedCount.collectAsStateWithLifecycle()
    val suspiciousLogged by viewModel.suspiciousCount.collectAsStateWithLifecycle()
    val quarantineMessages by viewModel.quarantineMessages.collectAsStateWithLifecycle()
    val whitelistedSenders by viewModel.whitelistedSenders.collectAsStateWithLifecycle()
    val blockedSenders by viewModel.blockedSenders.collectAsStateWithLifecycle()
    val customRules by viewModel.customRules.collectAsStateWithLifecycle()
    val totalUserHits by viewModel.totalUserHits.collectAsStateWithLifecycle()
    val trainingSamples by viewModel.trainingSamples.collectAsStateWithLifecycle()
    val learnedFeatures by viewModel.learnedFeatures.collectAsStateWithLifecycle()
    val learningReport by viewModel.learningReport.collectAsStateWithLifecycle()

    val titles = listOf("داشبورد", "قرنطینه", "یادگیری", "پک‌ها", "تنظیمات")

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("سپر پیامک • ${titles[selectedTab]}") },
                actions = {
                    IconButton(
                        onClick = {
                            val next = when (themeMode) {
                                ThemeMode.SYSTEM -> ThemeMode.DARK
                                ThemeMode.DARK -> ThemeMode.LIGHT
                                ThemeMode.LIGHT -> ThemeMode.SYSTEM
                            }
                            viewModel.setThemeMode(next)
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Brightness6,
                            contentDescription = "تغییر پوسته"
                        )
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Home, contentDescription = "داشبورد") },
                    label = { Text("داشبورد", maxLines = 1, overflow = TextOverflow.Ellipsis) }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = "قرنطینه") },
                    label = { Text("قرنطینه", maxLines = 1, overflow = TextOverflow.Ellipsis) }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.Psychology, contentDescription = "یادگیری") },
                    label = { Text("یادگیری", maxLines = 1, overflow = TextOverflow.Ellipsis) }
                )
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    icon = { Icon(Icons.Default.Star, contentDescription = "پک‌ها") },
                    label = { Text("پک‌ها", maxLines = 1, overflow = TextOverflow.Ellipsis) }
                )
                NavigationBarItem(
                    selected = selectedTab == 4,
                    onClick = { selectedTab = 4 },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "تنظیمات") },
                    label = { Text("تنظیمات", maxLines = 1, overflow = TextOverflow.Ellipsis) }
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
                    onToggleActiveMode = viewModel::setActiveMode,
                    onOpenSimulator = {
                        simulatorOpenTrigger++
                        selectedTab = 2
                    }
                )
                1 -> QuarantineScreen(
                    messages = quarantineMessages,
                    onToggleFeedback = { msg, state ->
                        viewModel.toggleQuarantineFeedback(msg, state)
                    },
                    onAddToWhitelist = { sender ->
                        viewModel.whitelistSender(sender)
                        scope.launch {
                            snackbarHostState.showSnackbar("فرستنده $sender به لیست مجاز اضافه شد.")
                        }
                    },
                    onBlockSender = { sender ->
                        viewModel.addBlockedSender(sender, SenderMatchType.EXACT)
                        scope.launch {
                            snackbarHostState.showSnackbar("سرشماره $sender مسدود شد.")
                        }
                    },
                    onDeleteMessage = viewModel::deleteQuarantineMessage,
                    onClearAll = viewModel::clearAllQuarantine,
                    isLikelyMixedSender = viewModel::isLikelyMixedSender,
                    onOpenTapToTeach = openTapToTeach,
                    onCopyDiscountCode = copyDiscountCode
                )
                2 -> SmartLearningScreen(
                    samples = trainingSamples,
                    learnedFeatures = learnedFeatures,
                    report = learningReport,
                    pendingSharedTexts = pendingSharedTexts,
                    learningStatusMessage = learningStatusMessage,
                    spamThreshold = spamThreshold,
                    simulatorOpenTrigger = simulatorOpenTrigger,
                    onConsumePendingShared = onConsumePendingShared,
                    onAddSamples = { bodies, sender, label, source ->
                        scope.launch {
                            val count = viewModel.addTrainingSamples(bodies, sender, label, source)
                            learningStatusMessage = "$count نمونه جدید ثبت و مدل هوشمند به‌روزرسانی شد."
                        }
                    },
                    onDeleteSample = viewModel::deleteTrainingSample,
                    onTrainNow = {
                        scope.launch {
                            val reportResult = viewModel.runSmartTraining()
                            learningStatusMessage = "آموزش کامل شد: ${reportResult.topSpamPhrases.size} الگوی تبلیغاتی شناسایی شد."
                        }
                    },
                    onPromoteKeyword = { keyword ->
                        viewModel.addCustomRule(keyword, 25)
                        learningStatusMessage = "کلیدواژه «$keyword» به کلیدواژه‌های سفارشی اضافه شد."
                    },
                    onBuildPackFromLearned = { keywords ->
                        prefilledStudioKeywords = keywords
                        selectedTab = 3
                    },
                    onSimulateSms = viewModel::simulateClassification,
                    onInspectTokens = viewModel::inspectMessageTokens,
                    onOpenTapToTeach = openTapToTeach,
                    onCopyDiscountCode = copyDiscountCode
                )
                3 -> PacksScreen(
                    totalUserHits = totalUserHits,
                    customRules = customRules,
                    blockedSenders = blockedSenders,
                    pendingPreviewPack = pendingPreviewPack,
                    packStatusMessage = packStatusMessage,
                    prefilledStudioKeywords = prefilledStudioKeywords,
                    onConsumePrefilledStudio = { prefilledStudioKeywords = emptyList() },
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
                4 -> SettingsScreen(
                    spamThreshold = spamThreshold,
                    themeMode = themeMode,
                    whitelistedSenders = whitelistedSenders,
                    blockedSenders = blockedSenders,
                    customRules = customRules,
                    backupStatusMessage = backupStatusMessage,
                    hasNotificationAccess = hasAccess,
                    isIgnoringBatteryOptimizations = isIgnoringBattery,
                    diagnostics = diagnostics,
                    onOpenNotificationSettings = onOpenNotificationSettings,
                    onOpenBatteryOptimizationSettings = onOpenBatteryOptimizationSettings,
                    onThresholdChange = viewModel::setSpamThreshold,
                    onThemeModeChange = viewModel::setThemeMode,
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

    val activeTapToTeachBody = tapToTeachBody
    if (activeTapToTeachBody != null) {
        TapToTeachDialog(
            messageBody = activeTapToTeachBody,
            inspectedTokens = tapToTeachTokens,
            onConfirmAddKeywords = { keywords, weight ->
                viewModel.addCustomRulesBatch(keywords, weight)
                tapToTeachBody = null
                scope.launch {
                    snackbarHostState.showSnackbar("${keywords.size} کلیدواژه به قوانین سفارشی اضافه شد.")
                }
            },
            onDismiss = { tapToTeachBody = null }
        )
    }
}
