/*
 * Copyright (C) 2023 The Android Open Source Project
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

package android.permission.cts

import android.app.Instrumentation
import android.app.UiAutomation
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.permission.cts.NotificationListenerUtils.getNotificationForPackageAndId
import android.provider.DeviceConfig
import android.safetylabel.SafetyLabelConstants
import androidx.test.InstrumentationRegistry
import androidx.test.filters.SdkSuppress
import com.android.compatibility.common.util.DeviceConfigStateChangerRule
import com.android.compatibility.common.util.DisableAnimationRule
import com.android.compatibility.common.util.FreezeRotationRule
import com.android.compatibility.common.util.SystemUtil
import com.android.compatibility.common.util.SystemUtil.eventually
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Assume
import org.junit.Before
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test

/** End-to-end test for SafetyLabelChangesJobService. */
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.UPSIDE_DOWN_CAKE, codeName = "UpsideDownCake")
class SafetyLabelChangesJobServiceTest {
    @get:Rule val disableAnimationRule = DisableAnimationRule()

    @get:Rule val freezeRotationRule = FreezeRotationRule()

    @get:Rule
    val permissionRationaleEnabledConfig =
        DeviceConfigStateChangerRule(
            context,
            DeviceConfig.NAMESPACE_PRIVACY,
            SafetyLabelConstants.PERMISSION_RATIONALE_ENABLED,
            true.toString())

    @get:Rule
    val safetyLabelChangeNotificationsEnabledConfig =
        DeviceConfigStateChangerRule(
            context,
            DeviceConfig.NAMESPACE_PRIVACY,
            SafetyLabelConstants.SAFETY_LABEL_CHANGE_NOTIFICATIONS_ENABLED,
            true.toString())

    @Before
    fun setup() {
        val packageManager = context.packageManager
        Assume.assumeFalse(packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE))
        Assume.assumeFalse(packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK))
        Assume.assumeFalse(packageManager.hasSystemFeature(PackageManager.FEATURE_WATCH))

        SystemUtil.runShellCommand("input keyevent KEYCODE_WAKEUP")
        SystemUtil.runShellCommand("wm dismiss-keyguard")

        // Bypass battery saving restrictions
        SystemUtil.runShellCommand("cmd tare set-vip " +
            "${Process.myUserHandle().identifier} $permissionControllerPackageName true")
        NotificationListenerUtils.cancelNotifications(permissionControllerPackageName)
        resetPermissionControllerAndSimulateReboot()
    }

    @After
    fun tearDown() {
        cancelJob(SAFETY_LABEL_CHANGES_JOB_ID)
        NotificationListenerUtils.cancelNotifications(permissionControllerPackageName)
        // Reset battery saving restrictions
        SystemUtil.runShellCommand("cmd tare set-vip " +
            "${Process.myUserHandle().identifier} $permissionControllerPackageName default")
    }

    @Test
    fun afterRunMainJob_showNotification() {
        runMainJob()
        TestUtils.awaitJobUntilRequestedState(
            permissionControllerPackageName,
            SAFETY_LABEL_CHANGES_JOB_ID,
            TIMEOUT_TIME_MS, uiAutomation(),
            "unknown"
        )
        assertNotificationShown()
    }

    companion object {
        private const val TIMEOUT_TIME_MS = 25_000L

        private const val SAFETY_LABEL_CHANGES_JOB_ID = 9
        private const val SET_UP_SAFETY_LABEL_CHANGES_JOB =
            "com.android.permissioncontroller.action.SET_UP_SAFETY_LABEL_CHANGES_JOB"
        private const val SAFETY_LABEL_CHANGES_JOB_SERVICE_RECEIVER_CLASS =
            "com.android.permissioncontroller.permission.service.v34" +
                ".SafetyLabelChangesJobService\$Receiver"

        private const val SAFETY_LABEL_CHANGES_NOTIFICATION_ID = 5

        private val context: Context = InstrumentationRegistry.getTargetContext()
        private val instrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation()
        private fun uiAutomation(): UiAutomation = instrumentation.uiAutomation
        private val permissionControllerPackageName =
            context.packageManager.permissionControllerPackageName
        private val userId = Process.myUserHandle().identifier

        @get:ClassRule
        @JvmStatic
        val ctsNotificationListenerHelper =
            CtsNotificationListenerHelperRule(
                InstrumentationRegistry.getInstrumentation().targetContext)

        private fun assertNotificationShown() {
            eventually {
                val notification = getNotification(false)
                assertThat(notification).isNotNull()
            }
        }

        private fun getNotification(cancelNotification: Boolean) =
            getNotificationForPackageAndId(
                permissionControllerPackageName,
                SAFETY_LABEL_CHANGES_NOTIFICATION_ID,
                cancelNotification)?.notification

        private fun cancelJob(jobId: Int) {
            SystemUtil.runShellCommand(
                "cmd jobscheduler cancel -u $userId $permissionControllerPackageName $jobId")
            TestUtils.awaitJobUntilRequestedState(
                permissionControllerPackageName, jobId, TIMEOUT_TIME_MS, uiAutomation(), "unknown")
        }

        private fun runMainJob() {
            val runJobCmd = "cmd jobscheduler run -u $userId -f $permissionControllerPackageName " +
                "$SAFETY_LABEL_CHANGES_JOB_ID"
            try {
                SystemUtil.runShellCommand(uiAutomation(), runJobCmd)
            } catch (e: Throwable) {
                throw RuntimeException(e)
            }
        }

        private fun resetPermissionControllerAndSimulateReboot() {
            PermissionUtils.resetPermissionControllerJob(uiAutomation(),
                permissionControllerPackageName, SAFETY_LABEL_CHANGES_JOB_ID,
                TIMEOUT_TIME_MS, SET_UP_SAFETY_LABEL_CHANGES_JOB,
                SAFETY_LABEL_CHANGES_JOB_SERVICE_RECEIVER_CLASS)
        }
    }
}
