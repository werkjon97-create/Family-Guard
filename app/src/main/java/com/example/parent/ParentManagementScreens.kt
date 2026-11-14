package com.example.parent

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.data.remote.GeneratedPairingResult
import com.example.core.domain.AppLimit
import com.example.core.domain.AppResult
import com.example.core.domain.ChildDevice
import com.example.core.domain.DeviceAlert
import com.example.core.utils.Formatters

@Composable
fun ParentDevicesScreen(
    state: ParentDashboardUiState,
    pairingState: AppResult<GeneratedPairingResult>?,
    onGeneratePairingCode: (String) -> Unit,
    onClearPairingState: () -> Unit,
    onToggleMonitoring: (String, Boolean) -> Unit,
    onSelectDevice: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showPairingDialog by remember { mutableStateOf(false) }
    var childLabelInput by remember { mutableStateOf("") }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.my_children_title),
                    style = MaterialTheme.typography.headlineSmall
                )
                Button(
                    onClick = { showPairingDialog = true },
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("add_child_device_button")
                ) {
                    Icon(Icons.Filled.QrCode2, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.add_child_device))
                }
            }
        }

        if (state.devices.isEmpty()) {
            item {
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Filled.Devices, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(44.dp))
                        Text(stringResource(R.string.no_devices_title), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.no_devices_desc), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        } else {
            items(state.devices, key = { it.id }) { device ->
                ChildDeviceCard(
                    device = device,
                    privacyMode = state.privacyMode,
                    isSelected = device.id == state.selectedDevice?.id,
                    onClick = { onSelectDevice(device.id) },
                    onToggleMonitoring = { enabled -> onToggleMonitoring(device.id, enabled) }
                )
            }
        }
    }

    if (showPairingDialog) {
        AlertDialog(
            onDismissRequest = {
                showPairingDialog = false
                onClearPairingState()
            },
            title = { Text(stringResource(R.string.generate_pairing_code)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    when (pairingState) {
                        is AppResult.Loading -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center
                            ) { CircularProgressIndicator() }
                        }
                        is AppResult.Success -> {
                            val code = pairingState.data.rawCode
                            Text(stringResource(R.string.pairing_code_label), style = MaterialTheme.typography.labelLarge)
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = MaterialTheme.shapes.medium,
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
                                        text = code,
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    IconButton(
                                        onClick = {
                                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            cm.setPrimaryClip(ClipData.newPlainText("PairingCode", code))
                                            Toast.makeText(context, context.getString(R.string.pairing_copied_toast), Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.testTag("copy_pairing_code_button")
                                    ) {
                                        Icon(Icons.Filled.ContentCopy, contentDescription = stringResource(R.string.pairing_copy_code))
                                    }
                                }
                            }
                            Text(
                                text = stringResource(R.string.pairing_code_expiry),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        is AppResult.Error -> {
                            Text(pairingState.message, color = MaterialTheme.colorScheme.error)
                        }
                        null, AppResult.Empty -> {
                            OutlinedTextField(
                                value = childLabelInput,
                                onValueChange = { childLabelInput = it },
                                label = { Text(stringResource(R.string.pairing_child_name_hint)) },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("pairing_child_label_input")
                            )
                        }
                    }
                }
            },
            confirmButton = {
                if (pairingState is AppResult.Success) {
                    TextButton(
                        onClick = {
                            showPairingDialog = false
                            childLabelInput = ""
                            onClearPairingState()
                        }
                    ) { Text(stringResource(R.string.save_action)) }
                } else {
                    Button(
                        onClick = { onGeneratePairingCode(childLabelInput) },
                        enabled = pairingState !is AppResult.Loading,
                        modifier = Modifier.testTag("confirm_generate_code_button")
                    ) { Text(stringResource(R.string.generate_pairing_code)) }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showPairingDialog = false
                        onClearPairingState()
                    }
                ) { Text(stringResource(R.string.cancel_action)) }
            }
        )
    }
}

@Composable
private fun ChildDeviceCard(
    device: ChildDevice,
    privacyMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onToggleMonitoring: (Boolean) -> Unit
) {
    val childName = if (privacyMode) stringResource(R.string.hidden_child_label) else device.childName
    val deviceName = if (privacyMode) stringResource(R.string.hidden_device_label) else device.deviceName

    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
            else MaterialTheme.colorScheme.surface
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(childName, style = MaterialTheme.typography.titleLarge)
                    Text(
                        "$deviceName • ${stringResource(R.string.android_version_prefix, device.androidVersion)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = Formatters.formatDurationHms(device.screenTimeMs),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${if (device.isOnline) stringResource(R.string.device_status_online) else stringResource(R.string.device_status_offline)} • ${stringResource(R.string.last_seen_prefix, Formatters.formatTimeAgo(device.lastSeenMs))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.monitoring_switch_label), style = MaterialTheme.typography.bodyMedium)
                Switch(
                    checked = device.monitoringActive,
                    onCheckedChange = onToggleMonitoring,
                    modifier = Modifier.testTag("monitoring_switch_${device.id}")
                )
            }
        }
    }
}

