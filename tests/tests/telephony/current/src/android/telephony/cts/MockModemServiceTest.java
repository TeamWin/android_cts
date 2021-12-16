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
package android.telephony.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.sysprop.TelephonyProperties;
import android.telephony.TelephonyManager;
import android.util.Log;

import androidx.test.InstrumentationRegistry;

import com.android.compatibility.common.util.ShellIdentityUtils;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

/** Test MockModemService interfaces. */
public class MockModemServiceTest {
    private static final String TAG = "MockModemServiceTest";
    private static MockModemServiceConnector sServiceConnector;
    private static TestMockModemService sMockModem = null;

    TelephonyManager mTelephonyManager =
            (TelephonyManager) getContext().getSystemService(Context.TELEPHONY_SERVICE);

    @BeforeClass
    public static void beforeAllTests() throws Exception {

        Log.d(TAG, "MockModemServiceTest#beforeAllTests()");

        // Override all interfaces to TestMockModemService
        sServiceConnector =
                new MockModemServiceConnector(InstrumentationRegistry.getInstrumentation());

        assertNotNull(sServiceConnector);
        assertTrue(sServiceConnector.connectMockModemService());

        sMockModem = sServiceConnector.getMockModemService();
    }

    @AfterClass
    public static void afterAllTests() throws Exception {
        Log.d(TAG, "MockModemServiceTest#afterAllTests()");

        // Rebind all interfaces which is binding to MockModemService to default.
        assertNotNull(sServiceConnector);
        assertTrue(sServiceConnector.disconnectMockModemService());
        sMockModem = null;
        sServiceConnector = null;
    }

    private static Context getContext() {
        return InstrumentationRegistry.getInstrumentation().getContext();
    }

    @Test
    public void testRadioPower() throws Throwable {
        Log.d(TAG, "MockModemServiceTest#testRadioPower");

        boolean apm = TelephonyProperties.airplane_mode_on().orElse(false);
        Log.d(TAG, "APM setting: " + apm);

        int expectedState;
        int waitLatch;
        if (!apm) {
            expectedState = TelephonyManager.RADIO_POWER_ON;
            waitLatch = TestMockModemService.LATCH_MOCK_MODEM_RADIO_POWR_ON;
        } else {
            expectedState = TelephonyManager.RADIO_POWER_OFF;
            waitLatch = TestMockModemService.LATCH_MOCK_MODEM_RADIO_POWR_OFF;
        }

        assertEquals(mTelephonyManager.getRadioPowerState(), expectedState);

        boolean switchState;
        if (!apm) {
            waitLatch = TestMockModemService.LATCH_MOCK_MODEM_RADIO_POWR_OFF;
            switchState = false;
            expectedState = TelephonyManager.RADIO_POWER_OFF;
        } else {
            waitLatch = TestMockModemService.LATCH_MOCK_MODEM_RADIO_POWR_ON;
            switchState = true;
            expectedState = TelephonyManager.RADIO_POWER_ON;
        }
        sMockModem.resetState(); // Reset the latch

        Log.d(TAG, "set Radio Power: " + switchState);

        boolean result = false;
        try {
            boolean state = switchState;
            result =
                    ShellIdentityUtils.invokeThrowableMethodWithShellPermissions(
                            mTelephonyManager,
                            (tm) -> tm.setRadioPower(state),
                            SecurityException.class,
                            "android.permission.MODIFY_PHONE_STATE");
        } catch (SecurityException e) {
            Log.d(TAG, "TelephonyManager#setRadioPower should require " + e);
        }
        assertTrue(result);
        assertTrue(sMockModem.waitForLatchCountdown(waitLatch));
        assertEquals(mTelephonyManager.getRadioPowerState(), expectedState);

        // Recovery to APM setting
        if (apm) {
            waitLatch = TestMockModemService.LATCH_MOCK_MODEM_RADIO_POWR_OFF;
            switchState = false;
            expectedState = TelephonyManager.RADIO_POWER_OFF;
        } else {
            waitLatch = TestMockModemService.LATCH_MOCK_MODEM_RADIO_POWR_ON;
            switchState = true;
            expectedState = TelephonyManager.RADIO_POWER_ON;
        }
        sMockModem.resetState(); // Reset the latch

        Log.d(TAG, "Recovery Radio Power: " + switchState);

        result = false;
        try {
            boolean state = switchState;
            result =
                    ShellIdentityUtils.invokeThrowableMethodWithShellPermissions(
                            mTelephonyManager,
                            (tm) -> tm.setRadioPower(state),
                            SecurityException.class,
                            "android.permission.MODIFY_PHONE_STATE");
        } catch (SecurityException e) {
            Log.d(TAG, "TelephonyManager#setRadioPower should require " + e);
        }
        assertTrue(result);
        assertTrue(sMockModem.waitForLatchCountdown(waitLatch));
        assertEquals(mTelephonyManager.getRadioPowerState(), expectedState);

        Log.d(TAG, "Test Done ");
    }
}
