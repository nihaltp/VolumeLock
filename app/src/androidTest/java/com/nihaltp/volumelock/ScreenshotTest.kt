package com.nihaltp.volumelock

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.printToLog
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nihaltp.volumelock.ui.viewmodel.AppVolumeEntry
import com.nihaltp.volumelock.ui.viewmodel.VolumeState
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import tools.fastlane.screengrab.Screengrab
import tools.fastlane.screengrab.locale.LocaleTestRule

@RunWith(AndroidJUnit4::class)
class ScreenshotTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Rule
    @JvmField
    val localeTestRule = LocaleTestRule()

    var screenshotCounter = 1

    @Before
    fun setUp() {
        val activity = composeTestRule.activity
        val viewModel = activity.viewModel

        // Setup testing flags to prevent background updates and system queries
        viewModel.isTesting = true
        viewModel._materialYouEnabled.value = false
        activity.forceDisableDynamicColor = true
    }

    @Test
    fun captureScreenshots() {
        // Hide system status bar and navigation bar dynamically to get only the app screen
        composeTestRule.activity.runOnUiThread {
            val window = composeTestRule.activity.window
            val controller = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
            controller.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        composeTestRule.waitForIdle()

        val activity = composeTestRule.activity
        val viewModel = activity.viewModel

        // Seed initial states for home screen
        viewModel._volumeLockEnabled.value = true
        viewModel._appVolumeLockEnabled.value = true
        viewModel._loggingEnabled.value = true
        viewModel._accessibilityGranted.value = true

        // Seed installed apps list
        val fakeApps = listOf(
            AppVolumeEntry("com.google.android.youtube", "YouTube", true, 12),
            AppVolumeEntry("com.spotify.music", "Spotify", true, 8),
            AppVolumeEntry("com.netflix.mediaclient", "Netflix", false, null),
            AppVolumeEntry("org.videolan.vlc", "VLC", false, null),
            AppVolumeEntry("com.mxtech.videoplayer.ad", "MX Player", true, 10),
            AppVolumeEntry("com.amazon.avod.thirdpartyclient", "Prime Video", false, null)
        )
        viewModel._installedApps.value = fakeApps

        // Seed volumes state
        viewModel._currentVolumes.value = VolumeState(media = 10, ring = 6, notification = 4, alarm = 5)
        viewModel._lockedVolumes.value = VolumeState(media = 8, ring = 6, notification = 4, alarm = 5)

        try {
            // ==========================================
            // LIGHT THEME
            // ==========================================
            activity.runOnUiThread {
                viewModel._themeMode.value = "light"
                viewModel._materialYouEnabled.value = false
            }
            composeTestRule.waitForIdle()

            // 1. Home Screen - Light
            Thread.sleep(1200)
            Screengrab.screenshot(screenshotCounter.toString())
            screenshotCounter++

            // 2. Volume Lock Screen - Light
            composeTestRule.onNode(hasText("Volume Lock") and hasClickAction()).performClick()
            composeTestRule.waitForIdle()
            Thread.sleep(1200)
            Screengrab.screenshot(screenshotCounter.toString())
            screenshotCounter++

            // Go back to Home
            composeTestRule.onNodeWithContentDescription("Back").performClick()
            composeTestRule.waitForIdle()

            // 3. App Volume Lock Screen - Light
            composeTestRule.onNode(hasText("App Volume Lock") and hasClickAction()).performClick()
            composeTestRule.waitForIdle()
            Thread.sleep(1200)
            Screengrab.screenshot(screenshotCounter.toString())
            screenshotCounter++

            // Go back to Home
            composeTestRule.onNodeWithContentDescription("Back").performClick()
            composeTestRule.waitForIdle()

            // 4. Settings Screen - Light
            composeTestRule.onNode(hasText("Settings") and hasClickAction()).performClick()
            composeTestRule.waitForIdle()
            Thread.sleep(1200)
            Screengrab.screenshot(screenshotCounter.toString())
            screenshotCounter++

            // Go back to Home
            composeTestRule.onNodeWithContentDescription("Back").performClick()
            composeTestRule.waitForIdle()

            // ==========================================
            // DARK THEME
            // ==========================================
            activity.runOnUiThread {
                viewModel._themeMode.value = "dark"
                viewModel._materialYouEnabled.value = false
            }
            composeTestRule.waitForIdle()

            // 5. Home Screen - Dark
            Thread.sleep(1200)
            Screengrab.screenshot(screenshotCounter.toString())
            screenshotCounter++

            // 6. Volume Lock Screen - Dark
            composeTestRule.onNode(hasText("Volume Lock") and hasClickAction()).performClick()
            composeTestRule.waitForIdle()
            Thread.sleep(1200)
            Screengrab.screenshot(screenshotCounter.toString())
            screenshotCounter++

            // Go back to Home
            composeTestRule.onNodeWithContentDescription("Back").performClick()
            composeTestRule.waitForIdle()

            // 7. App Volume Lock Screen - Dark
            composeTestRule.onNode(hasText("App Volume Lock") and hasClickAction()).performClick()
            composeTestRule.waitForIdle()
            Thread.sleep(1200)
            Screengrab.screenshot(screenshotCounter.toString())
            screenshotCounter++

            // Go back to Home
            composeTestRule.onNodeWithContentDescription("Back").performClick()
            composeTestRule.waitForIdle()

            // 8. Settings Screen - Dark
            composeTestRule.onNode(hasText("Settings") and hasClickAction()).performClick()
            composeTestRule.waitForIdle()
            Thread.sleep(1200)
            Screengrab.screenshot(screenshotCounter.toString())
            screenshotCounter++

            // Go back to Home
            composeTestRule.onNodeWithContentDescription("Back").performClick()
            composeTestRule.waitForIdle()
        } catch (t: Throwable) {
            println("SCREENSHOT_TEST_FAILURE: ${t.message}")
            try {
                composeTestRule.onRoot().printToLog("SCREENSHOT_TEST_FAILURE_TREE")
            } catch (e: Exception) {
                println("Failed to print semantics tree: ${e.message}")
            }
            throw t
        } finally {
            // Copy screenshots to external files directory for reliable pulling on Windows
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val internalDir = java.io.File(context.filesDir.parentFile, "app_screengrab")
            val externalDir = context.getExternalFilesDir(null)
            if (internalDir.exists() && externalDir != null) {
                internalDir.copyRecursively(java.io.File(externalDir, "app_screengrab"), overwrite = true)
            }
        }
    }
}
