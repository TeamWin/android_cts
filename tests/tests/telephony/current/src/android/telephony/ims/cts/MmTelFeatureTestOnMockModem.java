/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.telephony.ims.cts;

import static android.telephony.CarrierConfigManager.ImsSs.CALL_WAITING_SYNC_NONE;
import static android.telephony.CarrierConfigManager.ImsSs.KEY_TERMINAL_BASED_CALL_WAITING_SYNC_TYPE_INT;
import static android.telephony.CarrierConfigManager.ImsSs.KEY_UT_TERMINAL_BASED_SERVICES_INT_ARRAY;
import static android.telephony.CarrierConfigManager.ImsSs.SUPPLEMENTARY_SERVICE_CW;
import static android.telephony.CarrierConfigManager.KEY_USE_CALL_WAITING_USSD_BOOL;
import static android.telephony.mockmodem.MockSimService.MOCK_SIM_PROFILE_ID_TWN_CHT;

import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertTrue;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.PersistableBundle;
import android.os.SystemProperties;
import android.telephony.CarrierConfigManager;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.telephony.ims.ImsService;
import android.telephony.ims.feature.ImsFeature;
import android.telephony.ims.stub.ImsFeatureConfiguration;
import android.telephony.mockmodem.MockModemManager;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.ShellIdentityUtils;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * CTS tests for MmTelFeature API.
 */
@RunWith(AndroidJUnit4.class)
public class MmTelFeatureTestOnMockModem {
    private static final String LOG_TAG = "MmTelFeatureTestOnMockModem";
    private static final boolean VDBG = false;

    private static final boolean DEBUG = !"user".equals(Build.TYPE);

    private static final int FEATURE_STATE_READY = 0;

    private static final String ALLOW_MOCK_MODEM_PROPERTY = "persist.radio.allow_mock_modem";

    // the timeout to wait result in milliseconds
    private static final int WAIT_UPDATE_TIMEOUT_MS = 2000;

    // the timeout to wait sim state changed in seconds
    private static final int WAIT_SIM_STATE_TIMEOUT_SEC = 3;

    private static ImsServiceConnector sServiceConnector;
    private static CarrierConfigReceiver sReceiver;
    private static MockModemManager sMockModemManager;

    private static int sTestSlot = 0;
    private static int sTestSub = SubscriptionManager.INVALID_SUBSCRIPTION_ID;

    private abstract static class BaseReceiver extends BroadcastReceiver {
        protected CountDownLatch mLatch = new CountDownLatch(1);

        void clearQueue() {
            mLatch = new CountDownLatch(1);
        }

        void waitForChanged() throws Exception {
            mLatch.await(5000, TimeUnit.MILLISECONDS);
        }
    }

    private static class CarrierConfigReceiver extends BaseReceiver {
        private final int mSubId;

        CarrierConfigReceiver(int subId) {
            mSubId = subId;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            if (CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED.equals(intent.getAction())) {
                int subId = intent.getIntExtra(CarrierConfigManager.EXTRA_SUBSCRIPTION_INDEX, -1);
                if (mSubId == subId) {
                    mLatch.countDown();
                }
            }
        }
    }

    @BeforeClass
    public static void beforeAllTests() throws Exception {
        if (VDBG) Log.d(LOG_TAG, "beforeAllTests");

        if (!hasFeature(PackageManager.FEATURE_TELEPHONY)) {
            return;
        }

        if (!ImsUtils.shouldTestImsService()) {
            return;
        }

        enforceMockModemDeveloperSetting();
        sMockModemManager = new MockModemManager();
        assertNotNull(sMockModemManager);
        assertTrue(sMockModemManager.connectMockModemService(MOCK_SIM_PROFILE_ID_TWN_CHT));

        TelephonyManager tm = (TelephonyManager) getContext()
                .getSystemService(Context.TELEPHONY_SERVICE);

        TimeUnit.SECONDS.sleep(WAIT_SIM_STATE_TIMEOUT_SEC);

        int simCardState = tm.getSimCardState();
        assertEquals(TelephonyManager.SIM_STATE_PRESENT, simCardState);

        // Check SIM state ready
        simCardState = tm.getSimState();
        assertEquals(TelephonyManager.SIM_STATE_READY, simCardState);

        sTestSub = ImsUtils.getPreferredActiveSubId();

        int[] subs = SubscriptionManager.getSubId(sTestSlot);
        for (int sub : subs) {
            if (VDBG) Log.i(LOG_TAG, "beforeAllTests sub=" + sub);
            if (SubscriptionManager.isValidSubscriptionId(sub)) {
                sTestSub = sub;
                break;
            }
        }
        if (VDBG) Log.i(LOG_TAG, "sTestSub=" + sTestSub);

        sServiceConnector = new ImsServiceConnector(InstrumentationRegistry.getInstrumentation());

        // Remove all live ImsServices until after these tests are done
        sServiceConnector.clearAllActiveImsServices(sTestSlot);

        // Configure SMS receiver based on the Android version.
        sServiceConnector.setDefaultSmsApp();

        sReceiver = new CarrierConfigReceiver(sTestSub);
        IntentFilter filter = new IntentFilter(CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED);
        // ACTION_CARRIER_CONFIG_CHANGED is sticky, so we will get a callback right away.
        InstrumentationRegistry.getInstrumentation().getContext()
                .registerReceiver(sReceiver, filter);
    }

