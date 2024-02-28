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

import static junit.framework.Assert.assertTrue;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import android.annotation.NonNull;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.PersistableBundle;
import android.telephony.CarrierConfigManager;
import android.telephony.Rlog;
import android.telephony.ServiceState;
import android.telephony.SmsManager;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyCallback;
import android.telephony.TelephonyManager;
import android.telephony.mockmodem.MockModemConfigBase;
import android.telephony.mockmodem.MockModemManager;

import androidx.test.InstrumentationRegistry;

import com.android.compatibility.common.util.CarrierPrivilegeUtils;
import com.android.compatibility.common.util.ShellIdentityUtils;
import com.android.internal.annotations.GuardedBy;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class CarrierRoamingSatelliteTestBase {
    private static final String TAG = "CarrierRoamingSatelliteTestBase";
    protected static final long TIMEOUT = TimeUnit.SECONDS.toMillis(5);
    protected static final int SLOT_ID_0 = 0;
    protected static final int SLOT_ID_1 = 1;

    protected static MockModemManager sMockModemManager;
    protected static TelephonyManager sTelephonyManager;
    protected static SubscriptionManager sSubscriptionManager;
    private static CarrierConfigReceiver sCarrierConfigReceiver;

    protected static void beforeAllTestsBase() throws Exception {
        logd(TAG, "beforeAllTestsBase");

        MockModemManager.enforceMockModemDeveloperSetting();
        sMockModemManager = new MockModemManager();
        assertNotNull(sMockModemManager);
        assertTrue(sMockModemManager.connectMockModemService());

        sTelephonyManager = getContext().getSystemService(TelephonyManager.class);
        sSubscriptionManager = getContext().getSystemService(SubscriptionManager.class);

        sCarrierConfigReceiver = new CarrierConfigReceiver();
        IntentFilter filter = new IntentFilter(CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED);
        // ACTION_CARRIER_CONFIG_CHANGED is sticky, so we will get a callback right away.
        getContext().registerReceiver(sCarrierConfigReceiver, filter);
    }

    protected static void afterAllTestsBase() throws Exception {
        logd(TAG, "afterAllTestsBase");
        assertTrue(sMockModemManager.disconnectMockModemService());
        sMockModemManager = null;
        sTelephonyManager = null;
        sSubscriptionManager = null;

        if (sCarrierConfigReceiver != null) {
            getContext().unregisterReceiver(sCarrierConfigReceiver);
            sCarrierConfigReceiver = null;
        }
    }

    private static class CarrierConfigReceiver extends BroadcastReceiver {
        private final Semaphore mSemaphore = new Semaphore(0);
        private final Object mSubIdLock = new Object();
        @GuardedBy("mSubIdLock")
        private int mSubId;

        @Override
        public void onReceive(Context context, Intent intent) {
            if (CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED.equals(intent.getAction())) {
                int subId = intent.getIntExtra(CarrierConfigManager.EXTRA_SUBSCRIPTION_INDEX, -1);
                logd(TAG, "CarrierConfigReceiver onReceive() subId:" + subId);
                synchronized (mSubIdLock) {
                    if (mSubId == subId) {
                        mSemaphore.release();
                    }
                }
            }
        }

        public void setSubId(int subId) {
            synchronized (mSubIdLock) {
                logd(TAG, "CarrierConfigReceiver setSubId() subId:" + subId);
                mSubId = subId;
                mSemaphore.drainPermits();
            }
        }

        public boolean waitForCarrierConfigChanged() {
            logd(TAG, "CarrierConfigReceiver waitForCarrierConfigChanged()");
            try {
                if (!mSemaphore.tryAcquire(TIMEOUT, TimeUnit.MILLISECONDS)) {
                    loge(TAG, "Timeout to receive ACTION_CARRIER_CONFIG_CHANGED");
                    return false;
                }
            } catch (Exception e) {
                loge(TAG, "CarrierConfigReceiver waitForCarrierConfigChanged: Got exception=" + e);
            }
            return true;
        }
    }

    private static class ServiceStateListenerTest extends TelephonyCallback
            implements TelephonyCallback.ServiceStateListener {

        private final Semaphore mNonTerrestrialNetworkSemaphore = new Semaphore(0);

        @Override
        public void onServiceStateChanged(ServiceState serviceState) {
            logd(TAG, "onServiceStateChanged: serviceState=" + serviceState);

            try {
                if (serviceState.isUsingNonTerrestrialNetwork()) {
                    mNonTerrestrialNetworkSemaphore.release();
                }
            } catch (Exception e) {
                loge(TAG, "onServiceStateChanged: Got exception=" + e);
            }
        }

        public boolean waitForNonTerrestrialNetworkConnection() {
            try {
                if (!mNonTerrestrialNetworkSemaphore.tryAcquire(TIMEOUT, TimeUnit.MILLISECONDS)) {
                    loge(TAG, "Timeout to connect to non-terrestrial network");
                    return false;
                }
            } catch (Exception e) {
                loge(TAG, "ServiceStateListenerTest waitForNonTerrestrialNetworkConnection: "
                        + "Got exception=" + e);
                return false;
            }
            return true;
        }

        public void clearServiceStateChanges() {
            logd(TAG, "clearServiceStateChanges()");
            mNonTerrestrialNetworkSemaphore.drainPermits();
        }
    }

    protected static class SmsMmsBroadcastReceiver extends BroadcastReceiver {
        private final Semaphore mSemaphore = new Semaphore(0);
        private final Object mActionLock = new Object();
        @GuardedBy("mActionLock")
        private String mAction;

        public void setAction(String action) {
            synchronized (mActionLock) {
                mAction = action;
                mSemaphore.drainPermits();
            }
        }

        public String getAction() {
            synchronized (mActionLock) {
                return mAction;
            }
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            synchronized (mActionLock) {
                logd(TAG, "onReceive: " + intent.getAction());
                if (intent.getAction().equals(mAction)) {
                    mSemaphore.release();
                }
            }
        }

        public boolean waitForBroadcast(int expectedNumberOfEvents) {
            logd(TAG, "waitForBroadcast()");
            for (int i = 0; i < expectedNumberOfEvents; i++) {
                try {
                    if (!mSemaphore.tryAcquire(TIMEOUT, TimeUnit.MILLISECONDS)) {
                        loge(TAG, "Timeout to receive sms/mms broadcast");
                        return false;
                    }
                } catch (Exception ex) {
                    loge(TAG, "waitForBroadcast: Got exception=" + ex);
                    return false;
                }
            }
            return true;
        }
    }

    private static void overrideCarrierConfig(int subId, PersistableBundle bundle)
            throws Exception {
        logd(TAG, "overrideCarrierConfig() subId:" + subId);
        try {
            CarrierConfigManager carrierConfigManager = InstrumentationRegistry.getInstrumentation()
                    .getContext().getSystemService(CarrierConfigManager.class);
            sCarrierConfigReceiver.setSubId(subId);
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(carrierConfigManager,
                    (m) -> m.overrideConfig(subId, bundle));
            assertTrue(sCarrierConfigReceiver.waitForCarrierConfigChanged());
        } catch (Exception ex) {
            loge(TAG, "overrideCarrierConfig(), ex=" + ex);
        }
    }

    protected static void insertSatelliteEnabledSim(int slotId, int profile) throws Exception {
        logd(TAG, "insertSatelliteEnabledSim() slotId:" + slotId);

        // Register service state listener
        ServiceStateListenerTest serviceStateListener = new ServiceStateListenerTest();
        serviceStateListener.clearServiceStateChanges();
        sTelephonyManager.registerTelephonyCallback(getContext().getMainExecutor(),
                serviceStateListener);

        assertTrue(sMockModemManager.insertSimCard(slotId, profile));
        TimeUnit.MILLISECONDS.sleep(TIMEOUT);

        int subId = SubscriptionManager.getSubscriptionId(slotId);

        String mccmnc = sMockModemManager.getSimInfo(slotId,
                MockModemConfigBase.SimInfoChangedResult.SIM_INFO_TYPE_MCC_MNC);

        // Set phone number
        setPhoneNumber(subId);

        // Override carrier config
        PersistableBundle bundle = new PersistableBundle();
        bundle.putBoolean(
                CarrierConfigManager.KEY_SATELLITE_ATTACH_SUPPORTED_BOOL, true);
        PersistableBundle plmnBundle = new PersistableBundle();
        int[] intArray1 = {3, 5};
        plmnBundle.putIntArray(mccmnc, intArray1);
        bundle.putPersistableBundle(
                CarrierConfigManager.KEY_CARRIER_SUPPORTED_SATELLITE_SERVICES_PER_PROVIDER_BUNDLE,
                plmnBundle);
        overrideCarrierConfig(subId, bundle);

        // Enter service
        sMockModemManager.changeNetworkService(slotId, profile, true);

        assertTrue(serviceStateListener.waitForNonTerrestrialNetworkConnection());
    }

    protected static void removeSatelliteEnabledSim(int slotId, int profile) throws Exception {
        logd(TAG, "removeSatelliteEnabledSim");
        int subId = SubscriptionManager.getSubscriptionId(slotId);

        overrideCarrierConfig(subId, null);

        // Leave service
        sMockModemManager.changeNetworkService(slotId, profile, false);

        // Remove the SIM
        sMockModemManager.removeSimCard(slotId);
    }

    private static void setPhoneNumber(int subId) throws Exception {
        final String carrierNumber = "1234567890";
        CarrierPrivilegeUtils.withCarrierPrivileges(
                InstrumentationRegistry.getContext(),
                subId,
                () -> {
                    sSubscriptionManager.setCarrierPhoneNumber(subId, carrierNumber);
                    assertEquals(
                            carrierNumber,
                            sSubscriptionManager.getPhoneNumber(
                                    subId, SubscriptionManager.PHONE_NUMBER_SOURCE_CARRIER));
                });
    }

    protected static Context getContext() {
        return InstrumentationRegistry.getContext();
    }

    protected SmsManager getSmsManager() {
        return SmsManager.getDefault();
    }

    /**
     * Adopts shell permission identity
     */
    protected static void adoptShellIdentity() {
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .adoptShellPermissionIdentity();
    }

    /**
     * Drop shell permission identity
     */
    protected static void dropShellIdentity() {
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .dropShellPermissionIdentity();
    }

    protected static void logd(@NonNull String tag, @NonNull String log) {
        Rlog.d(tag, log);
    }

    protected static void loge(@NonNull String tag, @NonNull String log) {
        Rlog.e(tag, log);
    }
}
