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

package android.permission3.cts

import android.app.Instrumentation
import android.app.UiAutomation
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.permission.cts.CtsNotificationListenerHelperRule
import android.permission.cts.CtsNotificationListenerServiceUtils
import android.permission.cts.CtsNotificationListenerServiceUtils.getNotificationForPackageAndId
import android.permission.cts.PermissionUtils
import android.permission.cts.TestUtils
import android.permission3.cts.AppMetadata.createAppMetadataWithLocationSharingNoAds
import android.permission3.cts.AppMetadata.createAppMetadataWithNoSharing
import android.provider.DeviceConfig
import android.safetylabel.SafetyLabelConstants
import androidx.test.InstrumentationRegistry
import androidx.test.filters.SdkSuppress
import com.android.compatibility.common.util.DeviceConfigStateChangerRule
import com.android.compatibility.common.util.SystemUtil
import com.android.compatibility.common.util.SystemUtil.eventually
import com.android.compatibility.common.util.SystemUtil.waitForBroadcasts
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Assume
import org.junit.Before
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test

/** End-to-end test for SafetyLabelChangesJobService. */
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.UPSIDE_DOWN_CAKE, codeName = "UpsideDownCake")
class SafetyLabelChangesJobServiceTest : BaseUsePermissionTest() {

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

    /**
     * This rule serves to limit the max number of safety labels that can be persisted, so that
     * repeated tests don't overwhelm the disk storage on the device.
     */
    @get:Rule
    val deviceConfigMaxSafetyLabelsPersistedPerApp =
        DeviceConfigStateChangerRule(
            context,
            DeviceConfig.NAMESPACE_PRIVACY,
            PROPERTY_MAX_SAFETY_LABELS_PERSISTED_PER_APP,
            "2")

    @get:Rule
    val deviceConfigDataSharingUpdatesPeriod =
        DeviceConfigStateChangerRule(
            BasePermissionTest.context,
            DeviceConfig.NAMESPACE_PRIVACY,
            PROPERTY_DATA_SHARING_UPDATE_PERIOD_MILLIS,
            "600000")

