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

import static android.Manifest.permission.ACCESS_BACKGROUND_LOCATION;
import static android.Manifest.permission.ACCESS_FINE_LOCATION;
import static android.Manifest.permission.READ_CONTACTS;
import static android.Manifest.permission.WRITE_CONTACTS;
import static android.app.AppOpsManager.MODE_ALLOWED;
import static android.app.AppOpsManager.MODE_FOREGROUND;
import static android.app.AppOpsManager.MODE_IGNORED;
import static android.app.AppOpsManager.permissionToOp;
import static android.content.pm.PackageManager.FLAG_PERMISSION_REVIEW_REQUIRED;
import static android.content.pm.PackageManager.FLAG_PERMISSION_USER_FIXED;
import static android.content.pm.PackageManager.FLAG_PERMISSION_USER_SET;
import static android.content.pm.PackageManager.PERMISSION_DENIED;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static android.permission.cts.PermissionUtils.grantPermission;

import static com.android.compatibility.common.util.BackupUtils.LOCAL_TRANSPORT_TOKEN;
import static com.android.compatibility.common.util.SystemUtil.callWithShellPermissionIdentity;
import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;

import android.app.AppOpsManager;
import android.content.Context;
import android.os.ParcelFileDescriptor;
import android.platform.test.annotations.AppModeFull;

import androidx.annotation.NonNull;
import androidx.test.InstrumentationRegistry;

import com.android.compatibility.common.util.BackupUtils;
import com.android.compatibility.common.util.ShellUtils;

import java.io.IOException;
import java.io.InputStream;

/**
 * Verifies that restored permissions are the same with backup value.
 *
 * @see com.android.packageinstaller.permission.service.BackupHelper
 */
@AppModeFull
public class PermissionTest extends BaseBackupCtsTest {

    /** The name of the package of the apps under test */
    private static final String APP = "android.backup.permission";
    private static final String APP22 = "android.backup.permission22";

    /** The apk of the packages */
    private static final String APK_PATH = "/data/local/tmp/cts/backup/";
    private static final String APP_APK_CERT_1 = APK_PATH + "CtsPermissionBackupAppCert1.apk";
    private static final String APP_APK_CERT_1_DUP = APK_PATH
            + "CtsPermissionBackupAppCert1Dup.apk";
    private static final String APP_APK_CERT_2 = APK_PATH + "CtsPermissionBackupAppCert2.apk";
    private static final String APP_APK_CERT_3 = APK_PATH + "CtsPermissionBackupAppCert3.apk";
    private static final String APP_APK_CERT_5 = APK_PATH + "CtsPermissionBackupAppCert5.apk";
    private static final String APP_APK_CERT_1_2 = APK_PATH + "CtsPermissionBackupAppCert12.apk";
    private static final String APP_APK_CERT_1_2_DUP = APK_PATH
            + "CtsPermissionBackupAppCert12Dup.apk";
    private static final String APP_APK_CERT_1_2_3 = APK_PATH + "CtsPermissionBackupAppCert123.apk";
    private static final String APP_APK_CERT_3_4 = APK_PATH + "CtsPermissionBackupAppCert34.apk";
    private static final String APP_APK_CERT_5_HISTORY_1_2_4 = APK_PATH
            + "CtsPermissionBackupAppCert5History124.apk";
    private static final String APP22_APK = APK_PATH + "CtsPermissionBackupApp22.apk";

    /** The name of the package for backup */
    private static final String ANDROID_PACKAGE = "android";

    private static final Context sContext = InstrumentationRegistry.getTargetContext();
    private static final long TIMEOUT_MILLIS = 10000;

    private BackupUtils mBackupUtils =
            new BackupUtils() {
                @Override
                protected InputStream executeShellCommand(String command) throws IOException {
                    ParcelFileDescriptor pfd =
                            getInstrumentation().getUiAutomation().executeShellCommand(command);
                    return new ParcelFileDescriptor.AutoCloseInputStream(pfd);
                }
            };

    @Override
    protected void setUp() throws Exception {
        super.setUp();
    }

    @Override
    protected void tearDown() throws Exception {
        super.setUp();

        uninstallIfInstalled(APP);
        uninstallIfInstalled(APP22);
    }

    /** Test backup and restore of regular runtime permissions. */
    public void testRestore_restoresRuntimePermissions() throws Exception {
        install(APP_APK_CERT_1);
        if (!isBackupSupported()) {
            return;
        }
        grantPermission(APP, ACCESS_FINE_LOCATION);

        mBackupUtils.backupNowAndAssertSuccess(ANDROID_PACKAGE);
        resetApp(APP);
        mBackupUtils.restoreAndAssertSuccess(LOCAL_TRANSPORT_TOKEN, ANDROID_PACKAGE);

        eventually(() -> {
            assertEquals(PERMISSION_GRANTED, checkPermission(APP, ACCESS_FINE_LOCATION));
            assertEquals(PERMISSION_DENIED, checkPermission(APP, READ_CONTACTS));
        });
    }

    /**
     * Test backup and restore of regular runtime permissions, when the app being restored has the
     * same certificate as the backed up app.
     */
    public void testRestore_sameCert_restoresRuntimePermissions() throws Exception {
        install(APP_APK_CERT_1);
        if (!isBackupSupported()) {
            return;
        }
        grantPermission(APP, ACCESS_FINE_LOCATION);

        mBackupUtils.backupNowAndAssertSuccess(ANDROID_PACKAGE);
        uninstallIfInstalled(APP);
        install(APP_APK_CERT_1_DUP);
        mBackupUtils.restoreAndAssertSuccess(LOCAL_TRANSPORT_TOKEN, ANDROID_PACKAGE);

        eventually(() -> {
            assertEquals(PERMISSION_GRANTED, checkPermission(APP, ACCESS_FINE_LOCATION));
            assertEquals(PERMISSION_DENIED, checkPermission(APP, READ_CONTACTS));
        });
    }

