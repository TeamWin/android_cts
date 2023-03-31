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

package android.telephony.satellite.cts;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.os.Build;
import android.os.CancellationSignal;
import android.os.SystemProperties;
import android.telephony.satellite.SatelliteDatagram;
import android.telephony.satellite.SatelliteManager;
import android.telephony.satellite.stub.SatelliteError;

import androidx.test.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class SatelliteManagerTestOnMockService extends SatelliteManagerTestBase {
    private static final String ALLOW_MOCK_MODEM_PROPERTY = "persist.radio.allow_mock_modem";
    private static final boolean DEBUG = !"user".equals(Build.TYPE);

    private MockSatelliteServiceManager mMockSatelliteServiceManager;
    private boolean mOriginalIsSatelliteEnabled = false;
    private boolean mOriginalIsSatelliteProvisioned = false;

    @Before
    public void setUp() throws Exception {
        super.setUp();

        enforceMockModemDeveloperSetting();

        mMockSatelliteServiceManager = new MockSatelliteServiceManager(
                InstrumentationRegistry.getInstrumentation());
        assertTrue(mMockSatelliteServiceManager.connectSatelliteService());
        mMockSatelliteServiceManager.setErrorCode(SatelliteError.ERROR_NONE);

        grantSatellitePermission();
        assertTrue(isSatelliteSupported());
        mOriginalIsSatelliteProvisioned = isSatelliteProvisioned();
        if (!mOriginalIsSatelliteProvisioned) {
            SatelliteProvisionStateCallbackTest satelliteProvisionStateCallback =
                    new SatelliteProvisionStateCallbackTest();
            long registerError = mSatelliteManager.registerForSatelliteProvisionStateChanged(
                    getContext().getMainExecutor(), satelliteProvisionStateCallback);
            assertEquals(SatelliteManager.SATELLITE_ERROR_NONE, registerError);

            assertTrue(provisionSatellite());
            assertTrue(satelliteProvisionStateCallback.waitUntilResult(1));
            assertTrue(satelliteProvisionStateCallback.isProvisioned);
            mSatelliteManager.unregisterForSatelliteProvisionStateChanged(
                    satelliteProvisionStateCallback);
        }

        mOriginalIsSatelliteEnabled = isSatelliteEnabled();
        if (!mOriginalIsSatelliteEnabled) {
            requestSatelliteEnabled(true);
        }
        revokeSatellitePermission();
    }

    @After
    public void tearDown() {
        mMockSatelliteServiceManager.setErrorCode(SatelliteError.ERROR_NONE);

        grantSatellitePermission();

        // Restore enabled state
        requestSatelliteEnabled(mOriginalIsSatelliteEnabled);

        // Restore provision state
        if (mOriginalIsSatelliteProvisioned) {
            if (!isSatelliteProvisioned()) {
                assertTrue(provisionSatellite());
            }
        } else {
            if (isSatelliteProvisioned()) {
                assertTrue(deprovisionSatellite());
            }
        }
        revokeSatellitePermission();

        assertTrue(mMockSatelliteServiceManager.restoreSatelliteServicePackageName());
    }

    @Test
    public void testProvisionSatelliteService() {
        logd("testProvisionSatelliteService: start");
        grantSatellitePermission();

        LinkedBlockingQueue<Integer> error = new LinkedBlockingQueue<>(1);
        SatelliteProvisionStateCallbackTest satelliteProvisionStateCallback =
                new SatelliteProvisionStateCallbackTest();
        long registerError = mSatelliteManager.registerForSatelliteProvisionStateChanged(
                getContext().getMainExecutor(), satelliteProvisionStateCallback);
        assertEquals(SatelliteManager.SATELLITE_ERROR_NONE, registerError);

        if (isSatelliteProvisioned()) {
            logd("testProvisionSatelliteService: dreprovision");
            assertTrue(deprovisionSatellite());
            assertTrue(satelliteProvisionStateCallback.waitUntilResult(1));
            assertFalse(satelliteProvisionStateCallback.isProvisioned);
        }

        logd("testProvisionSatelliteService: provision and cancel");
        satelliteProvisionStateCallback.clearProvisionedStates();
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
        assertEquals(SatelliteManager.SATELLITE_ERROR_NONE, (long) errorCode);

        // Provision succeeded and then got canceled - deprovisioned
        assertTrue(satelliteProvisionStateCallback.waitUntilResult(2));
        assertEquals(2, satelliteProvisionStateCallback.getTotalCountOfProvisionedStates());
        assertTrue(satelliteProvisionStateCallback.getProvisionedState(0));
        assertFalse(satelliteProvisionStateCallback.getProvisionedState(1));
        assertFalse(satelliteProvisionStateCallback.isProvisioned);
        assertFalse(isSatelliteProvisioned());

        logd("testProvisionSatelliteService: restore provision state");
        assertTrue(provisionSatellite());
        assertTrue(satelliteProvisionStateCallback.waitUntilResult(1));
        assertTrue(satelliteProvisionStateCallback.isProvisioned);
        mSatelliteManager.unregisterForSatelliteProvisionStateChanged(
                satelliteProvisionStateCallback);

        revokeSatellitePermission();
    }

    @Test
    public void testSatelliteModemStateChanged() {
        grantSatellitePermission();

        assertTrue(isSatelliteProvisioned());

        boolean originalEnabledState = isSatelliteEnabled();
        if (originalEnabledState) {
            // Turn off the satellite modem to move it to POWER_OFF state.
            requestSatelliteEnabled(false);
            assertFalse(isSatelliteEnabled());
        }

        SatelliteStateCallbackTest
                callback = new SatelliteStateCallbackTest();
        long registerResult = mSatelliteManager
                .registerForSatelliteModemStateChanged(getContext().getMainExecutor(), callback);
        assertEquals(SatelliteManager.SATELLITE_ERROR_NONE, registerResult);
        assertTrue(callback.waitUntilResult(1));
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_OFF, callback.modemState);

        // Turn on the satellite modem to move it to IDLE state.
        requestSatelliteEnabled(true);
        assertTrue(isSatelliteEnabled());
        assertTrue(callback.waitUntilResult(1));
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_IDLE, callback.modemState);

        SatelliteStateCallbackTest
                callback1 = new SatelliteStateCallbackTest();
        registerResult = mSatelliteManager
                .registerForSatelliteModemStateChanged(getContext().getMainExecutor(), callback1);
        assertEquals(SatelliteManager.SATELLITE_ERROR_NONE, registerResult);
        assertTrue(callback1.waitUntilResult(1));
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_IDLE, callback1.modemState);
        mSatelliteManager.unregisterForSatelliteModemStateChanged(callback);

        // Verify state transitions: IDLE -> TRANSFERRING -> LISTENING
        sendSatelliteDatagramWithSuccessfulResult(callback1);

        // Verify state transitions: LISTENING -> TRANSFERRING -> LISTENING
        receiveSatelliteDatagramWithSuccessfulResult(callback1);

        // Verify state transitions: LISTENING -> TRANSFERRING -> IDLE
        sendSatelliteDatagramWithFailedResult(callback1);

        // Verify state transitions: IDLE -> TRANSFERRING -> LISTENING
        sendSatelliteDatagramWithSuccessfulResult(callback1);

        // Verify state transitions: LISTENING -> TRANSFERRING -> IDLE
        receiveSatelliteDatagramWithFailedResult(callback1);

        if (!originalEnabledState) {
            // Restore original modem enabled state.
            requestSatelliteEnabled(false);
            assertEquals(isSatelliteEnabled(), originalEnabledState);
            assertTrue(callback1.waitUntilResult(1));
            assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_OFF, callback1.modemState);
        }
        mSatelliteManager.unregisterForSatelliteModemStateChanged(callback1);

        revokeSatellitePermission();
    }

    @Test
    public void testSatelliteDatagramReceivedAck() {
        grantSatellitePermission();

        assertTrue(isSatelliteProvisioned());

        SatelliteDatagramCallbackTest satelliteDatagramCallback =
                new SatelliteDatagramCallbackTest();
        mSatelliteManager.registerForSatelliteDatagram(
                getContext().getMainExecutor(), satelliteDatagramCallback);

        String receivedText = "This is a test datagram message from satellite";
        android.telephony.satellite.stub.SatelliteDatagram receivedDatagram =
                new android.telephony.satellite.stub.SatelliteDatagram();
        receivedDatagram.data = receivedText.getBytes();
        mMockSatelliteServiceManager.sendOnSatelliteDatagramReceived(receivedDatagram, 0);

        assertTrue(satelliteDatagramCallback.waitUntilResult(1));
        assertArrayEquals(satelliteDatagramCallback.mDatagram.getSatelliteDatagram(),
                receivedText.getBytes());

        revokeSatellitePermission();
    }

    /**
     * Before calling this function, caller need to make sure the modem is in LISTENING or IDLE
     * state.
     */
    private void sendSatelliteDatagramWithSuccessfulResult(SatelliteStateCallbackTest callback) {
        if (callback.modemState != SatelliteManager.SATELLITE_MODEM_STATE_LISTENING
                && callback.modemState != SatelliteManager.SATELLITE_MODEM_STATE_IDLE) {
            fail("sendSatelliteDatagramWithSuccessfulResult: wrong modem state="
                    + callback.modemState);
            return;
        }

        LinkedBlockingQueue<Integer> resultListener = new LinkedBlockingQueue<>(1);
        String mText = "This is a test datagram message from user";
        SatelliteDatagram datagram = new SatelliteDatagram(mText.getBytes());

        callback.clearModemStates();
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
        assertEquals(SatelliteManager.SATELLITE_ERROR_NONE, (long) errorCode);

        /**
         * Modem state should have the following transitions:
         * 1) IDLE to TRANSFERRING.
         * 2) TRANSFERRING to LISTENING.
         * 3) LISTENING to IDLE.
         *
         * Satellite will stay at LISTENING mode for 3 minutes by default. Thus, we will skip
         * checking the last state transition.
         */
        assertTrue(callback.waitUntilResult(2));
        assertEquals(2, callback.getTotalCountOfModemStates());
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_DATAGRAM_TRANSFERRING,
                callback.getModemState(0));
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_LISTENING,
                callback.getModemState(1));
    }

    /**
     * Before calling this function, caller need to make sure the modem is in LISTENING or IDLE
     * state.
     */
    private void sendSatelliteDatagramWithFailedResult(SatelliteStateCallbackTest callback) {
        if (callback.modemState != SatelliteManager.SATELLITE_MODEM_STATE_LISTENING
                && callback.modemState != SatelliteManager.SATELLITE_MODEM_STATE_IDLE) {
            fail("sendSatelliteDatagramWithFailedResult: wrong modem state=" + callback.modemState);
            return;
        }

        LinkedBlockingQueue<Integer> resultListener = new LinkedBlockingQueue<>(1);
        String mText = "This is a test datagram message from user";
        SatelliteDatagram datagram = new SatelliteDatagram(mText.getBytes());

        mMockSatelliteServiceManager.setErrorCode(SatelliteError.SATELLITE_ERROR);
        callback.clearModemStates();
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
        assertEquals(SatelliteManager.SATELLITE_ERROR, (long) errorCode);
        assertTrue(callback.waitUntilResult(2));
        assertEquals(2, callback.getTotalCountOfModemStates());
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_DATAGRAM_TRANSFERRING,
                callback.getModemState(0));
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_IDLE,
                callback.getModemState(1));
        mMockSatelliteServiceManager.setErrorCode(SatelliteError.ERROR_NONE);
    }

    /**
     * Before calling this function, caller need to make sure the modem is in LISTENING state.
     */
    private void receiveSatelliteDatagramWithSuccessfulResult(
            SatelliteStateCallbackTest callback) {
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_LISTENING, callback.modemState);

        // TODO (b/275086547): remove the below registerForSatelliteDatagram command when the bug
        // is resolved.
        SatelliteDatagramCallbackTest satelliteDatagramCallback =
                new SatelliteDatagramCallbackTest();
        mSatelliteManager.registerForSatelliteDatagram(
                getContext().getMainExecutor(), satelliteDatagramCallback);

        callback.clearModemStates();
        mMockSatelliteServiceManager.sendOnPendingDatagrams();
        assertTrue(mMockSatelliteServiceManager.waitForEventOnPollPendingSatelliteDatagrams(1));

        assertTrue(callback.waitUntilResult(1));
        assertEquals(1, callback.getTotalCountOfModemStates());
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_DATAGRAM_TRANSFERRING,
                callback.getModemState(0));

        String receivedText = "This is a test datagram message from satellite";
        android.telephony.satellite.stub.SatelliteDatagram receivedDatagram =
                new android.telephony.satellite.stub.SatelliteDatagram();
        receivedDatagram.data = receivedText.getBytes();
        callback.clearModemStates();
        mMockSatelliteServiceManager.sendOnSatelliteDatagramReceived(receivedDatagram, 0);
        assertTrue(callback.waitUntilResult(1));
        assertEquals(1, callback.getTotalCountOfModemStates());
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_LISTENING,
                callback.getModemState(0));
        mSatelliteManager.unregisterForSatelliteDatagram(satelliteDatagramCallback);
    }

    /**
     * Before calling this function, caller need to make sure the modem is in LISTENING state.
     */
    private void receiveSatelliteDatagramWithFailedResult(
            SatelliteStateCallbackTest callback) {
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_LISTENING, callback.modemState);

        // TODO (b/275086547): remove the below registerForSatelliteDatagram command when the bug
        // is resolved.
        SatelliteDatagramCallbackTest satelliteDatagramCallback =
                new SatelliteDatagramCallbackTest();
        mSatelliteManager.registerForSatelliteDatagram(
                getContext().getMainExecutor(), satelliteDatagramCallback);

        callback.clearModemStates();
        mMockSatelliteServiceManager.setErrorCode(SatelliteError.SATELLITE_ERROR);
        mMockSatelliteServiceManager.sendOnPendingDatagrams();
        assertTrue(mMockSatelliteServiceManager.waitForEventOnPollPendingSatelliteDatagrams(1));

        assertTrue(callback.waitUntilResult(2));
        assertEquals(2, callback.getTotalCountOfModemStates());
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_DATAGRAM_TRANSFERRING,
                callback.getModemState(0));
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_IDLE,
                callback.getModemState(1));

        mSatelliteManager.unregisterForSatelliteDatagram(satelliteDatagramCallback);
        mMockSatelliteServiceManager.setErrorCode(SatelliteError.ERROR_NONE);
    }

    private static void enforceMockModemDeveloperSetting() {
        boolean isAllowed = SystemProperties.getBoolean(ALLOW_MOCK_MODEM_PROPERTY, false);
        // Check for developer settings for user build. Always allow for debug builds
        if (!isAllowed && !DEBUG) {
            throw new IllegalStateException(
                    "!! Enable Mock Modem before running this test !! "
                            + "Developer options => Allow Mock Modem");
        }
    }
}
