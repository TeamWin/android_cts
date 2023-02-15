/*
 * Copyright (C) 2023 The Android Open Source Project
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

package android.net.wifi.cts;

import static com.google.common.truth.Truth.assertThat;

import android.net.wifi.ScanResult;
import android.net.wifi.WifiScanner;
import android.os.Build;
import android.os.Parcel;

import androidx.test.filters.SdkSuppress;

import java.util.ArrayList;
import java.util.List;

public class WifiScannerTest extends WifiJUnit3TestBase {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
    }

    private static WifiScanner.ScanSettings createRequest(WifiScanner.ChannelSpec[] channels,
            int period, int batch, int bssidsPerScan, int reportEvents) {
        WifiScanner.ScanSettings request = new WifiScanner.ScanSettings();
        request.band = WifiScanner.WIFI_BAND_UNSPECIFIED;
        request.channels = channels;
        request.periodInMs = period;
        request.numBssidsPerScan = bssidsPerScan;
        request.maxScansToCache = batch;
        request.reportEvents = reportEvents;
        return request;
    }

    private static WifiScanner.ScanSettings createRequest(int type, int band, int period, int batch,
            int bssidsPerScan, int reportEvents) {
        return createRequest(WifiScanner.SCAN_TYPE_HIGH_ACCURACY, band, period, 0, 0,
                batch, bssidsPerScan, reportEvents);
    }

    private static WifiScanner.ScanSettings createRequest(int band, int period, int batch,
            int bssidsPerScan, int reportEvents) {
        return createRequest(WifiScanner.SCAN_TYPE_HIGH_ACCURACY, band, period, 0, 0, batch,
                bssidsPerScan, reportEvents);
    }

    private static WifiScanner.ScanSettings createRequest(int type, int band, int period,
            int maxPeriod, int stepCount, int batch, int bssidsPerScan, int reportEvents) {
        WifiScanner.ScanSettings request = new WifiScanner.ScanSettings();
        request.type = type;
        request.band = band;
        request.channels = null;
        request.periodInMs = period;
        request.maxPeriodInMs = maxPeriod;
        request.stepCount = stepCount;
        request.numBssidsPerScan = bssidsPerScan;
        request.maxScansToCache = batch;
        request.reportEvents = reportEvents;
        return request;
    }

    /**
     * Verify WifiScanner ScanSettings setVendorIes() and getVendorIes() methods.
     * Test ScanSettings object being serialized and deserialized while vendorIes keeping the
     * values unchanged.
     */
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.UPSIDE_DOWN_CAKE, codeName = "UpsideDownCake")
    public void testVendorIesParcelable() throws Exception {
        WifiScanner.ScanSettings requestSettings = createRequest(
                WifiScanner.WIFI_BAND_BOTH_WITH_DFS, 0,
                0, 20, WifiScanner.REPORT_EVENT_AFTER_EACH_SCAN);
        List<ScanResult.InformationElement> vendorIesList = new ArrayList<>();
        ScanResult.InformationElement vendorIe1 = new ScanResult.InformationElement(221, 0,
                new byte[]{0x00, 0x50, (byte) 0xf2, 0x08, 0x11, 0x22, 0x33});
        ScanResult.InformationElement vendorIe2 = new ScanResult.InformationElement(221, 0,
                new byte[]{0x00, 0x50, (byte) 0xf2, 0x08, (byte) 0xaa, (byte) 0xbb, (byte) 0xcc});
        vendorIesList.add(vendorIe1);
        vendorIesList.add(vendorIe2);
        requestSettings.setVendorIes(vendorIesList);
        assertEquals(vendorIesList, requestSettings.getVendorIes());

        Parcel parcel = Parcel.obtain();
        requestSettings.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        assertThat(
                WifiScanner.ScanSettings.CREATOR.createFromParcel(parcel).getVendorIes()).isEqualTo(
                requestSettings.getVendorIes());
    }

    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.UPSIDE_DOWN_CAKE, codeName = "UpsideDownCake")
    public void testPnoSettings() throws Exception {
        android.net.wifi.nl80211.PnoSettings pnoSettings =
                new android.net.wifi.nl80211.PnoSettings();
        pnoSettings.setScanIterations(3);
        pnoSettings.setScanIntervalMultiplier(4);
        assertEquals(3, pnoSettings.getScanIterations());
        assertEquals(4, pnoSettings.getScanIntervalMultiplier());
    }
}
