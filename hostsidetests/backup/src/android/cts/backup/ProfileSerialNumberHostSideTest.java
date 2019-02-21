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
 * limitations under the License
 */

package android.cts.backup;

import android.platform.test.annotations.AppModeFull;

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Verifies that serial number for ancestral work profile is read/written correctly. The host-side
 * part is needed to create/remove a test user as the test is run for a new secondary user to avoid
 * changing serial numbers for existing users.
 */
@RunWith(DeviceJUnit4ClassRunner.class)
@AppModeFull
public class ProfileSerialNumberHostSideTest extends BaseMultiUserBackupHostSideTest {
    private static final String APK_NAME = "CtsProfileSerialNumberApp.apk";
    private static final String TEST_PACKAGE = "android.cts.backup.profileserialnumberapp";
    private static final String TEST_CLASS = TEST_PACKAGE + ".ProfileSerialNumberTest";

    private ITestDevice mDevice;
    private int mUserId;

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();

        mDevice = getDevice();

        int userId = mDevice.getCurrentUser();
        mUserId = createProfileUser(userId, "Profile-Serial-Number");
        startUserAndInitializeForBackup(mUserId);

        installPackageAsUser(APK_NAME, false, mUserId);
    }

    @After
    @Override
    public void tearDown() throws Exception {
        uninstallPackageAsUser(APK_NAME, mUserId);
        mDevice.removeUser(mUserId);

        super.tearDown();
    }

    /**
     * Test reading and writing of serial number for user's ancestral profile.
     *
     * Test logic:
     *    1. Set ancestral serial number for user with
     *       {@link BackupManager#setAncestralSerialNumber(long)}
     *    2. Get user by ancestral serial number with
     *       {@link BackupManager#getUserForAncestralSerialNumber(long)} and verify the same user is
     *       returned
     */
    @Test
    public void testSetAndGetAncestralSerialNumber() throws Exception {
        checkDeviceTest("testSetAndGetAncestralSerialNumber");
    }

    /**
     * Test that {@link BackupManager#getUserForAncestralSerialNumber(long)} returns {@code null}
     * when the given serial number is not assigned to any user.
     */
    @Test
    public void testGetUserForAncestralSerialNumber_returnsNull_whenNoUserHasGivenSerialNumber()
            throws Exception {
        checkDeviceTest(
                "testGetUserForAncestralSerialNumber_returnsNull_whenNoUserHasGivenSerialNumber");
    }

    private void checkDeviceTest(String methodName) throws DeviceNotAvailableException {
        checkDeviceTestAsUser(TEST_PACKAGE, TEST_CLASS, methodName, mUserId);
    }
}
