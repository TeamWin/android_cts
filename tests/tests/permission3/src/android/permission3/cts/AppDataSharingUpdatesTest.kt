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

package android.permission3.cts

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.Intent.ACTION_REVIEW_APP_DATA_SHARING_UPDATES
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.os.Build
import android.permission3.cts.AppMetadata.createAppMetadataWithLocationSharingNoAds
import android.permission3.cts.AppMetadata.createAppMetadataWithNoSharing
import android.provider.DeviceConfig
import android.safetylabel.SafetyLabelConstants.PERMISSION_RATIONALE_ENABLED
import android.safetylabel.SafetyLabelConstants.SAFETY_LABEL_CHANGE_NOTIFICATIONS_ENABLED
import android.support.test.uiautomator.By
import androidx.test.filters.SdkSuppress
import com.android.compatibility.common.util.DeviceConfigStateChangerRule
import com.android.compatibility.common.util.SystemUtil.eventually
import com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity
import com.android.compatibility.common.util.SystemUtil.waitForBroadcasts
import com.android.modules.utils.build.SdkLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Tests the UI that displays information about apps' updates to their data sharing policies. */
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.UPSIDE_DOWN_CAKE, codeName = "UpsideDownCake")
class AppDataSharingUpdatesTest : BaseUsePermissionTest() {
    // TODO(b/263838456): Add tests for personal and work profile.

    private var activityManager: ActivityManager? = null

    @get:Rule
    val deviceConfigSafetyLabelChangeNotificationsEnabled =
        DeviceConfigStateChangerRule(
            context,
            DeviceConfig.NAMESPACE_PRIVACY,
            SAFETY_LABEL_CHANGE_NOTIFICATIONS_ENABLED,
            true.toString())

    @get:Rule
    val deviceConfigPlaceholderSafetyLabelUpdatesEnabled =
        DeviceConfigStateChangerRule(
            context,
            DeviceConfig.NAMESPACE_PRIVACY,
            PLACEHOLDER_SAFETY_LABEL_UPDATES_FLAG,
            false.toString())

    @get:Rule
    val deviceConfigPermissionRationaleEnabled =
        DeviceConfigStateChangerRule(
            context, DeviceConfig.NAMESPACE_PRIVACY, PERMISSION_RATIONALE_ENABLED, true.toString())

    @get:Rule
    val deviceConfigDataSharingUpdatesPeriod =
        DeviceConfigStateChangerRule(
            context,
            DeviceConfig.NAMESPACE_PRIVACY,
            PROPERTY_DATA_SHARING_UPDATE_PERIOD_MILLIS,
            "600000")

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

    @Before
    fun setup() {
        Assume.assumeTrue(
            "Data sharing updates page is only available on U+", SdkLevel.isAtLeastU())
        Assume.assumeFalse(isAutomotive)
        Assume.assumeFalse(isTv)
        Assume.assumeFalse(isWatch)

        activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

        installPackageViaSession(APP_APK_NAME_31, createAppMetadataWithNoSharing())
        waitForBroadcasts()
        installPackageViaSession(APP_APK_NAME_31, createAppMetadataWithLocationSharingNoAds())
        waitForBroadcasts()
    }

    @Test
    fun startActivityWithIntent_featuresEnabled_whenAppHasLocationGranted_showUpdates() {
        grantLocationPermission(APP_PACKAGE_NAME)

        startAppDataSharingUpdatesActivity()

        try {
            findView(By.descContains(DATA_SHARING_UPDATES), true)
            findView(By.textContains(DATA_SHARING_UPDATES_SUBTITLE), true)
            findView(By.textContains(UPDATES_IN_LAST_30_DAYS), true)
            findView(By.textContains(APP_PACKAGE_NAME_SUBSTRING), true)
            findView(By.textContains(DATA_SHARING_UPDATES_FOOTER_MESSAGE), true)
            findView(By.textContains(LEARN_ABOUT_DATA_SHARING), true)
        } finally {
            pressBack()
        }
    }

