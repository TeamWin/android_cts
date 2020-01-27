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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import android.content.Context;
import android.net.Uri;
import android.os.Looper;
import android.telecom.PhoneAccount;
import android.telephony.SubscriptionManager;
import android.telephony.ims.ImsException;
import android.telephony.ims.ImsManager;
import android.telephony.ims.RcsUceAdapter;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.ShellIdentityUtils;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;

@RunWith(AndroidJUnit4.class)
public class RcsUceAdapterTest {

    private static int sTestSub = SubscriptionManager.INVALID_SUBSCRIPTION_ID;
    private static final Uri TEST_NUMBER_URI =
            Uri.fromParts(PhoneAccount.SCHEME_TEL, "6505551212", null);

    @BeforeClass
    public static void beforeAllTests() {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }

        sTestSub = ImsUtils.getPreferredActiveSubId();

        if (Looper.getMainLooper() == null) {
            Looper.prepareMainLooper();
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
                    "android.permission.READ_PRIVILEGED_PHONE_STATE");
            assertNotNull(isEnabled);

            boolean userSetIsEnabled = isEnabled;
            ShellIdentityUtils.invokeThrowableMethodWithShellPermissionsNoReturn(
                    adapter, a -> a.setUceSettingEnabled(!userSetIsEnabled), ImsException.class,
                    "android.permission.MODIFY_PHONE_STATE");


            Boolean setResult = ShellIdentityUtils.invokeThrowableMethodWithShellPermissions(
                    adapter, RcsUceAdapter::isUceSettingEnabled, ImsException.class,
                    "android.permission.READ_PRIVILEGED_PHONE_STATE");
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

        // requestCapabilities
        ArrayList<Uri> numbers = new ArrayList<>(1);
        numbers.add(TEST_NUMBER_URI);
        try {
            ShellIdentityUtils.invokeThrowableMethodWithShellPermissionsNoReturn(uceAdapter,
                    (m) -> m.requestCapabilities(Runnable::run, numbers,
                            new RcsUceAdapter.CapabilitiesCallback() {
                            }), ImsException.class,
                    "android.permission.READ_PRIVILEGED_PHONE_STATE");
        } catch (SecurityException e) {
            fail("requestCapabilities should succeed with READ_PRIVILEGED_PHONE_STATE.");
        } catch (ImsException e) {
            // unsupported is a valid fail cause.
            if (e.getCode() != ImsException.CODE_ERROR_UNSUPPORTED_OPERATION) {
                fail("request capabilities failed with code " + e.getCode());
            }
        }

        try {
            uceAdapter.requestCapabilities(Runnable::run, numbers,
                    new RcsUceAdapter.CapabilitiesCallback() {});
            fail("requestCapabilities should require READ_PRIVILEGED_PHONE_STATE.");
        } catch (SecurityException e) {
            //expected
        }

        // getUcePublishState
        try {
            Integer result = ShellIdentityUtils.invokeThrowableMethodWithShellPermissions(
                    uceAdapter, RcsUceAdapter::getUcePublishState, ImsException.class,
                    "android.permission.READ_PRIVILEGED_PHONE_STATE");
            assertNotNull("result from getUcePublishState should not be null", result);
        } catch (SecurityException e) {
            fail("getUcePublishState should succeed with READ_PRIVILEGED_PHONE_STATE.");
        } catch (ImsException e) {
            // unsupported is a valid fail cause.
            if (e.getCode() != ImsException.CODE_ERROR_UNSUPPORTED_OPERATION) {
                fail("getUcePublishState failed with code " + e.getCode());
            }
        }
        try {
            uceAdapter.getUcePublishState();
            fail("requestCapabilities should require READ_PRIVILEGED_PHONE_STATE.");
        } catch (SecurityException e) {
            //expected
        }

        //isUceSettingEnabled
        Boolean isUceSettingEnabledResult = null;
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
        try {
            uceAdapter.getUcePublishState();
            fail("isUceSettingEnabled should require READ_PRIVILEGED_PHONE_STATE.");
        } catch (SecurityException e) {
            //expected
        }

        //setUceSettingEnabled
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

        try {
            uceAdapter.requestCapabilities(Runnable::run, numbers,
                    new RcsUceAdapter.CapabilitiesCallback() {});
            fail("setUceSettingEnabled should require MODIFY_PHONE_STATE.");
        } catch (SecurityException e) {
            //expected
        }
    }

    private static Context getContext() {
        return InstrumentationRegistry.getInstrumentation().getContext();
    }
}