    /**
     * Test backup and restore of regular runtime permissions, when the app being restored has a
     * different certificate as the backed up app.
     */
    public void testRestore_diffCert_doesNotGrantRuntimePermissions() throws Exception {
        install(APP_APK_CERT_1);
        if (!isBackupSupported()) {
            return;
        }
        grantPermission(APP, ACCESS_FINE_LOCATION);

        mBackupUtils.backupNowAndAssertSuccess(ANDROID_PACKAGE);
        uninstallIfInstalled(APP);
        install(APP_APK_CERT_3);
        mBackupUtils.restoreAndAssertSuccess(LOCAL_TRANSPORT_TOKEN, ANDROID_PACKAGE);

        eventually(() -> {
            assertEquals(PERMISSION_DENIED, checkPermission(APP, ACCESS_FINE_LOCATION));
            assertEquals(PERMISSION_DENIED, checkPermission(APP, READ_CONTACTS));
        });
    }

    /**
     * Test backup and restore of regular runtime permissions, when the app being restored has the
     * backed up app's certificate in its signing history.
     */
    public void testRestore_midHistoryToRotated_restoresRuntimePermissions() throws Exception {
        install(APP_APK_CERT_2);
        if (!isBackupSupported()) {
            return;
        }
        grantPermission(APP, ACCESS_FINE_LOCATION);

        mBackupUtils.backupNowAndAssertSuccess(ANDROID_PACKAGE);
        uninstallIfInstalled(APP);
        install(APP_APK_CERT_5_HISTORY_1_2_4);
        mBackupUtils.restoreAndAssertSuccess(LOCAL_TRANSPORT_TOKEN, ANDROID_PACKAGE);

        eventually(() -> {
            assertEquals(PERMISSION_GRANTED, checkPermission(APP, ACCESS_FINE_LOCATION));
            assertEquals(PERMISSION_DENIED, checkPermission(APP, READ_CONTACTS));
        });
    }

    /**
     * Test backup and restore of regular runtime permissions, when the app being restored has the
     * backed up app's certificate as the original certificate in its signing history.
     */
    public void testRestore_origToRotated_restoresRuntimePermissions() throws Exception {
        install(APP_APK_CERT_1);
        if (!isBackupSupported()) {
            return;
        }
        grantPermission(APP, ACCESS_FINE_LOCATION);

        mBackupUtils.backupNowAndAssertSuccess(ANDROID_PACKAGE);
        uninstallIfInstalled(APP);
        install(APP_APK_CERT_5_HISTORY_1_2_4);
        mBackupUtils.restoreAndAssertSuccess(LOCAL_TRANSPORT_TOKEN, ANDROID_PACKAGE);

        eventually(() -> {
            assertEquals(PERMISSION_GRANTED, checkPermission(APP, ACCESS_FINE_LOCATION));
            assertEquals(PERMISSION_DENIED, checkPermission(APP, READ_CONTACTS));
        });
    }

    /**
     * Test backup and restore of regular runtime permissions, when the backed up app has the
     * restored app's certificate in its signing history.
     */
    public void testRestore_rotatedToMidHistory_restoresRuntimePermissions() throws Exception {
        install(APP_APK_CERT_5_HISTORY_1_2_4);
        if (!isBackupSupported()) {
            return;
        }
        grantPermission(APP, ACCESS_FINE_LOCATION);

        mBackupUtils.backupNowAndAssertSuccess(ANDROID_PACKAGE);
        uninstallIfInstalled(APP);
        install(APP_APK_CERT_2);
        mBackupUtils.restoreAndAssertSuccess(LOCAL_TRANSPORT_TOKEN, ANDROID_PACKAGE);

        eventually(() -> {
            assertEquals(PERMISSION_GRANTED, checkPermission(APP, ACCESS_FINE_LOCATION));
            assertEquals(PERMISSION_DENIED, checkPermission(APP, READ_CONTACTS));
        });
    }

    /**
     * Test backup and restore of regular runtime permissions, when the backed up app has the
     * restored app's certificate in its signing history as its original certificate.
     */
    public void testRestore_rotatedToOrig_restoresRuntimePermissions() throws Exception {
        install(APP_APK_CERT_5_HISTORY_1_2_4);
        if (!isBackupSupported()) {
            return;
        }
        grantPermission(APP, ACCESS_FINE_LOCATION);

        mBackupUtils.backupNowAndAssertSuccess(ANDROID_PACKAGE);
        uninstallIfInstalled(APP);
        install(APP_APK_CERT_1);
        mBackupUtils.restoreAndAssertSuccess(LOCAL_TRANSPORT_TOKEN, ANDROID_PACKAGE);

        eventually(() -> {
            assertEquals(PERMISSION_GRANTED, checkPermission(APP, ACCESS_FINE_LOCATION));
            assertEquals(PERMISSION_DENIED, checkPermission(APP, READ_CONTACTS));
        });
    }

    /**
     * Test backup and restore of regular runtime permissions, when the backed up app has the
     * same certificate as the restored app, but the restored app additionally has signing
     * certificate history.
     */
    public void testRestore_sameWithHistory_restoresRuntimePermissions() throws Exception {
        install(APP_APK_CERT_5);
        if (!isBackupSupported()) {
            return;
        }
        grantPermission(APP, ACCESS_FINE_LOCATION);

        mBackupUtils.backupNowAndAssertSuccess(ANDROID_PACKAGE);
        uninstallIfInstalled(APP);
        install(APP_APK_CERT_5_HISTORY_1_2_4);
        mBackupUtils.restoreAndAssertSuccess(LOCAL_TRANSPORT_TOKEN, ANDROID_PACKAGE);

        eventually(() -> {
            assertEquals(PERMISSION_GRANTED, checkPermission(APP, ACCESS_FINE_LOCATION));
            assertEquals(PERMISSION_DENIED, checkPermission(APP, READ_CONTACTS));
        });
    }

