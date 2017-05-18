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

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNull;
import static junit.framework.Assert.assertTrue;

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.log.LogUtil.CLog;

import org.junit.runner.RunWith;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;

/**
 * Test for checking that key/value backup and restore works correctly.
 * It interacts with the app that generates random values and saves them in different shared
 * preferences and files. The app uses BackupAgentHelper to do key/value backup of those values.
 * The tests verifies that the values are restored after the app is uninstalled and reinstalled.
 *
 * NB: The tests uses "bmgr backupnow" for backup, which works on N+ devices.
 */
@RunWith(DeviceJUnit4ClassRunner.class)
public class BackupRestoreHostSideTest extends BaseBackupHostSideTest {
    /** The name of the APK of the app under test */
    private static final String TEST_APP_APK = "CtsBackupRestoreDeviceApp.apk";

    /** The package name of the APK */
    private static final String PACKAGE_UNDER_TEST = "android.backup.cts.backuprestoreapp";

    /** The class name of the main activity in the APK */
    private static final String RANDOM_DATA_ACTIVITY = "KeyValueBackupRandomDataActivity";

    /** Class name of the shared preferences test activity */
    private static final String SHARED_PREFS_ACTIVITY = "SharedPrefsRestoreTestActivity";

    /** The command to launch the random-data backup test activity */
    private static final String CMD_START_RANDOM_DATA_ACTIVITY = String.format(
            "am start -W -a android.intent.action.MAIN -n %s/%s.%s", PACKAGE_UNDER_TEST,
            PACKAGE_UNDER_TEST,
            RANDOM_DATA_ACTIVITY);

    /** Shell commands to launch the shared prefs restore test activity */
    private static final String CMD_START_SHARED_PREFS_ACTIVITY = String.format(
            "am start -W -n %s/%s.%s", PACKAGE_UNDER_TEST, PACKAGE_UNDER_TEST,
            SHARED_PREFS_ACTIVITY);

    /** Shared prefs restore test logging tag */
    private static final String SHARED_PREFS_TAG = "SharedPrefsTest";

    /** The command to clear the user data of the package */
    private static final String CMD_CLEAR_DATA_IN_PACKAGE = String.format(
            "pm clear %s", PACKAGE_UNDER_TEST);

    /**
     * Time we wait before reading the logcat again if the message we want is not logged by the
     * app yet.
     */
    private static final int SMALL_LOGCAT_DELAY_MS = 1000;

    /**
     * Message logged by the app after all the values were loaded from SharedPreferences and files.
     */
    private static final String VALUES_LOADED_MESSAGE = "ValuesLoaded";

    /**
     * Keys for various shared preferences and files saved/read by the app.
     */
    private static final String INT_PREF = "int-pref";
    private static final String BOOL_PREF = "bool-pref";
    private static final String FLOAT_PREF = "float-pref";
    private static final String LONG_PREF = "long-pref";
    private static final String STRING_PREF = "string-pref";
    private static final String TEST_FILE_1 = "test-file-1";
    private static final String TEST_FILE_2 = "test-file-2";

    /** Number of the values saved/restored by the app (keys listed above) */
    private static final int NUMBER_OF_VALUES = 7;

    /**
     * String equivalents of the default values of the shared preferences logged by the app.
     * These values are logged by the app by default if it fails to generate or restore values.
     */
    private static final String DEFAULT_INT_STRING = Integer.toString(0);
    private static final String DEFAULT_BOOL_STRING = Boolean.toString(false);
    private static final String DEFAULT_FLOAT_STRING = Float.toString(0.0f);
    private static final String DEFAULT_LONG_STRING = Long.toString(0L);
    private static final String DEFAULT_STRING_STRING = "null";
    private static final String DEFAULT_FILE_STRING = "empty";

    /*
     *  Shared prefs test activity actions
     */
    static final String INIT_ACTION = "android.backup.cts.backuprestore.INIT";
    static final String UPDATE_ACTION = "android.backup.cts.backuprestore.UPDATE";
    static final String TEST_ACTION = "android.backup.cts.backuprestore.TEST";

    private boolean mIsBackupSupported;
    private boolean mWasBackupEnabled;
    private String mOldTransport;

