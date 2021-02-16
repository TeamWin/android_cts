/*
 * Copyright (C) 2021 The Android Open Source Project
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


import static com.android.compatibility.common.util.WifiConfigCreator.SECURITY_TYPE_NONE;

import android.Manifest;
import android.content.pm.PackageManager;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;

import com.android.compatibility.common.util.WifiConfigCreator;

import java.util.List;

public class WifiNetworkConfigurationWithoutFineLocationPermissionTest extends BaseDeviceOwnerTest {
    private static final String TAG = "WifiNetworkConfigurationWithoutFineLocationPermissionTest";

    // Unique SSID to use for this test (max SSID length is 32)
    private static final String NETWORK_SSID = "com.android.cts.abcdefghijklmnop";
    private static final int INVALID_NETWORK_ID = -1;

    private WifiManager mWifiManager;
    private WifiConfigCreator mWifiConfigCreator;

    @Override
    public void setUp() throws Exception {
        super.setUp();

        mWifiConfigCreator = new WifiConfigCreator(getContext());
        mWifiManager = getContext().getSystemService(WifiManager.class);
    }

    public void testAddAndRetrieveCallerConfiguredNetworks() throws Exception {
        assertTrue("WiFi is not enabled", mWifiManager.isWifiEnabled());
        assertEquals(PackageManager.PERMISSION_DENIED,
                mContext.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION));

        int netId = mWifiConfigCreator.addNetwork(NETWORK_SSID, /* hidden */ false,
                SECURITY_TYPE_NONE, /* password */ null);
        assertNotSame("Failed to add network", INVALID_NETWORK_ID, netId);

        try {
            List<WifiConfiguration> configs = mWifiManager.getCallerConfiguredNetworks();
            assertEquals(1, configs.size());
            assertEquals('"' + NETWORK_SSID + '"', configs.get(0).SSID);
        } finally {
            mWifiManager.removeNetwork(netId);
        }
    }
}