    @Test
    fun startActivityWithIntent_featuresEnabled_withPlaceholderData_showUpdates() {
        setDeviceConfigPrivacyProperty(PLACEHOLDER_SAFETY_LABEL_UPDATES_FLAG, true.toString())

        startAppDataSharingUpdatesActivity()

        try {
            findView(By.descContains(DATA_SHARING_UPDATES), true)
            findView(By.textContains(DATA_SHARING_UPDATES_SUBTITLE), true)
            findView(By.textContains(UPDATES_IN_LAST_30_DAYS), true)
            findView(By.textContains(DATA_SHARING_UPDATES_FOOTER_MESSAGE), true)
            findView(By.textContains(LEARN_ABOUT_DATA_SHARING), true)
        } finally {
            pressBack()
        }
    }

    @Test
    fun clickLearnMore_opensHelpCenter() {
        grantLocationPermission(APP_PACKAGE_NAME)
        startAppDataSharingUpdatesActivity()

        try {
            findView(By.descContains(DATA_SHARING_UPDATES), true)
            findView(By.textContains(LEARN_ABOUT_DATA_SHARING), true)
            findView(By.textContains(APP_PACKAGE_NAME_SUBSTRING), true)
            waitForIdle()

            click(By.textContains(LEARN_ABOUT_DATA_SHARING))
            waitForIdle()

            eventually {
                assertHelpCenterLinkClickSuccessful()
            }
        } finally {
            pressBack()
            pressBack()
        }
    }

    @Test
    fun clickSettingsGearInUpdate_opensAppPermissionsPage() {
        grantLocationPermission(APP_PACKAGE_NAME)
        startAppDataSharingUpdatesActivity()

        try {
            findView(By.descContains(DATA_SHARING_UPDATES), true)
            findView(By.textContains(UPDATES_IN_LAST_30_DAYS), true)
            findView(By.textContains(APP_PACKAGE_NAME_SUBSTRING), true)
            waitForIdle()

            click(By.res(SETTINGS_BUTTON_RES_ID))

            findView(By.descContains(APP_PERMISSIONS), true)
            findView(By.textContains(APP_PACKAGE_NAME), true)
        } finally {
            pressBack()
            pressBack()
        }
    }

    @Test
    fun startActivityWithIntent_featuresEnabled_whenAppDoesntHaveLocationGranted_showsNoUpdates() {
        startAppDataSharingUpdatesActivity()

        try {
            findView(By.descContains(DATA_SHARING_UPDATES), true)
            findView(By.textContains(DATA_SHARING_UPDATES_SUBTITLE), true)
            findView(By.textContains(DATA_SHARING_NO_UPDATES_MESSAGE), true)
            findView(By.textContains(APP_PACKAGE_NAME_SUBSTRING), false)
            findView(By.textContains(UPDATES_IN_LAST_30_DAYS), false)
            findView(By.textContains(DATA_SHARING_UPDATES_FOOTER_MESSAGE), true)
            findView(By.textContains(LEARN_ABOUT_DATA_SHARING), true)
        } finally {
            pressBack()
        }
    }

    @Test
    fun startActivityWithIntent_permissionRationaleDisabled_doesNotOpenDataSharingUpdatesPage() {
        setDeviceConfigPrivacyProperty(PERMISSION_RATIONALE_ENABLED, false.toString())

        startAppDataSharingUpdatesActivity()

        findView(By.descContains(DATA_SHARING_UPDATES), false)
    }

    @Test
    fun startActivityWithIntent_safetyLabelChangesDisabled_doesNotOpenDataSharingUpdatesPage() {
        setDeviceConfigPrivacyProperty(SAFETY_LABEL_CHANGE_NOTIFICATIONS_ENABLED, false.toString())

        startAppDataSharingUpdatesActivity()

        findView(By.descContains(DATA_SHARING_UPDATES), false)
    }

