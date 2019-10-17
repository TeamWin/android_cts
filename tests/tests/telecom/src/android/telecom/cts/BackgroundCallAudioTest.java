package android.telecom.cts;

import static android.telecom.cts.TestUtils.TEST_PHONE_ACCOUNT_HANDLE;
import static android.telecom.cts.TestUtils.waitOnAllHandlers;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.IBinder;
import android.telecom.Call;
import android.telecom.Call.Details;
import android.telecom.CallScreeningService.CallResponse;
import android.telecom.Connection;
import android.telecom.DisconnectCause;
import android.telecom.TelecomManager;
import android.telecom.VideoProfile;
import android.telecom.cts.MockCallScreeningService.CallScreeningServiceCallbacks;
import android.telecom.cts.api29incallservice.CtsApi29InCallService;
import android.telecom.cts.api29incallservice.CtsApi29InCallServiceControl;
import android.telecom.cts.api29incallservice.ICtsApi29InCallServiceControl;
import android.text.TextUtils;
import android.util.Log;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class BackgroundCallAudioTest extends BaseTelecomTestWithMockServices {
    private static final String LOG_TAG = BackgroundCallAudioTest.class.getSimpleName();

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        if (mShouldTestTelecom) {
            mPreviousDefaultDialer = TestUtils.getDefaultDialer(getInstrumentation());
            TestUtils.setDefaultDialer(getInstrumentation(), TestUtils.PACKAGE);
            setupConnectionService(null, FLAG_REGISTER | FLAG_ENABLE);
            MockCallScreeningService.enableService(mContext);
        }
    }

    @Override
    protected void tearDown() throws Exception {
        if (mShouldTestTelecom && !TextUtils.isEmpty(mPreviousDefaultDialer)) {
            TestUtils.setDefaultDialer(getInstrumentation(), mPreviousDefaultDialer);
            mTelecomManager.unregisterPhoneAccount(TEST_PHONE_ACCOUNT_HANDLE);
            CtsConnectionService.tearDown();
            MockCallScreeningService.disableService(mContext);
        }
        super.tearDown();
    }

    public void testAudioProcessingFromCallScreeningAllow() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }

        setupIncomingCallWithCallScreening();

        final MockConnection connection = verifyConnectionForIncomingCall();

        if (!mInCallCallbacks.lock.tryAcquire(TestUtils.WAIT_FOR_CALL_ADDED_TIMEOUT_S,
                TimeUnit.SECONDS)) {
            fail("No call added to InCallService.");
        }

        Call call = mInCallCallbacks.getService().getLastCall();
        assertCallState(call, Call.STATE_AUDIO_PROCESSING);
        assertConnectionState(connection, Connection.STATE_ACTIVE);

        AudioManager audioManager = mContext.getSystemService(AudioManager.class);
        assertEquals(0 /* TODO: put new mode here */, audioManager.getMode());

        verifySimulateRingAndUserPickup(call, connection);
    }

    public void testAudioProcessingFromCallScreeningDisallow() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }

        setupIncomingCallWithCallScreening();

        final MockConnection connection = verifyConnectionForIncomingCall();

        if (!mInCallCallbacks.lock.tryAcquire(TestUtils.WAIT_FOR_CALL_ADDED_TIMEOUT_S,
                TimeUnit.SECONDS)) {
            fail("No call added to InCallService.");
        }

        Call call = mInCallCallbacks.getService().getLastCall();
        assertCallState(call, Call.STATE_AUDIO_PROCESSING);
        assertConnectionState(connection, Connection.STATE_ACTIVE);

        AudioManager audioManager = mContext.getSystemService(AudioManager.class);
        assertEquals(0 /* TODO: put new mode here */, audioManager.getMode());

        call.disconnect();
        assertCallState(call, Call.STATE_DISCONNECTED);
        assertEquals(DisconnectCause.REJECTED, call.getDetails().getDisconnectCause().getCode());
    }

    public void testAudioProcessingFromCallScreeningMissed() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }

        setupIncomingCallWithCallScreening();

        final MockConnection connection = verifyConnectionForIncomingCall();

        if (!mInCallCallbacks.lock.tryAcquire(TestUtils.WAIT_FOR_CALL_ADDED_TIMEOUT_S,
                TimeUnit.SECONDS)) {
            fail("No call added to InCallService.");
        }

        Call call = mInCallCallbacks.getService().getLastCall();
        assertCallState(call, Call.STATE_AUDIO_PROCESSING);
        assertConnectionState(connection, Connection.STATE_ACTIVE);

        AudioManager audioManager = mContext.getSystemService(AudioManager.class);
        assertEquals(0 /* TODO: put new mode here */, audioManager.getMode());

        verifySimulateRingAndUserMissed(call, connection);
    }

    public void testAudioProcessActiveCall() {
        if (!mShouldTestTelecom) {
            return;
        }

        if (true) {
            // TODO: enable test
            return;
        }

        placeAndVerifyCall();
        final MockConnection connection = verifyConnectionForOutgoingCall();

        final MockInCallService inCallService = mInCallCallbacks.getService();

        connection.setActive();
        final Call call = inCallService.getLastCall();
        assertCallState(call, Call.STATE_ACTIVE);

        call.enterBackgroundAudioProcessing();
        assertCallState(call, Call.STATE_AUDIO_PROCESSING);

        AudioManager audioManager = mContext.getSystemService(AudioManager.class);
        assertEquals(0 /* TODO: put new mode here */, audioManager.getMode());
        assertConnectionState(connection, Connection.STATE_ACTIVE);

        verifySimulateRingAndUserPickup(call, connection);
    }

    public void testAudioProcessActiveCallMissed() {
        if (!mShouldTestTelecom) {
            return;
        }

        if (true) {
            // TODO: enable test
            return;
        }

        placeAndVerifyCall();
        final MockConnection connection = verifyConnectionForOutgoingCall();

        final MockInCallService inCallService = mInCallCallbacks.getService();

        connection.setActive();
        final Call call = inCallService.getLastCall();
        assertCallState(call, Call.STATE_ACTIVE);

        call.enterBackgroundAudioProcessing();
        assertCallState(call, Call.STATE_AUDIO_PROCESSING);
        assertConnectionState(connection, Connection.STATE_ACTIVE);

        AudioManager audioManager = mContext.getSystemService(AudioManager.class);
        assertEquals(0 /* TODO: put new mode here */, audioManager.getMode());

        verifySimulateRingAndUserMissed(call, connection);
    }

    public void testAudioProcessActiveCallRemoteHangup() {
        if (!mShouldTestTelecom) {
            return;
        }

        if (true) {
            // TODO: enable test
            return;
        }

        placeAndVerifyCall();
        final MockConnection connection = verifyConnectionForOutgoingCall();

        final MockInCallService inCallService = mInCallCallbacks.getService();

        connection.setActive();
        final Call call = inCallService.getLastCall();
        assertCallState(call, Call.STATE_ACTIVE);

        call.enterBackgroundAudioProcessing();
        assertCallState(call, Call.STATE_AUDIO_PROCESSING);
        assertConnectionState(connection, Connection.STATE_ACTIVE);

        AudioManager audioManager = mContext.getSystemService(AudioManager.class);
        assertEquals(0 /* TODO: put new mode here */, audioManager.getMode());

        connection.setDisconnected(new DisconnectCause(DisconnectCause.REMOTE));
        assertCallState(call, Call.STATE_DISCONNECTED);
        assertEquals(DisconnectCause.REMOTE, call.getDetails().getDisconnectCause().getCode());
    }

    public void testManualAudioCallScreenAccept() {
        if (!mShouldTestTelecom) {
            return;
        }

        if (true) {
            // TODO: enable test
            return;
        }

        addAndVerifyNewIncomingCall(createTestNumber(), null);
        final MockConnection connection = verifyConnectionForIncomingCall();

        Call call = mInCallCallbacks.getService().getLastCall();
        assertCallState(call, Call.STATE_RINGING);

        call.enterBackgroundAudioProcessing();
        assertCallState(call, Call.STATE_AUDIO_PROCESSING);
        assertConnectionState(connection, Connection.STATE_ACTIVE);

        AudioManager audioManager = mContext.getSystemService(AudioManager.class);
        assertEquals(0 /* TODO: put new mode here */, audioManager.getMode());

        call.exitBackgroundAudioProcessing(false);
        assertCallState(call, Call.STATE_ACTIVE);
        assertEquals(AudioManager.MODE_IN_CALL, audioManager.getMode());
    }

    public void testManualAudioCallScreenReject() {
        if (!mShouldTestTelecom) {
            return;
        }

        if (true) {
            // TODO: enable test
            return;
        }

        addAndVerifyNewIncomingCall(createTestNumber(), null);
        final MockConnection connection = verifyConnectionForIncomingCall();

        Call call = mInCallCallbacks.getService().getLastCall();
        assertCallState(call, Call.STATE_RINGING);

        call.enterBackgroundAudioProcessing();
        assertCallState(call, Call.STATE_AUDIO_PROCESSING);
        assertConnectionState(connection, Connection.STATE_ACTIVE);

        AudioManager audioManager = mContext.getSystemService(AudioManager.class);
        assertEquals(0 /* TODO: put new mode here */, audioManager.getMode());

        call.disconnect();
        assertCallState(call, Call.STATE_DISCONNECTED);
        assertEquals(DisconnectCause.REJECTED, call.getDetails().getDisconnectCause().getCode());
    }

    public void testEnterAudioProcessingWithoutPermission() {
        if (!mShouldTestTelecom) {
            return;
        }

        if (true) {
            // TODO: enable test
            return;
        }

        placeAndVerifyCall();
        final MockConnection connection = verifyConnectionForOutgoingCall();

        final MockInCallService inCallService = mInCallCallbacks.getService();

        connection.setActive();
        final Call call = inCallService.getLastCall();
        assertCallState(call, Call.STATE_ACTIVE);

        try {
            call.enterBackgroundAudioProcessing();
            fail("Expected SecurityException");
        } catch (SecurityException e) {
            // expected
        }
    }

    public void testLowerApiLevelCompatibility() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }

        ICtsApi29InCallServiceControl controlInterface = setUpControl();

        setupIncomingCallWithCallScreening();

        final MockConnection connection = verifyConnectionForIncomingCall();

        if (!mInCallCallbacks.lock.tryAcquire(TestUtils.WAIT_FOR_CALL_ADDED_TIMEOUT_S,
                TimeUnit.SECONDS)) {
            fail("No call added to InCallService.");
        }

        Call call = mInCallCallbacks.getService().getLastCall();
        assertCallState(call, Call.STATE_AUDIO_PROCESSING);
        assertConnectionState(connection, Connection.STATE_ACTIVE);
        // Make sure that the dummy app never got any calls
        assertEquals(0, controlInterface.getHistoricalCallCount());

        call.exitBackgroundAudioProcessing(true);
        assertCallState(call, Call.STATE_SIMULATED_RINGING);
        waitOnAllHandlers(getInstrumentation());
        assertConnectionState(connection, Connection.STATE_ACTIVE);
        // Make sure that the dummy app sees a ringing call.
        assertEquals(Call.STATE_RINGING,
                controlInterface.getCallState(call.getDetails().getTelecomCallId()));

        call.answer(VideoProfile.STATE_AUDIO_ONLY);
        assertCallState(call, Call.STATE_ACTIVE);
        waitOnAllHandlers(getInstrumentation());
        assertConnectionState(connection, Connection.STATE_ACTIVE);
        // Make sure that the dummy app sees an active call.
        assertEquals(Call.STATE_ACTIVE,
                controlInterface.getCallState(call.getDetails().getTelecomCallId()));

        TestUtils.executeShellCommand(getInstrumentation(),
                "telecom add-or-remove-call-companion-app " + CtsApi29InCallService.PACKAGE_NAME
                        + " 0");
    }

    private void verifySimulateRingAndUserPickup(Call call, Connection connection) {
        AudioManager audioManager = mContext.getSystemService(AudioManager.class);

        call.exitBackgroundAudioProcessing(true);
        assertCallState(call, Call.STATE_SIMULATED_RINGING);
        waitOnAllHandlers(getInstrumentation());
        assertEquals(AudioManager.MODE_RINGTONE, audioManager.getMode());
        assertConnectionState(connection, Connection.STATE_ACTIVE);

        call.answer(VideoProfile.STATE_AUDIO_ONLY);
        assertCallState(call, Call.STATE_ACTIVE);
        waitOnAllHandlers(getInstrumentation());
        assertEquals(AudioManager.MODE_IN_CALL, audioManager.getMode());
        assertConnectionState(connection, Connection.STATE_ACTIVE);
    }

    private void verifySimulateRingAndUserMissed(Call call, Connection connection) {
        AudioManager audioManager = mContext.getSystemService(AudioManager.class);

        call.exitBackgroundAudioProcessing(true);
        assertCallState(call, Call.STATE_SIMULATED_RINGING);
        waitOnAllHandlers(getInstrumentation());
        assertEquals(AudioManager.MODE_RINGTONE, audioManager.getMode());
        assertConnectionState(connection, Connection.STATE_ACTIVE);

        call.disconnect();
        assertCallState(call, Call.STATE_DISCONNECTED);
        assertConnectionState(connection, Connection.STATE_DISCONNECTED);
        assertEquals(DisconnectCause.MISSED, call.getDetails().getDisconnectCause().getCode());
    }

    private void setupIncomingCallWithCallScreening() throws Exception {
        CallScreeningServiceCallbacks callback = new CallScreeningServiceCallbacks() {
            @Override
            public void onScreenCall(Details callDetails) {
                getService().respondToCall(callDetails, new CallResponse.Builder()
                        .setDisallowCall(false)
                        .setShouldScreenCallFurther(true)
                        .build());
                lock.release();
            }
        };
        MockCallScreeningService.setCallbacks(callback);
        Bundle extras = new Bundle();
        extras.putParcelable(TelecomManager.EXTRA_INCOMING_CALL_ADDRESS, createTestNumber());
        mTelecomManager.addNewIncomingCall(TEST_PHONE_ACCOUNT_HANDLE, extras);

        if (!callback.lock.tryAcquire(TestUtils.WAIT_FOR_CALL_ADDED_TIMEOUT_S,
                TimeUnit.SECONDS)) {
            fail("Call screening service never got the call");
        }

    }

    private ICtsApi29InCallServiceControl setUpControl() throws Exception {
        TestUtils.executeShellCommand(getInstrumentation(),
                "telecom add-or-remove-call-companion-app " + CtsApi29InCallService.PACKAGE_NAME
                        + " 1");

        Intent bindIntent = new Intent(CtsApi29InCallServiceControl.CONTROL_INTERFACE_ACTION);
        ComponentName controlComponentName =
                ComponentName.createRelative(
                        CtsApi29InCallServiceControl.class.getPackage().getName(),
                        CtsApi29InCallServiceControl.class.getName());

        bindIntent.setComponent(controlComponentName);
        LinkedBlockingQueue<ICtsApi29InCallServiceControl> result = new LinkedBlockingQueue<>(1);

        boolean success = mContext.bindService(bindIntent, new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                Log.i(LOG_TAG, "Service Connected: " + name);
                result.offer(ICtsApi29InCallServiceControl.Stub.asInterface(service));
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
            }
        }, Context.BIND_AUTO_CREATE);
        if (!success) {
            fail("Failed to get control interface -- bind error");
        }
        return result.poll(TestUtils.WAIT_FOR_STATE_CHANGE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
    }
}