package com.nihaltp.volumelock.ui.screens

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nihaltp.volumelock.ui.viewmodel.VolumeLockViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VolumeLockScreen(
    viewModel: VolumeLockViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val enabled by viewModel.volumeLockEnabled.collectAsState()
    val volumes by viewModel.currentVolumes.collectAsState()
    val locked by viewModel.lockedVolumes.collectAsState()

    var showPermissionDialog by remember { mutableStateOf(false) }

    val handleToggle: (Boolean) -> Unit = { isChecked ->
        if (isChecked) {
            // Check permission first
            if (!viewModel.hasNotificationPolicyAccess()) {
                showPermissionDialog = true
            } else {
                viewModel.setVolumeLockEnabled(true)
            }
        } else {
            viewModel.setVolumeLockEnabled(false)
        }
    }

    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            title = { Text(text = "Permission Required") },
            text = { Text(text = "To lock notification and ring volumes, please grant \"Do Not Disturb access\" in the next screen.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showPermissionDialog = false
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                            context.startActivity(intent)
                        }
                    }
                ) {
                    Text("Open Settings")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "Volume Lock", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.background,
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                        )
                    )
                )
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Toggle Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "Volume Lock",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (enabled) {
                                "Active — volumes will be restored if changed while the screen is off."
                            } else {
                                "When enabled, the current volumes are remembered when the screen turns off."
                            },
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 16.sp
                        )
                    }

                    Switch(
                        checked = enabled,
                        onCheckedChange = handleToggle
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Current Volumes Display Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    Text(
                        text = "Current Volumes",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    VolumeRow(
                        icon = Icons.Default.MusicNote,
                        label = "Media",
                        currentVal = volumes.media,
                        maxVal = volumes.mediaMax,
                        lockedVal = locked?.media
                    )
                    VolumeRow(
                        icon = Icons.AutoMirrored.Filled.VolumeUp,
                        label = "Ring",
                        currentVal = volumes.ring,
                        maxVal = volumes.ringMax,
                        lockedVal = locked?.ring
                    )
                    VolumeRow(
                        icon = Icons.Default.Notifications,
                        label = "Notification",
                        currentVal = volumes.notification,
                        maxVal = volumes.notificationMax,
                        lockedVal = locked?.notification
                    )
                    VolumeRow(
                        icon = Icons.Default.Alarm,
                        label = "Alarm",
                        currentVal = volumes.alarm,
                        maxVal = volumes.alarmMax,
                        lockedVal = locked?.alarm
                    )
                }
            }

            // Locked indicator alert
            if (enabled && locked != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Locked State",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Locked volumes: Media ${locked!!.media}, Ring ${locked!!.ring}, Notification ${locked!!.notification}, Alarm ${locked!!.alarm}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Info Card
            InfoSection(
                title = "How it works",
                bullets = listOf(
                    "Enable Volume Lock to start the service.",
                    "When the screen turns off, the current volume levels are recorded.",
                    "If any volume is changed while the screen remains off, it is automatically restored to the recorded levels when the screen turns back on.",
                    "Disabling Volume Lock stops the background monitoring service entirely."
                )
            )
        }
    }
}

@Composable
fun VolumeRow(
    icon: ImageVector,
    label: String,
    currentVal: Int,
    maxVal: Int,
    lockedVal: Int?
) {
    val fraction = if (maxVal > 0) currentVal.toFloat() / maxVal.toFloat() else 0f
    val isOverridden = lockedVal != null && lockedVal != currentVal

    Row(
        modifier = Modifier.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = label,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.width(90.dp)
        )

        // Progress Bar
        LinearProgressIndicator(
            progress = { fraction.coerceIn(0f, 1f) },
            modifier = Modifier
                .weight(1f)
                .height(6.dp),
            strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
        )

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = "$currentVal",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(24.dp)
        )

        if (isOverridden) {
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = "Locked at $lockedVal",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

@Composable
fun InfoSection(
    title: String,
    bullets: List<String>
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            bullets.forEach { bullet ->
                Row(
                    modifier = Modifier.padding(vertical = 3.dp)
                ) {
                    Text(text = "• ", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        text = bullet,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 16.sp
                    )
                }
            }
        }
    }
}
