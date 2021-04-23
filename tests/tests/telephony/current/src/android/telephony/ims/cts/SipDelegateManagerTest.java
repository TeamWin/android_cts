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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import static java.nio.charset.StandardCharsets.UTF_8;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.InetAddresses;
import android.net.Uri;
import android.os.Parcel;
import android.os.PersistableBundle;
import android.telephony.CarrierConfigManager;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.telephony.ims.DelegateRegistrationState;
import android.telephony.ims.DelegateRequest;
import android.telephony.ims.FeatureTagState;
import android.telephony.ims.ImsException;
import android.telephony.ims.ImsManager;
import android.telephony.ims.ImsService;
import android.telephony.ims.SipDelegateConfiguration;
import android.telephony.ims.SipDelegateImsConfiguration;
import android.telephony.ims.SipDelegateManager;
import android.telephony.ims.SipMessage;
import android.telephony.ims.feature.ImsFeature;
import android.telephony.ims.stub.ImsFeatureConfiguration;
import android.util.ArraySet;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.ShellIdentityUtils;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * CTS tests for {@link SipDelegateManager} API.
 */
@RunWith(AndroidJUnit4.class)
public class SipDelegateManagerTest {
    private static final String MMTEL_TAG =
            "+g.3gpp.iari-ref=\"urn%3Aurn-7%3A3gpp-service.ims.icsi.mmtel\"";
    private static final String ONE_TO_ONE_CHAT_TAG =
            "+g.3gpp.icsi-ref=\"urn%3Aurn-7%3A3gppservice.ims.icsi.oma.cpm.msg\"";
    private static final String GROUP_CHAT_TAG =
            "+g.3gpp.icsi-ref=\"urn%3Aurn-7%3A3gppservice.ims.icsi.oma.cpm.session\"";
    private static final String FILE_TRANSFER_HTTP_TAG =
            "+g.3gpp.iari-ref=\"urn%3Aurn-7%3A3gppapplication.ims.iari.rcs.fthttp\"";

    private static final String[] DEFAULT_FEATURE_TAGS = {
            ONE_TO_ONE_CHAT_TAG, GROUP_CHAT_TAG, FILE_TRANSFER_HTTP_TAG};

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

    private static int sTestSlot = 0;
    private static int sTestSub = SubscriptionManager.INVALID_SUBSCRIPTION_ID;
    private static ImsServiceConnector sServiceConnector;
    private static CarrierConfigReceiver sReceiver;