    /**
     * Map of the shared preferences/files values reported by the app.
     * Format example: INT_PREF -> 17 (string, as found in the logcat).
     */
    private Map<String, String> mSavedValues;

    @Test
    public void testKeyValueBackupAndRestore() throws Exception {
        // Clear app data if any
        mDevice.executeShellCommand(CMD_CLEAR_DATA_IN_PACKAGE);
        // Clear logcat
        mDevice.executeAdbCommand("logcat", "-c");
        // Start the main activity of the app
        mDevice.executeShellCommand(CMD_START_RANDOM_DATA_ACTIVITY);

        // The app will generate some random values onCreate. Save them to mSavedValues
        saveDataValuesReportedByApp();

        // If all the values are default, there is something wrong with the app
        assertNotAllValuesAreDefault();

        // Run backup
        // TODO: make this compatible with N-, potentially by replacing 'backupnow' with 'run'.
        String backupnowOutput = backupNow(PACKAGE_UNDER_TEST);

        assertBackupIsSuccessful(PACKAGE_UNDER_TEST, backupnowOutput);

        assertNull(uninstallPackage(PACKAGE_UNDER_TEST));

        installPackage(TEST_APP_APK);

        mDevice.executeAdbCommand("logcat", "-c");

        // Start the reinstalled app
        mDevice.executeShellCommand(CMD_START_RANDOM_DATA_ACTIVITY);

        // If the app data was restored successfully, the app should not generate new values and
        // the values reported by the app should match values saved in mSavedValues
        assertValuesAreRestored();
    }

    /**
     * Saves the data values reported by the app in {@code mSavedValues}.
     */
    private void saveDataValuesReportedByApp()
            throws InterruptedException, DeviceNotAvailableException {
        mSavedValues = readDataValuesFromLogcat();
        assertEquals(NUMBER_OF_VALUES, mSavedValues.size());
    }

    /**
     * Checks that at least some values in {@code mSavedValues} are different from corresponding
     * default values.
     */
    private void assertNotAllValuesAreDefault() {
        boolean allValuesAreDefault = mSavedValues.get(INT_PREF).equals(DEFAULT_INT_STRING)
                && mSavedValues.get(BOOL_PREF).equals(DEFAULT_BOOL_STRING)
                && mSavedValues.get(FLOAT_PREF).equals(DEFAULT_FLOAT_STRING)
                && mSavedValues.get(LONG_PREF).equals(DEFAULT_LONG_STRING)
                && mSavedValues.get(STRING_PREF).equals(DEFAULT_STRING_STRING)
                && mSavedValues.get(TEST_FILE_1).equals(DEFAULT_FILE_STRING)
                && mSavedValues.get(TEST_FILE_2).equals(DEFAULT_FILE_STRING);

        assertFalse("The values were not changed from default.", allValuesAreDefault);
    }

    /**
     * Reads the values logged by the app and verifies that they are the same as the ones we saved
     * in {@code mSavedValues}.
     */
    private void assertValuesAreRestored()
            throws InterruptedException, DeviceNotAvailableException {
        Map<String, String> restoredValues = readDataValuesFromLogcat();

        // Iterating through mSavedValues (vs. restoredValues) keyset to make sure all of the
        // keys are reported in restored data
        for (String dataType : mSavedValues.keySet()) {
            assertEquals(mSavedValues.get(dataType), restoredValues.get(dataType));
        }
    }

