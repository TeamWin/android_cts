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

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import android.os.Build;
import android.os.CancellationSignal;
import android.os.OutcomeReceiver;
import android.os.SystemProperties;
import android.telephony.satellite.AntennaDirection;
import android.telephony.satellite.AntennaPosition;
import android.telephony.satellite.PointingInfo;
import android.telephony.satellite.SatelliteCapabilities;
import android.telephony.satellite.SatelliteDatagram;
import android.telephony.satellite.SatelliteManager;
import android.telephony.satellite.stub.SatelliteError;

import androidx.test.InstrumentationRegistry;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class SatelliteManagerTestOnMockService extends SatelliteManagerTestBase {
    private static final String ALLOW_MOCK_MODEM_PROPERTY = "persist.radio.allow_mock_modem";
    private static final boolean DEBUG = !"user".equals(Build.TYPE);
    private static final long TEST_SATELLITE_LISTENING_TIMEOUT_MILLIS = 100;
    private static final long TEST_SATELLITE_LISTENING_FOREVER_TIMEOUT_MILLIS = 60 * 10 * 1000;

    private static MockSatelliteServiceManager sMockSatelliteServiceManager;
    private static boolean sOriginalIsSatelliteEnabled = false;
    private static boolean sOriginalIsSatelliteProvisioned = false;

    /** SatelliteCapabilities constant indicating that the radio technology is proprietary. */
    private static final Set<Integer> SUPPORTED_RADIO_TECHNOLOGIES;
    static {
        SUPPORTED_RADIO_TECHNOLOGIES = new HashSet<>();
        SUPPORTED_RADIO_TECHNOLOGIES.add(SatelliteManager.NT_RADIO_TECHNOLOGY_PROPRIETARY);
    }
    /** SatelliteCapabilities constant indicating that pointing to satellite is required. */
    private static final boolean POINTING_TO_SATELLITE_REQUIRED = true;
    /** SatelliteCapabilities constant indicating the maximum number of characters per datagram. */
    private static final int MAX_BYTES_PER_DATAGRAM = 339;
    /** SatelliteCapabilites constant antenna position map received from satellite modem. */
    private static final Map<Integer, AntennaPosition> ANTENNA_POSITION_MAP;
    static {
        ANTENNA_POSITION_MAP = new HashMap<>();
        ANTENNA_POSITION_MAP.put(SatelliteManager.DISPLAY_MODE_OPENED,
                new AntennaPosition(new AntennaDirection(1,1,1),
                        SatelliteManager.DEVICE_HOLD_POSITION_PORTRAIT));
        ANTENNA_POSITION_MAP.put(SatelliteManager.DISPLAY_MODE_CLOSED,
                new AntennaPosition(new AntennaDirection(2,2,2),
                        SatelliteManager.DEVICE_HOLD_POSITION_LANDSCAPE_LEFT));
    }

    @BeforeClass
    public static void beforeAllTests() throws Exception {
        logd("beforeAllTests");

        if (!shouldTestSatellite()) return;

        beforeAllTestsBase();
        enforceMockModemDeveloperSetting();

        sMockSatelliteServiceManager = new MockSatelliteServiceManager(
                InstrumentationRegistry.getInstrumentation());
        assertTrue(sMockSatelliteServiceManager.connectSatelliteService());

        grantSatellitePermission();
        assertTrue(isSatelliteSupported());
        sOriginalIsSatelliteProvisioned = isSatelliteProvisioned();
        if (!sOriginalIsSatelliteProvisioned) {
            logd("Provision satellite");

            SatelliteProvisionStateCallbackTest satelliteProvisionStateCallback =
                    new SatelliteProvisionStateCallbackTest();
            long registerError = sSatelliteManager.registerForSatelliteProvisionStateChanged(
                    getContext().getMainExecutor(), satelliteProvisionStateCallback);
            assertEquals(SatelliteManager.SATELLITE_ERROR_NONE, registerError);

            assertTrue(provisionSatellite());

            assertTrue(satelliteProvisionStateCallback.waitUntilResult(1));
            assertTrue(satelliteProvisionStateCallback.isProvisioned);
            sSatelliteManager.unregisterForSatelliteProvisionStateChanged(
                    satelliteProvisionStateCallback);
        }

        sOriginalIsSatelliteEnabled = isSatelliteEnabled();
        if (!sOriginalIsSatelliteEnabled) {
            logd("Enable satellite");

            SatelliteStateCallbackTest callback = new SatelliteStateCallbackTest();
            long registerResult = sSatelliteManager.registerForSatelliteModemStateChanged(
                    getContext().getMainExecutor(), callback);
            assertEquals(SatelliteManager.SATELLITE_ERROR_NONE, registerResult);
            assertTrue(callback.waitUntilResult(1));

            requestSatelliteEnabled(true);

            assertTrue(callback.waitUntilResult(1));
            assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_IDLE, callback.modemState);
            assertTrue(isSatelliteEnabled());
            sSatelliteManager.unregisterForSatelliteModemStateChanged(callback);
        }
        revokeSatellitePermission();
    }

    @AfterClass
    public static void afterAllTests() {
        logd("afterAllTests");

        if (!shouldTestSatellite()) return;

        sMockSatelliteServiceManager.setErrorCode(SatelliteError.ERROR_NONE);

        grantSatellitePermission();
        logd("Restore enabled state");
        if (isSatelliteEnabled() != sOriginalIsSatelliteEnabled) {
            SatelliteStateCallbackTest callback = new SatelliteStateCallbackTest();
            long registerResult = sSatelliteManager.registerForSatelliteModemStateChanged(
                    getContext().getMainExecutor(), callback);
            assertEquals(SatelliteManager.SATELLITE_ERROR_NONE, registerResult);
            assertTrue(callback.waitUntilResult(1));

            requestSatelliteEnabled(sOriginalIsSatelliteEnabled);

            assertTrue(callback.waitUntilResult(1));
            assertEquals(sOriginalIsSatelliteEnabled ? SatelliteManager.SATELLITE_MODEM_STATE_IDLE :
                    SatelliteManager.SATELLITE_MODEM_STATE_OFF, callback.modemState);
            assertTrue(isSatelliteEnabled() == sOriginalIsSatelliteEnabled);
            sSatelliteManager.unregisterForSatelliteModemStateChanged(callback);
        }

        logd("Restore provision state");
        SatelliteProvisionStateCallbackTest satelliteProvisionStateCallback =
                new SatelliteProvisionStateCallbackTest();
        long registerError = sSatelliteManager.registerForSatelliteProvisionStateChanged(
                getContext().getMainExecutor(), satelliteProvisionStateCallback);
        assertEquals(SatelliteManager.SATELLITE_ERROR_NONE, registerError);
        if (sOriginalIsSatelliteProvisioned) {
            if (!isSatelliteProvisioned()) {
                assertTrue(provisionSatellite());

                assertTrue(satelliteProvisionStateCallback.waitUntilResult(1));
                assertTrue(satelliteProvisionStateCallback.isProvisioned);
            }
        } else {
            if (isSatelliteProvisioned()) {
                assertTrue(deprovisionSatellite());

                assertTrue(satelliteProvisionStateCallback.waitUntilResult(1));
                assertFalse(satelliteProvisionStateCallback.isProvisioned);
            }
        }
        sSatelliteManager.unregisterForSatelliteProvisionStateChanged(
                satelliteProvisionStateCallback);
        revokeSatellitePermission();

        assertTrue(sMockSatelliteServiceManager.restoreSatelliteServicePackageName());
        sMockSatelliteServiceManager = null;
        afterAllTestsBase();
    }

    @Before
    public void setUp() throws Exception {
        logd("setUp");

        if (!shouldTestSatellite()) return;
        assumeTrue(sMockSatelliteServiceManager != null);
        sMockSatelliteServiceManager.setErrorCode(SatelliteError.ERROR_NONE);
        sMockSatelliteServiceManager.setWaitToSend(false);
    }

    @After
    public void tearDown() {
        logd("tearDown");
    }

    @Test
    public void testProvisionSatelliteService() {
        if (!shouldTestSatellite()) return;

        logd("testProvisionSatelliteService: start");
        grantSatellitePermission();

        LinkedBlockingQueue<Integer> error = new LinkedBlockingQueue<>(1);
        SatelliteProvisionStateCallbackTest satelliteProvisionStateCallback =
                new SatelliteProvisionStateCallbackTest();
        long registerError = sSatelliteManager.registerForSatelliteProvisionStateChanged(
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
        String mText = "This is test provision data.";
        byte[] testProvisionData = mText.getBytes();
        sSatelliteManager.provisionSatelliteService(TOKEN, testProvisionData, cancellationSignal,
                getContext().getMainExecutor(), error::offer);
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
        sSatelliteManager.unregisterForSatelliteProvisionStateChanged(
                satelliteProvisionStateCallback);

        revokeSatellitePermission();
    }

    @Test
    public void testSatelliteModemStateChanged() {
        if (!shouldTestSatellite()) return;

        grantSatellitePermission();

        assertTrue(isSatelliteProvisioned());

        SatelliteStateCallbackTest callback = new SatelliteStateCallbackTest();
        boolean originalEnabledState = isSatelliteEnabled();
        boolean registerCallback = false;
        if (originalEnabledState) {
            registerCallback = true;

            long registerResult = sSatelliteManager.registerForSatelliteModemStateChanged(
                    getContext().getMainExecutor(), callback);
            assertEquals(SatelliteManager.SATELLITE_ERROR_NONE, registerResult);
            assertTrue(callback.waitUntilResult(1));

            requestSatelliteEnabled(false);

            assertTrue(callback.waitUntilResult(1));
            assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_OFF, callback.modemState);
            assertFalse(isSatelliteEnabled());
        }
        if (!registerCallback) {
            long registerResult = sSatelliteManager
                    .registerForSatelliteModemStateChanged(getContext().getMainExecutor(),
                            callback);
            assertEquals(SatelliteManager.SATELLITE_ERROR_NONE, registerResult);
            assertTrue(callback.waitUntilResult(1));
            assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_OFF, callback.modemState);
        }

        assertTrue(sMockSatelliteServiceManager.connectSatelliteGatewayService());
        assertTrue(sMockSatelliteServiceManager.overrideSatellitePointingUiClassName());
        sMockSatelliteServiceManager.clearMockPointingUiActivityStatusChanges();
        requestSatelliteEnabled(true);

        assertTrue(callback.waitUntilResult(1));
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_IDLE, callback.modemState);
        assertTrue(isSatelliteEnabled());
        assertTrue(sMockSatelliteServiceManager.waitForRemoteSatelliteGatewayServiceConnected(1));
        assertTrue(sMockSatelliteServiceManager.waitForEventMockPointingUiActivityStarted(1));
        assertTrue(sMockSatelliteServiceManager.restoreSatellitePointingUiClassName());

        SatelliteStateCallbackTest
                callback1 = new SatelliteStateCallbackTest();
        long registerResult = sSatelliteManager
                .registerForSatelliteModemStateChanged(getContext().getMainExecutor(), callback1);
        assertEquals(SatelliteManager.SATELLITE_ERROR_NONE, registerResult);
        assertTrue(callback1.waitUntilResult(1));
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_IDLE, callback1.modemState);
        sSatelliteManager.unregisterForSatelliteModemStateChanged(callback);

        assertTrue(sMockSatelliteServiceManager.setSatelliteListeningTimeoutDuration(
                TEST_SATELLITE_LISTENING_TIMEOUT_MILLIS));

        // Verify state transitions: IDLE -> TRANSFERRING -> LISTENING -> IDLE
        sendSatelliteDatagramWithSuccessfulResult(callback1, true);

        assertTrue(sMockSatelliteServiceManager.setSatelliteListeningTimeoutDuration(
                TEST_SATELLITE_LISTENING_FOREVER_TIMEOUT_MILLIS));

        // Move to LISTENING state
        sendSatelliteDatagramWithSuccessfulResult(callback1, false);

        // Verify state transitions: LISTENING -> TRANSFERRING -> LISTENING
        receiveSatelliteDatagramWithSuccessfulResult(callback1);

        // Verify state transitions: LISTENING -> TRANSFERRING -> IDLE
        sendSatelliteDatagramWithFailedResult(callback1);

        // Move to LISTENING state
        sendSatelliteDatagramWithSuccessfulResult(callback1, false);

        // Verify state transitions: LISTENING -> TRANSFERRING -> IDLE
        receiveSatelliteDatagramWithFailedResult(callback1);

        requestSatelliteEnabled(false);

        assertFalse(callback.waitUntilResult(1));
        assertTrue(callback1.waitUntilResult(1));
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_OFF, callback1.modemState);
        assertFalse(isSatelliteEnabled());
        assertTrue(
                sMockSatelliteServiceManager.waitForRemoteSatelliteGatewayServiceDisconnected(1));

        if (originalEnabledState) {
            // Restore original modem enabled state.
            requestSatelliteEnabled(true);

            assertTrue(callback1.waitUntilResult(1));
            assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_IDLE, callback1.modemState);
            assertTrue(isSatelliteEnabled());
        }
        sSatelliteManager.unregisterForSatelliteModemStateChanged(callback1);
        assertTrue(sMockSatelliteServiceManager.setSatelliteListeningTimeoutDuration(0));
        assertTrue(sMockSatelliteServiceManager.restoreSatelliteGatewayServicePackageName());

        revokeSatellitePermission();
    }

    @Test
    public void testSatelliteDatagramReceivedAck() {
        if (!shouldTestSatellite()) return;

        grantSatellitePermission();

        assertTrue(isSatelliteProvisioned());

        SatelliteDatagramCallbackTest satelliteDatagramCallback =
                new SatelliteDatagramCallbackTest();
        sSatelliteManager.registerForSatelliteDatagram(
                getContext().getMainExecutor(), satelliteDatagramCallback);

        String receivedText = "This is a test datagram message from satellite";
        android.telephony.satellite.stub.SatelliteDatagram receivedDatagram =
                new android.telephony.satellite.stub.SatelliteDatagram();
        receivedDatagram.data = receivedText.getBytes();
        sMockSatelliteServiceManager.sendOnSatelliteDatagramReceived(receivedDatagram, 0);

        assertTrue(satelliteDatagramCallback.waitUntilResult(1));
        assertArrayEquals(satelliteDatagramCallback.mDatagram.getSatelliteDatagram(),
                receivedText.getBytes());

        sSatelliteManager.unregisterForSatelliteDatagram(satelliteDatagramCallback);
        revokeSatellitePermission();
    }

    @Test
    public void  testRequestSatelliteCapabilities() {
        if (!shouldTestSatellite()) return;

        logd("testRequestSatelliteCapabilities");
        grantSatellitePermission();

        assertTrue(isSatelliteProvisioned());

        final AtomicReference<SatelliteCapabilities> capabilities = new AtomicReference<>();
        final AtomicReference<Integer> errorCode = new AtomicReference<>();
        OutcomeReceiver<SatelliteCapabilities, SatelliteManager.SatelliteException> receiver =
                new OutcomeReceiver<>() {
                    @Override
                    public void onResult(SatelliteCapabilities result) {
                        logd("testRequestSatelliteCapabilities: onResult");
                        capabilities.set(result);

                        assertNotNull(result);
                        assertNotNull(result.getSupportedRadioTechnologies());
                        assertThat(SUPPORTED_RADIO_TECHNOLOGIES)
                                .isEqualTo(result.getSupportedRadioTechnologies());
                        assertThat(POINTING_TO_SATELLITE_REQUIRED)
                                .isEqualTo(result.isPointingRequired());
                        assertThat(MAX_BYTES_PER_DATAGRAM)
                                .isEqualTo(result.getMaxBytesPerOutgoingDatagram());
                        assertNotNull(result.getAntennaPositionMap());
                        assertThat(ANTENNA_POSITION_MAP).isEqualTo(result.getAntennaPositionMap());
                    }

                    @Override
                    public void onError(SatelliteManager.SatelliteException exception) {
                        logd("testRequestSatelliteCapabilities: onError");
                        errorCode.set(exception.getErrorCode());
                    }
                };

        sSatelliteManager.requestSatelliteCapabilities(getContext().getMainExecutor(), receiver);

        revokeSatellitePermission();
    }

    @Test
    public void testSendSatelliteDatagram_success() {
        if (!shouldTestSatellite()) return;

        logd("testSendSatelliteDatagram_success");
        grantSatellitePermission();
        assertTrue(isSatelliteProvisioned());

        for (int i = 0; i < 5; i++) {
            logd("testSendSatelliteDatagram_success: moveToSendingState");
            assertTrue(isSatelliteEnabled());
            moveToSendingState();

            logd("testSendSatelliteDatagram_success: Disable satellite");
            SatelliteTransmissionUpdateCallbackTest transmissionUpdateCallback =
                    startTransmissionUpdates();
            requestSatelliteEnabled(false);
            assertFalse(isSatelliteEnabled());

            // Datagram transfer state should change from SENDING to FAILED and then IDLE.
            assertTrue(transmissionUpdateCallback.waitUntilOnSendDatagramStateChanged(2));
            assertThat(transmissionUpdateCallback.getNumOfSendDatagramStateChanges()).isEqualTo(2);
            assertThat(transmissionUpdateCallback.getSendDatagramStateChange(0)).isEqualTo(
                    new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                            SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_SEND_FAILED,
                            1, SatelliteManager.SATELLITE_REQUEST_ABORTED));
            assertThat(transmissionUpdateCallback.getSendDatagramStateChange(1)).isEqualTo(
                    new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                            SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE,
                            0, SatelliteManager.SATELLITE_ERROR_NONE));
            stopTransmissionUpdates(transmissionUpdateCallback);

            logd("testSendSatelliteDatagram_success: Enable satellite");
            requestSatelliteEnabled(true);
            assertTrue(isSatelliteEnabled());

            logd("testSendSatelliteDatagram_success: sendSatelliteDatagramSuccess");
            sendSatelliteDatagramSuccess();
        }
        revokeSatellitePermission();
    }

    @Test
    public void testSendSatelliteDatagram_failure() {
        if (!shouldTestSatellite()) return;

        logd("testSendSatelliteDatagram_failure");
        grantSatellitePermission();
        assertTrue(isSatelliteProvisioned());

        LinkedBlockingQueue<Integer> resultListener = new LinkedBlockingQueue<>(1);
        SatelliteTransmissionUpdateCallbackTest callback =
                new SatelliteTransmissionUpdateCallbackTest();
        sSatelliteManager.startSatelliteTransmissionUpdates(getContext().getMainExecutor(),
                resultListener::offer, callback);
        Integer errorCode;
        try {
            errorCode = resultListener.poll(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("testSendSatelliteDatagram_failure: Got InterruptedException in waiting"
                    + " for the startSatelliteTransmissionUpdates result code");
            return;
        }
        assertNotNull(errorCode);
        assertThat(errorCode).isEqualTo(SatelliteManager.SATELLITE_ERROR_NONE);

        // Send satellite datagram
        String mText = "This is a test datagram message from user";
        SatelliteDatagram datagram = new SatelliteDatagram(mText.getBytes());
        callback.clearSendDatagramStateChanges();
        sMockSatelliteServiceManager.setErrorCode(SatelliteError.SATELLITE_ERROR);
        sSatelliteManager.sendSatelliteDatagram(SatelliteManager.DATAGRAM_TYPE_SOS_MESSAGE,
                datagram, true, getContext().getMainExecutor(),
                resultListener::offer);

        /**
         * Send datagram transfer state should have the following transitions:
         * 1) SENDING to SENDING_FAILED
         * 2) SENDING_FAILED to IDLE
         */
        int expectedNumOfEvents = 3;
        assertTrue(callback.waitUntilOnSendDatagramStateChanged(expectedNumOfEvents));
        assertThat(callback.getNumOfSendDatagramStateChanges()).isEqualTo(expectedNumOfEvents);
        assertThat(callback.getSendDatagramStateChange(0)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_SENDING,
                        1, SatelliteManager.SATELLITE_ERROR_NONE));
        assertThat(callback.getSendDatagramStateChange(1)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_SEND_FAILED,
                        0, SatelliteManager.SATELLITE_ERROR));
        assertThat(callback.getSendDatagramStateChange(2)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE,
                        0, SatelliteManager.SATELLITE_ERROR_NONE));

        try {
            errorCode = resultListener.poll(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("testSendSatelliteDatagram_failure: Got InterruptedException in waiting"
                    + " for the sendSatelliteDatagram result code");
            return;
        }
        assertNotNull(errorCode);
        assertThat(errorCode).isEqualTo(SatelliteManager.SATELLITE_ERROR);

        callback.clearSendDatagramStateChanges();
        sSatelliteManager.stopSatelliteTransmissionUpdates(callback, getContext().getMainExecutor(),
                resultListener::offer);
        revokeSatellitePermission();
    }

    @Test
    public void testSendMultipleSatelliteDatagrams_success() {
        if (!shouldTestSatellite()) return;

        logd("testSendMultipleSatelliteDatagrams_success");
        grantSatellitePermission();
        assertTrue(isSatelliteProvisioned());

        LinkedBlockingQueue<Integer> resultListener = new LinkedBlockingQueue<>(1);
        SatelliteTransmissionUpdateCallbackTest callback =
                new SatelliteTransmissionUpdateCallbackTest();
        sSatelliteManager.startSatelliteTransmissionUpdates(getContext().getMainExecutor(),
                resultListener::offer, callback);
        Integer errorCode;
        try {
            errorCode = resultListener.poll(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("testSendMultipleSatelliteDatagrams_success: Got InterruptedException in waiting"
                    + " for the sendSatelliteDatagram result code");
            return;
        }
        assertNotNull(errorCode);
        assertThat(errorCode).isEqualTo(SatelliteManager.SATELLITE_ERROR_NONE);

        String mText = "This is a test datagram message from user";
        SatelliteDatagram datagram = new SatelliteDatagram(mText.getBytes());
        callback.clearSendDatagramStateChanges();
        sMockSatelliteServiceManager.clearSentSatelliteDatagramInfo();

        // Wait to process datagrams so that datagrams are added to pending list.
        sMockSatelliteServiceManager.setWaitToSend(true);

        // Send three datagrams to observe how pendingCount is updated
        // after processing one datagram at a time.
        LinkedBlockingQueue<Integer> resultListener1 = new LinkedBlockingQueue<>(1);
        sSatelliteManager.sendSatelliteDatagram(SatelliteManager.DATAGRAM_TYPE_SOS_MESSAGE,
                datagram, true, getContext().getMainExecutor(),
                resultListener1::offer);
        LinkedBlockingQueue<Integer> resultListener2 = new LinkedBlockingQueue<>(1);
        sSatelliteManager.sendSatelliteDatagram(SatelliteManager.DATAGRAM_TYPE_SOS_MESSAGE,
                datagram, true, getContext().getMainExecutor(),
                resultListener2::offer);
        LinkedBlockingQueue<Integer> resultListener3 = new LinkedBlockingQueue<>(1);
        sSatelliteManager.sendSatelliteDatagram(SatelliteManager.DATAGRAM_TYPE_SOS_MESSAGE,
                datagram, true, getContext().getMainExecutor(),
                resultListener3::offer);

        assertTrue(sMockSatelliteServiceManager.waitForEventOnSendSatelliteDatagram(1));

        // Send first datagram: SENDING to SENDING_SUCCESS
        assertTrue(sMockSatelliteServiceManager.sendSavedDatagram());
        int expectedNumOfEvents = 2;
        assertTrue(callback.waitUntilOnSendDatagramStateChanged(expectedNumOfEvents));
        assertThat(callback.getSendDatagramStateChange(0)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_SENDING,
                        1, SatelliteManager.SATELLITE_ERROR_NONE));
        // Pending count is 2 as there are 2 datagrams to be sent.
        assertThat(callback.getSendDatagramStateChange(1)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_SEND_SUCCESS,
                        2, SatelliteManager.SATELLITE_ERROR_NONE));
        try {
            errorCode = resultListener1.poll(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("testSendMultipleSatelliteDatagrams_success: Got InterruptedException in waiting"
                    + " for the sendSatelliteDatagram result code");
            return;
        }
        assertNotNull(errorCode);
        assertThat(errorCode).isEqualTo(SatelliteManager.SATELLITE_ERROR_NONE);

        assertTrue(sMockSatelliteServiceManager.waitForEventOnSendSatelliteDatagram(1));

        // Send second datagram: SENDING to SENDING_SUCCESS
        // callback.clearSendDatagramStateChanges();
        assertTrue(sMockSatelliteServiceManager.sendSavedDatagram());
        expectedNumOfEvents = 2;
        assertTrue(callback.waitUntilOnSendDatagramStateChanged(expectedNumOfEvents));
        assertThat(callback.getSendDatagramStateChange(2)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_SENDING,
                        2, SatelliteManager.SATELLITE_ERROR_NONE));
        assertThat(callback.getSendDatagramStateChange(3)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_SEND_SUCCESS,
                        1, SatelliteManager.SATELLITE_ERROR_NONE));
        try {
            errorCode = resultListener2.poll(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("testSendMultipleSatelliteDatagrams_success: Got InterruptedException in waiting"
                    + " for the sendSatelliteDatagram result code");
            return;
        }
        assertNotNull(errorCode);
        assertThat(errorCode).isEqualTo(SatelliteManager.SATELLITE_ERROR_NONE);

        assertTrue(sMockSatelliteServiceManager.waitForEventOnSendSatelliteDatagram(1));

        // Send third datagram: SENDING - SENDING_SUCCESS - IDLE
        assertTrue(sMockSatelliteServiceManager.sendSavedDatagram());
        expectedNumOfEvents = 3;
        assertTrue(callback.waitUntilOnSendDatagramStateChanged(expectedNumOfEvents));
        assertThat(callback.getSendDatagramStateChange(4)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_SENDING,
                        1, SatelliteManager.SATELLITE_ERROR_NONE));
        assertThat(callback.getSendDatagramStateChange(5)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_SEND_SUCCESS,
                        0, SatelliteManager.SATELLITE_ERROR_NONE));
        assertThat(callback.getSendDatagramStateChange(6)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE,
                        0, SatelliteManager.SATELLITE_ERROR_NONE));
        try {
            errorCode = resultListener3.poll(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("testSendMultipleSatelliteDatagrams_success: Got InterruptedException in waiting"
                    + " for the sendSatelliteDatagram result code");
            return;
        }
        assertNotNull(errorCode);
        assertThat(errorCode).isEqualTo(SatelliteManager.SATELLITE_ERROR_NONE);

        callback.clearSendDatagramStateChanges();
        sMockSatelliteServiceManager.clearSentSatelliteDatagramInfo();
        sMockSatelliteServiceManager.setWaitToSend(false);
        sSatelliteManager.stopSatelliteTransmissionUpdates(callback, getContext().getMainExecutor(),
                resultListener::offer);
        revokeSatellitePermission();
    }

    @Test
    public void testSendMultipleSatelliteDatagrams_failure() {
        if (!shouldTestSatellite()) return;

        logd("testSendMultipleSatelliteDatagrams_failure");
        grantSatellitePermission();
        assertTrue(isSatelliteProvisioned());

        LinkedBlockingQueue<Integer> resultListener = new LinkedBlockingQueue<>(1);
        SatelliteTransmissionUpdateCallbackTest callback =
                new SatelliteTransmissionUpdateCallbackTest();
        sSatelliteManager.startSatelliteTransmissionUpdates(getContext().getMainExecutor(),
                resultListener::offer, callback);
        Integer errorCode;
        try {
            errorCode = resultListener.poll(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("testSendMultipleSatelliteDatagrams_failure: Got InterruptedException in waiting"
                    + " for the sendSatelliteDatagram result code");
            return;
        }
        assertNotNull(errorCode);
        assertThat(errorCode).isEqualTo(SatelliteManager.SATELLITE_ERROR_NONE);

        String mText = "This is a test datagram message from user";
        SatelliteDatagram datagram = new SatelliteDatagram(mText.getBytes());
        callback.clearSendDatagramStateChanges();
        sMockSatelliteServiceManager.clearSentSatelliteDatagramInfo();

        // Wait to process datagrams so that datagrams are added to pending list.
        sMockSatelliteServiceManager.setWaitToSend(true);

        // Send three datagrams to observe how pendingCount is updated
        // after processing one datagram at a time.
        LinkedBlockingQueue<Integer> resultListener1 = new LinkedBlockingQueue<>(1);
        sSatelliteManager.sendSatelliteDatagram(SatelliteManager.DATAGRAM_TYPE_SOS_MESSAGE,
                datagram, true, getContext().getMainExecutor(),
                resultListener1::offer);
        LinkedBlockingQueue<Integer> resultListener2 = new LinkedBlockingQueue<>(1);
        sSatelliteManager.sendSatelliteDatagram(SatelliteManager.DATAGRAM_TYPE_SOS_MESSAGE,
                datagram, true, getContext().getMainExecutor(),
                resultListener2::offer);
        LinkedBlockingQueue<Integer> resultListener3 = new LinkedBlockingQueue<>(1);
        sSatelliteManager.sendSatelliteDatagram(SatelliteManager.DATAGRAM_TYPE_SOS_MESSAGE,
                datagram, true, getContext().getMainExecutor(),
                resultListener3::offer);

        assertTrue(sMockSatelliteServiceManager.waitForEventOnSendSatelliteDatagram(1));

        // Set error and send first datagram: SENDING to SENDING_FAILED
        sMockSatelliteServiceManager.setErrorCode(SatelliteError.SATELLITE_ERROR);
        assertTrue(sMockSatelliteServiceManager.sendSavedDatagram());
        int expectedNumOfEvents = 3;
        assertTrue(callback.waitUntilOnSendDatagramStateChanged(expectedNumOfEvents));
        assertThat(callback.getSendDatagramStateChange(0)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_SENDING,
                        1, SatelliteManager.SATELLITE_ERROR_NONE));
        // Pending count is 2 as there are 2 datagrams to be sent.
        assertThat(callback.getSendDatagramStateChange(1)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_SEND_FAILED,
                        2, SatelliteManager.SATELLITE_ERROR));
        assertThat(callback.getSendDatagramStateChange(2)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE,
                        0, SatelliteManager.SATELLITE_ERROR_NONE));
        try {
            errorCode = resultListener1.poll(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("testSendMultipleSatelliteDatagrams_success: Got InterruptedException in waiting"
                    + " for the sendSatelliteDatagram result code");
            return;
        }
        assertNotNull(errorCode);
        assertThat(errorCode).isEqualTo(SatelliteManager.SATELLITE_ERROR);

        try {
            errorCode = resultListener2.poll(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("testSendMultipleSatelliteDatagrams_success: Got InterruptedException in waiting"
                    + " for the sendSatelliteDatagram result code");
            return;
        }
        assertNotNull(errorCode);
        assertThat(errorCode).isEqualTo(SatelliteManager.SATELLITE_REQUEST_ABORTED);

        try {
            errorCode = resultListener3.poll(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("testSendMultipleSatelliteDatagrams_success: Got InterruptedException in waiting"
                    + " for the sendSatelliteDatagram result code");
            return;
        }
        assertNotNull(errorCode);
        assertThat(errorCode).isEqualTo(SatelliteManager.SATELLITE_REQUEST_ABORTED);

        callback.clearSendDatagramStateChanges();
        sMockSatelliteServiceManager.clearSentSatelliteDatagramInfo();
        sMockSatelliteServiceManager.setWaitToSend(false);
        sSatelliteManager.stopSatelliteTransmissionUpdates(callback, getContext().getMainExecutor(),
                resultListener::offer);
        revokeSatellitePermission();
    }


    @Test
    public void testReceiveSatelliteDatagram() {
        if (!shouldTestSatellite()) return;

        logd("testReceiveSatelliteDatagram");
        grantSatellitePermission();
        assertTrue(isSatelliteProvisioned());

        for (int i = 0; i < 5; i++) {
            logd("testReceiveSatelliteDatagram: moveToReceivingState");
            assertTrue(isSatelliteEnabled());
            moveToReceivingState();

            logd("testReceiveSatelliteDatagram: Disable satellite");
            SatelliteTransmissionUpdateCallbackTest transmissionUpdateCallback =
                    startTransmissionUpdates();
            requestSatelliteEnabled(false);
            assertFalse(isSatelliteEnabled());

            // Datagram transfer state should change from RECEIVING to IDLE.
            assertTrue(transmissionUpdateCallback
                    .waitUntilOnReceiveDatagramStateChanged(2));
            assertThat(transmissionUpdateCallback.getNumOfReceiveDatagramStateChanges())
                    .isEqualTo(2);
            assertThat(transmissionUpdateCallback.getReceiveDatagramStateChange(0)).isEqualTo(
                    new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                            SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_RECEIVE_FAILED,
                            0, SatelliteManager.SATELLITE_REQUEST_ABORTED));
            assertThat(transmissionUpdateCallback.getReceiveDatagramStateChange(1)).isEqualTo(
                    new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                            SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE,
                            0, SatelliteManager.SATELLITE_ERROR_NONE));
            stopTransmissionUpdates(transmissionUpdateCallback);

            logd("testReceiveSatelliteDatagram: Enable satellite");
            requestSatelliteEnabled(true);
            assertTrue(isSatelliteEnabled());

            logd("testReceiveSatelliteDatagram: receiveSatelliteDatagramSuccess");
            receiveSatelliteDatagramSuccess();
        }

        revokeSatellitePermission();
    }

    @Test
    public void testReceiveMultipleSatelliteDatagrams() {
        if (!shouldTestSatellite()) return;

        logd("testReceiveMultipleSatelliteDatagrams");
        grantSatellitePermission();
        assertTrue(isSatelliteProvisioned());

        LinkedBlockingQueue<Integer> resultListener = new LinkedBlockingQueue<>(1);
        SatelliteTransmissionUpdateCallbackTest transmissionUpdateCallback =
                new SatelliteTransmissionUpdateCallbackTest();
        sSatelliteManager.startSatelliteTransmissionUpdates(getContext().getMainExecutor(),
                resultListener::offer, transmissionUpdateCallback);
        Integer errorCode;
        try {
            errorCode = resultListener.poll(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("testReceiveMultipleSatelliteDatagrams: Got InterruptedException in waiting"
                    + " for the startSatelliteTransmissionUpdates result code");
            return;
        }
        assertNotNull(errorCode);
        assertThat(errorCode).isEqualTo(SatelliteManager.SATELLITE_ERROR_NONE);

        SatelliteDatagramCallbackTest satelliteDatagramCallback =
                new SatelliteDatagramCallbackTest();
        sSatelliteManager.registerForSatelliteDatagram(
                getContext().getMainExecutor(), satelliteDatagramCallback);

        transmissionUpdateCallback.clearReceiveDatagramStateChanges();
        sMockSatelliteServiceManager.sendOnPendingDatagrams();
        assertTrue(sMockSatelliteServiceManager.waitForEventOnPollPendingSatelliteDatagrams(1));

        // Receive first datagram: Datagram state changes from RECEIVING to RECEIVE_SUCCESS
        String receivedText = "This is a test datagram message from satellite";
        android.telephony.satellite.stub.SatelliteDatagram receivedDatagram =
                new android.telephony.satellite.stub.SatelliteDatagram();
        receivedDatagram.data = receivedText.getBytes();
        sMockSatelliteServiceManager.sendOnSatelliteDatagramReceived(receivedDatagram, 2);
        assertTrue(satelliteDatagramCallback.waitUntilResult(1));
        assertArrayEquals(satelliteDatagramCallback.mDatagram.getSatelliteDatagram(),
                receivedText.getBytes());
        int expectedNumOfEvents = 2;
        assertTrue(transmissionUpdateCallback
                .waitUntilOnReceiveDatagramStateChanged(expectedNumOfEvents));
        assertThat(transmissionUpdateCallback.getReceiveDatagramStateChange(0)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_RECEIVING,
                        0, SatelliteManager.SATELLITE_ERROR_NONE));
        assertThat(transmissionUpdateCallback.getReceiveDatagramStateChange(1)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_RECEIVE_SUCCESS,
                        2, SatelliteManager.SATELLITE_ERROR_NONE));


        // Receive second datagram: Datagram state changes from RECEIVING to RECEIVE_SUCCESS
        assertTrue(sMockSatelliteServiceManager.waitForEventOnPollPendingSatelliteDatagrams(1));
        sMockSatelliteServiceManager.sendOnSatelliteDatagramReceived(receivedDatagram, 1);
        assertTrue(satelliteDatagramCallback.waitUntilResult(1));
        assertArrayEquals(satelliteDatagramCallback.mDatagram.getSatelliteDatagram(),
                receivedText.getBytes());
        expectedNumOfEvents = 2;
        assertTrue(transmissionUpdateCallback
                .waitUntilOnReceiveDatagramStateChanged(2));
        assertThat(transmissionUpdateCallback.getReceiveDatagramStateChange(2)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_RECEIVING,
                        2, SatelliteManager.SATELLITE_ERROR_NONE));
        assertThat(transmissionUpdateCallback.getReceiveDatagramStateChange(3)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_RECEIVE_SUCCESS,
                        1, SatelliteManager.SATELLITE_ERROR_NONE));

        // Receive third datagram: Datagram state changes from RECEIVING - RECEIVE_SUCCESS - IDLE
        assertTrue(sMockSatelliteServiceManager.waitForEventOnPollPendingSatelliteDatagrams(1));
        sMockSatelliteServiceManager.sendOnSatelliteDatagramReceived(receivedDatagram, 0);
        assertTrue(satelliteDatagramCallback.waitUntilResult(1));
        assertArrayEquals(satelliteDatagramCallback.mDatagram.getSatelliteDatagram(),
                receivedText.getBytes());
        expectedNumOfEvents = 3;
        assertTrue(transmissionUpdateCallback
                .waitUntilOnReceiveDatagramStateChanged(3));
        assertThat(transmissionUpdateCallback.getReceiveDatagramStateChange(4)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_RECEIVING,
                        1, SatelliteManager.SATELLITE_ERROR_NONE));
        assertThat(transmissionUpdateCallback.getReceiveDatagramStateChange(5)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_RECEIVE_SUCCESS,
                        0, SatelliteManager.SATELLITE_ERROR_NONE));
        assertThat(transmissionUpdateCallback.getReceiveDatagramStateChange(6)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE,
                        0, SatelliteManager.SATELLITE_ERROR_NONE));

        transmissionUpdateCallback.clearReceiveDatagramStateChanges();
        sSatelliteManager.stopSatelliteTransmissionUpdates(transmissionUpdateCallback,
                getContext().getMainExecutor(), resultListener::offer);
        sSatelliteManager.unregisterForSatelliteDatagram(satelliteDatagramCallback);
        revokeSatellitePermission();
    }

    @Test
    public void testReceiveSatellitePositionUpdate() {
        if (!shouldTestSatellite()) return;

        logd("testReceiveSatellitePositionUpdate");
        grantSatellitePermission();
        assertTrue(isSatelliteProvisioned());

        LinkedBlockingQueue<Integer> resultListener = new LinkedBlockingQueue<>(1);
        SatelliteTransmissionUpdateCallbackTest transmissionUpdateCallback =
                new SatelliteTransmissionUpdateCallbackTest();
        sSatelliteManager.startSatelliteTransmissionUpdates(getContext().getMainExecutor(),
                resultListener::offer, transmissionUpdateCallback);
        Integer errorCode;
        try {
            errorCode = resultListener.poll(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("testReceiveSatellitePositionUpdate: Got InterruptedException in waiting"
                    + " for the startSatelliteTransmissionUpdates result code");
            return;
        }
        assertNotNull(errorCode);
        assertThat(errorCode).isEqualTo(SatelliteManager.SATELLITE_ERROR_NONE);

        android.telephony.satellite.stub.PointingInfo pointingInfo =
                new android.telephony.satellite.stub.PointingInfo();
        pointingInfo.satelliteAzimuth = 10.5f;
        pointingInfo.satelliteElevation = 30.23f;
        PointingInfo expectedPointingInfo = new PointingInfo(10.5f, 30.23f);
        sMockSatelliteServiceManager.sendOnSatellitePositionChanged(pointingInfo);
        assertTrue(transmissionUpdateCallback.waitUntilOnSatellitePositionChanged(1));
        assertThat(transmissionUpdateCallback.mPointingInfo.getSatelliteAzimuthDegrees())
                .isEqualTo(expectedPointingInfo.getSatelliteAzimuthDegrees());
        assertThat(transmissionUpdateCallback.mPointingInfo.getSatelliteElevationDegrees())
                .isEqualTo(expectedPointingInfo.getSatelliteElevationDegrees());

        sSatelliteManager.stopSatelliteTransmissionUpdates(transmissionUpdateCallback,
                getContext().getMainExecutor(), resultListener::offer);
        revokeSatellitePermission();
    }

    @Test
    public void testReceiveMultipleSatellitePositionUpdates() {
        if (!shouldTestSatellite()) return;

        logd("testReceiveMultipleSatellitePositionUpdates");
        grantSatellitePermission();
        assertTrue(isSatelliteProvisioned());

        LinkedBlockingQueue<Integer> resultListener = new LinkedBlockingQueue<>(1);
        SatelliteTransmissionUpdateCallbackTest transmissionUpdateCallback =
                new SatelliteTransmissionUpdateCallbackTest();
        sSatelliteManager.startSatelliteTransmissionUpdates(getContext().getMainExecutor(),
                resultListener::offer, transmissionUpdateCallback);
        Integer errorCode;
        try {
            errorCode = resultListener.poll(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("testReceiveMultipleSatellitePositionUpdates: Got InterruptedException in waiting"
                    + " for the startSatelliteTransmissionUpdates result code");
            return;
        }
        assertNotNull(errorCode);
        assertThat(errorCode).isEqualTo(SatelliteManager.SATELLITE_ERROR_NONE);

        // Receive first position update
        android.telephony.satellite.stub.PointingInfo pointingInfo =
                new android.telephony.satellite.stub.PointingInfo();
        pointingInfo.satelliteAzimuth = 10.5f;
        pointingInfo.satelliteElevation = 30.23f;
        PointingInfo expectedPointingInfo = new PointingInfo(10.5f, 30.23f);
        sMockSatelliteServiceManager.sendOnSatellitePositionChanged(pointingInfo);
        assertTrue(transmissionUpdateCallback.waitUntilOnSatellitePositionChanged(1));
        assertThat(transmissionUpdateCallback.mPointingInfo.getSatelliteAzimuthDegrees())
                .isEqualTo(expectedPointingInfo.getSatelliteAzimuthDegrees());
        assertThat(transmissionUpdateCallback.mPointingInfo.getSatelliteElevationDegrees())
                .isEqualTo(expectedPointingInfo.getSatelliteElevationDegrees());

        // Receive second position update
        pointingInfo.satelliteAzimuth = 100;
        pointingInfo.satelliteElevation = 120;
        expectedPointingInfo = new PointingInfo(100, 120);
        sMockSatelliteServiceManager.sendOnSatellitePositionChanged(pointingInfo);
        assertTrue(transmissionUpdateCallback.waitUntilOnSatellitePositionChanged(1));
        assertThat(transmissionUpdateCallback.mPointingInfo.getSatelliteAzimuthDegrees())
                .isEqualTo(expectedPointingInfo.getSatelliteAzimuthDegrees());
        assertThat(transmissionUpdateCallback.mPointingInfo.getSatelliteElevationDegrees())
                .isEqualTo(expectedPointingInfo.getSatelliteElevationDegrees());

        sSatelliteManager.stopSatelliteTransmissionUpdates(transmissionUpdateCallback,
                getContext().getMainExecutor(), resultListener::offer);
        revokeSatellitePermission();
    }

    /**
     * Before calling this function, caller need to make sure the modem is in LISTENING or IDLE
     * state.
     */
    private void sendSatelliteDatagramWithSuccessfulResult(
            SatelliteStateCallbackTest callback, boolean verifyListenToIdleTransition) {
        if (callback.modemState != SatelliteManager.SATELLITE_MODEM_STATE_LISTENING
                && callback.modemState != SatelliteManager.SATELLITE_MODEM_STATE_IDLE) {
            fail("sendSatelliteDatagramWithSuccessfulResult: wrong modem state="
                    + callback.modemState);
            return;
        }

        LinkedBlockingQueue<Integer> resultListener = new LinkedBlockingQueue<>(1);
        String mText = "This is a test datagram message from user";
        SatelliteDatagram datagram = new SatelliteDatagram(mText.getBytes());

        sMockSatelliteServiceManager.clearListeningEnabledList();
        callback.clearModemStates();
        sSatelliteManager.sendSatelliteDatagram(
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
         * When verifyListenToIdleTransition is true, we expect the above 3 state transitions.
         * Otherwise, we expect only the first 2 transitions since satellite is still in LISTENING
         * state (timeout duration is long when verifyListenToIdleTransition is false).
         */
        int expectedNumberOfEvents = verifyListenToIdleTransition ? 3 : 2;
        assertTrue(callback.waitUntilResult(expectedNumberOfEvents));
        assertEquals(expectedNumberOfEvents, callback.getTotalCountOfModemStates());
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_DATAGRAM_TRANSFERRING,
                callback.getModemState(0));
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_LISTENING,
                callback.getModemState(1));

        /**
         * On entering LISTENING state, we expect one event of EventOnSatelliteListeningEnabled with
         * value true. On exiting LISTENING state, we expect one event of
         * EventOnSatelliteListeningEnabled with value false.
         *
         * When verifyListenToIdleTransition is true, we expect satellite entering and then exiting
         * LISTENING state. Otherwise, we expect satellite entering and staying at LISTENING state.
         */
        expectedNumberOfEvents = verifyListenToIdleTransition ? 2 : 1;
        assertTrue(sMockSatelliteServiceManager.waitForEventOnSatelliteListeningEnabled(
                expectedNumberOfEvents));
        assertEquals(expectedNumberOfEvents,
                sMockSatelliteServiceManager.getTotalCountOfListeningEnabledList());
        assertTrue(sMockSatelliteServiceManager.getListeningEnabled(0));

        if (verifyListenToIdleTransition) {
            assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_IDLE,
                    callback.getModemState(2));
            assertFalse(sMockSatelliteServiceManager.getListeningEnabled(1));
        }
        sMockSatelliteServiceManager.clearListeningEnabledList();
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
        boolean isFirstStateListening =
                (callback.modemState == SatelliteManager.SATELLITE_MODEM_STATE_LISTENING);

        LinkedBlockingQueue<Integer> resultListener = new LinkedBlockingQueue<>(1);
        String mText = "This is a test datagram message from user";
        SatelliteDatagram datagram = new SatelliteDatagram(mText.getBytes());

        sMockSatelliteServiceManager.setErrorCode(SatelliteError.SATELLITE_ERROR);
        sMockSatelliteServiceManager.clearListeningEnabledList();
        callback.clearModemStates();
        sSatelliteManager.sendSatelliteDatagram(
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

        if (isFirstStateListening) {
            assertTrue(sMockSatelliteServiceManager.waitForEventOnSatelliteListeningEnabled(1));
            assertEquals(1, sMockSatelliteServiceManager.getTotalCountOfListeningEnabledList());
            assertFalse(sMockSatelliteServiceManager.getListeningEnabled(0));
        }
        sMockSatelliteServiceManager.clearListeningEnabledList();
        sMockSatelliteServiceManager.setErrorCode(SatelliteError.ERROR_NONE);
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
        sSatelliteManager.registerForSatelliteDatagram(
                getContext().getMainExecutor(), satelliteDatagramCallback);

        sMockSatelliteServiceManager.clearListeningEnabledList();
        callback.clearModemStates();
        sMockSatelliteServiceManager.sendOnPendingDatagrams();
        assertTrue(sMockSatelliteServiceManager.waitForEventOnPollPendingSatelliteDatagrams(1));

        assertTrue(callback.waitUntilResult(1));
        assertEquals(1, callback.getTotalCountOfModemStates());
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_DATAGRAM_TRANSFERRING,
                callback.getModemState(0));

        assertTrue(sMockSatelliteServiceManager.waitForEventOnSatelliteListeningEnabled(1));
        assertEquals(1, sMockSatelliteServiceManager.getTotalCountOfListeningEnabledList());
        assertFalse(sMockSatelliteServiceManager.getListeningEnabled(0));

        String receivedText = "This is a test datagram message from satellite";
        android.telephony.satellite.stub.SatelliteDatagram receivedDatagram =
                new android.telephony.satellite.stub.SatelliteDatagram();
        receivedDatagram.data = receivedText.getBytes();

        sMockSatelliteServiceManager.clearListeningEnabledList();
        callback.clearModemStates();
        sMockSatelliteServiceManager.sendOnSatelliteDatagramReceived(receivedDatagram, 0);
        assertTrue(callback.waitUntilResult(1));
        assertEquals(1, callback.getTotalCountOfModemStates());
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_LISTENING,
                callback.getModemState(0));

        assertTrue(sMockSatelliteServiceManager.waitForEventOnSatelliteListeningEnabled(1));
        assertEquals(1, sMockSatelliteServiceManager.getTotalCountOfListeningEnabledList());
        assertTrue(sMockSatelliteServiceManager.getListeningEnabled(0));
        sMockSatelliteServiceManager.clearListeningEnabledList();

        sSatelliteManager.unregisterForSatelliteDatagram(satelliteDatagramCallback);
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
        sSatelliteManager.registerForSatelliteDatagram(
                getContext().getMainExecutor(), satelliteDatagramCallback);

        sMockSatelliteServiceManager.clearListeningEnabledList();
        callback.clearModemStates();
        sMockSatelliteServiceManager.setErrorCode(SatelliteError.SATELLITE_ERROR);
        sMockSatelliteServiceManager.sendOnPendingDatagrams();
        assertTrue(sMockSatelliteServiceManager.waitForEventOnPollPendingSatelliteDatagrams(1));

        assertTrue(callback.waitUntilResult(2));
        assertEquals(2, callback.getTotalCountOfModemStates());
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_DATAGRAM_TRANSFERRING,
                callback.getModemState(0));
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_IDLE,
                callback.getModemState(1));

        /**
         * On entering LISTENING state, we expect one event of EventOnSatelliteListeningEnabled with
         * value true. On exiting LISTENING state, we expect one event of
         * EventOnSatelliteListeningEnabled with value false.
         *
         * At the beginning of this function, satellite is in LISTENING state. It then transitions
         * to TRANSFERRING state. Thus, we expect one event of EventOnSatelliteListeningEnabled with
         * value false.
         */
        assertTrue(sMockSatelliteServiceManager.waitForEventOnSatelliteListeningEnabled(1));
        assertEquals(1, sMockSatelliteServiceManager.getTotalCountOfListeningEnabledList());
        assertFalse(sMockSatelliteServiceManager.getListeningEnabled(0));

        sMockSatelliteServiceManager.clearListeningEnabledList();
        sSatelliteManager.unregisterForSatelliteDatagram(satelliteDatagramCallback);
        sMockSatelliteServiceManager.setErrorCode(SatelliteError.ERROR_NONE);
    }

    private void moveToSendingState() {
        LinkedBlockingQueue<Integer> resultListener = new LinkedBlockingQueue<>(1);
        SatelliteTransmissionUpdateCallbackTest callback =
                new SatelliteTransmissionUpdateCallbackTest();
        sSatelliteManager.startSatelliteTransmissionUpdates(getContext().getMainExecutor(),
                resultListener::offer, callback);
        Integer errorCode;
        try {
            errorCode = resultListener.poll(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("moveToSendingState: Got InterruptedException in waiting"
                    + " for the startSatelliteTransmissionUpdates result code");
            return;
        }
        assertNotNull(errorCode);
        assertThat(errorCode).isEqualTo(SatelliteManager.SATELLITE_ERROR_NONE);

        // Send satellite datagram
        String mText = "This is a test datagram message from user";
        SatelliteDatagram datagram = new SatelliteDatagram(mText.getBytes());
        callback.clearSendDatagramStateChanges();
        sMockSatelliteServiceManager.setShouldRespondTelephony(false);
        sMockSatelliteServiceManager.clearSentSatelliteDatagramInfo();
        sSatelliteManager.sendSatelliteDatagram(SatelliteManager.DATAGRAM_TYPE_SOS_MESSAGE,
                datagram, true, getContext().getMainExecutor(),
                resultListener::offer);

        assertTrue(sMockSatelliteServiceManager.waitForEventOnSendSatelliteDatagram(1));

        // Send datagram transfer state should move from IDLE to SENDING.
        assertSingleSendDatagramStateChanged(callback,
                SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_SENDING,
                1, SatelliteManager.SATELLITE_ERROR_NONE);

        sMockSatelliteServiceManager.setShouldRespondTelephony(true);
        sMockSatelliteServiceManager.clearSentSatelliteDatagramInfo();
        callback.clearSendDatagramStateChanges();
        sSatelliteManager.stopSatelliteTransmissionUpdates(callback, getContext().getMainExecutor(),
                resultListener::offer);
    }

    private void sendSatelliteDatagramSuccess() {
        SatelliteTransmissionUpdateCallbackTest callback = startTransmissionUpdates();

        // Send satellite datagram
        String mText = "This is a test datagram message from user";
        SatelliteDatagram datagram = new SatelliteDatagram(mText.getBytes());
        LinkedBlockingQueue<Integer> resultListener = new LinkedBlockingQueue<>(1);
        callback.clearSendDatagramStateChanges();
        assertTrue(sMockSatelliteServiceManager.overrideSatellitePointingUiClassName());
        sMockSatelliteServiceManager.clearMockPointingUiActivityStatusChanges();
        sMockSatelliteServiceManager.clearSentSatelliteDatagramInfo();
        sSatelliteManager.sendSatelliteDatagram(SatelliteManager.DATAGRAM_TYPE_SOS_MESSAGE,
                datagram, true, getContext().getMainExecutor(),
                resultListener::offer);

        Integer errorCode;
        try {
            errorCode = resultListener.poll(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("testSendSatelliteDatagram_success: Got InterruptedException in waiting"
                    + " for the sendSatelliteDatagram result code");
            return;
        }
        assertNotNull(errorCode);
        assertThat(errorCode).isEqualTo(SatelliteManager.SATELLITE_ERROR_NONE);
        assertTrue(sMockSatelliteServiceManager.waitForEventOnSendSatelliteDatagram(1));

        /**
         * Send datagram transfer state should have the following transitions:
         * 1) IDLE to SENDING
         * 2) SENDING to SENDING_SUCCESS
         * 3) SENDING_SUCCESS to IDLE
         */
        int expectedNumOfEvents = 3;
        assertTrue(callback.waitUntilOnSendDatagramStateChanged(expectedNumOfEvents));
        assertThat(callback.getNumOfSendDatagramStateChanges()).isEqualTo(expectedNumOfEvents);
        assertThat(callback.getSendDatagramStateChange(0)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_SENDING,
                        1, SatelliteManager.SATELLITE_ERROR_NONE));
        assertThat(callback.getSendDatagramStateChange(1)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_SEND_SUCCESS,
                        0, SatelliteManager.SATELLITE_ERROR_NONE));
        assertThat(callback.getSendDatagramStateChange(2)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE,
                        0, SatelliteManager.SATELLITE_ERROR_NONE));
        assertTrue(sMockSatelliteServiceManager.waitForEventMockPointingUiActivityStarted(1));
        assertTrue(sMockSatelliteServiceManager.restoreSatellitePointingUiClassName());

        callback.clearSendDatagramStateChanges();
        sSatelliteManager.stopSatelliteTransmissionUpdates(callback, getContext().getMainExecutor(),
                resultListener::offer);
    }

    private void moveToReceivingState() {
        LinkedBlockingQueue<Integer> resultListener = new LinkedBlockingQueue<>(1);
        SatelliteTransmissionUpdateCallbackTest transmissionUpdateCallback =
                new SatelliteTransmissionUpdateCallbackTest();
        sSatelliteManager.startSatelliteTransmissionUpdates(getContext().getMainExecutor(),
                resultListener::offer, transmissionUpdateCallback);
        Integer errorCode;
        try {
            errorCode = resultListener.poll(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("testReceiveSatelliteDatagram: Got InterruptedException in waiting"
                    + " for the startSatelliteTransmissionUpdates result code");
            return;
        }
        assertNotNull(errorCode);
        assertThat(errorCode).isEqualTo(SatelliteManager.SATELLITE_ERROR_NONE);

        transmissionUpdateCallback.clearReceiveDatagramStateChanges();
        sMockSatelliteServiceManager.sendOnPendingDatagrams();
        assertTrue(sMockSatelliteServiceManager.waitForEventOnPollPendingSatelliteDatagrams(1));

        // Datagram transfer state changes from IDLE to RECEIVING.
        assertSingleReceiveDatagramStateChanged(transmissionUpdateCallback,
                SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_RECEIVING,
                0, SatelliteManager.SATELLITE_ERROR_NONE);

        transmissionUpdateCallback.clearReceiveDatagramStateChanges();
        stopTransmissionUpdates(transmissionUpdateCallback);
    }

    private void receiveSatelliteDatagramSuccess() {
        SatelliteTransmissionUpdateCallbackTest transmissionUpdateCallback =
                startTransmissionUpdates();

        SatelliteDatagramCallbackTest satelliteDatagramCallback =
                new SatelliteDatagramCallbackTest();
        sSatelliteManager.registerForSatelliteDatagram(
                getContext().getMainExecutor(), satelliteDatagramCallback);

        transmissionUpdateCallback.clearReceiveDatagramStateChanges();
        sMockSatelliteServiceManager.sendOnPendingDatagrams();
        assertTrue(sMockSatelliteServiceManager.waitForEventOnPollPendingSatelliteDatagrams(1));

        // Receive one datagram
        String receivedText = "This is a test datagram message from satellite";
        android.telephony.satellite.stub.SatelliteDatagram receivedDatagram =
                new android.telephony.satellite.stub.SatelliteDatagram();
        receivedDatagram.data = receivedText.getBytes();
        sMockSatelliteServiceManager.sendOnSatelliteDatagramReceived(receivedDatagram, 0);
        assertTrue(satelliteDatagramCallback.waitUntilResult(1));
        assertArrayEquals(satelliteDatagramCallback.mDatagram.getSatelliteDatagram(),
                receivedText.getBytes());

        // As pending count is 0, datagram transfer state changes from
        // IDLE -> RECEIVING -> RECEIVE_SUCCESS -> IDLE.
        int expectedNumOfEvents = 3;
        assertTrue(transmissionUpdateCallback
                .waitUntilOnReceiveDatagramStateChanged(expectedNumOfEvents));
        assertThat(transmissionUpdateCallback.getNumOfReceiveDatagramStateChanges())
                .isEqualTo(expectedNumOfEvents);
        assertThat(transmissionUpdateCallback.getReceiveDatagramStateChange(0)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_RECEIVING,
                        0, SatelliteManager.SATELLITE_ERROR_NONE));
        assertThat(transmissionUpdateCallback.getReceiveDatagramStateChange(1)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_RECEIVE_SUCCESS,
                        0, SatelliteManager.SATELLITE_ERROR_NONE));
        assertThat(transmissionUpdateCallback.getReceiveDatagramStateChange(2)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE,
                        0, SatelliteManager.SATELLITE_ERROR_NONE));

        transmissionUpdateCallback.clearReceiveDatagramStateChanges();
        stopTransmissionUpdates(transmissionUpdateCallback);
        sSatelliteManager.unregisterForSatelliteDatagram(satelliteDatagramCallback);
    }

    private SatelliteTransmissionUpdateCallbackTest startTransmissionUpdates() {
        LinkedBlockingQueue<Integer> resultListener = new LinkedBlockingQueue<>(1);
        SatelliteTransmissionUpdateCallbackTest transmissionUpdateCallback =
                new SatelliteTransmissionUpdateCallbackTest();
        sSatelliteManager.startSatelliteTransmissionUpdates(getContext().getMainExecutor(),
                resultListener::offer, transmissionUpdateCallback);
        Integer errorCode;
        try {
            errorCode = resultListener.poll(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("SatelliteTransmissionUpdateCallbackTest: Got InterruptedException in waiting"
                    + " for the startSatelliteTransmissionUpdates result code");
            return null;
        }
        assertNotNull(errorCode);
        assertThat(errorCode).isEqualTo(SatelliteManager.SATELLITE_ERROR_NONE);
        return transmissionUpdateCallback;
    }

    private void stopTransmissionUpdates(
            SatelliteTransmissionUpdateCallbackTest transmissionUpdateCallback) {
        LinkedBlockingQueue<Integer> resultListener = new LinkedBlockingQueue<>(1);
        sSatelliteManager.stopSatelliteTransmissionUpdates(transmissionUpdateCallback,
                getContext().getMainExecutor(), resultListener::offer);
    }

    private void assertSingleSendDatagramStateChanged(
            SatelliteTransmissionUpdateCallbackTest transmissionUpdateCallback,
            int expectedTransferState, int expectedPendingCount, int expectedErrorCode) {
        assertTrue(transmissionUpdateCallback.waitUntilOnSendDatagramStateChanged(1));
        assertThat(transmissionUpdateCallback.getNumOfSendDatagramStateChanges()).isEqualTo(1);
        assertThat(transmissionUpdateCallback.getSendDatagramStateChange(0)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        expectedTransferState, expectedPendingCount, expectedErrorCode));
    }

    private void assertSingleReceiveDatagramStateChanged(
            SatelliteTransmissionUpdateCallbackTest transmissionUpdateCallback,
            int expectedTransferState, int expectedPendingCount, int expectedErrorCode) {
        assertTrue(transmissionUpdateCallback
                .waitUntilOnReceiveDatagramStateChanged(1));
        assertThat(transmissionUpdateCallback.getNumOfReceiveDatagramStateChanges())
                .isEqualTo(1);
        assertThat(transmissionUpdateCallback.getReceiveDatagramStateChange(0)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        expectedTransferState, expectedPendingCount, expectedErrorCode));
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
