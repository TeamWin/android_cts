/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.car.cts;

import static android.car.cts.ShellHelper.MAX_NUMBER_USERS_DEFAULT;
import static android.car.cts.ShellHelper.createFullUser;
import static android.car.cts.ShellHelper.getCurrentUser;
import static android.car.cts.ShellHelper.getMaxNumberUsers;
import static android.car.cts.ShellHelper.isNightMode;
import static android.car.cts.ShellHelper.removeUser;
import static android.car.cts.ShellHelper.setDayMode;
import static android.car.cts.ShellHelper.setMaxNumberUsers;
import static android.car.cts.ShellHelper.setNightMode;
import static android.car.cts.ShellHelper.switchUser;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Check car config consistency across day night mode switching.
 */
@RunWith(DeviceJUnit4ClassRunner.class)
public final class UiModeHostTest extends BaseHostJUnit4Test {

    private static final String FEATURE_AUTOMOTIVE = "android.hardware.type.automotive";

    private static int sNumMaxUsersBefore = MAX_NUMBER_USERS_DEFAULT;

    @Before
    public void setUp() throws Exception {
        assumeTrue(hasDeviceFeature(FEATURE_AUTOMOTIVE));
        // TODO (b/167698977): Remove assumption after the user build has proper permissions.
        assumeTrue(getDevice().isAdbRoot());

        // increase max user limit by 2 to prevent create-user failure
        sNumMaxUsersBefore = getMaxNumberUsers(getDevice());
        setMaxNumberUsers(getDevice(), sNumMaxUsersBefore + 2);
    }

    @After
    public void cleanUp() throws Exception {
        setMaxNumberUsers(getDevice(), sNumMaxUsersBefore);
    }

    /**
     * Test day/night mode consistency across user switching. Day/night mode config should be
     * persistent across user switching.
     */
    @Test
    public void testUserSwitchingConfigConsistency() throws Exception {
        ITestDevice device = getDevice();
        int originalUserId = getCurrentUser(device);

        // create 2 test users
        int userIdFoo = createFullUser(device, "UserSwitchingHostTest-user-1");
        int userIdBar = createFullUser(device, "UserSwitchingHostTest-user-2");

        // start with user foo in day mode
        switchUser(device, userIdFoo);
        setDayMode(device);
        assertFalse(isNightMode(device));

        // set to night mode
        setNightMode(device);
        assertTrue(isNightMode(device));

        // switch to user bar and verify night mode
        switchUser(device, userIdBar);
        assertTrue(isNightMode(device));

        // set to day mode
        setDayMode(device);
        assertFalse(isNightMode(device));

        // switch to user foo and verify day mode
        switchUser(device, userIdFoo);
        assertFalse(isNightMode(device));

        // switch back to original user and remove test users
        switchUser(device, originalUserId);
        removeUser(device, userIdFoo);
        removeUser(device, userIdBar);
    }
}
