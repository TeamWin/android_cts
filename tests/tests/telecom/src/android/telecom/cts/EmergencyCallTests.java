/*
 * Copyright (C) 2019 The Android Open Source Project
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

package android.telecom.cts;

import android.content.ComponentName;
import android.net.Uri;
import android.os.Bundle;
import android.provider.CallLog;
import android.telecom.Call;
import android.telecom.Connection;
import android.telecom.DisconnectCause;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;

import java.util.ArrayList;
import java.util.Random;
import java.util.UUID;

public class EmergencyCallTests extends BaseTelecomTestWithMockServices {

    @Override
    public void setUp() throws Exception {
        // Sets up this package as default dialer in super.
        super.setUp();
        NewOutgoingCallBroadcastReceiver.reset();
        if (!mShouldTestTelecom) return;
        setupConnectionService(null, FLAG_REGISTER | FLAG_ENABLE);
        setupForEmergencyCalling(TEST_EMERGENCY_NUMBER);
    }

    /**
     * Tests a scenario where an emergency call could fail due to the presence of invalid
     * {@link PhoneAccount} data.
     * The seed and quantity for {@link #generateRandomPhoneAccounts(long, int)} is chosen to
     * represent a set of phone accounts which is known in AOSP to cause a failure placing an
     * emergency call.  {@code 52L} was chosen as a random seed and {@code 50} was chosen as the
     * set size for {@link PhoneAccount}s as these were observed in repeated test invocations to
     * induce the failure method.
     *
     * @throws Exception
     */
    public void testEmergencyCallFailureDueToInvalidPhoneAccounts() throws Exception {
        if (!mShouldTestTelecom) return;

        ArrayList<PhoneAccount> accounts = generateRandomPhoneAccounts(52L, 50);
        accounts.stream().forEach(a -> mTelecomManager.registerPhoneAccount(a));
        try {
            assertTrue(mTelecomManager.getSelfManagedPhoneAccounts().size() >= 50);

            // The existing start emergency call test is impacted if there is a failure due to
            // excess phone accounts being present.
            testStartEmergencyCall();
        } finally {
            accounts.stream().forEach(d -> mTelecomManager.unregisterPhoneAccount(
                    d.getAccountHandle()));
        }
    }

    /**
     * Place an outgoing emergency call and ensure it is started successfully.
     */
    public void testStartEmergencyCall() throws Exception {
        if (!mShouldTestTelecom) return;
        Connection conn = placeAndVerifyEmergencyCall(true /*supportsHold*/);
        Call eCall = getInCallService().getLastCall();
        assertCallState(eCall, Call.STATE_DIALING);

        assertIsInCall(true);
        assertIsInManagedCall(true);
        conn.setActive();
        conn.setDisconnected(new DisconnectCause(DisconnectCause.LOCAL));
        conn.destroy();
        assertConnectionState(conn, Connection.STATE_DISCONNECTED);
        assertIsInCall(false);
    }

    /**
     * Place an outgoing emergency call and ensure any incoming call is rejected automatically and
     * logged in call log as a new missed call.
     *
     * Note: PSAPs have requirements that active emergency calls can not be put on hold, so if for
     * some reason an incoming emergency callback happens while on another emergency call, that call
     * will automatically be rejected as well.
     */
    public void testOngoingEmergencyCallAndReceiveIncomingCall() throws Exception {
        if (!mShouldTestTelecom) return;

        Connection eConnection = placeAndVerifyEmergencyCall(true /*supportsHold*/);
        assertIsInCall(true);
        assertIsInManagedCall(true);
        Call eCall = getInCallService().getLastCall();
        assertCallState(eCall, Call.STATE_DIALING);
        eConnection.setActive();
        assertCallState(eCall, Call.STATE_ACTIVE);

        Uri normalCallNumber = createRandomTestNumber();
        addAndVerifyNewFailedIncomingCall(normalCallNumber, null);
        assertCallState(eCall, Call.STATE_ACTIVE);

        // Notify as missed instead of rejected, since the user did not explicitly reject.
        verifyCallLogging(normalCallNumber, CallLog.Calls.MISSED_TYPE);
    }

    /**
     * Receive an incoming ringing call and place an emergency call. The ringing call should be
     * rejected and logged as a new missed call.
     */
    public void testIncomingRingingCallAndPlaceEmergencyCall() throws Exception {
        if (!mShouldTestTelecom) return;

        Uri normalCallNumber = createRandomTestNumber();
        addAndVerifyNewIncomingCall(normalCallNumber, null);
        MockConnection incomingConnection = verifyConnectionForIncomingCall();
        // Ensure destroy happens after emergency call is placed to prevent unbind -> rebind.
        incomingConnection.disableAutoDestroy();
        Call incomingCall = getInCallService().getLastCall();
        assertCallState(incomingCall, Call.STATE_RINGING);

        // Do not support holding incoming call for emergency call.
        Connection eConnection = placeAndVerifyEmergencyCall(false /*supportsHold*/);
        Call eCall = getInCallService().getLastCall();
        assertCallState(eCall, Call.STATE_DIALING);

        incomingConnection.destroy();
        assertConnectionState(incomingConnection, Connection.STATE_DISCONNECTED);
        assertCallState(incomingCall, Call.STATE_DISCONNECTED);

        eConnection.setActive();
        assertCallState(eCall, Call.STATE_ACTIVE);

        // Notify as missed instead of rejected, since the user did not explicitly reject.
        verifyCallLogging(normalCallNumber, CallLog.Calls.MISSED_TYPE);
    }

    /**
     * While on an outgoing call, receive an incoming ringing call and then place an emergency call.
     * The other two calls should stay active while the ringing call should be rejected and logged
     * as a new missed call.
     */
    public void testActiveCallAndIncomingRingingCallAndPlaceEmergencyCall() throws Exception {
        if (!mShouldTestTelecom) return;

        Uri normalOutgoingCallNumber = createRandomTestNumber();
        Bundle extras = new Bundle();
        extras.putParcelable(TestUtils.EXTRA_PHONE_NUMBER, normalOutgoingCallNumber);
        placeAndVerifyCall(extras);
        Connection outgoingConnection = verifyConnectionForOutgoingCall();
        Call outgoingCall = getInCallService().getLastCall();
        outgoingConnection.setActive();
        assertCallState(outgoingCall, Call.STATE_ACTIVE);

        Uri normalIncomingCallNumber = createRandomTestNumber();
        addAndVerifyNewIncomingCall(normalIncomingCallNumber, null);
        MockConnection incomingConnection = verifyConnectionForIncomingCall();
        // Ensure destroy happens after emergency call is placed to prevent unbind -> rebind.
        incomingConnection.disableAutoDestroy();
        Call incomingCall = getInCallService().getLastCall();
        assertCallState(incomingCall, Call.STATE_RINGING);

        // Do not support holding incoming call for emergency call.
        Connection eConnection = placeAndVerifyEmergencyCall(false /*supportsHold*/);
        Call eCall = getInCallService().getLastCall();
        assertCallState(eCall, Call.STATE_DIALING);

        incomingConnection.destroy();
        assertConnectionState(incomingConnection, Connection.STATE_DISCONNECTED);
        assertCallState(incomingCall, Call.STATE_DISCONNECTED);

        eConnection.setActive();
        assertCallState(eCall, Call.STATE_ACTIVE);

        // Notify as missed instead of rejected, since the user did not explicitly reject.
        verifyCallLogging(normalIncomingCallNumber, CallLog.Calls.MISSED_TYPE);
    }

    /**
     * Generates random phone accounts.
     * @param seed random seed to use for random UUIDs; passed in for determinism.
     * @param count How many phone accounts to use.
     * @return Random phone accounts.
     */
    private ArrayList<PhoneAccount> generateRandomPhoneAccounts(long seed, int count) {
        Random random = new Random(seed);
        ArrayList<PhoneAccount> accounts = new ArrayList<>();
        for (int ix = 0 ; ix < count; ix++) {
            ArrayList<String> supportedSchemes = new ArrayList<>();
            supportedSchemes.add("tel");
            supportedSchemes.add("sip");
            supportedSchemes.add("custom");
            PhoneAccountHandle handle = new PhoneAccountHandle(new ComponentName(TestUtils.PACKAGE,
                    TestUtils.COMPONENT), getRandomUuid(random).toString());
            PhoneAccount acct = new PhoneAccount.Builder(handle, "TelecommTests")
                    .setAddress(Uri.fromParts("tel", "555-1212", null))
                    .setCapabilities(PhoneAccount.CAPABILITY_SELF_MANAGED)
                    .setHighlightColor(0)
                    .setShortDescription("test_" + ix)
                    .setSupportedUriSchemes(supportedSchemes)
                    .build();
            accounts.add(acct);
        }
        return accounts;
    }

    /**
     * Returns a random UUID based on the passed in Random generator.
     * @param random Random generator.
     * @return The UUID.
     */
    private UUID getRandomUuid(Random random) {
        byte[] array = new byte[16];
        random.nextBytes(array);
        return UUID.nameUUIDFromBytes(array);
    }

}
