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

package android.telephony.cts.telephonypermission;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import android.app.UiAutomation;
import android.content.Context;
import android.content.pm.PackageManager;
import android.platform.test.annotations.AppModeFull;
import android.telecom.PhoneAccount;
import android.telecom.TelecomManager;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.telephony.emergency.EmergencyNumber;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test TelephonyManager APIs with READ_PHONE_STATE Permission.
 */
@RunWith(AndroidJUnit4.class)
@AppModeFull(reason = "Cannot grant the runtime permission in instant app mode")
public class TelephonyManagerReadPhoneStatePermissionTest {

    private boolean mHasTelephony;
    TelephonyManager mTelephonyManager = null;
    TelecomManager mTelecomManager = null;

    @Before
    public void setUp() throws Exception {
        mHasTelephony = getContext().getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_TELEPHONY);
        mTelephonyManager =
                (TelephonyManager) getContext().getSystemService(Context.TELEPHONY_SERVICE);
        assertNotNull(mTelephonyManager);
        mTelecomManager =
                (TelecomManager) getContext().getSystemService(Context.TELECOM_SERVICE);
        assertNotNull(mTelecomManager);
    }

    public static void grantUserReadPhoneStatePermission() {
        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        uiAutomation.grantRuntimePermission(getContext().getPackageName(),
                android.Manifest.permission.READ_PHONE_STATE);
    }

    public static void revokeUserReadPhoneStatePermission() {
        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        uiAutomation.revokeRuntimePermission(getContext().getPackageName(),
                android.Manifest.permission.READ_PHONE_STATE);
    }

    /**
     * Verify that TelephonyManager APIs requiring READ_PHONE_STATE Permission must work.
     * <p>
     * Requires Permission:
     * {@link android.Manifest.permission#READ_PHONE_STATE}.
     *
     * APIs list:
     * getDeviceSoftwareVersion()
     * getCarrierConfig()
     * getNetworkType()
     * getDataNetworkType()
     * getVoiceNetworkType()
     * getGroupIdLevel1()
     * getLine1AlphaTag()
     * getVoiceMailNumber()
     * getVisualVoicemailPackageName()
     * getVoiceMailAlphaTag()
     * getForbiddenPlmns()
     * isDataRoamingEnabled()
     * getSubscriptionId(@NonNull PhoneAccountHandle phoneAccountHandle)
     * getServiceState()
     * getEmergencyNumberList()
     * getEmergencyNumberList(@EmergencyServiceCategories int categories)
     * getPreferredOpportunisticDataSubscription()
     * isModemEnabledForSlot(int slotIndex)
     * isMultiSimSupported()
     * doesSwitchMultiSimConfigTriggerReboot()
     */
    @Test
    public void testTelephonyManagersAPIsRequiringReadPhoneStatePermissions() {
        if (!mHasTelephony) {
            return;
        }

        grantUserReadPhoneStatePermission();

        int subId = mTelephonyManager.getSubscriptionId();

        try {
            mTelephonyManager.getNetworkType();
        } catch (SecurityException e) {
            fail("getNetworkType() must not throw a SecurityException with READ_PHONE_STATE" + e);
        }
        try {
            mTelephonyManager.getDeviceSoftwareVersion();
        } catch (SecurityException e) {
            fail("getDeviceSoftwareVersion() must not throw a SecurityException"
                    + " with READ_PHONE_STATE" + e);
        }
        try {
            mTelephonyManager.getCarrierConfig();
        } catch (SecurityException e) {
            fail("getCarrierConfig() must not throw a SecurityException"
                    + " with READ_PHONE_STATE" + e);
        }
        try {
            mTelephonyManager.getDataNetworkType();
        } catch (SecurityException e) {
            fail("getDataNetworkType() must not throw a SecurityException"
                    + " with READ_PHONE_STATE" + e);
        }
        try {
            mTelephonyManager.getVoiceNetworkType();
        } catch (SecurityException e) {
            fail("getVoiceNetworkType() must not throw a SecurityException"
                    + " with READ_PHONE_STATE" + e);
        }
        try {
            mTelephonyManager.getGroupIdLevel1();
        } catch (SecurityException e) {
            fail("getGroupIdLevel1() must not throw a SecurityException"
                    + " with READ_PHONE_STATE" + e);
        }
        try {
            mTelephonyManager.getLine1AlphaTag();
        } catch (SecurityException e) {
            fail("getLine1AlphaTag() must not throw a SecurityException"
                    + " with READ_PHONE_STATE" + e);
        }
        try {
            mTelephonyManager.getVoiceMailNumber();
        } catch (SecurityException e) {
            fail("getVoiceMailNumber() must not throw a SecurityException"
                    + " with READ_PHONE_STATE" + e);
        }
        try {
            mTelephonyManager.getVisualVoicemailPackageName();
        } catch (SecurityException e) {
            fail("getVisualVoicemailPackageName() must not throw a SecurityException"
                    + " with READ_PHONE_STATE" + e);
        }
        try {
            mTelephonyManager.getVoiceMailAlphaTag();
        } catch (SecurityException e) {
            fail("getVoiceMailAlphaTag() must not throw a SecurityException"
                    + " with READ_PHONE_STATE" + e);
        }
        try {
            mTelephonyManager.getForbiddenPlmns();
        } catch (SecurityException e) {
            fail("getForbiddenPlmns() must not throw a SecurityException"
                    + " with READ_PHONE_STATE" + e);
        }
        try {
            mTelephonyManager.isDataRoamingEnabled();
        } catch (SecurityException e) {
            fail("isDataRoamingEnabled() must not throw a SecurityException"
                    + " with READ_PHONE_STATE" + e);
        }
        try {
            mTelephonyManager.getSubscriptionId(
                    mTelecomManager.getDefaultOutgoingPhoneAccount(PhoneAccount.SCHEME_TEL));
        } catch (SecurityException e) {
            fail("getSubscriptionId(phoneAccountHandle) must not throw a SecurityException"
                    + " with READ_PHONE_STATE" + e);
        }
        try {
            mTelephonyManager.getServiceState();
        } catch (SecurityException e) {
            fail("getServiceState() must not throw a SecurityException"
                    + " with READ_PHONE_STATE" + e);
        }
        try {
            mTelephonyManager.getEmergencyNumberList();
        } catch (SecurityException e) {
            fail("getEmergencyNumberList() must not throw a SecurityException"
                    + " with READ_PHONE_STATE" + e);
        }
        try {
            mTelephonyManager.getEmergencyNumberList(
                    EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_POLICE);
        } catch (SecurityException e) {
            fail("getEmergencyNumberList(EMERGENCY_SERVICE_CATEGORY_POLICE) must"
                    + " not throw a SecurityException with READ_PHONE_STATE" + e);
        }
        try {
            mTelephonyManager.getPreferredOpportunisticDataSubscription();
        } catch (SecurityException e) {
            fail("getPreferredOpportunisticDataSubscription() must not throw"
                    + " a SecurityException with READ_PHONE_STATE" + e);
        }
        try {
            mTelephonyManager.isModemEnabledForSlot(SubscriptionManager.getSlotIndex(subId));
        } catch (SecurityException e) {
            fail("isModemEnabledForSlot(slotIndex) must not throw a SecurityException"
                    + " with READ_PHONE_STATE" + e);
        }
        try {
            mTelephonyManager.isMultiSimSupported();
        } catch (SecurityException e) {
            fail("isMultiSimSupported() must not throw a SecurityException"
                    + " with READ_PHONE_STATE" + e);
        }
        try {
            mTelephonyManager.doesSwitchMultiSimConfigTriggerReboot();
        } catch (SecurityException e) {
            fail("doesSwitchMultiSimConfigTriggerReboot() must not throw a SecurityException"
                    + " with READ_PHONE_STATE" + e);
        }

        revokeUserReadPhoneStatePermission();
    }

    private static Context getContext() {
        return InstrumentationRegistry.getContext();
    }
}
