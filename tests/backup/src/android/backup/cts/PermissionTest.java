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
 * limitations under the License
 */

package android.backup.cts;

import static com.android.compatibility.common.util.BackupUtils.LOCAL_TRANSPORT_TOKEN;

import android.Manifest;
import android.app.Instrumentation;
import android.content.pm.PackageManager;
import android.os.ParcelFileDescriptor;
import android.platform.test.annotations.AppModeFull;

import com.android.compatibility.common.util.BackupUtils;

import java.io.IOException;
import java.io.InputStream;

/**
 * Verifies that restored permissions are the same with backup value.
 */
@AppModeFull
public class PermissionTest extends BaseBackupCtsTest {

    /** The name of the package of the app under test */
    private static final String APP_PACKAGE = "android.backup.permission";

    /** The name of the package for backup */
    private static final String ANDROID_PACKAGE = "android";

    private BackupUtils mBackupUtils =
            new BackupUtils() {
                @Override
                protected InputStream executeShellCommand(String command) throws IOException {
                    ParcelFileDescriptor pfd =
                            getInstrumentation().getUiAutomation().executeShellCommand(command);
                    return new ParcelFileDescriptor.AutoCloseInputStream(pfd);
                }
            };

    /**
     * Test backup and restore of grant runtime permission.
     *
     * Test logic:
     * 1. Grant SEND_SMS and WRITE_CONTACTS permissions to APP_PACKAGE.
     * 2. Backup android package, revoke SEND_SMS and WRITE_CONTACTS permissions to APP_PACKAGE.
     * Then restore android package.
     * 3. Check restored SEND_SMS and WRITE_CONTACTS permissions.
     *
     * @see PackageManagerService#serializeRuntimePermissionGrantsLPr(XmlSerializer, int) and
     * PackageManagerService#processRestoredPermissionGrantsLPr(XmlPullParser, int)
     */
    public void testGrantRuntimePermission() throws Exception {
        grantRuntimePermission(Manifest.permission.SEND_SMS);
        grantRuntimePermission(Manifest.permission.WRITE_CONTACTS);

        mBackupUtils.backupNowAndAssertSuccess(ANDROID_PACKAGE);
        revokeRuntimePermission(Manifest.permission.SEND_SMS);
        revokeRuntimePermission(Manifest.permission.WRITE_CONTACTS);
        mBackupUtils.restoreAndAssertSuccess(LOCAL_TRANSPORT_TOKEN, ANDROID_PACKAGE);

        assertEquals(PackageManager.PERMISSION_GRANTED,
                checkPermission(Manifest.permission.SEND_SMS));
        assertEquals(PackageManager.PERMISSION_GRANTED,
                checkPermission(Manifest.permission.WRITE_CONTACTS));
    }

    private int checkPermission(String permission) {
        return getInstrumentation().getContext().getPackageManager().checkPermission(permission,
                APP_PACKAGE);
    }

    private void grantRuntimePermission(String permission) {
        if (checkPermission(permission) != PackageManager.PERMISSION_GRANTED) {
            getInstrumentation().getUiAutomation().grantRuntimePermission(APP_PACKAGE, permission);
            assertEquals(PackageManager.PERMISSION_GRANTED, checkPermission(permission));
        }
    }

    private void revokeRuntimePermission(String permission) {
        if (checkPermission(permission) == PackageManager.PERMISSION_GRANTED) {
            getInstrumentation().getUiAutomation().revokeRuntimePermission(APP_PACKAGE, permission);
            assertEquals(PackageManager.PERMISSION_DENIED, checkPermission(permission));
        }
    }
}
