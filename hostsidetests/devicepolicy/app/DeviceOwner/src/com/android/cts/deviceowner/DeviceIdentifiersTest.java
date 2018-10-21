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

    private static final String NULL_DEVICE_ID_ERROR_MESSAGE =
            "The device owner with the READ_PHONE_STATE permission must receive a non-null value"
                    + " when invoking %s";
    private static final String NO_SECURITY_EXCEPTION_ERROR_MESSAGE =
            "A device owner that does not have the READ_PHONE_STATE permission must receive a "
                    + "SecurityException when invoking %s";

    public void testDeviceOwnerCanGetDeviceIdentifiersWithPermission() throws Exception {
        // The device owner with the READ_PHONE_STATE permission should have access to all device
        // identifiers.
        TelephonyManager telephonyManager = (TelephonyManager) mContext.getSystemService(
                Context.TELEPHONY_SERVICE);
        try {
            assertNotNull(String.format(NULL_DEVICE_ID_ERROR_MESSAGE, "getDeviceId"),
                    telephonyManager.getDeviceId());
            assertNotNull(String.format(NULL_DEVICE_ID_ERROR_MESSAGE, "getImei"),
                    telephonyManager.getImei());
            assertNotNull(String.format(NULL_DEVICE_ID_ERROR_MESSAGE, "getMeid"),
                    telephonyManager.getMeid());
            assertNotNull(String.format(NULL_DEVICE_ID_ERROR_MESSAGE, "getSubscriberId"),
                    telephonyManager.getSubscriberId());
            assertNotNull(String.format(NULL_DEVICE_ID_ERROR_MESSAGE, "getSimSerialNumber"),
                    telephonyManager.getSimSerialNumber());
            assertNotNull(String.format(NULL_DEVICE_ID_ERROR_MESSAGE, "Build#getSerial"),
                    Build.getSerial());
        } catch (SecurityException e) {
            fail("The device owner with the READ_PHONE_STATE permission must be able to access "
                    + "the device IDs: " + e);
        }
    }

    public void testDeviceOwnerCannotGetDeviceIdentifiersWithoutPermission() throws Exception {
        // The device owner without the READ_PHONE_STATE permission should still receive a
        // SecurityException when querying for device identifiers.
        TelephonyManager telephonyManager = (TelephonyManager) mContext.getSystemService(
                Context.TELEPHONY_SERVICE);
        try {
            telephonyManager.getDeviceId();
            fail(String.format(NO_SECURITY_EXCEPTION_ERROR_MESSAGE, "getDeviceId"));
        } catch (SecurityException expected) {
        }

        try {
            telephonyManager.getImei();
            fail(String.format(NO_SECURITY_EXCEPTION_ERROR_MESSAGE, "getImei"));
        } catch (SecurityException expected) {
        }

        try {
            telephonyManager.getMeid();
            fail(String.format(NO_SECURITY_EXCEPTION_ERROR_MESSAGE, "getMeid"));
        } catch (SecurityException expected) {
        }

        try {
            telephonyManager.getSubscriberId();
            fail(String.format(NO_SECURITY_EXCEPTION_ERROR_MESSAGE, "getSubscriberId"));
        } catch (SecurityException expected) {
        }

        try {
            telephonyManager.getSimSerialNumber();
            fail(String.format(NO_SECURITY_EXCEPTION_ERROR_MESSAGE, "getSimSerialNumber"));
        } catch (SecurityException expected) {
        }

        try {
            Build.getSerial();
            fail(String.format(NO_SECURITY_EXCEPTION_ERROR_MESSAGE, "Build#getSerial"));
        } catch (SecurityException expected) {
        }
    }
}
