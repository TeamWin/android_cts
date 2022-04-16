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

import android.app.Activity
import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Intent
import android.location.LocationManager
import android.net.Uri
import android.provider.Settings
import android.support.test.uiautomator.By
import com.android.compatibility.common.util.AppOpsUtils.setOpMode
import com.android.compatibility.common.util.SystemUtil.callWithShellPermissionIdentity
import com.android.compatibility.common.util.SystemUtil.eventually
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class PermissionAllServicesTest : BasePermissionTest() {

    // "All services" screen is not supported on Auto in T
    @Before
    fun assumeNotAuto() = assumeFalse(isAutomotive)

    val locationManager = context.getSystemService(LocationManager::class.java)!!

    @Test
    fun testAllServicesPreferenceShownWhenAppIsLocationProviderAndCanHandleClick() {
        installPackage(LOCATION_PROVIDER_APP_APK_PATH_2, grantRuntimePermissions = true)
        allowPackagesToMockLocation(LOCATION_PROVIDER_APP_PACKAGE_NAME_2)
        enableAppAsLocationProvider(LOCATION_PROVIDER_APP_PACKAGE_NAME_2)

        eventually({
            try {
                launchAppInfoActivity(LOCATION_PROVIDER_APP_PACKAGE_NAME_2)
                waitFindObject(By.textContains(ALL_SERVICES_LABEL))
            } catch (e: Exception) {
                pressBack()
                throw e
            } }, 1000L)

        uninstallPackage(LOCATION_PROVIDER_APP_PACKAGE_NAME_2, requireSuccess = false)
        locationManager.removeTestProvider(LOCATION_PROVIDER_APP_APK_PATH_2)
    }

    @Test
    fun testAllServicesSummaryShowsWhenAppIsLocationProviderAndCanHandleClick() {
        installPackage(LOCATION_PROVIDER_APP_APK_PATH_2, grantRuntimePermissions = true)
        allowPackagesToMockLocation(LOCATION_PROVIDER_APP_PACKAGE_NAME_2)
        enableAppAsLocationProvider(LOCATION_PROVIDER_APP_PACKAGE_NAME_2)

        eventually({
            try {
                launchAppInfoActivity(LOCATION_PROVIDER_APP_PACKAGE_NAME_2)
                waitFindObject(By.textContains(SUMMARY))
            } catch (e: Exception) {
                pressBack()
                throw e
            } }, 1000L)

        uninstallPackage(LOCATION_PROVIDER_APP_PACKAGE_NAME_2, requireSuccess = false)
        locationManager.removeTestProvider(LOCATION_PROVIDER_APP_APK_PATH_2)
    }

    @Test
    fun testAllServicesPreferenceNotShownWhenAppCannotHandleClick() {
        installPackage(LOCATION_PROVIDER_APP_APK_PATH_1, grantRuntimePermissions = true)
        allowPackagesToMockLocation(LOCATION_PROVIDER_APP_PACKAGE_NAME_1)
        enableAppAsLocationProvider(LOCATION_PROVIDER_APP_PACKAGE_NAME_1)

        eventually({
            try {
                launchAppInfoActivity(LOCATION_PROVIDER_APP_PACKAGE_NAME_1)
                assertNull(waitFindObjectOrNull(By.textContains(ALL_SERVICES_LABEL)))
            } catch (e: Exception) {
                pressBack()
                throw e
            } }, 1000L)

        uninstallPackage(LOCATION_PROVIDER_APP_PACKAGE_NAME_1, requireSuccess = false)
        locationManager.removeTestProvider(LOCATION_PROVIDER_APP_APK_PATH_1)
    }

    @Test
    fun testAllServicesPreferenceNotShownWhenAppIsNotLocationProvider() {
        installPackage(NON_LOCATION_APP_APK_PATH, grantRuntimePermissions = true)

        eventually({
            try {
                launchAppInfoActivity(NON_LOCATION_APP_PACKAGE_NAME)
                assertNull(waitFindObjectOrNull(By.textContains(ALL_SERVICES_LABEL)))
            } catch (e: Exception) {
                pressBack()
                throw e
            } }, 1000L)

        uninstallPackage(NON_LOCATION_APP_APK_PATH, requireSuccess = false)
    }

    private fun allowPackagesToMockLocation(packageName: String) {
        setOpMode(packageName, AppOpsManager.OPSTR_MOCK_LOCATION, AppOpsManager.MODE_ALLOWED)
        setOpMode(
            context.packageName, AppOpsManager.OPSTR_MOCK_LOCATION, AppOpsManager.MODE_ALLOWED
        )
    }

    private fun launchAppInfoActivity(packageName: String) {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
                addCategory(Intent.CATEGORY_DEFAULT)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
            })
    }

    private fun enableAppAsLocationProvider(appPackageName: String) {
        // Add the test app as location provider.
        val future = startActivityForFuture(
            Intent().apply {
                component = ComponentName(
                    appPackageName, "$appPackageName.AddLocationProviderActivity"
                )
            })

        val result = future.get(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
        assertEquals(Activity.RESULT_OK, result.resultCode)
        assertTrue(
            callWithShellPermissionIdentity {
                locationManager.isProviderPackage(appPackageName)
            }
        )
    }

    companion object {
        const val LOCATION_PROVIDER_APP_APK_PATH_1 =
            "$APK_DIRECTORY/CtsAccessMicrophoneAppLocationProvider.apk"
        const val NON_LOCATION_APP_APK_PATH = "$APK_DIRECTORY/CtsUsePermissionApp22.apk"
        const val LOCATION_PROVIDER_APP_APK_PATH_2 =
            "$APK_DIRECTORY/CtsAppLocationProviderWithSummary.apk"
        const val NON_LOCATION_APP_PACKAGE_NAME = "android.permission3.cts.usepermission"
        const val LOCATION_PROVIDER_APP_PACKAGE_NAME_1 =
            "android.permission3.cts.accessmicrophoneapplocationprovider"
        const val LOCATION_PROVIDER_APP_PACKAGE_NAME_2 =
            "android.permission3.cts.applocationproviderwithsummary"
        const val APP_LABEL = "LocationProviderWithSummaryApp"
        const val ALL_SERVICES_LABEL = "All Services"
        const val SUMMARY = "Services summary."
    }
}