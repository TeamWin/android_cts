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

package android.keystore.cts.devicepolicy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.android.compatibility.common.tradefed.build.CompatibilityBuildHelper;
import com.android.tradefed.config.Option;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

/**
 * Base class for device policy tests. It offers utility methods to run tests, set device or profile
 * owner, etc.
 */
@RunWith(DeviceJUnit4ClassRunner.class)
public abstract class BaseDevicePolicyTest extends BaseHostJUnit4Test {

    //The maximum time to wait for user to be unlocked.
    private static final long USER_UNLOCK_TIMEOUT_SEC = 30;
    private static final String USER_STATE_UNLOCKED = "RUNNING_UNLOCKED";

    protected static final String PERMISSION_INTERACT_ACROSS_USERS =
            "android.permission.INTERACT_ACROSS_USERS";

    @Option(
            name = "skip-device-admin-feature-check",
            description = "Flag that allows to skip the check for android.software.device_admin "
                + "and run the tests no matter what. This is useful for system that do not what "
                + "to expose that feature publicly."
    )
    private boolean mSkipDeviceAdminFeatureCheck = false;

    private static final String RUNNER = "androidx.test.runner.AndroidJUnitRunner";

    protected static final int USER_SYSTEM = 0; // From the UserHandle class.

    protected static final int USER_OWNER = USER_SYSTEM;

    private static final long TIMEOUT_USER_REMOVED_MILLIS = TimeUnit.SECONDS.toMillis(15);
    private static final long WAIT_SAMPLE_INTERVAL_MILLIS = 200;

    /**
     * The defined timeout (in milliseconds) is used as a maximum waiting time when expecting the
     * command output from the device. At any time, if the shell command does not output anything
     * for a period longer than defined timeout the Tradefed run terminates.
     */
    private static final long DEFAULT_SHELL_TIMEOUT_MILLIS = TimeUnit.MINUTES.toMillis(20);

    /**
     * Sets timeout (in milliseconds) that will be applied to each test. In the
     * event of a test timeout it will log the results and proceed with executing the next test.
     */
    private static final long DEFAULT_TEST_TIMEOUT_MILLIS = TimeUnit.MINUTES.toMillis(10);

    /**
     * The amount of milliseconds to wait for the remove user calls in {@link #tearDown}.
     * This is a temporary measure until b/114057686 is fixed.
     */
    private static final long USER_REMOVE_WAIT = TimeUnit.SECONDS.toMillis(5);

    /**
     * The amount of milliseconds to wait for the switch user calls in {@link #tearDown}.
     */
    private static final long USER_SWITCH_WAIT = TimeUnit.SECONDS.toMillis(5);

    // From the UserInfo class
    protected static final int FLAG_GUEST = 0x00000004;
    protected static final int FLAG_EPHEMERAL = 0x00000100;
    protected static final int FLAG_MANAGED_PROFILE = 0x00000020;

    /**
     * The {@link android.os.BatteryManager} flags value representing all charging types; {@link
     * android.os.BatteryManager#BATTERY_PLUGGED_AC}, {@link
     * android.os.BatteryManager#BATTERY_PLUGGED_USB}, and {@link
     * android.os.BatteryManager#BATTERY_PLUGGED_WIRELESS}.
     */
    private static final int STAY_ON_WHILE_PLUGGED_IN_FLAGS = 7;

    /**
     * User ID for all users.
     * The value is from the UserHandle class.
     */
    protected static final int USER_ALL = -1;

    private static final String TEST_UPDATE_LOCATION = "/data/local/tmp/cts/deviceowner";

    /**
     * Copied from {@link android.app.admin.DevicePolicyManager
     * .InstallSystemUpdateCallback#UPDATE_ERROR_UPDATE_FILE_INVALID}
     */
    protected static final int UPDATE_ERROR_UPDATE_FILE_INVALID = 3;

    protected CompatibilityBuildHelper mBuildHelper;
    private String mPackageVerifier;

    /** Packages installed as part of the tests */
    private Set<String> mFixedPackages;

    protected int mDeviceOwnerUserId;
    protected int mPrimaryUserId;

    /** Record the initial user ID. */
    protected int mInitialUserId;

    protected boolean mHasAttestation;

    private static final String VERIFY_CREDENTIAL_CONFIRMATION = "Lock credential verified";

    @Rule
    public final DeviceAdminFeaturesCheckerRule mFeaturesCheckerRule =
            new DeviceAdminFeaturesCheckerRule(this);

