/*
 * Copyright (C) 2019 The Android Open Source Project
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

package android.permission.cts;

import static android.Manifest.permission.READ_CALENDAR;
import static android.Manifest.permission.READ_CONTACTS;
import static android.permission.cts.PermissionUtils.grantPermission;
import static android.permission.cts.PermissionUtils.install;
import static android.permission.cts.PermissionUtils.isPermissionGranted;
import static android.permission.cts.PermissionUtils.uninstallApp;

import static com.google.common.truth.Truth.assertThat;

import android.platform.test.annotations.AppModeFull;
import android.util.Log;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class SharedUidPermissionsTest {
    private static final String LOG_TAG = SharedUidPermissionsTest.class.getSimpleName();

    /** The package name of all apps used in the test */
    private static final String PKG_THAT_REQUESTS_PERMISSIONS = "android.permission.cts.appthatrequestpermission";
    private static final String PKG_THAT_REQUESTS_NO_PERMISSIONS = "android.permission.cts.appthatrequestnopermission";

    private static final String TMP_DIR = "/data/local/tmp/cts/permissions/";
    private static final String APK_THAT_REQUESTS_PERMISSIONS =
            TMP_DIR + "CtsAppWithSharedUidThatRequestsPermissions.apk";
    private static final String APK_THAT_REQUESTS_NO_PERMISSIONS =
            TMP_DIR + "CtsAppWithSharedUidThatRequestsNoPermissions.apk";

    @Test
    @AppModeFull(reason = "Instant apps cannot read properties of other packages which is needed "
            + "to grant permissions to them.")
    public void appsWithSharedUidsSharePermissions() throws Exception {
        install(APK_THAT_REQUESTS_PERMISSIONS);
        install(APK_THAT_REQUESTS_NO_PERMISSIONS);

        try {
            grantPermission(PKG_THAT_REQUESTS_PERMISSIONS, READ_CONTACTS);
            grantPermission(PKG_THAT_REQUESTS_PERMISSIONS, READ_CALENDAR);

            // Permissions are shared
            assertThat(isPermissionGranted(PKG_THAT_REQUESTS_NO_PERMISSIONS, READ_CONTACTS)).isTrue();
            assertThat(isPermissionGranted(PKG_THAT_REQUESTS_NO_PERMISSIONS, READ_CALENDAR)).isTrue();

            uninstallApp(PKG_THAT_REQUESTS_PERMISSIONS);

            // When the app requesting the permissions is uninstalled, the other apps are no longer
            // granted the permissions it requested.
            assertThat(isPermissionGranted(PKG_THAT_REQUESTS_NO_PERMISSIONS, READ_CONTACTS)).isFalse();
            assertThat(isPermissionGranted(PKG_THAT_REQUESTS_NO_PERMISSIONS, READ_CALENDAR)).isFalse();
        } finally {
            uninstallApp(PKG_THAT_REQUESTS_PERMISSIONS);
            uninstallApp(PKG_THAT_REQUESTS_NO_PERMISSIONS);
        }
    }
}
