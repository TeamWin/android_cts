/*
 * Copyright (C) 2020 The Android Open Source Project
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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.PersistableBundle;
import android.telephony.CarrierConfigManager;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.telephony.ims.ImsException;
import android.telephony.ims.ImsManager;
import android.telephony.ims.ImsService;
import android.telephony.ims.SipDelegateManager;
import android.telephony.ims.feature.ImsFeature;
import android.telephony.ims.stub.ImsFeatureConfiguration;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.ShellIdentityUtils;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * CTS tests for {@link SipDelegateManager} API.
 */
@RunWith(AndroidJUnit4.class)
public class SipDelegateManagerTest {

    private static int sTestSlot = 0;
    private static int sTestSub = SubscriptionManager.INVALID_SUBSCRIPTION_ID;
    private static ImsServiceConnector sServiceConnector;
    private static CarrierConfigReceiver sReceiver;

    private static class CarrierConfigReceiver extends BroadcastReceiver {
        private CountDownLatch mLatch = new CountDownLatch(1);
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

        void clearQueue() {
            mLatch = new CountDownLatch(1);
        }

        void waitForCarrierConfigChanged() throws Exception {
            mLatch.await(5000, TimeUnit.MILLISECONDS);
        }
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
    }

    @AfterClass
    public static void afterAllTests() throws Exception {
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
    }

    @Before
    public void beforeTest() {
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
    }

