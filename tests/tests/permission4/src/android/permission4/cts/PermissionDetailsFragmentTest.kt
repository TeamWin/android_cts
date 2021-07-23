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

package android.permission4.cts

import android.Manifest
import android.app.Instrumentation
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.support.test.uiautomator.By
import android.support.test.uiautomator.UiDevice
import android.support.test.uiautomator.UiSelector
import androidx.test.platform.app.InstrumentationRegistry
import com.android.compatibility.common.util.SystemUtil
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test

private const val APP_LABEL_1 = "CtsCameraMicAccess"
private const val APP_LABEL_2 = "CtsMicAccess"
private const val USE_CAMERA = "use_camera"
private const val USE_MICROPHONE = "use_microphone"
private const val USE_HOTWORD = "use_hotword"
private const val INTENT_ACTION_1 = "test.action.USE_CAMERA_OR_MIC"
private const val INTENT_ACTION_2 = "test.action.USE_MIC"
private const val PERMISSION_CONTROLLER_PACKAGE_ID_PREFIX = "com.android.permissioncontroller:id/"
private const val HISTORY_PREFERENCE_ICON = "permission_history_icon"
private const val HISTORY_PREFERENCE_TIME = "permission_history_time"
private const val SHOW_SYSTEM = "Show system"
private const val MORE_OPTIONS = "More options"

class PermissionDetailsFragmentTest {

    private val instrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.context
    private val uiDevice: UiDevice = UiDevice.getInstance(instrumentation)
    private var screenTimeoutBeforeTest: Long = 0L
    private val packageManager: PackageManager = context.packageManager
    private val micLabel = packageManager.getPermissionGroupInfo(
            Manifest.permission_group.MICROPHONE, 0).loadLabel(packageManager).toString()

    @Before
    fun setup() {
        SystemUtil.runWithShellPermissionIdentity {
            screenTimeoutBeforeTest = Settings.System.getLong(
                    context.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT
            )
            Settings.System.putLong(
                    context.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, 1800000L
            )
        }

        uiDevice.wakeUp()
        SystemUtil.runShellCommand(instrumentation, "wm dismiss-keyguard")
        uiDevice.findObject(By.text("Close"))?.click()
    }

    @After
    fun tearDown() {
        SystemUtil.runWithShellPermissionIdentity {
            Settings.System.putLong(
                    context.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT,
                    screenTimeoutBeforeTest
            )
        }

        Thread.sleep(3000)
    }

    @Test
    fun testToggleSystemApps() {
        // I had some hard time mocking a system app.
        // Hence here I am only testing if the toggle is there.
        // Will comeback and add the system app for testing if we
        // need the line coverage for this. - theianchen@
        openMicrophoneOrCameraApp()
        SystemUtil.eventually {
            val appView = uiDevice.findObject(UiSelector().textContains(APP_LABEL_1))
            Assert.assertTrue("View with text $APP_LABEL_1 not found", appView.exists())
        }

        openMicrophoneTimeline()
        SystemUtil.eventually {
            val menuView = uiDevice.findObject(UiSelector().descriptionContains(MORE_OPTIONS))
            Assert.assertTrue("View with description $MORE_OPTIONS not found", menuView.exists())
            menuView.click()

            val showSystemView = uiDevice.findObject(By.text(SHOW_SYSTEM))
            Assert.assertNotNull("View with description $SHOW_SYSTEM not found", showSystemView)
            uiDevice.pressBack()
        }

        uiDevice.pressHome()
    }

    @Test
    fun testMicrophoneTimelineWithOneApp() {
        openMicrophoneOrCameraApp()
        SystemUtil.eventually {
            val appView = uiDevice.findObject(UiSelector().textContains(APP_LABEL_1))
            Assert.assertTrue("View with text $APP_LABEL_1 not found", appView.exists())
        }

        openMicrophoneTimeline()
        SystemUtil.eventually {
            val timelineView = uiDevice.findObject(UiSelector().descriptionContains(micLabel))
            Assert.assertTrue("View with description $micLabel not found",
                    timelineView.exists())

            val appView = uiDevice.findObject(UiSelector().textContains(APP_LABEL_1))
            Assert.assertTrue("View with text $APP_LABEL_1 not found", appView.exists())

            val iconView = uiDevice.findObject(By.res(
                    PERMISSION_CONTROLLER_PACKAGE_ID_PREFIX + HISTORY_PREFERENCE_ICON))
            Assert.assertNotNull("View with description $HISTORY_PREFERENCE_ICON not found",
                    iconView)

            val timestampView = uiDevice.findObject(By.res(
                    PERMISSION_CONTROLLER_PACKAGE_ID_PREFIX + HISTORY_PREFERENCE_TIME))
            Assert.assertNotNull("View with description $HISTORY_PREFERENCE_TIME not found",
                    timestampView)
        }

        uiDevice.pressHome()
    }

    @Test
    fun testCameraTimelineWithMultipleApps() {
        openMicrophoneOrCameraApp()
        SystemUtil.eventually {
            val appView = uiDevice.findObject(UiSelector().textContains(APP_LABEL_1))
            Assert.assertTrue("View with text $APP_LABEL_1 not found", appView.exists())
        }

        openMicrophoneApp()
        SystemUtil.eventually {
            val appView = uiDevice.findObject(UiSelector().textContains(APP_LABEL_2))
            Assert.assertTrue("View with text $APP_LABEL_2 not found", appView.exists())
        }

        openMicrophoneTimeline()
        SystemUtil.eventually {
            val timelineView = uiDevice.findObject(UiSelector().descriptionContains(micLabel))
            Assert.assertTrue("View with description $micLabel not found",
                    timelineView.exists())

            val app1View = uiDevice.findObject(UiSelector().textContains(APP_LABEL_1))
            Assert.assertTrue("View with text $APP_LABEL_1 not found", app1View.exists())

            val app2View = uiDevice.findObject(UiSelector().textContains(APP_LABEL_2))
            Assert.assertTrue("View with text $APP_LABEL_2 not found", app2View.exists())
        }

        uiDevice.pressHome()
    }

    private fun openMicrophoneOrCameraApp() {
        context.startActivity(Intent(INTENT_ACTION_1).apply {
            putExtra(USE_MICROPHONE, true)
            putExtra(USE_CAMERA, false)
            putExtra(USE_HOTWORD, false)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    private fun openMicrophoneApp() {
        context.startActivity(Intent(INTENT_ACTION_2).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    private fun openMicrophoneTimeline() {
        SystemUtil.runWithShellPermissionIdentity {
            context.startActivity(Intent(Intent.ACTION_REVIEW_PERMISSION_HISTORY).apply {
                putExtra(Intent.EXTRA_PERMISSION_GROUP_NAME, Manifest.permission_group.MICROPHONE)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }
    }
}