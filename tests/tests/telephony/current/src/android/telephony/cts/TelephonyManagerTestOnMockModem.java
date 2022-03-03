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

import static com.android.internal.telephony.RILConstants.INTERNAL_ERR;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_RADIO_POWER;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.content.Context;
import android.content.pm.PackageManager;
import android.telephony.TelephonyManager;
import android.util.Log;

import androidx.test.InstrumentationRegistry;

import com.android.compatibility.common.util.ShellIdentityUtils;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

/** Test MockModemService interfaces. */
public class TelephonyManagerTestOnMockModem {
    private static final String TAG = "TelephonyManagerTestOnMockModem";
    private static MockModemManager sMockModemManager;
    private static TelephonyManager sTelephonyManager;

    @BeforeClass
    public static void beforeAllTests() throws Exception {
        Log.d(TAG, "TelephonyManagerTestOnMockModem#beforeAllTests()");

        if (!hasTelephonyFeature()) {
            return;
        }

        sTelephonyManager =
                (TelephonyManager) getContext().getSystemService(Context.TELEPHONY_SERVICE);

        sMockModemManager = new MockModemManager();
        assertNotNull(sMockModemManager);
        assertTrue(sMockModemManager.connectMockModemService());
    }

    @AfterClass
    public static void afterAllTests() throws Exception {
        Log.d(TAG, "TelephonyManagerTestOnMockModem#afterAllTests()");

        if (!hasTelephonyFeature()) {
            return;
        }

        // Rebind all interfaces which is binding to MockModemService to default.
        assertNotNull(sMockModemManager);
        assertTrue(sMockModemManager.disconnectMockModemService());
        sMockModemManager = null;
    }

    @Before
    public void beforeTest() {
        assumeTrue(hasTelephonyFeature());
    }

    private static Context getContext() {
        return InstrumentationRegistry.getInstrumentation().getContext();
    }

    private static boolean hasTelephonyFeature() {
        final PackageManager pm = getContext().getPackageManager();
        if (!pm.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
            Log.d(TAG, "Skipping test that requires FEATURE_TELEPHONY");
            return false;
        }
        return true;
    }

    @Test
    public void testSimStateChange() throws Throwable {
        Log.d(TAG, "TelephonyManagerTestOnMockModem#testSimStateChange");

        int slotId = 0;
        int simCardState = sTelephonyManager.getSimCardState();
        Log.d(TAG, "Current SIM card state: " + simCardState);

        assertTrue(
                Arrays.asList(TelephonyManager.SIM_STATE_UNKNOWN, TelephonyManager.SIM_STATE_ABSENT)
                        .contains(simCardState));

        // Insert a SIM
        assertTrue(
                sMockModemManager.insertSimCard(
                        slotId, MockSimService.MOCK_SIM_PROFILE_ID_TWN_CHT));
        simCardState = sTelephonyManager.getSimCardState();
        assertEquals(TelephonyManager.SIM_STATE_PRESENT, simCardState);

        // Check SIM state ready
        simCardState = sTelephonyManager.getSimState();
        assertEquals(TelephonyManager.SIM_STATE_READY, simCardState);

        // Remove the SIM
        assertTrue(sMockModemManager.removeSimCard(slotId));
        simCardState = sTelephonyManager.getSimCardState();
        assertEquals(TelephonyManager.SIM_STATE_ABSENT, simCardState);
    }

    @Test
    public void testRadioPowerToggle() throws Throwable {
        Log.d(TAG, "TelephonyManagerTestOnMockModem#testRadioPowerToggle");

        int radioState = sTelephonyManager.getRadioPowerState();
        Log.d(TAG, "Radio state: " + radioState);

        // Toggle radio power
        try {
            ShellIdentityUtils.invokeThrowableMethodWithShellPermissionsNoReturn(
                    sTelephonyManager,
                    (tm) -> tm.toggleRadioOnOff(),
                    SecurityException.class,
                    "android.permission.MODIFY_PHONE_STATE");
        } catch (SecurityException e) {
            Log.d(TAG, "TelephonyManager#toggleRadioOnOff should require " + e);
        }

        // Wait the radio state update in Framework
        TimeUnit.SECONDS.sleep(1);
        int toggleRadioState =
                radioState == TelephonyManager.RADIO_POWER_ON
                        ? TelephonyManager.RADIO_POWER_OFF
                        : TelephonyManager.RADIO_POWER_ON;
        assertEquals(sTelephonyManager.getRadioPowerState(), toggleRadioState);

        // Toggle radio power again back to original radio state
        try {
            ShellIdentityUtils.invokeThrowableMethodWithShellPermissionsNoReturn(
                    sTelephonyManager,
                    (tm) -> tm.toggleRadioOnOff(),
                    SecurityException.class,
                    "android.permission.MODIFY_PHONE_STATE");
        } catch (SecurityException e) {
            Log.d(TAG, "TelephonyManager#toggleRadioOnOff should require " + e);
        }

        // Wait the radio state update in Framework
        TimeUnit.SECONDS.sleep(1);
        assertEquals(sTelephonyManager.getRadioPowerState(), radioState);

        Log.d(TAG, "Test Done ");
    }

    @Test
    public void testRadioPowerWithFailureResults() throws Throwable {
        Log.d(TAG, "TelephonyManagerTestOnMockModem#testRadioPowerWithFailureResults");

        int radioState = sTelephonyManager.getRadioPowerState();
        Log.d(TAG, "Radio state: " + radioState);

        int slotId = 0;
        int toggleRadioState =
                radioState == TelephonyManager.RADIO_POWER_ON
                        ? TelephonyManager.RADIO_POWER_OFF
                        : TelephonyManager.RADIO_POWER_ON;

        // Force the returned response of RIL_REQUEST_RADIO_POWER as INTERNAL_ERR
        sMockModemManager.forceErrorResponse(slotId, RIL_REQUEST_RADIO_POWER, INTERNAL_ERR);

        boolean result = false;
        try {
            boolean state = (toggleRadioState == TelephonyManager.RADIO_POWER_ON) ? true : false;
            result =
                    ShellIdentityUtils.invokeThrowableMethodWithShellPermissions(
                            sTelephonyManager,
                            (tm) -> tm.setRadioPower(state),
                            SecurityException.class,
                            "android.permission.MODIFY_PHONE_STATE");
        } catch (SecurityException e) {
            Log.d(TAG, "TelephonyManager#setRadioPower should require " + e);
        }

        TimeUnit.SECONDS.sleep(1);
        assertTrue(result);
        assertNotEquals(sTelephonyManager.getRadioPowerState(), toggleRadioState);

        // Reset the modified error response of RIL_REQUEST_RADIO_POWER to the original behavior
        // and -1 means to disable the modifed mechanism in mock modem
        sMockModemManager.forceErrorResponse(slotId, RIL_REQUEST_RADIO_POWER, -1);

        // Recovery the power state back to original radio state
        try {
            boolean state = (radioState == TelephonyManager.RADIO_POWER_ON) ? true : false;
            result =
                    ShellIdentityUtils.invokeThrowableMethodWithShellPermissions(
                            sTelephonyManager,
                            (tm) -> tm.setRadioPower(state),
                            SecurityException.class,
                            "android.permission.MODIFY_PHONE_STATE");
        } catch (SecurityException e) {
            Log.d(TAG, "TelephonyManager#setRadioPower should require " + e);
        }
        TimeUnit.SECONDS.sleep(1);
        assertTrue(result);
        assertEquals(sTelephonyManager.getRadioPowerState(), radioState);
    }
}
