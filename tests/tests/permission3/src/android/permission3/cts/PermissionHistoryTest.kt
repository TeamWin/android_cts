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

import android.Manifest
import android.app.Activity
import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.provider.DeviceConfig.NAMESPACE_PRIVACY
import androidx.test.filters.SdkSuppress
import androidx.test.uiautomator.By
import com.android.compatibility.common.util.AppOpsUtils
import com.android.compatibility.common.util.DeviceConfigStateChangerRule
import com.android.compatibility.common.util.SystemUtil
import com.android.modules.utils.build.SdkLevel
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import org.junit.After
import org.junit.Assert
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Tests for Privacy Dashboard UI, which displays when apps recently accessed permissions. */
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.S)
class PermissionHistoryTest : BasePermissionTest() {
    val locationManager = context.getSystemService(LocationManager::class.java)!!
    val appOpsManager = context.getSystemService(AppOpsManager::class.java)!!
    val packageManager: PackageManager = context.packageManager!!

    @get:Rule
    val privacy7DaysEnabled =
        DeviceConfigStateChangerRule(
            context, NAMESPACE_PRIVACY, PRIVACY_DASHBOARD_7_DAY_TOGGLE_ENABLED, true.toString())

    @Before
    fun checkPreconditions() {
        assumeFalse(isTv)
        assumeFalse(isAutomotive && !SdkLevel.isAtLeastT())
    }

    @Before
    fun setUpApps() {
        installPackage(MIC_APP_1_APK_PATH, grantRuntimePermissions = true)
        installPackage(MIC_APP_2_APK_PATH, grantRuntimePermissions = true)
        installPackage(CAM_AND_MIC_APP_APK_PATH, grantRuntimePermissions = true)
        installPackage(MIC_LOCATION_PROVIDER_APP_APK_PATH, grantRuntimePermissions = true)
        SystemUtil.runWithShellPermissionIdentity {
            appOpsManager.clearHistory()
            appOpsManager.resetPackageOpsNoHistory(MIC_APP_1_PACKAGE_NAME)
            appOpsManager.resetPackageOpsNoHistory(MIC_APP_2_PACKAGE_NAME)
            appOpsManager.resetPackageOpsNoHistory(CAM_AND_MIC_APP_PACKAGE_NAME)
            appOpsManager.resetPackageOpsNoHistory(MIC_LOCATION_PROVIDER_APP_PACKAGE_NAME)
            val packageNames = packageManager.getInstalledPackages(0).map { it.packageName }
            packageNames.forEach { appOpsManager.resetPackageOpsNoHistory(it) }
        }
        // The package name of a mock location provider is the caller adding it, so we have to let
        // the test app add itself.
        AppOpsUtils.setOpMode(
            MIC_LOCATION_PROVIDER_APP_PACKAGE_NAME,
            AppOpsManager.OPSTR_MOCK_LOCATION,
            AppOpsManager.MODE_ALLOWED)
    }

    @After
    fun tearDownApps() {
        tearDownLocationProvider()
        SystemUtil.runWithShellPermissionIdentity {
            appOpsManager.clearHistory()
            appOpsManager.resetPackageOpsNoHistory(MIC_APP_1_PACKAGE_NAME)
            appOpsManager.resetPackageOpsNoHistory(MIC_APP_2_PACKAGE_NAME)
            appOpsManager.resetPackageOpsNoHistory(CAM_AND_MIC_APP_PACKAGE_NAME)
            appOpsManager.resetPackageOpsNoHistory(MIC_LOCATION_PROVIDER_APP_PACKAGE_NAME)
            val packageNames = packageManager.getInstalledPackages(0).map { it.packageName }
            packageNames.forEach { appOpsManager.resetPackageOpsNoHistory(it) }
        }
        uninstallPackage(MIC_APP_1_PACKAGE_NAME, requireSuccess = false)
        uninstallPackage(MIC_APP_2_PACKAGE_NAME, requireSuccess = false)
        uninstallPackage(CAM_AND_MIC_APP_PACKAGE_NAME, requireSuccess = false)
        uninstallPackage(MIC_LOCATION_PROVIDER_APP_PACKAGE_NAME, requireSuccess = false)
    }

