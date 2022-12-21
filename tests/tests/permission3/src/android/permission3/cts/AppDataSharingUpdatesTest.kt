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
import android.safetylabel.SafetyLabelConstants.SAFETY_LABEL_CHANGE_NOTIFICATIONS_ENABLED
import android.support.test.uiautomator.By
import androidx.test.filters.SdkSuppress
import com.android.compatibility.common.util.DeviceConfigStateChangerRule
import com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity
import com.android.modules.utils.build.SdkLevel
import org.junit.Assert.assertNull
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
    val deviceConfigPermissionRationaleEnabled =
        DeviceConfigStateChangerRule(
            context,
            DeviceConfig.NAMESPACE_PRIVACY,
            PRIVACY_PERMISSION_RATIONALE_ENABLED,
            true.toString())

    @Before
    fun setup() {
        Assume.assumeTrue(
            "Data sharing updates page is only available on U+", SdkLevel.isAtLeastU())
        Assume.assumeFalse(isAutomotive)
        Assume.assumeFalse(isTv)
        Assume.assumeFalse(isWatch)
    }

    @Test
    fun startActivityWithIntent_featuresEnabled_opensDataSharingUpdatesPage() {
        runWithShellPermissionIdentity {
            context.startActivity(
                Intent(ACTION_REVIEW_APP_DATA_SHARING_UPDATES).apply {
                    addFlags(FLAG_ACTIVITY_NEW_TASK)
                })
        }

        waitFindObject(By.textContains("Data Sharing updates"))
    }

    @Test
    fun startActivityWithIntent_permissionRationaleDisabled_doesNotOpenDataSharingUpdatesPage() {
        setDeviceConfigPrivacyProperty(PRIVACY_PERMISSION_RATIONALE_ENABLED, false.toString())
        runWithShellPermissionIdentity {
            context.startActivity(
                Intent(ACTION_REVIEW_APP_DATA_SHARING_UPDATES).apply {
                    addFlags(FLAG_ACTIVITY_NEW_TASK)
                })
        }

        val dataSharingUpdates = waitFindObjectOrNull(By.textContains("Data Sharing updates"))
        assertNull(dataSharingUpdates)
    }

    @Test
    fun startActivityWithIntent_safetyLabelChangesDisabled_doesNotOpenDataSharingUpdatesPage() {
        setDeviceConfigPrivacyProperty(SAFETY_LABEL_CHANGE_NOTIFICATIONS_ENABLED, false.toString())
        runWithShellPermissionIdentity {
            context.startActivity(
                Intent(ACTION_REVIEW_APP_DATA_SHARING_UPDATES).apply {
                    addFlags(FLAG_ACTIVITY_NEW_TASK)
                })
        }

        val dataSharingUpdates = waitFindObjectOrNull(By.textContains("Data Sharing updates"))
        assertNull(dataSharingUpdates)
    }

    @Test
    fun startActivityWithIntent_bothFeaturesDisabled_doesNotOpenDataSharingUpdatesPage() {
        setDeviceConfigPrivacyProperty(PRIVACY_PERMISSION_RATIONALE_ENABLED, false.toString())
        setDeviceConfigPrivacyProperty(SAFETY_LABEL_CHANGE_NOTIFICATIONS_ENABLED, false.toString())
        runWithShellPermissionIdentity {
            context.startActivity(
                Intent(ACTION_REVIEW_APP_DATA_SHARING_UPDATES).apply {
                    addFlags(FLAG_ACTIVITY_NEW_TASK)
                })
        }

        val dataSharingUpdates = waitFindObjectOrNull(By.textContains("Data Sharing updates"))
        assertNull(dataSharingUpdates)
    }

    /** Companion object for [AppDataSharingUpdatesTest]. */
    companion object {
        /**
         * [DeviceConfig] flag controlling to show permission rationale in permission settings and
         * grant dialog.
         */
        private const val PRIVACY_PERMISSION_RATIONALE_ENABLED =
            "privacy_permission_rationale_enabled"
    }
}