    /**
     * Test backup and restore of regular runtime permissions, when the backed up app has the
     * same certificate as the restored app, but the backed up app additionally has signing
     * certificate history.
     */
    public void testRestore_sameWithoutHistory_restoresRuntimePermissions() throws Exception {
        install(APP_APK_CERT_5_HISTORY_1_2_4);
        if (!isBackupSupported()) {
            return;
        }
        grantPermission(APP, ACCESS_FINE_LOCATION);

        mBackupUtils.backupNowAndAssertSuccess(ANDROID_PACKAGE);
        uninstallIfInstalled(APP);
        install(APP_APK_CERT_5);
        mBackupUtils.restoreAndAssertSuccess(LOCAL_TRANSPORT_TOKEN, ANDROID_PACKAGE);

        eventually(() -> {
            assertEquals(PERMISSION_GRANTED, checkPermission(APP, ACCESS_FINE_LOCATION));
            assertEquals(PERMISSION_DENIED, checkPermission(APP, READ_CONTACTS));
        });
    }

    /**
     * Test backup and restore of regular runtime permissions, when the app being restored has
     * signing history, but the backed up app's certificate is not in this signing history.
     */
    public void testRestore_notInBackedUpHistory_doesNotRestoreRuntimePerms() throws Exception {
        install(APP_APK_CERT_5_HISTORY_1_2_4);
        if (!isBackupSupported()) {
            return;
        }
        grantPermission(APP, ACCESS_FINE_LOCATION);

        mBackupUtils.backupNowAndAssertSuccess(ANDROID_PACKAGE);
        uninstallIfInstalled(APP);
        install(APP_APK_CERT_3);
        mBackupUtils.restoreAndAssertSuccess(LOCAL_TRANSPORT_TOKEN, ANDROID_PACKAGE);

        eventually(() -> {
            assertEquals(PERMISSION_DENIED, checkPermission(APP, ACCESS_FINE_LOCATION));
            assertEquals(PERMISSION_DENIED, checkPermission(APP, READ_CONTACTS));
        });
    }

    /**
     * Test backup and restore of regular runtime permissions, when the app being restored has
     * signing history, but the backed up app's certificate is not in this signing history.
     */
    public void testRestore_notInRestoredHistory_doesNotRestoreRuntimePerms() throws Exception {
        install(APP_APK_CERT_3);
        if (!isBackupSupported()) {
            return;
        }
        grantPermission(APP, ACCESS_FINE_LOCATION);

        mBackupUtils.backupNowAndAssertSuccess(ANDROID_PACKAGE);
        uninstallIfInstalled(APP);
        install(APP_APK_CERT_5_HISTORY_1_2_4);
        mBackupUtils.restoreAndAssertSuccess(LOCAL_TRANSPORT_TOKEN, ANDROID_PACKAGE);

        eventually(() -> {
            assertEquals(PERMISSION_DENIED, checkPermission(APP, ACCESS_FINE_LOCATION));
            assertEquals(PERMISSION_DENIED, checkPermission(APP, READ_CONTACTS));
        });
    }

    /**
     * Test backup and restore of regular runtime permissions, when the app being restored has
     * multiple certificates, and the backed up app also has identical multiple certificates.
     */
    public void testRestore_sameMultCerts_restoresRuntimePermissions() throws Exception {
        install(APP_APK_CERT_1_2);
        if (!isBackupSupported()) {
            return;
        }
        grantPermission(APP, ACCESS_FINE_LOCATION);

        mBackupUtils.backupNowAndAssertSuccess(ANDROID_PACKAGE);
        uninstallIfInstalled(APP);
        install(APP_APK_CERT_1_2_DUP);
        mBackupUtils.restoreAndAssertSuccess(LOCAL_TRANSPORT_TOKEN, ANDROID_PACKAGE);

        eventually(() -> {
            assertEquals(PERMISSION_GRANTED, checkPermission(APP, ACCESS_FINE_LOCATION));
            assertEquals(PERMISSION_DENIED, checkPermission(APP, READ_CONTACTS));
        });
    }

    /**
     * Test backup and restore of regular runtime permissions, when the app being restored has
     * multiple certificates, and the backed up app do not have identical multiple certificates.
     */
    public void testRestore_diffMultCerts_doesNotRestoreRuntimePermissions() throws Exception {
        install(APP_APK_CERT_1_2);
        if (!isBackupSupported()) {
            return;
        }
        grantPermission(APP, ACCESS_FINE_LOCATION);

        mBackupUtils.backupNowAndAssertSuccess(ANDROID_PACKAGE);
        uninstallIfInstalled(APP);
        install(APP_APK_CERT_3_4);
        mBackupUtils.restoreAndAssertSuccess(LOCAL_TRANSPORT_TOKEN, ANDROID_PACKAGE);

        eventually(() -> {
            assertEquals(PERMISSION_DENIED, checkPermission(APP, ACCESS_FINE_LOCATION));
            assertEquals(PERMISSION_DENIED, checkPermission(APP, READ_CONTACTS));
        });
    }

