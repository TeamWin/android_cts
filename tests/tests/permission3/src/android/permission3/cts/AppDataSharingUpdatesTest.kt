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

import android.content.Intent
import android.content.Intent.ACTION_REVIEW_APP_DATA_SHARING_UPDATES
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.os.Build
import android.provider.DeviceConfig
import android.safetylabel.SafetyLabelConstants.PERMISSION_RATIONALE_ENABLED
import android.safetylabel.SafetyLabelConstants.SAFETY_LABEL_CHANGE_NOTIFICATIONS_ENABLED
import android.support.test.uiautomator.By
import androidx.test.filters.SdkSuppress
import com.android.compatibility.common.util.DeviceConfigStateChangerRule
import com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity
import com.android.modules.utils.build.SdkLevel
import org.junit.Assume
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Tests the UI that displays information about apps' updates to their data sharing policies. */
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.UPSIDE_DOWN_CAKE, codeName = "UpsideDownCake")
class AppDataSharingUpdatesTest : BasePermissionTest() {

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
            true.toString())

    @get:Rule
    val deviceConfigPermissionRationaleEnabled =
        DeviceConfigStateChangerRule(
            context, DeviceConfig.NAMESPACE_PRIVACY, PERMISSION_RATIONALE_ENABLED, true.toString())

    @Before
    fun setup() {
        Assume.assumeTrue(
            "Data sharing updates page is only available on U+", SdkLevel.isAtLeastU())
        Assume.assumeFalse(isAutomotive)
        Assume.assumeFalse(isTv)
        Assume.assumeFalse(isWatch)
    }

    @Test
    fun startActivityWithIntent_featuresEnabled_withPlaceholderData_showUpdates() {
        startAppDataSharingUpdatesActivity()

        try {
            findView(By.descContains(DATA_SHARING_UPDATES), true)
            findView(By.textContains(DATA_SHARING_UPDATES_SUBTITLE), true)
            findView(By.textContains(UPDATES_IN_LAST_30_DAYS), true)
            findView(By.textContains(DATA_SHARING_UPDATES_FOOTER_MESSAGE), true)
            findView(By.textContains(LEARN_MORE_ABOUT_DATA_SHARING), true)
        } finally {
            pressBack()
        }
    }

    // TODO(b/263838996): Check that Safety Label Help Center is opened.
    @Test
    fun clickLearnMore_opensPermissionManager() {
        startAppDataSharingUpdatesActivity()

        try {
            findView(By.descContains(DATA_SHARING_UPDATES), true)
            findView(By.textContains(LEARN_MORE_ABOUT_DATA_SHARING), true)
            waitForIdle()

            click(By.textContains(LEARN_MORE_ABOUT_DATA_SHARING))

            findView(By.descContains(PERMISSION_MANAGER), true)
        } finally {
            pressBack()
            pressBack()
        }
    }

    // TODO(b/261665490): Update this test to check app name in update and app permissions page
    //  once we no longer use placeholder data.
    @Test
    fun clickSettingsGearInUpdate_opensAppPermissionsPage() {
        startAppDataSharingUpdatesActivity()

        try {
            findView(By.descContains(DATA_SHARING_UPDATES), true)
            findView(By.textContains(UPDATES_IN_LAST_30_DAYS), true)
            waitForIdle()

            click(By.res(SETTINGS_BUTTON_RES_ID))

            findView(By.descContains(APP_PERMISSIONS), true)
        } finally {
            pressBack()
            pressBack()
        }
    }

    @Test
    fun startActivityWithIntent_featuresEnabled_withoutPlaceholderData_showsNoUpdates() {
        setDeviceConfigPrivacyProperty(PLACEHOLDER_SAFETY_LABEL_UPDATES_FLAG, false.toString())

        startAppDataSharingUpdatesActivity()

        try {
            findView(By.descContains(DATA_SHARING_UPDATES), true)
            findView(By.textContains(DATA_SHARING_NO_UPDATES_SUBTITLE), true)
            findView(By.textContains(DATA_SHARING_UPDATES_SUBTITLE), false)
            findView(By.textContains(UPDATES_IN_LAST_30_DAYS), false)
            findView(By.textContains(DATA_SHARING_UPDATES_FOOTER_MESSAGE), false)
            findView(By.textContains(LEARN_MORE_ABOUT_DATA_SHARING), false)
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

    /** Companion object for [AppDataSharingUpdatesTest]. */
    companion object {
        private const val DATA_SHARING_UPDATES = "Data sharing updates"
        private const val DATA_SHARING_UPDATES_SUBTITLE =
            "These apps have provided updates on data sharing practices. Review these updates and" +
                " modify app permissions if necessary."
        private const val DATA_SHARING_NO_UPDATES_SUBTITLE = "No apps have provided recent updates."
        private const val UPDATES_IN_LAST_30_DAYS = "Updated in the last 30 days"
        private const val DATA_SHARING_UPDATES_FOOTER_MESSAGE =
            "The developers of the apps listed here provided this information about their sharing" +
                " practices and may update it over time.\nData privacy and security practices" +
                " may vary based on your use, region, and age."
        private const val LEARN_MORE_ABOUT_DATA_SHARING = "Learn more about data sharing"
        private const val APP_PERMISSIONS = "App permissions"
        private const val PERMISSION_MANAGER = "Permission manager"
        private const val SETTINGS_BUTTON_RES_ID =
            "com.android.permissioncontroller:id/settings_button"
        private const val PLACEHOLDER_SAFETY_LABEL_UPDATES_FLAG =
            "placeholder_safety_label_updates_flag"
    }
}
