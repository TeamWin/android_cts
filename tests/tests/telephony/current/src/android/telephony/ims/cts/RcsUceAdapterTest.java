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

import static android.telephony.ims.RcsContactUceCapability.REQUEST_RESULT_FOUND;
import static android.telephony.ims.RcsContactUceCapability.REQUEST_RESULT_NOT_FOUND;
import static android.telephony.ims.stub.RcsCapabilityExchangeImplBase.COMMAND_CODE_FETCH_ERROR;
import static android.telephony.ims.stub.RcsCapabilityExchangeImplBase.COMMAND_CODE_GENERIC_FAILURE;
import static android.telephony.ims.stub.RcsCapabilityExchangeImplBase.COMMAND_CODE_INSUFFICIENT_MEMORY;
import static android.telephony.ims.stub.RcsCapabilityExchangeImplBase.COMMAND_CODE_INVALID_PARAM;
import static android.telephony.ims.stub.RcsCapabilityExchangeImplBase.COMMAND_CODE_LOST_NETWORK_CONNECTION;
import static android.telephony.ims.stub.RcsCapabilityExchangeImplBase.COMMAND_CODE_NOT_FOUND;
import static android.telephony.ims.stub.RcsCapabilityExchangeImplBase.COMMAND_CODE_NOT_SUPPORTED;
import static android.telephony.ims.stub.RcsCapabilityExchangeImplBase.COMMAND_CODE_NO_CHANGE;
import static android.telephony.ims.stub.RcsCapabilityExchangeImplBase.COMMAND_CODE_REQUEST_TIMEOUT;
import static android.telephony.ims.stub.RcsCapabilityExchangeImplBase.COMMAND_CODE_SERVICE_UNAVAILABLE;
import static android.telephony.ims.stub.RcsCapabilityExchangeImplBase.COMMAND_CODE_SERVICE_UNKNOWN;

import static junit.framework.Assert.assertTrue;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.PersistableBundle;
import android.provider.Telephony;
import android.telecom.PhoneAccount;
import android.telephony.CarrierConfigManager;
import android.telephony.SubscriptionManager;
import android.telephony.ims.ImsException;
import android.telephony.ims.ImsManager;
import android.telephony.ims.ImsRcsManager;
import android.telephony.ims.RcsContactPresenceTuple;
import android.telephony.ims.RcsContactPresenceTuple.ServiceCapabilities;
import android.telephony.ims.RcsContactUceCapability;
import android.telephony.ims.RcsUceAdapter;
import android.telephony.ims.feature.ImsFeature;
import android.telephony.ims.stub.ImsFeatureConfiguration;
import android.util.Log;
import android.util.Pair;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.ShellIdentityUtils;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@RunWith(AndroidJUnit4.class)
public class RcsUceAdapterTest {

    private static int sTestSlot = 0;
    private static int sTestSub = SubscriptionManager.INVALID_SUBSCRIPTION_ID;
    private static final Uri LISTENER_URI = Uri.withAppendedPath(Telephony.SimInfo.CONTENT_URI,
            Telephony.SimInfo.COLUMN_IMS_RCS_UCE_ENABLED);
    private static HandlerThread sHandlerThread;
    private static ImsServiceConnector sServiceConnector;
    private static CarrierConfigReceiver sReceiver;
    private static boolean sDeviceUceEnabled;

    private static String sTestPhoneNumber;
    private static String sTestContact2;
    private static String sTestContact3;
    private static Uri sTestNumberUri;
    private static Uri sTestContact2Uri;
    private static Uri sTestContact3Uri;

    private ContentObserver mUceObserver;

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

        sTestSub = ImsUtils.getPreferredActiveSubId();
        sTestSlot = SubscriptionManager.getSlotIndex(sTestSub);

        if (Looper.getMainLooper() == null) {
            Looper.prepareMainLooper();
        }
        sHandlerThread = new HandlerThread("CtsTelephonyTestCases");
        sHandlerThread.start();

        sServiceConnector = new ImsServiceConnector(InstrumentationRegistry.getInstrumentation());
        sServiceConnector.clearAllActiveImsServices(sTestSlot);

        // Save the original config of device uce enabled and override it.
        sDeviceUceEnabled = sServiceConnector.getDeviceUceEnabled();
        sServiceConnector.setDeviceUceEnabled(true);

        sReceiver = new RcsUceAdapterTest.CarrierConfigReceiver(sTestSub);
        IntentFilter filter = new IntentFilter(CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED);
        // ACTION_CARRIER_CONFIG_CHANGED is sticky, so we will get a callback right away.
        InstrumentationRegistry.getInstrumentation().getContext()
                .registerReceiver(sReceiver, filter);

