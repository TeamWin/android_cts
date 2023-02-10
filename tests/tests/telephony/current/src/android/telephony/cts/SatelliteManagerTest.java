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

import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeTrue;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.telephony.satellite.PointingInfo;
import android.telephony.satellite.SatelliteCallback;
import android.telephony.satellite.SatelliteManager;
import android.telephony.satellite.stub.SatelliteImplBase;
import android.util.Log;

import androidx.test.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.concurrent.LinkedBlockingQueue;

public class SatelliteManagerTest {
    private static final String TAG = "SatelliteManagerTest";

    private static final long TIMEOUT = 1000;

    private SatelliteManager mSatelliteManager;

    @Before
    public void setUp() throws Exception {
        assumeTrue(getContext().getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_TELEPHONY_SATELLITE));
        mSatelliteManager = getContext().getSystemService(SatelliteManager.class);
    }

    @Test
    public void testSatellitePositionUpdates() {
        SatellitePositionUpdateListenerTest callback =
                new SatellitePositionUpdateListenerTest();

        // Throws SecurityException as we do not have SATELLITE_COMMUNICATION permission.
        assertThrows(SecurityException.class,
                () -> mSatelliteManager.startSatellitePositionUpdates(
                        getContext().getMainExecutor(), callback));
    }

    @Test
    public void testGetMaxCharactersPerSatelliteTextMessage() throws Exception {
        LinkedBlockingQueue<Integer> maxCharResult = new LinkedBlockingQueue<>(1);

        // Throws SecurityException as we do not have SATELLITE_COMMUNICATION permission.
        assertThrows(SecurityException.class,
                ()-> mSatelliteManager.getMaxCharactersPerSatelliteTextMessage(
                        getContext().getMainExecutor(), maxCharResult::offer));
    }

    @Test
    public void testProvisionSatelliteService() {
        int[] features = {SatelliteImplBase.FEATURE_EMERGENCY_SMS,
                SatelliteImplBase.FEATURE_LOCATION_SHARING};

        final LinkedBlockingQueue<Integer> resultQueue1 = new LinkedBlockingQueue<>(1);
        assertThrows(SecurityException.class,
                ()-> mSatelliteManager.provisionSatelliteService(
                features, getContext().getMainExecutor(), resultQueue1::offer, null));
    }

    @Test
    public void testRegisterForSatelliteProvisionStateChanged() {
        SatelliteProvisionStateListenerTest satelliteProvisionStateListener =
                new SatelliteProvisionStateListenerTest();
        assertThrows(SecurityException.class,
                ()-> mSatelliteManager.registerForSatelliteProvisionStateChanged(
                        getContext().getMainExecutor(), satelliteProvisionStateListener));
    }

    @Test
    public void testGetProvisionedSatelliteFeatures() {
        final LinkedBlockingQueue<int[]> resultQueue = new LinkedBlockingQueue<>();
        assertThrows(SecurityException.class,
                ()-> mSatelliteManager.getProvisionedSatelliteFeatures(
                        getContext().getMainExecutor(), resultQueue::offer));
    }

    private Context getContext() {
        return InstrumentationRegistry.getContext();
    }

    private void grantSatellitePermission() {
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.SATELLITE_COMMUNICATION);
    }

    private static class SatellitePositionUpdateListenerTest extends SatelliteCallback
            implements SatelliteCallback.SatellitePositionUpdateListener {
        @Override
        public void onSatellitePositionUpdate(PointingInfo pointingInfo) {
            Log.d(TAG, "onSatellitePositionUpdate: pointingInfo=" + pointingInfo);
        }

        @Override
        public void onMessageTransferStateUpdate(int state) {
            Log.d(TAG, "onMessageTransferStateUpdate: state=" + state);
        }
    }

    private static class SatelliteProvisionStateListenerTest extends SatelliteCallback
            implements SatelliteCallback.SatelliteProvisionStateListener {
        @Override
        public void onSatelliteProvisionStateChanged(int[] features,
                boolean provisioned) {
            Log.d(TAG, "onSatelliteProvisionStateChanged: features="
                    + Arrays.toString(features) + ", provisioned=" + provisioned);
        }
    }
}
