package com.example.child

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.domain.AppErrorCode
import com.example.core.domain.AppResult
import com.example.core.ui.LanguageQuickToggle
import com.example.core.utils.Formatters
import com.example.core.utils.UsageAccessHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChildShellScreen(
    viewModel: ChildViewModel,
    languageCode: String,
    onLanguageSelected: (String) -> Unit,
    onSwitchToParentRole: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val pairingResult by viewModel.pairingResult.collectAsStateWithLifecycle()

    var setupStarted by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.refreshUsageAccess(context)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshUsageAccess(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    LanguageQuickToggle(languageCode, onLanguageSelected)
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Box(modifier = Modifier.widthIn(max = 620.dp).fillMaxSize()) {
                when {
                    !state.isLinked && !setupStarted -> {
                        ChildWelcomeStep(
                            onStartSetup = { setupStarted = true },
                            onSwitchToParentRole = onSwitchToParentRole
                        )
                    }
                    !state.isLinked && setupStarted -> {
                        BackHandler {
                            setupStarted = false
                            viewModel.clearPairingResult()
                        }
                        ChildEnterCodeStep(
                            pairingResult = pairingResult,
                            onConnect = { code, name ->
                                viewModel.connectWithPairingCode(context, code, name)
                            },
                            onBack = {
                                setupStarted = false
                                viewModel.clearPairingResult()
                            }
                        )
                    }
                    else -> {
                        ChildConnectedScreen(
                            state = state,
                            onOpenUsageSettings = {
                                context.startActivity(UsageAccessHelper.createUsageAccessSettingsIntent())
                            },
                            onSyncNow = { viewModel.syncNow(context) },
                            onResetLocalLink = { viewModel.resetLocalLink(context) },
                            onSwitchToParentRole = onSwitchToParentRole
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChildWelcomeStep(
    onStartSetup: () -> Unit,
    onSwitchToParentRole: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Filled.Shield,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(68.dp)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.child_welcome_title),
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.child_welcome_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onStartSetup,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .testTag("child_start_setup_button")
        ) {
            Text(stringResource(R.string.child_start_setup))
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = onSwitchToParentRole,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("child_switch_to_parent_button")
        ) {
            Icon(Icons.Filled.SwapHoriz, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.switch_to_parent_action))
        }
    }
}

@Composable
private fun ChildEnterCodeStep(
    pairingResult: AppResult<*>?,
    onConnect: (code: String, childName: String) -> Unit,
    onBack: () -> Unit
) {
    var codeInput by remember { mutableStateOf("") }
    var nameInput by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.child_enter_code_title),
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = codeInput,
            onValueChange = { codeInput = it.uppercase() },
            label = { Text(stringResource(R.string.child_code_hint)) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("child_pairing_code_input")
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = nameInput,
            onValueChange = { nameInput = it },
            label = { Text(stringResource(R.string.child_name_hint)) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("child_name_input")
        )

        if (pairingResult is AppResult.Error) {
            Spacer(Modifier.height(12.dp))
            val errorText = when (pairingResult.code) {
                AppErrorCode.INVALID_PAIRING_CODE -> stringResource(R.string.error_invalid_code)
                AppErrorCode.EXPIRED_PAIRING_CODE -> stringResource(R.string.error_expired_code)
                AppErrorCode.NO_INTERNET -> stringResource(R.string.error_no_internet)
                else -> pairingResult.message
            }
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = errorText,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        Button(
            onClick = { onConnect(codeInput, nameInput) },
            enabled = pairingResult !is AppResult.Loading && codeInput.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .testTag("child_connect_button")
        ) {
            if (pairingResult is AppResult.Loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
            } else {
                Icon(Icons.Filled.Link, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.child_connect_button))
            }
        }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(
            onClick = onBack,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            Text(stringResource(R.string.cancel_action))
        }
    }
}

@Composable
private fun ChildConnectedScreen(
    state: ChildUiState,
    onOpenUsageSettings: () -> Unit,
    onSyncNow: () -> Unit,
    onResetLocalLink: () -> Unit,
    onSwitchToParentRole: () -> Unit
) {
    val context = LocalContext.current
    var hasNotificationPermission by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        )
    }
    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotificationPermission = granted
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Connected status + Mandatory Transparency Card
        item {
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = stringResource(R.string.child_connected_badge),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Security,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = stringResource(R.string.child_transparency_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Text(
                        text = stringResource(R.string.child_transparency_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        // Official Usage Access Permission Card
        item {
            if (!state.usageAccessGranted) {
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                            Text(
                                text = stringResource(R.string.usage_access_required_title),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                        Text(
                            text = stringResource(R.string.usage_access_required_body),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Button(
                            onClick = onOpenUsageSettings,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("open_usage_access_settings_button")
                        ) {
                            Icon(Icons.Filled.Settings, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.open_android_settings))
                        }
                    }
                }
            } else {
                Card(
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.usage_access_granted_badge),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Button(
                            onClick = onSyncNow,
                            enabled = !state.isSyncing,
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("child_sync_now_button")
                        ) {
                            if (state.isSyncing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.syncing_label))
                            } else {
                                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.sync_now_button))
                            }
                        }
                    }
                }
            }
        }

        // Runtime POST_NOTIFICATIONS prompt on Android 13+
        if (!hasNotificationPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            item {
                Card(
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Filled.NotificationsActive, contentDescription = null)
                            Text(stringResource(R.string.enable_notifications_banner), style = MaterialTheme.typography.bodyMedium)
                        }
                        Button(
                            onClick = { notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                            modifier = Modifier.height(48.dp)
                        ) {
                            Text(stringResource(R.string.enable_notifications_button))
                        }
                    }
                }
            }
        }

        if (state.syncError != null) {
            item {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = state.syncError.orEmpty(),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }

        // Active Daily Limits from Parent
        if (state.localLimits.isNotEmpty()) {
            item {
                Text(stringResource(R.string.child_limits_section), style = MaterialTheme.typography.titleLarge)
            }
            items(state.localLimits.filter { it.isEnabled }, key = { it.packageName }) { limit ->
                Card(
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(limit.appName, style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = stringResource(R.string.limit_minutes_format, limit.dailyLimitMinutes),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        // Local Usage Preview (Room cache)
        item {
            Text(stringResource(R.string.child_local_usage_section), style = MaterialTheme.typography.titleLarge)
        }
        if (state.localApps.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.no_usage_yet),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(state.localApps.take(10), key = { it.packageName }) { app ->
                Card(
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(app.appName, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = Formatters.formatDurationHms(app.usageTimeMs),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        // Switch / Reset Actions
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = onSwitchToParentRole,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("connected_switch_to_parent_button")
                ) {
                    Icon(Icons.Filled.SwapHoriz, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.switch_to_parent_action))
                }
                OutlinedButton(
                    onClick = onResetLocalLink,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("reset_local_pairing_button")
                ) {
                    Text(stringResource(R.string.child_unlink_local))
                }
            }
        }
    }
}