    @After
    public void afterTest() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }
        // Unbind the ImsService after the test completes.
        if (sServiceConnector != null) {
            sServiceConnector.disconnectCarrierImsService();
        }
    }

    @Test
    public void testIncorrectPermissions() throws Exception {
        SipDelegateManager sipDelegateManager = getSipDelegateManager();
        try {
            sipDelegateManager.isSupported();
            fail("isSupported requires READ_PRIVILEGED_PHONE_STATE");
        } catch (SecurityException e) {
            //expected
        }
    }

    @Test
    public void testIsSupportedWithSipTransportCapable() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }
        PersistableBundle b = new PersistableBundle();
        b.putBoolean(CarrierConfigManager.Ims.KEY_IMS_SINGLE_REGISTRATION_REQUIRED_BOOL, true);
        overrideCarrierConfig(b);
        connectTestImsServiceWithSipTransport();

        SipDelegateManager manager = getSipDelegateManager();
        Boolean result = null;
        try {
            result = callUntilImsServiceIsAvailable(() ->
                    ShellIdentityUtils.invokeThrowableMethodWithShellPermissions(manager,
                            SipDelegateManager::isSupported, ImsException.class,
                            "android.permission.READ_PRIVILEGED_PHONE_STATE"));
        } catch (SecurityException e) {
            fail("isSupported requires READ_PRIVILEGED_PHONE_STATE permission");
        }
        assertNotNull(result);
        assertTrue("isSupported should return true", result);
    }

    @Test
    public void testIsSupportedWithSipTransportCapableCarrierConfigNotSet() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }
        PersistableBundle b = new PersistableBundle();
        // Carrier Config is explicitly set to not support single registration.
        b.putBoolean(CarrierConfigManager.Ims.KEY_IMS_SINGLE_REGISTRATION_REQUIRED_BOOL, false);
        overrideCarrierConfig(b);
        connectTestImsServiceWithSipTransport();

        Boolean result = callUntilImsServiceIsAvailable(() ->
                ShellIdentityUtils.invokeThrowableMethodWithShellPermissions(
                        getSipDelegateManager(), SipDelegateManager::isSupported,
                        ImsException.class, "android.permission.READ_PRIVILEGED_PHONE_STATE"));
        assertNotNull(result);
        assertFalse("isSupported should return false if"
                + "CarrierConfigManager.Ims.KEY_RCS_SINGLE_REGISTRATION_REQUIRED_BOOL is set to "
                + "false", result);
    }

    @Test
    public void testIsSupportedWithSipTransportCapableOnlyRcs() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }
        PersistableBundle b = new PersistableBundle();
        b.putBoolean(CarrierConfigManager.Ims.KEY_IMS_SINGLE_REGISTRATION_REQUIRED_BOOL, true);
        overrideCarrierConfig(b);
        assertTrue(sServiceConnector.connectCarrierImsServiceLocally());

        ImsFeatureConfiguration c = getConfigForRcs();
        assertTrue(sServiceConnector.triggerFrameworkConnectionToCarrierImsService(c));
        verifyImsServiceState(c);

        Boolean result = callUntilImsServiceIsAvailable(() ->
                ShellIdentityUtils.invokeThrowableMethodWithShellPermissions(
                        getSipDelegateManager(), SipDelegateManager::isSupported,
                        ImsException.class, "android.permission.READ_PRIVILEGED_PHONE_STATE"));
        assertNotNull(result);
        assertFalse("isSupported should return false in the case that the ImsService is only "
                + "attached for MMTEL and not MMTEL and RCS", result);
    }


    @Test
    public void testIsSupportedWithSipTransportCapableButNotImplemented() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }
        PersistableBundle b = new PersistableBundle();
        b.putBoolean(CarrierConfigManager.Ims.KEY_IMS_SINGLE_REGISTRATION_REQUIRED_BOOL, true);
        overrideCarrierConfig(b);

        assertTrue(sServiceConnector.connectCarrierImsServiceLocally());
        // SipTransport set as capable, but no SipTransport implementation is returned.
        sServiceConnector.getCarrierService().addCapabilities(
                ImsService.CAPABILITY_SIP_DELEGATE_CREATION);
        ImsFeatureConfiguration c = getConfigForMmTelAndRcs();
        assertTrue(sServiceConnector.triggerFrameworkConnectionToCarrierImsService(c));
        verifyImsServiceState(c);

        Boolean result = callUntilImsServiceIsAvailable(() ->
                ShellIdentityUtils.invokeThrowableMethodWithShellPermissions(
                        getSipDelegateManager(), SipDelegateManager::isSupported,
                        ImsException.class, "android.permission.READ_PRIVILEGED_PHONE_STATE"));
        assertNotNull(result);
        assertFalse("isSupported should return false in the case that SipTransport is not "
                + "implemented", result);
    }

    @Test
    public void testIsSupportedWithSipTransportImplementedButNotCapable() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }
        PersistableBundle b = new PersistableBundle();
        b.putBoolean(CarrierConfigManager.Ims.KEY_IMS_SINGLE_REGISTRATION_REQUIRED_BOOL, true);
        overrideCarrierConfig(b);

        assertTrue(sServiceConnector.connectCarrierImsServiceLocally());
        // SipTransport is set as Implemented, but not Capable
        sServiceConnector.getCarrierService().setSipTransportImplemented();
        ImsFeatureConfiguration c = getConfigForMmTelAndRcs();
        assertTrue(sServiceConnector.triggerFrameworkConnectionToCarrierImsService(c));
        verifyImsServiceState(c);

        Boolean result = callUntilImsServiceIsAvailable(() ->
                ShellIdentityUtils.invokeThrowableMethodWithShellPermissions(
                        getSipDelegateManager(), SipDelegateManager::isSupported,
                        ImsException.class, "android.permission.READ_PRIVILEGED_PHONE_STATE"));
        assertNotNull(result);
        assertFalse("isSupported should return false in the case that SipTransport is not "
                + "set as capable in ImsService#getImsServiceCapabilities", result);
    }

    @Test
    public void testIsSupportedWithSipTransportNotImplementedNotCapable() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }
        PersistableBundle b = new PersistableBundle();
        b.putBoolean(CarrierConfigManager.Ims.KEY_IMS_SINGLE_REGISTRATION_REQUIRED_BOOL, true);
        overrideCarrierConfig(b);

        assertTrue(sServiceConnector.connectCarrierImsServiceLocally());
        // NoytImplemented/capable
        ImsFeatureConfiguration c = getConfigForMmTelAndRcs();
        assertTrue(sServiceConnector.triggerFrameworkConnectionToCarrierImsService(c));
        verifyImsServiceState(c);

        Boolean result = callUntilImsServiceIsAvailable(() ->
                ShellIdentityUtils.invokeThrowableMethodWithShellPermissions(
                        getSipDelegateManager(), SipDelegateManager::isSupported,
                        ImsException.class, "android.permission.READ_PRIVILEGED_PHONE_STATE"));
        assertNotNull(result);
        assertFalse("isSupported should return false in the case that SipTransport is not "
                + "set as capable in ImsService#getImsServiceCapabilities", result);
    }

    private void connectTestImsServiceWithSipTransport() throws Exception {
        assertTrue(sServiceConnector.connectCarrierImsServiceLocally());
        sServiceConnector.getCarrierService().addCapabilities(
                ImsService.CAPABILITY_SIP_DELEGATE_CREATION);
        sServiceConnector.getCarrierService().setSipTransportImplemented();
        ImsFeatureConfiguration c = getConfigForMmTelAndRcs();
        assertTrue(sServiceConnector.triggerFrameworkConnectionToCarrierImsService(c));
        verifyImsServiceState(c);
    }

    private void verifyImsServiceState(ImsFeatureConfiguration config) {
        for (ImsFeatureConfiguration.FeatureSlotPair p : config.getServiceFeatures()) {
            switch (p.featureType) {
                case ImsFeature.FEATURE_MMTEL: {
                    sServiceConnector.getCarrierService().waitForLatchCountdown(
                            TestImsService.LATCH_CREATE_MMTEL);
                    assertNotNull("ImsService created, but ImsService#createMmTelFeature was not "
                            + "called!", sServiceConnector.getCarrierService().getMmTelFeature());
                    break;
                }
                case ImsFeature.FEATURE_RCS: {
                    sServiceConnector.getCarrierService().waitForLatchCountdown(
                            TestImsService.LATCH_CREATE_RCS);
                    assertNotNull("ImsService created, but ImsService#createRcsFeature was not "
                            + "called!", sServiceConnector.getCarrierService().getRcsFeature());
                    break;
                }
            }
        }
    }

    /**
     * Wait up to five seconds (retrying a command 1 time per second) until ImsExceptions due to the
     * ImsService not being available go away. If the ImsService never becomes available, this
     * method will return null.
     */
    private <T> T callUntilImsServiceIsAvailable(Callable<T> command) throws Exception {
        int retry = 0;
        while (retry < 5) {
            try {
                return command.call();
            } catch (ImsException e) {
                // we want to absorb only the unavailable error, as telephony may still be
                // internally setting up. Any other type of ImsException is unexpected.
                if (e.getCode() != ImsException.CODE_ERROR_SERVICE_UNAVAILABLE) {
                    throw e;
                }
            }
            Thread.sleep(1000);
            retry++;
        }
        return null;
    }

    private ImsFeatureConfiguration getConfigForMmTelAndRcs() {
        return new ImsFeatureConfiguration.Builder()
                .addFeature(sTestSlot, ImsFeature.FEATURE_EMERGENCY_MMTEL)
                .addFeature(sTestSlot, ImsFeature.FEATURE_MMTEL)
                .addFeature(sTestSlot, ImsFeature.FEATURE_RCS)
                .build();
    }
    private ImsFeatureConfiguration getConfigForRcs() {
        return new ImsFeatureConfiguration.Builder()
                .addFeature(sTestSlot, ImsFeature.FEATURE_RCS)
                .build();
    }

    private static void overrideCarrierConfig(PersistableBundle bundle) throws Exception {
        CarrierConfigManager carrierConfigManager = InstrumentationRegistry.getInstrumentation()
                .getContext().getSystemService(CarrierConfigManager.class);
        sReceiver.clearQueue();
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(carrierConfigManager,
                (m) -> m.overrideConfig(sTestSub, bundle));
        sReceiver.waitForCarrierConfigChanged();
    }

    private SipDelegateManager getSipDelegateManager() {
        ImsManager imsManager = getContext().getSystemService(ImsManager.class);
        assertNotNull(imsManager);
        return imsManager.getSipDelegateManager(sTestSub);
    }

    private static Context getContext() {
        return InstrumentationRegistry.getInstrumentation().getContext();
    }
}
