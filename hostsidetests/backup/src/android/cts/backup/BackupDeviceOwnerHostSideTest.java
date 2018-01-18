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

package android.cts.backup;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import com.android.compatibility.common.tradefed.build.CompatibilityBuildHelper;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.log.LogUtil;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.FileNotFoundException;
import java.lang.Thread;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Test for checking the interaction between backup policies which can be set by a device owner and
 * the backup subsystem.
 */
@RunWith(DeviceJUnit4ClassRunner.class)
public class BackupDeviceOwnerHostSideTest extends BaseBackupHostSideTest {

    private static final long MAX_TRANSPORTS_WAIT_MS = 5000;
    private static final long LIST_TRANSPORTS_RETRY_MS = 200;
    private static final String DEVICE_OWNER_APK = "CtsBackupDeviceOwnerApp.apk";
    private static final String DEVICE_OWNER_PKG = "android.cts.backup.deviceownerapp";
    private static final String DEVICE_OWNER_TEST_CLASS = ".BackupDeviceOwnerTest";
    private static final String DEVICE_OWNER_TEST_NAME = DEVICE_OWNER_PKG + DEVICE_OWNER_TEST_CLASS;
    private static final String ADMIN_RECEIVER_TEST_CLASS =
            DEVICE_OWNER_PKG + ".BackupDeviceAdminReceiver";
    private static final String DEVICE_OWNER_COMPONENT = DEVICE_OWNER_PKG + "/"
            + ADMIN_RECEIVER_TEST_CLASS;

