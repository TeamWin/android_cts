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
 * limitations under the License.
 */
package com.android.cts.deviceowner;

import android.content.Context;
import android.os.Build;
import android.telephony.TelephonyManager;

/**
 * Verifies device identifier access for the device owner.
 */
public class DeviceIdentifiersTest extends BaseDeviceOwnerTest {

    public void testDeviceOwnerCanGetDeviceIdentifiers() throws Exception {
        try {
            TelephonyManager tm = (TelephonyManager) mContext.getSystemService(
                    Context.TELEPHONY_SERVICE);
            assertNotNull("The device owner must have access to getDeviceId", tm.getDeviceId());
            assertNotNull("The device owner must have access to getImei", tm.getImei());
            assertNotNull("The device owner must have access to getMeid", tm.getMeid());
            assertNotNull("The device owner must have access to getSubscriberId",
                    tm.getSubscriberId());
            assertNotNull("The device owner must have access to getSimSerialNumber",
                    tm.getSimSerialNumber());
            assertNotNull("The device owner must have access to Build.getSerial",
                    Build.getSerial());
        } catch (SecurityException e) {
            fail("The device owner must be able to access the device IDs");
        }
    }
}
