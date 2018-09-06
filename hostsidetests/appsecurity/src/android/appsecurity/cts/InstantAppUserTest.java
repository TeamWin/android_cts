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

package android.appsecurity.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import android.platform.test.annotations.AppModeFull;

import com.android.compatibility.common.tradefed.build.CompatibilityBuildHelper;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests for ephemeral packages.
 */
@RunWith(DeviceJUnit4ClassRunner.class)
@AppModeFull(reason = "Already handles instant installs when needed")
public class InstantAppUserTest extends BaseHostJUnit4Test {

    // a normally installed application
    private static final String NORMAL_APK = "CtsEphemeralTestsNormalApp.apk";
    private static final String NORMAL_PKG = "com.android.cts.normalapp";

    // a normally installed application with implicitly exposed components
    private static final String IMPLICIT_APK = "CtsEphemeralTestsImplicitApp.apk";
    private static final String IMPLICIT_PKG = "com.android.cts.implicitapp";

    // the first ephemerally installed application
    private static final String EPHEMERAL_1_APK = "CtsEphemeralTestsEphemeralApp1.apk";
    private static final String EPHEMERAL_1_PKG = "com.android.cts.ephemeralapp1";

    // an application to verify instant/full app per user
    private static final String USER_APK = "CtsEphemeralTestsUserApp.apk";
    private static final String USER_PKG = "com.android.cts.userapp";

    private static final String USER_TEST_APK = "CtsEphemeralTestsUserAppTest.apk";
    private static final String USER_TEST_PKG = "com.android.cts.userapptest";

    private static final String TEST_CLASS = ".ClientTest";

    private boolean mSupportsMultiUser;
    private int mPrimaryUserId;
    private int[] mTestUser = new int[2];

    @Before
    public void setUp() throws Exception {
        // This test only runs when we have at least 3 users to work with
        final int[] users = Utils.prepareMultipleUsers(getDevice(), 3);
        mSupportsMultiUser = (users.length == 3);
        if (!mSupportsMultiUser) {
            return;
        }
        mPrimaryUserId = getDevice().getPrimaryUserId();
        mTestUser[0] = users[1];
        mTestUser[1] = users[2];
        getDevice().switchUser(mTestUser[0]);
        uninstallTestPackages();
        installTestPackages();
    }

    @After
    public void tearDown() throws Exception {
        if (!mSupportsMultiUser) {
            return;
        }
        uninstallTestPackages();
        getDevice().switchUser(mPrimaryUserId);
    }

    // each connection to an exposed component needs to run in its own test to
    // avoid sharing state. once an instant app is exposed to a component, it's
    // exposed until the device restarts or the instant app is removed.
    @Test
    public void testStartExposed01() throws Exception {
        if (!mSupportsMultiUser) {
            return;
        }
        runDeviceTestsAsUser(EPHEMERAL_1_PKG, TEST_CLASS, "testStartExposed01", mTestUser[0]);
    }
    @Test
    public void testStartExposed02() throws Exception {
        if (!mSupportsMultiUser) {
            return;
        }
        runDeviceTestsAsUser(EPHEMERAL_1_PKG, TEST_CLASS, "testStartExposed02", mTestUser[0]);
    }
    @Test
    public void testStartExposed03() throws Exception {
        if (!mSupportsMultiUser) {
            return;
        }
        runDeviceTestsAsUser(EPHEMERAL_1_PKG, TEST_CLASS, "testStartExposed03", mTestUser[0]);
    }
    @Test
    public void testStartExposed04() throws Exception {
        if (!mSupportsMultiUser) {
            return;
        }
        runDeviceTestsAsUser(EPHEMERAL_1_PKG, TEST_CLASS, "testStartExposed04", mTestUser[0]);
    }
    @Test
    public void testStartExposed05() throws Exception {
        if (!mSupportsMultiUser) {
            return;
        }
        runDeviceTestsAsUser(EPHEMERAL_1_PKG, TEST_CLASS, "testStartExposed05", mTestUser[0]);
    }
    @Test
    public void testStartExposed06() throws Exception {
        if (!mSupportsMultiUser) {
            return;
        }
        runDeviceTestsAsUser(EPHEMERAL_1_PKG, TEST_CLASS, "testStartExposed06", mTestUser[0]);
    }
    @Test
    public void testStartExposed07() throws Exception {
        if (!mSupportsMultiUser) {
            return;
        }
        runDeviceTestsAsUser(EPHEMERAL_1_PKG, TEST_CLASS, "testStartExposed07", mTestUser[0]);
    }
    @Test
    public void testStartExposed08() throws Exception {
        if (!mSupportsMultiUser) {
            return;
        }
        runDeviceTestsAsUser(EPHEMERAL_1_PKG, TEST_CLASS, "testStartExposed08", mTestUser[0]);
    }
    @Test
    public void testStartExposed09() throws Exception {
        if (!mSupportsMultiUser) {
            return;
        }
        runDeviceTestsAsUser(EPHEMERAL_1_PKG, TEST_CLASS, "testStartExposed09", mTestUser[0]);
    }
    @Test
    public void testStartExposed10() throws Exception {
        if (!mSupportsMultiUser) {
            return;
        }
        runDeviceTestsAsUser(EPHEMERAL_1_PKG, TEST_CLASS, "testStartExposed10", mTestUser[0]);
    }

