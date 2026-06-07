package com.nihaltp.volume_lock

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.nihaltp.volume_lock.ui.screens.AppVolumeLockScreen
import com.nihaltp.volume_lock.ui.screens.HomeScreen
import com.nihaltp.volume_lock.ui.screens.LogViewerScreen
import com.nihaltp.volume_lock.ui.screens.SettingsScreen
import com.nihaltp.volume_lock.ui.screens.VolumeLockScreen
import com.nihaltp.volume_lock.ui.theme.VolumeLockTheme
import com.nihaltp.volume_lock.ui.viewmodel.VolumeLockViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: VolumeLockViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        createNotificationChannels()
        requestNotificationPermission()

        setContent {
            VolumeLockTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()

                    NavHost(
                        navController = navController,
                        startDestination = "home"
                    ) {
                        composable("home") {
                            HomeScreen(
                                viewModel = viewModel,
                                onNavigateToVolumeLock = { navController.navigate("volume_lock") },
                                onNavigateToAppVolumeLock = { navController.navigate("app_volume_lock") },
                                onNavigateToSettings = { navController.navigate("settings") }
                            )
                        }
                        composable("volume_lock") {
                            VolumeLockScreen(
                                viewModel = viewModel,
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }
                        composable("app_volume_lock") {
                            AppVolumeLockScreen(
                                viewModel = viewModel,
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }
                        composable("settings") {
                            SettingsScreen(
                                viewModel = viewModel,
                                onNavigateToLogs = { navController.navigate("logs") },
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }
                        composable("logs") {
                            LogViewerScreen(
                                viewModel = viewModel,
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            nm.createNotificationChannel(
                NotificationChannel(
                    VolumeLockService.CHANNEL_ID,
                    "Volume Lock",
                    NotificationManager.IMPORTANCE_LOW
                ).apply { description = "Volume Lock foreground service" }
            )

            nm.createNotificationChannel(
                NotificationChannel(
                    AppVolumeLockService.CHANNEL_ID,
                    "App Volume Lock",
                    NotificationManager.IMPORTANCE_LOW
                ).apply { description = "App Volume Lock foreground service" }
            )
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(
                    arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                    101
                )
            }
        }
    }
}