    /**
     * Test backup and restore of regular runtime permissions, when the app being restored has
     * multiple certificates, and the backed up app's certificate is present in th restored app's
     * certificates.
     */
    public void testRestore_singleToMultiCert_restoresRuntimePerms() throws Exception {
        install(APP_APK_CERT_1);
        if (!isBackupSupported()) {
            return;
        }
        grantPermission(APP, ACCESS_FINE_LOCATION);

        mBackupUtils.backupNowAndAssertSuccess(ANDROID_PACKAGE);
        uninstallIfInstalled(APP);
        install(APP_APK_CERT_1_2_3);
        mBackupUtils.restoreAndAssertSuccess(LOCAL_TRANSPORT_TOKEN, ANDROID_PACKAGE);

        eventually(() -> {
            assertEquals(PERMISSION_DENIED, checkPermission(APP, ACCESS_FINE_LOCATION));
            assertEquals(PERMISSION_DENIED, checkPermission(APP, READ_CONTACTS));
        });
    }

    /**
     * Test backup and restore of regular runtime permissions, when the backed up app and the app
     * being restored have multiple certificates, and the backed up app's certificates are a subset
     * of the restored app's certificates.
     */
    public void testRestore_multCertsToSuperset_doesNotRestoreRuntimePerms() throws Exception {
        install(APP_APK_CERT_1_2);
        if (!isBackupSupported()) {
            return;
        }
        grantPermission(APP, ACCESS_FINE_LOCATION);

        mBackupUtils.backupNowAndAssertSuccess(ANDROID_PACKAGE);
        uninstallIfInstalled(APP);
        install(APP_APK_CERT_1_2_3);
        mBackupUtils.restoreAndAssertSuccess(LOCAL_TRANSPORT_TOKEN, ANDROID_PACKAGE);

        eventually(() -> {
            assertEquals(PERMISSION_DENIED, checkPermission(APP, ACCESS_FINE_LOCATION));
            assertEquals(PERMISSION_DENIED, checkPermission(APP, READ_CONTACTS));
        });
    }

    /**
     * Test backup and restore of regular runtime permissions, when the backed up app and the app
     * being restored have multiple certificates, and the backed up app's certificates are a
     * superset of the restored app's certificates.
     */
    public void testRestore_multCertsToSubset_doesNotRestoreRuntimePermissions() throws Exception {
        install(APP_APK_CERT_1_2_3);
        if (!isBackupSupported()) {
            return;
        }
        grantPermission(APP, ACCESS_FINE_LOCATION);

        mBackupUtils.backupNowAndAssertSuccess(ANDROID_PACKAGE);
        uninstallIfInstalled(APP);
        install(APP_APK_CERT_1_2);
        mBackupUtils.restoreAndAssertSuccess(LOCAL_TRANSPORT_TOKEN, ANDROID_PACKAGE);

        eventually(() -> {
            assertEquals(PERMISSION_DENIED, checkPermission(APP, ACCESS_FINE_LOCATION));
            assertEquals(PERMISSION_DENIED, checkPermission(APP, READ_CONTACTS));
        });
    }

    /**
     * Test backup and restore of pre-M regular runtime permission.
     */
    public void testRestore_api22_restoresRuntimePermissions() throws Exception {
        install(APP22_APK);
        if (!isBackupSupported()) {
            return;
        }
        setAppOp(APP22, READ_CONTACTS, MODE_IGNORED);

        mBackupUtils.backupNowAndAssertSuccess(ANDROID_PACKAGE);
        resetApp(APP22);
        mBackupUtils.restoreAndAssertSuccess(LOCAL_TRANSPORT_TOKEN, ANDROID_PACKAGE);

        eventually(() -> {
            assertEquals(MODE_IGNORED, getAppOp(APP22, READ_CONTACTS));
            assertEquals(MODE_ALLOWED, getAppOp(APP22, ACCESS_FINE_LOCATION));
        });
    }

    /**
     * Test backup and restore of tri-state permissions, when both foreground and background runtime
     * permissions are not granted.
     */
    public void testRestore_fgBgDenied_restoresFgBgPermissions() throws Exception {
        install(APP_APK_CERT_1);
        if (!isBackupSupported()) {
            return;
        }
        // Make a token change to permission state, to enable to us to determine when restore is
        // complete.
        grantPermission(APP, WRITE_CONTACTS);
        // PERMISSION_DENIED is the default state, so we mark the permissions as user set in order
        // to ensure that permissions are backed up.
        setFlag(APP, ACCESS_FINE_LOCATION, FLAG_PERMISSION_USER_SET);
        setFlag(APP, ACCESS_BACKGROUND_LOCATION, FLAG_PERMISSION_USER_SET);

        mBackupUtils.backupNowAndAssertSuccess(ANDROID_PACKAGE);
        resetApp(APP);
        mBackupUtils.restoreAndAssertSuccess(LOCAL_TRANSPORT_TOKEN, ANDROID_PACKAGE);

        eventually(() -> {
            // Wait until restore is complete.
            assertEquals(PERMISSION_GRANTED, checkPermission(APP, WRITE_CONTACTS));

            assertEquals(PERMISSION_DENIED, checkPermission(APP, ACCESS_FINE_LOCATION));
            assertEquals(PERMISSION_DENIED, checkPermission(APP, ACCESS_BACKGROUND_LOCATION));
            assertEquals(MODE_IGNORED, getAppOp(APP, ACCESS_FINE_LOCATION));
        });
    }