    @AfterClass
    public static void afterAllTests() throws Exception {
        if (VDBG) Log.d(LOG_TAG, "afterAllTests");

        if (!hasFeature(PackageManager.FEATURE_TELEPHONY)) {
            return;
        }

        if (!ImsUtils.shouldTestImsService()) {
            return;
        }

        // Restore all ImsService configurations that existed before the test.
        if (sServiceConnector != null) {
            sServiceConnector.disconnectServices();
        }
        sServiceConnector = null;

        // Ensure there are no CarrierConfig overrides as well as reset the ImsResolver in case the
        // ImsService override changed in CarrierConfig while we were overriding it.
        overrideCarrierConfig(null);

        if (sReceiver != null) {
            InstrumentationRegistry.getInstrumentation().getContext().unregisterReceiver(sReceiver);
            sReceiver = null;
        }

        // Rebind all interfaces which is binding to MockModemService to default.
        if (sMockModemManager != null) {
            //assertNotNull(sMockModemManager);
            assertTrue(sMockModemManager.disconnectMockModemService());
            sMockModemManager = null;

            TimeUnit.SECONDS.sleep(WAIT_SIM_STATE_TIMEOUT_SEC);
        }
    }

    @Before
    public void beforeTest() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY));
        assumeTrue(ImsUtils.shouldTestImsService());
    }

    @After
    public void afterTest() throws Exception {
        if (VDBG) Log.d(LOG_TAG, "afterTest");

        // Unbind the ImsService after the test completes.
        if (sServiceConnector != null) {
            sServiceConnector.setSingleRegistrationTestModeEnabled(false);
            sServiceConnector.disconnectCarrierImsService();
            sServiceConnector.disconnectDeviceImsService();
        }
    }

    @Test
    public void testSetTerminalBasedCallWaitingNotSupported() throws Exception {
        if (VDBG) Log.d(LOG_TAG, "testSetTerminalBasedCallWaitingNotSupported");

        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_CALLING));

        if (!ImsUtils.shouldTestImsService()) {
            return;
        }

        PersistableBundle b = new PersistableBundle();
        b.putIntArray(KEY_UT_TERMINAL_BASED_SERVICES_INT_ARRAY, new int[] {});
        overrideCarrierConfig(b);

        // Connect to device ImsService with MmTelFeature
        triggerFrameworkConnectToCarrierImsService(
                ImsService.CAPABILITY_TERMINAL_BASED_CALL_WAITING);

        sServiceConnector.getCarrierService().getMmTelFeature()
                .getTerminalBasedCallWaitingLatch().await(WAIT_UPDATE_TIMEOUT_MS,
                        TimeUnit.MILLISECONDS);

        assertFalse(sServiceConnector.getCarrierService()
                .getMmTelFeature().isTerminalBasedCallWaitingNotified());

        sServiceConnector.getCarrierService().getMmTelFeature()
                .resetTerminalBasedCallWaitingLatch();
    }

    @Test
    public void testSetTerminalBasedCallWaiting() throws Exception {
        if (VDBG) Log.d(LOG_TAG, "testSetTerminalBasedCallWaiting");

        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_CALLING));

        if (!ImsUtils.shouldTestImsService()) {
            return;
        }

        PersistableBundle b = new PersistableBundle();
        b.putBoolean(KEY_USE_CALL_WAITING_USSD_BOOL, false);
        b.putInt(KEY_TERMINAL_BASED_CALL_WAITING_SYNC_TYPE_INT, CALL_WAITING_SYNC_NONE);
        b.putIntArray(KEY_UT_TERMINAL_BASED_SERVICES_INT_ARRAY,
                new int[] { SUPPLEMENTARY_SERVICE_CW });
        overrideCarrierConfig(b);

        // Connect to device ImsService with MmTelFeature
        triggerFrameworkConnectToCarrierImsService(
                ImsService.CAPABILITY_TERMINAL_BASED_CALL_WAITING);

        sServiceConnector.getCarrierService().getMmTelFeature()
                .getTerminalBasedCallWaitingLatch().await(WAIT_UPDATE_TIMEOUT_MS,
                        TimeUnit.MILLISECONDS);

        assertTrue(sServiceConnector.getCarrierService()
                .getMmTelFeature().isTerminalBasedCallWaitingNotified());

        boolean enabled = sServiceConnector.getCarrierService()
                .getMmTelFeature().isTerminalBasedCallWaitingEnabled();

        sServiceConnector.getCarrierService().getMmTelFeature()
                .resetTerminalBasedCallWaitingLatch();

        TelephonyManager tm = (TelephonyManager) getContext()
                .getSystemService(Context.TELEPHONY_SERVICE);
        tm = tm.createForSubscriptionId(sTestSub);

        boolean expected = !enabled;
        final LinkedBlockingQueue<Integer> resultQueue = new LinkedBlockingQueue<>();
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(tm,
                (m) -> m.setCallWaitingEnabled(expected,
                        (r) -> r.run(), (result) -> resultQueue.offer(result)),
                "android.permission.MODIFY_PHONE_STATE");
        int result = waitForIntResult(resultQueue);

        if (result == TelephonyManager.CALL_WAITING_STATUS_ENABLED
                || result == TelephonyManager.CALL_WAITING_STATUS_DISABLED) {
            sServiceConnector.getCarrierService().getMmTelFeature()
                    .getTerminalBasedCallWaitingLatch().await(WAIT_UPDATE_TIMEOUT_MS,
                            TimeUnit.MILLISECONDS);

            assertTrue(sServiceConnector.getCarrierService()
                    .getMmTelFeature().isTerminalBasedCallWaitingNotified());

            enabled = sServiceConnector.getCarrierService()
                    .getMmTelFeature().isTerminalBasedCallWaitingEnabled();

            assertEquals(expected, enabled);

            sServiceConnector.getCarrierService().getMmTelFeature()
                    .resetTerminalBasedCallWaitingLatch();
        } else {
            fail("setCallWaitingEnabled failed");
        }
    }

    private void triggerFrameworkConnectToCarrierImsService(long capabilities) throws Exception {
        assertTrue(sServiceConnector.connectCarrierImsServiceLocally());
        sServiceConnector.getCarrierService().addCapabilities(capabilities);
        // Connect to the ImsService with the MmTel feature.
        assertTrue(sServiceConnector.triggerFrameworkConnectionToCarrierImsService(
                new ImsFeatureConfiguration.Builder()
                .addFeature(sTestSlot, ImsFeature.FEATURE_MMTEL)
                .build()));
        // The MmTelFeature is created when the ImsService is bound. If it wasn't created, then the
        // Framework did not call it.
        assertTrue("Did not receive createMmTelFeature", sServiceConnector.getCarrierService()
                .waitForLatchCountdown(TestImsService.LATCH_CREATE_MMTEL));
        assertTrue("Did not receive MmTelFeature#onReady", sServiceConnector.getCarrierService()
                .waitForLatchCountdown(TestImsService.LATCH_MMTEL_READY));
        assertNotNull("ImsService created, but ImsService#createMmTelFeature was not called!",
                sServiceConnector.getCarrierService().getMmTelFeature());
        int serviceSlot = sServiceConnector.getCarrierService().getMmTelFeature().getSlotIndex();
        assertEquals("The slot specified for the test (" + sTestSlot + ") does not match the "
                        + "assigned slot (" + serviceSlot + "+ for the associated MmTelFeature",
                sTestSlot, serviceSlot);
    }

    private int waitForIntResult(LinkedBlockingQueue<Integer> queue) throws Exception {
        Integer result = queue.poll(ImsUtils.TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        return result != null ? result : Integer.MAX_VALUE;
    }

    private int waitForIntResult(LinkedBlockingQueue<Integer> queue, int timeout)
            throws Exception {
        Integer result = queue.poll(timeout, TimeUnit.MILLISECONDS);
        return result != null ? result : Integer.MAX_VALUE;
    }

    private static void overrideCarrierConfig(PersistableBundle bundle) throws Exception {
        CarrierConfigManager carrierConfigManager = InstrumentationRegistry.getInstrumentation()
                .getContext().getSystemService(CarrierConfigManager.class);
        if (sReceiver != null) sReceiver.clearQueue();
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(carrierConfigManager,
                (m) -> m.overrideConfig(sTestSub, bundle));
        if (sReceiver != null) sReceiver.waitForChanged();
    }

    private static Context getContext() {
        return InstrumentationRegistry.getInstrumentation().getContext();
    }

    /** Checks whether the system feature is supported. */
    private static boolean hasFeature(String feature) {
        final PackageManager pm = getContext().getPackageManager();
        if (!pm.hasSystemFeature(feature)) {
            Log.d(LOG_TAG, "Skipping test that requires " + feature);
            return false;
        }
        return true;
    }

    private static void enforceMockModemDeveloperSetting() throws Exception {
        boolean isAllowed = SystemProperties.getBoolean(ALLOW_MOCK_MODEM_PROPERTY, false);
        // Check for developer settings for user build. Always allow for debug builds
        if (!isAllowed && !DEBUG) {
            throw new IllegalStateException(
                "!! Enable Mock Modem before running this test !! "
                    + "Developer options => Allow Mock Modem");
        }
    }
}