    @Test
    fun openPrivacyDashboard_showsPermissionEntries() {
        openPrivacyDashboard()

        try {
            waitFindObject(By.hasChild(By.textContains(MICROPHONE)))
            waitFindObject(By.hasChild(By.textContains(CAMERA)))
            waitFindObject(By.hasChild(By.textContains(LOCATION)))
            if (!isAutomotive) {
                // Auto shows a single action item option instead of the overflow menu. The
                // "Show system" action will only appear when there are system apps not shown.
                waitFindObject(By.descContains(MORE_OPTIONS))
            }
        } finally {
            pressBack()
            pressBack()
        }
    }

    @Test
    fun openPrivacyDashboard_shows7DaysToggle() {
        // Don't run this test on Auto as it doesn't support the 7 day view.
        assumeFalse(isAutomotive)
        startActivityWithIntent(MIC_APP_1_INTENT)
        waitFindObject(By.textContains(MIC_APP_LABEL_1))

        openPrivacyDashboard()

        try {
            waitFindObject(By.descContains(MORE_OPTIONS)).click()
            waitFindObject(By.text(SHOW_7_DAYS)).click()
            waitFindObject(
                By.hasChild(By.textContains(MICROPHONE)).hasChild(By.textStartsWith("Used by")))
            waitFindObject(
                By.text(Pattern.compile(DASHBOARD_7_DAYS_DESCRIPTION_REGEX, Pattern.DOTALL)))
        } finally {
            pressBack()
            pressBack()
        }
    }

    @Test
    fun openPrivacyDashboard_whenUsedMicApp_showsMicAppUsage() {
        startActivityWithIntent(MIC_APP_1_INTENT)
        waitFindObject(By.textContains(MIC_APP_LABEL_1))

        openPrivacyDashboard()

        try {
            waitFindObject(
                    By.hasChild(By.textContains(MICROPHONE)).hasChild(By.textStartsWith("Used by")))
                .click()
            waitFindObject(By.textContains(MICROPHONE))
            waitFindObject(By.textContains(MIC_APP_LABEL_1))
        } finally {
            pressBack()
            pressBack()
        }
    }

    @Test
    fun openMicrophoneTimeline_showsSystemToggle() {
        // TODO(b/274339224): Auto only shows the "show system" toggle if system apps are present.
        // Enable this test for Auto if system apps are tested.
        assumeFalse(isAutomotive)
        startActivityWithIntent(MIC_APP_1_INTENT)
        waitFindObject(By.textContains(MIC_APP_LABEL_1))

        openPrivacyTimeline(Manifest.permission_group.MICROPHONE)

        try {
            waitFindObject(By.descContains(MORE_OPTIONS)).click()
            waitFindObject(By.text(SHOW_SYSTEM))
        } finally {
            pressBack()
            pressBack()
        }
    }

    @Test
    fun openMicrophoneTimeline_shows7DaysToggle() {
        // Don't run this test on Auto as it doesn't support the 7 day view.
        assumeFalse(isAutomotive)
        startActivityWithIntent(MIC_APP_1_INTENT)
        waitFindObject(By.textContains(MIC_APP_LABEL_1))

        openPrivacyTimeline(Manifest.permission_group.MICROPHONE)

        try {
            waitFindObject(By.descContains(MORE_OPTIONS)).click()
            waitFindObject(By.text(SHOW_7_DAYS)).click()
            waitFindObject(By.descContains(MICROPHONE))
            waitFindObject(By.textContains(MIC_APP_LABEL_1))
            waitFindObject(
                By.text(Pattern.compile(DASHBOARD_7_DAYS_DESCRIPTION_REGEX, Pattern.DOTALL)))
            waitFindObject(By.descContains(MORE_OPTIONS)).click()
            waitFindObject(By.text(SHOW_24_HOURS)).click()
            waitFindObject(By.descContains(MORE_OPTIONS)).click()
            waitFindObject(By.text(SHOW_7_DAYS))
        } finally {
            pressBack()
            pressBack()
        }
    }