    @Before
    public void setUp() throws Exception {
        assertNotNull(getBuild());  // ensure build has been set before test is run.

        mFixedPackages = getDevice().getInstalledPackageNames();

        String propertyValue = getDevice().getProperty("ro.product.first_api_level");
        if (propertyValue != null && !propertyValue.isEmpty()) {
            mHasAttestation = Integer.parseInt(propertyValue) >= 26;
        }

        // disable the package verifier to avoid the dialog when installing an app
        mPackageVerifier = getDevice().executeShellCommand(
                "settings get global verifier_verify_adb_installs");
        getDevice().executeShellCommand("settings put global verifier_verify_adb_installs 0");

        if (!isHeadlessSystemUserMode()) {
            mDeviceOwnerUserId = mPrimaryUserId = getPrimaryUser();
        } else {
            // For headless system user, all tests will be executed on current user
            // and therefore, initial user is set as primary user for test purpose.
            mPrimaryUserId = mInitialUserId;
            mDeviceOwnerUserId = USER_SYSTEM;
        }

        getDevice().executeShellCommand(" mkdir " + TEST_UPDATE_LOCATION);

        removeOwners();

        // Unlock keyguard before test
        wakeupAndDismissKeyguard();
        stayAwake();
        // Go to home.
        executeShellCommand("input keyevent KEYCODE_HOME");
    }

    @After
    public void tearDown() throws Exception {
        // reset the package verifier setting to its original value
        getDevice().executeShellCommand("settings put global verifier_verify_adb_installs "
                + mPackageVerifier);
        removeOwners();

        removeTestPackages();
        getDevice().executeShellCommand(" rm -r " + TEST_UPDATE_LOCATION);
    }

    protected void installAppAsUser(String appFileName, int userId) throws FileNotFoundException,
            DeviceNotAvailableException {
        installAppAsUser(appFileName, true, userId);
    }

    protected void installAppAsUser(String appFileName, boolean grantPermissions, int userId)
            throws FileNotFoundException, DeviceNotAvailableException {
        installAppAsUser(appFileName, grantPermissions, /* dontKillApp */ false, userId);
    }

    protected void installAppAsUser(String appFileName, boolean grantPermissions,
            boolean dontKillApp, int userId)
                    throws FileNotFoundException, DeviceNotAvailableException {
        CLog.e("Installing app %s for user %d", appFileName, userId);
        CompatibilityBuildHelper buildHelper = new CompatibilityBuildHelper(getBuild());
        List<String> extraArgs = new LinkedList<>();
        extraArgs.add("-t");
        // Make the test app queryable by other apps via PackageManager APIs.
        extraArgs.add("--force-queryable");
        if (dontKillApp) extraArgs.add("--dont-kill");
        String result = getDevice().installPackageForUser(
                buildHelper.getTestFile(appFileName), true, grantPermissions, userId,
                extraArgs.toArray(new String[extraArgs.size()]));
        assertNull("Failed to install " + appFileName + " for user " + userId + ": " + result,
                result);
    }

    protected void installAppIncremental(String appFileName)
            throws FileNotFoundException, DeviceNotAvailableException {
        final String signatureSuffix = ".idsig";
        CompatibilityBuildHelper buildHelper = new CompatibilityBuildHelper(getBuild());
        final File apk = buildHelper.getTestFile(appFileName);
        assertNotNull(apk);
        final File idsig = buildHelper.getTestFile(appFileName + signatureSuffix);
        assertNotNull(idsig);
        final String remoteApkPath = TEST_UPDATE_LOCATION + "/" + apk.getName();
        final String remoteIdsigPath = remoteApkPath + signatureSuffix;
        assertTrue(getDevice().pushFile(apk, remoteApkPath));
        assertTrue(getDevice().pushFile(idsig, remoteIdsigPath));
        String installResult = getDevice().executeShellCommand(
                "pm install-incremental -t -g " + remoteApkPath);
        assertEquals("Success\n", installResult);
    }

    protected void installDeviceOwnerApp(String apk) throws Exception {
        installAppAsUser(apk, mDeviceOwnerUserId);

        if (isHeadlessSystemUserMode()) {
            // Need to explicitly install the device owner app for the current user (rather than
            // relying on DPMS) so it has the same privileges (like INTERACT_ACROSS_USERS) as the
            // app running on system user, otherwise some tests might fail
            installAppAsUser(apk, mPrimaryUserId);
        }
    }

    protected void removeDeviceOwnerAdmin(String componentName) throws DeviceNotAvailableException {
        // Don't fail as it could hide the real failure from the test method
        if (!removeAdmin(componentName, mDeviceOwnerUserId)) {
            CLog.e("Failed to remove device owner %s on user %d", componentName,
                    mDeviceOwnerUserId);
        }
        if (isHeadlessSystemUserMode() && !removeAdmin(componentName, mPrimaryUserId)) {
            CLog.e("Failed to remove profile owner %s on user %d", componentName, mPrimaryUserId);
        }
    }

