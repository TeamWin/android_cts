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
import android.app.Notification
import android.app.UiAutomation
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.permission.PermissionControllerManager
import android.permission.cts.NotificationListenerUtils.getNotificationForPackageAndId
import android.provider.DeviceConfig
import android.safetylabel.SafetyLabelConstants
import androidx.test.InstrumentationRegistry
import androidx.test.filters.SdkSuppress
import com.android.compatibility.common.util.DeviceConfigStateChangerRule
import com.android.compatibility.common.util.DeviceConfigStateManager
import com.android.compatibility.common.util.DisableAnimationRule
import com.android.compatibility.common.util.FreezeRotationRule
import com.android.compatibility.common.util.SystemUtil
import com.android.compatibility.common.util.SystemUtil.eventually
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.AfterClass
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test

/** End-to-end test for SafetyLabelChangesJobService. */
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.UPSIDE_DOWN_CAKE, codeName = "UpsideDownCake")
class SafetyLabelChangesJobServiceTest {
    private val context: Context = InstrumentationRegistry.getTargetContext()
    private val instrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation()
    private val uiAutomation: UiAutomation = instrumentation.getUiAutomation()
    private val permissionControllerPackageName =
        context.packageManager.permissionControllerPackageName
    private val userId = Process.myUserHandle().identifier

    private lateinit var packageManager: PackageManager
    private lateinit var permissionControllerManager: PermissionControllerManager

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

    @get:Rule
    val safetyLabelChangesJobDelayMillisConfig =
        DeviceConfigStateChangerRule(
            context,
            DeviceConfig.NAMESPACE_PRIVACY,
            PROPERTY_SAFETY_LABEL_CHANGES_JOB_DELAY_MILLIS,
            0.toString())

    @Before
    fun setup() {
        packageManager = context.packageManager
        permissionControllerManager =
            context.getSystemService(PermissionControllerManager::class.java)!!
        assertThat(getJobState(PERIODIC_SAFETY_LABEL_CHANGES_JOB_ID)).startsWith("waiting")
        cancelJob(SAFETY_LABEL_CHANGES_JOB_ID)
        assertThat(getJobState(SAFETY_LABEL_CHANGES_JOB_ID)).startsWith("unknown")
        NotificationListenerUtils.cancelNotifications(permissionControllerPackageName)
        assertNotificationNotShown()
    }

    @After
    fun tearDown() {
        cancelJob(SAFETY_LABEL_CHANGES_JOB_ID)
        NotificationListenerUtils.cancelNotifications(permissionControllerPackageName)
    }

    @Test
    fun afterRunPeriodicJob_mainJobDelays() {
        DeviceConfigStateManager(
            context,
            DeviceConfig.NAMESPACE_PRIVACY,
            PROPERTY_SAFETY_LABEL_CHANGES_JOB_DELAY_MILLIS)
            .set(TIMEOUT_TIME_MS.toString())

        runJob(PERIODIC_SAFETY_LABEL_CHANGES_JOB_ID)

        assertThat(getJobState(SAFETY_LABEL_CHANGES_JOB_ID)).startsWith("waiting")
    }

    @Test
    fun afterRunMainJob_showNotification() {
        runJob(PERIODIC_SAFETY_LABEL_CHANGES_JOB_ID)
        waitForJobFinished(SAFETY_LABEL_CHANGES_JOB_ID)

        assertNotificationShown()
    }

    private fun assertNotificationShown() {
        eventually {
            val notification = getNotification(false)
            assertThat(notification).isNotNull()
            assertThat(notification!!.extras.getString(Notification.EXTRA_TITLE))
                .isEqualTo(SAFETY_LABEL_CHANGES_NOTIFICATION_TITLE)
            assertThat(notification!!.extras.getString(Notification.EXTRA_TEXT))
                .isEqualTo(SAFETY_LABEL_CHANGES_NOTIFICATION_DESC)
        }
    }

    private fun assertNotificationNotShown() = assertThat(getNotification(false))

    private fun getNotification(cancelNotification: Boolean) =
        getNotificationForPackageAndId(
            permissionControllerPackageName,
            SAFETY_LABEL_CHANGES_NOTIFICATION_ID,
            cancelNotification)?.notification

    private fun runJob(jobId: Int) =
        TestUtils.runJobAndWaitUntilCompleted(
            permissionControllerPackageName, jobId, TIMEOUT_TIME_MS, uiAutomation)

    private fun cancelJob(jobId: Int) =
        SystemUtil.runShellCommandOrThrow(
            "cmd jobscheduler cancel -u $userId $permissionControllerPackageName $jobId")

    private fun waitForJobFinished(jobId: Int) = waitForJobState(jobId, "unknown")

    private fun waitForJobState(jobId: Int, requestedState: String) =
        TestUtils.awaitJobUntilRequestedState(
            permissionControllerPackageName, jobId, TIMEOUT_TIME_MS, uiAutomation, requestedState)

    private fun getJobState(jobId: Int): String =
        SystemUtil.runShellCommand(
            "cmd jobscheduler get-job-state -u $userId $permissionControllerPackageName $jobId")
            .trim()

    companion object {
        private const val TIMEOUT_TIME_MS = 5000L

        private const val PERIODIC_SAFETY_LABEL_CHANGES_JOB_ID = 8
        private const val SAFETY_LABEL_CHANGES_JOB_ID = 9

        private const val PROPERTY_SAFETY_LABEL_CHANGES_JOB_DELAY_MILLIS =
            "safety_label_changes_job_delay_millis"

        private const val SAFETY_LABEL_CHANGES_NOTIFICATION_ID = 5

        private val SAFETY_LABEL_CHANGES_NOTIFICATION_TITLE = "Review data sharing updates"
        private val SAFETY_LABEL_CHANGES_NOTIFICATION_DESC = "The way some apps share location " +
                "data has changed"

        @BeforeClass
        @JvmStatic
        fun beforeClassSetup() = allowNotificationAccess()

        @AfterClass
        @JvmStatic
        fun cleanupAfterClass() = disallowNotificationAccess()

        @JvmStatic
        private fun allowNotificationAccess() {
            SystemUtil.runShellCommand("cmd notification allow_listener " + ComponentName(
                InstrumentationRegistry.getTargetContext(),
                NotificationListener::class.java)
                .flattenToString())
        }

        @JvmStatic
        private fun disallowNotificationAccess() {
            SystemUtil.runShellCommand("cmd notification disallow_listener " + ComponentName(
                InstrumentationRegistry.getTargetContext(),
                NotificationListener::class.java)
                .flattenToString())
        }
    }
}
