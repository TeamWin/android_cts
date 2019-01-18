/*
 * Copyright (C) 2019 The Android Open Source Project
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

package android.cts.backup;

import static org.junit.Assert.assertTrue;

import android.platform.test.annotations.AppModeFull;

import com.android.compatibility.common.util.BackupUtils;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Test the backup and restore flow for a key-value app in a managed profile. */
@RunWith(DeviceJUnit4ClassRunner.class)
@AppModeFull
public class ManagedProfileKeyValueBackupRestoreHostSideTest
        extends BaseMultiUserBackupHostSideTest {
    private static final String TEST_APK = "CtsProfileKeyValueApp.apk";
    private static final String TEST_PACKAGE = "android.cts.backup.profilekeyvalueapp";
    private static final String DEVICE_TEST_NAME =
            TEST_PACKAGE + ".ProfileKeyValueBackupRestoreTest";

    private final BackupUtils mBackupUtils = getBackupUtils();
    private ITestDevice mDevice;
    private int mProfileUserId;
    private String mTransport;

    /** Create the managed profile, switch to the local transport and setup the test package. */
    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();
        mDevice = getDevice();

        // Create profile user.
        int parentUserId = mDevice.getCurrentUser();
        mProfileUserId = createManagedProfileUser(parentUserId, "Profile1");
        startUserAndInitializeForBackup(mProfileUserId);

        // Switch to local transport.
        mTransport = mBackupUtils.getLocalTransportName();
        assertTrue(
                "User doesn't have local transport",
                mBackupUtils.userHasBackupTransport(mTransport, mProfileUserId));
        mBackupUtils.setBackupTransportForUser(mTransport, mProfileUserId);
        assertTrue(
                "Failed to select local transport",
                mBackupUtils.isLocalTransportSelectedForUser(mProfileUserId));

        // Setup test package.
        installPackageAsUser(TEST_APK, true, mProfileUserId);
        clearPackageDataAsUser(TEST_PACKAGE, mProfileUserId);
    }

    /** Uninstall the test package and remove the managed profile. */
    @After
    @Override
    public void tearDown() throws Exception {
        clearBackupDataInTransportForUser(TEST_PACKAGE, mTransport, mProfileUserId);
        uninstallPackageAsUser(TEST_PACKAGE, mProfileUserId);
        mDevice.removeUser(mProfileUserId);
        super.tearDown();
    }

    /**
     * Tests key-value app backup and restore in the profile user.
     *
     * <ol>
     *   <li>App writes shared preferences.
     *   <li>Force a backup.
     *   <li>Uninstall the app.
     *   <li>Install the app to perform a restore-at-install operation.
     *   <li>Check that the shared preferences are restored.
     * </ol>
     */
    @Test
    public void testKeyValueBackupAndRestore() throws Exception {
        checkDeviceTest("assertSharedPrefsIsEmpty");
        checkDeviceTest("writeSharedPrefsAndAssertSuccess");

        mBackupUtils.backupNowAndAssertSuccessForUser(TEST_PACKAGE, mProfileUserId);

        uninstallPackageAsUser(TEST_PACKAGE, mProfileUserId);

        installPackageAsUser(TEST_APK, true, mProfileUserId);

        checkDeviceTest("assertSharedPrefsRestored");
    }

    private void checkDeviceTest(String methodName) throws DeviceNotAvailableException {
        checkDeviceTestAsUser(TEST_PACKAGE, DEVICE_TEST_NAME, methodName, mProfileUserId);
    }
}
