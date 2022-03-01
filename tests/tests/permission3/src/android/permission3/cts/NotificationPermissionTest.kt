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

package android.permission3.cts

import android.Manifest.permission.POST_NOTIFICATIONS
import android.Manifest.permission.RECORD_AUDIO
import android.app.ActivityManager
import android.app.ActivityOptions
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.PackageManager.FLAG_PERMISSION_REVIEW_REQUIRED
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.os.Build
import android.os.Process
import android.os.UserHandle
import android.provider.Settings
import android.support.test.uiautomator.By
import androidx.test.filters.SdkSuppress
import com.android.compatibility.common.util.SystemUtil
import com.android.compatibility.common.util.SystemUtil.callWithShellPermissionIdentity
import com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch

const val EXTRA_DELETE_CHANNELS_ON_CLOSE = "extra_delete_channels_on_close"
const val EXTRA_CREATE_CHANNELS = "extra_create"
const val EXTRA_CREATE_CHANNELS_DELAYED = "extra_create_delayed"
const val EXTRA_REQUEST_PERMISSIONS = "extra_request_permissions"
const val EXTRA_REQUEST_PERMISSIONS_DELAYED = "extra_request_permissions_delayed"
const val ACTIVITY_NAME = "CreateNotificationChannelsActivity"
const val ACTIVITY_LABEL = "CreateNotif"
const val ALLOW = "to send you"
const val CONTINUE_ALLOW = "to continue sending you"
const val INTENT_ACTION = "usepermission.createchannels.MAIN"
const val BROADCAST_ACTION = "usepermission.createchannels.BROADCAST"
const val NOTIFICATION_PERMISSION_ENABLED = "notification_permission_enabled"
const val DELAY_MS = 5000L

@SdkSuppress(minSdkVersion = Build.VERSION_CODES.TIRAMISU, codeName = "Tiramisu")
class NotificationPermissionTest : BaseUsePermissionTest() {

