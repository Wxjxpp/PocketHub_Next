package com.pockethub.ui.settings

import com.pockethub.R

import androidx.compose.ui.res.stringResource

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Brightness2
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.GTranslate
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.ManageAccounts
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pockethub.BuildConfig
import com.pockethub.ui.theme.AppStyle
import com.pockethub.ui.theme.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onNavigateToFeedSources: () -> Unit = {},
    onSignOut: () -> Unit = {},
    vm: SettingsViewModel = hiltViewModel(),
) {
    val themeMode by vm.themeMode.collectAsState()
    val appStyle by vm.appStyle.collectAsState()
    val appLocale by vm.appLocale.collectAsState()
    val customClientId by vm.customClientId.collectAsState()
    val customClientSecret by vm.customClientSecret.collectAsState()
    val notifPollMinutes by vm.notifPollMinutes.collectAsState()
    val accountCount by vm.accountCount.collectAsState()
    val cacheSizeBytes by vm.cacheSizeBytes.collectAsState()
    val translateTarget by vm.translateTarget.collectAsState()
    var showStyleSheet by remember { mutableStateOf(false) }
    var showLanguageSheet by remember { mutableStateOf(false) }
    var showTranslateSheet by remember { mutableStateOf(false) }
    var showNotifPollSheet by remember { mutableStateOf(false) }
    var showOAuthSheet by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var showSignOutDialog by remember { mutableStateOf(false) }
    val issueCount by vm.issueCount.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // Manual update check — independent Hilt VM so the same UpdateDialog is reused.
    val updateVm: com.pockethub.ui.main.UpdateViewModel = hiltViewModel()
    val updateState by updateVm.state.collectAsState()

    // Compute cache size once on entry so the user sees current disk usage.
    LaunchedEffect(Unit) {
        val bytes = withContext(Dispatchers.IO) { appCacheSize(context.cacheDir) }
        vm.setCacheSize(bytes)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings), fontWeight = FontWeight.SemiBold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.action_back)) } },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())) {
            SectionHeader(stringResource(R.string.section_appearance))
            // Single appearance entry — combines the old "theme mode"
            // (Dark/Light/System) and "app style" pickers into one coherent
            // list. The available styles double as dark/light theme presets.
            ListItem(
                leadingContent = { Icon(Icons.Outlined.Palette, contentDescription = null) },
                headlineContent = { Text(stringResource(R.string.app_style)) },
                supportingContent = { Text(styleLabel(appStyle)) },
                modifier = Modifier.clickable { showStyleSheet = true },
            )
            HorizontalDivider()

            SectionHeader(stringResource(R.string.section_language))
            ListItem(
                leadingContent = { Icon(Icons.Outlined.Translate, contentDescription = null) },
                headlineContent = { Text(stringResource(R.string.app_language)) },
                supportingContent = { Text(localeLabel(appLocale)) },
                modifier = Modifier.clickable { showLanguageSheet = true },
            )
            ListItem(
                leadingContent = { Icon(Icons.Outlined.GTranslate, contentDescription = null) },
                headlineContent = { Text(stringResource(R.string.translate_readme)) },
                supportingContent = {
                    Text(when (translateTarget) {
                        "zh" -> stringResource(R.string.translate_to_chinese)
                        "en" -> stringResource(R.string.translate_to_english)
                        else -> stringResource(R.string.translate_off)
                    })
                },
                modifier = Modifier.clickable { showTranslateSheet = true },
            )
            HorizontalDivider()

            SectionHeader(stringResource(R.string.section_notifications))
            ListItem(
                leadingContent = { Icon(Icons.Outlined.Notifications, contentDescription = null) },
                headlineContent = { Text(stringResource(R.string.polling_cadence)) },
                supportingContent = { Text(notificationCadenceLabel(notifPollMinutes)) },
                modifier = Modifier.clickable { showNotifPollSheet = true },
            )
            ListItem(
                leadingContent = { Icon(Icons.Outlined.Brightness2, contentDescription = null) },
                headlineContent = { Text(stringResource(R.string.system_notification_settings)) },
                supportingContent = { Text(stringResource(R.string.system_notification_settings_summary)) },
                modifier = Modifier.clickable { openAppNotificationSettings(context) },
            )
            HorizontalDivider()

            SectionHeader(stringResource(R.string.section_explore))
            ListItem(
                leadingContent = { Icon(Icons.Outlined.Public, contentDescription = null) },
                headlineContent = { Text(stringResource(R.string.feed_sources)) },
                supportingContent = { Text(stringResource(R.string.feed_sources_intro)) },
                modifier = Modifier.clickable { onNavigateToFeedSources() },
            )
            HorizontalDivider()

            SectionHeader(stringResource(R.string.section_account))
            ListItem(
                leadingContent = { Icon(Icons.Outlined.ManageAccounts, contentDescription = null) },
                headlineContent = { Text(stringResource(R.string.accounts)) },
                supportingContent = { Text(stringResource(R.string.accounts_summary, accountCount)) },
            )
            ListItem(
                leadingContent = { Icon(Icons.Outlined.VpnKey, contentDescription = null) },
                headlineContent = { Text(stringResource(R.string.custom_oauth_client)) },
                supportingContent = { Text(if (customClientId.isBlank()) stringResource(R.string.custom_oauth_client_not_configured) else stringResource(R.string.custom_oauth_client_configured, customClientId.take(8))) },
                modifier = Modifier.clickable { showOAuthSheet = true },
            )
            ListItem(
                leadingContent = { Icon(Icons.Outlined.CleaningServices, contentDescription = null) },
                headlineContent = { Text(stringResource(R.string.clear_cache)) },
                supportingContent = { Text(stringResource(R.string.clear_cache_summary, formatBytes(cacheSizeBytes))) },
                modifier = Modifier.clickable {
                    scope.launch {
                        val bytes = withContext(Dispatchers.IO) { clearCache(context.cacheDir) }
                        vm.setCacheSize(bytes)
                        snackbarHostState.showSnackbar(context.getString(R.string.cache_cleared))
                    }
                },
            )
            ListItem(
                leadingContent = { Icon(Icons.Outlined.Logout, contentDescription = null) },
                headlineContent = { Text(stringResource(R.string.action_sign_out)) },
                supportingContent = { Text(stringResource(R.string.sign_out_summary)) },
                modifier = Modifier.clickable { showSignOutDialog = true },
            )
            HorizontalDivider()

            SectionHeader(stringResource(R.string.section_severe_issues))
            ListItem(
                leadingContent = { Icon(Icons.Outlined.BugReport, contentDescription = null) },
                headlineContent = { Text(stringResource(R.string.severe_issues_send_now)) },
                supportingContent = {
                    Text(stringResource(R.string.severe_issues_one_tap_summary, issueCount))
                },
                modifier = Modifier.clickable(enabled = issueCount > 0) {
                    scope.launch {
                        val events = vm.issueEvents()
                        if (events.isEmpty()) {
                            snackbarHostState.showSnackbar(context.getString(R.string.severe_issues_none_local))
                        } else {
                            sendIssueReportByEmail(context, events)
                            vm.clearIssueLog()
                        }
                    }
                },
            )
            HorizontalDivider()


            SectionHeader(stringResource(R.string.section_about))
            ListItem(
                leadingContent = { Icon(Icons.Outlined.SystemUpdate, contentDescription = null) },
                headlineContent = { Text(stringResource(R.string.update_check_now)) },
                supportingContent = {
                    Text(
                        when (updateState) {
                            is com.pockethub.ui.main.UpdateViewModel.State.Checking -> stringResource(R.string.update_checking)
                            is com.pockethub.ui.main.UpdateViewModel.State.UpToDate -> stringResource(R.string.update_uptodate)
                            is com.pockethub.ui.main.UpdateViewModel.State.Error -> stringResource(R.string.update_check_failed)
                            is com.pockethub.ui.main.UpdateViewModel.State.UpdateAvailable -> {
                                val v = (updateState as com.pockethub.ui.main.UpdateViewModel.State.UpdateAvailable).info.latestVersionName
                                "${v} →"
                            }
                            else -> stringResource(R.string.update_check_summary)
                        }
                    )
                },
                modifier = Modifier.clickable { updateVm.manualCheck() },
            )
            ListItem(
                leadingContent = { Icon(Icons.Outlined.Info, contentDescription = null) },
                headlineContent = { Text(stringResource(R.string.about_pockethub)) },
                supportingContent = { Text(stringResource(R.string.version_template, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE)) },
                modifier = Modifier.clickable { showAbout = true },
            )
            Spacer(Modifier.height(48.dp))
        }
    }

    if (showStyleSheet) {
        // Curated list — drops the old "Default (follows theme)" row so every
        // option is a concrete style. `appStyle == null` (legacy / first-run)
        // shows up as Linear Dark selected, because resolveStyle maps null +
        // ThemeMode.Dark to AppStyle.LinearDark. Removing rows vs. removing the
        // enum entries themselves keeps any already-persisted preference
        // (Lavender / Forest) working — those users still see their baseline.
        val visibleStyles = listOf(
            AppStyle.LinearDark,
            AppStyle.PrimerLight,
            AppStyle.Paper,
            AppStyle.Neon,
        )
        ModalBottomSheet(onDismissRequest = { showStyleSheet = false }, sheetState = rememberModalBottomSheetState()) {
            Column(Modifier.padding(bottom = 24.dp)) {
                Text(stringResource(R.string.app_style), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(16.dp))
                visibleStyles.forEach { style ->
                    // Null appStyle resolves to LinearDark under the default
                    // (Dark) theme mode — show that as selected so first-run
                    // users land on a highlighted row.
                    val isSelected = appStyle == style || (appStyle == null && style == AppStyle.LinearDark)
                    StyleOption(style, isSelected) {
                        vm.setAppStyle(style)
                        // Keep themeMode consistent: dark styles → Dark, light styles → Light,
                        // so the legacy status-bar / system-bar tint logic stays correct.
                        vm.setThemeMode(if (com.pockethub.ui.theme.styleDef(style).isDark) ThemeMode.Dark else ThemeMode.Light)
                        showStyleSheet = false
                    }
                }
            }
        }
    }

    if (showLanguageSheet) {
        ModalBottomSheet(onDismissRequest = { showLanguageSheet = false }, sheetState = rememberModalBottomSheetState()) {
            Column(Modifier.padding(bottom = 24.dp)) {
                Text(stringResource(R.string.language), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(16.dp))
                AppLocale.entries.forEach { locale ->
                    LanguageOption(
                        label = localeLabel(locale),
                        selected = appLocale == locale,
                        onClick = { vm.setAppLocale(locale); showLanguageSheet = false },
                    )
                }
            }
        }
    }

    if (showTranslateSheet) {
        ModalBottomSheet(onDismissRequest = { showTranslateSheet = false }, sheetState = rememberModalBottomSheetState()) {
            Column(Modifier.padding(bottom = 24.dp)) {
                Text(stringResource(R.string.translate_readme), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(16.dp))
                TranslateOption(stringResource(R.string.translate_off), translateTarget == null) { vm.setTranslateTarget(null); showTranslateSheet = false }
                TranslateOption(stringResource(R.string.translate_to_chinese), translateTarget == "zh") { vm.setTranslateTarget("zh"); showTranslateSheet = false }
                TranslateOption(stringResource(R.string.translate_to_english), translateTarget == "en") { vm.setTranslateTarget("en"); showTranslateSheet = false }
            }
        }
    }

    if (showOAuthSheet) {
        OAuthClientSheet(
            initialId = customClientId,
            initialSecret = customClientSecret,
            onDismiss = { showOAuthSheet = false },
            onSave = { id, secret -> vm.setCustomOAuthClient(id, secret); showOAuthSheet = false },
        )
    }

    if (showAbout) {
        ModalBottomSheet(onDismissRequest = { showAbout = false }, sheetState = rememberModalBottomSheetState()) {
            AboutContent()
        }
    }

    if (showNotifPollSheet) {
        ModalBottomSheet(onDismissRequest = { showNotifPollSheet = false }, sheetState = rememberModalBottomSheetState()) {
            Column(Modifier.padding(bottom = 24.dp)) {
                Text(stringResource(R.string.polling_cadence), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(16.dp))
                listOf(0, 15, 60, 1440).forEach { minutes ->
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            vm.setNotifPollMinutes(minutes)
                            showNotifPollSheet = false
                        }.padding(vertical = 8.dp).padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = notifPollMinutes == minutes, onClick = null)
                        Spacer(Modifier.width(12.dp))
                        Text(notificationCadenceLabel(minutes))
                    }
                }
            }
        }
    }

    if (showSignOutDialog) {
        AlertDialog(
            onDismissRequest = { showSignOutDialog = false },
            title = { Text(stringResource(R.string.sign_out_dialog_title)) },
            text = { Text(stringResource(R.string.sign_out_dialog_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showSignOutDialog = false
                    onSignOut() // Navigation handled by AppNavigation observing signedOut.
                }) { Text(stringResource(R.string.action_sign_out), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showSignOutDialog = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }

    // Manual update check results.
    val updateDownload by updateVm.download.collectAsState()
    when (val s = updateState) {
        is com.pockethub.ui.main.UpdateViewModel.State.UpdateAvailable ->
            com.pockethub.ui.main.UpdateDialog(
                info = s.info,
                downloadState = updateDownload,
                onDownload = { updateVm.startDownload(s.info) },
                onCancel = { updateVm.cancelDownload() },
                onInstall = { path -> updateVm.install(context, path) },
                onRetry = { updateVm.startDownload(s.info) },
                onIgnore = { updateVm.ignoreVersion(s.info.latestVersionName) },
                onLater = { updateVm.dismiss() },
            )
        is com.pockethub.ui.main.UpdateViewModel.State.UpToDate -> {
            LaunchedEffect(s) {
                snackbarHostState.showSnackbar(context.getString(R.string.update_uptodate))
                updateVm.dismiss()
            }
        }
        is com.pockethub.ui.main.UpdateViewModel.State.Error -> {
            LaunchedEffect(s) {
                snackbarHostState.showSnackbar(context.getString(R.string.update_check_failed))
                updateVm.dismiss()
            }
        }
        else -> Unit
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun OAuthClientSheet(
    initialId: String,
    initialSecret: String,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
) {
    var id by rememberSaveable { mutableStateOf(initialId) }
    var secret by rememberSaveable { mutableStateOf(initialSecret) }
    var showSecret by rememberSaveable { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .imePadding()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.custom_oauth_client_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                stringResource(R.string.custom_oauth_client_summary),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = id,
                onValueChange = { id = it },
                label = { Text(stringResource(R.string.client_id)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { Icon(Icons.Outlined.VpnKey, null, modifier = Modifier.size(18.dp)) },
            )
            OutlinedTextField(
                value = secret,
                onValueChange = { secret = it },
                label = { Text(stringResource(R.string.client_secret)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = if (showSecret) VisualTransformation.None else PasswordVisualTransformation(),
                leadingIcon = { Icon(Icons.Outlined.Lock, null, modifier = Modifier.size(18.dp)) },
                trailingIcon = {
                    TextButton(onClick = { showSecret = !showSecret }) {
                        Text(stringResource(if (showSecret) R.string.action_hide else R.string.action_show), style = MaterialTheme.typography.labelMedium)
                    }
                },
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onSave(id.trim(), secret.trim()) },
                    enabled = id.isNotBlank(),
                ) { Text(stringResource(R.string.action_save)) }
                OutlinedButton(onClick = { onSave("", "") }) { Text(stringResource(R.string.action_clear)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ThemeOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.height(0.dp))
        Text(label, modifier = Modifier.padding(start = 12.dp))
    }
}

@Composable
private fun styleLabel(style: AppStyle?): String = when (style) {
    // null = legacy / first-run. resolveStyle maps null + ThemeMode.Dark (the
    // default) to AppStyle.LinearDark, so the list-row supporting text mirrors
    // that instead of showing a now-retired "Default" label.
    null, AppStyle.LinearDark -> stringResource(R.string.style_linear_dark)
    AppStyle.PrimerLight -> stringResource(R.string.style_primer_light)
    AppStyle.Paper -> stringResource(R.string.style_paper)
    AppStyle.Neon -> stringResource(R.string.style_neon)
    AppStyle.Lavender -> stringResource(R.string.style_lavender)
    AppStyle.Forest -> stringResource(R.string.style_forest)
}

/** Visual style picker row — shows the style's palette as swatches plus a shape hint. */
@Composable
private fun StyleOption(style: AppStyle, selected: Boolean, onClick: () -> Unit) {
    val def = com.pockethub.ui.theme.styleDef(style)
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = onClick)
        // Palette swatches: background / surfaceVariant / primary / secondary / tertiary
        Row(Modifier.padding(start = 12.dp)) {
            val swatches = listOf(
                def.colors.background, def.colors.surfaceVariant,
                def.colors.primary, def.colors.secondary, def.colors.tertiary,
            )
            swatches.forEachIndexed { i, c ->
                Box(
                    Modifier
                        .padding(start = if (i == 0) 0.dp else 4.dp)
                        .size(20.dp)
                        .background(c, def.shapes.small)
                        .border(1.dp, def.colors.outline.copy(alpha = 0.4f), def.shapes.small)
                )
            }
        }
        Column(Modifier.padding(start = 16.dp)) {
            Text(styleLabel(style), style = MaterialTheme.typography.bodyLarge)
            Text(
                if (def.isDark) stringResource(R.string.theme_dark) else stringResource(R.string.theme_light),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LanguageOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.height(0.dp))
        Text(label, modifier = Modifier.padding(start = 12.dp))
    }
}

@Composable
private fun TranslateOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.height(0.dp))
        Text(label, modifier = Modifier.padding(start = 12.dp))
    }
}

@Composable
private fun AboutContent() {
    Column(Modifier.fillMaxWidth().padding(16.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.version_template, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(R.string.about_description), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        // Privacy & security notes moved here from the removed settings section.
        Text(stringResource(R.string.section_privacy_security), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text("• " + stringResource(R.string.token_storage_summary), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("• " + stringResource(R.string.analytics_telemetry_summary), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.open_source_licenses), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        OpenSourceLicensesList()
    }
}

@Composable
private fun OpenSourceLicensesList() {
    val libs = listOf(
        Triple("Kotlin", "JetBrains", "Apache License 2.0"),
        Triple("Jetpack Compose (Material 3)", "Google / AOSP", "Apache License 2.0"),
        Triple("Kotlinx Coroutines", "JetBrains", "Apache License 2.0"),
        Triple("Kotlinx Serialization", "JetBrains", "Apache License 2.0"),
        Triple("Hilt / Dagger", "Google", "Apache License 2.0"),
        Triple("Room", "Google / AOSP", "Apache License 2.0"),
        Triple("Retrofit", "Square, Inc.", "Apache License 2.0"),
        Triple("OkHttp", "Square, Inc.", "Apache License 2.0"),
        Triple("Coil", "coil-kt", "Apache License 2.0"),
        Triple("AndroidX Browser / Custom Tabs", "Google / AOSP", "Apache License 2.0"),
        Triple("androidx-datastore", "Google / AOSP", "Apache License 2.0"),
        Triple("androidx-navigation-compose", "Google / AOSP", "Apache License 2.0"),
        Triple("androidx-lifecycle-runtime-ktx", "Google / AOSP", "Apache License 2.0"),
    )

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        libs.forEach { (name, author, license) ->
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.by_author_license, author, license), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                HorizontalDivider(Modifier.padding(top = 6.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.pockethub_license),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ── helpers ────────────────────────────────────────────────────────────

/** Severe-issue reports always go to the developer's inbox. */
private const val DEVELOPER_EMAIL = "wochatchat@gmail.com"

/**
 * Build a well-formatted severe-issue report from the local ring buffer.
 * Returns null when there is nothing to report (caller shows a reminder).
 */
private fun buildIssueReportBody(events: List<com.pockethub.data.reporting.IssueEvent>): String? {
    if (events.isEmpty()) return null
    return buildString {
        appendLine("PocketHub 严重问题报告")
        appendLine("==================================")
        appendLine()
        appendLine("共 ${events.size} 条事件（最新在前）")
        appendLine()
        events.forEachIndexed { i, e ->
            appendLine("---- #${i + 1} [${e.kind}] ----")
            appendLine("时间:   ${e.isoTs}")
            appendLine("版本:   ${e.appVersionName} (${e.appVersionCode}) ${e.appVariant}")
            appendLine("设备:   ${e.deviceModel} · Android ${e.sdkInt}")
            e.threadName.takeIf { it.isNotBlank() }?.let { appendLine("线程:   $it") }
            e.subject.takeIf { it.isNotBlank() }?.let { appendLine("摘要:   $it") }
            e.stackTrace.takeIf { it.isNotBlank() }?.let {
                appendLine("堆栈:")
                // Cap each trace so a single huge crash can't flood the email body.
                val lines = it.lines().take(30)
                lines.forEach { l -> appendLine("  $l") }
                if (it.lines().size > 30) appendLine("  …(截断)")
            }
            if (e.extra.isNotEmpty()) {
                appendLine("附加信息:")
                e.extra.forEach { (k, v) -> appendLine("  $k: $v") }
            }
            appendLine()
        }
    }
}

/**
 * Open the system mail composer addressed to [DEVELOPER_EMAIL] with a
 * formatted report of [events]. The caller checks emptiness first and shows
 * a reminder instead — this function assumes non-empty input.
 */
private fun sendIssueReportByEmail(
    context: android.content.Context,
    events: List<com.pockethub.data.reporting.IssueEvent>,
) {
    val count = events.size
    val first = events.firstOrNull()
    val subject = if (count == 1 && first != null) {
        "[PocketHub] ${first.kind} — ${first.isoTs.take(10)} · v${first.appVersionName}"
    } else {
        "[PocketHub] 严重问题汇总 ×$count"
    }
    openChooser(context, DEVELOPER_EMAIL, subject, buildIssueReportBody(events).orEmpty())
}

private fun openChooser(context: android.content.Context, email: String, subject: String, body: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "message/rfc822"
        putExtra(Intent.EXTRA_EMAIL, arrayOf(email))
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, body)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching {
        val chooser = Intent.createChooser(intent, "选择邮件客户端发送").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }
}

@Composable
private fun localeLabel(locale: AppLocale): String = when (locale) {
    AppLocale.SYSTEM -> stringResource(R.string.locale_system)
    AppLocale.ENGLISH -> stringResource(R.string.locale_english)
    AppLocale.CHINESE -> stringResource(R.string.locale_chinese)
}

@Composable
private fun notificationCadenceLabel(minutes: Int): String = when (minutes) {
    0    -> stringResource(R.string.notification_cadence_manual)
    15   -> stringResource(R.string.notification_cadence_15m)
    60   -> stringResource(R.string.notification_cadence_1h)
    1440 -> stringResource(R.string.notification_cadence_1d)
    else -> stringResource(R.string.notification_cadence_min, minutes)
}

private fun openAppNotificationSettings(context: android.content.Context) {
    val intent = Intent().apply {
        when {
            android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O -> {
                action = android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS
                putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
            }
            else -> {
                action = android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                data = Uri.fromParts("package", context.packageName, null)
            }
        }
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(intent) }
}

private fun appCacheSize(cacheDir: File): Long {
    if (!cacheDir.exists()) return 0
    return cacheDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
}

/** Best-effort cache clear — must run on a background thread. */
private suspend fun clearCache(dir: File): Long {
    if (dir.exists()) dir.walkTopDown().forEach { runCatching { it.delete() } }
    return 0L
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1_073_741_824L -> "%.1f GB".format(bytes / 1_073_741_824.0)
    bytes >= 1_048_576L     -> "%.1f MB".format(bytes / 1_048_576.0)
    bytes >= 1024L          -> "%.1f KB".format(bytes / 1024.0)
    else                   -> "$bytes B"
}
