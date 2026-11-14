package com.example.parent

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.navigation.ParentTab

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParentShellScreen(
    viewModel: ParentViewModel,
    isOnline: Boolean,
    languageCode: String,
    onLanguageSelected: (String) -> Unit,
    onOpenPrivacyData: () -> Unit,
    onSwitchToChildRole: () -> Unit,
    onSignOut: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val pairingState by viewModel.pairingState.collectAsStateWithLifecycle()
    val actionMessage by viewModel.actionMessage.collectAsStateWithLifecycle()
    var activeTab by remember { mutableStateOf(ParentTab.DASHBOARD) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(actionMessage) {
        val msg = actionMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.clearActionMessage()
    }

    val unreadCount = state.alerts.count { !it.isRead }

    BackHandler(enabled = activeTab != ParentTab.DASHBOARD) {
        activeTab = ParentTab.DASHBOARD
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 700.dp
        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.app_name)) }
                )
            },
            bottomBar = {
                if (!useRail) {
                    NavigationBar {
                        ParentTab.entries.forEach { tab ->
                            val selected = tab == activeTab
                            NavigationBarItem(
                                selected = selected,
                                onClick = { activeTab = tab },
                                icon = { TabIcon(tab, selected, unreadCount) },
                                label = { Text(stringResource(tabTitleRes(tab)), maxLines = 1) },
                                modifier = Modifier.testTag("nav_tab_${tab.name.lowercase()}")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail) {
                    NavigationRail {
                        ParentTab.entries.forEach { tab ->
                            val selected = tab == activeTab
                            NavigationRailItem(
                                selected = selected,
                                onClick = { activeTab = tab },
                                icon = { TabIcon(tab, selected, unreadCount) },
                                label = { Text(stringResource(tabTitleRes(tab))) }
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Box(modifier = Modifier.widthIn(max = 760.dp).fillMaxSize()) {
                        when (activeTab) {
                            ParentTab.DASHBOARD -> ParentDashboardScreen(
                                state = state,
                                isOnline = isOnline,
                                onSelectDevice = viewModel::selectDevice,
                                onNavigateToDevices = { activeTab = ParentTab.DEVICES },
                                onNavigateToAlerts = { activeTab = ParentTab.ALERTS }
                            )
                            ParentTab.DEVICES -> ParentDevicesScreen(
                                state = state,
                                pairingState = pairingState,
                                onGeneratePairingCode = viewModel::generatePairingCode,
                                onClearPairingState = viewModel::clearPairingState,
                                onToggleMonitoring = viewModel::toggleMonitoring,
                                onSelectDevice = viewModel::selectDevice
                            )
                            ParentTab.LIMITS -> ParentLimitsScreen(
                                state = state,
                                onUpsertLimit = viewModel::upsertLimit,
                                onDeleteLimit = viewModel::deleteLimit
                            )
                            ParentTab.ALERTS -> ParentAlertsScreen(
                                state = state,
                                onMarkRead = viewModel::markAlertRead
                            )
                            ParentTab.SETTINGS -> ParentSettingsScreen(
                                state = state,
                                languageCode = languageCode,
                                onLanguageSelected = onLanguageSelected,
                                onTogglePrivacyMode = viewModel::setPrivacyMode,
                                onOpenPrivacyData = onOpenPrivacyData,
                                onSwitchToChildRole = onSwitchToChildRole,
                                onSignOut = onSignOut
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TabIcon(tab: ParentTab, selected: Boolean, unreadAlerts: Int) {
    val icon = when (tab) {
        ParentTab.DASHBOARD -> if (selected) Icons.Filled.Dashboard else Icons.Outlined.Dashboard
        ParentTab.DEVICES -> if (selected) Icons.Filled.Devices else Icons.Outlined.Devices
        ParentTab.LIMITS -> if (selected) Icons.Filled.Timer else Icons.Outlined.Timer
        ParentTab.ALERTS -> if (selected) Icons.Filled.Notifications else Icons.Outlined.Notifications
        ParentTab.SETTINGS -> if (selected) Icons.Filled.Settings else Icons.Outlined.Settings
    }
    if (tab == ParentTab.ALERTS && unreadAlerts > 0) {
        BadgedBox(badge = { Badge { Text(unreadAlerts.toString()) } }) {
            Icon(icon, contentDescription = stringResource(tabTitleRes(tab)))
        }
    } else {
        Icon(icon, contentDescription = stringResource(tabTitleRes(tab)))
    }
}

private fun tabTitleRes(tab: ParentTab): Int = when (tab) {
    ParentTab.DASHBOARD -> R.string.nav_dashboard
    ParentTab.DEVICES -> R.string.nav_devices
    ParentTab.LIMITS -> R.string.nav_limits
    ParentTab.ALERTS -> R.string.nav_alerts
    ParentTab.SETTINGS -> R.string.nav_settings
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyDataScreen(
    viewModel: ParentViewModel,
    onBack: () -> Unit,
    onAccountDeleted: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var confirmDeleteDevice by remember { mutableStateOf(false) }
    var confirmDeleteAccount by remember { mutableStateOf(false) }
    val selected = state.selectedDevice

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.privacy_data_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("privacy_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cancel_action))
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                PrivacySectionCard(
                    title = stringResource(R.string.privacy_what_title),
                    body = stringResource(R.string.privacy_what_body)
                )
            }
            item {
                PrivacySectionCard(
                    title = stringResource(R.string.privacy_why_title),
                    body = stringResource(R.string.privacy_why_body)
                )
            }
            item {
                PrivacySectionCard(
                    title = stringResource(R.string.privacy_where_title),
                    body = stringResource(R.string.privacy_where_body)
                )
            }
            if (selected != null) {
                item {
                    OutlinedButton(
                        onClick = { confirmDeleteDevice = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("delete_child_device_data_button"),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Filled.DeleteForever, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.delete_device_data_action))
                    }
                }
            }
            item {
                Button(
                    onClick = { confirmDeleteAccount = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("delete_account_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Filled.DeleteForever, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.delete_account_action))
                }
            }
        }
    }

    if (confirmDeleteDevice && selected != null) {
        AlertDialog(
            onDismissRequest = { confirmDeleteDevice = false },
            title = { Text(stringResource(R.string.confirm_delete_device_title)) },
            text = { Text(stringResource(R.string.confirm_delete_device_body, selected.childName)) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteChildDeviceData(selected.id)
                        confirmDeleteDevice = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(R.string.confirm_action)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteDevice = false }) { Text(stringResource(R.string.cancel_action)) }
            }
        )
    }

    if (confirmDeleteAccount) {
        AlertDialog(
            onDismissRequest = { confirmDeleteAccount = false },
            title = { Text(stringResource(R.string.confirm_delete_account_title)) },
            text = { Text(stringResource(R.string.confirm_delete_account_body)) },
            confirmButton = {
                Button(
                    onClick = {
                        confirmDeleteAccount = false
                        viewModel.deleteAccountAndData(onAccountDeleted)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(R.string.confirm_action)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteAccount = false }) { Text(stringResource(R.string.cancel_action)) }
            }
        )
    }
}

@Composable
private fun PrivacySectionCard(title: String, body: String) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
