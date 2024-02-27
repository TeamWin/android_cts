/*
 * Copyright (C) 2024 The Android Open Source Project
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

import static android.telephony.mockmodem.MockSimService.MOCK_SIM_PROFILE_ID_TWN_CHT;

import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.SystemClock;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.provider.Telephony;
import android.telephony.cts.util.DefaultSmsAppHelper;
import android.telephony.cts.util.TelephonyUtils;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.internal.telephony.flags.Flags;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;

public class CarrierRoamingSatelliteSmsTest extends CarrierRoamingSatelliteTestBase {
    @Rule
    public final CheckFlagsRule mCheckFlagsRule =
            DeviceFlagsValueProvider.createCheckFlagsRule();

    private static final String TAG = "CarrierRoamingSatelliteSmsTest";
    private static final String SMS_SEND_ACTION = "CTS_SMS_SEND_ACTION";
    private static final String TEST_EMERGENCY_NUMBER = "+14154255486";

    /**
     * Setup before all tests.
     * @throws Exception
     */
    @BeforeClass
    public static void beforeAllTests() throws Exception {
        logd(TAG, "beforeAllTests");

        if (!Flags.carrierEnabledSatelliteFlag()) return;

        beforeAllTestsBase();
        insertSatelliteEnabledSim(SLOT_ID_0, MOCK_SIM_PROFILE_ID_TWN_CHT);
    }

    /**
     * Cleanup resources after all tests.
     * @throws Exception
     */
    @AfterClass
    public static void afterAllTests() throws Exception {
        logd(TAG, "afterAllTests");

        if (!Flags.carrierEnabledSatelliteFlag()) return;

        removeSatelliteEnabledSim(SLOT_ID_0, MOCK_SIM_PROFILE_ID_TWN_CHT);
        afterAllTestsBase();
    }

    @Before
    public void setUp() throws Exception {
        logd(TAG, "setUp()");
        if (!Flags.carrierEnabledSatelliteFlag()) return;

        assumeTrue(getContext().getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_TELEPHONY_MESSAGING));

        DefaultSmsAppHelper.stopBeingDefaultSmsApp();
    }

    @After
    public void tearDown() throws Exception {
        logd(TAG, "tearDown()");
        if (!Flags.carrierEnabledSatelliteFlag()) return;
    }

    @Test
    public void testSendMessage() throws Exception {
        logd(TAG, "testSendMessage");

        if (!Flags.carrierEnabledSatelliteFlag()) return;

        String destAddr = "1234567890";

        // Test non-default SMS app
        sendMessage(destAddr);

        // Test default SMS app
        DefaultSmsAppHelper.ensureDefaultSmsApp();
        sendMessage(destAddr);
        DefaultSmsAppHelper.stopBeingDefaultSmsApp();
    }

    @Test
    public void testReceiveMessage() throws Exception {
        logd(TAG, "testReceiveMessage");

        if (!Flags.carrierEnabledSatelliteFlag()) return;

        // Test non-default SMS app
        receiveMessage();

        // Test default SMS app
        DefaultSmsAppHelper.ensureDefaultSmsApp();
        receiveMessage();
        DefaultSmsAppHelper.stopBeingDefaultSmsApp();
    }

    @Test
    public void testSendEmergencySms() throws Exception {
        logd(TAG, "testSendEmergencySms");

        if (!Flags.carrierEnabledSatelliteFlag()) return;

        TelephonyUtils.addTestEmergencyNumber(
                InstrumentationRegistry.getInstrumentation(), TEST_EMERGENCY_NUMBER);
        sendMessage(TEST_EMERGENCY_NUMBER);

        // Test default SMS app
        DefaultSmsAppHelper.ensureDefaultSmsApp();
        sendMessage(TEST_EMERGENCY_NUMBER);
        DefaultSmsAppHelper.stopBeingDefaultSmsApp();

        TelephonyUtils.removeTestEmergencyNumber(
                InstrumentationRegistry.getInstrumentation(), TEST_EMERGENCY_NUMBER);
    }

    private void sendMessage(String destAddr) throws Exception {
        logd(TAG, "sendMessage destAddr:" + destAddr);

        // Register receiver
        SmsMmsBroadcastReceiver sendReceiver = new SmsMmsBroadcastReceiver();
        sendReceiver.setAction(SMS_SEND_ACTION);
        getContext().registerReceiver(sendReceiver,  new IntentFilter(sendReceiver.getAction()),
                Context.RECEIVER_EXPORTED_UNAUDITED);
        Intent sendIntent = new Intent(SMS_SEND_ACTION).setPackage(getContext().getPackageName());
        PendingIntent sendPendingIntent = PendingIntent.getBroadcast(getContext(), 0,
                sendIntent, PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_MUTABLE);

        try {
            getSmsManager().sendTextMessage(destAddr, null,
                    String.valueOf(SystemClock.elapsedRealtimeNanos()), sendPendingIntent,
                    null);

            assertTrue(sendReceiver.waitForBroadcast(1));
        } finally {
            getContext().unregisterReceiver(sendReceiver);
        }
    }

    private void receiveMessage() throws Exception {
        logd(TAG, "receiveMessage()");

        // Register receiver
        SmsMmsBroadcastReceiver receivedReceiver = new SmsMmsBroadcastReceiver();
        receivedReceiver.setAction(Telephony.Sms.Intents.SMS_RECEIVED_ACTION);
        getContext().registerReceiver(receivedReceiver,
                new IntentFilter(receivedReceiver.getAction()),
                Context.RECEIVER_EXPORTED_UNAUDITED);

        assertTrue(sMockModemManager.triggerIncomingSms(SLOT_ID_0));

        try {
            assertTrue(receivedReceiver.waitForBroadcast(1));
        } finally {
            getContext().unregisterReceiver(receivedReceiver);
        }
    }
}