    /**
     * Test backup and restore of tri-state permissions, when both foreground and background runtime
     * permissions are not granted and the backed up and restored app have compatible certificates.
     */
    public void testRestore_fgBgDenied_matchingCerts_restoresFgBgPermissions() throws Exception {
        install(APP_APK_CERT_2);
        if (!isBackupSupported()) {
            return;
        }
        // Make a token change to permission state, to enable to us to determine when restore is
        // complete.
        grantPermission(APP, WRITE_CONTACTS);
        // PERMISSION_DENIED is the default state, so we mark the permissions as user set in order
        // to ensure that permissions are backed up.
        setFlag(APP, ACCESS_FINE_LOCATION, FLAG_PERMISSION_USER_SET);
        setFlag(APP, ACCESS_BACKGROUND_LOCATION, FLAG_PERMISSION_USER_SET);

        mBackupUtils.backupNowAndAssertSuccess(ANDROID_PACKAGE);
        uninstallIfInstalled(APP);
        install(APP_APK_CERT_5_HISTORY_1_2_4);
        mBackupUtils.restoreAndAssertSuccess(LOCAL_TRANSPORT_TOKEN, ANDROID_PACKAGE);

        eventually(() -> {
            // Wait until restore is complete.
            assertEquals(PERMISSION_GRANTED, checkPermission(APP, WRITE_CONTACTS));

            assertEquals(PERMISSION_DENIED, checkPermission(APP, ACCESS_FINE_LOCATION));
            assertEquals(PERMISSION_DENIED, checkPermission(APP, ACCESS_BACKGROUND_LOCATION));
            assertEquals(MODE_IGNORED, getAppOp(APP, ACCESS_FINE_LOCATION));
        });
    }

    /**
     * Test backup and restore of tri-state permissions, when both foreground and background
     * runtime permissions are not granted and the backed up and restored app don't have compatible
     * certificates.
     */
    public void testRestore_fgBgDenied_notMatchingCerts_doesNotRestorePerms() throws Exception {
        install(APP_APK_CERT_1);
        if (!isBackupSupported()) {
            return;
        }
        // Make a token change to permission state, to enable to us to determine when restore is
        // complete.
        grantPermission(APP, WRITE_CONTACTS);
        // PERMISSION_DENIED is the default state, so we mark the permissions as user set in order
        // to ensure that permissions are backed up.
        setFlag(APP, ACCESS_FINE_LOCATION, FLAG_PERMISSION_USER_SET);
        setFlag(APP, ACCESS_BACKGROUND_LOCATION, FLAG_PERMISSION_USER_SET);

        mBackupUtils.backupNowAndAssertSuccess(ANDROID_PACKAGE);
        uninstallIfInstalled(APP);
        install(APP_APK_CERT_2);
        mBackupUtils.restoreAndAssertSuccess(LOCAL_TRANSPORT_TOKEN, ANDROID_PACKAGE);

        eventually(() -> {
            // Wait until restore is complete.
            assertEquals(PERMISSION_DENIED, checkPermission(APP, WRITE_CONTACTS));

            assertEquals(PERMISSION_DENIED, checkPermission(APP, ACCESS_FINE_LOCATION));
            assertEquals(PERMISSION_DENIED, checkPermission(APP, ACCESS_BACKGROUND_LOCATION));
            assertEquals(MODE_IGNORED, getAppOp(APP, ACCESS_FINE_LOCATION));
        });
    }

    /**
     * Test backup and restore of pre-M foreground permissions, when foreground runtime permission
     * is not granted.
     */
    public void testRestore_api22_fgDenied_restoresPermissions() throws Exception {
        install(APP22_APK);
        if (!isBackupSupported()) {
            return;
        }
        setAppOp(APP22, ACCESS_FINE_LOCATION, MODE_IGNORED);

        mBackupUtils.backupNowAndAssertSuccess(ANDROID_PACKAGE);
        resetApp(APP22);
        mBackupUtils.restoreAndAssertSuccess(LOCAL_TRANSPORT_TOKEN, ANDROID_PACKAGE);

        eventually(() -> assertEquals(MODE_IGNORED, getAppOp(APP22, ACCESS_FINE_LOCATION)));
    }

    /**
     * Test backup and restore of tri-state permissions, when foreground runtime permission is
     * granted.
     */
    public void testRestore_fgGranted_restoresFgBgPermissions() throws Exception {
        install(APP_APK_CERT_1);
        if (!isBackupSupported()) {
            return;
        }
        grantPermission(APP, ACCESS_FINE_LOCATION);
        // PERMISSION_DENIED is the default state, so we mark the permissions as user set in order
        // to ensure that permissions are backed up.
        setFlag(APP, ACCESS_BACKGROUND_LOCATION, FLAG_PERMISSION_USER_SET);

        mBackupUtils.backupNowAndAssertSuccess(ANDROID_PACKAGE);
        resetApp(APP);
        mBackupUtils.restoreAndAssertSuccess(LOCAL_TRANSPORT_TOKEN, ANDROID_PACKAGE);

        eventually(() -> {
            assertEquals(PERMISSION_GRANTED, checkPermission(APP, ACCESS_FINE_LOCATION));
            assertEquals(PERMISSION_DENIED, checkPermission(APP, ACCESS_BACKGROUND_LOCATION));
            assertEquals(MODE_FOREGROUND, getAppOp(APP, ACCESS_FINE_LOCATION));
        });
    }

    /**
     * Test backup and restore of tri-state permissions, when foreground runtime permission is
     * granted and the backed up and restored app have compatible certificates.
     */
    public void testRestore_fgGranted_matchingCerts_restoresFgBgPermissions() throws Exception {
        install(APP_APK_CERT_2);
        if (!isBackupSupported()) {
            return;
        }
        grantPermission(APP, ACCESS_FINE_LOCATION);
        // PERMISSION_DENIED is the default state, so we mark the permissions as user set in order
        // to ensure that permissions are backed up.
        setFlag(APP, ACCESS_BACKGROUND_LOCATION, FLAG_PERMISSION_USER_SET);

        mBackupUtils.backupNowAndAssertSuccess(ANDROID_PACKAGE);
        uninstallIfInstalled(APP);
        install(APP_APK_CERT_5_HISTORY_1_2_4);
        mBackupUtils.restoreAndAssertSuccess(LOCAL_TRANSPORT_TOKEN, ANDROID_PACKAGE);

        eventually(() -> {
            assertEquals(PERMISSION_GRANTED, checkPermission(APP, ACCESS_FINE_LOCATION));
            assertEquals(PERMISSION_DENIED, checkPermission(APP, ACCESS_BACKGROUND_LOCATION));
            assertEquals(MODE_FOREGROUND, getAppOp(APP, ACCESS_FINE_LOCATION));
        });
    }

