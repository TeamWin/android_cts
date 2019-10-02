package android.telecom.cts;

import static android.telecom.cts.TestUtils.TEST_PHONE_ACCOUNT_HANDLE;

import android.media.AudioManager;
import android.os.Bundle;
import android.telecom.Call;
import android.telecom.Call.Details;
import android.telecom.CallScreeningService.CallResponse;
import android.telecom.Connection;
import android.telecom.DisconnectCause;
import android.telecom.TelecomManager;
import android.telecom.VideoProfile;
import android.telecom.cts.MockCallScreeningService.CallScreeningServiceCallbacks;
import android.text.TextUtils;

import java.util.concurrent.TimeUnit;

public class BackgroundCallAudioTest extends BaseTelecomTestWithMockServices {
    private static final boolean ENABLED = false; // TODO: change to true once tests pass

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mShouldTestTelecom &= ENABLED;
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

        Call call = mInCallCallbacks.getService().getLastCall();
        assertCallState(call, Call.STATE_AUDIO_PROCESSING);
        assertConnectionState(connection, Connection.STATE_ACTIVE);

        AudioManager audioManager = mContext.getSystemService(AudioManager.class);
        assertEquals(0 /* TODO: put new mode here */, audioManager.getMode());

        call.disconnect();
        assertCallState(call, Call.STATE_DISCONNECTED);
        assertEquals(DisconnectCause.REJECTED, connection.getDisconnectCause().getCode());
    }

    public void testAudioProcessingFromCallScreeningMissed() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }

        setupIncomingCallWithCallScreening();

        final MockConnection connection = verifyConnectionForIncomingCall();

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
        assertEquals(DisconnectCause.REJECTED, connection.getDisconnectCause().getCode());
    }

    public void testEnterAudioProcessingWithoutPermission() {
        if (!mShouldTestTelecom) {
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

    private void verifySimulateRingAndUserPickup(Call call, Connection connection) {
        AudioManager audioManager = mContext.getSystemService(AudioManager.class);

        call.exitBackgroundAudioProcessing(true);
        assertCallState(call, Call.STATE_SIMULATED_RINGING);
        assertEquals(AudioManager.MODE_RINGTONE, audioManager.getMode());
        assertConnectionState(connection, Connection.STATE_ACTIVE);

        call.answer(VideoProfile.STATE_AUDIO_ONLY);
        assertCallState(call, Call.STATE_ACTIVE);
        assertEquals(AudioManager.MODE_IN_CALL, audioManager.getMode());
        assertConnectionState(connection, Connection.STATE_ACTIVE);
    }

    private void verifySimulateRingAndUserMissed(Call call, Connection connection) {
        AudioManager audioManager = mContext.getSystemService(AudioManager.class);

        call.exitBackgroundAudioProcessing(true);
        assertCallState(call, Call.STATE_SIMULATED_RINGING);
        assertEquals(AudioManager.MODE_RINGTONE, audioManager.getMode());
        assertConnectionState(connection, Connection.STATE_ACTIVE);

        call.disconnect();
        assertCallState(call, Call.STATE_DISCONNECTED);
        assertConnectionState(connection, Connection.STATE_DISCONNECTED);
        assertEquals(DisconnectCause.MISSED, connection.getDisconnectCause().getCode());
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

}