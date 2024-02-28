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

import android.bluetooth.BluetoothAdapter;
import android.content.pm.PackageManager;
import android.net.wifi.WifiManager;
import android.nfc.NfcAdapter;
import android.os.Build;
import android.os.CancellationSignal;
import android.os.OutcomeReceiver;
import android.os.SystemProperties;
import android.provider.Settings;
import android.telephony.satellite.AntennaDirection;
import android.telephony.satellite.AntennaPosition;
import android.telephony.satellite.PointingInfo;
import android.telephony.satellite.SatelliteCapabilities;
import android.telephony.satellite.SatelliteDatagram;
import android.telephony.satellite.SatelliteManager;
import android.telephony.satellite.stub.SatelliteError;
import android.uwb.UwbManager;

import androidx.test.InstrumentationRegistry;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
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
    private static final long TEST_SATELLITE_DEVICE_ALIGN_TIMEOUT_MILLIS = 100;
    private static final long TEST_SATELLITE_DEVICE_ALIGN_FOREVER_TIMEOUT_MILLIS = 100000;

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

    UwbManager mUwbManager = null;
    NfcAdapter mNfcAdapter = null;
    BluetoothAdapter mBluetoothAdapter = null;
    WifiManager mWifiManager = null;
    private String mTestSatelliteModeRadios = null;
    boolean mBTInitState = false;
    boolean mWifiInitState = false;
    boolean mNfcInitState = false;
    boolean mUwbInitState = false;

    @BeforeClass
    public static void beforeAllTests() throws Exception {
        logd("beforeAllTests");
        if (!shouldTestSatelliteWithMockService()) return;

        beforeAllTestsBase();
        enforceMockModemDeveloperSetting();

        sMockSatelliteServiceManager = new MockSatelliteServiceManager(
                InstrumentationRegistry.getInstrumentation());

        grantSatellitePermission();

        revokeSatellitePermission();
    }

    @AfterClass
    public static void afterAllTests() {
        logd("afterAllTests");
        if (!shouldTestSatelliteWithMockService()) return;

        sMockSatelliteServiceManager = null;
        afterAllTestsBase();
    }

    @Before
    public void setUp() throws Exception {
        logd("setUp");
        assumeTrue(getContext().getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_TELEPHONY_SATELLITE));
        if (!shouldTestSatelliteWithMockService()) return;
        assumeTrue(sMockSatelliteServiceManager != null);

        sMockSatelliteServiceManager.setErrorCode(SatelliteError.ERROR_NONE);
        sMockSatelliteServiceManager.setWaitToSend(false);
        sMockSatelliteServiceManager.setShouldRespondTelephony(true);

        // Initialize radio state
        mBTInitState = false;
        mWifiInitState = false;
        mNfcInitState = false;
        mUwbInitState = false;
        mTestSatelliteModeRadios = "";

        SatelliteModeRadiosUpdater satelliteRadiosModeUpdater =
                new SatelliteModeRadiosUpdater(getContext());
        assertTrue(satelliteRadiosModeUpdater.setSatelliteModeRadios(""));

        grantSatellitePermission();

        revokeSatellitePermission();
    }

    @After
    public void tearDown() {
        logd("tearDown");
        if (!shouldTestSatelliteWithMockService()) return;
        assumeTrue(sMockSatelliteServiceManager != null);
        sMockSatelliteServiceManager.setErrorCode(SatelliteError.ERROR_NONE);
        sMockSatelliteServiceManager.setWaitToSend(false);
        sMockSatelliteServiceManager.setShouldRespondTelephony(true);

        grantSatellitePermission();

        sMockSatelliteServiceManager.clearSentSatelliteDatagramInfo();
        sMockSatelliteServiceManager.clearMockPointingUiActivityStatusChanges();
        sMockSatelliteServiceManager.clearListeningEnabledList();
        revokeSatellitePermission();
        waitFor(500);
    }

    @Test
    public void testProvisionSatelliteService() {
        if (!shouldTestSatelliteWithMockService()) return;

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
    public void testSatelliteDatagramReceivedAck() {
        if (!shouldTestSatelliteWithMockService()) return;

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
        if (!shouldTestSatelliteWithMockService()) return;

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
    public void testSendSatelliteDatagram_failure() {
        if (!shouldTestSatelliteWithMockService()) return;

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

        try {
            errorCode = resultListener.poll(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("testSendSatelliteDatagram_failure: Got InterruptedException in waiting"
                    + " for the sendSatelliteDatagram result code");
            return;
        }
        assertNotNull(errorCode);
        assertThat(errorCode).isEqualTo(SatelliteManager.SATELLITE_ERROR);

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

        callback.clearSendDatagramStateChanges();
        sSatelliteManager.stopSatelliteTransmissionUpdates(callback, getContext().getMainExecutor(),
                resultListener::offer);
        revokeSatellitePermission();
    }

    @Test
    public void testSendMultipleSatelliteDatagrams_success() {
        if (!shouldTestSatelliteWithMockService()) return;

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

        try {
            errorCode = resultListener1.poll(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("testSendMultipleSatelliteDatagrams_success: Got InterruptedException in waiting"
                    + " for the sendSatelliteDatagram result code");
            return;
        }
        assertNotNull(errorCode);
        assertThat(errorCode).isEqualTo(SatelliteManager.SATELLITE_ERROR_NONE);

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

        assertTrue(sMockSatelliteServiceManager.waitForEventOnSendSatelliteDatagram(1));

        // Send second datagram: SENDING to SENDING_SUCCESS
        // callback.clearSendDatagramStateChanges();
        assertTrue(sMockSatelliteServiceManager.sendSavedDatagram());

        try {
            errorCode = resultListener2.poll(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("testSendMultipleSatelliteDatagrams_success: Got InterruptedException in waiting"
                    + " for the sendSatelliteDatagram result code");
            return;
        }
        assertNotNull(errorCode);
        assertThat(errorCode).isEqualTo(SatelliteManager.SATELLITE_ERROR_NONE);

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

        assertTrue(sMockSatelliteServiceManager.waitForEventOnSendSatelliteDatagram(1));

        // Send third datagram: SENDING - SENDING_SUCCESS - IDLE
        assertTrue(sMockSatelliteServiceManager.sendSavedDatagram());

        try {
            errorCode = resultListener3.poll(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("testSendMultipleSatelliteDatagrams_success: Got InterruptedException in waiting"
                    + " for the sendSatelliteDatagram result code");
            return;
        }
        assertNotNull(errorCode);
        assertThat(errorCode).isEqualTo(SatelliteManager.SATELLITE_ERROR_NONE);

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

        callback.clearSendDatagramStateChanges();
        sMockSatelliteServiceManager.clearSentSatelliteDatagramInfo();
        sMockSatelliteServiceManager.setWaitToSend(false);
        sSatelliteManager.stopSatelliteTransmissionUpdates(callback, getContext().getMainExecutor(),
                resultListener::offer);
        revokeSatellitePermission();
    }

    @Test
    public void testSendMultipleSatelliteDatagrams_failure() {
        if (!shouldTestSatelliteWithMockService()) return;

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

        callback.clearSendDatagramStateChanges();
        sMockSatelliteServiceManager.clearSentSatelliteDatagramInfo();
        sMockSatelliteServiceManager.setWaitToSend(false);
        sSatelliteManager.stopSatelliteTransmissionUpdates(callback, getContext().getMainExecutor(),
                resultListener::offer);
        revokeSatellitePermission();
    }

    @Test
    public void testReceiveMultipleSatelliteDatagrams() {
        if (!shouldTestSatelliteWithMockService()) return;

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
        if (!shouldTestSatelliteWithMockService()) return;

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

        transmissionUpdateCallback.clearPointingInfo();
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

        transmissionUpdateCallback.clearPointingInfo();
        sSatelliteManager.stopSatelliteTransmissionUpdates(transmissionUpdateCallback,
                getContext().getMainExecutor(), resultListener::offer);
        revokeSatellitePermission();
    }

    @Test
    public void testReceiveMultipleSatellitePositionUpdates() {
        if (!shouldTestSatelliteWithMockService()) return;

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
        transmissionUpdateCallback.clearPointingInfo();
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
        transmissionUpdateCallback.clearPointingInfo();
        pointingInfo.satelliteAzimuth = 100;
        pointingInfo.satelliteElevation = 120;
        expectedPointingInfo = new PointingInfo(100, 120);
        sMockSatelliteServiceManager.sendOnSatellitePositionChanged(pointingInfo);
        assertTrue(transmissionUpdateCallback.waitUntilOnSatellitePositionChanged(1));
        assertThat(transmissionUpdateCallback.mPointingInfo.getSatelliteAzimuthDegrees())
                .isEqualTo(expectedPointingInfo.getSatelliteAzimuthDegrees());
        assertThat(transmissionUpdateCallback.mPointingInfo.getSatelliteElevationDegrees())
                .isEqualTo(expectedPointingInfo.getSatelliteElevationDegrees());

        transmissionUpdateCallback.clearPointingInfo();
        sSatelliteManager.stopSatelliteTransmissionUpdates(transmissionUpdateCallback,
                getContext().getMainExecutor(), resultListener::offer);
        revokeSatellitePermission();
    }




    @Test
    public void testSatelliteModemBusy_modemSendingDatagram_pollingFailure() {
        if (!shouldTestSatellite()) return;

        logd("testSatelliteModemBusy_modemSendingDatagram_pollingFailure");
        grantSatellitePermission();
        assertTrue(isSatelliteProvisioned());

        String mText = "This is a test datagram message from user";
        SatelliteDatagram datagram = new SatelliteDatagram(mText.getBytes());
        sMockSatelliteServiceManager.clearSentSatelliteDatagramInfo();

        // Wait to process datagrams so that datagrams are added to pending list
        // and modem is busy sending datagrams
        sMockSatelliteServiceManager.setWaitToSend(true);

        LinkedBlockingQueue<Integer> sendResultListener = new LinkedBlockingQueue<>(1);
        sSatelliteManager.sendSatelliteDatagram(SatelliteManager.DATAGRAM_TYPE_SOS_MESSAGE,
                datagram, true, getContext().getMainExecutor(),
                sendResultListener::offer);

        LinkedBlockingQueue<Integer> pollResultListener = new LinkedBlockingQueue<>(1);
        sSatelliteManager.pollPendingSatelliteDatagrams(getContext().getMainExecutor(),
                pollResultListener::offer);

        Integer errorCode;
        try {
            errorCode = pollResultListener.poll(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("testSatelliteModemBusy_modemSendingDatagram_pollingFailure: Got "
                    + "InterruptedException in waiting for the pollPendingSatelliteDatagrams "
                    + "result code");
            return;
        }
        assertNotNull(errorCode);
        assertThat(errorCode).isEqualTo(SatelliteManager.SATELLITE_MODEM_BUSY);

        // Send datagram successfully to bring sending state back to IDLE.
        assertTrue(sMockSatelliteServiceManager.waitForEventOnSendSatelliteDatagram(1));
        assertTrue(sMockSatelliteServiceManager.sendSavedDatagram());
        sMockSatelliteServiceManager.clearSentSatelliteDatagramInfo();
        sMockSatelliteServiceManager.setWaitToSend(false);
        revokeSatellitePermission();
    }

    @Test
    public void testSatelliteModemBusy_modemPollingDatagrams_pollingFailure() {
        if (!shouldTestSatellite()) return;

        logd("testSatelliteModemBusy_modemPollingDatagrams_pollingFailure");
        grantSatellitePermission();
        assertTrue(isSatelliteProvisioned());

        LinkedBlockingQueue<Integer> pollResultListener1 = new LinkedBlockingQueue<>(1);
        sSatelliteManager.pollPendingSatelliteDatagrams(getContext().getMainExecutor(),
                pollResultListener1::offer);

        // As we already got one polling request, this second polling request would fail
        LinkedBlockingQueue<Integer> pollResultListener2 = new LinkedBlockingQueue<>(1);
        sSatelliteManager.pollPendingSatelliteDatagrams(getContext().getMainExecutor(),
                pollResultListener2::offer);

        Integer errorCode;
        try {
            errorCode = pollResultListener2.poll(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("testSatelliteModemBusy_modemSendingDatagram_pollingFailure: Got "
                    + "InterruptedException in waiting for the pollPendingSatelliteDatagrams "
                    + "result code");
            return;
        }
        assertNotNull(errorCode);
        assertThat(errorCode).isEqualTo(SatelliteManager.SATELLITE_MODEM_BUSY);

        // Receive one datagram successfully to bring receiving state back to IDLE.
        String receivedText = "This is a test datagram message from satellite";
        android.telephony.satellite.stub.SatelliteDatagram receivedDatagram =
                new android.telephony.satellite.stub.SatelliteDatagram();
        receivedDatagram.data = receivedText.getBytes();
        sMockSatelliteServiceManager.sendOnSatelliteDatagramReceived(receivedDatagram, 0);
        revokeSatellitePermission();
    }

    @Test
    public void testSatelliteModemBusy_modemPollingDatagram_sendingDelayed() {
        if (!shouldTestSatellite()) return;

        logd("testSatelliteModemBusy_modemPollingDatagram_sendingDelayed");
        grantSatellitePermission();
        assertTrue(isSatelliteProvisioned());

        String mText = "This is a test datagram message from user";
        SatelliteDatagram datagram = new SatelliteDatagram(mText.getBytes());
        sMockSatelliteServiceManager.clearSentSatelliteDatagramInfo();

        LinkedBlockingQueue<Integer> resultListener = new LinkedBlockingQueue<>(1);
        SatelliteTransmissionUpdateCallbackTest transmissionUpdateCallback =
                new SatelliteTransmissionUpdateCallbackTest();
        sSatelliteManager.startSatelliteTransmissionUpdates(getContext().getMainExecutor(),
                resultListener::offer, transmissionUpdateCallback);
        Integer errorCode;
        try {
            errorCode = resultListener.poll(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("testSatelliteModemBusy_modemPollingDatagram_sendingDelayed: "
                    + "Got InterruptedException in waiting for the "
                    + "startSatelliteTransmissionUpdates result code");
            return;
        }
        assertNotNull(errorCode);
        assertThat(errorCode).isEqualTo(SatelliteManager.SATELLITE_ERROR_NONE);

        SatelliteDatagramCallbackTest satelliteDatagramCallback =
                new SatelliteDatagramCallbackTest();
        sSatelliteManager.registerForSatelliteDatagram(
                getContext().getMainExecutor(), satelliteDatagramCallback);

        transmissionUpdateCallback.clearSendDatagramStateChanges();
        transmissionUpdateCallback.clearReceiveDatagramStateChanges();

        LinkedBlockingQueue<Integer> pollResultListener = new LinkedBlockingQueue<>(1);
        sSatelliteManager.pollPendingSatelliteDatagrams(getContext().getMainExecutor(),
                pollResultListener::offer);
        // Datagram transfer state changes from IDLE -> RECEIVING.
        assertSingleReceiveDatagramStateChanged(transmissionUpdateCallback,
                SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_RECEIVING,
                0, SatelliteManager.SATELLITE_ERROR_NONE);

        LinkedBlockingQueue<Integer> sendResultListener = new LinkedBlockingQueue<>(1);
        sSatelliteManager.sendSatelliteDatagram(SatelliteManager.DATAGRAM_TYPE_SOS_MESSAGE,
                datagram, true, getContext().getMainExecutor(),
                sendResultListener::offer);
        // Sending datagram will be delayed as modem is in RECEIVING state
        assertFalse(sMockSatelliteServiceManager.waitForEventOnSendSatelliteDatagram(1));

        String receivedText = "This is a test datagram message from satellite";
        android.telephony.satellite.stub.SatelliteDatagram receivedDatagram =
                new android.telephony.satellite.stub.SatelliteDatagram();
        receivedDatagram.data = receivedText.getBytes();
        transmissionUpdateCallback.clearReceiveDatagramStateChanges();
        sMockSatelliteServiceManager.sendOnSatelliteDatagramReceived(receivedDatagram, 0);
        // As pending count is 0, datagram transfer state changes from
        // RECEIVING -> RECEIVE_SUCCESS -> IDLE.
        int expectedNumOfEvents = 2;
        assertTrue(transmissionUpdateCallback
                .waitUntilOnReceiveDatagramStateChanged(expectedNumOfEvents));
        assertThat(transmissionUpdateCallback.getReceiveDatagramStateChange(0)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_RECEIVE_SUCCESS,
                        0, SatelliteManager.SATELLITE_ERROR_NONE));
        assertThat(transmissionUpdateCallback.getReceiveDatagramStateChange(1)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE,
                        0, SatelliteManager.SATELLITE_ERROR_NONE));

        // As polling is completed, now modem will start sending datagrams
        expectedNumOfEvents = 3;
        assertTrue(transmissionUpdateCallback.
                waitUntilOnSendDatagramStateChanged(expectedNumOfEvents));
        assertThat(transmissionUpdateCallback.getSendDatagramStateChange(0)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_SENDING,
                        1, SatelliteManager.SATELLITE_ERROR_NONE));
        assertThat(transmissionUpdateCallback.getSendDatagramStateChange(1)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_SEND_SUCCESS,
                        0, SatelliteManager.SATELLITE_ERROR_NONE));
        assertThat(transmissionUpdateCallback.getSendDatagramStateChange(2)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE,
                        0, SatelliteManager.SATELLITE_ERROR_NONE));

        transmissionUpdateCallback.clearSendDatagramStateChanges();
        transmissionUpdateCallback.clearReceiveDatagramStateChanges();
        sMockSatelliteServiceManager.clearSentSatelliteDatagramInfo();
        sSatelliteManager.stopSatelliteTransmissionUpdates(transmissionUpdateCallback,
                getContext().getMainExecutor(), resultListener::offer);
        sSatelliteManager.unregisterForSatelliteDatagram(satelliteDatagramCallback);
        revokeSatellitePermission();
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

    private void sendSatelliteDatagramDemoModeSuccess(String sampleText) {
        SatelliteTransmissionUpdateCallbackTest callback = startTransmissionUpdates();

        // Send satellite datagram
        SatelliteDatagram datagram = new SatelliteDatagram(sampleText.getBytes());
        LinkedBlockingQueue<Integer> resultListener = new LinkedBlockingQueue<>(1);
        callback.clearSendDatagramStateChanges();
        assertTrue(sMockSatelliteServiceManager.overrideSatellitePointingUiClassName());
        sMockSatelliteServiceManager.clearMockPointingUiActivityStatusChanges();
        sMockSatelliteServiceManager.clearSentSatelliteDatagramInfo();
        sSatelliteManager.onDeviceAlignedWithSatellite(true);
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

        sSatelliteManager.onDeviceAlignedWithSatellite(false);
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

    private void identifyRadiosSensitiveToSatelliteMode() {
        PackageManager packageManager = getContext().getPackageManager();
        List<String> satelliteModeRadiosList = new ArrayList<>();

        if (packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI)) {
            satelliteModeRadiosList.add(Settings.Global.RADIO_WIFI);
            mWifiManager = getContext().getSystemService(WifiManager.class);
            mWifiInitState = mWifiManager.isWifiEnabled();
        }

        if (packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)) {
            satelliteModeRadiosList.add(Settings.Global.RADIO_BLUETOOTH);
            mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            mBTInitState = mBluetoothAdapter.isEnabled();
        }

        if (packageManager.hasSystemFeature(PackageManager.FEATURE_NFC)) {
            satelliteModeRadiosList.add(Settings.Global.RADIO_NFC);
            mNfcAdapter = NfcAdapter.getNfcAdapter(getContext().getApplicationContext());
            mNfcInitState = mNfcAdapter.isEnabled();
        }

        if (packageManager.hasSystemFeature(PackageManager.FEATURE_UWB)) {
            satelliteModeRadiosList.add(Settings.Global.RADIO_UWB);
            mUwbManager = getContext().getSystemService(UwbManager.class);
            mUwbInitState = mUwbManager.isUwbEnabled();
        }

        mTestSatelliteModeRadios = String.join(",", satelliteModeRadiosList);
    }

    private boolean isRadioSatelliteModeSensitive(String radio) {
        return mTestSatelliteModeRadios.contains(radio);
    }

    private boolean areAllRadiosDisabled() {
        logd("areAllRadiosDisabled");
        if (isRadioSatelliteModeSensitive(Settings.Global.RADIO_WIFI)) {
            assertFalse(mWifiManager.isWifiEnabled());
        }

        if (isRadioSatelliteModeSensitive(Settings.Global.RADIO_UWB)) {
            assertFalse(mUwbManager.isUwbEnabled());
        }

        if (isRadioSatelliteModeSensitive(Settings.Global.RADIO_NFC)) {
            assertFalse(mNfcAdapter.isEnabled());
        }

        if (isRadioSatelliteModeSensitive(Settings.Global.RADIO_BLUETOOTH)) {
            assertFalse(mBluetoothAdapter.isEnabled());
        }

        return true;
    }

    private boolean areAllRadiosResetToInitialState() {
        Object lock = new Object();
        synchronized (lock) {
            try {
                lock.wait(TIMEOUT);
            } catch (InterruptedException e) {
                // Ignore
                logd("InterruptedException e:" + e);
            }

            logd("areAllRadiosResetToInitialState");
            if (isRadioSatelliteModeSensitive(Settings.Global.RADIO_WIFI)) {
                if (mWifiInitState) {
                    assertTrue(mWifiManager.isWifiEnabled());
                } else {
                    assertFalse(mWifiManager.isWifiEnabled());
                }
            }

            if (isRadioSatelliteModeSensitive(Settings.Global.RADIO_BLUETOOTH)) {
                if (mBTInitState) {
                    assertTrue(mBluetoothAdapter.isEnabled());
                } else {
                    assertFalse(mBluetoothAdapter.isEnabled());
                }
            }

            if (isRadioSatelliteModeSensitive(Settings.Global.RADIO_NFC)) {
                if (mNfcInitState) {
                    assertTrue(mNfcAdapter.isEnabled());
                } else {
                    assertFalse(mNfcAdapter.isEnabled());
                }
            }

            if (isRadioSatelliteModeSensitive(Settings.Global.RADIO_UWB)) {
                if (mUwbInitState) {
                    assertTrue(mUwbManager.isUwbEnabled());
                } else {
                    assertFalse(mUwbManager.isUwbEnabled());
                }
            }

            return true;
        }
    }

    private void waitFor(long timeoutMillis) {
        Object delayTimeout = new Object();
        synchronized (delayTimeout) {
            try {
                delayTimeout.wait(timeoutMillis);
            } catch (InterruptedException ex) {
                // Ignore the exception
                logd("waitFor: delayTimeout ex=" + ex);
            }
        }
    }
}
