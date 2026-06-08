package com.nihaltp.volumelock.ui.screens

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nihaltp.volumelock.ui.viewmodel.AppVolumeEntry
import com.nihaltp.volumelock.ui.viewmodel.VolumeLockViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppVolumeLockScreen(
    viewModel: VolumeLockViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val enabled by viewModel.appVolumeLockEnabled.collectAsState()
    val apps by viewModel.installedApps.collectAsState()
    val isLoading by viewModel.isLoadingApps.collectAsState()
    val accessibilityGranted by viewModel.accessibilityGranted.collectAsState()

    var search by remember { mutableStateOf("") }
    var showPermissionDialog by remember { mutableStateOf(false) }

    // Check accessibility on launch and resume
    LaunchedEffect(Unit) {
        viewModel.checkAccessibilityPermission()
        viewModel.loadInstalledApps()
    }

    val handleToggle: (Boolean) -> Unit = { isChecked ->
        if (isChecked && !accessibilityGranted) {
            showPermissionDialog = true
        } else {
            viewModel.setAppVolumeLockEnabled(isChecked)
        }
    }

    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            title = { Text(text = "Accessibility Permission Required") },
            text = { Text(text = "App Volume Lock uses the Accessibility Service to detect which app is in the foreground.\n\nPlease enable \"Volume Lock\" in Accessibility Settings → Installed Services.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showPermissionDialog = false
                        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                        context.startActivity(intent)
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

    // Filter and sort apps
    val filteredApps = remember(apps, search) {
        apps.filter {
            search.isEmpty() ||
                it.appName.contains(search, ignoreCase = true) ||
                it.packageName.contains(search, ignoreCase = true)
        }.sortedWith { a, b ->
            if (a.isTracked != b.isTracked) {
                if (a.isTracked) -1 else 1
            } else {
                a.appName.compareTo(b.appName, ignoreCase = true)
            }
        }
    }

    val allVisibleSelected = filteredApps.isNotEmpty() && filteredApps.all { it.isTracked }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "App Volume Lock", fontWeight = FontWeight.Bold) },
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
        ) {
            // Header configuration section
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                // Toggle card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "App Volume Lock",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (enabled) {
                                    "Active — media volume is restored when you return to a tracked app."
                                } else {
                                    "Remembers media volume per app and restores it on each return."
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

                // Accessibility Warning Card
                if (!accessibilityGranted) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                context.startActivity(intent)
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.WarningAmber,
                                contentDescription = "Warning",
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Accessibility Service not enabled. Tap to open settings.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = "Open",
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Actions header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Select apps to track",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )

                    TextButton(
                        enabled = !isLoading && filteredApps.isNotEmpty(),
                        onClick = {
                            viewModel.setAppTrackingForPackages(
                                filteredApps.map { it.packageName },
                                !allVisibleSelected
                            )
                        }
                    ) {
                        Text(if (allVisibleSelected) "Deselect All" else "Select All")
                    }

                    TextButton(
                        enabled = !isLoading,
                        onClick = { viewModel.loadInstalledApps() }
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Refresh")
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Search field
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    hintText = "Search apps…",
                    leadingIcon = Icons.Default.Search,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // List of Apps
            if (filteredApps.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    if (isLoading) {
                        CircularProgressIndicator()
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Apps,
                                contentDescription = "No apps",
                                tint = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "No apps loaded.\nTap Refresh to load installed apps.",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 16.dp)
                ) {
                    items(filteredApps, key = { it.packageName }) { appEntry ->
                        AppTile(
                            entry = appEntry,
                            onToggle = { isTracked ->
                                viewModel.toggleAppTracking(appEntry.packageName, isTracked)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AppTile(
    entry: AppVolumeEntry,
    onToggle: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onToggle(!entry.isTracked) },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon Avatar
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        if (entry.isTracked) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                AppIcon(
                    packageName = entry.packageName,
                    fallbackChar = if (entry.appName.isNotEmpty()) entry.appName[0].uppercaseChar() else '?'
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = entry.appName,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (entry.rememberedMediaVolume != null) {
                        "Remembered volume: ${entry.rememberedMediaVolume}"
                    } else {
                        "No remembered volume yet"
                    },
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Checkbox(
                checked = entry.isTracked,
                onCheckedChange = { onToggle(it) }
            )
        }
    }
}

@Composable
fun AppIcon(packageName: String, fallbackChar: Char) {
    val context = LocalContext.current
    val pm = context.packageManager
    var bitmap by remember(packageName) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(packageName) {
        withContext(Dispatchers.IO) {
            try {
                val drawable = pm.getApplicationIcon(packageName)
                bitmap = drawableToBitmap(drawable)
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    if (bitmap != null) {
        Image(
            painter = BitmapPainter(bitmap!!.asImageBitmap()),
            contentDescription = "App Icon",
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
        )
    } else {
        Text(
            text = "$fallbackChar",
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun drawableToBitmap(drawable: Drawable): Bitmap {
    if (drawable is android.graphics.drawable.BitmapDrawable) {
        return drawable.bitmap
    }
    val bitmap = Bitmap.createBitmap(
        drawable.intrinsicWidth.coerceAtLeast(1),
        drawable.intrinsicHeight.coerceAtLeast(1),
        Bitmap.Config.ARGB_8888
    )
    val canvas = Canvas(bitmap)
    drawable.setBounds(0, 0, canvas.width, canvas.height)
    drawable.draw(canvas)
    return bitmap
}

@Composable
fun OutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    hintText: String,
    leadingIcon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(hintText, fontSize = 14.sp) },
        leadingIcon = { Icon(imageVector = leadingIcon, contentDescription = hintText) },
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
    )
}
