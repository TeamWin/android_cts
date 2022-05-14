/*
 * Copyright (C) 2022 The Android Open Source Project
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

import android.accessibility.cts.common.InstrumentedAccessibilityService
import android.accessibility.cts.common.InstrumentedAccessibilityServiceTestRule
import android.app.Instrumentation
import android.app.UiAutomation
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import android.permission.cts.NotificationListenerUtils.assertEmptyNotification
import android.permission.cts.NotificationListenerUtils.assertNotificationExist
import android.permission.cts.NotificationListenerUtils.cancelNotification
import android.permission.cts.NotificationListenerUtils.cancelNotifications
import android.permission.cts.NotificationListenerUtils.getNotification
import android.permission.cts.SafetyCenterUtils.assertSafetyCenterIssueDoesNotExist
import android.permission.cts.SafetyCenterUtils.assertSafetyCenterIssueExist
import android.permission.cts.SafetyCenterUtils.assertSafetyCenterStarted
import android.permission.cts.SafetyCenterUtils.deviceSupportsSafetyCenter
import android.permission.cts.SafetyCenterUtils.setDeviceConfigPrivacyProperty
import android.provider.DeviceConfig
import android.safetycenter.SafetyCenterManager
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.AndroidJUnit4
import com.android.compatibility.common.util.DeviceConfigStateChangerRule
import com.android.compatibility.common.util.ProtoUtils
import com.android.compatibility.common.util.SystemUtil.runShellCommand
import com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity
import com.android.server.job.nano.JobSchedulerServiceDumpProto
import org.junit.After
import org.junit.Assert
import org.junit.Assume
import org.junit.Before
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.TIRAMISU, codeName = "Tiramisu")
class AccessibilityPrivacySourceTest {

    private val instrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext
    private val mAccessibilityServiceRule = InstrumentedAccessibilityServiceTestRule(
        AccessibilityTestService::class.java, false
    )
    private val permissionControllerPackage = context.packageManager
        .permissionControllerPackageName
    private val accessibilityTestService = ComponentName(
        context,
        AccessibilityTestService::class.java
    ).flattenToString()
    private val safetyCenterIssueId = "accessibility_$accessibilityTestService"
    private val safetyCenterManager = context.getSystemService(SafetyCenterManager::class.java)

    @get:Rule
    val deviceConfigSafetyCenterEnabled = DeviceConfigStateChangerRule(
        context,
        DeviceConfig.NAMESPACE_PRIVACY,
        SAFETY_CENTER_ENABLED,
        true.toString()
    )

    @get:Rule
    val deviceConfigA11ySourceEnabled = DeviceConfigStateChangerRule(
        context,
        DeviceConfig.NAMESPACE_PRIVACY,
        ACCESSIBILITY_SOURCE_ENABLED,
        true.toString()
    )

    @get:Rule
    val deviceConfigA11yListenerDisabled = DeviceConfigStateChangerRule(
        context,
        DeviceConfig.NAMESPACE_PRIVACY,
        ACCESSIBILITY_LISTENER_ENABLED,
        false.toString()
    )

    @Before
    fun setup() {
        Assume.assumeTrue(deviceSupportsSafetyCenter(context))
        InstrumentedAccessibilityService.disableAllServices()
        runShellCommand("input keyevent KEYCODE_WAKEUP")
        resetPermissionController()
        cancelNotifications(permissionControllerPackage)
    }

    @After
    fun cleanup() {
        cancelNotifications(permissionControllerPackage)
        runWithShellPermissionIdentity {
            safetyCenterManager?.clearAllSafetySourceDataForTests()
        }
    }

    @Test
    fun testJobSendsNotification() {
        mAccessibilityServiceRule.enableService()
        runJobAndWaitUntilCompleted()
        assertNotificationExist(permissionControllerPackage, ACCESSIBILITY_NOTIFICATION_ID)
    }

    @Test
    fun testJobSendsIssuesToSafetyCenter() {
        mAccessibilityServiceRule.enableService()
        runJobAndWaitUntilCompleted()
        assertSafetyCenterIssueExist(SC_ACCESSIBILITY_SOURCE_ID, safetyCenterIssueId)
    }

    @Test
    fun testJobDoesNotSendNotificationInSecondRunForSameService() {
        mAccessibilityServiceRule.enableService()
        runJobAndWaitUntilCompleted()
        assertNotificationExist(permissionControllerPackage, ACCESSIBILITY_NOTIFICATION_ID)

        cancelNotification(permissionControllerPackage, ACCESSIBILITY_NOTIFICATION_ID)

        runJobAndWaitUntilCompleted()
        assertEmptyNotification(permissionControllerPackage, ACCESSIBILITY_NOTIFICATION_ID)
    }

    @Test
    fun testAccessibilityListenerSendsIssueToSafetyCenter() {
        setDeviceConfigPrivacyProperty(ACCESSIBILITY_LISTENER_ENABLED, true.toString())
        val automation = getAutomation()
        mAccessibilityServiceRule.enableService()
        TestUtils.eventually({
            assertSafetyCenterIssueExist(SC_ACCESSIBILITY_SOURCE_ID,
                safetyCenterIssueId, automation)
        }, TIMEOUT_MILLIS)
        automation.destroy()
    }

    @Test
    fun testJobWithDisabledServiceDoesNotSendNotification() {
        runJobAndWaitUntilCompleted()
        assertEmptyNotification(permissionControllerPackage, ACCESSIBILITY_NOTIFICATION_ID)
    }

    @Test
    fun testJobWithDisabledServiceDoesNotSendIssueToSafetyCenter() {
        runJobAndWaitUntilCompleted()
        assertSafetyCenterIssueDoesNotExist(SC_ACCESSIBILITY_SOURCE_ID, safetyCenterIssueId)
    }

    @Test
    fun testJobWithAccessibilityFeatureDisabledDoesNotSendNotification() {
        setDeviceConfigPrivacyProperty(ACCESSIBILITY_SOURCE_ENABLED, false.toString())
        mAccessibilityServiceRule.enableService()
        runJobAndWaitUntilCompleted()
        assertEmptyNotification(permissionControllerPackage, ACCESSIBILITY_NOTIFICATION_ID)
    }

    @Test
    fun testJobWithAccessibilityFeatureDisabledDoesNotSendIssueToSafetyCenter() {
        setDeviceConfigPrivacyProperty(ACCESSIBILITY_SOURCE_ENABLED, false.toString())
        mAccessibilityServiceRule.enableService()
        runJobAndWaitUntilCompleted()
        assertSafetyCenterIssueDoesNotExist(SC_ACCESSIBILITY_SOURCE_ID, safetyCenterIssueId)
    }

    @Test
    fun testJobWithSafetyCenterDisabledDoesNotSendNotification() {
        setDeviceConfigPrivacyProperty(SAFETY_CENTER_ENABLED, false.toString())
        mAccessibilityServiceRule.enableService()
        runJobAndWaitUntilCompleted()
        assertEmptyNotification(permissionControllerPackage, ACCESSIBILITY_NOTIFICATION_ID)
    }

    @Test
    fun testJobWithSafetyCenterDisabledDoesNotSendIssueToSafetyCenter() {
        setDeviceConfigPrivacyProperty(SAFETY_CENTER_ENABLED, false.toString())
        mAccessibilityServiceRule.enableService()
        runJobAndWaitUntilCompleted()
        assertSafetyCenterIssueDoesNotExist(SC_ACCESSIBILITY_SOURCE_ID, safetyCenterIssueId)
    }

    @Test
    fun testNotificationClickOpenSafetyCenter() {
        mAccessibilityServiceRule.enableService()
        runJobAndWaitUntilCompleted()
        val statusBarNotification = getNotification(permissionControllerPackage,
            ACCESSIBILITY_NOTIFICATION_ID)
        Assert.assertNotNull(statusBarNotification)
        val contentIntent = statusBarNotification!!.notification.contentIntent
        contentIntent.send()
        assertSafetyCenterStarted()
    }

    private fun getAutomation(): UiAutomation {
        return instrumentation.getUiAutomation(
            UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES
        )
    }

    private fun runJobAndWaitUntilCompleted() {
        TestUtils.runJobAndWaitUntilCompleted(permissionControllerPackage, ACCESSIBILITY_JOB_ID,
            TIMEOUT_MILLIS, getAutomation())
    }

    /**
     * Reset the permission controllers state.
     */
    @Throws(Throwable::class)
    private fun resetPermissionController() {
        PermissionUtils.clearAppState(permissionControllerPackage)
        val currentUserId = Process.myUserHandle().identifier

        // Wait until jobs are cleared
        TestUtils.eventually({
            val dump = getJobSchedulerDump()
            for (job in dump!!.registeredJobs) {
                if (job.dump.sourceUserId == currentUserId &&
                    job.dump.sourcePackageName == permissionControllerPackage
                ) {
                    Assert.assertFalse(
                        job.dump.jobInfo.service.className.contains("AccessibilityJobService")
                    )
                }
            }
        }, TIMEOUT_MILLIS)

        runShellCommand(
            "cmd jobscheduler reset-execution-quota -u " +
                    "${Process.myUserHandle().identifier} $permissionControllerPackage"
        )

        context.sendBroadcast(Intent().apply {
            setClassName(permissionControllerPackage, AccessibilityOnBootReceiver)
            setFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            setPackage(permissionControllerPackage)
        })

        // Wait until jobs are set up
        TestUtils.eventually({
            val dump = getJobSchedulerDump()
            for (job in dump!!.registeredJobs) {
                if (job.dump.sourceUserId == currentUserId &&
                    job.dump.sourcePackageName == permissionControllerPackage &&
                    job.dump.jobInfo.service.className.contains("AccessibilityJobService")
                ) {
                    return@eventually
                }
            }
            Assert.fail("accessibility job not found")
        }, TIMEOUT_MILLIS)
    }

    @Throws(Exception::class)
    private fun getJobSchedulerDump(): JobSchedulerServiceDumpProto? {
        return ProtoUtils.getProto(
            getAutomation(),
            JobSchedulerServiceDumpProto::class.java,
            ProtoUtils.DUMPSYS_JOB_SCHEDULER
        )
    }

    companion object {
        private const val SC_ACCESSIBILITY_SOURCE_ID = "AndroidAccessibility"
        private const val ACCESSIBILITY_SOURCE_ENABLED = "sc_accessibility_source_enabled"
        private const val SAFETY_CENTER_ENABLED = "safety_center_is_enabled"
        private const val ACCESSIBILITY_LISTENER_ENABLED = "sc_accessibility_listener_enabled"

        private const val ACCESSIBILITY_JOB_ID = 6
        private const val ACCESSIBILITY_NOTIFICATION_ID = 4
        private const val TIMEOUT_MILLIS: Long = 10000

        private const val AccessibilityOnBootReceiver =
            "com.android.permissioncontroller.privacysources.AccessibilityOnBootReceiver"

        @get:ClassRule
        @JvmStatic
        val ctsNotificationListenerHelper = CtsNotificationListenerHelperRule(
            InstrumentationRegistry.getInstrumentation().targetContext)
    }
}