    @Test
    public void testInstallInstant() throws Exception {
        if (!mSupportsMultiUser) {
            return;
        }
        installInstantApp(USER_APK);
        runDeviceTestsAsUser(USER_TEST_PKG, TEST_CLASS, "testQueryInstant", mPrimaryUserId);
        runDeviceTestsAsUser(USER_TEST_PKG, TEST_CLASS, "testQueryInstant", mTestUser[0]);
        runDeviceTestsAsUser(USER_TEST_PKG, TEST_CLASS, "testQueryInstant", mTestUser[1]);
    }

    @Test
    public void testInstallFull() throws Exception {
        if (!mSupportsMultiUser) {
            return;
        }
        installApp(USER_APK);
        runDeviceTestsAsUser(USER_TEST_PKG, TEST_CLASS, "testQueryFull", mPrimaryUserId);
        runDeviceTestsAsUser(USER_TEST_PKG, TEST_CLASS, "testQueryFull", mTestUser[0]);
        runDeviceTestsAsUser(USER_TEST_PKG, TEST_CLASS, "testQueryFull", mTestUser[1]);
    }

    @Test
    public void testInstallMultiple() throws Exception {
        if (!mSupportsMultiUser) {
            return;
        }
        installAppAsUser(USER_APK, mPrimaryUserId);
        installExistingInstantAppAsUser(USER_PKG, mTestUser[0]);
        installExistingFullAppAsUser(USER_PKG, mTestUser[1]);
        runDeviceTestsAsUser(USER_TEST_PKG, TEST_CLASS, "testQueryFull", mPrimaryUserId);
        runDeviceTestsAsUser(USER_TEST_PKG, TEST_CLASS, "testQueryInstant", mTestUser[0]);
        runDeviceTestsAsUser(USER_TEST_PKG, TEST_CLASS, "testQueryFull", mTestUser[1]);
    }

    @Test
    public void testUpgradeExisting() throws Exception {
        if (!mSupportsMultiUser) {
            return;
        }
        installInstantApp(USER_APK);
        runDeviceTestsAsUser(USER_TEST_PKG, TEST_CLASS, "testQueryInstant", mPrimaryUserId);
        runDeviceTestsAsUser(USER_TEST_PKG, TEST_CLASS, "testQueryInstant", mTestUser[0]);
        runDeviceTestsAsUser(USER_TEST_PKG, TEST_CLASS, "testQueryInstant", mTestUser[1]);

        installExistingFullAppAsUser(USER_PKG, mTestUser[0]);
        runDeviceTestsAsUser(USER_TEST_PKG, TEST_CLASS, "testQueryInstant", mPrimaryUserId);
        runDeviceTestsAsUser(USER_TEST_PKG, TEST_CLASS, "testQueryFull", mTestUser[0]);
        runDeviceTestsAsUser(USER_TEST_PKG, TEST_CLASS, "testQueryInstant", mTestUser[1]);

        installExistingFullAppAsUser(USER_PKG, mTestUser[1]);
        runDeviceTestsAsUser(USER_TEST_PKG, TEST_CLASS, "testQueryInstant", mPrimaryUserId);
        runDeviceTestsAsUser(USER_TEST_PKG, TEST_CLASS, "testQueryFull", mTestUser[0]);
        runDeviceTestsAsUser(USER_TEST_PKG, TEST_CLASS, "testQueryFull", mTestUser[1]);
    }