    /**
     * Test backup and restore of tri-state permissions, when foreground runtime permission is
     * granted and the backed up and restored app don't have compatible certificates.
     */
    public void testRestore_fgGranted_notMatchingCerts_doesNotRestoreFgBgPerms() throws Exception {
        install(APP_APK_CERT_1);
        if (!isBackupSupported()) {
            return;
        }
        grantPermission(APP, ACCESS_FINE_LOCATION);
        // PERMISSION_DENIED is the default state, so we mark the permissions as user set in order
        // to ensure that permissions are backed up.
        setFlag(APP, ACCESS_BACKGROUND_LOCATION, FLAG_PERMISSION_USER_SET);

        mBackupUtils.backupNowAndAssertSuccess(ANDROID_PACKAGE);
        uninstallIfInstalled(APP);
        install(APP_APK_CERT_2);
        mBackupUtils.restoreAndAssertSuccess(LOCAL_TRANSPORT_TOKEN, ANDROID_PACKAGE);

        eventually(() -> {
            assertEquals(PERMISSION_DENIED, checkPermission(APP, ACCESS_FINE_LOCATION));
            assertEquals(PERMISSION_DENIED, checkPermission(APP, ACCESS_BACKGROUND_LOCATION));
            assertEquals(MODE_IGNORED, getAppOp(APP, ACCESS_FINE_LOCATION));
        });
    }

    /**
     * Test backup and restore of foreground runtime permission.
     *
     * Comment out the test since it's a JUnit 3 test which doesn't support @Ignore
     * TODO: b/178522459 to fix the test once the foundamental issue has been fixed.
     */
//    public void testGrantForegroundRuntimePermission22() throws Exception {
//        if (!isBackupSupported()) {
//            return;
//        }
//        setAppOp(APP22, ACCESS_FINE_LOCATION, MODE_FOREGROUND);
//
//        mBackupUtils.backupNowAndAssertSuccess(ANDROID_PACKAGE);
//        resetApp(APP22);
//        mBackupUtils.restoreAndAssertSuccess(LOCAL_TRANSPORT_TOKEN, ANDROID_PACKAGE);
//
//        eventually(() -> assertEquals(MODE_FOREGROUND, getAppOp(APP22, ACCESS_FINE_LOCATION)));
//    }

    /**
     * Test backup and restore of tri-state permissions, when foreground and background runtime
     * permissions are granted.
     */
    public void testRestore_fgBgGranted_restoresFgBgPermissions() throws Exception {
        install(APP_APK_CERT_1);
        if (!isBackupSupported()) {
            return;
        }
        grantPermission(APP, ACCESS_FINE_LOCATION);
        grantPermission(APP, ACCESS_BACKGROUND_LOCATION);

        mBackupUtils.backupNowAndAssertSuccess(ANDROID_PACKAGE);
        resetApp(APP);
        mBackupUtils.restoreAndAssertSuccess(LOCAL_TRANSPORT_TOKEN, ANDROID_PACKAGE);

        eventually(() -> {
            assertEquals(PERMISSION_GRANTED, checkPermission(APP, ACCESS_FINE_LOCATION));
            assertEquals(PERMISSION_GRANTED, checkPermission(APP, ACCESS_BACKGROUND_LOCATION));
            assertEquals(MODE_ALLOWED, getAppOp(APP, ACCESS_FINE_LOCATION));
        });
    }

    /**
     * Test backup and restore of tri-state permissions, when foreground and background runtime
     * permissions are granted and the backed up and restored app have compatible certificates.
     */
    public void testRestore_fgBgGranted_matchingCerts_restoresFgBgPermissions() throws Exception {
        install(APP_APK_CERT_2);
        if (!isBackupSupported()) {
            return;
        }
        grantPermission(APP, ACCESS_FINE_LOCATION);
        grantPermission(APP, ACCESS_BACKGROUND_LOCATION);

        mBackupUtils.backupNowAndAssertSuccess(ANDROID_PACKAGE);
        uninstallIfInstalled(APP);
        install(APP_APK_CERT_5_HISTORY_1_2_4);
        mBackupUtils.restoreAndAssertSuccess(LOCAL_TRANSPORT_TOKEN, ANDROID_PACKAGE);

        eventually(() -> {
            assertEquals(PERMISSION_GRANTED, checkPermission(APP, ACCESS_FINE_LOCATION));
            assertEquals(PERMISSION_GRANTED, checkPermission(APP, ACCESS_BACKGROUND_LOCATION));
            assertEquals(MODE_ALLOWED, getAppOp(APP, ACCESS_FINE_LOCATION));
        });
    }

    /**
     * Test backup and restore of tri-state permissions, when foreground and background runtime
     * permissions are granted and the backed up and restored app don't have compatible
     * certificates.
     */
    public void testRestore_fgBgGranted_notMatchingCerts_restoresFgBgPerms() throws Exception {
        install(APP_APK_CERT_1);
        if (!isBackupSupported()) {
            return;
        }
        grantPermission(APP, ACCESS_FINE_LOCATION);
        grantPermission(APP, ACCESS_BACKGROUND_LOCATION);

        mBackupUtils.backupNowAndAssertSuccess(ANDROID_PACKAGE);
        uninstallIfInstalled(APP);
        install(APP_APK_CERT_2);
        mBackupUtils.restoreAndAssertSuccess(LOCAL_TRANSPORT_TOKEN, ANDROID_PACKAGE);

        eventually(() -> {
            assertEquals(PERMISSION_DENIED, checkPermission(APP, ACCESS_FINE_LOCATION));
            assertEquals(PERMISSION_DENIED, checkPermission(APP, ACCESS_BACKGROUND_LOCATION));
            assertEquals(MODE_IGNORED, getAppOp(APP, ACCESS_FINE_LOCATION));
        });
    }