    @Test
    fun openMicrophoneTimeline_whenUsedMicApp_showsMicAppUsage() {
        startActivityWithIntent(MIC_APP_1_INTENT)
        waitFindObject(By.textContains(MIC_APP_LABEL_1))

        openPrivacyTimeline(Manifest.permission_group.MICROPHONE)

        try {
            waitFindObject(By.textContains(MICROPHONE))
            waitFindObject(By.textContains(MIC_APP_LABEL_1))
            if (isAutomotive) {
                // Automotive views don't have the same ids as phones - find an example of the time
                // usage instead. Specify the package name to avoid matching with the system UI
                // time.
                waitFindObject(
                    By.text(Pattern.compile(DASHBOARD_TIME_DESCRIPTION_REGEX, Pattern.DOTALL))
                        .pkg(context.packageManager.permissionControllerPackageName))
            } else {
                waitFindObject(
                    By.res(PERMISSION_CONTROLLER_PACKAGE_ID_PREFIX + HISTORY_PREFERENCE_ICON))
                waitFindObject(
                    By.res(PERMISSION_CONTROLLER_PACKAGE_ID_PREFIX + HISTORY_PREFERENCE_TIME))
            }
        } finally {
            pressBack()
            pressBack()
        }
    }

    @Test
    fun openMicrophoneTimeline_whenUsedMicApps_showsMicAppUsage() {
        startActivityWithIntent(MIC_APP_1_INTENT)
        waitFindObject(By.textContains(MIC_APP_LABEL_1))
        startActivityWithIntent(MIC_APP_2_INTENT)
        waitFindObject(By.textContains(MIC_APP_LABEL_2))

        openPrivacyTimeline(Manifest.permission_group.MICROPHONE)

        try {
            waitFindObject(By.textContains(MICROPHONE))
            waitFindObject(By.textContains(MIC_APP_LABEL_1))
            waitFindObject(By.textContains(MIC_APP_LABEL_2))
        } finally {
            pressBack()
            pressBack()
        }
    }

    @Test
    fun openCameraTimeline_whenUsedCameraApp_showsCameraUsage() {
        context.startActivity(
            Intent(CAM_AND_MIC_APP_INTENT).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(USE_CAMERA, true)
            })
        waitFindObject(By.textContains(CAM_AND_MIC_APP_LABEL))

        openPrivacyTimeline(Manifest.permission_group.CAMERA)

