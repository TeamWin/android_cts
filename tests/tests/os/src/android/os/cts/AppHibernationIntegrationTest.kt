/*
 * Copyright (C) 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.os.cts

import android.app.Instrumentation
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.platform.test.annotations.AppModeFull
import android.provider.DeviceConfig.NAMESPACE_APP_HIBERNATION
import android.provider.Settings
import android.support.test.uiautomator.By
import android.support.test.uiautomator.BySelector
import android.support.test.uiautomator.UiObject2
import android.support.test.uiautomator.UiScrollable
import android.support.test.uiautomator.UiSelector
import androidx.test.InstrumentationRegistry
import androidx.test.filters.SdkSuppress
import androidx.test.runner.AndroidJUnit4
import com.android.compatibility.common.util.DisableAnimationRule
import com.android.compatibility.common.util.FreezeRotationRule
import com.android.compatibility.common.util.SystemUtil.runShellCommandOrThrow
import com.android.compatibility.common.util.UiAutomatorUtils
import org.hamcrest.CoreMatchers
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThat
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Integration test for app hibernation.
 */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.S, codeName = "S")
class AppHibernationIntegrationTest {
    companion object {
        const val LOG_TAG = "AppHibernationIntegrationTest"
        const val WAIT_TIME_MS = 1000L
        const val MAX_SCROLL_ATTEMPTS = 3

        const val SETTINGS_PACKAGE = "com.android.settings"
    }
    private val context: Context = InstrumentationRegistry.getTargetContext()
    private val instrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation()

    private lateinit var packageManager: PackageManager

    @get:Rule
    val disableAnimationRule = DisableAnimationRule()

    @get:Rule
    val freezeRotationRule = FreezeRotationRule()

    @Before
    fun setup() {
        packageManager = context.packageManager

        // Collapse notifications
        assertThat(
            runShellCommandOrThrow("cmd statusbar collapse"),
            CoreMatchers.equalTo(""))

        // Wake up the device
        runShellCommandOrThrow("input keyevent KEYCODE_WAKEUP")
        runShellCommandOrThrow("input keyevent 82")
    }

    @After
    fun cleanUp() {
        goHome()
    }

    @Test
    fun testUnusedApp_getsForceStopped() {
        withDeviceConfig(NAMESPACE_APP_HIBERNATION, "app_hibernation_enabled", "true") {
            withUnusedThresholdMs(1) {
                withApp(APK_PATH_S_APP, APK_PACKAGE_NAME_S_APP) {
                    // Use app
                    startApp(APK_PACKAGE_NAME_S_APP)
                    Thread.sleep(WAIT_TIME_MS)
                    runShellCommandOrThrow("input keyevent KEYCODE_BACK")
                    runShellCommandOrThrow("input keyevent KEYCODE_BACK")
                    Thread.sleep(WAIT_TIME_MS)
                    runShellCommandOrThrow("am kill $APK_PACKAGE_NAME_S_APP")
                    Thread.sleep(WAIT_TIME_MS)

                    // Run job
                    runAppHibernationJob(context, LOG_TAG)
                    Thread.sleep(WAIT_TIME_MS)

                    // Verify
                    val ai =
                        packageManager.getApplicationInfo(APK_PACKAGE_NAME_S_APP, 0 /* flags */)
                    val stopped = ((ai.flags and ApplicationInfo.FLAG_STOPPED) != 0)
                    assertTrue(stopped)
                    openUnusedAppsNotification()

                    waitFindObject(By.text(APK_PACKAGE_NAME_S_APP))
                }
            }
        }
    }

    @Test
    fun testPreSVersionUnusedApp_doesntGetForceStopped() {
        withUnusedThresholdMs(1) {
            withApp(APK_PATH_R_APP, APK_PACKAGE_NAME_R_APP) {
                // Use app
                startApp(APK_PACKAGE_NAME_R_APP)
                Thread.sleep(WAIT_TIME_MS)
                runShellCommandOrThrow("input keyevent KEYCODE_BACK")
                runShellCommandOrThrow("input keyevent KEYCODE_BACK")
                Thread.sleep(WAIT_TIME_MS)
                runShellCommandOrThrow("am kill $APK_PACKAGE_NAME_R_APP")
                Thread.sleep(WAIT_TIME_MS)

                // Run job
                runAppHibernationJob(context, LOG_TAG)
                Thread.sleep(WAIT_TIME_MS)

                // Verify
                val ai =
                    packageManager.getApplicationInfo(APK_PACKAGE_NAME_R_APP, 0 /* flags */)
                val stopped = ((ai.flags and ApplicationInfo.FLAG_STOPPED) != 0)
                assertFalse(stopped)
            }
        }
    }

    @AppModeFull(reason = "Uses application details settings")
    @Test
    fun testAppInfo_RemovePermissionsAndFreeUpSpaceToggleExists() {
        withDeviceConfig(NAMESPACE_APP_HIBERNATION, "app_hibernation_enabled", "true") {
            withApp(APK_PATH_S_APP, APK_PACKAGE_NAME_S_APP) {
                // Open app info
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                val uri = Uri.fromParts("package", APK_PACKAGE_NAME_S_APP, null /* fragment */)
                intent.data = uri
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)

                waitForIdle()
                UiAutomatorUtils.getUiDevice()

                val packageManager = context.packageManager
                val res = packageManager.getResourcesForApplication(SETTINGS_PACKAGE)
                val title = res.getString(
                    res.getIdentifier("unused_apps_switch", "string", SETTINGS_PACKAGE))
                assertTrue("Remove permissions and free up space toggle not found",
                    UiScrollable(UiSelector().scrollable(true)).scrollTextIntoView(title))
            }
        }
    }

    private fun waitFindObject(selector: BySelector): UiObject2 {
        return waitFindObject(instrumentation.uiAutomation, selector)
    }
}