    @Test
    fun startActivityWithIntent_bothFeaturesDisabled_doesNotOpenDataSharingUpdatesPage() {
        setDeviceConfigPrivacyProperty(PERMISSION_RATIONALE_ENABLED, false.toString())
        setDeviceConfigPrivacyProperty(SAFETY_LABEL_CHANGE_NOTIFICATIONS_ENABLED, false.toString())

        startAppDataSharingUpdatesActivity()

        findView(By.descContains(DATA_SHARING_UPDATES), false)
    }

    /** Starts activity with intent [ACTION_REVIEW_APP_DATA_SHARING_UPDATES]. */
    private fun startAppDataSharingUpdatesActivity() {
        runWithShellPermissionIdentity {
            context.startActivity(
                Intent(ACTION_REVIEW_APP_DATA_SHARING_UPDATES).apply {
                    addFlags(FLAG_ACTIVITY_NEW_TASK)
                })
        }
    }

    private fun grantLocationPermission(packageName: String) {
        uiAutomation.grantRuntimePermission(
            packageName, android.Manifest.permission.ACCESS_FINE_LOCATION)
    }

    private fun assertHelpCenterLinkClickSuccessful() {
        runWithShellPermissionIdentity {
            val runningTasks = activityManager!!.getRunningTasks(1)

            assertFalse("Expected runningTasks to not be empty",
                    runningTasks.isEmpty())

            val taskInfo = runningTasks[0]
            val observedIntentAction = taskInfo.baseIntent.action
            val observedIntentDataString = taskInfo.baseIntent.dataString
            val observedIntentScheme: String? = taskInfo.baseIntent.scheme

            assertEquals("Unexpected intent action",
                    Intent.ACTION_VIEW,
                    observedIntentAction)

            assertFalse(observedIntentDataString.isNullOrEmpty())
            assertTrue(observedIntentDataString?.startsWith(EXPECTED_HELP_CENTER_URL)
                    ?: false)

            assertFalse(observedIntentScheme.isNullOrEmpty())
            assertEquals("https", observedIntentScheme)
        }
    }

    /** Companion object for [AppDataSharingUpdatesTest]. */
    companion object {
        private const val DATA_SHARING_UPDATES = "Data sharing updates"
        private const val DATA_SHARING_UPDATES_SUBTITLE =
            "These apps have changed the way they share location data. They may not have shared" +
                    " it before, or may now share it for advertising or marketing purposes."
        private const val DATA_SHARING_NO_UPDATES_MESSAGE = "No updates at this time"
        private const val UPDATES_IN_LAST_30_DAYS = "Updated within 30 days"
        private const val DATA_SHARING_UPDATES_FOOTER_MESSAGE =
            "The developers of these apps provided info about their data sharing practices and" +
                    " may update it over time.\n\nData sharing practices may vary based on your" +
                    " app version, use, region, and age."
        private const val LEARN_ABOUT_DATA_SHARING = "Learn about data sharing"
        private const val APP_PERMISSIONS = "App permissions"
        private const val PERMISSION_MANAGER = "Permission manager"
        private const val APP_PACKAGE_NAME_SUBSTRING = "android.permission3"
        private const val SETTINGS_BUTTON_RES_ID =
            "com.android.permissioncontroller:id/settings_button"
        private const val PLACEHOLDER_SAFETY_LABEL_UPDATES_FLAG =
            "placeholder_safety_label_updates_flag"
        private const val PROPERTY_DATA_SHARING_UPDATE_PERIOD_MILLIS =
            "data_sharing_update_period_millis"
        private const val PROPERTY_MAX_SAFETY_LABELS_PERSISTED_PER_APP =
            "max_safety_labels_persisted_per_app"

        private const val EXPECTED_HELP_CENTER_URL =
                "https://support.google.com/android?p=data_sharing"
    }
}