@Composable
fun ParentLimitsScreen(
    state: ParentDashboardUiState,
    onUpsertLimit: (deviceId: String, childUid: String, pkg: String, name: String, minutes: Long, enabled: Boolean) -> Unit,
    onDeleteLimit: (deviceId: String, pkg: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val selected = state.selectedDevice
    var showAddDialog by remember { mutableStateOf(false) }
    var pkgInput by remember { mutableStateOf("com.google.android.youtube") }
    var nameInput by remember { mutableStateOf("YouTube") }
    var minutesInput by remember { mutableStateOf("30") }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.app_limits_title), style = MaterialTheme.typography.headlineSmall)
                    if (selected != null) {
                        Button(
                            onClick = { showAddDialog = true },
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("add_limit_button")
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.add_limit_button))
                        }
                    }
                }
                Text(
                    text = stringResource(R.string.app_limits_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (selected == null) {
            item {
                Text(
                    stringResource(R.string.no_devices_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else if (state.limits.isEmpty()) {
            item {
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Filled.Timer, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
                        Text(stringResource(R.string.no_limits_title), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.no_limits_desc), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        } else {
            items(state.limits, key = { it.packageName }) { limit ->
                AppLimitCard(
                    limit = limit,
                    privacyMode = state.privacyMode,
                    onToggle = { enabled ->
                        onUpsertLimit(selected.id, selected.childUid, limit.packageName, limit.appName, limit.dailyLimitMinutes, enabled)
                    },
                    onDelete = { onDeleteLimit(selected.id, limit.packageName) }
                )
            }
        }
    }

    if (showAddDialog && selected != null) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text(stringResource(R.string.add_limit_button)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        label = { Text(stringResource(R.string.limit_app_name_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = pkgInput,
                        onValueChange = { pkgInput = it },
                        label = { Text(stringResource(R.string.limit_package_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = minutesInput,
                        onValueChange = { minutesInput = it.filter { ch -> ch.isDigit() } },
                        label = { Text(stringResource(R.string.limit_minutes_label)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val mins = minutesInput.toLongOrNull()?.coerceIn(1L, 1440L) ?: 30L
                        if (pkgInput.isNotBlank() && nameInput.isNotBlank()) {
                            onUpsertLimit(selected.id, selected.childUid, pkgInput.trim(), nameInput.trim(), mins, true)
                            showAddDialog = false
                        }
                    },
                    modifier = Modifier.testTag("save_limit_button")
                ) { Text(stringResource(R.string.save_action)) }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text(stringResource(R.string.cancel_action)) }
            }
        )
    }
}

@Composable
private fun AppLimitCard(
    limit: AppLimit,
    privacyMode: Boolean,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
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
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = if (privacyMode) stringResource(R.string.hidden_app_label, 1) else limit.appName,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = stringResource(R.string.limit_minutes_format, limit.dailyLimitMinutes.toInt()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = limit.isEnabled, onCheckedChange = onToggle)
                IconButton(onClick = onDelete, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.delete_action), tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
fun ParentAlertsScreen(
    state: ParentDashboardUiState,
    onMarkRead: (deviceId: String, alertId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val selected = state.selectedDevice
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(stringResource(R.string.alerts_title), style = MaterialTheme.typography.headlineSmall)
        }
        if (state.alerts.isEmpty()) {
            item {
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Filled.NotificationsNone, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(42.dp))
                        Text(stringResource(R.string.no_alerts_title), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.no_alerts_desc), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        } else {
            items(state.alerts, key = { it.id }) { alert ->
                AlertItemCard(
                    alert = alert,
                    onMarkRead = {
                        if (selected != null) onMarkRead(selected.id, alert.id)
                    }
                )
            }
        }
    }
}

@Composable
private fun AlertItemCard(alert: DeviceAlert, onMarkRead: () -> Unit) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = if (alert.isRead) MaterialTheme.colorScheme.surface
            else MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = null,
                tint = if (alert.isRead) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.tertiary
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(alert.title, style = MaterialTheme.typography.titleMedium)
                Text(alert.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!alert.isRead) {
                IconButton(onClick = onMarkRead, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Filled.DoneAll, contentDescription = stringResource(R.string.mark_read_action))
                }
            }
        }
    }
}

@Composable
fun ParentSettingsScreen(
    state: ParentDashboardUiState,
    languageCode: String,
    onLanguageSelected: (String) -> Unit,
    onTogglePrivacyMode: (Boolean) -> Unit,
    onOpenPrivacyData: () -> Unit,
    onSwitchToChildRole: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineSmall)
        }

        item {
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(state.profile?.displayName.orEmpty().ifBlank { "Parent" }, style = MaterialTheme.typography.titleLarge)
                    Text(state.profile?.email.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        item {
            Card(
                shape = MaterialTheme.shapes.large,
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
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(R.string.privacy_mode_title), style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(R.string.privacy_mode_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Switch(
                        checked = state.privacyMode,
                        onCheckedChange = onTogglePrivacyMode,
                        modifier = Modifier.testTag("privacy_mode_switch")
                    )
                }
            }
        }

        item {
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.language_title), style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = languageCode.isEmpty(),
                            onClick = { onLanguageSelected("") },
                            label = { Text(stringResource(R.string.language_system)) }
                        )
                        FilterChip(
                            selected = languageCode == "en",
                            onClick = { onLanguageSelected("en") },
                            label = { Text(stringResource(R.string.language_english)) }
                        )
                        FilterChip(
                            selected = languageCode == "ar",
                            onClick = { onLanguageSelected("ar") },
                            label = { Text(stringResource(R.string.language_arabic)) }
                        )
                    }
                }
            }
        }

        item {
            OutlinedButton(
                onClick = onOpenPrivacyData,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("open_privacy_data_button"),
                shape = MaterialTheme.shapes.medium
            ) {
                Icon(Icons.Filled.PrivacyTip, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.privacy_data_title))
            }
        }

        item {
            OutlinedButton(
                onClick = onSwitchToChildRole,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("switch_to_child_button"),
                shape = MaterialTheme.shapes.medium
            ) {
                Icon(Icons.Filled.SwapHoriz, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.switch_role_action))
            }
        }

        item {
            Button(
                onClick = onSignOut,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("logout_button"),
                shape = MaterialTheme.shapes.medium
            ) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.logout_action))
            }
        }
    }
}
