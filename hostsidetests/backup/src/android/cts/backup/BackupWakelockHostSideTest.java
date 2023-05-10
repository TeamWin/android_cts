/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.cts.backup;

import static android.cts.backup.KeyValueBackupRestoreHostSideTest.KEY_VALUE_RESTORE_APP_APK;
import static android.cts.backup.KeyValueBackupRestoreHostSideTest.KEY_VALUE_RESTORE_APP_PACKAGE;
import static android.cts.backup.KeyValueBackupRestoreHostSideTest.KEY_VALUE_RESTORE_DEVICE_TEST_NAME;

import static org.junit.Assert.assertNull;

import android.platform.test.annotations.AppModeFull;

import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test for checking that backup/restore operations acquire wakelock at the start and release it at
 * the end.
 */
@RunWith(DeviceJUnit4ClassRunner.class)
@AppModeFull
public class BackupWakelockHostSideTest extends BaseBackupHostSideTest {
    private static final String TAG = "BackupWakelockHostSideTest";
    private static final String LOGCAT_FILTER =
            "BackupManagerService:* PerformBackupTask:* KeyValueBackupTask:* PFTBT:* "
                    + TAG + ":* *:S";
    private String mWakelockAcquiredLog;
    private String mWakelockReleasedLog;
    private static final String KEY_VALUE_START = "Beginning backup of ";
    private static final String KEY_VALUE_SUCCESS_LOG = "K/V backup pass finished";
    private static final String RESTOREATINSTALL_LOG =
            "restoreAtInstall pkg=" + KEY_VALUE_RESTORE_APP_PACKAGE;
    private static final String RESTORECOMPLETE_LOG = "Restore complete";
    private static final int TIMEOUT_SECS = 30;

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();
        installPackageAsUser(KEY_VALUE_RESTORE_APP_APK, mDefaultBackupUserId);
        clearPackageDataAsUser(KEY_VALUE_RESTORE_APP_PACKAGE, mDefaultBackupUserId);
        mWakelockAcquiredLog = "Acquired wakelock:*backup*-" + Integer.toString(
                mDefaultBackupUserId);
        mWakelockReleasedLog = "Released wakelock:*backup*-" + Integer.toString(
                mDefaultBackupUserId);
    }

    @After
    public void tearDown() throws Exception {
        clearBackupDataInLocalTransport(KEY_VALUE_RESTORE_APP_PACKAGE);
        assertNull(uninstallPackageAsUser(KEY_VALUE_RESTORE_APP_PACKAGE, mDefaultBackupUserId));
    }

    /**
     * Test that verifies requestBackup of key/value app acquires wakelock at the start and releases
     * it at the end.
     */
    @Test
    public void testRequestBackup_forKeyValue_acquiresAndReleasesWakelock() throws Exception {
        runDeviceSideProcedure("saveSharedPreferencesAndNotifyBackupManager");

        String startLog = mLogcatInspector.mark(TAG);
        runDeviceSideProcedure("requestBackup");

        mLogcatInspector.assertLogcatContainsInOrder(LOGCAT_FILTER, TIMEOUT_SECS, startLog,
                mWakelockAcquiredLog, KEY_VALUE_START, KEY_VALUE_SUCCESS_LOG,
                mWakelockReleasedLog);
    }

    /**
     * Test that verifies restore at install of key/value app acquires wakelock at the start and
     * releases it at the end.
     */
    @Test
    public void testRestoreAtInstall_forKeyValue_acquiresAndReleasesWakelock() throws Exception {
        runDeviceSideProcedure("saveSharedPreferencesAndNotifyBackupManager");
        runDeviceSideProcedure("requestBackup");
        assertNull(uninstallPackageAsUser(KEY_VALUE_RESTORE_APP_PACKAGE, mDefaultBackupUserId));

        String startLog = mLogcatInspector.mark(TAG);
        installPackageAsUser(KEY_VALUE_RESTORE_APP_APK, mDefaultBackupUserId);

        mLogcatInspector.assertLogcatContainsInOrder(LOGCAT_FILTER, TIMEOUT_SECS, startLog,
                RESTOREATINSTALL_LOG, mWakelockAcquiredLog, RESTORECOMPLETE_LOG,
                mWakelockReleasedLog);
    }

    private void runDeviceSideProcedure(String procedure) throws Exception {
        checkDeviceTestAsUser(KEY_VALUE_RESTORE_APP_PACKAGE, KEY_VALUE_RESTORE_DEVICE_TEST_NAME,
                procedure, mDefaultBackupUserId);
    }
}
