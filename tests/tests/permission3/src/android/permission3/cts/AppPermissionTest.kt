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

import android.Manifest.permission.ACCESS_COARSE_LOCATION
import android.Manifest.permission.CAMERA
import android.os.Build
import android.provider.DeviceConfig
import android.support.test.uiautomator.By
import androidx.test.filters.SdkSuppress
import com.android.compatibility.common.util.DeviceConfigStateChangerRule
import com.android.modules.utils.build.SdkLevel
import org.junit.Assume
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** App Permission page UI tests. */
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.UPSIDE_DOWN_CAKE, codeName = "UpsideDownCake")
class AppPermissionTest : BaseUsePermissionTest() {

  @get:Rule
  val deviceConfigPermissionRationaleEnabled =
      DeviceConfigStateChangerRule(
          context, DeviceConfig.NAMESPACE_PRIVACY, PERMISSION_RATIONALE_ENABLED, true.toString())

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
  }

  @Test
  fun showPermissionRationaleContainer_withInstallSource_whenPermissionRationaleEnabled() {
    installPackageWithInstallSource()
    navigateToIndividualPermissionSetting(ACCESS_COARSE_LOCATION)

    assertPermissionRationaleOnAppPermissionIsVisible(true)

    clickPermissionRationaleContentInAppPermission()
    assertPermissionRationaleDialogOnAppPermissionIsVisible(true)
  }

  @Test
  fun noShowPermissionRationaleContainer_withOutInstallSource_whenPermissionRationaleEnabled() {
    installPackageWithoutInstallSource()
    navigateToIndividualPermissionSetting(ACCESS_COARSE_LOCATION)

    assertPermissionRationaleOnAppPermissionIsVisible(false)
  }

  @Test
  fun noShowPermissionRationaleContainer_withoutLocationPermission_whenPermissionRationaleEnabled()
  {
    installPackageWithInstallSource()
    navigateToIndividualPermissionSetting(CAMERA)

    assertPermissionRationaleOnAppPermissionIsVisible(false)
  }

  @Test
  fun noShowPermissionRationaleContainer_whenSafetyLabelDataDisabled() {
    setDeviceConfigPrivacyProperty(PRIVACY_PLACEHOLDER_SAFETY_LABEL_DATA_ENABLED, false.toString())
    installPackageWithInstallSource()
    navigateToIndividualPermissionSetting(ACCESS_COARSE_LOCATION)

    assertPermissionRationaleOnAppPermissionIsVisible(false)
  }

  @Test
  fun noShowPermissionRationaleContainer_whenPermissionRationaleDisabled() {
    setDeviceConfigPrivacyProperty(PERMISSION_RATIONALE_ENABLED, false.toString())
    installPackageWithInstallSource()
    navigateToIndividualPermissionSetting(ACCESS_COARSE_LOCATION)

    assertPermissionRationaleOnAppPermissionIsVisible(false)
  }

  private fun installPackageWithInstallSource() {
    // TODO(b/257293222): Update/remove when hooking up PackageManager APIs
    installPackage(APP_APK_PATH_31, installSource = TEST_INSTALLER_PACKAGE_NAME)
  }

  private fun installPackageWithoutInstallSource() {
    // TODO(b/257293222): Update/remove when hooking up PackageManager APIs
    installPackage(APP_APK_PATH_31)
  }

  private fun assertPermissionRationaleOnAppPermissionIsVisible(expected: Boolean) {
    findView(By.res(APP_PERMISSION_RATIONALE_CONTAINER_VIEW), expected = expected)
  }

  private fun assertPermissionRationaleDialogOnAppPermissionIsVisible(expected: Boolean) {
    findView(By.res(PERMISSION_RATIONALE_ACTIVITY_TITLE_VIEW), expected = expected)
  }

  companion object {
    // TODO(b/257293222): Remove when hooking up PackageManager APIs
    private const val PRIVACY_PLACEHOLDER_SAFETY_LABEL_DATA_ENABLED =
        "privacy_placeholder_safety_label_data_enabled"

    private const val PERMISSION_RATIONALE_ENABLED = "permission_rationale_enabled"
  }
}