    protected void forceStopPackageForUser(String packageName, int userId) throws Exception {
        // TODO Move this logic to ITestDevice
        executeShellCommand("am force-stop --user " + userId + " " + packageName);
    }

    protected String executeShellCommand(String commandTemplate, Object...args) throws Exception {
        return executeShellCommand(String.format(commandTemplate, args));
    }

    protected String executeShellCommand(String command) throws Exception {
        CLog.d("Starting command %s", command);
        String commandOutput = getDevice().executeShellCommand(command);
        CLog.d("Output for command %s: %s", command, commandOutput);
        return commandOutput;
    }

    protected ArrayList<Integer> listUsers() throws DeviceNotAvailableException {
        return getDevice().listUsers();
    }

    protected  ArrayList<Integer> listRunningUsers() throws DeviceNotAvailableException {
        ArrayList<Integer> runningUsers = new ArrayList<>();
        for (int userId : listUsers()) {
            if (getDevice().isUserRunning(userId)) {
                runningUsers.add(userId);
            }
        }
        return runningUsers;
    }

    /** Removes any packages that were installed during the test. */
    protected void removeTestPackages() throws Exception {
        for (String packageName : getDevice().getUninstallablePackageNames()) {
            if (mFixedPackages.contains(packageName)) {
                continue;
            }
            CLog.w("removing leftover package: " + packageName);
            getDevice().uninstallPackage(packageName);
        }
    }

    protected void runDeviceTestsAsUser(
            String pkgName, @Nullable String testClassName, int userId)
            throws DeviceNotAvailableException {
        runDeviceTestsAsUser(pkgName, testClassName, /* testMethodName= */ null, userId);
    }

    protected void runDeviceTestsAsUser(
            String pkgName, @Nullable String testClassName, String testMethodName, int userId)
            throws DeviceNotAvailableException {
        Map<String, String> params = Collections.emptyMap();
        runDeviceTestsAsUser(pkgName, testClassName, testMethodName, userId, params);
    }

    protected void runDeviceTestsAsUser(
            String pkgName, @Nullable String testClassName,
            @Nullable String testMethodName, int userId,
            Map<String, String> params) throws DeviceNotAvailableException {
        if (testClassName != null && testClassName.startsWith(".")) {
            testClassName = pkgName + testClassName;
        }

        CLog.i("runDeviceTestsAsUser(): user=%d, pkg=%s class=%s, test=%s", userId, pkgName,
                testClassName, testMethodName);
        runDeviceTests(
                getDevice(),
                RUNNER,
                pkgName,
                testClassName,
                testMethodName,
                userId,
                DEFAULT_TEST_TIMEOUT_MILLIS,
                DEFAULT_SHELL_TIMEOUT_MILLIS,
                0L /* maxInstrumentationTimeoutMs */,
                true /* checkResults */,
                false /* isHiddenApiCheckDisabled */,
                params);
    }

    protected int getPrimaryUser() throws DeviceNotAvailableException {
        return getDevice().getPrimaryUserId();
    }

    protected int getCurrentUser() throws DeviceNotAvailableException {
        return getDevice().getCurrentUser();
    }

