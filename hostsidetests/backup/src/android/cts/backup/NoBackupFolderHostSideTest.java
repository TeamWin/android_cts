/*
 * Copyright (C) 2017 The Android Open Source Project
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

import static org.junit.Assert.assertTrue;

import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test checking that files created by an app are restored successfully after a backup, but that
 * files put in the folder provided by getNoBackupFilesDir() [files/no_backup] are NOT backed up.
 *
 * Invokes device side tests provided by android.cts.backup.fullbackupapp.FullbackupTest.
 */
@RunWith(DeviceJUnit4ClassRunner.class)
public class NoBackupFolderHostSideTest extends BaseBackupHostSideTest {

    private static final String TESTS_APP_NAME = "android.cts.backup.fullbackupapp";
    private static final String DEVICE_TEST_CLASS_NAME = TESTS_APP_NAME + ".FullbackupTest";

    @Test
    public void testNoBackupFolder() throws Exception {
        // Generate the files that are going to be backed up.
        checkDeviceTest(TESTS_APP_NAME, DEVICE_TEST_CLASS_NAME, "createFiles");

        // Do a backup
        String backupnowOutput = backupNow(TESTS_APP_NAME);

        assertBackupIsSuccessful(TESTS_APP_NAME, backupnowOutput);

        // Delete the files
        checkDeviceTest(TESTS_APP_NAME, DEVICE_TEST_CLASS_NAME, "deleteFilesAfterBackup");

        // Do a restore
        String restoreOutput = restore(TESTS_APP_NAME);

        assertRestoreIsSuccessful(restoreOutput);

        // Check that the right files were restored
        checkDeviceTest(TESTS_APP_NAME, DEVICE_TEST_CLASS_NAME, "checkRestoredFiles");
    }
}
