/*
 * Copyright (C) 2015 The Android Open Source Project
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

package android.security.cts;

import android.test.AndroidTestCase;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;
import android.content.pm.PackageManager;
import android.net.wifi.WifiManager;
import android.content.Context;

/**
 * Check that restricted information is not available
 * to the untrusted_app domain
 */
public class RestrictedInformationTest extends AndroidTestCase {
   /*
    * Test that wifi Mac address is not available through sysfs
    */
    public void testWifiMacAddr() throws Exception {
        /* if wifi does not exist, exit - PASS */
        PackageManager pm = getContext().getPackageManager();
        if (!pm.hasSystemFeature(PackageManager.FEATURE_WIFI))
            return;

        /* Wifi exists, but is not on - FAIL */
        WifiManager wifi = (WifiManager) getContext().getSystemService(Context.WIFI_SERVICE);
        assertTrue("Wifi must be enabled to pass this test.", wifi.isWifiEnabled());

        /* Enumerate through the interfaces */
        Enumeration<NetworkInterface> theInterfaces = NetworkInterface.getNetworkInterfaces();

        while (theInterfaces.hasMoreElements()) {
            NetworkInterface netif = theInterfaces.nextElement();
            String name = netif.getName();
            /* some devices label wifi network interface as eth */
            if (!name.contains("wlan") && !name.contains("eth"))
                continue;
            /* PASS means that getHardwareAddress throws a socket exception */
            try {
                byte[] hwAddr = netif.getHardwareAddress();
                fail("Mac address for " + name + "is accessible: " + new String(hwAddr) +
                        "\nTo pass this test, label the address with the sysfs_mac_address\n" +
                        "selinux label. " +
                        "e.g. https://android-review.googlesource.com/#/c/162180/1\n");
            } catch (SocketException se) {/* socket exception if MAC blocked - PASS */}
        }
    }
}
