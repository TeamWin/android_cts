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

import android.Manifest.permission.ACCESS_COARSE_LOCATION
import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.Manifest.permission.CAMERA
import android.os.Build
import android.provider.DeviceConfig
import android.support.test.uiautomator.By
import androidx.test.filters.SdkSuppress
import com.android.compatibility.common.util.DeviceConfigStateChangerRule
import com.android.compatibility.common.util.SystemUtil
import com.android.modules.utils.build.SdkLevel
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Permission rationale in Grant Permission Dialog tests. Permission rationale is only available on
 * U+
 */
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.UPSIDE_DOWN_CAKE, codeName = "UpsideDownCake")
class PermissionRationalePermissionGrantDialogTest : BaseUsePermissionTest() {

    @get:Rule
    val deviceConfigPermissionRationaleEnabled =
        DeviceConfigStateChangerRule(
            context,
            DeviceConfig.NAMESPACE_PRIVACY,
            PRIVACY_PERMISSION_RATIONALE_ENABLED,
            true.toString())

    // TODO(b/257293222): Remove when hooking up PackageManager APIs
    @get:Rule
    val deviceConfigTestSafetyLabelDataEnabled =
        DeviceConfigStateChangerRule(
            context,
            DeviceConfig.NAMESPACE_PRIVACY,
            PRIVACY_PLACEHOLDER_SAFETY_LABEL_DATA_ENABLED,
            true.toString())

    private fun setDeviceConfigPrivacyProperty(
        propertyName: String,
        value: String,
    ) {
        SystemUtil.runWithShellPermissionIdentity(instrumentation.uiAutomation) {
            val valueWasSet =
                DeviceConfig.setProperty(
                    DeviceConfig.NAMESPACE_PRIVACY,
                    /* name = */ propertyName,
                    /* value = */ value,
                    /* makeDefault = */ false)
            check(valueWasSet) { "Could not set $propertyName to $value" }
        }
    }

    private fun permissionRationaleIsVisible(): Boolean {
        return waitFindObjectOrNull(By.res(PERMISSION_RATIONALE_CONTAINER_VIEW), 1000L) != null
    }

    @Before
    fun setup() {
        Assume.assumeTrue("Permission rationale is only available on U+", SdkLevel.isAtLeastU())
        Assume.assumeFalse(isAutomotive)
        Assume.assumeFalse(isTv)
        Assume.assumeFalse(isWatch)
    }

    @Test
    fun noPermissionRationaleWhenFlagDisabled() {
        setDeviceConfigPrivacyProperty(PRIVACY_PERMISSION_RATIONALE_ENABLED, false.toString())
        installPackage(APP_APK_PATH_31)

        assertAppHasPermission(ACCESS_COARSE_LOCATION, false)
        assertAppHasPermission(ACCESS_FINE_LOCATION, false)

        requestAppPermissions(ACCESS_COARSE_LOCATION, ACCESS_FINE_LOCATION) {
            assertFalse(permissionRationaleIsVisible())
            return
        }
    }

    // TODO(b/257293222): Remove when hooking up PackageManager APIs
    @Test
    fun noPermissionRationaleWhenPlaceholderDataFlagDisabled() {
        setDeviceConfigPrivacyProperty(
            PRIVACY_PLACEHOLDER_SAFETY_LABEL_DATA_ENABLED, false.toString())
        installPackage(APP_APK_PATH_31)

        assertAppHasPermission(ACCESS_COARSE_LOCATION, false)
        assertAppHasPermission(ACCESS_FINE_LOCATION, false)

        requestAppPermissions(ACCESS_COARSE_LOCATION, ACCESS_FINE_LOCATION) {
            assertFalse(permissionRationaleIsVisible())
            return
        }
    }

    @Test
    fun noPermissionRationaleForNonLocationPermission() {
        installPackage(APP_APK_PATH_31)

        assertAppHasPermission(CAMERA, false)

        requestAppPermissions(CAMERA) {
            assertFalse(permissionRationaleIsVisible())
            return
        }
    }

    @Test
    fun hasPermissionRationaleForCoarseLocationPermission() {
        installPackage(APP_APK_PATH_31)

        assertAppHasPermission(ACCESS_COARSE_LOCATION, false)

        requestAppPermissions(ACCESS_COARSE_LOCATION) {
            assertTrue(permissionRationaleIsVisible())
            return
        }
    }

    @Test
    fun hasPermissionRationaleForFineLocationPermission() {
        installPackage(APP_APK_PATH_31)

        assertAppHasPermission(ACCESS_FINE_LOCATION, false)

        requestAppPermissions(ACCESS_FINE_LOCATION) {
            assertTrue(permissionRationaleIsVisible())
            return
        }
    }

    companion object {
        private const val PRIVACY_PERMISSION_RATIONALE_ENABLED =
            "privacy_permission_rationale_enabled"

        // TODO(b/257293222): Remove when hooking up PackageManager APIs
        private const val PRIVACY_PLACEHOLDER_SAFETY_LABEL_DATA_ENABLED =
            "privacy_placeholder_safety_label_data_enabled"

        private const val PERMISSION_RATIONALE_CONTAINER_VIEW =
            "com.android.permissioncontroller:id/permission_rationale_container"
    }
}
