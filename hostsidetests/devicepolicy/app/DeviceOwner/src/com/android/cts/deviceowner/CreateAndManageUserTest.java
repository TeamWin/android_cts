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
 * limitations under the License.
 */

package com.android.cts.deviceowner;

import android.app.admin.DevicePolicyManager;

import java.lang.reflect.Field;

/**
 * Test {@link DevicePolicyManager#createAndManageUser}.
 *
 * <p>The test creates users by calling to {@link DevicePolicyManager#createAndManageUser}, it
 * doesn't remove the users afterwards, so their properties can be queried and tested by host-side
 * tests.
 */
public class CreateAndManageUserTest extends BaseDeviceOwnerTest {

    /**
     * Test creating an ephemeral user using the {@link DevicePolicyManager#createAndManageUser}
     * method.
     *
     * <p>The user's flags will be checked from the corresponding host-side test.
     */
    public void testCreateAndManageEphemeralUser() throws Exception {
        String testUserName = "TestUser_" + System.currentTimeMillis();

        // Use reflection to get the value of the hidden flag to make the new user ephemeral.
        Field field = DevicePolicyManager.class.getField("MAKE_USER_EPHEMERAL");
        int makeEphemeralFlag = field.getInt(null);

        mDevicePolicyManager.createAndManageUser(
                getWho(),
                testUserName,
                getWho(),
                null,
                makeEphemeralFlag);
    }

}
