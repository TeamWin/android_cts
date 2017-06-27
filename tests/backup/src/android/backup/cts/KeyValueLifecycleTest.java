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

package android.backup.cts;

/**
 * Verifies that key methods are called in expected order during backup / restore.
 */
public class KeyValueLifecycleTest extends BaseBackupCtsTest {

    private static final String BACKUP_APP_NAME = "android.backup.kvapp";

    private static final int LOCAL_TRANSPORT_CONFORMING_FILE_SIZE = 5 * 1024;

    public void testExpectedMethodsCalledInOrder() throws Exception {
        if (!isBackupSupported()) {
            return;
        }
        exec("logcat --clear");
        exec("setprop log.tag." + APP_LOG_TAG +" VERBOSE");

        // Make sure there's something to backup
        createTestFileOfSize(BACKUP_APP_NAME, LOCAL_TRANSPORT_CONFORMING_FILE_SIZE);

        // Request backup and wait for it to complete
        exec("bmgr backupnow " + BACKUP_APP_NAME);
        assertTrue("Backup agent not destroyed", waitForLogcat("onDestroy", 10));

        verifyContainsInOrder(execLogcat(),
            "onCreate",
            "Backup requested",
            "onDestroy");

        exec("logcat --clear");

        // Now request restore and wait for it to complete
        exec("bmgr restore " + BACKUP_APP_NAME);
        assertTrue("Backup agent not destroyed", waitForLogcat("onDestroy", 10));

        verifyContainsInOrder(execLogcat(),
            "onCreate",
            "Restore requested",
            "onRestoreFinished",
            "onDestroy");
    }

    private void verifyContainsInOrder(String message, String... substrings) {
        int currentIndex = 0;
        for (String substring : substrings) {
            int substringIndex = message.indexOf(substring, currentIndex);
            if (substringIndex < 0) {
                fail("Didn't find '" + substring + "' in expected order");
            }
            currentIndex = substringIndex + substring.length();
        }
    };

    private String execLogcat() throws Exception {
        return exec("logcat -v brief -d " + APP_LOG_TAG + ":* *:S");
    }
}