        try {
            waitFindObject(By.textContains(CAMERA))
            waitFindObject(By.textContains(CAM_AND_MIC_APP_LABEL))
        } finally {
            pressBack()
            pressBack()
        }
    }

    @Test
    fun openMicrophoneTimeline_whenUsedMicAttributedToLocationProvider_showsAttribution() {
        enableMicrophoneAppAsLocationProvider()
        useMicrophoneLocationProviderApp()

        openPrivacyTimeline(Manifest.permission_group.MICROPHONE)

        try {
            waitFindObject(By.textContains(MICROPHONE))
            waitFindObject(By.textContains(MIC_LOCATION_PROVIDER_APP_LABEL))
            waitFindObject(By.textContains(MIC_LOCATION_PROVIDER_ATTRIBUTION_LABEL))
        } finally {
            pressBack()
            pressBack()
        }
    }

    private fun tearDownLocationProvider() {
        // Allow ourselves to reliably remove the test location provider.
        AppOpsUtils.setOpMode(
            context.packageName, AppOpsManager.OPSTR_MOCK_LOCATION, AppOpsManager.MODE_ALLOWED)
        locationManager.removeTestProvider(MIC_LOCATION_PROVIDER_APP_PACKAGE_NAME)
    }

    private fun enableMicrophoneAppAsLocationProvider() {
        val future =
            startActivityForFuture(
                Intent().apply {
                    component =
                        ComponentName(
                            MIC_LOCATION_PROVIDER_APP_PACKAGE_NAME,
                            "$MIC_LOCATION_PROVIDER_APP_PACKAGE_NAME.AddLocationProviderActivity")
                })
        val result = future.get(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
        Assert.assertEquals(Activity.RESULT_OK, result.resultCode)
        Assert.assertTrue(
            SystemUtil.callWithShellPermissionIdentity {
                locationManager.isProviderPackage(MIC_LOCATION_PROVIDER_APP_PACKAGE_NAME)
            })
    }

    private fun useMicrophoneLocationProviderApp() {
        val future =
            startActivityForFuture(
                Intent().apply {
                    component =
                        ComponentName(
                            MIC_LOCATION_PROVIDER_APP_PACKAGE_NAME,
                            "$MIC_LOCATION_PROVIDER_APP_PACKAGE_NAME.UseMicrophoneActivity")
                })
        val result = future.get(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
        Assert.assertEquals(Activity.RESULT_OK, result.resultCode)
    }

    companion object {
        private const val MIC_APP_1_APK_PATH = "$APK_DIRECTORY/CtsAccessMicrophoneApp.apk"
        private const val MIC_APP_1_PACKAGE_NAME = "android.permission3.cts.accessmicrophoneapp"
        private const val MIC_APP_2_APK_PATH = "$APK_DIRECTORY/CtsAccessMicrophoneApp2.apk"
        private const val MIC_APP_2_PACKAGE_NAME = "android.permission3.cts.accessmicrophoneapp2"
        private const val CAM_AND_MIC_APP_APK_PATH =
            "$APK_DIRECTORY/CtsAppThatAccessesMicAndCameraPermission.apk"
        private const val CAM_AND_MIC_APP_PACKAGE_NAME =
            "android.permission3.cts.appthataccessescameraandmic"
        private const val MIC_LOCATION_PROVIDER_APP_APK_PATH =
            "$APK_DIRECTORY/CtsAccessMicrophoneAppLocationProvider.apk"
        private const val MIC_LOCATION_PROVIDER_APP_PACKAGE_NAME =
            "android.permission3.cts.accessmicrophoneapplocationprovider"
        private const val MIC_LOCATION_PROVIDER_APP_LABEL = "LocationProviderWithMicApp"
        private const val MIC_LOCATION_PROVIDER_ATTRIBUTION_LABEL = "Attribution Label"
        private const val MICROPHONE = "Microphone"
        private const val CAMERA = "Camera"
        private const val LOCATION = "Location"
        private const val MIC_APP_LABEL_1 = "CtsMicAccess"
        private const val MIC_APP_LABEL_2 = "CtsMicAccess2"
        private const val CAM_AND_MIC_APP_LABEL = "CtsCameraMicAccess"
        private const val MIC_APP_1_INTENT = "test.action.USE_MIC"
        private const val MIC_APP_2_INTENT = "test.action.USE_MIC_2"
        private const val CAM_AND_MIC_APP_INTENT = "test.action.USE_CAMERA_OR_MIC"
        private const val USE_CAMERA = "use_camera"
        private const val PERMISSION_CONTROLLER_PACKAGE_ID_PREFIX =
            "com.android.permissioncontroller:id/"
        private const val HISTORY_PREFERENCE_ICON = "permission_history_icon"
        private const val HISTORY_PREFERENCE_TIME = "permission_history_time"
        private const val SHOW_SYSTEM = "Show system"
        private const val SHOW_7_DAYS = "Show 7 days"
        private const val SHOW_24_HOURS = "Show 24 hours"
        private const val MORE_OPTIONS = "More options"
        private const val DASHBOARD_7_DAYS_DESCRIPTION_REGEX = "^.*7.*days$"
        private const val DASHBOARD_TIME_DESCRIPTION_REGEX = "^[0-2]?[0-9]:[0-5][0-9].*"
        private const val PRIVACY_DASHBOARD_7_DAY_TOGGLE_ENABLED = "privacy_dashboard_7_day_toggle"

        private fun startActivityWithIntent(intentAction: String) {
            context.startActivity(
                Intent(intentAction).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
        }

        private fun openPrivacyDashboard() {
            SystemUtil.runWithShellPermissionIdentity {
                startActivityWithIntent(Intent.ACTION_REVIEW_PERMISSION_USAGE)
            }
        }

        private fun openPrivacyTimeline(permissionGroup: String) {
            SystemUtil.runWithShellPermissionIdentity {
                context.startActivity(
                    Intent(Intent.ACTION_REVIEW_PERMISSION_HISTORY).apply {
                        putExtra(Intent.EXTRA_PERMISSION_GROUP_NAME, permissionGroup)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    })
            }
        }
    }
}
