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

import static org.testng.Assert.assertThrows;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.telephony.TelephonyManager;

/**
 * Verifies a profile owner on a personal device cannot access device identifiers.
 */
public class DeviceIdentifiersTest extends BaseManagedProfileTest {

    public void testProfileOwnerOnPersonalDeviceCannotGetDeviceIdentifiers() throws Exception {
        // The profile owner with the READ_PHONE_STATE permission should still receive a
        // SecurityException when querying for device identifiers if it's not on an
        // organization-owned device.
        TelephonyManager telephonyManager = (TelephonyManager) mContext.getSystemService(
                Context.TELEPHONY_SERVICE);
        // Allow the APIs to also return null if the telephony feature is not supported.
        boolean hasTelephonyFeature =
                mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_TELEPHONY);
        if (hasTelephonyFeature) {
            assertThrows(SecurityException.class, telephonyManager::getDeviceId);
            assertThrows(SecurityException.class, telephonyManager::getImei);
            assertThrows(SecurityException.class, telephonyManager::getMeid);
            assertThrows(SecurityException.class, telephonyManager::getSubscriberId);
            assertThrows(SecurityException.class, telephonyManager::getSimSerialNumber);
            assertThrows(SecurityException.class, telephonyManager::getNai);
            assertThrows(SecurityException.class, Build::getSerial);
        } else {
            assertNull(telephonyManager.getDeviceId());
            assertNull(telephonyManager.getImei());
            assertNull(telephonyManager.getMeid());
            assertNull(telephonyManager.getSubscriberId());
            assertNull(telephonyManager.getSimSerialNumber());
            assertNull(telephonyManager.getNai());
            assertNull(Build.getSerial());
        }
    }
}
