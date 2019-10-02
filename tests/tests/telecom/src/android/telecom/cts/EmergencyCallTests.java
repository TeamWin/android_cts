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

import android.net.Uri;
import android.os.Bundle;
import android.telecom.Connection;
import android.telecom.DisconnectCause;

public class EmergencyCallTests extends BaseTelecomTestWithMockServices {

    private static final String TEST_EMERGENCY_NUMBER = "5553637";
    private static final Uri TEST_EMERGENCY_URI = Uri.fromParts("tel", TEST_EMERGENCY_NUMBER, null);

    @Override
    public void setUp() throws Exception {
        // Sets up this package as default dialer in super.
        super.setUp();
        NewOutgoingCallBroadcastReceiver.reset();
        if (!mShouldTestTelecom) return;
        setupConnectionService(null, 0);
        setupForEmergencyCalling(TEST_EMERGENCY_NUMBER);
    }

    /**
     * Place an outgoing emergency call and ensure it is started successfully.
     */
    public void testStartEmergencyCall() throws Exception {
        if (!mShouldTestTelecom) return;
        Bundle extras = new Bundle();
        extras.putParcelable(TestUtils.EXTRA_PHONE_NUMBER, TEST_EMERGENCY_URI);
        placeAndVerifyCall(extras);
        Connection connection = verifyConnectionForOutgoingCall();
        assertIsInCall(true);
        assertIsInManagedCall(true);
        try {
            TestUtils.waitOnAllHandlers(getInstrumentation());
        } catch (Exception e) {
            fail("Failed to wait on handlers " + e);
        }
        connection.setDisconnected(new DisconnectCause(DisconnectCause.LOCAL));
        connection.destroy();
    }
}
