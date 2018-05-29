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

package android.backup.cts.backup;

import com.android.cts.tradefed.build.CtsBuildHelper;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.DeviceTestCase;
import com.android.tradefed.testtype.IBuildReceiver;
import com.android.tradefed.log.LogUtil.CLog;

import java.io.FileNotFoundException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Test for checking that key/value backup and restore works correctly.
 * It interacts with the app that generates random values and saves them in different shared
 * preferences and files. The app uses BackupAgentHelper to do key/value backup of those values.
 * The tests verifies that the values are restored after the app is uninstalled and reinstalled.
 *
 */
public class BackupRestoreHostSideTest extends DeviceTestCase implements IBuildReceiver {

    private static final String LOG_TAG = "BackupRestoreHostSideTest";

    /** Value of PackageManager.FEATURE_BACKUP */
    private static final String FEATURE_BACKUP = "android.software.backup";

    private static final String LOCAL_TRANSPORT =
            "android/com.android.internal.backup.LocalTransport";

    /** The name of the APK of the app under test */
    private static final String TEST_APP_APK = "CtsBackupRestoreDeviceApp.apk";

    /** The package name of the APK */
    private static final String PACKAGE_UNDER_TEST = "android.backup.cts.backuprestoreapp";

    /** The class name of the main activity in the APK */
    private static final String CLASS_UNDER_TEST = "KeyValueBackupRandomDataActivity";

    /** The command to launch the main activity */
    private static final String START_ACTIVITY_UNDER_TEST_COMMAND = String.format(
            "am start -W -a android.intent.action.MAIN -n %s/%s.%s", PACKAGE_UNDER_TEST,
            PACKAGE_UNDER_TEST,
            CLASS_UNDER_TEST);

