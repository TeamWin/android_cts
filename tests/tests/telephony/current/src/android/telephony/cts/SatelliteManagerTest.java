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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeNoException;
import static org.junit.Assume.assumeTrue;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.CancellationSignal;
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

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class SatelliteManagerTest {
    private static final String TAG = "SatelliteManagerTest";

    private static final String TOKEN = "TEST_TOKEN";
    private static final String REGION = "TEST_REGION";

    private static final long TIMEOUT = 1000;

    private SatelliteManager mSatelliteManager;
    private boolean mIsSatelliteSupported = false;
    private boolean mOriginalIsSatelliteEnabled = false;
    private boolean mOriginalIsSatelliteProvisioned = false;
    private boolean mIsProvisionable = false;

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

        grantSatellitePermission();
        mIsSatelliteSupported = isSatelliteSupported();
        if (mIsSatelliteSupported) {
            mOriginalIsSatelliteProvisioned = isSatelliteProvisioned();

            boolean provisioned = mOriginalIsSatelliteProvisioned;
            if (!mOriginalIsSatelliteProvisioned) {
                SatelliteProvisionStateCallbackTest satelliteProvisionStateCallback =
                        new SatelliteProvisionStateCallbackTest();
                long registerError = mSatelliteManager.registerForSatelliteProvisionStateChanged(
                        getContext().getMainExecutor(), satelliteProvisionStateCallback);
                assertEquals(SatelliteManager.SATELLITE_ERROR_NONE, registerError);

                provisioned = provisionSatellite();
                if (provisioned) {
                    mIsProvisionable = true;
                    assertTrue(satelliteProvisionStateCallback.waitUntilResult());
                    assertTrue(satelliteProvisionStateCallback.isProvisioned);
                }
                mSatelliteManager.unregisterForSatelliteProvisionStateChanged(
                        satelliteProvisionStateCallback);
            }

            mOriginalIsSatelliteEnabled = isSatelliteEnabled();
            if (provisioned && !mOriginalIsSatelliteEnabled) {
                requestSatelliteEnabled(true);
            }
        }
        revokeSatellitePermission();
    }

    @After
    public void tearDown() {
        grantSatellitePermission();
        if (mIsSatelliteSupported) {
            // Restore enabled state
            requestSatelliteEnabled(mOriginalIsSatelliteEnabled);

            // Restore provision state
            if (mOriginalIsSatelliteProvisioned) {
                if (!isSatelliteProvisioned()) {
                    provisionSatellite();
                }
            } else {
                if (isSatelliteProvisioned()) {
                    assertTrue(deprovisionSatellite());
                }
            }
        }
        revokeSatellitePermission();
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
        mSatelliteManager.startSatelliteTransmissionUpdates(
                getContext().getMainExecutor(), error::offer, callback);
        Integer errorCode = error.poll(TIMEOUT, TimeUnit.MILLISECONDS);
        assertNotNull(errorCode);
        if (errorCode == SatelliteManager.SATELLITE_ERROR_NONE) {
            Log.d(TAG, "Successfully started transmission updates.");
        } else {
            Log.d(TAG, "Failed to start transmission updates: " + errorCode);
        }
        getContext().getSystemService(SatelliteManager.class).stopSatelliteTransmissionUpdates(
                callback, getContext().getMainExecutor(), error::offer);
        errorCode = error.poll(TIMEOUT, TimeUnit.MILLISECONDS);
        assertNotNull(errorCode);
        Log.d(TAG, "Stop transmission updates: " + errorCode);

        mSatelliteManager.stopSatelliteTransmissionUpdates(
                callback, getContext().getMainExecutor(), error::offer);
        errorCode = error.poll(TIMEOUT, TimeUnit.MILLISECONDS);
        assertNotNull(errorCode);
        assertEquals(SatelliteManager.SATELLITE_INVALID_ARGUMENTS, (long) errorCode);
        revokeSatellitePermission();
    }

    @Test
    public void testProvisionSatelliteService() {
        LinkedBlockingQueue<Integer> error = new LinkedBlockingQueue<>(1);

        // Throws SecurityException as we do not have SATELLITE_COMMUNICATION permission.
        assertThrows(SecurityException.class,
                () -> mSatelliteManager.provisionSatelliteService(
                        "", "", null, getContext().getMainExecutor(), error::offer));

        if (!mIsSatelliteSupported) {
            Log.d(TAG, "testProvisionSatelliteService: Satellite is not supported "
                    + "on the device.");
            return;
        }

        grantSatellitePermission();

        SatelliteProvisionStateCallbackTest satelliteProvisionStateCallback =
                new SatelliteProvisionStateCallbackTest();
        long registerError = mSatelliteManager.registerForSatelliteProvisionStateChanged(
                getContext().getMainExecutor(), satelliteProvisionStateCallback);
        assertEquals(SatelliteManager.SATELLITE_ERROR_NONE, registerError);

        if (isSatelliteProvisioned()) {
            if (!deprovisionSatellite()) {
                Log.d(TAG, "testProvisionSatelliteService: deprovisionSatellite failed");
                return;
            }
            assertTrue(satelliteProvisionStateCallback.waitUntilResult());
            assertFalse(satelliteProvisionStateCallback.isProvisioned);
        }

        CancellationSignal cancellationSignal = new CancellationSignal();
        mSatelliteManager.provisionSatelliteService(
                TOKEN, REGION, cancellationSignal, getContext().getMainExecutor(), error::offer);
        cancellationSignal.cancel();
        Integer errorCode;
        try {
            errorCode = error.poll(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("testProvisionSatelliteService: Got InterruptedException ex=" + ex);
            return;
        }
        assertNotNull(errorCode);
        Log.d(TAG, "testProvisionSatelliteService: provision result=" + errorCode);

        if (mIsProvisionable) {
            assertFalse(isSatelliteProvisioned());
            assertTrue(provisionSatellite());
            assertTrue(isSatelliteProvisioned());
        }
        mSatelliteManager.unregisterForSatelliteProvisionStateChanged(
                satelliteProvisionStateCallback);

        revokeSatellitePermission();
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
    public void testRequestSatelliteEnabled() throws Exception {
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
                        Log.d(TAG, "onResult: result=" + result);
                        enabled.set(result);
                    }

                    @Override
                    public void onError(SatelliteManager.SatelliteException exception) {
                        Log.d(TAG, "onError: onError=" + exception);
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

        Integer error = errorCode.get();
        Boolean isSupported = supported.get();
        if (error == null) {
            assertNotNull(isSupported);
            Log.d(TAG, "testRequestIsSatelliteSupported isSupported=" + isSupported);
        } else {
            assertNull(isSupported);
            Log.d(TAG, "testRequestIsSatelliteSupported error=" + error);
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
    public void testSatelliteModemStateChanged() {
        SatelliteStateCallbackTest callback = new SatelliteStateCallbackTest();

        // Throws SecurityException as we do not have SATELLITE_COMMUNICATION permission.
        assertThrows(SecurityException.class, ()-> mSatelliteManager
                .registerForSatelliteModemStateChanged(getContext().getMainExecutor(), callback));

        // Throws SecurityException as we do not have SATELLITE_COMMUNICATION permission.
        assertThrows(SecurityException.class, ()-> mSatelliteManager
                .unregisterForSatelliteModemStateChanged(callback));

        if (!mIsSatelliteSupported) {
            Log.d(TAG, "Satellite is not supported on the device");
            return;
        }

        grantSatellitePermission();

        if (!isSatelliteProvisioned()) {
            Log.d(TAG, "Satellite is not provisioned yet");
            return;
        }

        boolean originalEnabledState = isSatelliteEnabled();
        if (originalEnabledState) {
            requestSatelliteEnabled(false);
            assertFalse(isSatelliteEnabled());
        }

        long registerResult = mSatelliteManager
                .registerForSatelliteModemStateChanged(getContext().getMainExecutor(), callback);
        assertEquals(SatelliteManager.SATELLITE_ERROR_NONE, registerResult);
        assertTrue(callback.waitUntilResult(1));
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_OFF, callback.modemState);

        requestSatelliteEnabled(true);
        assertTrue(isSatelliteEnabled());
        assertTrue(callback.waitUntilResult(1));
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_IDLE, callback.modemState);

        SatelliteStateCallbackTest callback1 = new SatelliteStateCallbackTest();
        registerResult = mSatelliteManager
                .registerForSatelliteModemStateChanged(getContext().getMainExecutor(), callback1);
        assertEquals(SatelliteManager.SATELLITE_ERROR_NONE, registerResult);
        assertTrue(callback1.waitUntilResult(1));
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_IDLE, callback1.modemState);
        mSatelliteManager.unregisterForSatelliteModemStateChanged(callback);

        LinkedBlockingQueue<Integer> resultListener = new LinkedBlockingQueue<>(1);
        String mText = "This is a test datagram message";
        SatelliteDatagram datagram = new SatelliteDatagram(mText.getBytes());
        callback1.clearModemStates();
        mSatelliteManager.sendSatelliteDatagram(
                SatelliteManager.DATAGRAM_TYPE_SOS_MESSAGE, datagram, true,
                getContext().getMainExecutor(), resultListener::offer);

        Integer errorCode;
        try {
            errorCode = resultListener.poll(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("testSatelliteModemStateChanged: Got InterruptedException in waiting"
                    + " for the sendSatelliteDatagram result code");
            return;
        }
        assertNotNull(errorCode);
        Log.d(TAG, "testSatelliteModemStateChanged: sendSatelliteDatagram errorCode="
                + errorCode);

        assertFalse(callback.waitUntilResult(1));
        assertTrue(callback1.waitUntilResult(2));
        assertTrue(callback1.getTotalCountOfModemStates() >= 2);
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_DATAGRAM_TRANSFERRING,
                callback1.getModemState(0));
        if (errorCode == SatelliteManager.SATELLITE_ERROR_NONE) {
            /**
             * Modem state should have the following transitions:
             * 1) IDLE to TRANSFERRING.
             * 2) TRANSFERRING to LISTENING.
             * 3) LISTENING to IDLE
             */
            assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_LISTENING,
                    callback1.getModemState(1));
            /**
             * Satellite will stay at LISTENING mode for 3 minutes by default. Thus, we will skip
             * checking the last state transition.
             */
        } else {
            /**
             * Modem state should have the following transitions:
             * 1) IDLE to TRANSFERRING.
             * 2) TRANSFERRING to IDLE.
             */
            assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_IDLE,
                    callback1.getModemState(1));
        }

        if (!originalEnabledState) {
            // Restore original modem enabled state.
            requestSatelliteEnabled(false);
            assertFalse(isSatelliteEnabled());
            assertFalse(callback.waitUntilResult(1));
            assertTrue(callback1.waitUntilResult(1));
            assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_OFF, callback1.modemState);
        }
        mSatelliteManager.unregisterForSatelliteModemStateChanged(callback1);

        revokeSatellitePermission();
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
        public void onSendDatagramStateChanged(int state, int sendPendingCount, int errorCode) {
            Log.d(TAG, "onSendDatagramStateChanged: state=" + state + ", sendPendingCount="
                    + sendPendingCount + ", errorCode=" + errorCode);
        }

        @Override
        public void onReceiveDatagramStateChanged(int state, int receivePendingCount, int errorCode) {
            Log.d(TAG, "onReceiveDatagramStateChanged: state=" + state + ", "
                    + "receivePendingCount=" + receivePendingCount + ", errorCode=" + errorCode);
        }
    }

    private static class SatelliteProvisionStateCallbackTest implements
            SatelliteProvisionStateCallback {
        public boolean isProvisioned = false;
        private final Semaphore mSemaphore = new Semaphore(0);

        @Override
        public void onSatelliteProvisionStateChanged(boolean provisioned) {
            Log.d(TAG, "onSatelliteProvisionStateChanged: provisioned=" + provisioned);
            isProvisioned = provisioned;
            try {
                mSemaphore.release();
            } catch (Exception ex) {
                Log.d(TAG, "onSatelliteProvisionStateChanged: Got exception, ex=" + ex);
            }
        }

        public boolean waitUntilResult() {
            try {
                if (!mSemaphore.tryAcquire(TIMEOUT, TimeUnit.MILLISECONDS)) {
                    Log.d(TAG, "Timeout to receive onSatelliteProvisionStateChanged");
                    return false;
                }
                return true;
            } catch (Exception ex) {
                Log.d(TAG, "onSatelliteProvisionStateChanged: Got exception=" + ex);
                return false;
            }
        }
    }

    private static class SatelliteStateCallbackTest implements SatelliteStateCallback {
        public int modemState = SatelliteManager.SATELLITE_MODEM_STATE_OFF;
        private List<Integer> mModemStates = new ArrayList<>();
        private final Object mModemStatesLock = new Object();
        private final Semaphore mSemaphore = new Semaphore(0);

        @Override
        public void onSatelliteModemStateChanged(int state) {
            Log.d(TAG, "onSatelliteModemStateChanged: state=" + state);
            modemState = state;
            synchronized (mModemStatesLock) {
                mModemStates.add(state);
            }
            try {
                mSemaphore.release();
            } catch (Exception ex) {
                Log.d(TAG, "onSatelliteModemStateChanged: Got exception, ex=" + ex);
            }
        }

        public boolean waitUntilResult(int count) {
            for (int i = 0; i < count; i++) {
                try {
                    if (!mSemaphore.tryAcquire(TIMEOUT, TimeUnit.MILLISECONDS)) {
                        Log.d(TAG, "Timeout to receive onSatelliteModemStateChanged");
                        return false;
                    }
                } catch (Exception ex) {
                    Log.d(TAG, "onSatelliteModemStateChanged: Got exception=" + ex);
                    return false;
                }
            }
            return true;
        }

        public void clearModemStates() {
            synchronized (mModemStatesLock) {
                mModemStates.clear();
            }
        }

        public int getModemState(int index) {
            synchronized (mModemStatesLock) {
                if (index < mModemStates.size()) {
                    return mModemStates.get(index);
                } else {
                    Log.e(TAG, "getModemState: invalid index=" + index
                            + ", mModemStates.size=" + mModemStates.size());
                    return -1;
                }
            }
        }

        public int getTotalCountOfModemStates() {
            synchronized (mModemStatesLock) {
                return mModemStates.size();
            }
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

    private boolean provisionSatellite() {
        LinkedBlockingQueue<Integer> error = new LinkedBlockingQueue<>(1);

        mSatelliteManager.provisionSatelliteService(
                TOKEN, REGION, null, getContext().getMainExecutor(), error::offer);
        Integer errorCode;
        try {
            errorCode = error.poll(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            Log.d(TAG, "provisionSatellite ex=" + ex);
            return false;
        }
        if (errorCode == null || errorCode != SatelliteManager.SATELLITE_ERROR_NONE) {
            Log.d(TAG, "provisionSatellite failed with errorCode=" + errorCode);
            return false;
        }
        return true;
    }

    private boolean deprovisionSatellite() {
        LinkedBlockingQueue<Integer> error = new LinkedBlockingQueue<>(1);

        mSatelliteManager.deprovisionSatelliteService(
                TOKEN, getContext().getMainExecutor(), error::offer);
        Integer errorCode;
        try {
            errorCode = error.poll(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            Log.d(TAG, "deprovisionSatellite ex=" + ex);
            return false;
        }
        if (errorCode == null || errorCode != SatelliteManager.SATELLITE_ERROR_NONE) {
            Log.d(TAG, "deprovisionSatellite failed with errorCode=" + errorCode);
            return false;
        }
        return true;
    }

    private boolean isSatelliteProvisioned() {
        final AtomicReference<Boolean> provisioned = new AtomicReference<>();
        final AtomicReference<Integer> errorCode = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        OutcomeReceiver<Boolean, SatelliteManager.SatelliteException> receiver =
                new OutcomeReceiver<>() {
                    @Override
                    public void onResult(Boolean result) {
                        provisioned.set(result);
                        latch.countDown();
                    }

                    @Override
                    public void onError(SatelliteManager.SatelliteException exception) {
                        errorCode.set(exception.getErrorCode());
                        latch.countDown();
                    }
                };

        mSatelliteManager.requestIsSatelliteProvisioned(
                getContext().getMainExecutor(), receiver);
        try {
            assertTrue(latch.await(TIMEOUT, TimeUnit.MILLISECONDS));
        } catch (InterruptedException ex) {
            Log.d(TAG, "isSatelliteProvisioned ex=" + ex);
            return false;
        }

        Integer error = errorCode.get();
        Boolean isProvisioned = provisioned.get();
        if (error == null) {
            assertNotNull(isProvisioned);
            return isProvisioned;
        } else {
            assertNull(isProvisioned);
            Log.d(TAG, "isSatelliteProvisioned error=" + error);
            return false;
        }
    }

    private boolean isSatelliteEnabled() {
        final AtomicReference<Boolean> enabled = new AtomicReference<>();
        final AtomicReference<Integer> errorCode = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        OutcomeReceiver<Boolean, SatelliteManager.SatelliteException> receiver =
                new OutcomeReceiver<>() {
                    @Override
                    public void onResult(Boolean result) {
                        enabled.set(result);
                        latch.countDown();
                    }

                    @Override
                    public void onError(SatelliteManager.SatelliteException exception) {
                        errorCode.set(exception.getErrorCode());
                        latch.countDown();
                    }
                };


        mSatelliteManager.requestIsSatelliteEnabled(
                getContext().getMainExecutor(), receiver);
        try {
            assertTrue(latch.await(TIMEOUT, TimeUnit.MILLISECONDS));
        } catch (InterruptedException ex) {
            Log.d(TAG, "isSatelliteEnabled ex=" + ex);
            return false;
        }

        Integer error = errorCode.get();
        Boolean isEnabled = enabled.get();
        if (error == null) {
            assertNotNull(isEnabled);
            return isEnabled;
        } else {
            assertNull(isEnabled);
            Log.d(TAG, "isSatelliteEnabled error=" + error);
            return false;
        }
    }

    private void requestSatelliteEnabled(boolean enabled) {
        LinkedBlockingQueue<Integer> error = new LinkedBlockingQueue<>(1);
        mSatelliteManager.requestSatelliteEnabled(
                enabled, true, getContext().getMainExecutor(), error::offer);
        Integer errorCode;
        try {
            errorCode = error.poll(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            Log.d(TAG, "requestSatelliteEnabled: ex=" + ex);
            fail("requestSatelliteEnabled failed with ex=" + ex);
            return;
        }
        assertNotNull(errorCode);
        Log.d(TAG, "requestSatelliteEnabled: errorCode=" + errorCode);
        assertEquals(SatelliteManager.SATELLITE_ERROR_NONE, (long) errorCode);
    }

    private boolean isSatelliteSupported() {
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
        try {
            assertTrue(latch.await(TIMEOUT, TimeUnit.MILLISECONDS));
        } catch (InterruptedException ex) {
            Log.d(TAG, "isSatelliteSupported ex=" + ex);
            return false;
        }

        Integer error = errorCode.get();
        Boolean isSupported = supported.get();
        if (error == null) {
            assertNotNull(isSupported);
            Log.d(TAG, "isSatelliteSupported isSupported=" + isSupported);
            return isSupported;
        } else {
            assertNull(isSupported);
            Log.d(TAG, "isSatelliteSupported error=" + error);
            return false;
        }
    }
}