    protected int getUserSerialNumber(int userId) throws DeviceNotAvailableException{
        // TODO: Move this logic to ITestDevice.
        // dumpsys user output contains lines like "UserInfo{0:Owner:13} serialNo=0 isPrimary=true"
        final Pattern pattern =
                Pattern.compile("UserInfo\\{" + userId + ":[^\\n]*\\sserialNo=(\\d+)\\s");
        final String commandOutput = getDevice().executeShellCommand("dumpsys user");
        final Matcher matcher = pattern.matcher(commandOutput);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }
        fail("Couldn't find serial number for user " + userId);
        return -1;
    }

    private String setDeviceAdminInner(String componentName, int userId)
            throws DeviceNotAvailableException {
        String command = "dpm set-active-admin --user " + userId + " '" + componentName + "'";
        String commandOutput = getDevice().executeShellCommand(command);
        return commandOutput;
    }

    protected void setDeviceAdmin(String componentName, int userId)
            throws DeviceNotAvailableException {
        String commandOutput = setDeviceAdminInner(componentName, userId);
        CLog.d("Output for command " + commandOutput
                + ": " + commandOutput);
        assertTrue(commandOutput + " expected to start with \"Success:\"",
                commandOutput.startsWith("Success:"));
    }

    protected boolean setDeviceOwner(String componentName, int userId, boolean expectFailure)
            throws DeviceNotAvailableException {
        String command = "dpm set-device-owner --user " + userId + " '" + componentName + "'";
        String commandOutput = getDevice().executeShellCommand(command);
        boolean success = commandOutput.startsWith("Success:");
        // If we succeeded always log, if we are expecting failure don't log failures
        // as call stacks for passing tests confuse the logs.
        if (success || !expectFailure) {
            CLog.d("Output for command " + command + ": " + commandOutput);
        } else {
            CLog.d("Command Failed " + command);
        }
        return success;
    }

    protected void setDeviceOwnerExpectingFailure(String componentName, int userId)
            throws Exception {
        assertFalse(setDeviceOwner(componentName, userId, /* expectFailure =*/ true));
    }


    protected void affiliateUsers(String deviceAdminPkg, int userId1, int userId2)
            throws Exception {
        CLog.d("Affiliating users %d and %d on admin package %s", userId1, userId2, deviceAdminPkg);
        runDeviceTestsAsUser(
                deviceAdminPkg, ".AffiliationTest", "testSetAffiliationId1", userId1);
        runDeviceTestsAsUser(
                deviceAdminPkg, ".AffiliationTest", "testSetAffiliationId1", userId2);
    }

    protected boolean removeAdmin(String componentName, int userId)
            throws DeviceNotAvailableException {
        String command = "dpm remove-active-admin --user " + userId + " '" + componentName + "'";
        String commandOutput = getDevice().executeShellCommand(command);
        CLog.d("Output for command " + command + ": " + commandOutput);
        return commandOutput.startsWith("Success:");
    }

    // Tries to remove and profile or device owners it finds.
    protected void removeOwners() throws DeviceNotAvailableException {
        String command = "dumpsys device_policy";
        String commandOutput = getDevice().executeShellCommand(command);
        String[] lines = commandOutput.split("\\r?\\n");
        for (int i = 0; i < lines.length; ++i) {
            String line = lines[i].trim();
            if (line.contains("Profile Owner")) {
                // Line is "Profile owner (User <id>):
                String[] tokens = line.split("\\(|\\)| ");
                int userId = Integer.parseInt(tokens[4]);
                i++;
                line = lines[i].trim();
                // Line is admin=ComponentInfo{<component>}
                tokens = line.split("\\{|\\}");
                String componentName = tokens[1];
                CLog.w("Cleaning up profile owner " + userId + " " + componentName);
                removeAdmin(componentName, userId);
            } else if (line.contains("Device Owner:")) {
                i++;
                line = lines[i].trim();
                // Line is admin=ComponentInfo{<component>}
                String[] tokens = line.split("\\{|\\}");
                String componentName = tokens[1];
                // Skip to user id line.
                i += 4;
                line = lines[i].trim();
                // Line is User ID: <N>
                tokens = line.split(":");
                int userId = Integer.parseInt(tokens[1].trim());
                CLog.w("Cleaning up device owner " + userId + " " + componentName);
                removeAdmin(componentName, userId);
            }
        }
    }

    protected void wakeupAndDismissKeyguard() throws Exception {
        executeShellCommand("input keyevent KEYCODE_WAKEUP");
        executeShellCommand("wm dismiss-keyguard");
    }

    private void stayAwake() throws Exception {
        executeShellCommand(
                "settings put global stay_on_while_plugged_in " + STAY_ON_WHILE_PLUGGED_IN_FLAGS);
    }

    // TODO (b/174775905) remove after exposing the check from ITestDevice.
    boolean isHeadlessSystemUserMode() throws DeviceNotAvailableException {
        return isHeadlessSystemUserMode(getDevice());
    }

    // TODO (b/174775905) remove after exposing the check from ITestDevice.
    public static boolean isHeadlessSystemUserMode(ITestDevice device)
            throws DeviceNotAvailableException {
        final String result = device
                .executeShellCommand("getprop ro.fw.mu.headless_system_user").trim();
        return "true".equalsIgnoreCase(result);
    }

    /**
     * Generates instrumentation arguments that indicate the device-side test is exercising device
     * owner APIs.
     *
     * <p>This is needed for hostside tests that use the same class hierarchy for both device and
     * profile owner tests, as on headless system user mode the test side must decide whether to
     * use its "local DPC" or wrap the calls to the system user DPC.
     */
    protected static Map<String, String> getParamsForDeviceOwnerTest() {
        Map<String, String> params = new HashMap<>();
        params.put("admin_type", "DeviceOwner");
        return params;
    }
}
