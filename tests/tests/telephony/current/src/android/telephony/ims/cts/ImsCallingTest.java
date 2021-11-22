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

package android.telephony.ims.cts;

import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertTrue;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import android.app.Instrumentation;
import android.app.UiAutomation;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.telecom.Call;
import android.telecom.PhoneAccount;
import android.telecom.TelecomManager;
import android.telephony.CarrierConfigManager;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.telephony.cts.InCallServiceStateValidator;
import android.telephony.cts.InCallServiceStateValidator.InCallServiceCallbacks;
import android.telephony.cts.TelephonyUtils;
import android.telephony.ims.feature.ImsFeature;
import android.telephony.ims.feature.MmTelFeature;
import android.telephony.ims.stub.ImsFeatureConfiguration;
import android.telephony.ims.stub.ImsRegistrationImplBase;
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

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * CTS tests for ImsCall .
 */
@RunWith(AndroidJUnit4.class)
public class ImsCallingTest {

    private static ImsServiceConnector sServiceConnector;

    private static final String LOG_TAG = "CtsImsCallingTest";
    private static final String PACKAGE = "android.telephony.ims.cts";
    private static final String PACKAGE_CTS_DIALER = "android.telephony.cts";
    private static final String COMMAND_SET_DEFAULT_DIALER = "telecom set-default-dialer ";
    private static final String COMMAND_GET_DEFAULT_DIALER = "telecom get-default-dialer";

    public static final int WAIT_FOR_SERVICE_TO_UNBOUND = 40000;
    public static final int WAIT_FOR_CONDITION = 3000;
    public static final int WAIT_FOR_CALL_STATE = 10000;
    public static final int LATCH_WAIT = 0;
    public static final int LATCH_INCALL_SERVICE_BOUND = 1;
    public static final int LATCH_INCALL_SERVICE_UNBOUND = 2;
    public static final int LATCH_IS_ON_CALL_ADDED = 3;
    public static final int LATCH_IS_ON_CALL_REMOVED = 4;
    public static final int LATCH_IS_CALL_DIALING = 5;
    public static final int LATCH_IS_CALL_ACTIVE = 6;
    public static final int LATCH_IS_CALL_DISCONNECTING = 7;
    public static final int LATCH_IS_CALL_DISCONNECTED = 8;
    public static final int LATCH_MAX = 9;

    private static boolean sIsBound = false;
    private static int sCounter = 5553639;
    private static int sTestSlot = 0;
    private static int sTestSub = SubscriptionManager.INVALID_SUBSCRIPTION_ID;
    private static long sPreviousOptInStatus = 0;
    private static long sPreviousEn4GMode = 0;
    private static String sPreviousDefaultDialer;

    private static CarrierConfigReceiver sReceiver;
    private static SubscriptionManager sSubcriptionManager;

    private final Object mLock = new Object();
    private InCallServiceCallbacks mServiceCallBack;
    private Context mContext;
    private ConcurrentHashMap<String, Call> mCalls = new ConcurrentHashMap<String, Call>();
    private String mCurrentCallId = null;

    private static final CountDownLatch[] sLatches = new CountDownLatch[LATCH_MAX];
    static {
        for (int i = 0; i < LATCH_MAX; i++) {
            sLatches[i] = new CountDownLatch(1);
        }
    }

    public boolean callingTestLatchCountdown(int latchIndex, int waitMs) {
        boolean complete = false;
        try {
            CountDownLatch latch;
            synchronized (mLock) {
                latch = sLatches[latchIndex];
            }
            complete = latch.await(waitMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
             //complete == false
        }
        synchronized (mLock) {
            sLatches[latchIndex] = new CountDownLatch(1);
        }
        return complete;
    }

    public void countDownLatch(int latchIndex) {
        synchronized (mLock) {
            sLatches[latchIndex].countDown();
        }
    }

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

    public interface Condition {
        Object expected();
        Object actual();
    }

    void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (Exception e) {
            Log.d(LOG_TAG, "InterruptedException");
        }
    }

    void waitUntilConditionIsTrueOrTimeout(Condition condition, long timeout,
            String description) {
        final long start = System.currentTimeMillis();
        while (!Objects.equals(condition.expected(), condition.actual())
                && System.currentTimeMillis() - start < timeout) {
            sleep(50);
        }
        assertEquals(description, condition.expected(), condition.actual());
    }

