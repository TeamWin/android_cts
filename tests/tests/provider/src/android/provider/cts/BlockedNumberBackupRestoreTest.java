/*
 * Copyright (C) 2016 The Android Open Source Project
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

package android.provider.cts;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.provider.BlockedNumberContract;
import android.telecom.Log;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * CTS tests for backup and restore of blocked numbers using local transport.
 */
// To run the tests in this file w/o running all the cts tests:
// make cts
// cts-tradefed
// run cts -m CtsProviderTestCases --test android.provider.cts.BlockedNumberBackupRestoreTest
public class BlockedNumberBackupRestoreTest extends TestCaseThatRunsIfTelephonyIsEnabled {
    private static final String TAG = "BlockedNumberBackupRestoreTest";
    private static final String LOCAL_BACKUP_COMPONENT =
            "android/com.android.internal.backup.LocalTransport";
    private static final String BLOCKED_NUMBERS_PROVIDER_PACKAGE =
            "com.android.providers.blockednumber";
    private static final int BACKUP_TIMEOUT_MILLIS = 4000;
    private static final Pattern BMGR_ENABLED_PATTERN = Pattern.compile(
            "^Backup Manager currently (enabled|disabled)$");

    private ContentResolver mContentResolver;
    private Context mContext;
    private String mOldTransport;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mContext = getInstrumentation().getContext();
        mContentResolver = mContext.getContentResolver();

        BlockedNumberTestUtils.setDefaultSmsApp(
                true, mContext.getPackageName(), getInstrumentation().getUiAutomation());

        mOldTransport = setBackupTransport(LOCAL_BACKUP_COMPONENT);
        clearBlockedNumbers();
        wipeBackup();
    }

    @Override
    protected void tearDown() throws Exception {
        wipeBackup();
        clearBlockedNumbers();
        setBackupTransport(mOldTransport);

        BlockedNumberTestUtils.setDefaultSmsApp(
                false, mContext.getPackageName(), getInstrumentation().getUiAutomation());

        super.tearDown();
    }

    public void testBackupAndRestoreForSingleNumber() throws Exception {
        if (!hasBackupTransport(LOCAL_BACKUP_COMPONENT)) {
            Log.i(TAG, "skipping BlockedNumberBackupRestoreTest");
        }

        Log.i(TAG, "Adding blocked numbers.");
        insertBlockedNumber("123456789");

        Log.i(TAG, "Running backup.");
        runBackup();
        Thread.sleep(BACKUP_TIMEOUT_MILLIS);

        Log.i(TAG, "Clearing blocked numbers.");
        clearBlockedNumbers();
        verifyBlockedNumbers();

        Log.i(TAG, "Restoring blocked numbers.");
        runRestore();
        Thread.sleep(BACKUP_TIMEOUT_MILLIS);
        verifyBlockedNumbers("123456789");
    }

    public void testBackupAndRestoreWithDeletion() throws Exception {
        if (!hasBackupTransport(LOCAL_BACKUP_COMPONENT)) {
            Log.i(TAG, "skipping BlockedNumberBackupRestoreTest");
        }

        Log.i(TAG, "Adding blocked numbers.");
        insertBlockedNumber("123456789");
        insertBlockedNumber("223456789");
        insertBlockedNumber("323456789");

        Log.i(TAG, "Running backup.");
        runBackup();
        Thread.sleep(BACKUP_TIMEOUT_MILLIS);

        Log.i(TAG, "Deleting blocked number.");
        deleteNumber("123456789");
        verifyBlockedNumbers("223456789", "323456789");

        Log.i(TAG, "Running backup.");
        runBackup();
        Thread.sleep(BACKUP_TIMEOUT_MILLIS);

        Log.i(TAG, "Clearing blocked numbers.");
        clearBlockedNumbers();
        verifyBlockedNumbers();

        Log.i(TAG, "Restoring blocked numbers.");
        runRestore();
        Thread.sleep(BACKUP_TIMEOUT_MILLIS);
        verifyBlockedNumbers("223456789", "323456789");
    }

    private void insertBlockedNumber(String number) {
        ContentValues cv = new ContentValues();
        cv.put(BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER, number);
        mContentResolver.insert(BlockedNumberContract.BlockedNumbers.CONTENT_URI, cv);
    }

    private void deleteNumber(String number) {
        assertEquals(1,
                mContentResolver.delete(
                        BlockedNumberContract.BlockedNumbers.CONTENT_URI,
                        BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER + "= ?",
                        new String[] {number}));
    }

    private void verifyBlockedNumbers(String ... blockedNumbers) {
        assertEquals(blockedNumbers.length,
                mContentResolver.query(
                        BlockedNumberContract.BlockedNumbers.CONTENT_URI, null, null, null, null)
                        .getCount());
        for (String blockedNumber : blockedNumbers) {
            assertTrue(BlockedNumberContract.isBlocked(mContext, blockedNumber));
        }
    }

    private void clearBlockedNumbers() {
        mContentResolver.delete(BlockedNumberContract.BlockedNumbers.CONTENT_URI, null, null);
    }

    private boolean hasBackupTransport(String transport) throws Exception {
        String output = BlockedNumberTestUtils.executeShellCommand(
                getInstrumentation().getUiAutomation(), "bmgr list transports");
        for (String t : output.split(" ")) {
            if ("*".equals(t)) {
                // skip the current selection marker.
                continue;
            } else if (Objects.equals(transport, t)) {
                return true;
            }
        }
        return false;
    }

    private String setBackupTransport(String transport) throws Exception {
        String output = exec("bmgr transport " + transport);
        Pattern pattern = Pattern.compile("\\(formerly (.*)\\)$");
        Matcher matcher = pattern.matcher(output);
        if (matcher.find()) {
            return matcher.group(1);
        } else {
            throw new Exception("non-parsable output setting bmgr transport: " + output);
        }
    }

    private void runBackup() throws Exception {
        exec("bmgr backupnow " + BLOCKED_NUMBERS_PROVIDER_PACKAGE);
    }

    private void runRestore() throws Exception {
        exec("bmgr restore " + BLOCKED_NUMBERS_PROVIDER_PACKAGE);
    }

    private void wipeBackup() throws Exception {
        exec("bmgr wipe " + LOCAL_BACKUP_COMPONENT + " " + BLOCKED_NUMBERS_PROVIDER_PACKAGE);
    }

    private String exec(String command) throws Exception {
        return BlockedNumberTestUtils.executeShellCommand(
                getInstrumentation().getUiAutomation(), command);
    }
}
