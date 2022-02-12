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

import static android.os.PowerManager.PARTIAL_WAKE_LOCK;
import static android.os.PowerManager.SYSTEM_WAKELOCK;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.platform.test.annotations.AppModeFull;

import androidx.annotation.NonNull;
import androidx.test.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.EnsureHasPermission;
import com.android.compatibility.common.util.BlockingBroadcastReceiver;
import com.android.compatibility.common.util.CallbackAsserter;
import com.android.compatibility.common.util.ProtoUtils;
import com.android.compatibility.common.util.SystemUtil;
import com.android.server.power.nano.PowerManagerServiceDumpProto;
import com.android.server.power.nano.WakeLockProto;

import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;

@RunWith(BedsteadJUnit4.class)
public class LowPowerStandbyTest {
    private static final int BROADCAST_TIMEOUT_SEC = 3;

    private static final String SYSTEM_WAKE_LOCK_TAG = "LowPowerStandbyTest:KeepSystemAwake";
    private static final String TEST_WAKE_LOCK_TAG = "LowPowerStandbyTest:TestWakeLock";

    @ClassRule
    @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    private Context mContext;
    private PowerManager mPowerManager;
    private boolean mOriginalEnabled;

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();
        mPowerManager = mContext.getSystemService(PowerManager.class);
        mOriginalEnabled = mPowerManager.isLowPowerStandbyEnabled();
    }

    @After
    public void tearDown() throws Exception {
        if (mPowerManager != null) {
            SystemUtil.runWithShellPermissionIdentity(() -> {
                wakeUp();
                mPowerManager.setLowPowerStandbyEnabled(mOriginalEnabled);
                mPowerManager.forceLowPowerStandbyActive(false);
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
    @EnsureHasPermission(Manifest.permission.MANAGE_LOW_POWER_STANDBY)
    public void testSetLowPowerStandbyEnabled_withPermission_doesNotThrowsSecurityException() {
        mPowerManager.setLowPowerStandbyEnabled(false);
    }

    @Test
    @AppModeFull(reason = "Instant apps cannot hold MANAGE_LOW_POWER_STANDBY permission")
    @EnsureHasPermission(Manifest.permission.MANAGE_LOW_POWER_STANDBY)
    public void testSetLowPowerStandbyEnabled_reflectedByIsLowPowerStandbyEnabled() {
        assumeTrue(mPowerManager.isLowPowerStandbySupported());

        mPowerManager.setLowPowerStandbyEnabled(true);
        assertTrue(mPowerManager.isLowPowerStandbyEnabled());

        mPowerManager.setLowPowerStandbyEnabled(false);
        assertFalse(mPowerManager.isLowPowerStandbyEnabled());
    }

    @Test
    @AppModeFull(reason = "Instant apps cannot hold MANAGE_LOW_POWER_STANDBY permission")
    @EnsureHasPermission(Manifest.permission.MANAGE_LOW_POWER_STANDBY)
    public void testSetLowPowerStandbyEnabled_sendsBroadcast() throws Exception {
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
    }

    @Test
    @AppModeFull(reason = "Instant apps cannot hold MANAGE_LOW_POWER_STANDBY permission")
    @EnsureHasPermission({Manifest.permission.MANAGE_LOW_POWER_STANDBY,
            Manifest.permission.DEVICE_POWER})
    public void testLowPowerStandby_wakelockIsDisabled() throws Exception {
        assumeTrue(mPowerManager.isLowPowerStandbySupported());

        // Keep system awake with system wakelock
        WakeLock systemWakeLock = mPowerManager.newWakeLock(PARTIAL_WAKE_LOCK | SYSTEM_WAKELOCK,
                SYSTEM_WAKE_LOCK_TAG);
        systemWakeLock.acquire();

        // Acquire test wakelock, which should be disabled by LPS
        WakeLock testWakeLock = mPowerManager.newWakeLock(PARTIAL_WAKE_LOCK,
                TEST_WAKE_LOCK_TAG);
        testWakeLock.acquire();

        mPowerManager.setLowPowerStandbyEnabled(true);
        goToSleep();
        mPowerManager.forceLowPowerStandbyActive(true);

        assertFalse("System wakelock is disabled",
                isWakeLockDisabled(SYSTEM_WAKE_LOCK_TAG));
        assertTrue("Test wakelock not disabled", isWakeLockDisabled(TEST_WAKE_LOCK_TAG));
    }

    private void goToSleep() throws Exception {
        final BlockingBroadcastReceiver screenOffReceiver = new BlockingBroadcastReceiver(mContext,
                Intent.ACTION_SCREEN_OFF);
        screenOffReceiver.register();

        executeShellCommand("input keyevent SLEEP");

        screenOffReceiver.awaitForBroadcast(1000);
        screenOffReceiver.unregisterQuietly();
    }

    private void wakeUp() throws Exception {
        final BlockingBroadcastReceiver screenOnReceiver = new BlockingBroadcastReceiver(mContext,
                Intent.ACTION_SCREEN_ON);
        screenOnReceiver.register();

        executeShellCommand("input keyevent WAKEUP");

        screenOnReceiver.awaitForBroadcast(1000);
        screenOnReceiver.unregisterQuietly();
    }

    private boolean isWakeLockDisabled(@NonNull String tag) throws Exception {
        final PowerManagerServiceDumpProto powerManagerServiceDump = getPowerManagerDump();
        for (WakeLockProto wakelock : powerManagerServiceDump.wakeLocks) {
            if (tag.equals(wakelock.tag)) {
                return wakelock.isDisabled;
            }
        }
        return false;
    }

    private static PowerManagerServiceDumpProto getPowerManagerDump() throws Exception {
        return ProtoUtils.getProto(InstrumentationRegistry.getInstrumentation().getUiAutomation(),
                PowerManagerServiceDumpProto.class, "dumpsys power --proto");
    }

    private String executeShellCommand(String command) throws IOException {
        UiDevice uiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        return uiDevice.executeShellCommand(command);
    }
}
