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

import android.os.Build
import androidx.test.filters.SdkSuppress
import org.junit.Test

/**
 * Tests media storage supergroup behavior. I.e., on a T+ platform, for legacy (targetSdk<T) apps,
 * the storage permission groups (STORAGE, AURAL, and VISUAL) form a supergroup, which effectively
 * treats them as one group and therefore their permission state must always be equal.
 */
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.TIRAMISU, codeName = "Tiramisu")
class MediaPermissionTest : BaseUsePermissionTest() {
    private fun assertStorageAndMediaPermissionState(state: Boolean) {
        for (permission in STORAGE_AND_MEDIA_PERMISSIONS) {
            assertAppHasPermission(permission, state)
        }
    }

    @Test
    fun testWhenRESIsGrantedFromGrantDialogThenShouldGrantAllPermissions() {
        installPackage(APP_APK_PATH_23)
        requestAppPermissionsAndAssertResult(
            android.Manifest.permission.READ_EXTERNAL_STORAGE to true
        ) {
            clickPermissionRequestAllowButton()
        }
        assertStorageAndMediaPermissionState(true)
    }

    @Test
    fun testWhenRESIsGrantedManuallyThenShouldGrantAllPermissions() {
        installPackage(APP_APK_PATH_23)
        grantAppPermissions(android.Manifest.permission.READ_EXTERNAL_STORAGE, targetSdk = 23)
        assertStorageAndMediaPermissionState(true)
    }

    @Test
    fun testWhenAuralIsGrantedManuallyThenShouldGrantAllPermissions() {
        installPackage(APP_APK_PATH_23)
        grantAppPermissions(android.Manifest.permission.READ_MEDIA_AUDIO, targetSdk = 23)
        assertStorageAndMediaPermissionState(true)
    }

    @Test
    fun testWhenVisualIsGrantedManuallyThenShouldGrantAllPermissions() {
        installPackage(APP_APK_PATH_23)
        grantAppPermissions(android.Manifest.permission.READ_MEDIA_VIDEO, targetSdk = 23)
        assertStorageAndMediaPermissionState(true)
    }

    @Test
    fun testWhenRESIsDeniedFromGrantDialogThenShouldDenyAllPermissions() {
        installPackage(APP_APK_PATH_23)
        requestAppPermissionsAndAssertResult(
            android.Manifest.permission.READ_EXTERNAL_STORAGE to false
        ) {
            clickPermissionRequestDenyButton()
        }
        assertStorageAndMediaPermissionState(false)
    }

    @Test
    fun testWhenRESIsDeniedManuallyThenShouldDenyAllPermissions() {
        installPackage(APP_APK_PATH_23)
        grantAppPermissions(android.Manifest.permission.READ_EXTERNAL_STORAGE, targetSdk = 23)
        revokeAppPermissions(android.Manifest.permission.READ_EXTERNAL_STORAGE, targetSdk = 23)
        assertStorageAndMediaPermissionState(false)
    }

    @Test
    fun testWhenAuralIsDeniedManuallyThenShouldDenyAllPermissions() {
        installPackage(APP_APK_PATH_23)
        grantAppPermissions(android.Manifest.permission.READ_MEDIA_AUDIO, targetSdk = 23)
        revokeAppPermissions(android.Manifest.permission.READ_MEDIA_AUDIO, targetSdk = 23)
        assertStorageAndMediaPermissionState(false)
    }

    @Test
    fun testWhenVisualIsDeniedManuallyThenShouldDenyAllPermissions() {
        installPackage(APP_APK_PATH_23)
        grantAppPermissions(android.Manifest.permission.READ_MEDIA_VIDEO, targetSdk = 23)
        revokeAppPermissions(android.Manifest.permission.READ_MEDIA_VIDEO, targetSdk = 23)
        assertStorageAndMediaPermissionState(false)
    }

    @Test
    fun testWhenA33AppRequestsStorageThenNoDialogAndNoGrant() {
        installPackage(APP_APK_PATH_MEDIA_PERMISSION_33_WITH_STORAGE)
        requestAppPermissions(
            android.Manifest.permission.READ_EXTERNAL_STORAGE,
            android.Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) {
        }
        assertStorageAndMediaPermissionState(false)
    }
}