    @BeforeClass
    public static void beforeAllTests() throws Exception {
        // First, only populate test slot/sub
        if (!ImsUtils.shouldTestTelephony()) {
            return;
        }

        TelephonyManager tm = (TelephonyManager) getContext()
                .getSystemService(Context.TELEPHONY_SERVICE);
        sTestSub = ImsUtils.getPreferredActiveSubId();
        sTestSlot = SubscriptionManager.getSlotIndex(sTestSub);
        if (tm.getSimState(sTestSlot) != TelephonyManager.SIM_STATE_READY) {
            return;
        }
        // Next, only start tests that require ImsResolver to bind to test ImsService if
        // feature FEATURE_TELEPHONY_IMS is supported on this device.
        if (!ImsUtils.shouldTestImsService()) {
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

        if (!ImsUtils.shouldTestImsSingleRegistration()) {
            // override FEATURE_TELEPHONY_IMS_SINGLE_REGISTRATION setting for this test to enable
            // APIs.
            sServiceConnector.setDeviceSingleRegistrationEnabled(true);
        }

        setFeatureTagsCarrierAllowed(DEFAULT_FEATURE_TAGS);
    }

    @AfterClass
    public static void afterAllTests() throws Exception {
        // Only clean up ImsResolver overrides if feature FEATURE_TELEPHONY_IMS is supported.
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
        if (!ImsUtils.shouldTestTelephony()) {
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
        // Unbind the ImsService after the test completes only if feature FEATURE_TELEPHONY_IMS
        // is enabled.
        if (sServiceConnector != null) {
            sServiceConnector.disconnectCarrierImsService();
            sServiceConnector.restoreDefaultSmsApp();
        }
    }

    @Test
    // Note this test can run on devices with only feature FEATURE_TELEPHONY, so ImsResolver may not
    // be running.
    public void testIncorrectPermissions() throws Exception {
        if (!ImsUtils.shouldTestTelephony()) {
            return;
        }
        SipDelegateManager manager = getSipDelegateManager();
        try {
            manager.isSupported();
            fail("isSupported requires READ_PRIVILEGED_PHONE_STATE or "
                    + "PERFORM_IMS_SINGLE_REGISTRATION");
        } catch (SecurityException e) {
            //expected
        }
        try {
            ShellIdentityUtils.invokeThrowableMethodWithShellPermissions(
                    manager, SipDelegateManager::isSupported, ImsException.class,
                    "android.permission.PERFORM_IMS_SINGLE_REGISTRATION");
        } catch (ImsException e) {
            // Not a problem, only checking permissions here.
        } catch (SecurityException e) {
            fail("isSupported requires READ_PRIVILEGED_PHONE_STATE or "
                    + "PERFORM_IMS_SINGLE_REGISTRATION, exception:" + e);
        }
        try {
            ShellIdentityUtils.invokeThrowableMethodWithShellPermissions(
                    manager, SipDelegateManager::isSupported, ImsException.class,
                    "android.permission.READ_PRIVILEGED_PHONE_STATE");

        } catch (ImsException e) {
            // Not a problem, only checking permissions here.
        } catch (SecurityException e) {
            fail("isSupported requires READ_PRIVILEGED_PHONE_STATE or "
                    + "PERFORM_IMS_SINGLE_REGISTRATION, exception:" + e);
        }

        DelegateRequest d = new DelegateRequest(Collections.emptySet());
        TestSipDelegateConnection c = new TestSipDelegateConnection(d);
        try {
            manager.createSipDelegate(d, Runnable::run, c, c);
            fail("createSipDelegate requires PERFORM_IMS_SINGLE_REGISTRATION");
        } catch (SecurityException e) {
            //expected
        }
    }

    @Test
    // Note this test can run on devices with only feature FEATURE_TELEPHONY, so ImsResolver may not
    // be running.
    public void testFeatureImsNotSupported() throws Exception {
        if (!ImsUtils.shouldTestTelephony()) {
            return;
        }

        if (sServiceConnector != null) {
            // Override FEATURE_TELEPHONY_IMS_SINGLE_REGISTRATION for this test so that telephony
            // will report not enabled.
            sServiceConnector.setDeviceSingleRegistrationEnabled(false);
        }

        try {
            SipDelegateManager manager = getSipDelegateManager();

            try {
                // If FEATURE_TELEPHONY_IMS_SINGLE_REGISTRATION is not supported this should
                // return false.
                Boolean result = ShellIdentityUtils.invokeThrowableMethodWithShellPermissions(
                        manager, SipDelegateManager::isSupported, ImsException.class,
                        "android.permission.PERFORM_IMS_SINGLE_REGISTRATION");
                assertNotNull(result);
                assertFalse("isSupported should return false on devices that do not "
                        + "support feature FEATURE_TELEPHONY_IMS_SINGLE_REGISTRATION", result);
            } catch (SecurityException e) {
                fail("isSupported requires PERFORM_IMS_SINGLE_REGISTRATION permission");
            }

            try {
                // If FEATURE_TELEPHONY_IMS_SINGLE_REGISTRATION is not supported, this should throw
                // an ImsException
                DelegateRequest request = new DelegateRequest(Collections.emptySet());
                TestSipDelegateConnection delegateConn = new TestSipDelegateConnection(request);
                ShellIdentityUtils.invokeThrowableMethodWithShellPermissionsNoReturn(
                        manager, (m) -> m.createSipDelegate(request, Runnable::run,
                                delegateConn, delegateConn), ImsException.class,
                        "android.permission.PERFORM_IMS_SINGLE_REGISTRATION");
                fail("createSipDelegate should throw an ImsException with code "
                        + "CODE_ERROR_UNSUPPORTED_OPERATION on devices that do not support feature "
                        + "FEATURE_TELEPHONY_IMS_SINGLE_REGISTRATION");
            } catch (SecurityException e) {
                fail("isSupported requires PERFORM_IMS_SINGLE_REGISTRATION permission");
            } catch (ImsException e) {
                // expecting CODE_ERROR_UNSUPPORTED_OPERATION
                if (e.getCode() != ImsException.CODE_ERROR_UNSUPPORTED_OPERATION) {
                    fail("createSipDelegate should throw an ImsException with code "
                            + "CODE_ERROR_UNSUPPORTED_OPERATION on devices that do not support "
                            + "feature FEATURE_TELEPHONY_IMS_SINGLE_REGISTRATION");
                }
            }
        } finally {
            if (sServiceConnector != null) {
                // restore override for the rest of the tests.
                sServiceConnector.setDeviceSingleRegistrationEnabled(true);
            }
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
                            "android.permission.PERFORM_IMS_SINGLE_REGISTRATION"));
        } catch (SecurityException e) {
            fail("isSupported requires PERFORM_IMS_SINGLE_REGISTRATION permission");
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
                        ImsException.class, "android.permission.PERFORM_IMS_SINGLE_REGISTRATION"));
        assertNotNull(result);
        assertFalse("isSupported should return false if"
                + "CarrierConfigManager.Ims.KEY_RCS_SINGLE_REGISTRATION_REQUIRED_BOOL is set to "
                + "false", result);
    }

    @Ignore("Disabling for integration b/175766573")
    @Test
    public void testIsSupportedWithSipTransportCapableOnlyRcs() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }
        PersistableBundle b = new PersistableBundle();
        b.putBoolean(CarrierConfigManager.Ims.KEY_IMS_SINGLE_REGISTRATION_REQUIRED_BOOL, true);
        overrideCarrierConfig(b);

        assertTrue(sServiceConnector.connectCarrierImsServiceLocally());
        // set SipTransport as supported with RCS only attached.
        sServiceConnector.getCarrierService().addCapabilities(
                ImsService.CAPABILITY_SIP_DELEGATE_CREATION);
        sServiceConnector.getCarrierService().setSipTransportImplemented();

        ImsFeatureConfiguration c = getConfigForRcs();
        assertTrue(sServiceConnector.triggerFrameworkConnectionToCarrierImsService(c));
        verifyImsServiceState(c);

        Boolean result = callUntilImsServiceIsAvailable(() ->
                ShellIdentityUtils.invokeThrowableMethodWithShellPermissions(
                        getSipDelegateManager(), SipDelegateManager::isSupported,
                        ImsException.class, "android.permission.PERFORM_IMS_SINGLE_REGISTRATION"));
        assertNotNull(result);
        assertFalse("isSupported should return false in the case that the ImsService is only "
                + "attached for RCS and not MMTEL and RCS", result);
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
                        ImsException.class, "android.permission.PERFORM_IMS_SINGLE_REGISTRATION"));
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
                        ImsException.class, "android.permission.PERFORM_IMS_SINGLE_REGISTRATION"));
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
        // Not Implemented/capable
        ImsFeatureConfiguration c = getConfigForMmTelAndRcs();
        assertTrue(sServiceConnector.triggerFrameworkConnectionToCarrierImsService(c));
        verifyImsServiceState(c);

        Boolean result = callUntilImsServiceIsAvailable(() ->
                ShellIdentityUtils.invokeThrowableMethodWithShellPermissions(
                        getSipDelegateManager(), SipDelegateManager::isSupported,
                        ImsException.class, "android.permission.PERFORM_IMS_SINGLE_REGISTRATION"));
        assertNotNull(result);
        assertFalse("isSupported should return false in the case that SipTransport is not "
                + "set as capable in ImsService#getImsServiceCapabilities", result);
    }

    @Test
    public void testCreateDestroyDelegateNotDefaultMessagingApp() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }
        connectTestImsServiceWithSipTransportAndConfig();

        TestSipTransport transportImpl = sServiceConnector.getCarrierService().getSipTransport();
        TestImsRegistration imsReg = sServiceConnector.getCarrierService().getImsRegistration();
        SipDelegateManager manager = getSipDelegateManager();
        DelegateRequest request = getDefaultRequest();
        TestSipDelegateConnection delegateConn = new TestSipDelegateConnection(request);

        // wait for onCreated and registration state change to be called.
        createSipDelegateConnectionNoDelegateExpected(manager, delegateConn, transportImpl);

        // TODO deal with this case better when we can filter messages.
        delegateConn.sendMessageAndVerifyFailure(ImsUtils.TEST_SIP_MESSAGE,
                SipDelegateManager.MESSAGE_FAILURE_REASON_DELEGATE_DEAD);

        delegateConn.triggerFullNetworkRegistration(manager, 403, "FORBIDDEN");
        // wait 5 seconds, this should not return.
        TestImsRegistration.NetworkRegistrationInfo info =
                imsReg.getNextFullNetworkRegRequest(5000);
        assertNull("If there is no valid SipTransport, this should not be called", info);

        destroySipDelegateConnectionNoDelegate(manager, delegateConn);
    }

    @Test
    public void testCreateDelegateBasicUseCases() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }
        assertTrue(sServiceConnector.setDefaultSmsApp());
        connectTestImsServiceWithSipTransportAndConfig();

        TestSipTransport transportImpl = sServiceConnector.getCarrierService().getSipTransport();
        TestImsRegistration regImpl = sServiceConnector.getCarrierService().getImsRegistration();
        SipDelegateManager manager = getSipDelegateManager();
        DelegateRequest request = getDefaultRequest();
        TestSipDelegateConnection delegateConn = new TestSipDelegateConnection(request);

        TestSipDelegate delegate = createSipDelegateConnectionAndVerify(manager, delegateConn,
                transportImpl, Collections.emptySet(), 0);
        assertNotNull(delegate);
        verifyUpdateRegistrationCalled(regImpl);

        InetSocketAddress localAddr = new InetSocketAddress(
                InetAddresses.parseNumericAddress("1.1.1.1"), 80);
        InetSocketAddress serverAddr = new InetSocketAddress(
                InetAddresses.parseNumericAddress("2.2.2.2"), 81);
        SipDelegateConfiguration c = new SipDelegateConfiguration.Builder(1,
                SipDelegateConfiguration.SIP_TRANSPORT_TCP, localAddr, serverAddr).build();
        verifyRegisteredAndSendSipConfig(delegateConn, delegate, request.getFeatureTags(),
                Collections.emptySet(), c);

        sendMessageAndVerifyAck(delegateConn, delegate);
        receiveMessageAndVerifyAck(delegateConn, delegate);

        // Ensure requests to perform a full network re-registration work properly.
        verifyFullRegistrationTriggered(manager, regImpl, delegateConn);

        destroySipDelegateAndVerify(manager, transportImpl, delegateConn, delegate,
                request.getFeatureTags());
        assertEquals("There should be no more delegates", 0,
                transportImpl.getDelegates().size());
        verifyUpdateRegistrationCalled(regImpl);
    }

    @Test
    public void testDeprecatedConfig() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }
        assertTrue(sServiceConnector.setDefaultSmsApp());
        connectTestImsServiceWithSipTransportAndConfig();

        TestSipTransport transportImpl = sServiceConnector.getCarrierService().getSipTransport();
        TestImsRegistration regImpl = sServiceConnector.getCarrierService().getImsRegistration();
        SipDelegateManager manager = getSipDelegateManager();
        DelegateRequest request = getDefaultRequest();
        TestSipDelegateConnection delegateConn = new TestSipDelegateConnection(request);

        TestSipDelegate delegate = createSipDelegateConnectionAndVerify(manager, delegateConn,
                transportImpl, Collections.emptySet(), 0);
        assertNotNull(delegate);
        verifyUpdateRegistrationCalled(regImpl);
        verifyRegisteredAndSendOldSipConfig(delegateConn, delegate, request.getFeatureTags(),
                Collections.emptySet());

        destroySipDelegateAndVerify(manager, transportImpl, delegateConn, delegate,
                request.getFeatureTags());
        assertEquals("There should be no more delegates", 0,
                transportImpl.getDelegates().size());
        verifyUpdateRegistrationCalled(regImpl);
    }

    @Test
    public void testDelegateRegistrationChanges() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }
        assertTrue(sServiceConnector.setDefaultSmsApp());
        connectTestImsServiceWithSipTransportAndConfig();

        TestSipTransport transportImpl = sServiceConnector.getCarrierService().getSipTransport();
        SipDelegateManager manager = getSipDelegateManager();
        DelegateRequest request = getDefaultRequest();
        TestSipDelegateConnection delegateConn = new TestSipDelegateConnection(request);

        // Construct registered tags and denied tags, vendor denied FT tag.
        Set<String> registeredTags = new ArraySet<>(request.getFeatureTags());
        registeredTags.remove(FILE_TRANSFER_HTTP_TAG);
        Set<FeatureTagState> deniedTags = new ArraySet<>(1);
        deniedTags.add(new FeatureTagState(FILE_TRANSFER_HTTP_TAG,
                SipDelegateManager.DENIED_REASON_IN_USE_BY_ANOTHER_DELEGATE));
        TestSipDelegate delegate = createSipDelegateConnectionAndVerify(manager, delegateConn,
                transportImpl, deniedTags, 0);
        assertNotNull(delegate);

        InetSocketAddress localAddr = new InetSocketAddress(
                InetAddresses.parseNumericAddress("1.1.1.1"), 80);
        InetSocketAddress serverAddr = new InetSocketAddress(
                InetAddresses.parseNumericAddress("2.2.2.2"), 81);
        SipDelegateConfiguration c = new SipDelegateConfiguration.Builder(1,
                SipDelegateConfiguration.SIP_TRANSPORT_TCP, localAddr, serverAddr).build();
        verifyRegisteredAndSendSipConfig(delegateConn, delegate, registeredTags, deniedTags, c);

        // TODO verify messages can be sent on registered tags, but generate error for denied tags.

        // move reg state to deregistering and then deregistered
        delegateConn.setOperationCountDownLatch(1);
        DelegateRegistrationState s = getDeregisteringState(registeredTags,
                DelegateRegistrationState.DEREGISTERING_REASON_PDN_CHANGE);
        delegate.notifyImsRegistrationUpdate(s);
        delegateConn.waitForCountDown(ImsUtils.TEST_TIMEOUT_MS);
        delegateConn.verifyRegistrationStateEquals(s);

        delegateConn.setOperationCountDownLatch(1);
        s = getRegisteredRegistrationState(registeredTags);
        delegate.notifyImsRegistrationUpdate(s);
        delegateConn.waitForCountDown(ImsUtils.TEST_TIMEOUT_MS);
        delegateConn.verifyRegistrationStateEquals(s);

        destroySipDelegateAndVerify(manager, transportImpl, delegateConn, delegate,
                registeredTags);
        assertEquals("There should be no more delegates", 0,
                transportImpl.getDelegates().size());
    }

    @Test
    public void testCreateMultipleDelegates() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }
        assertTrue(sServiceConnector.setDefaultSmsApp());
        connectTestImsServiceWithSipTransportAndConfig();
        TestSipTransport transportImpl = sServiceConnector.getCarrierService().getSipTransport();
        TestImsRegistration regImpl = sServiceConnector.getCarrierService().getImsRegistration();
        SipDelegateManager manager = getSipDelegateManager();

        DelegateRequest request1 = getChatOnlyRequest();
        TestSipDelegateConnection delegateConn1 = new TestSipDelegateConnection(request1);
        Set<String> registeredTags1 = new ArraySet<>(request1.getFeatureTags());
        TestSipDelegate delegate1 = createSipDelegateConnectionAndVerify(manager, delegateConn1,
                transportImpl, Collections.emptySet(), 0);
        assertNotNull(delegate1);

        InetSocketAddress localAddr = new InetSocketAddress(
                InetAddresses.parseNumericAddress("1.1.1.1"), 80);
        InetSocketAddress serverAddr = new InetSocketAddress(
                InetAddresses.parseNumericAddress("2.2.2.2"), 81);
        SipDelegateConfiguration c = new SipDelegateConfiguration.Builder(1,
                SipDelegateConfiguration.SIP_TRANSPORT_TCP, localAddr, serverAddr).build();
        verifyRegisteredAndSendSipConfig(delegateConn1, delegate1, registeredTags1,
                Collections.emptySet(), c);

        // This will only be granted File transfer FT
        DelegateRequest request2 = getDefaultRequest();
        TestSipDelegateConnection delegateConn2 = new TestSipDelegateConnection(request2);
        Set<String> registeredTags2 = new ArraySet<>();
        registeredTags2.add(FILE_TRANSFER_HTTP_TAG);
        TestSipDelegate delegate2 = createSipDelegateConnectionAndVerify(manager, delegateConn2,
                transportImpl, Collections.emptySet(), 1);
        assertNotNull(delegate2);
        verifyUpdateRegistrationCalled(regImpl);
        Set<FeatureTagState> deniedSet = generateDeniedSetFromRequest(request1.getFeatureTags(),
                request2.getFeatureTags(),
                SipDelegateManager.DENIED_REASON_IN_USE_BY_ANOTHER_DELEGATE);
        verifyRegisteredAndSendSipConfig(delegateConn2, delegate2, registeredTags2,
                deniedSet, c);

        // Destroying delegate 1 will transfer all feature tags over to delegate 2
        delegateConn2.setOperationCountDownLatch(1);
        destroySipDelegateAndVerify(manager, transportImpl, delegateConn1, delegate1,
                registeredTags1);
        delegateConn2.waitForCountDown(ImsUtils.TEST_TIMEOUT_MS);
        // This internally triggers the destruction of the internal delegate2 and then recreation
        // of another delegate with the new feature set that it supports.
        verifySipDelegateDestroyed(transportImpl, delegateConn2, delegate2, registeredTags2,
                DelegateRegistrationState.DEREGISTERING_REASON_FEATURE_TAGS_CHANGING);
        delegate2 = getSipDelegate(transportImpl, Collections.emptySet(), 0);
        verifyUpdateRegistrationCalled(regImpl);
        verifyRegisteredAndSendSipConfig(delegateConn2, delegate2, request2.getFeatureTags(),
                Collections.emptySet(), c);

        destroySipDelegateAndVerify(manager, transportImpl, delegateConn2, delegate2,
                request2.getFeatureTags());
        assertEquals("There should be no more delegates", 0,
                transportImpl.getDelegates().size());
    }

    @Test
    public void testCreateDelegateMessagingAppChangesToApp() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }
        // start with no features granted
        connectTestImsServiceWithSipTransportAndConfig();

        TestSipTransport transportImpl = sServiceConnector.getCarrierService().getSipTransport();
        TestImsRegistration regImpl = sServiceConnector.getCarrierService().getImsRegistration();
        SipDelegateManager manager = getSipDelegateManager();
        DelegateRequest request = getDefaultRequest();
        TestSipDelegateConnection delegateConn = new TestSipDelegateConnection(request);

        // wait for onCreated and registration state change to be called.
        createSipDelegateConnectionNoDelegateExpected(manager, delegateConn, transportImpl);

        // Make this app the DMA
        regImpl.resetLatch(TestImsRegistration.LATCH_TRIGGER_DEREGISTRATION, 1);
        assertTrue(sServiceConnector.setDefaultSmsApp());
        assertTrue(regImpl.waitForLatchCountDown(TestImsRegistration.LATCH_TRIGGER_DEREGISTRATION,
                ImsUtils.TEST_TIMEOUT_MS));
        TestSipDelegate delegate = getSipDelegate(transportImpl, Collections.emptySet(), 0);
        verifyUpdateRegistrationCalled(regImpl);
        InetSocketAddress localAddr = new InetSocketAddress(
                InetAddresses.parseNumericAddress("1.1.1.1"), 80);
        InetSocketAddress serverAddr = new InetSocketAddress(
                InetAddresses.parseNumericAddress("2.2.2.2"), 81);
        SipDelegateConfiguration c = new SipDelegateConfiguration.Builder(1,
                SipDelegateConfiguration.SIP_TRANSPORT_TCP, localAddr, serverAddr).build();
        verifyRegisteredAndSendSipConfig(delegateConn, delegate, request.getFeatureTags(),
                Collections.emptySet(), c);
        destroySipDelegateAndVerify(manager, transportImpl, delegateConn, delegate,
                request.getFeatureTags());
        assertEquals("There should be no more delegates", 0,
                transportImpl.getDelegates().size());
    }

    @Test
    public void testCreateDelegateMessagingAppChangesAwayFromApp() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }
        // Make this app the DMA
        assertTrue(sServiceConnector.setDefaultSmsApp());
        connectTestImsServiceWithSipTransportAndConfig();
        TestSipTransport transportImpl = sServiceConnector.getCarrierService().getSipTransport();
        TestImsRegistration regImpl = sServiceConnector.getCarrierService().getImsRegistration();
        SipDelegateManager manager = getSipDelegateManager();

        DelegateRequest request = getDefaultRequest();
        TestSipDelegateConnection delegateConn = new TestSipDelegateConnection(request);
        TestSipDelegate delegate = createSipDelegateConnectionAndVerify(manager, delegateConn,
                transportImpl, Collections.emptySet(), 0);
        assertNotNull(delegate);
        verifyUpdateRegistrationCalled(regImpl);

        InetSocketAddress localAddr = new InetSocketAddress(
                InetAddresses.parseNumericAddress("1.1.1.1"), 80);
        InetSocketAddress serverAddr = new InetSocketAddress(
                InetAddresses.parseNumericAddress("2.2.2.2"), 81);
        SipDelegateConfiguration c = new SipDelegateConfiguration.Builder(1,
                SipDelegateConfiguration.SIP_TRANSPORT_TCP, localAddr, serverAddr).build();
        verifyRegisteredAndSendSipConfig(delegateConn, delegate, request.getFeatureTags(),
                Collections.emptySet(), c);


        // Move DMA to another app, we should receive a registration update.
        delegateConn.setOperationCountDownLatch(1);
        regImpl.resetLatch(TestImsRegistration.LATCH_TRIGGER_DEREGISTRATION, 1);
        sServiceConnector.restoreDefaultSmsApp();
        assertTrue(regImpl.waitForLatchCountDown(TestImsRegistration.LATCH_TRIGGER_DEREGISTRATION,
                ImsUtils.TEST_TIMEOUT_MS));
        delegateConn.waitForCountDown(ImsUtils.TEST_TIMEOUT_MS);
        // we should get another reg update with all tags denied.
        delegateConn.setOperationCountDownLatch(1);
        verifySipDelegateDestroyed(transportImpl, delegateConn, delegate, request.getFeatureTags(),
                DelegateRegistrationState.DEREGISTERING_REASON_FEATURE_TAGS_CHANGING);
        delegateConn.waitForCountDown(ImsUtils.TEST_TIMEOUT_MS);
        delegateConn.verifyRegistrationStateEmpty();
        // All requested features should have been denied due to the app not being the default sms
        // app.
        delegateConn.verifyAllDenied(SipDelegateManager.DENIED_REASON_NOT_ALLOWED);
        // There should not be any delegates left, as the only delegate should have been cleaned up.
        assertEquals("SipDelegate should not have any delegates", 0,
                transportImpl.getDelegates().size());
        verifyUpdateRegistrationCalled(regImpl);

        destroySipDelegateConnectionNoDelegate(manager, delegateConn);
    }
    @Test
    public void testParcelUnparcelDelegateRequest() {
        ArraySet<String> testTags = new ArraySet<>();
        testTags.add(MMTEL_TAG);
        testTags.add(ONE_TO_ONE_CHAT_TAG);
        testTags.add(GROUP_CHAT_TAG);
        testTags.add(FILE_TRANSFER_HTTP_TAG);
        DelegateRequest r = new DelegateRequest(testTags);
        Parcel p = Parcel.obtain();
        r.writeToParcel(p, 0);
        p.setDataPosition(0);
        DelegateRequest unparcelled = DelegateRequest.CREATOR.createFromParcel(p);
        assertEquals(r, unparcelled);
        assertEquals(r.getFeatureTags(), unparcelled.getFeatureTags());
    }

    @Test
    public void testParcelUnparcelFeatureTagState() {
        FeatureTagState f = new FeatureTagState(MMTEL_TAG,
                DelegateRegistrationState.DEREGISTERED_REASON_NOT_REGISTERED);
        Parcel p = Parcel.obtain();
        f.writeToParcel(p, 0);
        p.setDataPosition(0);
        FeatureTagState unparcelled = FeatureTagState.CREATOR.createFromParcel(p);
        assertEquals(f, unparcelled);
        assertEquals(f.getFeatureTag(), unparcelled.getFeatureTag());
        assertEquals(f.getState(), unparcelled.getState());
    }

    @Test
    public void testParcelUnparcelRegistrationState() {
        ArraySet<String> regTags = new ArraySet<>();
        regTags.add(MMTEL_TAG);
        DelegateRegistrationState s = new DelegateRegistrationState.Builder()
                .addRegisteredFeatureTags(regTags)
                .addRegisteredFeatureTag(ONE_TO_ONE_CHAT_TAG)
                .addDeregisteringFeatureTag(GROUP_CHAT_TAG,
                        DelegateRegistrationState.DEREGISTERING_REASON_PDN_CHANGE)
                .addDeregisteredFeatureTag(FILE_TRANSFER_HTTP_TAG,
                        DelegateRegistrationState.DEREGISTERED_REASON_NOT_REGISTERED)
                .build();
        Parcel p = Parcel.obtain();
        s.writeToParcel(p, 0);
        p.setDataPosition(0);
        DelegateRegistrationState unparcel = DelegateRegistrationState.CREATOR.createFromParcel(p);
        assertEquals(s, unparcel);
        assertEquals(s.getRegisteredFeatureTags(), unparcel.getRegisteredFeatureTags());
        assertEquals(s.getDeregisteringFeatureTags(), unparcel.getDeregisteringFeatureTags());
        assertEquals(s.getDeregisteredFeatureTags(), unparcel.getDeregisteredFeatureTags());
    }

    @Test
    public void testParcelUnparcelConfiguration() {
        // Set everything to a non-default value
        SipDelegateConfiguration c = generateNewTestConfig();
        assertEquals(1, c.getVersion());
        assertEquals(SipDelegateConfiguration.SIP_TRANSPORT_TCP, c.getTransportType());
        assertEquals("1.1.1.1", c.getLocalAddress().getAddress().getHostAddress());
        assertEquals(80, c.getLocalAddress().getPort());
        assertEquals("2.2.2.2", c.getSipServerAddress().getAddress().getHostAddress());
        assertEquals(81, c.getSipServerAddress().getPort());
        assertTrue(c.isSipCompactFormEnabled());
        assertTrue(c.isSipKeepaliveEnabled());
        assertEquals(508, c.getMaxUdpPayloadSizeBytes());
        assertEquals("test1", c.getPublicUserIdentifier());
        assertEquals("test2", c.getPrivateUserIdentifier());
        assertEquals("test.domain", c.getHomeDomain());
        assertEquals("testImei", c.getImei());
        assertEquals("sipauth", c.getSipAuthenticationHeader());
        assertEquals("sipnonce", c.getSipAuthenticationNonce());
        assertEquals("srvroute", c.getSipServiceRouteHeader());
        assertEquals("path", c.getSipPathHeader());
        assertEquals("ua", c.getSipUserAgentHeader());
        assertEquals("user", c.getSipContactUserParameter());
        assertEquals("pani", c.getSipPaniHeader());
        assertEquals("plani", c.getSipPlaniHeader());
        assertEquals("cni", c.getSipCniHeader());
        assertEquals("uri", c.getSipAssociatedUriHeader());
        Uri gruuUri = Uri.parse("sip:blah@gruu.net");
        assertEquals(gruuUri, c.getPublicGruuUri());

        Parcel p = Parcel.obtain();
        c.writeToParcel(p, 0);
        p.setDataPosition(0);
        SipDelegateConfiguration unparcelConfig =
                SipDelegateConfiguration.CREATOR.createFromParcel(p);
        assertEquals(c, unparcelConfig);
    }

    @Test
    public void testCopyConfiguration() {
        // Set everything to a non-default value
        SipDelegateConfiguration c = generateNewTestConfig();
        // The config should be exactly the same, but with an incremented version.
        SipDelegateConfiguration configInc = new SipDelegateConfiguration.Builder(c).build();
        assertEquals(2, configInc.getVersion());
        assertEquals(SipDelegateConfiguration.SIP_TRANSPORT_TCP, configInc.getTransportType());
        assertEquals("1.1.1.1", configInc.getLocalAddress().getAddress().getHostAddress());
        assertEquals(80, configInc.getLocalAddress().getPort());
        assertEquals("2.2.2.2",
                configInc.getSipServerAddress().getAddress().getHostAddress());
        assertEquals(81, configInc.getSipServerAddress().getPort());
        assertTrue(configInc.isSipCompactFormEnabled());
        assertTrue(configInc.isSipKeepaliveEnabled());
        assertEquals(508, configInc.getMaxUdpPayloadSizeBytes());
        assertEquals("test1", configInc.getPublicUserIdentifier());
        assertEquals("test2", configInc.getPrivateUserIdentifier());
        assertEquals("test.domain", configInc.getHomeDomain());
        assertEquals("testImei", configInc.getImei());
        assertEquals("sipauth", configInc.getSipAuthenticationHeader());
        assertEquals("sipnonce", configInc.getSipAuthenticationNonce());
        assertEquals("srvroute", configInc.getSipServiceRouteHeader());
        assertEquals("path", configInc.getSipPathHeader());
        assertEquals("ua", configInc.getSipUserAgentHeader());
        assertEquals("user", configInc.getSipContactUserParameter());
        assertEquals("pani", configInc.getSipPaniHeader());
        assertEquals("plani", configInc.getSipPlaniHeader());
        assertEquals("cni", configInc.getSipCniHeader());
        assertEquals("uri", configInc.getSipAssociatedUriHeader());
        Uri gruuUri = Uri.parse("sip:blah@gruu.net");
        assertEquals(gruuUri, configInc.getPublicGruuUri());
    }

    @Test
    public void testParcelUnparcelSipMessage() {
        String startLine =
                "INVITE sip:12345678@[2607:fc20:3806:2a44:0:6:42ae:5b01]:49155 SIP/2.0\r\n";
        String header = "Via: SIP/2.0/TCP [FD00:976A:C202:1808::1]:65529;"
                + "branch=z9hG4bKg3Zqkv7iivdfzmfqu68sro3cuht97q846\r\n"
                + "To: <sip:12345678;phone-context=xxx.com@xxx.com;user=phone>\r\n"
                + "From: <sip:12345679@xxx.com>;tag=ABC\r\n"
                + "Call-ID: 000050B04074-79e-fc9b8700-29df64-5f3e5811-26fa8\r\n";
        String branch = "z9hG4bKg3Zqkv7iivdfzmfqu68sro3cuht97q846";
        String callId = "000050B04074-79e-fc9b8700-29df64-5f3e5811-26fa8";
        byte[] bytes = new byte[1];
        bytes[0] = 'a';
        SipMessage m = new SipMessage(startLine, header, bytes);
        Parcel p = Parcel.obtain();
        m.writeToParcel(p, 0);
        p.setDataPosition(0);
        SipMessage unparcel = SipMessage.CREATOR.createFromParcel(p);
        assertEquals(m, unparcel);
        assertEquals(m.getStartLine(), unparcel.getStartLine());
        assertEquals(m.getHeaderSection(), unparcel.getHeaderSection());
        assertTrue(Arrays.equals(m.getContent(), unparcel.getContent()));
        assertEquals(branch, m.getViaBranchParameter());
        assertEquals(callId, m.getCallIdParameter());
        assertEquals(m.getViaBranchParameter(), unparcel.getViaBranchParameter());
        assertEquals(m.getCallIdParameter(), unparcel.getCallIdParameter());
    }

    @Test
    public void testEncodeSipMessage() {
        String startLine =
                "INVITE sip:12345678@[2607:fc20:3806:2a44:0:6:42ae:5b01]:49155 SIP/2.0\r\n";
        String header = "Via: SIP/2.0/TCP [FD00:976A:C202:1808::1]:65529;"
                + "branch=z9hG4bKg3Zqkv7iivdfzmfqu68sro3cuht97q846\r\n"
                + "To: <sip:12345678;phone-context=xxx.com@xxx.com;"
                + "user=phone>\r\n"
                + "From: <sip:12345679@xxx.com>;"
                + "tag=h7g4Esbg_mavodi-e-10b-123-6-ffffffff-_000050B04074-79e-fc9b8700-29df65"
                + "-5f3e5811-27196\r\n"
                + "Call-ID: 000050B04074-79e-fc9b8700-29df64-5f3e5811-26fa8\r\n";
        byte[] content1 = ("v=0\r\n"
                + "o=- 10 1000 IN IP6 FD00:976A:C202:1808::1\r\n"
                + "s=VOIP\r\n"
                + "c=IN IP6 fd00:976a:c002:1940::4\r\n").getBytes(UTF_8);
        byte[] content2 = new byte[0];

        SipMessage m = new SipMessage(startLine, header, content1);
        byte[] encodedMsg = m.toEncodedMessage();
        String decodedStr = new String(encodedMsg, UTF_8);
        SipMessage decodedMsg = generateSipMessage(decodedStr);
        assertEquals(decodedMsg.getStartLine(), m.getStartLine());
        assertEquals(decodedMsg.getHeaderSection(), m.getHeaderSection());
        assertTrue(Arrays.equals(decodedMsg.getContent(), m.getContent()));

        // Test empty content
        m = new SipMessage(startLine, header, content2);
        encodedMsg = m.toEncodedMessage();
        decodedStr = new String(encodedMsg, UTF_8);
        decodedMsg = generateSipMessage(decodedStr);
        assertEquals(decodedMsg.getStartLine(), m.getStartLine());
        assertEquals(decodedMsg.getHeaderSection(), m.getHeaderSection());
        assertTrue(Arrays.equals(decodedMsg.getContent(), m.getContent()));
    }

    @Test
    public void testFeatureTagDeniedByCarrierConfig() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }

        setFeatureTagsCarrierAllowed(new String[]{FILE_TRANSFER_HTTP_TAG});
        assertTrue(sServiceConnector.setDefaultSmsApp());
        connectTestImsServiceWithSipTransportAndConfig();
        TestSipTransport transportImpl = sServiceConnector.getCarrierService().getSipTransport();
        TestImsRegistration regImpl = sServiceConnector.getCarrierService().getImsRegistration();
        SipDelegateManager manager = getSipDelegateManager();
        DelegateRequest request = getDefaultRequest();
        TestSipDelegateConnection delegateConn = new TestSipDelegateConnection(request);
        Set<String> deniedTags = new ArraySet<>(request.getFeatureTags());
        deniedTags.remove(FILE_TRANSFER_HTTP_TAG);

        TestSipDelegate delegate = createSipDelegateConnectionAndVerify(manager, delegateConn,
                transportImpl, getDeniedTagsForReason(deniedTags,
                        SipDelegateManager.DENIED_REASON_NOT_ALLOWED), 0);
        assertNotNull(delegate);

        Set<String> registeredTags = new ArraySet<>(
                Arrays.asList(new String[]{FILE_TRANSFER_HTTP_TAG}));
        delegateConn.setOperationCountDownLatch(1);
        DelegateRegistrationState s = getRegisteredRegistrationState(registeredTags);
        delegate.notifyImsRegistrationUpdate(s);
        delegateConn.waitForCountDown(ImsUtils.TEST_TIMEOUT_MS);
        delegateConn.verifyRegistrationStateEquals(s);
        destroySipDelegateAndVerify(manager, transportImpl, delegateConn, delegate, registeredTags);
        assertEquals("There should be no more delegates", 0,
                transportImpl.getDelegates().size());
        setFeatureTagsCarrierAllowed(getDefaultRequest().getFeatureTags().toArray(new String[0]));
    }

    private SipMessage generateSipMessage(String str) {
        String crlf = "\r\n";
        String[] components = str.split(crlf);
        String startLine = "";
        String header = "";
        String content = "";
        StringBuilder sb = new StringBuilder();
        int idx = 1;
        if (components.length > 0) {
            startLine = components[0] + crlf;
        }
        // generate sip header
        idx = composeSipSection(idx, components, sb);
        header = sb.toString();

        idx++;
        sb.setLength(0);
        // generate sip body
        idx = composeSipSection(idx, components, sb);
        content = sb.toString();

        return new SipMessage(startLine, header, content.getBytes(UTF_8));
    }

    private int composeSipSection(int index, String[] components, StringBuilder sb) {
        String crlf = "\r\n";
        while (index < components.length) {
            if (components[index].length() > 0) {
                sb.append(components[index]).append(crlf);
                index++;
            } else {
                break;
            }
        }
        return index;
    }

    private void createSipDelegateConnectionNoDelegateExpected(SipDelegateManager manager,
            TestSipDelegateConnection conn, TestSipTransport transport) throws Exception {
        // wait for onCreated and reg state changed
        conn.setOperationCountDownLatch(2);
        conn.connect(manager);
        conn.waitForCountDown(ImsUtils.TEST_TIMEOUT_MS);
        conn.verifyDelegateCreated();
        conn.verifyRegistrationStateEmpty();
        // All requested features should have been denied due to the app not being the default sms
        // app.
        conn.verifyAllDenied(SipDelegateManager.DENIED_REASON_NOT_ALLOWED);
        // There should not have been a call to create a SipDelegate on the service side, since all
        // features were denied due to permissions issues.
        assertEquals("SipDelegate should not have been created", 0,
                transport.getDelegates().size());
    }

    private void destroySipDelegateConnectionNoDelegate(SipDelegateManager manager,
            TestSipDelegateConnection delegateConn) throws Exception {
        delegateConn.setOperationCountDownLatch(1);
        delegateConn.disconnect(manager,
                SipDelegateManager.SIP_DELEGATE_DESTROY_REASON_REQUESTED_BY_APP);
        delegateConn.waitForCountDown(ImsUtils.TEST_TIMEOUT_MS);
        delegateConn.verifyDestroyed(
                SipDelegateManager.SIP_DELEGATE_DESTROY_REASON_REQUESTED_BY_APP);
    }

    private void destroySipDelegateAndVerify(SipDelegateManager manager,
            TestSipTransport transportImpl, TestSipDelegateConnection delegateConn,
            TestSipDelegate delegate, Set<String> registeredTags) throws Exception {
        // wait for registration change upon disconnecting state change
        delegateConn.setOperationCountDownLatch(1);
        delegateConn.disconnect(manager,
                SipDelegateManager.SIP_DELEGATE_DESTROY_REASON_REQUESTED_BY_APP);
        delegateConn.waitForCountDown(ImsUtils.TEST_TIMEOUT_MS);
        // verify we move to deregistering for registered tags.
        DelegateRegistrationState s = getDeregisteringState(registeredTags,
                DelegateRegistrationState.DEREGISTERING_REASON_DESTROY_PENDING);
        delegateConn.verifyRegistrationStateEquals(s);
        // wait for on destroyed
        delegateConn.setOperationCountDownLatch(1);
        transportImpl.waitForLatchCountdownAndReset(TestSipTransport.LATCH_DESTROY_DELEGATE);
        delegate.notifyOnDestroyed(
                SipDelegateManager.SIP_DELEGATE_DESTROY_REASON_REQUESTED_BY_APP);
        delegateConn.waitForCountDown(ImsUtils.TEST_TIMEOUT_MS);
        delegateConn.verifyDestroyed(
                SipDelegateManager.SIP_DELEGATE_DESTROY_REASON_REQUESTED_BY_APP);
    }

    private void verifySipDelegateDestroyed(TestSipTransport transportImpl,
            TestSipDelegateConnection delegateConn, TestSipDelegate delegate,
            Set<String> registeredTags, int deregReason) {
        // verify we move to deregistering for registered tags.
        DelegateRegistrationState s = getDeregisteringState(registeredTags, deregReason);
        delegateConn.verifyRegistrationStateEquals(s);
        transportImpl.waitForLatchCountdownAndReset(TestSipTransport.LATCH_DESTROY_DELEGATE);
        delegate.notifyOnDestroyed(
                SipDelegateManager.SIP_DELEGATE_DESTROY_REASON_REQUESTED_BY_APP);
    }

    private TestSipDelegate createSipDelegateConnectionAndVerify(SipDelegateManager manager,
            TestSipDelegateConnection conn, TestSipTransport transport,
            Set<FeatureTagState>  deniedTags, int delegateIndex) throws Exception {
        conn.setOperationCountDownLatch(1);
        conn.connect(manager);
        TestSipDelegate d = getSipDelegate(transport, deniedTags, delegateIndex);
        conn.waitForCountDown(ImsUtils.TEST_TIMEOUT_MS);
        conn.verifyDelegateCreated();
        return d;
    }

    private TestSipDelegate getSipDelegate(TestSipTransport transport,
            Set<FeatureTagState> deniedTags, int delegateIndex) {
        transport.waitForLatchCountdownAndReset(TestSipTransport.LATCH_CREATE_DELEGATE);
        // There must have been a call to create a SipDelegate on the service side.
        assertEquals("SipDelegate should have been created", delegateIndex + 1,
                transport.getDelegates().size());
        TestSipDelegate d = transport.getDelegates().get(delegateIndex);
        d.notifyOnCreated(deniedTags);
        return d;
    }

    private void verifyRegisteredAndSendSipConfig(TestSipDelegateConnection delegateConn,
            TestSipDelegate delegate, Set<String> registeredTags,
            Set<FeatureTagState> deniedTags, SipDelegateConfiguration sipConfig) {
        // wait for reg change to be called
        delegateConn.setOperationCountDownLatch(1);
        DelegateRegistrationState s = getRegisteredRegistrationState(registeredTags);
        delegate.notifyImsRegistrationUpdate(s);
        delegateConn.waitForCountDown(ImsUtils.TEST_TIMEOUT_MS);
        delegateConn.verifyRegistrationStateRegistered(registeredTags);
        delegateConn.verifyDenied(deniedTags);

        // send config change as well.
        sendConfigChange(sipConfig, delegateConn, delegate);
    }

    private void verifyRegisteredAndSendOldSipConfig(TestSipDelegateConnection delegateConn,
            TestSipDelegate delegate, Set<String> registeredTags,
            Set<FeatureTagState> deniedTags) {
        // wait for reg change to be called
        delegateConn.setOperationCountDownLatch(1);
        DelegateRegistrationState s = getRegisteredRegistrationState(registeredTags);
        delegate.notifyImsRegistrationUpdate(s);
        delegateConn.waitForCountDown(ImsUtils.TEST_TIMEOUT_MS);
        delegateConn.verifyRegistrationStateRegistered(registeredTags);
        delegateConn.verifyDenied(deniedTags);

        delegateConn.setOperationCountDownLatch(1);
        // Use the old config here and ensure a equivalent version of the new config is received
        // by app.
        delegate.notifyImsConfigurationUpdate(generateOldConfig());
        delegateConn.waitForCountDown(ImsUtils.TEST_TIMEOUT_MS);
        delegateConn.verifyConfigEquals(generateNewTestConfig());
    }

    private Set<FeatureTagState> generateDeniedSetFromRequest(Set<String> grantedTags,
            Set<String> newTags, int reason) {
        // Deny features from newTags that are already granted in grantedTags.
        return grantedTags.stream().filter(newTags::contains)
                .map(s -> new FeatureTagState(s, reason))
                .collect(Collectors.toSet());
    }

    private void verifyUpdateRegistrationCalled(TestImsRegistration regImpl) {
        regImpl.resetLatch(TestImsRegistration.LATCH_UPDATE_REGISTRATION, 1);
        // it is okay to reset and wait here (without race conditions) because there is a
        // second delay between triggering update registration and the latch being triggered.
        assertTrue(regImpl.waitForLatchCountDown(TestImsRegistration.LATCH_UPDATE_REGISTRATION,
                ImsUtils.TEST_TIMEOUT_MS));
    }

    private void verifyFullRegistrationTriggered(SipDelegateManager manager,
            TestImsRegistration regImpl, TestSipDelegateConnection delegateConn) throws Exception {
        delegateConn.verifyDelegateCreated();
        delegateConn.triggerFullNetworkRegistration(manager, 403, "FORBIDDEN");
        TestImsRegistration.NetworkRegistrationInfo info =
                regImpl.getNextFullNetworkRegRequest(ImsUtils.TEST_TIMEOUT_MS);
        assertNotNull("full registration requested, but ImsRegistrationImplBase "
                + "implementation did not receive a request.", info);
        assertEquals(403, info.sipCode);
        assertEquals("FORBIDDEN", info.sipReason);
    }

    private void sendMessageAndVerifyAck(TestSipDelegateConnection delegateConn,
            TestSipDelegate delegate) throws Exception {
        // Send a message and ensure it gets received on the other end as well as acked
        delegateConn.sendMessageAndVerifyCompletedSuccessfully(ImsUtils.TEST_SIP_MESSAGE);
        delegate.verifyMessageSend(ImsUtils.TEST_SIP_MESSAGE);
        delegateConn.sendCloseSession(ImsUtils.TEST_CALL_ID);
        delegate.verifyCloseSession(ImsUtils.TEST_CALL_ID);
        // send a message and notify connection that it failed
        delegate.setSendMessageDenyReason(
                SipDelegateManager.MESSAGE_FAILURE_REASON_NETWORK_NOT_AVAILABLE);
        delegateConn.sendMessageAndVerifyFailure(ImsUtils.TEST_SIP_MESSAGE,
                SipDelegateManager.MESSAGE_FAILURE_REASON_NETWORK_NOT_AVAILABLE);
        delegate.verifyMessageSend(ImsUtils.TEST_SIP_MESSAGE);
    }

    private void receiveMessageAndVerifyAck(TestSipDelegateConnection delegateConn,
            TestSipDelegate delegate) throws Exception {
        // Receive a message and ensure it gets received on the other end as well as acked
        delegate.receiveMessageAndVerifyReceivedCalled(ImsUtils.TEST_SIP_MESSAGE);
        delegateConn.verifyMessageReceived(ImsUtils.TEST_SIP_MESSAGE);
        // Receive a message and have connection notify that it didn't complete
        delegateConn.setReceivedMessageErrorResponseReason(
                SipDelegateManager.MESSAGE_FAILURE_REASON_INVALID_BODY_CONTENT);
        delegate.receiveMessageAndVerifyReceiveErrorCalled(ImsUtils.TEST_SIP_MESSAGE,
                SipDelegateManager.MESSAGE_FAILURE_REASON_INVALID_BODY_CONTENT);
    }

    private void sendConfigChange(SipDelegateConfiguration c,
            TestSipDelegateConnection delegateConn, TestSipDelegate delegate) {
        delegateConn.setOperationCountDownLatch(1);
        delegate.notifyConfigurationUpdate(c);
        delegateConn.waitForCountDown(ImsUtils.TEST_TIMEOUT_MS);
        delegateConn.verifyConfigEquals(c);
    }

    /**
     * @return A new test SipDelegateConfiguration that has all fields populated.1
     */
    private SipDelegateConfiguration generateNewTestConfig() {
        InetSocketAddress localAddr = new InetSocketAddress(
                InetAddresses.parseNumericAddress("1.1.1.1"), 80);
        InetSocketAddress serverAddr = new InetSocketAddress(
                InetAddresses.parseNumericAddress("2.2.2.2"), 81);
        SipDelegateConfiguration.Builder b = new SipDelegateConfiguration.Builder(1,
                SipDelegateConfiguration.SIP_TRANSPORT_TCP, localAddr, serverAddr);
        b.setSipCompactFormEnabled(true);
        b.setSipKeepaliveEnabled(true);
        b.setMaxUdpPayloadSizeBytes(508);
        b.setPublicUserIdentifier("test1");
        b.setPrivateUserIdentifier("test2");
        b.setHomeDomain("test.domain");
        b.setImei("testImei");
        b.setSipAuthenticationHeader("sipauth");
        b.setSipAuthenticationNonce("sipnonce");
        b.setSipServiceRouteHeader("srvroute");
        b.setSipPathHeader("path");
        b.setSipUserAgentHeader("ua");
        b.setSipContactUserParameter("user");
        b.setSipPaniHeader("pani");
        b.setSipPlaniHeader("plani");
        b.setSipCniHeader("cni");
        b.setSipAssociatedUriHeader("uri");
        Uri gruuUri = Uri.parse("sip:blah@gruu.net");
        b.setPublicGruuUri(gruuUri);
        SipDelegateConfiguration.IpSecConfiguration ipSecConfig =
                new SipDelegateConfiguration.IpSecConfiguration(123, 124,
                        125, 126, 127, 128, "secverify");
        assertEquals(123, ipSecConfig.getLocalTxPort());
        assertEquals(124, ipSecConfig.getLocalRxPort());
        assertEquals(125, ipSecConfig.getLastLocalTxPort());
        assertEquals(126, ipSecConfig.getRemoteTxPort());
        assertEquals(127, ipSecConfig.getRemoteRxPort());
        assertEquals(128, ipSecConfig.getLastRemoteTxPort());
        assertEquals("secverify", ipSecConfig.getSipSecurityVerifyHeader());
        b.setIpSecConfiguration(ipSecConfig);
        InetSocketAddress natAddr = new InetSocketAddress(
                InetAddresses.parseNumericAddress("3.3.3.3"), 129);
        b.setNatSocketAddress(natAddr);
        assertEquals("3.3.3.3", natAddr.getAddress().getHostAddress());
        assertEquals(129, natAddr.getPort());
        return b.build();
    }


    /**
     * @return A instance of the deprecated SipDelegateImConfiguration, which has the same
     * equivalent configuration as {@link #generateNewTestConfig()}.
     */
    private SipDelegateImsConfiguration generateOldConfig() {
        return new SipDelegateImsConfiguration.Builder(1)
                .addString(SipDelegateImsConfiguration.KEY_SIP_CONFIG_TRANSPORT_TYPE_STRING,
                        SipDelegateImsConfiguration.SIP_TRANSPORT_TCP)
                .addString(SipDelegateImsConfiguration.KEY_SIP_CONFIG_UE_DEFAULT_IPADDRESS_STRING,
                        "1.1.1.1")
                .addInt(SipDelegateImsConfiguration.KEY_SIP_CONFIG_UE_DEFAULT_PORT_INT, 80)
                .addString(SipDelegateImsConfiguration
                                .KEY_SIP_CONFIG_SERVER_DEFAULT_IPADDRESS_STRING,
                        "2.2.2.2")
                .addInt(SipDelegateImsConfiguration.KEY_SIP_CONFIG_SERVER_DEFAULT_PORT_INT, 81)
                .addBoolean(SipDelegateImsConfiguration.KEY_SIP_CONFIG_IS_COMPACT_FORM_ENABLED_BOOL,
                        true)
                .addBoolean(SipDelegateImsConfiguration.KEY_SIP_CONFIG_IS_KEEPALIVE_ENABLED_BOOL,
                        true)
                .addInt(SipDelegateImsConfiguration.KEY_SIP_CONFIG_MAX_PAYLOAD_SIZE_ON_UDP_INT,
                        508)
                .addString(SipDelegateImsConfiguration.KEY_SIP_CONFIG_UE_PUBLIC_USER_ID_STRING,
                        "test1")
                .addString(SipDelegateImsConfiguration.KEY_SIP_CONFIG_UE_PRIVATE_USER_ID_STRING,
                        "test2")
                .addString(SipDelegateImsConfiguration.KEY_SIP_CONFIG_HOME_DOMAIN_STRING,
                        "test.domain")
                .addString(SipDelegateImsConfiguration.KEY_SIP_CONFIG_IMEI_STRING, "testImei")
                .addString(SipDelegateImsConfiguration.KEY_SIP_CONFIG_AUTHENTICATION_HEADER_STRING,
                        "sipauth")
                .addString(SipDelegateImsConfiguration.KEY_SIP_CONFIG_AUTHENTICATION_NONCE_STRING,
                        "sipnonce")
                .addString(SipDelegateImsConfiguration.KEY_SIP_CONFIG_SERVICE_ROUTE_HEADER_STRING,
                        "srvroute")
                .addString(SipDelegateImsConfiguration.KEY_SIP_CONFIG_PATH_HEADER_STRING, "path")
                .addString(SipDelegateImsConfiguration.KEY_SIP_CONFIG_USER_AGENT_HEADER_STRING,
                        "ua")
                .addString(SipDelegateImsConfiguration.KEY_SIP_CONFIG_URI_USER_PART_STRING, "user")
                .addString(SipDelegateImsConfiguration
                        .KEY_SIP_CONFIG_P_ACCESS_NETWORK_INFO_HEADER_STRING, "pani")
                .addString(SipDelegateImsConfiguration
                        .KEY_SIP_CONFIG_P_LAST_ACCESS_NETWORK_INFO_HEADER_STRING, "plani")
                .addString(SipDelegateImsConfiguration
                        .KEY_SIP_CONFIG_CELLULAR_NETWORK_INFO_HEADER_STRING, "cni")
                .addString(
                        SipDelegateImsConfiguration.KEY_SIP_CONFIG_P_ASSOCIATED_URI_HEADER_STRING,
                        "uri")
                .addBoolean(SipDelegateImsConfiguration.KEY_SIP_CONFIG_IS_GRUU_ENABLED_BOOL, true)
                .addString(SipDelegateImsConfiguration.KEY_SIP_CONFIG_UE_PUBLIC_GRUU_STRING,
                        "sip:blah@gruu.net")
                .addBoolean(SipDelegateImsConfiguration.KEY_SIP_CONFIG_IS_IPSEC_ENABLED_BOOL, true)
                .addInt(SipDelegateImsConfiguration.KEY_SIP_CONFIG_UE_IPSEC_CLIENT_PORT_INT, 123)
                .addInt(SipDelegateImsConfiguration.KEY_SIP_CONFIG_UE_IPSEC_SERVER_PORT_INT, 124)
                .addInt(SipDelegateImsConfiguration.KEY_SIP_CONFIG_UE_IPSEC_OLD_CLIENT_PORT_INT,
                        125)
                .addInt(SipDelegateImsConfiguration.KEY_SIP_CONFIG_SERVER_IPSEC_CLIENT_PORT_INT,
                        126)
                .addInt(SipDelegateImsConfiguration.KEY_SIP_CONFIG_SERVER_IPSEC_SERVER_PORT_INT,
                        127)
                .addInt(SipDelegateImsConfiguration.KEY_SIP_CONFIG_SERVER_IPSEC_OLD_CLIENT_PORT_INT,
                        128)
                .addString(SipDelegateImsConfiguration.KEY_SIP_CONFIG_SECURITY_VERIFY_HEADER_STRING,
                        "secverify")
                .addBoolean(SipDelegateImsConfiguration.KEY_SIP_CONFIG_IS_NAT_ENABLED_BOOL, true)
                .addString(SipDelegateImsConfiguration
                        .KEY_SIP_CONFIG_UE_PUBLIC_IPADDRESS_WITH_NAT_STRING, "3.3.3.3")
                .addInt(SipDelegateImsConfiguration.KEY_SIP_CONFIG_UE_PUBLIC_PORT_WITH_NAT_INT, 129)
                .build();
    }

    private DelegateRegistrationState getRegisteredRegistrationState(Set<String> registered) {
        return new DelegateRegistrationState.Builder().addRegisteredFeatureTags(registered).build();
    }

    private DelegateRegistrationState getDeregisteringState(Set<String> deregisterTags,
            int reason) {
        DelegateRegistrationState.Builder b = new DelegateRegistrationState.Builder();
        for (String t : deregisterTags) {
            b.addDeregisteringFeatureTag(t, reason);
        }
        return b.build();
    }

    private void connectTestImsServiceWithSipTransportAndConfig() throws Exception {
        PersistableBundle b = new PersistableBundle();
        b.putBoolean(CarrierConfigManager.Ims.KEY_IMS_SINGLE_REGISTRATION_REQUIRED_BOOL, true);
        overrideCarrierConfig(b);

        assertTrue(sServiceConnector.connectCarrierImsServiceLocally());
        sServiceConnector.getCarrierService().addCapabilities(
                ImsService.CAPABILITY_SIP_DELEGATE_CREATION);
        sServiceConnector.getCarrierService().setSipTransportImplemented();
        ImsFeatureConfiguration c = getConfigForMmTelAndRcs();
        assertTrue(sServiceConnector.triggerFrameworkConnectionToCarrierImsService(c));
        verifyImsServiceState(c);
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

    private DelegateRequest getDefaultRequest() {
        ArraySet<String> features = new ArraySet<>(Arrays.asList(DEFAULT_FEATURE_TAGS));
        return new DelegateRequest(features);
    }

    private DelegateRequest getChatOnlyRequest() {
        ArraySet<String> features = new ArraySet<>(3);
        features.add(ONE_TO_ONE_CHAT_TAG);
        features.add(GROUP_CHAT_TAG);
        return new DelegateRequest(features);
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

    private Set<FeatureTagState> getDeniedTagsForReason(Set<String> deniedTags, int reason) {
        return deniedTags.stream().map(t -> new FeatureTagState(t, reason))
                .collect(Collectors.toSet());
    }

    private static void overrideCarrierConfig(PersistableBundle bundle) throws Exception {
        CarrierConfigManager carrierConfigManager = InstrumentationRegistry.getInstrumentation()
                .getContext().getSystemService(CarrierConfigManager.class);
        sReceiver.clearQueue();
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(carrierConfigManager,
                (m) -> m.overrideConfig(sTestSub, bundle));
        sReceiver.waitForCarrierConfigChanged();
    }

    private static void setFeatureTagsCarrierAllowed(String[] tags) throws Exception {
        PersistableBundle bundle = new PersistableBundle();
        bundle.putStringArray(CarrierConfigManager.Ims.KEY_RCS_FEATURE_TAG_ALLOWED_STRING_ARRAY,
                tags);
        overrideCarrierConfig(bundle);
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