        // Initialize the test phone numbers
        initPhoneNumbers();
    }

    @AfterClass
    public static void afterAllTests() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }
        // // Restore all ImsService configurations that existed before the test.
        if (sServiceConnector != null) {
            sServiceConnector.disconnectServices();
            sServiceConnector.setDeviceUceEnabled(sDeviceUceEnabled);
        }
        sServiceConnector = null;

        // Ensure there are no CarrierConfig overrides as well as reset the ImsResolver in case the
        // ImsService override changed in CarrierConfig while we were overriding it.
        overrideCarrierConfig(null);

        if (sReceiver != null) {
            InstrumentationRegistry.getInstrumentation().getContext().unregisterReceiver(sReceiver);
            sReceiver = null;
        }

        if (sHandlerThread != null) {
            sHandlerThread.quit();
        }
    }

    @Before
    public void beforeTest() {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }
        if (!SubscriptionManager.isValidSubscriptionId(sTestSub)) {
            fail("This test requires that there is a SIM in the device!");
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
            sServiceConnector.disconnectDeviceImsService();
        }
        overrideCarrierConfig(null);
        // Remove all the test contacts from EAB database
        removeTestContactFromEab();
    }

    @Test
    public void testCapabilityDiscoveryIntentReceiverExists() {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }

        PackageManager packageManager = getContext().getPackageManager();
        ResolveInfo info = packageManager.resolveActivity(
                new Intent(ImsRcsManager.ACTION_SHOW_CAPABILITY_DISCOVERY_OPT_IN),
                PackageManager.MATCH_DEFAULT_ONLY);
        assertNotNull(ImsRcsManager.ACTION_SHOW_CAPABILITY_DISCOVERY_OPT_IN
                + " Intent action must be handled by an appropriate settings application.", info);
        assertNotEquals(ImsRcsManager.ACTION_SHOW_CAPABILITY_DISCOVERY_OPT_IN
                + " activity intent filter must have a > 0 priority.", 0, info.priority);
    }

    @Test
    public void testGetAndSetUceSetting() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }

        ImsManager imsManager = getContext().getSystemService(ImsManager.class);
        RcsUceAdapter adapter = imsManager.getImsRcsManager(sTestSub).getUceAdapter();
        assertNotNull("RcsUceAdapter can not be null!", adapter);

        Boolean isEnabled = null;
        try {
            isEnabled = ShellIdentityUtils.invokeThrowableMethodWithShellPermissions(
                    adapter, RcsUceAdapter::isUceSettingEnabled, ImsException.class,
                    "android.permission.READ_PHONE_STATE");
            assertNotNull(isEnabled);

            // Ensure the ContentObserver gets the correct callback based on the change.
            LinkedBlockingQueue<Uri> queue = new LinkedBlockingQueue<>(1);
            registerUceObserver(queue::offer);
            boolean userSetIsEnabled = isEnabled;
            ShellIdentityUtils.invokeThrowableMethodWithShellPermissionsNoReturn(
                    adapter, a -> a.setUceSettingEnabled(!userSetIsEnabled), ImsException.class,
                    "android.permission.MODIFY_PHONE_STATE");
            Uri result = queue.poll(ImsUtils.TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            assertNotNull(result);
            assertTrue("Unexpected URI, should only receive URIs with prefix " + LISTENER_URI,
                    result.isPathPrefixMatch(LISTENER_URI));
            // Verify the subId associated with the Observer is correct.
            List<String> pathSegments = result.getPathSegments();
            String subId = pathSegments.get(pathSegments.size() - 1);
            assertEquals("Subscription ID contained in ContentObserver URI doesn't match the "
                            + "subscription that has changed.",
                    String.valueOf(sTestSub), subId);

            Boolean setResult = ShellIdentityUtils.invokeThrowableMethodWithShellPermissions(
                    adapter, RcsUceAdapter::isUceSettingEnabled, ImsException.class,
                    "android.permission.READ_PHONE_STATE");
            assertNotNull(setResult);
            assertEquals("Incorrect setting!", !userSetIsEnabled, setResult);
        } catch (ImsException e) {
            if (e.getCode() != ImsException.CODE_ERROR_UNSUPPORTED_OPERATION) {
                fail("failed getting UCE setting with code: " + e.getCode());
            }
        } finally {
            if (isEnabled != null) {
                boolean userSetIsEnabled = isEnabled;
                // set back to user preference
                ShellIdentityUtils.invokeThrowableMethodWithShellPermissionsNoReturn(
                        adapter, a -> a.setUceSettingEnabled(userSetIsEnabled), ImsException.class,
                        "android.permission.MODIFY_PHONE_STATE");
            }
            unregisterUceObserver();
        }
    }

    @Test
    public void testMethodPermissions() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }
        ImsManager imsManager = getContext().getSystemService(ImsManager.class);
        RcsUceAdapter uceAdapter = imsManager.getImsRcsManager(sTestSub).getUceAdapter();
        assertNotNull("UCE adapter should not be null!", uceAdapter);
        ArrayList<Uri> numbers = new ArrayList<>(1);
        numbers.add(sTestNumberUri);

        // isUceSettingEnabled - read
        Boolean isUceSettingEnabledResult = null;
        try {
            isUceSettingEnabledResult =
                    ShellIdentityUtils.invokeThrowableMethodWithShellPermissions(
                    uceAdapter, RcsUceAdapter::isUceSettingEnabled, ImsException.class,
                    "android.permission.READ_PHONE_STATE");
            assertNotNull("result from isUceSettingEnabled should not be null",
                    isUceSettingEnabledResult);
        } catch (SecurityException e) {
            fail("isUceSettingEnabled should succeed with READ_PHONE_STATE.");
        } catch (ImsException e) {
            // unsupported is a valid fail cause.
            if (e.getCode() != ImsException.CODE_ERROR_UNSUPPORTED_OPERATION) {
                fail("isUceSettingEnabled failed with code " + e.getCode());
            }
        }

        // isUceSettingEnabled - read_privileged
        try {
            isUceSettingEnabledResult =
                    ShellIdentityUtils.invokeThrowableMethodWithShellPermissions(
                            uceAdapter, RcsUceAdapter::isUceSettingEnabled, ImsException.class,
                            "android.permission.READ_PRIVILEGED_PHONE_STATE");
            assertNotNull("result from isUceSettingEnabled should not be null",
                    isUceSettingEnabledResult);
        } catch (SecurityException e) {
            fail("isUceSettingEnabled should succeed with READ_PRIVILEGED_PHONE_STATE.");
        } catch (ImsException e) {
            // unsupported is a valid fail cause.
            if (e.getCode() != ImsException.CODE_ERROR_UNSUPPORTED_OPERATION) {
                fail("isUceSettingEnabled failed with code " + e.getCode());
            }
        }

        // setUceSettingEnabled
        boolean isUceSettingEnabled =
                (isUceSettingEnabledResult == null ? false : isUceSettingEnabledResult);
        try {
            ShellIdentityUtils.invokeThrowableMethodWithShellPermissionsNoReturn(uceAdapter,
                    (m) -> m.setUceSettingEnabled(isUceSettingEnabled), ImsException.class,
                    "android.permission.MODIFY_PHONE_STATE");
        } catch (SecurityException e) {
            fail("setUceSettingEnabled should succeed with MODIFY_PHONE_STATE.");
        } catch (ImsException e) {
            // unsupported is a valid fail cause.
            if (e.getCode() != ImsException.CODE_ERROR_UNSUPPORTED_OPERATION) {
                fail("setUceSettingEnabled failed with code " + e.getCode());
            }
        }

        // Connect to the TestImsService
        connectTestImsService();

        // getUcePublishState without permission
        try {
            uceAdapter.getUcePublishState();
            fail("getUcePublishState should require READ_PRIVILEGED_PHONE_STATE permission.");
        } catch (SecurityException e) {
            //expected
        }

        // getUcePublishState with permission
        try {
            ShellIdentityUtils.invokeThrowableMethodWithShellPermissionsNoReturn(uceAdapter,
                    (m) -> m.getUcePublishState(), ImsException.class,
                    "android.permission.READ_PRIVILEGED_PHONE_STATE");
        } catch (SecurityException e) {
            fail("getUcePublishState should succeed with READ_PRIVILEGED_PHONE_STATE.");
        } catch (ImsException e) {
            // unsupported is a valid fail cause.
            if (e.getCode() != ImsException.CODE_ERROR_UNSUPPORTED_OPERATION) {
                fail("getUcePublishState failed with code " + e.getCode());
            }
        }

        final RcsUceAdapter.OnPublishStateChangedListener publishStateListener = (state) -> { };

        // addOnPublishStateChangedListener without permission
        try {
            uceAdapter.addOnPublishStateChangedListener(Runnable::run, publishStateListener);
            fail("addOnPublishStateChangedListener should require "
                    + "READ_PRIVILEGED_PHONE_STATE");
        } catch (SecurityException e) {
            // expected
        }

        // addOnPublishStateChangedListener with permission.
        try {
            ShellIdentityUtils.invokeThrowableMethodWithShellPermissionsNoReturn(uceAdapter,
                    (m) -> m.addOnPublishStateChangedListener(Runnable::run, publishStateListener),
                    ImsException.class,
                    "android.permission.READ_PRIVILEGED_PHONE_STATE");
        } catch (SecurityException e) {
            fail("addOnPublishStateChangedListener should succeed with "
                    + "READ_PRIVILEGED_PHONE_STATE.");
        } catch (ImsException e) {
            // unsupported is a valid fail cause.
            if (e.getCode() != ImsException.CODE_ERROR_UNSUPPORTED_OPERATION) {
                fail("addOnPublishStateChangedListener failed with code " + e.getCode());
            }
        }

        // removeOnPublishStateChangedListener without permission
        try {
            uceAdapter.removeOnPublishStateChangedListener(publishStateListener);
            fail("removeOnPublishStateChangedListener should require "
                    + "READ_PRIVILEGED_PHONE_STATE");
        } catch (SecurityException e) {
            // expected
        }

        // Prepare the callback of the capability request
        RcsUceAdapter.CapabilitiesCallback callback = new RcsUceAdapter.CapabilitiesCallback() {
            @Override
            public void onCapabilitiesReceived(List<RcsContactUceCapability> capabilities) {
            }
            @Override
            public void onComplete() {
            }
            @Override
            public void onError(int errorCode, long retryAfterMilliseconds) {
            }
        };

        // requestCapabilities without permission
        try {
            uceAdapter.requestCapabilities(numbers, Runnable::run , callback);
            fail("requestCapabilities should require ACCESS_RCS_USER_CAPABILITY_EXCHANGE.");
        } catch (SecurityException e) {
            //expected
        }

        // requestAvailability without permission
        try {
            uceAdapter.requestAvailability(sTestNumberUri, Runnable::run, callback);
            fail("requestAvailability should require ACCESS_RCS_USER_CAPABILITY_EXCHANGE.");
        } catch (SecurityException e) {
            //expected
        }

        // Lunch an activity to stay in the foreground.
        lunchUceActivity();

        // requestCapabilities in the foreground
        try {
            ShellIdentityUtils.invokeThrowableMethodWithShellPermissionsNoReturn(uceAdapter,
                    (m) -> m.requestCapabilities(numbers, Runnable::run, callback),
                    ImsException.class,
                    "android.permission.ACCESS_RCS_USER_CAPABILITY_EXCHANGE");
        } catch (SecurityException e) {
            fail("requestCapabilities should succeed with ACCESS_RCS_USER_CAPABILITY_EXCHANGE.");
        } catch (ImsException e) {
            // unsupported is a valid fail cause.
            if (e.getCode() != ImsException.CODE_ERROR_UNSUPPORTED_OPERATION) {
                fail("requestCapabilities failed with code " + e.getCode());
            }
        }

        // requestAvailability in the foreground
        try {
            ShellIdentityUtils.invokeThrowableMethodWithShellPermissionsNoReturn(uceAdapter,
                    (m) -> m.requestAvailability(sTestNumberUri, Runnable::run, callback),
                    ImsException.class,
                    "android.permission.ACCESS_RCS_USER_CAPABILITY_EXCHANGE");
        } catch (SecurityException e) {
            fail("requestAvailability should succeed with ACCESS_RCS_USER_CAPABILITY_EXCHANGE.");
        } catch (ImsException e) {
            // unsupported is a valid fail cause.
            if (e.getCode() != ImsException.CODE_ERROR_UNSUPPORTED_OPERATION) {
                fail("requestAvailability failed with code " + e.getCode());
            }
        }

        // Finish the activity
        finishUceActivity();
    }

    @Test
    public void testCapabilitiesRequestAllowed() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }
        ImsManager imsManager = getContext().getSystemService(ImsManager.class);
        RcsUceAdapter uceAdapter = imsManager.getImsRcsManager(sTestSub).getUceAdapter();
        assertNotNull("UCE adapter should not be null!", uceAdapter);

        // Prepare the test contact and the callback
        ArrayList<Uri> numbers = new ArrayList<>(1);
        numbers.add(sTestNumberUri);

        ArrayList<String> pidfXmlList = new ArrayList<>(1);
        pidfXmlList.add(getPidfXmlData(sTestNumberUri, true, true));

        BlockingQueue<Boolean> completeQueue = new LinkedBlockingQueue<>();
        BlockingQueue<RcsContactUceCapability> capabilityQueue = new LinkedBlockingQueue<>();
        BlockingQueue<Integer> errorQueue = new LinkedBlockingQueue<>();
        RcsUceAdapter.CapabilitiesCallback callback = new RcsUceAdapter.CapabilitiesCallback() {
            @Override
            public void onCapabilitiesReceived(List<RcsContactUceCapability> capabilities) {
                capabilities.forEach(c -> capabilityQueue.offer(c));
            }
            @Override
            public void onComplete() {
                completeQueue.offer(true);
            }
            @Override
            public void onError(int errorCode, long retryAfterMilliseconds) {
                errorQueue.offer(errorCode);
            }
        };

        // The API requestCapabilities should fail when it doesn't grant the permission.
        try {
            uceAdapter.requestCapabilities(numbers, Runnable::run, callback);
            fail("requestCapabilities requires ACCESS_USER_CAPABILITY_EXCHANGE permission.");
        } catch (SecurityException e) {
            //expected
        }

        // The API requestAvailability should fail when it doesn't grant the permission.
        try {
            uceAdapter.requestAvailability(sTestNumberUri, Runnable::run, callback);
            fail("requestAvailability requires ACCESS_USER_CAPABILITY_EXCHANGE permission.");
        } catch (SecurityException e) {
            //expected
        }

        // Trigger carrier config changed
        PersistableBundle bundle = new PersistableBundle();
        bundle.putBoolean(CarrierConfigManager.Ims.KEY_ENABLE_PRESENCE_PUBLISH_BOOL, true);
        overrideCarrierConfig(bundle);

        // Connect to the TestImsService
        connectTestImsService();

        // Stay in the foreground.
        lunchUceActivity();

        TestRcsCapabilityExchangeImpl capabilityExchangeImpl = sServiceConnector
                .getCarrierService().getRcsFeature().getRcsCapabilityExchangeImpl();

        requestCapabilities(uceAdapter, numbers, callback);

        // Verify that the callback "onError" is called with the error code NOT_ENABLED because
        // the carrier config KEY_ENABLE_PRESENCE_CAPABILITY_EXCHANGE_BOOL is still false.
        try {
            assertEquals(RcsUceAdapter.ERROR_NOT_ENABLED, waitForIntResult(errorQueue));
        } catch (Exception e) {
            fail("requestCapabilities with command error failed: " + e);
        } finally {
            capabilityQueue.clear();
            completeQueue.clear();
            errorQueue.clear();
        }

        requestAvailability(uceAdapter, sTestNumberUri, callback);

        // Verify that the callback "onError" is called with the error code NOT_ENABLED because
        // the carrier config KEY_ENABLE_PRESENCE_CAPABILITY_EXCHANGE_BOOL is still false.
        try {
            assertEquals(RcsUceAdapter.ERROR_NOT_ENABLED, waitForIntResult(errorQueue));
        } catch (Exception e) {
            fail("requestAvailability with command error failed: " + e);
        } finally {
            capabilityQueue.clear();
            completeQueue.clear();
            errorQueue.clear();
        }

        // Override another carrier config KEY_ENABLE_PRESENCE_CAPABILITY_EXCHANGE_BOOL
        bundle.putBoolean(CarrierConfigManager.Ims.KEY_ENABLE_PRESENCE_CAPABILITY_EXCHANGE_BOOL,
                true);
        overrideCarrierConfig(bundle);

        // Preapre the network response is 200 OK and the capabilities update
        int networkRespCode = 200;
        String networkRespReason = "OK";
        capabilityExchangeImpl.setSubscribeOperation((uris, cb) -> {
            cb.onNetworkResponse(networkRespCode, networkRespReason);
            cb.onNotifyCapabilitiesUpdate(pidfXmlList);
            cb.onTerminated("", 0L);
        });

        requestCapabilities(uceAdapter, numbers, callback);

        // Verify that the contact capability is received and the onCompleted is called.
        RcsContactUceCapability capability = waitForResult(capabilityQueue);
        verifyCapabilityResult(capability, sTestNumberUri, REQUEST_RESULT_FOUND, true, true);
        waitForResult(completeQueue);

        errorQueue.clear();
        completeQueue.clear();
        capabilityQueue.clear();
        removeTestContactFromEab();

        requestAvailability(uceAdapter, sTestNumberUri, callback);

        // Verify that the contact capability is received and the onCompleted is called.
        capability = waitForResult(capabilityQueue);
        verifyCapabilityResult(capability, sTestNumberUri, REQUEST_RESULT_FOUND, true, true);
        waitForResult(completeQueue);

        finishUceActivity();
        overrideCarrierConfig(null);
    }

    @Test
    public void testCapabilitiesRequestWithCmdError() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }
        ImsManager imsManager = getContext().getSystemService(ImsManager.class);
        RcsUceAdapter uceAdapter = imsManager.getImsRcsManager(sTestSub).getUceAdapter();
        assertNotNull("UCE adapter should not be null!", uceAdapter);

        // Connect to the TestImsService
        setupTestImsService(uceAdapter);

        // Stay in the foreground
        lunchUceActivity();

        List<Uri> contacts = Collections.singletonList(sTestNumberUri);

        TestRcsCapabilityExchangeImpl capabilityExchangeImpl = sServiceConnector
                .getCarrierService().getRcsFeature().getRcsCapabilityExchangeImpl();

        // Prepare queues to receive the callback
        BlockingQueue<Integer> errorQueue = new LinkedBlockingQueue<>();
        BlockingQueue<Long> retryAfterQueue = new LinkedBlockingQueue<>();
        RcsUceAdapter.CapabilitiesCallback callback = new RcsUceAdapter.CapabilitiesCallback() {
            @Override
            public void onCapabilitiesReceived(List<RcsContactUceCapability> capabilities) {
            }
            @Override
            public void onComplete() {
            }
            @Override
            public void onError(int errorCode, long retryAfterMilliseconds) {
                errorQueue.offer(errorCode);
                retryAfterQueue.offer(retryAfterMilliseconds);
            }
        };

        // Prepare a map and define each command error code and its associated result.
        Map<Integer, Integer> cmdErrorMap = new HashMap<>();
        cmdErrorMap.put(COMMAND_CODE_SERVICE_UNKNOWN, RcsUceAdapter.ERROR_GENERIC_FAILURE);
        cmdErrorMap.put(COMMAND_CODE_GENERIC_FAILURE, RcsUceAdapter.ERROR_GENERIC_FAILURE);
        cmdErrorMap.put(COMMAND_CODE_INVALID_PARAM, RcsUceAdapter.ERROR_GENERIC_FAILURE);
        cmdErrorMap.put(COMMAND_CODE_FETCH_ERROR, RcsUceAdapter.ERROR_GENERIC_FAILURE);
        cmdErrorMap.put(COMMAND_CODE_REQUEST_TIMEOUT, RcsUceAdapter.ERROR_REQUEST_TIMEOUT);
        cmdErrorMap.put(COMMAND_CODE_INSUFFICIENT_MEMORY, RcsUceAdapter.ERROR_INSUFFICIENT_MEMORY);
        cmdErrorMap.put(COMMAND_CODE_LOST_NETWORK_CONNECTION, RcsUceAdapter.ERROR_LOST_NETWORK);
        cmdErrorMap.put(COMMAND_CODE_NOT_SUPPORTED, RcsUceAdapter.ERROR_GENERIC_FAILURE);
        cmdErrorMap.put(COMMAND_CODE_NOT_FOUND, RcsUceAdapter.ERROR_NOT_FOUND);
        cmdErrorMap.put(COMMAND_CODE_SERVICE_UNAVAILABLE, RcsUceAdapter.ERROR_SERVER_UNAVAILABLE);
        cmdErrorMap.put(COMMAND_CODE_NO_CHANGE, RcsUceAdapter.ERROR_GENERIC_FAILURE);

        // Verify each command error code and the expected callback result
        cmdErrorMap.forEach((cmdError, expectedCallbackResult) -> {
            // Setup the capabilities request that will be failed with the given command error code
            capabilityExchangeImpl.setSubscribeOperation((uris, cb) -> {
                cb.onCommandError(cmdError);
            });

            requestCapabilities(uceAdapter, contacts, callback);

            // Verify that the callback "onError" is called with the expected error code.
            try {
                assertEquals(expectedCallbackResult.intValue(), waitForIntResult(errorQueue));
                assertEquals(0L, waitForLongResult(retryAfterQueue));
            } catch (Exception e) {
                fail("requestCapabilities with command error failed: " + e);
            } finally {
                errorQueue.clear();
                retryAfterQueue.clear();
            }

            requestAvailability(uceAdapter, sTestNumberUri, callback);

            // Verify that the callback "onError" is called with the expected error code.
            try {
                assertEquals(expectedCallbackResult.intValue(), waitForIntResult(errorQueue));
                assertEquals(0L, waitForLongResult(retryAfterQueue));
            } catch (Exception e) {
                fail("requestCapabilities with command error failed: " + e);
            } finally {
                errorQueue.clear();
                retryAfterQueue.clear();
            }
        });

        finishUceActivity();
        overrideCarrierConfig(null);
    }

    @Test
    public void testCapabilitiesRequestWithResponseError() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }
        ImsManager imsManager = getContext().getSystemService(ImsManager.class);
        RcsUceAdapter uceAdapter = imsManager.getImsRcsManager(sTestSub).getUceAdapter();
        assertNotNull("UCE adapter should not be null!", uceAdapter);

        // Connect to the ImsService
        setupTestImsService(uceAdapter);

        ArrayList<Uri> numbers = new ArrayList<>(1);
        numbers.add(sTestNumberUri);

        BlockingQueue<Integer> errorQueue = new LinkedBlockingQueue<>();
        BlockingQueue<Long> retryAfterQueue = new LinkedBlockingQueue<>();
        RcsUceAdapter.CapabilitiesCallback callback = new RcsUceAdapter.CapabilitiesCallback() {
            @Override
            public void onCapabilitiesReceived(List<RcsContactUceCapability> capabilities) {
            }
            @Override
            public void onComplete() {
            }
            @Override
            public void onError(int errorCode, long retryAfterMilliseconds) {
                errorQueue.offer(errorCode);
                retryAfterQueue.offer(retryAfterMilliseconds);
            }
        };

        Map<Entry<Integer, String>, Integer> networkRespError = new HashMap<>();
        // 408 Request Timeout
        networkRespError.put(new Entry<Integer, String>() {
            @Override
            public Integer getKey() {
                return 408;
            }
            @Override
            public String getValue() {
                return "Request Timeout";
            }
            @Override
            public String setValue(String value) {
                return value;
            }
        }, RcsUceAdapter.ERROR_REQUEST_TIMEOUT);

        // 423 Interval Too Short
        networkRespError.put(new Entry<Integer, String>() {
            @Override
            public Integer getKey() {
                return 423;
            }
            @Override
            public String getValue() {
                return "Interval Too Short";
            }
            @Override
            public String setValue(String value) {
                return value;
            }
        }, RcsUceAdapter.ERROR_GENERIC_FAILURE);

        // 500 Server Internal Error
        networkRespError.put(new Entry<Integer, String>() {
            @Override
            public Integer getKey() {
                return 500;
            }
            @Override
            public String getValue() {
                return "Service Unavailable";
            }
            @Override
            public String setValue(String value) {
                return value;
            }
        }, RcsUceAdapter.ERROR_SERVER_UNAVAILABLE);

        // 503 Service Unavailable
        networkRespError.put(new Entry<Integer, String>() {
            @Override
            public Integer getKey() {
                return 503;
            }
            @Override
            public String getValue() {
                return "Service Unavailable";
            }
            @Override
            public String setValue(String value) {
                return value;
            }
        }, RcsUceAdapter.ERROR_SERVER_UNAVAILABLE);

        // Stay in the foreground.
        lunchUceActivity();

        TestRcsCapabilityExchangeImpl capabilityExchangeImpl = sServiceConnector
                .getCarrierService().getRcsFeature().getRcsCapabilityExchangeImpl();

        networkRespError.forEach((networkResp, expectedCallbackResult) -> {
            // Set the capabilities request failed with the given SIP code (without Reason header)
            capabilityExchangeImpl.setSubscribeOperation((uris, cb) -> {
                cb.onNetworkResponse(networkResp.getKey(), networkResp.getValue());
            });

            requestCapabilities(uceAdapter, numbers, callback);
            // Verify that the callback "onError" is called with the expected error code.
            try {
                assertEquals(expectedCallbackResult.intValue(), waitForIntResult(errorQueue));
                assertEquals(0L, waitForLongResult(retryAfterQueue));
            } catch (Exception e) {
                fail("requestCapabilities with command error failed: " + e);
            } finally {
                errorQueue.clear();
                retryAfterQueue.clear();
            }

            requestAvailability(uceAdapter, sTestNumberUri, callback);

            // Verify that the callback "onError" is called with the expected error code.
            try {
                assertEquals(expectedCallbackResult.intValue(), waitForIntResult(errorQueue));
                assertEquals(0L, waitForLongResult(retryAfterQueue));
            } catch (Exception e) {
                fail("requestAvailability with command error failed: " + e);
            } finally {
                errorQueue.clear();
                retryAfterQueue.clear();
            }

            /*
             * Set the capabilities request failed with the given SIP code (with Reason header)
             */
            capabilityExchangeImpl.setSubscribeOperation((uris, cb) -> {
                int networkRespCode = 200;
                String networkReason = "OK";
                cb.onNetworkResponse(networkRespCode, networkReason,
                        networkResp.getKey(), networkResp.getValue());
            });

            requestCapabilities(uceAdapter, numbers, callback);

            // Verify that the callback "onError" is called with the expected error code.
            try {
                assertEquals(expectedCallbackResult.intValue(), waitForIntResult(errorQueue));
                assertEquals(0L, waitForLongResult(retryAfterQueue));
            } catch (Exception e) {
                fail("requestCapabilities with command error failed: " + e);
            } finally {
                errorQueue.clear();
                retryAfterQueue.clear();
            }

            requestAvailability(uceAdapter, sTestNumberUri, callback);

            // Verify that the callback "onError" is called with the expected error code.
            try {
                assertEquals(expectedCallbackResult.intValue(), waitForIntResult(errorQueue));
                assertEquals(0L, waitForLongResult(retryAfterQueue));
            } catch (Exception e) {
                fail("requestAvailability with command error failed: " + e);
            } finally {
                errorQueue.clear();
                retryAfterQueue.clear();
            }
        });

        // Set the capabilities request will be failed with the 403 sip code
        int networkResp = 403;
        String networkRespReason = "";
        capabilityExchangeImpl.setSubscribeOperation((uris, cb) -> {
            cb.onNetworkResponse(networkResp, networkRespReason);
        });

        requestAvailability(uceAdapter, sTestNumberUri, callback);

        // Verify that the callback "onError" is called with the error code FORBIDDEN
        try {
            assertEquals(RcsUceAdapter.ERROR_FORBIDDEN, waitForIntResult(errorQueue));
            assertEquals(0L, waitForLongResult(retryAfterQueue));
        } catch (Exception e) {
            fail("requestAvailability with command error failed: " + e);
        } finally {
            errorQueue.clear();
            retryAfterQueue.clear();
        }

        requestCapabilities(uceAdapter, numbers, callback);

        // Verify that the capabilities request is sill failed because the ImsService has returned
        // the 403 error before.
        try {
            assertEquals(RcsUceAdapter.ERROR_FORBIDDEN, waitForIntResult(errorQueue));
            assertEquals(0L, waitForLongResult(retryAfterQueue));
        } catch (Exception e) {
            fail("requestCapabilities with command error failed: " + e);
        } finally {
            errorQueue.clear();
            retryAfterQueue.clear();
        }

        finishUceActivity();
        overrideCarrierConfig(null);
    }

    @Test
    public void testRequestCapabilities() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }
        ImsManager imsManager = getContext().getSystemService(ImsManager.class);
        RcsUceAdapter uceAdapter = imsManager.getImsRcsManager(sTestSub).getUceAdapter();
        assertNotNull("UCE adapter should not be null!", uceAdapter);

        // Remove the test contact capabilities
        removeTestContactFromEab();

        // Connect to the ImsService
        setupTestImsService(uceAdapter);

        TestRcsCapabilityExchangeImpl capabilityExchangeImpl = sServiceConnector
                .getCarrierService().getRcsFeature().getRcsCapabilityExchangeImpl();

        ArrayList<Uri> numbers = new ArrayList<>(1);
        numbers.add(sTestNumberUri);

        BlockingQueue<Long> errorQueue = new LinkedBlockingQueue<>();
        BlockingQueue<Boolean> completeQueue = new LinkedBlockingQueue<>();
        BlockingQueue<RcsContactUceCapability> capabilityQueue = new LinkedBlockingQueue<>();
        RcsUceAdapter.CapabilitiesCallback callback = new RcsUceAdapter.CapabilitiesCallback() {
            @Override
            public void onCapabilitiesReceived(List<RcsContactUceCapability> capabilities) {
                capabilities.forEach(c -> capabilityQueue.offer(c));
            }
            @Override
            public void onComplete() {
                completeQueue.offer(true);
            }
            @Override
            public void onError(int errorCode, long retryAfterMilliseconds) {
                errorQueue.offer(new Long(errorCode));
                errorQueue.offer(retryAfterMilliseconds);
            }
        };

        // Prepare three contacts
        final Uri contact1 = sTestNumberUri;
        final Uri contact2 = sTestContact2Uri;
        final Uri contact3 = sTestContact3Uri;

        ArrayList<Uri> contacts = new ArrayList<>(3);
        contacts.add(contact1);
        contacts.add(contact2);
        contacts.add(contact3);

        ArrayList<String> pidfXmlList = new ArrayList<>(3);
        pidfXmlList.add(getPidfXmlData(contact1, true, true));
        pidfXmlList.add(getPidfXmlData(contact2, true, false));
        pidfXmlList.add(getPidfXmlData(contact3, false, false));

        // Setup the network response is 200 OK and notify capabilities update
        int networkRespCode = 200;
        String networkRespReason = "OK";
        capabilityExchangeImpl.setSubscribeOperation((uris, cb) -> {
            cb.onNetworkResponse(networkRespCode, networkRespReason);
            cb.onNotifyCapabilitiesUpdate(pidfXmlList);
            cb.onTerminated("", 0L);
        });

        // Stay in the foreground.
        lunchUceActivity();

        requestCapabilities(uceAdapter, contacts, callback);

        // Verify that all the three contact's capabilities are received
        RcsContactUceCapability capability = waitForResult(capabilityQueue);
        verifyCapabilityResult(capability, contact1, REQUEST_RESULT_FOUND, true, true);

        capability = waitForResult(capabilityQueue);
        verifyCapabilityResult(capability, contact2, REQUEST_RESULT_FOUND, true, false);

        capability = waitForResult(capabilityQueue);
        verifyCapabilityResult(capability, contact3, REQUEST_RESULT_FOUND, false, false);

        // Verify the onCompleted is called
        waitForResult(completeQueue);

        errorQueue.clear();
        completeQueue.clear();
        capabilityQueue.clear();
        removeTestContactFromEab();

        // Setup the callback that some of the contacts are terminated.
        capabilityExchangeImpl.setSubscribeOperation((uris, cb) -> {
            cb.onNetworkResponse(networkRespCode, networkRespReason);
            // Notify capabilities updated for the first contact
            String pidfXml = pidfXmlList.get(0);
            cb.onNotifyCapabilitiesUpdate(Collections.singletonList(pidfXml));

            List<Pair<Uri, String>> terminatedResources = new ArrayList<>();
            for (int i = 1; i < uris.size(); i++) {
                Pair<Uri, String> pair = Pair.create(uris.get(i), "noresource");
                terminatedResources.add(pair);
            }
            cb.onResourceTerminated(terminatedResources);
            cb.onTerminated("", 0L);
        });

        requestCapabilities(uceAdapter, contacts, callback);

        // Verify the first contact is found.
        capability = waitForResult(capabilityQueue);
        verifyCapabilityResult(capability, contact1, REQUEST_RESULT_FOUND, true, true);

        // Verify the reset contacts are not found.
        capability = waitForResult(capabilityQueue);
        verifyCapabilityResult(capability, contact2, REQUEST_RESULT_NOT_FOUND, true, false);

        capability = waitForResult(capabilityQueue);
        verifyCapabilityResult(capability, contact3, REQUEST_RESULT_NOT_FOUND, false, false);

        // Verify the onCompleted is called
        waitForResult(completeQueue);

        finishUceActivity();
        overrideCarrierConfig(null);
    }

    private void setupTestImsService(RcsUceAdapter uceAdapter) throws Exception {
        // Trigger carrier config changed
        PersistableBundle bundle = new PersistableBundle();
        bundle.putBoolean(CarrierConfigManager.Ims.KEY_ENABLE_PRESENCE_PUBLISH_BOOL, true);
        bundle.putBoolean(CarrierConfigManager.Ims.KEY_ENABLE_PRESENCE_CAPABILITY_EXCHANGE_BOOL,
                true);
        overrideCarrierConfig(bundle);

        // Enable the UCE setting.
        try {
            ShellIdentityUtils.invokeThrowableMethodWithShellPermissionsNoReturn(
                    uceAdapter, adapter -> adapter.setUceSettingEnabled(true), ImsException.class,
                    "android.permission.MODIFY_PHONE_STATE");
        } catch (SecurityException e) {
            fail("setUceSettingEnabled should succeed with MODIFY_PHONE_STATE.");
        } catch (ImsException e) {
            fail("setUceSettingEnabled failed with code " + e);
        }

        // Connect to the TestImsService
        connectTestImsService();
    }

    private String getPidfXmlData(Uri contact, boolean audioSupported, boolean videoSupported) {
        StringBuilder pidfBuilder = new StringBuilder();
        pidfBuilder.append("<?xml version='1.0' encoding='utf-8' standalone='yes' ?>")
                .append("<presence entity=\"").append(contact).append("\"")
                .append(" xmlns=\"urn:ietf:params:xml:ns:pidf\"")
                .append(" xmlns:op=\"urn:oma:xml:prs:pidf:oma-pres\"")
                .append(" xmlns:caps=\"urn:ietf:params:xml:ns:pidf:caps\">")
                .append("<tuple id=\"tid0\"><status><basic>open</basic></status>")
                .append("<op:service-description>")
                .append("<op:service-id>service_id_01</op:service-id>")
                .append("<op:version>1.0</op:version>")
                .append("<op:description>description_test1</op:description>")
                .append("</op:service-description>")
                .append("<caps:servcaps>")
                .append("<caps:audio>").append(audioSupported).append("</caps:audio>")
                .append("<caps:video>").append(videoSupported).append("</caps:video>")
                .append("</caps:servcaps>")
                .append("<contact>").append(contact).append("</contact>")
                .append("</tuple></presence>");
        return pidfBuilder.toString();
    }

    private void verifyCapabilityResult(RcsContactUceCapability resultCapability, Uri expectedUri,
            int expectedResult, boolean expectedAudioSupported, boolean expectedVideoSupported) {
        // Verify the contact URI
        assertEquals(expectedUri, resultCapability.getContactUri());

        // Verify the source type is the network type.
        assertEquals(RcsContactUceCapability.SOURCE_TYPE_NETWORK,
                resultCapability.getSourceType());

        // Verify the request result is expected.
        final int requestResult = resultCapability.getRequestResult();
        assertEquals(requestResult, expectedResult);

        // Return directly if the result is not found.
        if (requestResult == REQUEST_RESULT_NOT_FOUND) {
            return;
        }

        // Verify the mechanism is presence
        assertEquals(RcsContactUceCapability.CAPABILITY_MECHANISM_PRESENCE,
                resultCapability.getCapabilityMechanism());

        RcsContactPresenceTuple presenceTuple =
                resultCapability.getCapabilityTuple("service_id_01");
        assertNotNull("Contact Presence tuple should not be null!", presenceTuple);

        ServiceCapabilities capabilities = presenceTuple.getServiceCapabilities();
        assertNotNull("Service capabilities should not be null!", capabilities);

        // Verify if the audio is supported
        assertEquals(expectedAudioSupported, capabilities.isAudioCapable());

        // Verify if the video is supported
        assertEquals(expectedVideoSupported, capabilities.isVideoCapable());
    }

    private void registerUceObserver(Consumer<Uri> resultConsumer) {
        mUceObserver = new ContentObserver(new Handler(sHandlerThread.getLooper())) {
            @Override
            public void onChange(boolean selfChange, Uri uri) {
                resultConsumer.accept(uri);
            }
        };
        getContext().getContentResolver().registerContentObserver(LISTENER_URI,
                true /*notifyForDecendents*/, mUceObserver);
    }

    private void unregisterUceObserver() {
        if (mUceObserver != null) {
            getContext().getContentResolver().unregisterContentObserver(mUceObserver);
        }
    }

    private int waitForIntResult(BlockingQueue<Integer> queue) throws Exception {
        Integer result = queue.poll(ImsUtils.TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        return result != null ? result : Integer.MAX_VALUE;
    }

    private long waitForLongResult(BlockingQueue<Long> queue) throws Exception {
        Long result = queue.poll(ImsUtils.TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        return result != null ? result : Long.MAX_VALUE;
    }

    private <T> T waitForResult(BlockingQueue<T> queue) throws Exception {
        return queue.poll(ImsUtils.TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);
    }

    private static Context getContext() {
        return InstrumentationRegistry.getInstrumentation().getContext();
    }

    private void connectTestImsService() throws Exception {
        assertTrue(sServiceConnector.connectCarrierImsService(new ImsFeatureConfiguration.Builder()
                .addFeature(sTestSlot, ImsFeature.FEATURE_MMTEL)
                .addFeature(sTestSlot, ImsFeature.FEATURE_RCS)
                .build()));

        // The RcsFeature is created when the ImsService is bound. If it wasn't created, then the
        // Framework did not call it.
        assertTrue("Did not receive createRcsFeature", sServiceConnector.getCarrierService()
                .waitForLatchCountdown(TestImsService.LATCH_CREATE_RCS));
        assertTrue("Did not receive RcsFeature#onReady", sServiceConnector.getCarrierService()
                .waitForLatchCountdown(TestImsService.LATCH_RCS_READY));
        // Make sure the RcsFeature was created in the test service.
        assertNotNull("Device ImsService created, but TestDeviceImsService#createRcsFeature was not"
                + "called!", sServiceConnector.getCarrierService().getRcsFeature());
        assertTrue("Did not receive RcsFeature#setCapabilityExchangeEventListener",
                sServiceConnector.getCarrierService().waitForLatchCountdown(
                        TestImsService.LATCH_UCE_LISTENER_SET));
        int serviceSlot = sServiceConnector.getCarrierService().getRcsFeature().getSlotIndex();
        assertEquals("The slot specified for the test (" + sTestSlot + ") does not match the "
                        + "assigned slot (" + serviceSlot + "+ for the associated RcsFeature",
                sTestSlot, serviceSlot);
    }

    private static void initPhoneNumbers() {
        // Generate a random phone number
        sTestPhoneNumber = generateRandomPhoneNumber();
        sTestNumberUri = Uri.fromParts(PhoneAccount.SCHEME_TEL, sTestPhoneNumber, null);

        sTestContact2 = generateRandomContact(5);
        sTestContact2Uri = Uri.fromParts(PhoneAccount.SCHEME_SIP, sTestContact2, null);

        sTestContact3 = generateRandomContact(6);
        sTestContact3Uri = Uri.fromParts(PhoneAccount.SCHEME_SIP, sTestContact3, null);
    }

    private static String generateRandomPhoneNumber() {
        Random random = new Random();
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            builder.append(random.nextInt(10));
        }
        return builder.toString();
    }

    private static String generateRandomContact(int length) {
        Random random = new Random();
        StringBuilder builder = new StringBuilder("TestContact");
        for (int i = 0; i < length; i++) {
            int asciiNum = random.nextInt(26) + 65;  // ascii 65
            builder.append((char) asciiNum);
        }
        return builder.toString();
    }

    private static void overrideCarrierConfig(PersistableBundle bundle) throws Exception {
        CarrierConfigManager carrierConfigManager = InstrumentationRegistry.getInstrumentation()
                .getContext().getSystemService(CarrierConfigManager.class);
        sReceiver.clearQueue();
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(carrierConfigManager,
                (m) -> m.overrideConfig(sTestSub, bundle));
        sReceiver.waitForCarrierConfigChanged();
    }

    private static void removeTestContactFromEab() {
        try {
            StringBuilder builder = new StringBuilder();
            builder.append(sTestPhoneNumber)
                    .append(",").append(sTestContact2)
                    .append(",").append(sTestContact3);
            sServiceConnector.removeEabContacts(sTestSlot, builder.toString());
        } catch (Exception e) {
            Log.w("RcsUceAdapterTest", "Cannot remove test contacts from eab database: " + e);
        }
    }

    private void requestCapabilities(RcsUceAdapter uceAdapter, List<Uri> numbers,
            RcsUceAdapter.CapabilitiesCallback callback) {
        try {
            ShellIdentityUtils.invokeThrowableMethodWithShellPermissionsNoReturn(
                    uceAdapter,
                    adapter -> adapter.requestCapabilities(numbers, Runnable::run, callback),
                    ImsException.class,
                    "android.permission.ACCESS_RCS_USER_CAPABILITY_EXCHANGE");
        } catch (SecurityException e) {
            fail("requestCapabilities should succeed with ACCESS_RCS_USER_CAPABILITY_EXCHANGE. "
                    + "Exception: " + e);
        } catch (ImsException e) {
            fail("requestCapabilities failed " + e);
        }
    }

    private void requestAvailability(RcsUceAdapter uceAdapter, Uri number,
            RcsUceAdapter.CapabilitiesCallback callback) {
        try {
            ShellIdentityUtils.invokeThrowableMethodWithShellPermissionsNoReturn(
                    uceAdapter,
                    adapter -> adapter.requestAvailability(number, Runnable::run, callback),
                    ImsException.class,
                    "android.permission.ACCESS_RCS_USER_CAPABILITY_EXCHANGE");
        } catch (SecurityException e) {
            fail("requestAvailability should succeed with ACCESS_RCS_USER_CAPABILITY_EXCHANGE. "
                    + "Exception: " + e);
        } catch (ImsException e) {
            fail("requestAvailability failed " + e);
        }
    }

    private void lunchUceActivity() throws Exception {
        final CountDownLatch countdownLatch = new CountDownLatch(1);
        final Intent activityIntent = new Intent(getContext(), UceActivity.class);
        activityIntent.setAction(Intent.ACTION_MAIN);
        activityIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        activityIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        UceActivity.setCountDownLatch(countdownLatch);
        getContext().startActivity(activityIntent);
        countdownLatch.await(5000, TimeUnit.MILLISECONDS);
    }

    private void finishUceActivity() {
        final Intent finishIntent = new Intent(getContext(), UceActivity.class);
        finishIntent.setAction(UceActivity.ACTION_FINISH);
        finishIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        finishIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        finishIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        getContext().startActivity(finishIntent);
    }
}