    @Before
    fun setup() {
        val packageManager = context.packageManager
        Assume.assumeFalse(packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE))
        Assume.assumeFalse(packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK))
        Assume.assumeFalse(packageManager.hasSystemFeature(PackageManager.FEATURE_WATCH))

        SystemUtil.runShellCommand("input keyevent KEYCODE_WAKEUP")
        SystemUtil.runShellCommand("wm dismiss-keyguard")

        // Bypass battery saving restrictions
        SystemUtil.runShellCommand(
            "cmd tare set-vip " +
                "${Process.myUserHandle().identifier} $permissionControllerPackageName true")
        CtsNotificationListenerServiceUtils.cancelNotifications(permissionControllerPackageName)
        resetPermissionControllerAndSimulateReboot()
    }

    @After
    fun cancelJobAndNotifications() {
        cancelJob(SAFETY_LABEL_CHANGES_JOB_ID)
        CtsNotificationListenerServiceUtils.cancelNotifications(permissionControllerPackageName)
        // Reset battery saving restrictions
        SystemUtil.runShellCommand(
            "cmd tare set-vip " +
                "${Process.myUserHandle().identifier} $permissionControllerPackageName default")
    }

    @Test
    fun runMainJob_whenLocationSharingUpdatesForLocationGrantedApps_showsNotification() {
        installPackageViaSession(APP_APK_NAME_31, createAppMetadataWithNoSharing())
        waitForBroadcasts()
        installPackageViaSession(APP_APK_NAME_31, createAppMetadataWithLocationSharingNoAds())
        waitForBroadcasts()
        grantLocationPermission(APP_PACKAGE_NAME)

        runMainJob()
        TestUtils.awaitJobUntilRequestedState(
            permissionControllerPackageName,
            SAFETY_LABEL_CHANGES_JOB_ID,
            TIMEOUT_TIME_MS,
            uiAutomation(),
            "unknown")

        assertNotificationShown()
    }

    @Test
    fun runMainJob_whenNoLocationGrantedApps_doesNotShowNotification() {
        installPackageViaSession(APP_APK_NAME_31, createAppMetadataWithNoSharing())
        waitForBroadcasts()
        installPackageViaSession(APP_APK_NAME_31, createAppMetadataWithLocationSharingNoAds())
        waitForBroadcasts()

        runMainJob()
        TestUtils.awaitJobUntilRequestedState(
            permissionControllerPackageName,
            SAFETY_LABEL_CHANGES_JOB_ID,
            TIMEOUT_TIME_MS,
            uiAutomation(),
            "unknown")

        assertNotificationNotShown()
    }

    @Test
    fun runMainJob_whenNoLocationSharingUpdates_doesNotShowNotification() {
        installPackageViaSession(APP_APK_NAME_31, createAppMetadataWithNoSharing())
        waitForBroadcasts()
        grantLocationPermission(APP_PACKAGE_NAME)

        runMainJob()
        TestUtils.awaitJobUntilRequestedState(
            permissionControllerPackageName,
            SAFETY_LABEL_CHANGES_JOB_ID,
            TIMEOUT_TIME_MS,
            uiAutomation(),
            "unknown")

        assertNotificationNotShown()
    }

    private fun grantLocationPermission(packageName: String) {
        uiAutomation.grantRuntimePermission(
            packageName, android.Manifest.permission.ACCESS_FINE_LOCATION)
    }

    companion object {
        private const val TIMEOUT_TIME_MS = 25_000L

        private const val SAFETY_LABEL_CHANGES_JOB_ID = 9
        private const val SET_UP_SAFETY_LABEL_CHANGES_JOB =
            "com.android.permissioncontroller.action.SET_UP_SAFETY_LABEL_CHANGES_JOB"
        private const val SAFETY_LABEL_CHANGES_JOB_SERVICE_RECEIVER_CLASS =
            "com.android.permissioncontroller.permission.service.v34" +
                ".SafetyLabelChangesJobService\$Receiver"
        private const val PROPERTY_DATA_SHARING_UPDATE_PERIOD_MILLIS =
            "data_sharing_update_period_millis"
        private const val PROPERTY_MAX_SAFETY_LABELS_PERSISTED_PER_APP =
            "max_safety_labels_persisted_per_app"
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

        private fun assertNotificationNotShown() {
            eventually {
                val notification = getNotification(false)
                assertThat(notification).isNull()
            }
        }

        private fun getNotification(cancelNotification: Boolean) =
            getNotificationForPackageAndId(
                    permissionControllerPackageName,
                    SAFETY_LABEL_CHANGES_NOTIFICATION_ID,
                    cancelNotification)
                ?.notification

        private fun cancelJob(jobId: Int) {
            SystemUtil.runShellCommand(
                "cmd jobscheduler cancel -u $userId $permissionControllerPackageName $jobId")
            TestUtils.awaitJobUntilRequestedState(
                permissionControllerPackageName, jobId, TIMEOUT_TIME_MS, uiAutomation(), "unknown")
        }

        private fun runMainJob() {
            val runJobCmd =
                "cmd jobscheduler run -u $userId -f $permissionControllerPackageName " +
                    "$SAFETY_LABEL_CHANGES_JOB_ID"
            try {
                SystemUtil.runShellCommand(uiAutomation(), runJobCmd)
            } catch (e: Throwable) {
                throw RuntimeException(e)
            }
        }

        private fun resetPermissionControllerAndSimulateReboot() {
            PermissionUtils.resetPermissionControllerJob(
                uiAutomation(),
                permissionControllerPackageName,
                SAFETY_LABEL_CHANGES_JOB_ID,
                TIMEOUT_TIME_MS,
                SET_UP_SAFETY_LABEL_CHANGES_JOB,
                SAFETY_LABEL_CHANGES_JOB_SERVICE_RECEIVER_CLASS)
        }
    }
}
