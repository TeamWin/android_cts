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

package android.bluetooth.cts;

import static android.bluetooth.le.AdvertisingSetCallback.ADVERTISE_SUCCESS;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertisingSet;
import android.bluetooth.le.AdvertisingSetCallback;
import android.bluetooth.le.AdvertisingSetParameters;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.Context;

import androidx.test.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Test of {@link AdvertisingSet} and {@link AdvertisingSetCallback}.
 *
 * Note: {@link AdvertisingSetParameters.Builder#setLegacyMode(boolean)} is set to {@code true}
 * because cuttlefish does not yet support non-legacy mode.
 *
 * TODO: Add tests for APIs related to periodic advertising.
 * TODO: Investigate if setting {@code txPower} via
 * {@link AdvertisingSetParameters.Builder#setTxPowerLevel(int)} can be reliably tested and add test
 * to {@link AdvertisingSetCallback#onAdvertisingSetStarted(AdvertisingSet, int, int)} if needed.
 */
@RunWith(AndroidJUnit4.class)
public class AdvertisingSetTest {

    private static final String TAG = "AdvertisingSetTest";
    private static final int TIMEOUT_MS = 5000;
    private static final AdvertisingSetParameters PARAMETER_LEGACY_MODE =
            new AdvertisingSetParameters.Builder().setLegacyMode(true).build();

    private static Context sContext;
    private static BluetoothAdapter sBluetoothAdapter;
    private static BluetoothLeAdvertiser sAdvertiser;
    private static TestAdvertisingSetCallback sCallback;

    @BeforeClass
    public static void initialize() throws InterruptedException {
        sContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        if (!TestUtils.isBleSupported(sContext)) {
            return;
        }

        BluetoothManager manager = sContext.getSystemService(BluetoothManager.class);
        sBluetoothAdapter = manager.getAdapter();
        if (!sBluetoothAdapter.isEnabled()) {
            assertTrue(BTAdapterUtils.enableAdapter(sBluetoothAdapter, sContext));
        }
        sAdvertiser = sBluetoothAdapter.getBluetoothLeAdvertiser();
        assertNotNull(sAdvertiser);

        sCallback = new TestAdvertisingSetCallback();
        startAdvertisingSet(sCallback);
    }

    @Before
    public void setUp() {
        sCallback.reset();
    }

    @AfterClass
    public static void tearDown() throws InterruptedException {
        if (!TestUtils.isBleSupported(sContext)) {
            return;
        }

        assertTrue(BTAdapterUtils.disableAdapter(sBluetoothAdapter, sContext));

        stopAdvertisingSet(sCallback);
    }

    @Test
    public void testEnableAndDisableAdvertising() throws InterruptedException {
        if (!TestUtils.isBleSupported(sContext)) {
            return;
        }

        sCallback.mAdvertisingSet.get().enableAdvertising(/* enable= */ true, /* duration= */ 1,
                /* maxExtendedAdvertisingEvents= */ 1);
        assertTrue(sCallback.mAdvertisingEnabledLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
        assertEquals(ADVERTISE_SUCCESS, sCallback.mAdvertisingEnabledStatus.get());

        sCallback.mAdvertisingSet.get().enableAdvertising(/* enable= */ false, /* duration= */ 1,
                /* maxExtendedAdvertisingEvents= */ 1);
        assertTrue(sCallback.mAdvertisingDisabledLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
        assertEquals(ADVERTISE_SUCCESS, sCallback.mAdvertisingDisabledStatus.get());
    }

    @Test
    public void testSetAdvertisingData() throws InterruptedException {
        if (!TestUtils.isBleSupported(sContext)) {
            return;
        }

        sCallback.mAdvertisingSet.get().setAdvertisingData(new AdvertiseData.Builder().build());
        assertTrue(sCallback.mAdvertisingDataSetLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
        assertEquals(ADVERTISE_SUCCESS, sCallback.mAdvertisingDataSetStatus.get());
    }

    @Test
    public void testSetScanResponseData() throws InterruptedException {
        if (!TestUtils.isBleSupported(sContext)) {
            return;
        }

        sCallback.mAdvertisingSet.get().setScanResponseData(new AdvertiseData.Builder().build());
        assertTrue(sCallback.mScanResponseDataSetLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
        assertEquals(ADVERTISE_SUCCESS, sCallback.mScanResponseDataSetStatus.get());
    }

    /**
     * Note: This test disables advertising via
     * {@link AdvertisingSet#enableAdvertising(boolean, int, int)} before calling
     * {@link AdvertisingSet#setAdvertisingParameters(AdvertisingSetParameters)} because advertising
     * set parameters must be called when advertising is not enabled.
     *
     * TODO: Investigate if setting tx power can be reliably tested and add if needed.
     */
    @Test
    public void testSetAdvertisingParameters() throws InterruptedException {
        if (!TestUtils.isBleSupported(sContext)) {
            return;
        }

        sCallback.mAdvertisingSet.get().enableAdvertising(false, /* duration= */ 1,
                /* maxExtendedAdvertisingEvents= */1);
        assertTrue(sCallback.mAdvertisingDisabledLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
        assertEquals(ADVERTISE_SUCCESS, sCallback.mAdvertisingDisabledStatus.get());

        sCallback.mAdvertisingSet.get().setAdvertisingParameters(
                new AdvertisingSetParameters.Builder()
                        .setLegacyMode(true)
                        .setScannable(false)
                        .build());
        assertTrue(sCallback.mAdvertisingParametersUpdatedLatch.await(TIMEOUT_MS,
                TimeUnit.MILLISECONDS));
        assertEquals(ADVERTISE_SUCCESS, sCallback.mAdvertisingParametersUpdatedStatus.get());
    }

    private static void startAdvertisingSet(AdvertisingSetCallback callback)
            throws InterruptedException {
        sAdvertiser.startAdvertisingSet(PARAMETER_LEGACY_MODE,
                /* advertiseData= */ null, /* scanResponse= */ null,
                /* periodicParameters= */ null, /* periodicData= */ null, callback);
        assertTrue(sCallback.mAdvertisingSetStartedLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
        assertEquals(ADVERTISE_SUCCESS, sCallback.mAdvertisingSetStartedStatus.get());
        assertNotNull(sCallback.mAdvertisingSet);
    }

    private static void stopAdvertisingSet(TestAdvertisingSetCallback callback)
            throws InterruptedException {
        if (sAdvertiser != null && callback != null) {
            sAdvertiser.stopAdvertisingSet(callback);
            assertTrue(callback.mAdvertisingSetStoppedLatch.await(TIMEOUT_MS,
                    TimeUnit.MILLISECONDS));
        }
    }

    private static class TestAdvertisingSetCallback extends AdvertisingSetCallback {
        public CountDownLatch mAdvertisingSetStartedLatch = new CountDownLatch(1);
        public CountDownLatch mAdvertisingEnabledLatch = new CountDownLatch(1);
        public CountDownLatch mAdvertisingDisabledLatch = new CountDownLatch(1);
        public CountDownLatch mAdvertisingParametersUpdatedLatch = new CountDownLatch(1);
        public CountDownLatch mAdvertisingDataSetLatch = new CountDownLatch(1);
        public CountDownLatch mScanResponseDataSetLatch = new CountDownLatch(1);
        public CountDownLatch mAdvertisingSetStoppedLatch = new CountDownLatch(1);

        public AtomicInteger mAdvertisingSetStartedStatus = new AtomicInteger();
        public AtomicInteger mAdvertisingEnabledStatus = new AtomicInteger();
        public AtomicInteger mAdvertisingDisabledStatus = new AtomicInteger();
        public AtomicInteger mAdvertisingParametersUpdatedStatus = new AtomicInteger();
        public AtomicInteger mAdvertisingDataSetStatus = new AtomicInteger();
        public AtomicInteger mScanResponseDataSetStatus = new AtomicInteger();

        public AtomicReference<AdvertisingSet> mAdvertisingSet = new AtomicReference();

        @Override
        public void onAdvertisingSetStarted(AdvertisingSet advertisingSet, int txPower,
                int status) {
            mAdvertisingSetStartedStatus.set(status);
            mAdvertisingSet.set(advertisingSet);
            mAdvertisingSetStartedLatch.countDown();
        }

        @Override
        public void onAdvertisingEnabled(AdvertisingSet advertisingSet, boolean enable,
                int status) {
            if (enable) {
                mAdvertisingEnabledStatus.set(status);
                mAdvertisingEnabledLatch.countDown();
            } else {
                mAdvertisingDisabledStatus.set(status);
                mAdvertisingDisabledLatch.countDown();
            }
        }

        @Override
        public void onAdvertisingParametersUpdated(AdvertisingSet advertisingSet, int txPower,
                int status) {
            mAdvertisingParametersUpdatedStatus.set(status);
            mAdvertisingParametersUpdatedLatch.countDown();
        }

        @Override
        public void onAdvertisingDataSet(AdvertisingSet advertisingSet, int status) {
            mAdvertisingDataSetStatus.set(status);
            mAdvertisingDataSetLatch.countDown();
        }

        @Override
        public void onScanResponseDataSet(AdvertisingSet advertisingSet, int status) {
            mScanResponseDataSetStatus.set(status);
            mScanResponseDataSetLatch.countDown();
        }

        @Override
        public void onAdvertisingSetStopped(AdvertisingSet advertisingSet) {
            mAdvertisingSetStoppedLatch.countDown();
        }

        /**
         * Reset all latches except {@code mAdvertisingSetStartedLatch} and
         * {@code mAdvertisingSetStoppedLatch} which will only be used once in this class.
         *
         * Reset all status variables except {@code mAdvertisingSetStartedStatus}.
         */
        public void reset() {
            mAdvertisingEnabledLatch = new CountDownLatch(1);
            mAdvertisingParametersUpdatedLatch = new CountDownLatch(1);
            mAdvertisingDataSetLatch = new CountDownLatch(1);
            mScanResponseDataSetLatch = new CountDownLatch(1);

            mAdvertisingEnabledStatus = new AtomicInteger();
            mAdvertisingDisabledStatus = new AtomicInteger();
            mAdvertisingParametersUpdatedStatus = new AtomicInteger();
            mAdvertisingDataSetStatus = new AtomicInteger();
            mScanResponseDataSetStatus = new AtomicInteger();
        }
    }
}