    @Test
    public void testReplaceExisting() throws Exception {
        if (!mSupportsMultiUser) {
            return;
        }
        installInstantApp(USER_APK);
        runDeviceTestsAsUser(USER_TEST_PKG, TEST_CLASS, "testQueryInstant", mPrimaryUserId);
        runDeviceTestsAsUser(USER_TEST_PKG, TEST_CLASS, "testQueryInstant", mTestUser[0]);
        runDeviceTestsAsUser(USER_TEST_PKG, TEST_CLASS, "testQueryInstant", mTestUser[1]);

        replaceFullAppAsUser(USER_APK, mTestUser[0]);
        runDeviceTestsAsUser(USER_TEST_PKG, TEST_CLASS, "testQueryInstant", mPrimaryUserId);
        runDeviceTestsAsUser(USER_TEST_PKG, TEST_CLASS, "testQueryFull", mTestUser[0]);
        runDeviceTestsAsUser(USER_TEST_PKG, TEST_CLASS, "testQueryInstant", mTestUser[1]);

        replaceFullAppAsUser(USER_APK, mTestUser[1]);
        runDeviceTestsAsUser(USER_TEST_PKG, TEST_CLASS, "testQueryInstant", mPrimaryUserId);
        runDeviceTestsAsUser(USER_TEST_PKG, TEST_CLASS, "testQueryFull", mTestUser[0]);
        runDeviceTestsAsUser(USER_TEST_PKG, TEST_CLASS, "testQueryFull", mTestUser[1]);
    }

    private void installTestPackages() throws Exception {
        installApp(NORMAL_APK);
        installApp(IMPLICIT_APK);
        installInstantApp(EPHEMERAL_1_APK);
        installApp(USER_TEST_APK);
    }

    private void uninstallTestPackages() throws Exception {
        getDevice().uninstallPackage(NORMAL_PKG);
        getDevice().uninstallPackage(IMPLICIT_APK);
        getDevice().uninstallPackage(EPHEMERAL_1_PKG);
        getDevice().uninstallPackage(USER_TEST_PKG);
        getDevice().uninstallPackage(USER_PKG);
    }

    private void runDeviceTestsAsUser(String packageName, String testClassName,
            String testMethodName, int userId)
            throws DeviceNotAvailableException {
        Utils.runDeviceTests(getDevice(), packageName, testClassName, testMethodName, userId);
    }

    private void installApp(String apk) throws Exception {
        CompatibilityBuildHelper buildHelper = new CompatibilityBuildHelper(getBuild());
        assertNull(getDevice().installPackage(buildHelper.getTestFile(apk), false));
    }

    private void installInstantApp(String apk) throws Exception {
        CompatibilityBuildHelper buildHelper = new CompatibilityBuildHelper(getBuild());
        assertNull(getDevice().installPackage(buildHelper.getTestFile(apk), false, "--instant"));
    }

    private void installAppAsUser(String apk, int userId) throws Exception {
        CompatibilityBuildHelper buildHelper = new CompatibilityBuildHelper(getBuild());
        assertNull(getDevice().installPackageForUser(buildHelper.getTestFile(apk), false, userId));
    }

    private void replaceFullAppAsUser(String apk, int userId) throws Exception {
        CompatibilityBuildHelper buildHelper = new CompatibilityBuildHelper(getBuild());
        assertNull(getDevice().installPackageForUser(
                buildHelper.getTestFile(apk), true, userId, "--full"));
    }

    private void installExistingInstantAppAsUser(String packageName, int userId) throws Exception {
        final String installString =
                "Package " + packageName + " installed for user: " + userId + "\n";
        CompatibilityBuildHelper buildHelper = new CompatibilityBuildHelper(getBuild());
        assertEquals(installString, getDevice().executeShellCommand(
                "cmd package install-existing --instant"
                        + " --user " + Integer.toString(userId)
                        + " " + packageName));
    }

    private void installExistingFullAppAsUser(String packageName, int userId) throws Exception {
        final String installString =
                "Package " + packageName + " installed for user: " + userId + "\n";
        CompatibilityBuildHelper buildHelper = new CompatibilityBuildHelper(getBuild());
        assertEquals(installString, getDevice().executeShellCommand(
                "cmd package install-existing --full"
                        + " --user " + Integer.toString(userId)
                        + " " + packageName));
    }
}
