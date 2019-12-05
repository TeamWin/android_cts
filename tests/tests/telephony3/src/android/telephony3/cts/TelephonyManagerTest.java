/*
 * Copyright (C) 2018 The Android Open Source Project
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

package android.telephony3.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import android.content.Context;
import android.os.Build;
import android.telephony.TelephonyManager;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.ApiLevelUtil;
import com.android.compatibility.common.util.ShellIdentityUtils;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Verifies the APIs for apps with the READ_PHONE_STATE permission targeting pre-Q.
 *
 * @see android.telephony.cts.TelephonyManagerTest
 */
@RunWith(AndroidJUnit4.class)
public class TelephonyManagerTest {
    private TelephonyManager mTelephonyManager;

    private static final boolean HAS_Q_API = ApiLevelUtil.isAtLeast(Build.VERSION_CODES.Q);

    public static boolean isAtLeastQApi() {
        return HAS_Q_API;
    }

    @Before
    public void setUp() throws Exception {
        // Test device identifier access for apps with the READ_PHONE_STATE
        // permission targeting pre-Q.
        if (HAS_Q_API) {
            return;
        }
        mTelephonyManager =
                (TelephonyManager) InstrumentationRegistry.getContext().getSystemService(
                        Context.TELEPHONY_SERVICE);
    }

    @Test
    public void testDeviceIdentifiersAreNotAccessible() throws Exception {
        // Test device identifier access for apps with the READ_PHONE_STATE
        // permission targeting pre-Q.
        if (HAS_Q_API) {
            return;
        }

        // Apps with the READ_PHONE_STATE permission should no longer have access to device
        // identifiers. If an app's target SDK is less than Q and it has been granted the
        // READ_PHONE_STATE permission then a null value should be returned when querying for device
        // identifiers; this test verifies a null value is returned for device identifier queries.
        try {
            assertNull(
                    "An app targeting pre-Q with the READ_PHONE_STATE permission granted must "
                            + "receive null when invoking getDeviceId",
                    ShellIdentityUtils.invokeMethodWithShellPermissions(
                            mTelephonyManager, tm -> tm.getDeviceId()));
            assertNull(
                    "An app targeting pre-Q with the READ_PHONE_STATE permission granted must "
                            + "receive null when invoking getImei",
                    ShellIdentityUtils.invokeMethodWithShellPermissions(
                            mTelephonyManager, tm -> tm.getImei()));
            assertNull(
                    "An app targeting pre-Q with the READ_PHONE_STATE permission granted must "
                            + "receive null when invoking getMeid",
                    ShellIdentityUtils.invokeMethodWithShellPermissions(
                            mTelephonyManager, tm -> tm.getMeid()));
            assertNull(
                    "An app targeting pre-Q with the READ_PHONE_STATE permission granted must "
                            + "receive null when invoking getSubscriberId",
                    ShellIdentityUtils.invokeMethodWithShellPermissions(
                            mTelephonyManager, tm -> tm.getSubscriberId()));
            assertNull(
                    "An app targeting pre-Q with the READ_PHONE_STATE permission granted must "
                            + "receive null when invoking getSimSerialNumber",
                    ShellIdentityUtils.invokeMethodWithShellPermissions(
                            mTelephonyManager, tm -> tm.getSimSerialNumber()));
            assertNull(
                    "An app targeting pre-Q with the READ_PHONE_STATE permission granted must "
                            + "receive null when invoking getNai",
                    ShellIdentityUtils.invokeMethodWithShellPermissions(
                            mTelephonyManager, tm -> tm.getNai()));
            // Since Build.getSerial is not documented to return null in previous releases this test
            // verifies that the Build.UNKNOWN value is returned when the caller does not have
            // permission to access the device identifier.
            assertEquals(
                    "An app targeting pre-Q with the READ_PHONE_STATE permission granted must "
                            + "receive " + Build.UNKNOWN + " when invoking Build.getSerial",
                    Build.getSerial(), Build.UNKNOWN);
        } catch (SecurityException e) {
            fail("An app targeting pre-Q with the READ_PHONE_STATE permission granted must "
                    + "receive null (or "
                    + Build.UNKNOWN
                    + " for Build#getSerial) when querying for device identifiers, caught "
                    + "SecurityException instead: " + e);
        }
    }
}