    private boolean mIsDeviceManagementSupported;
    private int mPrimaryUserId;
    private boolean mBackupsInitiallyEnabled;
    private String mOriginalTransport;

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();
        assumeTrue("android.software.backup feature is not supported on this device",
                mIsBackupSupported);
        mIsDeviceManagementSupported = hasDeviceFeature("android.software.device_admin");
        assumeTrue("android.software.device_admin feature is not supported on this device",
                mIsDeviceManagementSupported);
        mBackupsInitiallyEnabled = isBackupEnabled();
        if (mBackupsInitiallyEnabled) {
            mOriginalTransport = getCurrentTransport();
        }
        mPrimaryUserId = getDevice().getPrimaryUserId();
        installAppAsUser(DEVICE_OWNER_APK, mPrimaryUserId);
        if (!getDevice().setDeviceOwner(DEVICE_OWNER_COMPONENT, mPrimaryUserId)) {
            getDevice().removeAdmin(DEVICE_OWNER_COMPONENT, mPrimaryUserId);
            getDevice().uninstallPackage(DEVICE_OWNER_PKG);
            fail("Failed to set device owner");
        }
    }

    @After
    @Override
    public void tearDown() throws Exception {
        try {
            // Clear the policy.
            checkDeviceTest("testClearMandatoryBackupTransport");
            // Re-enable backup service in case something went wrong during the test.
            checkDeviceTest("testEnableBackupService");
            // Re-enable backups if they were originally enabled.
            enableBackup(mBackupsInitiallyEnabled);
            // Restore originally selected transport.
            if (mOriginalTransport != null) {
                setBackupTransport(mOriginalTransport);
            }
        } finally {
            getDevice().removeAdmin(DEVICE_OWNER_COMPONENT, mPrimaryUserId);
            getDevice().uninstallPackage(DEVICE_OWNER_PKG);
            super.tearDown();
        }
    }

    @Test
    public void testMandatoryBackupTransport()  throws Exception {
        // The backup service is initially disabled after the installation of the device owner.
        checkDeviceTest("testBackupServiceDisabled");

        // Try to set the mandatory backup transport to the local transport and verify it is
        // set.
        checkDeviceTest("testSetMandatoryBackupTransport");

        // Verify that backups have been enabled.
        assertTrue("Backups should be enabled after setting the mandatory backup transport",
                isBackupEnabled());

        // Get the transports; eventually wait for the backup subsystem to initialize.
        String[] transports = waitForTransportsAndReturnTheList();

        // Verify that the local transport is selected.
        assertEquals(LOCAL_TRANSPORT, getCurrentTransport());

        // Verify that switching to other than local transport, which is locked by the policy,
        // is not possible.
        for (String transport : transports) {
            setBackupTransport(transport);
            assertEquals(LOCAL_TRANSPORT, getCurrentTransport());
        }

        // Clear the mandatory backup transport policy.
        checkDeviceTest("testClearMandatoryBackupTransport");

        // Verify it is possible to switch the transports again.
        for (String transport : transports) {
            setBackupTransport(transport);
            assertEquals(transport, getCurrentTransport());
        }
    }

    @Test
    public void testMandatoryBackupTransport_withComponents()  throws Exception {
        // The backup service is initially disabled after the installation of the device owner.
        checkDeviceTest("testBackupServiceDisabled");

        // Try to set the mandatory backup transport to the local transport and verify it is
        // set.
        checkDeviceTest("testSetMandatoryBackupTransport");

        // Verify that backups have been enabled.
        assertTrue("Backups should be enabled after setting the mandatory backup transport",
                isBackupEnabled());

        // Get the transports; eventually wait for the backup subsystem to initialize.
        String[] transportComponents = waitForTransportComponentsAndReturnTheList();

        // Verify that the local transport is selected.
        assertEquals(LOCAL_TRANSPORT, getCurrentTransport());

        // Verify that switching to other than local transport, which is locked by the policy,
        // is not possible.
        for (String transportComponent : transportComponents) {
            setBackupTransportComponent(transportComponent);
            assertEquals(LOCAL_TRANSPORT, getCurrentTransport());
        }

        // Clear the mandatory backup transport policy.
        checkDeviceTest("testClearMandatoryBackupTransport");

        // Verify it is possible to switch the transports again.
        for (String transportComponent : transportComponents) {
            assertTrue(setBackupTransportComponent(transportComponent));
        }
    }

    private void installAppAsUser(String appFileName, int userId) throws FileNotFoundException,
            DeviceNotAvailableException {
        installAppAsUser(appFileName, true, userId);
    }

    private void installAppAsUser(String appFileName, boolean grantPermissions, int userId)
            throws FileNotFoundException, DeviceNotAvailableException {
        LogUtil.CLog.d("Installing app " + appFileName + " for user " + userId);
        CompatibilityBuildHelper buildHelper = new CompatibilityBuildHelper(getBuild());
        String result = getDevice().installPackageForUser(
                buildHelper.getTestFile(appFileName), true, grantPermissions, userId, "-t");
        assertNull("Failed to install " + appFileName + " for user " + userId + ": " + result,
                result);
    }

    private void checkDeviceTest(String methodName) throws Exception {
        checkDeviceTest(DEVICE_OWNER_PKG, DEVICE_OWNER_TEST_NAME, methodName);
    }

    private void enableBackup(boolean enable) throws DeviceNotAvailableException {
        getDevice().executeShellCommand("bmgr enable " + enable);
    }

    private String[] waitForTransportsAndReturnTheList()
            throws DeviceNotAvailableException, InterruptedException {
        long startTime = System.currentTimeMillis();
        String output = "";
        do {
            output = getDevice().executeShellCommand("bmgr list transports");
            if (!"No transports available.".equals(output.trim())) {
                break;
            } else {
                output = "";
            }
            Thread.sleep(LIST_TRANSPORTS_RETRY_MS);
        } while (System.currentTimeMillis() - startTime < MAX_TRANSPORTS_WAIT_MS);
        output = output.replace("*", "");
        return output.trim().split("\\s+");
    }

    private String[] waitForTransportComponentsAndReturnTheList()
            throws DeviceNotAvailableException, InterruptedException {
        long startTime = System.currentTimeMillis();
        String[] transportComponents;
        do {
            String output = getDevice().executeShellCommand("bmgr list transports -c");
            transportComponents = output.trim().split("\\s+");
            if (transportComponents.length > 0 && !transportComponents[0].isEmpty()) {
                break;
            }
            Thread.sleep(LIST_TRANSPORTS_RETRY_MS);
        } while (System.currentTimeMillis() - startTime < MAX_TRANSPORTS_WAIT_MS);
        return transportComponents;
    }

    private void setBackupTransport(String transport) throws DeviceNotAvailableException {
        getDevice().executeShellCommand("bmgr transport " + transport);
    }

    private boolean setBackupTransportComponent(String transport)
            throws DeviceNotAvailableException {
        String output = getDevice().executeShellCommand("bmgr transport -c " + transport);
        return output.contains("Success.");
    }
}
