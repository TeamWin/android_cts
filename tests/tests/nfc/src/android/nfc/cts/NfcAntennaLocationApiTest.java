/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.nfc.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assume.assumeTrue;

import android.content.Context;
import android.content.pm.PackageManager;
import android.nfc.AvailableNfcAntenna;
import android.nfc.NfcAdapter;
import android.nfc.NfcAntennaInfo;

import androidx.test.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class NfcAntennaLocationApiTest {
    private static int sDeviceWidth = 111;
    private static int sDeviceHeight = 112;
    private static boolean sDeviceFoldable = true;
    private static int sAntennaX = 12;
    private static int sAntennaY = 13;

    private boolean supportsHardware() {
        final PackageManager pm = InstrumentationRegistry.getContext().getPackageManager();
        return pm.hasSystemFeature(PackageManager.FEATURE_NFC);
    }

    private NfcAdapter mAdapter;
    private Context mContext;

    @Before
    public void setUp() throws Exception {
        assumeTrue(supportsHardware());
        mContext = InstrumentationRegistry.getContext();
        mAdapter = NfcAdapter.getDefaultAdapter(mContext);
        assertNotNull(mAdapter);
    }

    @After
    public void tearDown() throws Exception {
    }

    /** Tests getNfcAntennaInfo API */
    @Test
    public void testNfcAntennaInfoIsReturned() {
        NfcAntennaInfo nfcAntennaInfo = mAdapter.getNfcAntennaInfo();

        assertEquals("Device widths do not match", sDeviceWidth,
                nfcAntennaInfo.getDeviceWidth());
        assertEquals("Device heights do not match", sDeviceHeight,
                nfcAntennaInfo.getDeviceHeight());
        assertEquals("Device foldable do not match", sDeviceFoldable,
                nfcAntennaInfo.isDeviceFoldable());
        assertEquals("Wrong number of available antennas", 1,
                nfcAntennaInfo.getAvailableNfcAntennas().size());

        AvailableNfcAntenna availableNfcAntenna = nfcAntennaInfo.getAvailableNfcAntennas().get(0);

        assertEquals("Wrong nfc antenna X axis",
                availableNfcAntenna.getLocationX(), sAntennaX);
        assertEquals("Wrong nfc antenna Y axis",
                availableNfcAntenna.getLocationY(), sAntennaY);
    }
}
