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

package android.os.cts;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import android.Manifest;
import android.content.Context;
import android.content.IntentFilter;
import android.os.PowerManager;
import android.platform.test.annotations.AppModeFull;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.CallbackAsserter;
import com.android.compatibility.common.util.SystemUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class LowPowerStandbyTest {
    private static final int BROADCAST_TIMEOUT_SEC = 5;

    private PowerManager mPowerManager;
    private boolean mOriginalEnabled;

    @Before
    public void setUp() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        mPowerManager = context.getSystemService(PowerManager.class);
        mOriginalEnabled = mPowerManager.isLowPowerStandbyEnabled();
    }

    @After
    public void tearDown() throws Exception {
        if (mPowerManager != null) {
            SystemUtil.runWithShellPermissionIdentity(() -> {
                mPowerManager.setLowPowerStandbyEnabled(mOriginalEnabled);
            }, Manifest.permission.MANAGE_LOW_POWER_STANDBY);
        }
    }

    @Test
    public void testSetLowPowerStandbyEnabled_withoutPermission_throwsSecurityException() {
        try {
            mPowerManager.setLowPowerStandbyEnabled(false);
            fail("PowerManager.setLowPowerStandbyEnabled() didn't throw SecurityException as "
                    + "expected");
        } catch (SecurityException e) {
            // expected
        }
    }

    @Test
    @AppModeFull(reason = "Instant apps cannot hold MANAGE_LOW_POWER_STANDBY permission")
    public void testSetLowPowerStandbyEnabled_withPermission_doesNotThrowsSecurityException() {
        SystemUtil.runWithShellPermissionIdentity(() -> {
            mPowerManager.setLowPowerStandbyEnabled(false);
        }, Manifest.permission.MANAGE_LOW_POWER_STANDBY);
    }

    @Test
    @AppModeFull(reason = "Instant apps cannot hold MANAGE_LOW_POWER_STANDBY permission")
    public void testSetLowPowerStandbyEnabled_reflectedByIsLowPowerStandbyEnabled() {
        SystemUtil.runWithShellPermissionIdentity(() -> {
            assumeTrue(mPowerManager.isLowPowerStandbySupported());

            mPowerManager.setLowPowerStandbyEnabled(true);
            assertTrue(mPowerManager.isLowPowerStandbyEnabled());

            mPowerManager.setLowPowerStandbyEnabled(false);
            assertFalse(mPowerManager.isLowPowerStandbyEnabled());
        }, Manifest.permission.MANAGE_LOW_POWER_STANDBY);
    }

    @Test
    @AppModeFull(reason = "Instant apps cannot hold MANAGE_LOW_POWER_STANDBY permission")
    public void testSetLowPowerStandbyEnabled_sendsBroadcast() {
        SystemUtil.runWithShellPermissionIdentity(() -> {
            assumeTrue(mPowerManager.isLowPowerStandbySupported());

            mPowerManager.setLowPowerStandbyEnabled(false);

            CallbackAsserter broadcastAsserter = CallbackAsserter.forBroadcast(
                    new IntentFilter(PowerManager.ACTION_LOW_POWER_STANDBY_ENABLED_CHANGED));
            mPowerManager.setLowPowerStandbyEnabled(true);
            broadcastAsserter.assertCalled(
                    "ACTION_LOW_POWER_STANDBY_ENABLED_CHANGED broadcast not received",
                    BROADCAST_TIMEOUT_SEC);

            broadcastAsserter = CallbackAsserter.forBroadcast(
                    new IntentFilter(PowerManager.ACTION_LOW_POWER_STANDBY_ENABLED_CHANGED));
            mPowerManager.setLowPowerStandbyEnabled(false);
            broadcastAsserter.assertCalled(
                    "ACTION_LOW_POWER_STANDBY_ENABLED_CHANGED broadcast not received",
                    BROADCAST_TIMEOUT_SEC);
        }, Manifest.permission.MANAGE_LOW_POWER_STANDBY);
    }
}
