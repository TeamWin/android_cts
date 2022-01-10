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
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.PackageManager.FLAG_PERMISSION_REVIEW_REQUIRED
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.os.Process
import android.os.UserHandle
import android.provider.Settings
import com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import java.util.concurrent.CountDownLatch

const val EXTRA_DELETE_CHANNELS_ON_CLOSE = "extra_delete_channels_on_close"
const val EXTRA_CREATE_CHANNELS = "extra_create"
const val EXTRA_CREATE_CHANNELS_DELAYED = "extra_create_delayed"
const val EXTRA_REQUEST_PERMISSIONS = "extra_request_permissions"
const val EXTRA_REQUEST_PERMISSIONS_DELAYED = "extra_request_permissions_delayed"
const val ACTIVITY_NAME = "CreateNotificationChannelsActivity"
const val INTENT_ACTION = "usepermission.createchannels.MAIN"
const val BROADCAST_ACTION = "usepermission.createchannels.BROADCAST"
const val NOTIFICATION_PERMISSION_ENABLED = "notification_permission_enabled"
const val DELAY_MS = 2000L

class NotificationPermissionTest : BaseUsePermissionTest() {

    private val cr = context.createContextAsUser(UserHandle.SYSTEM, 0).contentResolver
    private var previousEnableState = 0
    private var countDown: CountDownLatch = CountDownLatch(1)
    private var allowedGroups = listOf<String>()
    private val receiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            countDown.countDown()
            allowedGroups = intent?.getStringArrayListExtra(
                PackageManager.EXTRA_REQUEST_PERMISSIONS_RESULTS) ?: emptyList()
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

    // TODO ntmyren: enable when SDK 33 is a valid target
    @Test
    @Ignore
    fun notificationPermissionIsNotImplicitlyAddedTo33Apps() {
        installPackage(APP_APK_PATH_CREATE_NOTIFICATION_CHANNELS_33, expectSuccess = true)
        runWithShellPermissionIdentity {
            Assert.assertFalse("SDK < 33 apps should NOT have POST_NOTIFICATIONS added implicitly",
                context.packageManager.getPackageInfo(APP_PACKAGE_NAME,
                    PackageManager.GET_PERMISSIONS).requestedPermissions
                    .contains(POST_NOTIFICATIONS))
        }
    }

    // TODO ntmyren: enable when SDK 33 is a valid target
    @Test
    @Ignore
    fun reviewRequiredClearedForTAppsOnLaunch() {
        installPackage(APP_APK_PATH_CREATE_NOTIFICATION_CHANNELS_33, expectSuccess = true)
        runWithShellPermissionIdentity {
            context.packageManager.updatePermissionFlags(POST_NOTIFICATIONS, APP_PACKAGE_NAME,
                FLAG_PERMISSION_REVIEW_REQUIRED, FLAG_PERMISSION_REVIEW_REQUIRED,
                Process.myUserHandle())
        }
        launchApp()
        waitForIdle()
        assertNotificationReviewRequiredCleared()
    }

    @Test
    fun notificationPromptShowsForLegacyAppAfterCreatingNotificationChannels() {
        installPackage(APP_APK_PATH_CREATE_NOTIFICATION_CHANNELS_31, expectSuccess = true)
        launchApp()
        waitForIdle()
        clickPermissionRequestAllowButton()
    }

    @Test
    fun notificationPromptShowsForLegacyAppWithNotificationChannelsOnStart() {
        installPackage(APP_APK_PATH_CREATE_NOTIFICATION_CHANNELS_31, expectSuccess = true)
        launchApp()
        waitForIdle()
        pressBack()
        pressBack()
        waitForIdle()
        launchApp()
        clickPermissionRequestAllowButton()
    }

    @Test
    fun notificationPromptDoesNotShowForLegacyAppWithNoNotificationChannels() {
        installPackage(APP_APK_PATH_CREATE_NOTIFICATION_CHANNELS_31, expectSuccess = true)
        launchApp(createChannels = false)
        try {
            clickPermissionRequestAllowButton()
            Assert.fail("Expected not to find permission request dialog")
        } catch (expected: RuntimeException) {
            // Do nothing
        }
    }

    @Test
    fun notificationGrantedAndReviewRequiredClearedOnLegacyGrant() {
        installPackage(APP_APK_PATH_CREATE_NOTIFICATION_CHANNELS_31, expectSuccess = true)
        launchApp()
        clickPermissionRequestAllowButton()
        assertAppPermissionGrantedState(POST_NOTIFICATIONS, granted = true)
        assertNotificationReviewRequiredCleared()
    }

    @Test
    fun notificationReviewRequiredClearedOnLegacyDeny() {
        installPackage(APP_APK_PATH_CREATE_NOTIFICATION_CHANNELS_31, expectSuccess = true)
        launchApp()
        clickPermissionRequestDenyButton()
        assertNotificationReviewRequiredCleared()
    }

    @Test
    fun ensureNonSystemServerPackageCannotShowPromptForOtherPackage() {
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

    private fun assertAppPermissionGrantedState(permission: String, granted: Boolean) {
        runWithShellPermissionIdentity {
            Assert.assertEquals("Expected $permission to be granted", context.packageManager
                .checkPermission(permission, APP_PACKAGE_NAME), PERMISSION_GRANTED)
        }
    }

    private fun assertNotificationReviewRequiredCleared() {
        runWithShellPermissionIdentity {
            val permFlags = context.packageManager
                .getPermissionFlags(POST_NOTIFICATIONS, APP_PACKAGE_NAME, Process.myUserHandle())
            Assert.assertEquals("Expected REVIEW_REQUIRED to bel cleared for POST_NOTIFICATIONS",
                permFlags and FLAG_PERMISSION_REVIEW_REQUIRED, 0)
        }
    }

    private fun launchApp(
        createChannels: Boolean = true,
        createChannelsDelayed: Boolean = false,
        deleteChannels: Boolean = false,
        requestPermissions: Boolean = false,
        requestPermissionsDelayed: Boolean = false
    ) {
        val intent = Intent(INTENT_ACTION)
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

        context.startActivity(intent)
    }
}