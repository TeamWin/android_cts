/*
 * Copyright (C) 2018 The Android Open Source Project
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
import android.content.pm.PackageManager
import com.android.compatibility.common.util.SystemUtil
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.Test

/**
 * Runtime permission behavior tests for permission splits.
 */
class PermissionSplitTest : BaseUsePermissionTest() {
    @Before
    fun assumeNotTv() {
        assumeFalse(isTv)
    }

    @Test
    fun testPermissionSplit28() {
        installPackage(APP_APK_PATH_28)
        testLocationPermissionSplit(true)
    }

    @Test
    fun testPermissionNotSplit29() {
        installPackage(APP_APK_PATH_29)
        testLocationPermissionSplit(false)
    }

    @Test
    fun testPermissionNotSplit30() {
        installPackage(APP_APK_PATH_30)
        testLocationPermissionSplit(false)
    }

    @Test
    fun testPermissionNotSplitLatest() {
        installPackage(APP_APK_PATH_LATEST)
        testLocationPermissionSplit(false)
    }

    @Test
    fun testMicCamPermissionSplit30() {
        installPackage(APP_APK_PATH_30)
        testMicCamPermissionSplit(expectSplit = true, exemptPermissions = true)
    }

    @Test
    fun testMicCamPermissionNoExemptNoSplit30() {
        installPackage(APP_APK_PATH_30)
        testMicCamPermissionSplit(expectSplit = false, exemptPermissions = false)
    }

    private fun testLocationPermissionSplit(expectSplit: Boolean) {
        assertAppHasPermission(android.Manifest.permission.ACCESS_FINE_LOCATION, false)
        assertAppHasPermission(android.Manifest.permission.ACCESS_BACKGROUND_LOCATION, false)

        requestAppPermissionsAndAssertResult(
                android.Manifest.permission.ACCESS_FINE_LOCATION to true
        ) {
            if (expectSplit) {
                clickPermissionRequestSettingsLinkAndAllowAlways()
            } else {
                clickPermissionRequestAllowForegroundButton()
            }
        }

        assertAppHasPermission(android.Manifest.permission.ACCESS_BACKGROUND_LOCATION, expectSplit)
    }

    private fun testMicCamPermissionSplit(expectSplit: Boolean, exemptPermissions: Boolean) {
        assertAppHasPermission(android.Manifest.permission.RECORD_AUDIO, false)
        assertAppHasPermission(android.Manifest.permission.CAMERA, false)

        if (exemptPermissions) {
            SystemUtil.runWithShellPermissionIdentity {
                packageManager.addWhitelistedRestrictedPermission(APP_PACKAGE_NAME,
                        Manifest.permission.RECORD_BACKGROUND_AUDIO,
                        PackageManager.FLAG_PERMISSION_WHITELIST_SYSTEM)
                packageManager.addWhitelistedRestrictedPermission(APP_PACKAGE_NAME,
                        Manifest.permission.BACKGROUND_CAMERA,
                        PackageManager.FLAG_PERMISSION_WHITELIST_SYSTEM)
            }
        }

        requestAppPermissionsAndAssertResult(
                android.Manifest.permission.RECORD_AUDIO to true
        ) {
            if (expectSplit) {
                clickPermissionRequestSettingsLinkAndAllowAlways()
            } else {
                clickPermissionRequestAllowForegroundButton()
            }
        }
        assertAppHasPermission(android.Manifest.permission.RECORD_BACKGROUND_AUDIO, expectSplit)

        requestAppPermissionsAndAssertResult(
                android.Manifest.permission.CAMERA to true
        ) {
            if (expectSplit) {
                clickPermissionRequestSettingsLinkAndAllowAlways()
            } else {
                clickPermissionRequestAllowForegroundButton()
            }
        }

        assertAppHasPermission(android.Manifest.permission.BACKGROUND_CAMERA, expectSplit)
    }
}
