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
package com.android.cts.managedprofile;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.telephony.TelephonyManager;

/**
 * Verifies a profile owner on a personal device cannot access device identifiers.
 */
public class DeviceIdentifiersTest extends BaseManagedProfileTest {
    private static final String NO_SECURITY_EXCEPTION_ERROR_MESSAGE =
            "A profile owner that does not have the READ_PHONE_STATE permission must receive a "
                    + "SecurityException when invoking %s";

    public void testProfileOwnerOnPersonalDeviceCannotGetDeviceIdentifiers() throws Exception {
        // The profile owner with the READ_PHONE_STATE permission should still receive a
        // SecurityException when querying for device identifiers if it's not on an
        // organization-owned device.
        TelephonyManager telephonyManager = (TelephonyManager) mContext.getSystemService(
                Context.TELEPHONY_SERVICE);
        // Allow the APIs to also return null if the telephony feature is not supported.
        boolean hasTelephonyFeature =
                mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_TELEPHONY);
        try {
            String deviceId = telephonyManager.getDeviceId();
            if (hasTelephonyFeature) {
                fail(String.format(NO_SECURITY_EXCEPTION_ERROR_MESSAGE, "getDeviceId"));
            } else {
                assertEquals(null, deviceId);
            }
        } catch (SecurityException expected) {
        }

        try {
            String imei = telephonyManager.getImei();
            if (hasTelephonyFeature) {
                fail(String.format(NO_SECURITY_EXCEPTION_ERROR_MESSAGE, "getImei"));
            } else {
                assertEquals(null, imei);
            }
        } catch (SecurityException expected) {
        }

        try {
            String meid = telephonyManager.getMeid();
            if (hasTelephonyFeature) {
                fail(String.format(NO_SECURITY_EXCEPTION_ERROR_MESSAGE, "getMeid"));
            } else {
                assertEquals(null, meid);
            }
        } catch (SecurityException expected) {
        }

        try {
            String subscriberId = telephonyManager.getSubscriberId();
            if (hasTelephonyFeature) {
                fail(String.format(NO_SECURITY_EXCEPTION_ERROR_MESSAGE, "getSubscriberId"));
            } else {
                assertEquals(null, subscriberId);
            }
        } catch (SecurityException expected) {
        }

        try {
            String simSerialNumber = telephonyManager.getSimSerialNumber();
            if (hasTelephonyFeature) {
                fail(String.format(NO_SECURITY_EXCEPTION_ERROR_MESSAGE, "getSimSerialNumber"));
            } else {
                assertEquals(null, simSerialNumber);
            }
        } catch (SecurityException expected) {
        }

        try {
            String serial = Build.getSerial();
            if (hasTelephonyFeature) {
                fail(String.format(NO_SECURITY_EXCEPTION_ERROR_MESSAGE, "Build#getSerial"));
            } else {
              assertEquals(null, serial);
            }
        } catch (SecurityException expected) {
        }
    }
}