    /**
     * Test backup and restore of pre-M foreground permissions, when foreground runtime permission
     * is granted.
     */
    public void testRestore_api22_fgGranted_restoresFgPermissions() throws Exception {
        install(APP22_APK);
        if (!isBackupSupported()) {
            return;
        }
        // Make a token change to permission state, to enable to us to determine when restore is
        // complete.
        setAppOp(APP22, WRITE_CONTACTS, MODE_IGNORED);

        mBackupUtils.backupNowAndAssertSuccess(ANDROID_PACKAGE);
        resetApp(APP22);
        mBackupUtils.restoreAndAssertSuccess(LOCAL_TRANSPORT_TOKEN, ANDROID_PACKAGE);

        eventually(() -> {
            // Wait until restore is complete.
            assertEquals(MODE_IGNORED, getAppOp(APP22, WRITE_CONTACTS));

            assertEquals(MODE_ALLOWED, getAppOp(APP22, ACCESS_FINE_LOCATION));
        });
    }

    /** Test backup and restore of the permission review required flag. */
    public void testRestore_restoresPermissionReviewRequiredFlag() throws Exception {
        install(APP22_APK);
        if (!isBackupSupported()) {
            return;
        }
        clearFlag(APP22, WRITE_CONTACTS, FLAG_PERMISSION_REVIEW_REQUIRED);

        mBackupUtils.backupNowAndAssertSuccess(ANDROID_PACKAGE);
        resetApp(APP22);
        mBackupUtils.restoreAndAssertSuccess(LOCAL_TRANSPORT_TOKEN, ANDROID_PACKAGE);

        eventually(() -> assertFalse(
                isFlagSet(APP22, WRITE_CONTACTS, FLAG_PERMISSION_REVIEW_REQUIRED)));
    }

    /** Test backup and restore of the user set flag. */
    public void testRestore_restoresUserSetFlag() throws Exception {
        install(APP_APK_CERT_1);
        if (!isBackupSupported()) {
            return;
        }
        setFlag(APP, WRITE_CONTACTS, FLAG_PERMISSION_USER_SET);

        mBackupUtils.backupNowAndAssertSuccess(ANDROID_PACKAGE);
        resetApp(APP);
        mBackupUtils.restoreAndAssertSuccess(LOCAL_TRANSPORT_TOKEN, ANDROID_PACKAGE);

        eventually(() -> assertTrue(isFlagSet(APP, WRITE_CONTACTS, FLAG_PERMISSION_USER_SET)));
    }

    /** Test backup and restore of the user fixed flag. */
    public void testRestore_restoresUserFixedFlag() throws Exception {
        install(APP_APK_CERT_1);
        if (!isBackupSupported()) {
            return;
        }
        setFlag(APP, WRITE_CONTACTS, FLAG_PERMISSION_USER_FIXED);

        mBackupUtils.backupNowAndAssertSuccess(ANDROID_PACKAGE);
        resetApp(APP);
        mBackupUtils.restoreAndAssertSuccess(LOCAL_TRANSPORT_TOKEN, ANDROID_PACKAGE);

        eventually(() -> assertTrue(isFlagSet(APP, WRITE_CONTACTS, FLAG_PERMISSION_USER_FIXED)));
    }

    /** Test backup and restore restores flag values but does not grant permissions. */
    public void testRestore_whenFlagRestored_doesNotGrantPermission() throws Exception {
        install(APP_APK_CERT_1);
        if (!isBackupSupported()) {
            return;
        }
        setFlag(APP, WRITE_CONTACTS, FLAG_PERMISSION_USER_FIXED);

        mBackupUtils.backupNowAndAssertSuccess(ANDROID_PACKAGE);
        resetApp(APP);
        mBackupUtils.restoreAndAssertSuccess(LOCAL_TRANSPORT_TOKEN, ANDROID_PACKAGE);

        eventually(() -> assertEquals(PERMISSION_DENIED, checkPermission(APP, WRITE_CONTACTS)));
    }

    /**
     * Test backup and restore of flags when the backed up app and restored app have compatible
     * certificates.
     */
    public void testRestore_matchingCerts_restoresFlags() throws Exception {
        install(APP_APK_CERT_2);
        if (!isBackupSupported()) {
            return;
        }
        setFlag(APP, WRITE_CONTACTS, FLAG_PERMISSION_USER_SET);

        mBackupUtils.backupNowAndAssertSuccess(ANDROID_PACKAGE);
        uninstallIfInstalled(APP);
        install(APP_APK_CERT_5_HISTORY_1_2_4);
        mBackupUtils.restoreAndAssertSuccess(LOCAL_TRANSPORT_TOKEN, ANDROID_PACKAGE);

        eventually(() -> assertTrue(isFlagSet(APP, WRITE_CONTACTS, FLAG_PERMISSION_USER_SET)));
    }

    /**
     * Test backup and restore of flags when the backed up app and restored app don't have
     * compatible certificates.
     */
    public void testRestore_notMatchingCerts_doesNotRestoreFlag() throws Exception {
        install(APP_APK_CERT_1);
        if (!isBackupSupported()) {
            return;
        }
        setFlag(APP, WRITE_CONTACTS, FLAG_PERMISSION_USER_SET);

        mBackupUtils.backupNowAndAssertSuccess(ANDROID_PACKAGE);
        uninstallIfInstalled(APP);
        install(APP_APK_CERT_2);
        mBackupUtils.restoreAndAssertSuccess(LOCAL_TRANSPORT_TOKEN, ANDROID_PACKAGE);

        eventually(() -> assertFalse(isFlagSet(APP, WRITE_CONTACTS, FLAG_PERMISSION_USER_SET)));
    }

