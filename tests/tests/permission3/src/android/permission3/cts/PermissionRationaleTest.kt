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

import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.DeviceConfig
import android.safetylabel.SafetyLabelConstants.PERMISSION_RATIONALE_ENABLED
import android.support.test.uiautomator.By
import android.text.Spanned
import android.text.style.ClickableSpan
import android.view.View
import androidx.test.filters.SdkSuppress
import com.android.compatibility.common.util.DeviceConfigStateChangerRule
import com.android.compatibility.common.util.SystemUtil
import com.android.modules.utils.build.SdkLevel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Permission rationale activity tests. Permission rationale is only available on U+ */
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.UPSIDE_DOWN_CAKE, codeName = "UpsideDownCake")
class PermissionRationaleTest : BaseUsePermissionTest() {

    private var activityManager: ActivityManager? = null

    @get:Rule
    val deviceConfigPermissionRationaleEnabled =
        DeviceConfigStateChangerRule(
            context,
            DeviceConfig.NAMESPACE_PRIVACY,
            PERMISSION_RATIONALE_ENABLED,
            true.toString())

    // TODO(b/257293222): Remove when hooking up PackageManager APIs
    @get:Rule
    val deviceConfigTestSafetyLabelDataEnabled =
        DeviceConfigStateChangerRule(
            context,
            DeviceConfig.NAMESPACE_PRIVACY,
            PRIVACY_PLACEHOLDER_SAFETY_LABEL_DATA_ENABLED,
            true.toString())

    @Before
    fun setup() {
        Assume.assumeTrue("Permission rationale is only available on U+", SdkLevel.isAtLeastU())
        Assume.assumeFalse(isAutomotive)
        Assume.assumeFalse(isTv)
        Assume.assumeFalse(isWatch)

        activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

        installPackage(APP_APK_PATH_TEST_STORE_APP)

        // TODO(b/257293222): Update/remove when hooking up PackageManager APIs
        installPackage(APP_APK_PATH_31, installSource = TEST_STORE_PACKAGE_NAME)

        assertAppHasPermission(Manifest.permission.ACCESS_FINE_LOCATION, false)
    }

    @After
    fun uninstallApps() {
        // APP_PACKAGE_NAME in uninstalled in BaseUsePermissionTest which this class extends
        uninstallPackage(TEST_STORE_PACKAGE_NAME, requireSuccess = false)
    }

    @Test
    fun startsPermissionRationaleActivity() {
        navigateToPermissionRationaleActivity()

        assertPermissionRationaleActivityTitleIsVisible(true)
    }

    @Test
    fun linksToInstallSource() {
        navigateToPermissionRationaleActivity()

        assertPermissionRationaleActivityTitleIsVisible(true)

        clickInstallSourceLink()

        SystemUtil.eventually {
            assertStoreLinkClickSuccessful(installerPackageName = TEST_STORE_PACKAGE_NAME)
        }
    }

    private fun navigateToPermissionRationaleActivity() {
        requestAppPermissionsForNoResult(Manifest.permission.ACCESS_FINE_LOCATION) {
            assertPermissionRationaleOnGrantDialogIsVisible(true)
            clickPermissionRationaleViewInGrantDialog()
        }
    }

    private fun clickInstallSourceLink() {
        waitForIdle()
        SystemUtil.eventually {
            // UiObject2 doesn't expose CharSequence.
            val node = uiAutomation.rootInActiveWindow.findAccessibilityNodeInfosByViewId(
                "com.android.permissioncontroller:id/purpose_message"
            )[0]
            assertTrue(node.isVisibleToUser)
            val text = node.text as Spanned
            val clickableSpan = text.getSpans(0, text.length, ClickableSpan::class.java)[0]
            // We could pass in null here in Java, but we need an instance in Kotlin.
            clickableSpan.onClick(View(context))
        }
        waitForIdle()
    }

    private fun assertPermissionRationaleOnGrantDialogIsVisible(expected: Boolean) {
        findView(By.res(GRANT_DIALOG_PERMISSION_RATIONALE_CONTAINER_VIEW), expected = expected)
    }

    private fun assertPermissionRationaleActivityTitleIsVisible(expected: Boolean) {
        findView(By.res(PERMISSION_RATIONALE_ACTIVITY_TITLE_VIEW), expected = expected)
    }

    private fun assertStoreLinkClickSuccessful(
        installerPackageName: String,
        packageName: String? = null
    ) {
        SystemUtil.runWithShellPermissionIdentity {
            val runningTasks = activityManager!!.getRunningTasks(1)

            assertFalse("Expected runningTasks to not be empty",
                runningTasks.isEmpty())

            val taskInfo = runningTasks[0]
            val observedIntentAction = taskInfo.baseIntent.action
            val observedPackageName = taskInfo.baseIntent.getStringExtra(Intent.EXTRA_PACKAGE_NAME)
            val observedInstallerPackageName = taskInfo.topActivity?.packageName

            assertEquals("Unexpected intent action",
                Intent.ACTION_SHOW_APP_INFO,
                observedIntentAction)
            assertEquals("Unexpected installer package name",
                installerPackageName,
                observedInstallerPackageName)
            assertEquals("Unexpected package name",
                packageName,
                observedPackageName)
        }
    }

    companion object {
        // TODO(b/257293222): Remove when hooking up PackageManager APIs
        private const val PRIVACY_PLACEHOLDER_SAFETY_LABEL_DATA_ENABLED =
            "privacy_placeholder_safety_label_data_enabled"
    }
}