    /** The command to clear the user data of the package */
    private static final String CLEAR_DATA_IN_PACKAGE_UNDER_TEST_COMMAND = String.format(
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

    private boolean mIsBackupSupported;
    private boolean mWasBackupEnabled;
    private String mOldTransport;
    private ITestDevice mDevice;
    private HashSet<String> mAvailableFeatures;
    private CtsBuildHelper mBuild;

    /**
     * Map of the shared preferences/files values reported by the app.
     * Format example: INT_PREF -> 17 (string, as found in the logcat).
     */
    private Map<String, String> mSavedValues;

    @Override
    public void setBuild(IBuildInfo buildInfo) {
        mBuild = CtsBuildHelper.createBuildHelper(buildInfo);
    }

    @Override
    public void setUp() throws DeviceNotAvailableException, Exception {
        mDevice = getDevice();
        mIsBackupSupported = hasDeviceFeature(FEATURE_BACKUP);
        if(mIsBackupSupported){
		// Enable backup and select local backup transport
		assertTrue("LocalTransport should be available.", hasBackupTransport(LOCAL_TRANSPORT));
		mWasBackupEnabled = enableBackup(true);
		mOldTransport = setBackupTransport(LOCAL_TRANSPORT);
		assertNotNull(mBuild);
	}
    }

    @Override
    public void tearDown() throws Exception {
        if (mIsBackupSupported) {
            setBackupTransport(mOldTransport);
            enableBackup(mWasBackupEnabled);
        }
    }

    public void testKeyValueBackupAndRestore() throws Exception {
	if(!mIsBackupSupported){
		return;
	}
	assertNull(mDevice.installPackage(mBuild.getTestApp(TEST_APP_APK), true));
        // Clear app data if any
        mDevice.executeShellCommand(CLEAR_DATA_IN_PACKAGE_UNDER_TEST_COMMAND);
        // Clear logcat
        mDevice.executeAdbCommand("logcat", "-c");
        // Start the main activity of the app
        mDevice.executeShellCommand(START_ACTIVITY_UNDER_TEST_COMMAND);

        // The app will generate some random values onCreate. Save them to mSavedValues
        saveDataValuesReportedByApp();

        // If all the values are default, there is something wrong with the app
        assertNotAllValuesAreDefault();

        // Run backup
        mDevice.executeShellCommand("bmgr run");

        assertBackupFinished();

        mDevice.uninstallPackage(PACKAGE_UNDER_TEST);

        assertNull(mDevice.installPackage(mBuild.getTestApp(TEST_APP_APK), true));

        mDevice.executeAdbCommand("logcat", "-c");

        // Start the reinstalled app
        mDevice.executeShellCommand(START_ACTIVITY_UNDER_TEST_COMMAND);

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
     * Parsing logcat after "bmgr run" command and checking that the backup pass is finished before
     * we move on to avoid a race condition.
     *
     * Expected format: "BackupManagerService: Backup pass finished"
     */
    private void assertBackupFinished() throws DeviceNotAvailableException {
        boolean backupFinished = false;
        long timeout = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(10 * 60); // 10 min
        while (timeout >= System.currentTimeMillis() && !backupFinished) {
            String logs = getLogcatForClass("BackupManagerService");

            Scanner in = new Scanner(logs);
            while (in.hasNextLine()) {
                String line = in.nextLine();
                if (line.contains("Backup pass finished")) {
                    backupFinished = true;
                    break;
                }
            }
            in.close();
        }

        assertTrue("The backup pass never finished.", backupFinished);
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
            String logs = getLogcatForClass(CLASS_UNDER_TEST);

            Scanner in = new Scanner(logs);
            while (in.hasNextLine()) {
                String line = in.nextLine();
                // Filter by TAG.
                if (line.startsWith("I/" + CLASS_UNDER_TEST)) {
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

    /**
     * Returns the logcat string with the tag {@param className} and clears everything else.
     */
    private String getLogcatForClass(String className) throws DeviceNotAvailableException {
        return mDevice.executeAdbCommand("logcat", "-v", "brief", "-d",
                className + ":I", "*:S");
    }

    // Copied over from BackupQuotaTest
    private boolean enableBackup(boolean enable) throws Exception {
        boolean previouslyEnabled;
        String output = mDevice.executeShellCommand("bmgr enabled");
        Pattern pattern = Pattern.compile("^Backup Manager currently (enabled|disabled)$");
        Matcher matcher = pattern.matcher(output.trim());
        if (matcher.find()) {
            previouslyEnabled = "enabled".equals(matcher.group(1));
        } else {
            throw new RuntimeException("non-parsable output setting bmgr enabled: " + output);
        }

        mDevice.executeShellCommand("bmgr enable " + enable);
        return previouslyEnabled;
    }

    // Copied over from BackupQuotaTest
    private String setBackupTransport(String transport) throws Exception {
        String output = mDevice.executeShellCommand("bmgr transport " + transport);
        Pattern pattern = Pattern.compile("\\(formerly (.*)\\)$");
        Matcher matcher = pattern.matcher(output);
        if (matcher.find()) {
            return matcher.group(1);
        } else {
            throw new RuntimeException("non-parsable output setting bmgr transport: " + output);
        }
    }

    // Copied over from BackupQuotaTest
    private boolean hasBackupTransport(String transport) throws Exception {
	CLog.i("In method hasBackupTransport");
        String output = mDevice.executeShellCommand("bmgr list transports");
        for (String t : output.split(" ")) {
            if (transport.equals(t.trim())) {
                return true;
            }
        }
        return false;
    }

    private boolean hasDeviceFeature(String requiredFeature) throws DeviceNotAvailableException {
        if (mAvailableFeatures == null) {
            String command = "pm list features";
            String commandOutput = getDevice().executeShellCommand(command);
	    CLog.i("Output for command " + command + ": " + commandOutput);

            // Extract the id of the new user.
            mAvailableFeatures = new HashSet<>();
            for (String feature: commandOutput.split("\\s+")) {
                // Each line in the output of the command has the format "feature:{FEATURE_VALUE}".
                String[] tokens = feature.split(":");
                assertTrue("\"" + feature + "\" expected to have format feature:{FEATURE_VALUE}",
                        tokens.length > 1);
                assertEquals(feature, "feature", tokens[0]);
                mAvailableFeatures.add(tokens[1]);
            }
        }
        boolean result = mAvailableFeatures.contains(requiredFeature);
        if (!result) {
		CLog.d("Device doesn't have required feature "+ requiredFeature + ". Test won't run.");
        }
        return result;
    }
}
