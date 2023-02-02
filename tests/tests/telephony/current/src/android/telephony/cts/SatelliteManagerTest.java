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

package android.telephony.cts;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeTrue;

import androidx.test.InstrumentationRegistry;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.telephony.satellite.PointingInfo;
import android.telephony.satellite.SatelliteManager;
import android.util.Log;

import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class SatelliteManagerTest {
    private static final String TAG = "SatelliteManagerTest";

    private static final long TIMEOUT = 1000;

    private SatelliteManager mSatelliteManager;

    @Before
    public void setUp() throws Exception {
        assumeTrue(getContext().getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_TELEPHONY_SATELLITE));
        mSatelliteManager =
                (SatelliteManager) getContext().getSystemService(Context.SATELLITE_SERVICE);
    }

    @Test
    public void testSatellitePositionUpdates() {
        SatelliteManager.SatellitePositionUpdateCallback callback =
                new SatelliteManager.SatellitePositionUpdateCallback() {
                    @Override
                    public void onSatellitePositionUpdate(PointingInfo pointingInfo) {
                        Log.d(TAG, "onSatellitePositionUpdate: pointingInfo=" + pointingInfo);
                    }

                    @Override
                    public void onMessageTransferStateUpdate(int state) {
                        Log.d(TAG, "onMessageTransferStateUpdate: state=" + state);
                    }
        };

        // Throws SecurityException as we do not have SATELLITE_COMMUNICATION permission.
        assertThrows(SecurityException.class, ()-> mSatelliteManager.startSatellitePositionUpdates(
                getContext().getMainExecutor(), callback));

        /*
        grantSatellitePermission();
        int startResult = mSatelliteManager.startSatellitePositionUpdates(
                getContext().getMainExecutor(), callback);
        if (startResult != SatelliteManager.SATELLITE_SERVICE_SUCCESS) {
            Log.d(TAG, "Failed to start position updates.");
            assertThrows(IllegalArgumentException.class,
                    () -> mSatelliteManager.stopSatellitePositionUpdates(callback));
            return;
        }

        Log.d(TAG, "Successfully started position updates.");
        int stopResult = mSatelliteManager.stopSatellitePositionUpdates(callback);
        if (stopResult == SatelliteManager.SATELLITE_SERVICE_SUCCESS) {
            Log.d(TAG, "Successfully stopped position updates.");
            assertThrows(IllegalArgumentException.class,
                    () -> mSatelliteManager.stopSatellitePositionUpdates(callback));
        } else {
            Log.d(TAG, "Failed to stop position updates.");
        }
        assertThrows(IllegalArgumentException.class,
                () -> mSatelliteManager.stopSatellitePositionUpdates(callback));
         */
    }

    @Test
    public void testGetMaxCharactersPerSatelliteTextMessage() throws Exception {
        LinkedBlockingQueue<Integer> maxCharResult = new LinkedBlockingQueue<>(1);

        // Throws SecurityException as we do not have SATELLITE_COMMUNICATION permission.
        assertThrows(SecurityException.class,
                ()-> mSatelliteManager.getMaxCharactersPerSatelliteTextMessage(
                        getContext().getMainExecutor(), maxCharResult::offer));

        /*
        grantSatellitePermission();
        mSatelliteManager.getMaxCharactersPerSatelliteTextMessage(
                getContext().getMainExecutor(), maxCharResult::offer);
        Integer result = maxCharResult.poll(TIMEOUT, TimeUnit.MILLISECONDS);
        assertNotNull(result);
         */
    }

    private Context getContext() {
        return InstrumentationRegistry.getContext();
    }

    private void grantSatellitePermission() {
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.SATELLITE_COMMUNICATION);
    }
}
