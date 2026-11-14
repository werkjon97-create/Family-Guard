package com.example.parent

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.domain.AppUsageEntry
import com.example.core.domain.ChildDevice
import com.example.core.utils.Formatters

@Composable
fun ParentDashboardScreen(
    state: ParentDashboardUiState,
    isOnline: Boolean,
    onSelectDevice: (String) -> Unit,
    onNavigateToDevices: () -> Unit,
    onNavigateToAlerts: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (state.isLoading) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val unreadAlertsCount = state.alerts.count { !it.isRead }
    val selected = state.selectedDevice

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            val greetingName = state.profile?.displayName?.ifBlank { null } ?: "Parent"
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.welcome_greeting, greetingName),
                    style = MaterialTheme.typography.headlineMedium
                )
                if (!isOnline) {
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = stringResource(R.string.error_no_internet),
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
                if (state.privacyMode) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.VisibilityOff,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = stringResource(R.string.privacy_mode_active_banner),
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }

        if (state.devices.isEmpty()) {
            item {
                EmptyDevicesCard(onAddDeviceClick = onNavigateToDevices)
            }
            return@LazyColumn
        }

        if (state.devices.size > 1) {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.devices, key = { it.id }) { device ->
                        val label = if (state.privacyMode) {
                            stringResource(R.string.hidden_child_label)
                        } else {
                            device.childName
                        }
                        FilterChip(
                            selected = device.id == selected?.id,
                            onClick = { onSelectDevice(device.id) },
                            label = { Text(label) },
                            modifier = Modifier.testTag("device_chip_${device.id}")
                        )
                    }
                }
            }
        }

        if (selected != null) {
            item {
                HeroScreenTimeCard(
                    device = selected,
                    privacyMode = state.privacyMode
                )
            }

            item {
                MetricsGridRow(
                    device = selected,
                    unreadAlerts = unreadAlertsCount,
                    onAlertsClick = onNavigateToAlerts
                )
            }

            item {
                Text(
                    text = stringResource(R.string.most_used_title),
                    style = MaterialTheme.typography.titleLarge
                )
            }

            if (state.appUsage.isEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = stringResource(R.string.no_usage_yet),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(20.dp)
                        )
                    }
                }
            } else {
                item {
                    UsageBarChartCard(
                        apps = state.appUsage.take(5),
                        privacyMode = state.privacyMode
                    )
                }

                itemsIndexed(state.appUsage.take(8), key = { _, item -> item.packageName }) { index, app ->
                    AppUsageRowCard(
                        app = app,
                        totalMs = selected.screenTimeMs.coerceAtLeast(1L),
                        maskedName = if (state.privacyMode) stringResource(R.string.hidden_app_label, index + 1) else null
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroScreenTimeCard(
    device: ChildDevice,
    privacyMode: Boolean
) {
    val childLabel = if (privacyMode) stringResource(R.string.hidden_child_label) else device.childName
    val deviceLabel = if (privacyMode) stringResource(R.string.hidden_device_label) else device.deviceName
    val gradient = Brush.linearGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.secondary
        )
    )
    Card(
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(gradient)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = childLabel,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Text(
                        text = "$deviceLabel • ${device.androidVersion}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                    )
                }
                Surface(
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.18f),
                    shape = CircleShape
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (device.isOnline) Color(0xFF4ADE80) else Color(0xFFFBBF24))
                        )
                        Text(
                            text = if (device.isOnline) stringResource(R.string.device_status_online)
                            else stringResource(R.string.device_status_offline),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = stringResource(R.string.screen_time_title),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                )
                Text(
                    text = Formatters.formatDurationHms(device.screenTimeMs),
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.Bold
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Shield,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = if (device.monitoringActive) stringResource(R.string.monitoring_active)
                        else stringResource(R.string.monitoring_paused),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
                Text(
                    text = "${stringResource(R.string.last_sync_label)}: ${Formatters.formatTimeAgo(device.lastSeenMs)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f)
                )
            }
        }
    }
}

@Composable
private fun MetricsGridRow(
    device: ChildDevice,
    unreadAlerts: Int,
    onAlertsClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StatMiniCard(
            title = stringResource(R.string.apps_used_label),
            value = device.appCount.toString(),
            icon = Icons.Filled.Apps,
            modifier = Modifier.weight(1f)
        )
        StatMiniCard(
            title = stringResource(R.string.alerts_label),
            value = unreadAlerts.toString(),
            icon = Icons.Filled.NotificationsActive,
            highlight = unreadAlerts > 0,
            onClick = onAlertsClick,
            modifier = Modifier.weight(1f)
        )
        StatMiniCard(
            title = stringResource(R.string.last_sync_label),
            value = device.syncStatus.replaceFirstChar { it.uppercase() },
            icon = Icons.Filled.Sync,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun StatMiniCard(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    highlight: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    Card(
        onClick = { onClick?.invoke() },
        enabled = onClick != null,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = if (highlight) MaterialTheme.colorScheme.tertiaryContainer
            else MaterialTheme.colorScheme.surface
        ),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = if (highlight) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun UsageBarChartCard(
    apps: List<AppUsageEntry>,
    privacyMode: Boolean
) {
    val maxUsage = apps.maxOfOrNull { it.usageTimeMs }?.coerceAtLeast(1L) ?: 1L
    val barColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.surfaceVariant

    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.todays_usage_title),
                style = MaterialTheme.typography.titleMedium
            )
            apps.forEachIndexed { index, app ->
                val label = if (privacyMode) stringResource(R.string.hidden_app_label, index + 1) else app.appName
                val fraction = (app.usageTimeMs.toFloat() / maxUsage.toFloat()).coerceIn(0.04f, 1f)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(label, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            Formatters.formatMinutesCompact(app.usageTimeMs / 60_000L),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                    ) {
                        drawRoundRect(
                            color = trackColor,
                            cornerRadius = CornerRadius(12f, 12f),
                            size = size
                        )
                        drawRoundRect(
                            color = barColor,
                            topLeft = Offset.Zero,
                            size = Size(size.width * fraction, size.height),
                            cornerRadius = CornerRadius(12f, 12f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppUsageRowCard(
    app: AppUsageEntry,
    totalMs: Long,
    maskedName: String?
) {
    val ratio = (app.usageTimeMs.toFloat() / totalMs.toFloat()).coerceIn(0f, 1f)
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = maskedName ?: app.appName,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = stringResource(R.string.launches_count_format, app.launchCount.toInt()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = Formatters.formatDurationHms(app.usageTimeMs),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            LinearProgressIndicator(
                progress = { ratio },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape)
            )
        }
    }
}

@Composable
private fun EmptyDevicesCard(onAddDeviceClick: () -> Unit) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Devices,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp)
            )
            Text(
                text = stringResource(R.string.no_devices_title),
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = stringResource(R.string.no_devices_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = onAddDeviceClick,
                modifier = Modifier
                    .height(48.dp)
                    .testTag("empty_add_child_button")
            ) {
                Text(stringResource(R.string.add_child_device))
            }
        }
    }
}