    /**
     * Test backup and delayed restore of regular runtime permission, i.e. when an app is installed
     * after restore has run.
     */
    public void testRestore_appInstalledLater_restoresCorrectly() throws Exception {
        install(APP_APK_CERT_1);
        install(APP22_APK);
        if (!isBackupSupported()) {
            return;
        }
        grantPermission(APP, ACCESS_FINE_LOCATION);
        setAppOp(APP22, READ_CONTACTS, MODE_IGNORED);
        mBackupUtils.backupNowAndAssertSuccess(ANDROID_PACKAGE);
        uninstallIfInstalled(APP);
        uninstallIfInstalled(APP22);

        mBackupUtils.restoreAndAssertSuccess(LOCAL_TRANSPORT_TOKEN, ANDROID_PACKAGE);

        install(APP_APK_CERT_1);

        eventually(() -> assertEquals(PERMISSION_GRANTED,
                checkPermission(APP, ACCESS_FINE_LOCATION)));

        install(APP22_APK);

        eventually(() -> assertEquals(MODE_IGNORED, getAppOp(APP22, READ_CONTACTS)));
    }

    /**
     * Test backup and delayed restore of regular runtime permission, i.e. when an app is installed
     * after restore has run, and the backed up app and restored app have compatible certificates.
     */
    public void testRestore_appInstalledLater_matchingCerts_restoresCorrectly() throws Exception {
        install(APP_APK_CERT_2);
        if (!isBackupSupported()) {
            return;
        }
        grantPermission(APP, ACCESS_FINE_LOCATION);
        mBackupUtils.backupNowAndAssertSuccess(ANDROID_PACKAGE);
        uninstallIfInstalled(APP);

        mBackupUtils.restoreAndAssertSuccess(LOCAL_TRANSPORT_TOKEN, ANDROID_PACKAGE);
        install(APP_APK_CERT_5_HISTORY_1_2_4);

        eventually(() -> assertEquals(PERMISSION_GRANTED,
                checkPermission(APP, ACCESS_FINE_LOCATION)));
    }

    /**
     * Test backup and delayed restore of regular runtime permission, i.e. when an app is installed
     * after restore has run, and the backed up app and restored app don't have compatible
     * certificates.
     */
    public void testRestore_appInstalledLater_notMatchingCerts_doesNotRestore() throws Exception {
        install(APP_APK_CERT_1);
        if (!isBackupSupported()) {
            return;
        }
        grantPermission(APP, ACCESS_FINE_LOCATION);
        mBackupUtils.backupNowAndAssertSuccess(ANDROID_PACKAGE);
        uninstallIfInstalled(APP);

        mBackupUtils.restoreAndAssertSuccess(LOCAL_TRANSPORT_TOKEN, ANDROID_PACKAGE);
        install(APP_APK_CERT_5_HISTORY_1_2_4);

        eventually(() -> assertEquals(PERMISSION_DENIED,
                checkPermission(APP, ACCESS_FINE_LOCATION)));
    }

    private void install(String apk) {
        String output = ShellUtils.runShellCommand("pm install -r " + apk);
        assertEquals("Success", output);
    }

    private void uninstallIfInstalled(String packageName) {
        ShellUtils.runShellCommand("pm uninstall " + packageName);
    }

    private void resetApp(String packageName) {
        ShellUtils.runShellCommand("pm clear " + packageName);
        ShellUtils.runShellCommand("appops reset " + packageName);
    }

    /**
     * Make sure that a {@link Runnable} eventually finishes without throwing a {@link
     * Exception}.
     *
     * @param r The {@link Runnable} to run.
     */
    public static void eventually(@NonNull Runnable r) {
        long start = System.currentTimeMillis();

        while (true) {
            try {
                r.run();
                return;
            } catch (Throwable e) {
                if (System.currentTimeMillis() - start < TIMEOUT_MILLIS) {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException ignored) {
                        throw new RuntimeException(e);
                    }
                } else {
                    throw e;
                }
            }
        }
    }

    private void setFlag(String app, String permission, int flag) {
        runWithShellPermissionIdentity(
                () -> sContext.getPackageManager().updatePermissionFlags(permission, app,
                        flag, flag, sContext.getUser()));
    }

    private void clearFlag(String app, String permission, int flag) {
        runWithShellPermissionIdentity(
                () -> sContext.getPackageManager().updatePermissionFlags(permission, app,
                        flag, 0, sContext.getUser()));
    }

    private boolean isFlagSet(String app, String permission, int flag) {
        try {
            return (callWithShellPermissionIdentity(
                    () -> sContext.getPackageManager().getPermissionFlags(permission, app,
                            sContext.getUser())) & flag) == flag;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private int checkPermission(String app, String permission) {
        return sContext.getPackageManager().checkPermission(permission, app);
    }

    private void setAppOp(String app, String permission, int mode) {
        runWithShellPermissionIdentity(
                () -> sContext.getSystemService(AppOpsManager.class).setUidMode(
                        permissionToOp(permission),
                        sContext.getPackageManager().getPackageUid(app, 0), mode));
    }

    private int getAppOp(String app, String permission) {
        try {
            return callWithShellPermissionIdentity(
                    () -> sContext.getSystemService(AppOpsManager.class).unsafeCheckOpRaw(
                            permissionToOp(permission),
                            sContext.getPackageManager().getPackageUid(app, 0), app));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
