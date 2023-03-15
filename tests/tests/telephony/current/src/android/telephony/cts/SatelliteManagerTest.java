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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeNoException;
import static org.junit.Assume.assumeTrue;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.OutcomeReceiver;
import android.telephony.TelephonyManager;
import android.telephony.satellite.PointingInfo;
import android.telephony.satellite.SatelliteCapabilities;
import android.telephony.satellite.SatelliteDatagram;
import android.telephony.satellite.SatelliteDatagramCallback;
import android.telephony.satellite.SatelliteManager;
import android.telephony.satellite.SatelliteProvisionStateCallback;
import android.telephony.satellite.SatelliteStateCallback;
import android.telephony.satellite.SatelliteTransmissionUpdateCallback;
import android.util.Log;

import androidx.test.InstrumentationRegistry;

import com.android.internal.telephony.ILongConsumer;

import org.junit.Before;
import org.junit.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class SatelliteManagerTest {
    private static final String TAG = "SatelliteManagerTest";

    private static final long TIMEOUT = 1000;

    private SatelliteManager mSatelliteManager;

    @Before
    public void setUp() throws Exception {
        assumeTrue(getContext().getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_TELEPHONY_SATELLITE));
        try {
            getContext().getSystemService(TelephonyManager.class)
                    .getHalVersion(TelephonyManager.HAL_SERVICE_RADIO);
        } catch (IllegalStateException e) {
            assumeNoException("Skipping tests because Telephony service is null", e);
        }
        mSatelliteManager = getContext().getSystemService(SatelliteManager.class);
    }

    @Test
    public void testSatelliteTransmissionUpdates() throws Exception {
        LinkedBlockingQueue<Integer> error = new LinkedBlockingQueue<>(1);
        SatelliteTransmissionUpdateCallbackTest callback =
                new SatelliteTransmissionUpdateCallbackTest();

        // Throws SecurityException as we do not have SATELLITE_COMMUNICATION permission.
        assertThrows(SecurityException.class,
                () -> mSatelliteManager.startSatelliteTransmissionUpdates(
                        getContext().getMainExecutor(), error::offer, callback));

        grantSatellitePermission();
        int errorCode;
        mSatelliteManager.startSatelliteTransmissionUpdates(
                getContext().getMainExecutor(), error::offer, callback);
        errorCode = error.poll(TIMEOUT, TimeUnit.MILLISECONDS);
        if (errorCode == SatelliteManager.SATELLITE_ERROR_NONE) {
            Log.d(TAG, "Successfully started transmission updates.");
        } else {
            Log.d(TAG, "Failed to start transmission updates: " + errorCode);
        }
        getContext().getSystemService(SatelliteManager.class).stopSatelliteTransmissionUpdates(
                callback, getContext().getMainExecutor(), error::offer);
        errorCode = error.poll(TIMEOUT, TimeUnit.MILLISECONDS);
        Log.d(TAG, "Stop transmission updates: " + errorCode);

        mSatelliteManager.stopSatelliteTransmissionUpdates(
                callback, getContext().getMainExecutor(), error::offer);
        errorCode = error.poll(TIMEOUT, TimeUnit.MILLISECONDS);
        assertEquals(errorCode, SatelliteManager.SATELLITE_INVALID_ARGUMENTS);
        revokeSatellitePermission();
    }

    @Test
    public void testProvisionSatelliteService() {
        LinkedBlockingQueue<Integer> error = new LinkedBlockingQueue<>(1);

        // Throws SecurityException as we do not have SATELLITE_COMMUNICATION permission.
        assertThrows(SecurityException.class,
                () -> mSatelliteManager.provisionSatelliteService(
                        "", "", null, getContext().getMainExecutor(), error::offer));
    }

    @Test
    public void testDeprovisionSatelliteService() {
        LinkedBlockingQueue<Integer> error = new LinkedBlockingQueue<>(1);

        // Throws SecurityException as we do not have SATELLITE_COMMUNICATION permission.
        assertThrows(SecurityException.class,
                () -> mSatelliteManager.deprovisionSatelliteService(
                        "", getContext().getMainExecutor(), error::offer));
    }

    @Test
    public void testRegisterForSatelliteProvisionStateChanged() {
        SatelliteProvisionStateCallbackTest satelliteProvisionStateCallback =
                new SatelliteProvisionStateCallbackTest();

        // Throws SecurityException as we do not have SATELLITE_COMMUNICATION permission.
        assertThrows(SecurityException.class,
                () -> mSatelliteManager.registerForSatelliteProvisionStateChanged(
                        getContext().getMainExecutor(), satelliteProvisionStateCallback));
    }

    @Test
    public void testRequestIsSatelliteProvisioned() {
        final AtomicReference<Boolean> provisioned = new AtomicReference<>();
        final AtomicReference<Integer> errorCode = new AtomicReference<>();
        OutcomeReceiver<Boolean, SatelliteManager.SatelliteException> receiver =
                new OutcomeReceiver<>() {
                    @Override
                    public void onResult(Boolean result) {
                        provisioned.set(result);
                    }

                    @Override
                    public void onError(SatelliteManager.SatelliteException exception) {
                        errorCode.set(exception.getErrorCode());
                    }
                };

        // Throws SecurityException as we do not have SATELLITE_COMMUNICATION permission.
        assertThrows(SecurityException.class,
                () -> mSatelliteManager.requestIsSatelliteProvisioned(
                        getContext().getMainExecutor(), receiver));
    }

    @Test
    public void testRequestSatelliteEnabled() {
        LinkedBlockingQueue<Integer> error = new LinkedBlockingQueue<>(1);

        // Throws SecurityException as we do not have SATELLITE_COMMUNICATION permission.
        assertThrows(SecurityException.class, () -> mSatelliteManager.requestSatelliteEnabled(
                true, true, getContext().getMainExecutor(), error::offer));
        assertThrows(SecurityException.class, () -> mSatelliteManager.requestSatelliteEnabled(
                false, true, getContext().getMainExecutor(), error::offer));
    }

    @Test
    public void testRequestIsSatelliteEnabled() {
        final AtomicReference<Boolean> enabled = new AtomicReference<>();
        final AtomicReference<Integer> errorCode = new AtomicReference<>();
        OutcomeReceiver<Boolean, SatelliteManager.SatelliteException> receiver =
                new OutcomeReceiver<>() {
                    @Override
                    public void onResult(Boolean result) {
                        enabled.set(result);
                    }

                    @Override
                    public void onError(SatelliteManager.SatelliteException exception) {
                        errorCode.set(exception.getErrorCode());
                    }
                };

        // Throws SecurityException as we do not have SATELLITE_COMMUNICATION permission.
        assertThrows(SecurityException.class, () -> mSatelliteManager.requestIsSatelliteEnabled(
                getContext().getMainExecutor(), receiver));
    }

    @Test
    public void testRequestIsSatelliteSupported() throws Exception {
        final AtomicReference<Boolean> supported = new AtomicReference<>();
        final AtomicReference<Integer> errorCode = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        OutcomeReceiver<Boolean, SatelliteManager.SatelliteException> receiver =
                new OutcomeReceiver<>() {
                    @Override
                    public void onResult(Boolean result) {
                        supported.set(result);
                        latch.countDown();
                    }

                    @Override
                    public void onError(SatelliteManager.SatelliteException exception) {
                        errorCode.set(exception.getErrorCode());
                        latch.countDown();
                    }
                };

        mSatelliteManager.requestIsSatelliteSupported(getContext().getMainExecutor(),
                receiver);
        assertTrue(latch.await(TIMEOUT, TimeUnit.MILLISECONDS));

        assertNotNull(errorCode.get());
        int error = errorCode.get();
        if (error == SatelliteManager.SATELLITE_ERROR_NONE) {
            assertNotNull(supported.get());
        } else {
            assertNull(supported.get());
        }
    }

    @Test
    public void testRequestSatelliteCapabilities() {
        final AtomicReference<SatelliteCapabilities> capabilities = new AtomicReference<>();
        final AtomicReference<Integer> errorCode = new AtomicReference<>();
        OutcomeReceiver<SatelliteCapabilities, SatelliteManager.SatelliteException> receiver =
                new OutcomeReceiver<>() {
                    @Override
                    public void onResult(SatelliteCapabilities result) {
                        capabilities.set(result);
                    }

                    @Override
                    public void onError(SatelliteManager.SatelliteException exception) {
                        errorCode.set(exception.getErrorCode());
                    }
                };

        // Throws SecurityException as we do not have SATELLITE_COMMUNICATION permission.
        assertThrows(SecurityException.class, () -> mSatelliteManager.requestSatelliteCapabilities(
                getContext().getMainExecutor(), receiver));
    }

    @Test
    public void testSatelliteModemStateChanged() throws Exception {
        SatelliteStateCallbackTest callback = new SatelliteStateCallbackTest();

        // Throws SecurityException as we do not have SATELLITE_COMMUNICATION permission.
        assertThrows(SecurityException.class, ()-> mSatelliteManager
                .registerForSatelliteModemStateChanged(getContext().getMainExecutor(), callback));

        // Throws SecurityException as we do not have SATELLITE_COMMUNICATION permission.
        assertThrows(SecurityException.class, ()-> mSatelliteManager
                .unregisterForSatelliteModemStateChanged(callback));
    }

    @Test
    public void testSatelliteDatagramCallback() throws Exception {
        SatelliteDatagramCallbackTest callback = new SatelliteDatagramCallbackTest();

        // Throws SecurityException as we do not have SATELLITE_COMMUNICATION permission.
        assertThrows(SecurityException.class, ()-> mSatelliteManager
                .registerForSatelliteDatagram(getContext().getMainExecutor(), callback));

        // Throws SecurityException as we do not have SATELLITE_COMMUNICATION permission.
        assertThrows(SecurityException.class, ()-> mSatelliteManager
                .unregisterForSatelliteDatagram(callback));
    }

    @Test
    public void testPollPendingSatelliteDatagrams() throws Exception {
        LinkedBlockingQueue<Integer> resultListener = new LinkedBlockingQueue<>(1);

        // Throws SecurityException as we do not have SATELLITE_COMMUNICATION permission.
        assertThrows(SecurityException.class,
                ()-> mSatelliteManager.pollPendingSatelliteDatagrams(
                        getContext().getMainExecutor(), resultListener::offer));
    }

    @Test
    public void testSendSatelliteDatagram() {
        LinkedBlockingQueue<Integer> resultListener = new LinkedBlockingQueue<>(1);

        String mText = "This is a test datagram message";
        SatelliteDatagram datagram = new SatelliteDatagram(mText.getBytes());

        // Throws SecurityException as we do not have SATELLITE_COMMUNICATION permission.
        assertThrows(SecurityException.class,
                ()-> mSatelliteManager.sendSatelliteDatagram(
                        SatelliteManager.DATAGRAM_TYPE_SOS_MESSAGE, datagram, true,
                        getContext().getMainExecutor(), resultListener::offer));
        // TODO: add detailed test
    }

    @Test
    public void testRequestIsSatelliteCommunicationAllowedForCurrentLocation() {
        final AtomicReference<Boolean> enabled = new AtomicReference<>();
        final AtomicReference<Integer> errorCode = new AtomicReference<>();
        OutcomeReceiver<Boolean, SatelliteManager.SatelliteException> receiver =
                new OutcomeReceiver<>() {
                    @Override
                    public void onResult(Boolean result) {
                        enabled.set(result);
                    }

                    @Override
                    public void onError(SatelliteManager.SatelliteException exception) {
                        errorCode.set(exception.getErrorCode());
                    }
                };

        // Throws SecurityException as we do not have SATELLITE_COMMUNICATION permission.
        assertThrows(SecurityException.class, () -> mSatelliteManager.requestIsSatelliteEnabled(
                getContext().getMainExecutor(), receiver));
    }

    @Test
    public void testRequestTimeForNextSatelliteVisibility() {
        final AtomicReference<Duration> nextVisibilityDuration = new AtomicReference<>();
        final AtomicReference<Integer> errorCode = new AtomicReference<>();
        OutcomeReceiver<Duration, SatelliteManager.SatelliteException> receiver =
                new OutcomeReceiver<>() {
                    @Override
                    public void onResult(Duration result) {
                        nextVisibilityDuration.set(result);
                    }

                    @Override
                    public void onError(SatelliteManager.SatelliteException exception) {
                        errorCode.set(exception.getErrorCode());
                    }
                };

        // Throws SecurityException as we do not have SATELLITE_COMMUNICATION permission.
        assertThrows(SecurityException.class,
                () -> mSatelliteManager.requestTimeForNextSatelliteVisibility(
                        getContext().getMainExecutor(), receiver));
    }

    private Context getContext() {
        return InstrumentationRegistry.getContext();
    }

    private void grantSatellitePermission() {
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.SATELLITE_COMMUNICATION);
    }

    private void revokeSatellitePermission() {
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .dropShellPermissionIdentity();
    }

    private static class SatelliteTransmissionUpdateCallbackTest implements
            SatelliteTransmissionUpdateCallback {
        @Override
        public void onSatellitePositionChanged(PointingInfo pointingInfo) {
            Log.d(TAG, "onSatellitePositionChanged: pointingInfo=" + pointingInfo);
        }

        @Override
        public void onDatagramTransferStateChanged(int state, int sendPendingCount,
                int receivePendingCount, int errorCode) {
            Log.d(TAG, "onSatellitePositionChanged: state=" + state + ", sendPendingCount="
                    + sendPendingCount + ", receivePendingCount=" + receivePendingCount
                    + ", errorCode=" + errorCode);
        }
    }

    private static class SatelliteProvisionStateCallbackTest implements
            SatelliteProvisionStateCallback {
        @Override
        public void onSatelliteProvisionStateChanged(boolean provisioned) {
            Log.d(TAG, "onSatelliteProvisionStateChanged: provisioned=" + provisioned);
        }
    }

    private static class SatelliteStateCallbackTest implements SatelliteStateCallback {
        @Override
        public void onSatelliteModemStateChanged(int state) {
            Log.d(TAG, "onSatelliteModemStateChanged: state=" + state);
        }
    }

    private static class SatelliteDatagramCallbackTest implements SatelliteDatagramCallback {
        @Override
        public void onSatelliteDatagramReceived(long datagramId, SatelliteDatagram datagram,
                int pendingCount, ILongConsumer callback) {
            Log.d(TAG, "onSatelliteDatagramReceived: datagramId=" + datagramId + ", datagram="
                    + datagram + ", pendingCount=" + pendingCount);
        }
    }
}