    /**
     * Reads the values that app has reported via logcat and saves them in a map.
     *
     * The app logs the values once they are read from shared preferences or a file.
     * If the values are default ones (i.e., it's the first run of the application), the app then
     * generates random values and saves them in shared preferences or a file.
     * Finally, the app reads the values from shared preferences or a file again and logs them.
     * We are only interested in the final (generated or restored) values.
     * The format of the log messages is "INT_PREF:17".
     *
     * @return Map of the values found in logcat.
     */
    private Map<String, String> readDataValuesFromLogcat()
            throws InterruptedException, DeviceNotAvailableException {
        Map<String, String> result = new HashMap<>();

        long timeout = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(30);

        // The app generates reads, generates and reads values in async tasks fired onCreate.
        // It may take some time for all tasks to finish and for logs to appear, so we check logcat
        // repeatedly until we read VALUES_LOADED_MESSAGE, which is the last message the app logs.
        search:
        while (timeout >= System.currentTimeMillis()) {
            String logs = getLogcatForClass(RANDOM_DATA_ACTIVITY);

            Scanner in = new Scanner(logs);
            while (in.hasNextLine()) {
                String line = in.nextLine();
                // Filter by TAG.
                if (line.startsWith("I/" + RANDOM_DATA_ACTIVITY)) {
                    // Get rid of the TAG.
                    String message = line.split(":", 2)[1].trim();

                    // VALUES_LOADED_MESSAGE is logged by the app when all the values are loaded and
                    // logged so we can stop expecting more lines at this point.
                    if (message.equals(VALUES_LOADED_MESSAGE)) {
                        break search;
                    }

                    // Values are logged by the app in the format "INT_PREF:17".
                    String[] values = message.split(":");
                    if (values.length == 2) {
                        result.put(values[0], values[1]);
                    }
                }
            }
            in.close();

            // In case the key has not been found, wait for the log to update before
            // performing the next search.
            Thread.sleep(SMALL_LOGCAT_DELAY_MS);
        }
        assertTrue("Timeout while reading the app values", timeout > System.currentTimeMillis());
        return result;
    }

    @Test
    public void testSharedPreferencesRestore() throws Exception {
        // Clear app data if any
        mDevice.executeShellCommand(CMD_CLEAR_DATA_IN_PACKAGE);
        // Clear logcat
        mDevice.executeAdbCommand("logcat", "-c");

        // Start the main test activity and generate some data in shared prefs.
        mDevice.executeShellCommand(
                CMD_START_SHARED_PREFS_ACTIVITY + " -a " + INIT_ACTION);
        waitForLogcat(SHARED_PREFS_TAG, "processLaunchCommand: " + INIT_ACTION);

        // Back up that shared prefs state
        backupNow(PACKAGE_UNDER_TEST);

        // Update the shared-prefs contents via the activity, post-backup
        mDevice.executeAdbCommand("logcat", "-c");
        mDevice.executeShellCommand(
                CMD_START_SHARED_PREFS_ACTIVITY + " -a " + UPDATE_ACTION);
        waitForLogcat(SHARED_PREFS_TAG, "processLaunchCommand: " + UPDATE_ACTION);

        // Issue a restore operation for the package, which will rewrite shared prefs
        // out from under the activity's live SharedPreferences instance
        restore(PACKAGE_UNDER_TEST);

        // Tell the activity to report its shared prefs state, and evaluate.
        mDevice.executeAdbCommand("logcat", "-c");
        mDevice.executeShellCommand(
                CMD_START_SHARED_PREFS_ACTIVITY + " -a " + TEST_ACTION);
        final String result = waitForLogcat(SHARED_PREFS_TAG, "Shared prefs changed:");
        assertTrue("Shared prefs instance not reinitialized from disk", result.contains("true"));
    }

    /**
     * Watch logcat until we see a string from Log.i() under the given tag that contains
     * the stated string, and return the line.
     */
    private String waitForLogcat(String tag, String contents)
            throws InterruptedException, DeviceNotAvailableException {
        final long timeout = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(30);

        // Read the shared prefs restore result from the activity
        while (timeout >= System.currentTimeMillis()) {
            String logs = getLogcatForClass(tag);

            Scanner in = new Scanner(logs);
            try {
                while (in.hasNextLine()) {
                    String line = in.nextLine();
                    // Filter by TAG.
                    if (line.startsWith("I/" + tag)) {
                        // Get rid of the TAG.
                        line = line.split(":", 2)[1].trim();
                        if (line.contains(contents)) {
                            return line;
                        }
                    }
                }
            } finally {
                in.close();
            }

            // In case the key has not been found, wait for the log to update before
            // performing the next search.
            Thread.sleep(SMALL_LOGCAT_DELAY_MS);
        }
        assertTrue("Timeout while waiting for logged string: I/" + tag + " : " + contents,
                timeout > System.currentTimeMillis());
        return null;
    }

    /**
     * Returns the logcat string with the tag {@param className} and clears everything else.
     */
    private String getLogcatForClass(String className) throws DeviceNotAvailableException {
        return mDevice.executeAdbCommand("logcat", "-v", "brief", "-d", className + ":I", "*:S");
    }
}