    private val cr = callWithShellPermissionIdentity {
        context.createContextAsUser(UserHandle.SYSTEM, 0).contentResolver
    }
    private var previousEnableState = 0
    private var countDown: CountDownLatch = CountDownLatch(1)
    private var allowedGroups = listOf<String>()
    private val receiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            allowedGroups = intent?.getStringArrayListExtra(
                PackageManager.EXTRA_REQUEST_PERMISSIONS_RESULTS) ?: emptyList()
            countDown.countDown()
        }
    }

    @Before
    fun setLatchAndEnablePermission() {
        runWithShellPermissionIdentity {
            previousEnableState = Settings.Secure.getInt(cr, NOTIFICATION_PERMISSION_ENABLED, 0)
            Settings.Secure.putInt(cr, NOTIFICATION_PERMISSION_ENABLED, 1)
        }
        countDown = CountDownLatch(1)
        allowedGroups = listOf()
        context.registerReceiver(receiver, IntentFilter(BROADCAST_ACTION))
    }

    @After
    fun resetPermissionAndRemoveReceiver() {
        runWithShellPermissionIdentity {
            Settings.Secure.putInt(cr, NOTIFICATION_PERMISSION_ENABLED, previousEnableState)
        }
        context.unregisterReceiver(receiver)
    }

    @Test
    fun notificationPermissionAddedForLegacyApp() {
        installPackage(APP_APK_PATH_CREATE_NOTIFICATION_CHANNELS_31, expectSuccess = true)
        runWithShellPermissionIdentity {
            Assert.assertTrue("SDK < 32 apps should have POST_NOTIFICATIONS added implicitly",
                context.packageManager.getPackageInfo(APP_PACKAGE_NAME,
                    PackageManager.GET_PERMISSIONS).requestedPermissions
                    .contains(POST_NOTIFICATIONS))
        }
    }

    @Test
    fun notificationPermissionIsNotImplicitlyAddedTo33Apps() {
        installPackage(APP_APK_PATH_LATEST_NONE, expectSuccess = true)
        runWithShellPermissionIdentity {
            val requestedPerms = context.packageManager.getPackageInfo(APP_PACKAGE_NAME,
                    PackageManager.GET_PERMISSIONS).requestedPermissions
            Assert.assertTrue("SDK >= 33 apps should NOT have POST_NOTIFICATIONS added implicitly",
                    requestedPerms == null || !requestedPerms.contains(POST_NOTIFICATIONS))
        }
    }

    @Test
    fun reviewRequiredClearedForTAppsOnLaunch() {
        installPackage(APP_APK_PATH_CREATE_NOTIFICATION_CHANNELS_33, expectSuccess = true)
        setReviewRequired()
        assertNotificationReviewRequiredState(shouldBeSet = true)
        launchApp()
        assertNotificationReviewRequiredState(shouldBeSet = false)
    }

    @Test
    fun notificationPromptShowsForLegacyAppAfterCreatingNotificationChannels() {
        installPackage(APP_APK_PATH_CREATE_NOTIFICATION_CHANNELS_31, expectSuccess = true)
        setReviewRequired()
        launchApp()
        clickPermissionRequestAllowButton()
    }

    @Test
    fun notificationPromptShowsForLegacyAppWithNotificationChannelsOnStart() {
        installPackage(APP_APK_PATH_CREATE_NOTIFICATION_CHANNELS_31, expectSuccess = true)
        setReviewRequired()
        // create channels, then leave the app
        launchApp()
        killTestApp()
        launchApp()
        waitFindObject(By.textContains(CONTINUE_ALLOW))
        clickPermissionRequestAllowButton()
    }

    @Test
    fun nonReviewRequiredLegacyAppsDontShowContinuePrompt() {
        installPackage(APP_APK_PATH_CREATE_NOTIFICATION_CHANNELS_31, expectSuccess = true)
        setReviewRequired(false)
        launchApp()
        waitFindObject(By.textContains(ALLOW))
    }

    @Test
    fun notificationPromptDoesNotShowForLegacyAppWithNoNotificationChannels() {
        installPackage(APP_APK_PATH_CREATE_NOTIFICATION_CHANNELS_31, expectSuccess = true)
        setReviewRequired()
        launchApp(createChannels = false)
        try {
            clickPermissionRequestAllowButton()
            Assert.fail("Expected not to find permission request dialog")
        } catch (expected: RuntimeException) {
            // Do nothing
        }
    }

    @Test
    fun notificationPromptDoesNotShowForNonLauncherIntentCategoryLaunches() {
        installPackage(APP_APK_PATH_CREATE_NOTIFICATION_CHANNELS_31, expectSuccess = true)
        setReviewRequired()
        // create channels, then leave the app
        launchApp()
        killTestApp()
        launchApp(launcherCategory = false)
        try {
            clickPermissionRequestAllowButton()
            Assert.fail("Expected not to find permission request dialog")
        } catch (expected: RuntimeException) {
            // Do nothing
        }
    }

    @Test
    fun notificationPromptDoesNotShowForNonMainIntentActionLaunches() {
        installPackage(APP_APK_PATH_CREATE_NOTIFICATION_CHANNELS_31, expectSuccess = true)
        setReviewRequired()
        // create channels, then leave the app
        launchApp()
        killTestApp()
        launchApp(mainIntent = false)
        try {
            clickPermissionRequestAllowButton()
            Assert.fail("Expected not to find permission request dialog")
        } catch (expected: RuntimeException) {
            // Do nothing
        }
    }

    @Test
    fun notificationPromptShowsIfActivityOptionSet() {
        installPackage(APP_APK_PATH_CREATE_NOTIFICATION_CHANNELS_31, expectSuccess = true)
        setReviewRequired()
        // create channels, then leave the app
        launchApp()
        killTestApp()
        launchApp(mainIntent = false, isEligibleForPromptOption = true)
        clickPermissionRequestAllowButton()
    }

    @Test
    fun reviewRequiredNotClearedOnNonLauncherIntentCategoryLaunches() {
        installPackage(APP_APK_PATH_CREATE_NOTIFICATION_CHANNELS_33, expectSuccess = true)
        setReviewRequired()
        launchApp(launcherCategory = false)
        assertNotificationReviewRequiredState(true)
    }

    @Test
    fun reviewRequiredNotClearedOnNonMainIntentActionLaunches() {
        installPackage(APP_APK_PATH_CREATE_NOTIFICATION_CHANNELS_33, expectSuccess = true)
        setReviewRequired()
        launchApp(mainIntent = false)
        assertNotificationReviewRequiredState(true)
    }

    @Test
    fun reviewRequiredClearedIfActivityOptionSet() {
        installPackage(APP_APK_PATH_CREATE_NOTIFICATION_CHANNELS_33, expectSuccess = true)
        setReviewRequired()
        launchApp(isEligibleForPromptOption = true)
        assertNotificationReviewRequiredState(false)
    }

    @Test
    fun notificationGrantedAndReviewRequiredClearedOnLegacyGrant() {
        installPackage(APP_APK_PATH_CREATE_NOTIFICATION_CHANNELS_31, expectSuccess = true)
        setReviewRequired()
        launchApp()
        clickPermissionRequestAllowButton()
        assertAppPermissionGrantedState(POST_NOTIFICATIONS, granted = true)
        assertNotificationReviewRequiredState(shouldBeSet = false)
    }

    @Test
    fun notificationReviewRequiredClearedOnLegacyDeny() {
        installPackage(APP_APK_PATH_CREATE_NOTIFICATION_CHANNELS_31, expectSuccess = true)
        setReviewRequired()
        launchApp()
        clickPermissionRequestDenyButton()
        waitForIdle()
        assertNotificationReviewRequiredState(shouldBeSet = false)
    }

    @Test
    fun nonSystemServerPackageCannotShowPromptForOtherPackage() {
        installPackage(APP_APK_PATH_CREATE_NOTIFICATION_CHANNELS_31, expectSuccess = true)
        runWithShellPermissionIdentity {
            val grantPermission = Intent(PackageManager.ACTION_REQUEST_PERMISSIONS_FOR_OTHER)
            grantPermission.putExtra(Intent.EXTRA_PACKAGE_NAME, APP_PACKAGE_NAME)
            grantPermission.putExtra(PackageManager.EXTRA_REQUEST_PERMISSIONS_NAMES,
                arrayOf(POST_NOTIFICATIONS))
            grantPermission.setPackage(context.packageManager.permissionControllerPackageName)
            grantPermission.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(grantPermission)
        }
        try {
            clickPermissionRequestAllowButton()
            Assert.fail("Expected not to find permission request dialog")
        } catch (expected: RuntimeException) {
            // Do nothing
        }
    }

    @Test
    fun mergeAppPermissionRequestIntoNotificationAndVerifyResult() {
        installPackage(APP_APK_PATH_CREATE_NOTIFICATION_CHANNELS_31, expectSuccess = true)
        launchApp(requestPermissionsDelayed = true)
        Thread.sleep(DELAY_MS)
        clickPermissionRequestAllowButton()
        assertAppPermissionGrantedState(POST_NOTIFICATIONS, granted = true)
        clickPermissionRequestAllowForegroundButton()
        assertAppPermissionGrantedState(RECORD_AUDIO, granted = true)
        countDown.await()
        // Result should contain only the microphone request
        Assert.assertEquals(listOf(RECORD_AUDIO), allowedGroups)
    }

    @Test
    fun mergeNotificationRequestIntoAppPermissionRequestAndVerifyResult() {
        installPackage(APP_APK_PATH_CREATE_NOTIFICATION_CHANNELS_31, expectSuccess = true)
        launchApp(createChannels = false, createChannelsDelayed = true, requestPermissions = true)
        Thread.sleep(DELAY_MS)
        clickPermissionRequestAllowForegroundButton()
        assertAppPermissionGrantedState(RECORD_AUDIO, granted = true)
        clickPermissionRequestAllowButton()
        assertAppPermissionGrantedState(POST_NOTIFICATIONS, granted = true)
        countDown.await()
        // Result should contain only the microphone request
        Assert.assertEquals(listOf(RECORD_AUDIO), allowedGroups)
    }

    // Enable this test once droidfood code is removed
    @Test
    fun newlyInstalledLegacyAppsDontHaveReviewRequired() {
        installPackage(APP_APK_PATH_CREATE_NOTIFICATION_CHANNELS_31, expectSuccess = true)
        runWithShellPermissionIdentity {
            Assert.assertEquals("expect REVIEW_REQUIRED to not be set", 0, context.packageManager
                .getPermissionFlags(POST_NOTIFICATIONS, APP_PACKAGE_NAME, Process.myUserHandle())
                and FLAG_PERMISSION_REVIEW_REQUIRED)
        }
    }

    @Test
    fun newlyInstalledTAppsDontHaveReviewRequired() {
        installPackage(APP_APK_PATH_CREATE_NOTIFICATION_CHANNELS_33, expectSuccess = true)
        runWithShellPermissionIdentity {
            Assert.assertEquals("expect REVIEW_REQUIRED to not be set", 0, context.packageManager
                .getPermissionFlags(POST_NOTIFICATIONS, APP_PACKAGE_NAME, Process.myUserHandle())
                and FLAG_PERMISSION_REVIEW_REQUIRED)
        }
    }

    @Test
    fun reviewRequiredTAppsShowContinueMessage() {
        installPackage(APP_APK_PATH_CREATE_NOTIFICATION_CHANNELS_33, expectSuccess = true)
        setReviewRequired(true)
        assertNotificationReviewRequiredState(true)
        launchApp(requestPermissions = true)
        waitFindObject(By.textContains(CONTINUE_ALLOW))
    }

    @Test
    fun nonReviewRequiredTAppsShowAllowMessage() {
        installPackage(APP_APK_PATH_CREATE_NOTIFICATION_CHANNELS_33, expectSuccess = true)
        assertNotificationReviewRequiredState(false)
        launchApp(requestPermissions = true)
        waitFindObject(By.textContains(ALLOW))
    }

    private fun assertAppPermissionGrantedState(permission: String, granted: Boolean) {
        SystemUtil.eventually {
            runWithShellPermissionIdentity {
                Assert.assertEquals("Expected $permission to be granted", context.packageManager
                        .checkPermission(permission, APP_PACKAGE_NAME), PERMISSION_GRANTED)
            }
        }
    }

    private fun assertNotificationReviewRequiredState(shouldBeSet: Boolean) {
        val flagSet = callWithShellPermissionIdentity {
            (context.packageManager.getPermissionFlags(POST_NOTIFICATIONS,
                APP_PACKAGE_NAME, Process.myUserHandle()) and FLAG_PERMISSION_REVIEW_REQUIRED) != 0
        }
        Assert.assertEquals("Unexpected REVIEW_REQUIRED state for POST_NOTIFICATIONS: ",
            shouldBeSet, flagSet)
    }

    private fun setReviewRequired(set: Boolean = true) {
        val flag = if (set) {
            FLAG_PERMISSION_REVIEW_REQUIRED
        } else {
            0
        }
        runWithShellPermissionIdentity {
            context.packageManager.updatePermissionFlags(POST_NOTIFICATIONS, APP_PACKAGE_NAME,
                FLAG_PERMISSION_REVIEW_REQUIRED, flag, Process.myUserHandle())
        }
    }

    private fun launchApp(
        createChannels: Boolean = true,
        createChannelsDelayed: Boolean = false,
        deleteChannels: Boolean = false,
        requestPermissions: Boolean = false,
        requestPermissionsDelayed: Boolean = false,
        launcherCategory: Boolean = true,
        mainIntent: Boolean = true,
        isEligibleForPromptOption: Boolean = false
    ) {
        val intent = if (mainIntent && launcherCategory) {
            packageManager.getLaunchIntentForPackage(APP_PACKAGE_NAME)!!
        } else if (mainIntent) {
            Intent(Intent.ACTION_MAIN)
        } else {
            Intent(INTENT_ACTION)
        }

        intent.`package` = APP_PACKAGE_NAME
        intent.putExtra(EXTRA_CREATE_CHANNELS, createChannels)
        if (!createChannels) {
            intent.putExtra(EXTRA_CREATE_CHANNELS_DELAYED, createChannelsDelayed)
        }
        intent.putExtra(EXTRA_DELETE_CHANNELS_ON_CLOSE, deleteChannels)
        intent.putExtra(EXTRA_REQUEST_PERMISSIONS, requestPermissions)
        if (!requestPermissions) {
            intent.putExtra(EXTRA_REQUEST_PERMISSIONS_DELAYED, requestPermissionsDelayed)
        }
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK

        val options = ActivityOptions.makeBasic()
        options.isEligibleForLegacyPermissionPrompt = isEligibleForPromptOption
        context.startActivity(intent, options.toBundle())

        waitFindObject(By.textContains(ACTIVITY_LABEL))
        waitForIdle()
    }

    private fun killTestApp() {
        pressBack()
        pressBack()
        runWithShellPermissionIdentity {
            val am = context.getSystemService(ActivityManager::class.java)!!
            am.forceStopPackage(APP_PACKAGE_NAME)
        }
        waitForIdle()
    }
}