    @BeforeClass
    public static void beforeAllTests() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }

        TelephonyManager tm = (TelephonyManager) getContext()
                .getSystemService(Context.TELEPHONY_SERVICE);
        sTestSub = ImsUtils.getPreferredActiveSubId();
        sTestSlot = SubscriptionManager.getSlotIndex(sTestSub);
        if (tm.getSimState(sTestSlot) != TelephonyManager.SIM_STATE_READY) {
            return;
        }

        sServiceConnector = new ImsServiceConnector(InstrumentationRegistry.getInstrumentation());
        // Remove all live ImsServices until after these tests are done
        sServiceConnector.clearAllActiveImsServices(sTestSlot);

        sReceiver = new CarrierConfigReceiver(sTestSub);
        IntentFilter filter = new IntentFilter(CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED);
        // ACTION_CARRIER_CONFIG_CHANGED is sticky, so we will get a callback right away.
        InstrumentationRegistry.getInstrumentation().getContext()
                .registerReceiver(sReceiver, filter);

        UiAutomation ui = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        try {
            ui.adoptShellPermissionIdentity();
            // Get the default dialer and save it to restore after test ends.
            sPreviousDefaultDialer = getDefaultDialer(InstrumentationRegistry.getInstrumentation());
            // Set dialer as "android.telephony.cts"
            setDefaultDialer(InstrumentationRegistry.getInstrumentation(), PACKAGE_CTS_DIALER);

            sSubcriptionManager = InstrumentationRegistry.getInstrumentation()
                    .getContext().getSystemService(SubscriptionManager.class);
            // Get the default Subscription values and save it to restore after test ends.
            sPreviousOptInStatus = sSubcriptionManager.getLongSubscriptionProperty(sTestSub,
                        SubscriptionManager.VOIMS_OPT_IN_STATUS, 0, getContext());
            sPreviousEn4GMode = sSubcriptionManager.getLongSubscriptionProperty(sTestSub,
                        SubscriptionManager.ENHANCED_4G_MODE_ENABLED, 0, getContext());
            // Set the new Sunbscription values
            sSubcriptionManager.setSubscriptionProperty(sTestSub,
                    SubscriptionManager.VOIMS_OPT_IN_STATUS, String.valueOf(1));
            sSubcriptionManager.setSubscriptionProperty(sTestSub,
                    SubscriptionManager.ENHANCED_4G_MODE_ENABLED, String.valueOf(1));

            //Override the carrier configurartions
            CarrierConfigManager configurationManager = InstrumentationRegistry.getInstrumentation()
                    .getContext().getSystemService(CarrierConfigManager.class);
            PersistableBundle bundle = new PersistableBundle(1);
            bundle.putBoolean(CarrierConfigManager.KEY_CARRIER_VOLTE_AVAILABLE_BOOL, true);
            bundle.putBoolean(CarrierConfigManager.KEY_ENHANCED_4G_LTE_ON_BY_DEFAULT_BOOL, true);
            bundle.putBoolean(CarrierConfigManager.KEY_EDITABLE_ENHANCED_4G_LTE_BOOL, false);
            bundle.putBoolean(CarrierConfigManager.KEY_CARRIER_VOLTE_TTY_SUPPORTED_BOOL, true);
            bundle.putBoolean(CarrierConfigManager.KEY_CARRIER_IMS_GBA_REQUIRED_BOOL , false);

            sReceiver.clearQueue();
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(configurationManager,
                    (m) -> m.overrideConfig(sTestSub, bundle));
        } finally {
            ui.dropShellPermissionIdentity();
        }
        sReceiver.waitForChanged();
    }

    @AfterClass
    public static void afterAllTests() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }

        UiAutomation ui = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        try {
            ui.adoptShellPermissionIdentity();
            // Set the default Sunbscription values.
            sSubcriptionManager.setSubscriptionProperty(sTestSub,
                    SubscriptionManager.VOIMS_OPT_IN_STATUS, String.valueOf(sPreviousOptInStatus));
            sSubcriptionManager.setSubscriptionProperty(sTestSub,
                    SubscriptionManager.ENHANCED_4G_MODE_ENABLED, String.valueOf(
                    sPreviousEn4GMode));
            // Set default dialer
            setDefaultDialer(InstrumentationRegistry.getInstrumentation(), sPreviousDefaultDialer);

            // Restore all ImsService configurations that existed before the test.
            if (sServiceConnector != null && sIsBound) {
                sServiceConnector.disconnectServices();
                sIsBound = false;
            }
            sServiceConnector = null;
            overrideCarrierConfig(null);

            if (sReceiver != null) {
                InstrumentationRegistry.getInstrumentation().getContext()
                        .unregisterReceiver(sReceiver);
                sReceiver = null;
            }
        } finally {
            ui.dropShellPermissionIdentity();
        }
    }

    @Before
    public void beforeTest() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }
        TelephonyManager tm = (TelephonyManager) InstrumentationRegistry.getInstrumentation()
                .getContext().getSystemService(Context.TELEPHONY_SERVICE);
        if (tm.getSimState(sTestSlot) != TelephonyManager.SIM_STATE_READY) {
            fail("This test requires that there is a SIM in the device!");
        }
        // Correctness check: ensure that the subscription hasn't changed between tests.
        int[] subs = SubscriptionManager.getSubId(sTestSlot);

        if (subs == null) {
            fail("This test requires there is an active subscription in slot " + sTestSlot);
        }
        boolean isFound = false;
        for (int sub : subs) {
            isFound |= (sTestSub == sub);
        }
        if (!isFound) {
            fail("Invalid state found: the test subscription in slot " + sTestSlot + " changed "
                    + "during this test.");
        }
        // Connect to the ImsService with the MmTel feature.
        assertTrue(sServiceConnector.connectCarrierImsService(new ImsFeatureConfiguration.Builder()
                .addFeature(sTestSlot, ImsFeature.FEATURE_MMTEL)
                .build()));
        sIsBound = true;
        // The MmTelFeature is created when the ImsService is bound. If it wasn't created, then the
        // Framework did not call it.
        sServiceConnector.getCarrierService().waitForLatchCountdown(
                TestImsService.LATCH_CREATE_MMTEL);
        assertNotNull("ImsService created, but ImsService#createMmTelFeature was not called!",
                sServiceConnector.getCarrierService().getMmTelFeature());

        sServiceConnector.getCarrierService().waitForLatchCountdown(
                TestImsService.LATCH_MMTEL_CAP_SET);

        MmTelFeature.MmTelCapabilities capabilities = new MmTelFeature.MmTelCapabilities(
                MmTelFeature.MmTelCapabilities.CAPABILITY_TYPE_VOICE);
        // Set Registered and VoLTE capable
        sServiceConnector.getCarrierService().getImsService().getRegistration(0).onRegistered(
                ImsRegistrationImplBase.REGISTRATION_TECH_LTE);
        sServiceConnector.getCarrierService().getMmTelFeature().setCapabilities(capabilities);
        sServiceConnector.getCarrierService().getMmTelFeature()
                .notifyCapabilitiesStatusChanged(capabilities);

        // Wait a second for the notifyCapabilitiesStatusChanged indication to be processed on the
        // main telephony thread - currently no better way of knowing that telephony has processed
        // this command. SmsManager#isImsSmsSupported() is @hide and must be updated to use new API.
        Thread.sleep(3000);
    }

    @After
    public void afterTest() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }

        if (!mCalls.isEmpty() && (mCurrentCallId != null)) {
            Call call = mCalls.get(mCurrentCallId);
            call.disconnect();
        }

        if (sServiceConnector != null && sIsBound) {
            sServiceConnector.disconnectCarrierImsService();
            sServiceConnector.disconnectServices();
            sIsBound = false;
        }
    }

    @Test
    public void testOutGoingCall() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }
        mServiceCallBack = new ServiceCallBack();
        InCallServiceStateValidator.setCallbacks(mServiceCallBack);

        TelecomManager telecomManager = (TelecomManager) InstrumentationRegistry
                .getInstrumentation().getContext().getSystemService(Context.TELECOM_SERVICE);

        final Uri imsUri = Uri.fromParts(PhoneAccount.SCHEME_TEL, String.valueOf(++sCounter), null);
        Bundle extras = new Bundle();

        // Place outgoing call
        telecomManager.placeCall(imsUri, extras);
        assertTrue(callingTestLatchCountdown(LATCH_IS_ON_CALL_ADDED, WAIT_FOR_CALL_STATE));

        Call call = null;
        if (mCurrentCallId != null) {
            call = getCall(mCurrentCallId);
        }

        assertTrue(callingTestLatchCountdown(LATCH_IS_CALL_DIALING, WAIT_FOR_CALL_STATE));
        TestImsCallSessionImpl mCallSession = mCallSession =
                sServiceConnector.getCarrierService().getMmTelFeature().getImsCallsession();

        if ((call != null) && (mCallSession != null)) {
            isCallActive(call, mCallSession);
        }
        assertTrue(callingTestLatchCountdown(LATCH_IS_CALL_DISCONNECTING, WAIT_FOR_CALL_STATE));

        if ((call != null) && (mCallSession != null)) {
            isCallDisconnected(call, mCallSession);
        }
        assertTrue(callingTestLatchCountdown(LATCH_IS_ON_CALL_REMOVED, WAIT_FOR_CALL_STATE));

        waitUntilConditionIsTrueOrTimeout(
                new Condition() {
                    @Override
                    public Object expected() {
                        return true;
                    }

                    @Override
                    public Object actual() {
                        InCallServiceStateValidator inCallService = mServiceCallBack.getService();
                        return (inCallService.isServiceUnBound()) ? true : false;
                    }
                }, WAIT_FOR_SERVICE_TO_UNBOUND, "Service Unbound");
    }

    public void isCallActive(Call call, TestImsCallSessionImpl callsession) {
        assertTrue(callingTestLatchCountdown(LATCH_IS_CALL_ACTIVE, WAIT_FOR_CALL_STATE));
        waitUntilConditionIsTrueOrTimeout(
                new Condition() {
                    @Override
                    public Object expected() {
                        return true;
                    }

                    @Override
                    public Object actual() {
                        return (callsession.isInCall()) ? true : false;
                    }
                }, WAIT_FOR_CONDITION, "Call Active");

        callingTestLatchCountdown(LATCH_WAIT, 5000);
        call.disconnect();
    }

    public void isCallDisconnected(Call call, TestImsCallSessionImpl callsession) {
        assertTrue(callingTestLatchCountdown(LATCH_IS_CALL_DISCONNECTED, WAIT_FOR_CALL_STATE));
        waitUntilConditionIsTrueOrTimeout(
                new Condition() {
                    @Override
                    public Object expected() {
                        return true;
                    }

                    @Override
                    public Object actual() {
                        return (callsession.isInTerminated()) ? true : false;
                    }
                }, WAIT_FOR_CONDITION, "Call Disconnected");
    }

    private void setCallID(String callid) {
        mCurrentCallId = callid;
    }

    public void addCall(Call call) {
        String callid = getCallId(call);
        setCallID(callid);
        synchronized (mCalls) {
            mCalls.put(callid, call);
        }
    }

    public String getCallId(Call call) {
        String str = call.toString();
        String[] arrofstr = str.split(",", 3);
        int index = arrofstr[0].indexOf(":");
        String callId = arrofstr[0].substring(index + 1);
        return callId;
    }

    public Call getCall(String callId) {
        synchronized (mCalls) {
            if (mCalls.isEmpty()) {
                return null;
            }

            for (Map.Entry<String, Call> entry : mCalls.entrySet()) {
                if (entry.getKey().equals(callId)) {
                    return entry.getValue();
                }
            }
        }
        return null;
    }

    private void removeCall(Call call) {
        if (mCalls.isEmpty()) {
            return;
        }

        String callid = getCallId(call);
        Map.Entry<String, Call>[] entries = mCalls.entrySet().toArray(new Map.Entry[mCalls.size()]);
        for (Map.Entry<String, Call> entry : entries) {
            if (entry.getKey().equals(callid)) {
                mCalls.remove(entry.getKey());
                setCallID(null);
            }
        }
    }

    class ServiceCallBack extends InCallServiceCallbacks {

        @Override
        public void onCallAdded(Call call, int numCalls) {
            Log.i(LOG_TAG, "onCallAdded, Call: " + call + ", Num Calls: " + numCalls);
            addCall(call);
            countDownLatch(LATCH_IS_ON_CALL_ADDED);
        }

        @Override
        public void onCallRemoved(Call call, int numCalls) {
            Log.i(LOG_TAG, "onCallRemoved, Call: " + call + ", Num Calls: " + numCalls);
            removeCall(call);
            countDownLatch(LATCH_IS_ON_CALL_REMOVED);
        }

        @Override
        public void onCallStateChanged(Call call, int state) {
            Log.i(LOG_TAG, "onCallStateChanged " + state + "Call: " + call);

            switch(state) {
                case Call.STATE_DIALING : {
                    countDownLatch(LATCH_IS_CALL_DIALING);
                    break;
                }
                case Call.STATE_ACTIVE : {
                    countDownLatch(LATCH_IS_CALL_ACTIVE);
                    break;
                }
                case Call.STATE_DISCONNECTING : {
                    countDownLatch(LATCH_IS_CALL_DISCONNECTING);
                    break;
                }
                case Call.STATE_DISCONNECTED : {
                    countDownLatch(LATCH_IS_CALL_DISCONNECTED);
                    break;
                }
                default:
                    break;
            }
        }
    }

    private static Context getContext() {
        return InstrumentationRegistry.getInstrumentation().getContext();
    }

    private static String setDefaultDialer(Instrumentation instrumentation, String packageName)
            throws Exception {
        String str =  TelephonyUtils.executeShellCommand(instrumentation, COMMAND_SET_DEFAULT_DIALER
                + packageName);
        return str;
    }

    private static String getDefaultDialer(Instrumentation instrumentation) throws Exception {
        String str = TelephonyUtils.executeShellCommand(instrumentation,
                COMMAND_GET_DEFAULT_DIALER);
        return str;
    }

    private static void overrideCarrierConfig(PersistableBundle bundle) throws Exception {
        CarrierConfigManager carrierConfigManager = InstrumentationRegistry.getInstrumentation()
                .getContext().getSystemService(CarrierConfigManager.class);
        sReceiver.clearQueue();
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(carrierConfigManager,
                (m) -> m.overrideConfig(sTestSub, bundle));
        sReceiver.waitForChanged();
    }

}
