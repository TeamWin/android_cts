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

import android.app.Activity
import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Intent
import android.location.LocationManager
import android.os.Build
import android.support.test.uiautomator.By
import com.android.compatibility.common.util.ApiLevelUtil
import com.android.compatibility.common.util.AppOpsUtils.setOpMode
import com.android.compatibility.common.util.SystemUtil.callWithShellPermissionIdentity
import com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * Tests permission usage info action for location providers.
 */
class PermissionUsageInfoTest : BaseUsePermissionTest() {
    val locationManager = context.getSystemService(LocationManager::class.java)!!

    @Before
    fun installAppLocationProviderAndAllowMockLocation() {
        installPackage(APP_APK_PATH_LOCATION_PROVIDER)
        // The package name of a mock location provider is the caller adding it, so we have to let
        // the test app add itself.
        setOpMode(APP_PACKAGE_NAME, AppOpsManager.OPSTR_MOCK_LOCATION, AppOpsManager.MODE_ALLOWED)
    }

    @Before
    fun allowMockLocation() {
        // Allow ourselves to reliably remove the test location provider.
        setOpMode(
            context.packageName, AppOpsManager.OPSTR_MOCK_LOCATION, AppOpsManager.MODE_ALLOWED
        )
    }

    @After
    fun removeTestLocationProvider() {
        locationManager.removeTestProvider(APP_PACKAGE_NAME)
    }

    @Test
    fun testLocationProviderPermissionUsageInfo() {
        val locationProviderPackageName: String
        if (ApiLevelUtil.isAtLeast(Build.VERSION_CODES.S) || ApiLevelUtil.codenameEquals("S")) {
            // Add the test app as location provider.
            val future = startActivityForFuture(
                Intent().apply {
                    component = ComponentName(
                        APP_PACKAGE_NAME, "$APP_PACKAGE_NAME.AddLocationProviderActivity"
                    )
                }
            )
            val result = future.get(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
            assertEquals(Activity.RESULT_OK, result.resultCode)
            assertTrue(
                callWithShellPermissionIdentity {
                    locationManager.isProviderPackage(APP_PACKAGE_NAME)
                }
            )
            locationProviderPackageName = APP_PACKAGE_NAME
        } else {
            // Test location provider doesn't count as location provier package before S.
            val locationManager = context.getSystemService(LocationManager::class.java)!!
            locationProviderPackageName = packageManager.getInstalledApplications(0)
                .map { it.packageName }
                .filter {
                    callWithShellPermissionIdentity { locationManager.isProviderPackage(it) }
                }
                .firstOrNull {
                    Intent(Intent.ACTION_VIEW_PERMISSION_USAGE)
                        .setPackage(it)
                        .resolveActivity(packageManager) != null
                }
                .let {
                    assumeTrue(it != null)
                    it!!
                }
        }

        runWithShellPermissionIdentity {
            context.startActivity(
                Intent(Intent.ACTION_MANAGE_APP_PERMISSIONS).apply {
                    putExtra(Intent.EXTRA_PACKAGE_NAME, locationProviderPackageName)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        }
        click(By.res("com.android.permissioncontroller:id/icon"))
    }
}
