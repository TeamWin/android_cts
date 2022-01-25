/*
 * Copyright (C) 2019 The Android Open Source Project
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
 * limitations under the License
 */

package android.telecom.cts;

import static com.android.compatibility.common.util.ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn;
import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;

import android.app.AppOpsManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelUuid;
import android.telecom.CallEndpoint;
import android.telecom.TelecomManager;

import androidx.test.InstrumentationRegistry;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class TelecomManagerTest extends BaseTelecomTestWithMockServices {

    private static final String TEST_EMERGENCY_NUMBER = "5553637";
    private static final Uri TEST_EMERGENCY_URI = Uri.fromParts("tel", TEST_EMERGENCY_NUMBER, null);
    private static final String CTS_TELECOM_PKG = TelecomManagerTest.class.getPackage().getName();

    private static final CallEndpoint CALL_ENDPOINT_1 = new CallEndpoint(
            ParcelUuid.fromString(UUID.randomUUID().toString()), "cts endpoint 1",
            CallEndpoint.ENDPOINT_TYPE_TETHERED,
            new ComponentName(CTS_TELECOM_PKG, TelecomManagerTest.class.getName()));
    private static final CallEndpoint CALL_ENDPOINT_2 = new CallEndpoint(
            ParcelUuid.fromString(UUID.randomUUID().toString()), "cts endpoint 2",
            CallEndpoint.ENDPOINT_TYPE_TETHERED,
            new ComponentName(CTS_TELECOM_PKG, TelecomManagerTest.class.getName()));

    public void testGetCurrentTtyMode() {
        if (!mShouldTestTelecom) {
            return;
        }

        LinkedBlockingQueue<Integer> queue = new LinkedBlockingQueue(1);
        runWithShellPermissionIdentity(() ->
                queue.put(mTelecomManager.getCurrentTtyMode()));
        try {
            int currentTtyMode = queue.poll(TestUtils.WAIT_FOR_STATE_CHANGE_TIMEOUT_MS,
                    TimeUnit.MILLISECONDS);
            assertEquals(TelecomManager.TTY_MODE_OFF, currentTtyMode);
            assertFalse(TelecomManager.TTY_MODE_FULL == currentTtyMode);
            assertFalse(TelecomManager.TTY_MODE_HCO == currentTtyMode);
            assertFalse(TelecomManager.TTY_MODE_VCO == currentTtyMode);
        } catch (InterruptedException e) {
            fail("Couldn't get TTY mode.");
            e.printStackTrace();
        }
    }

    public void testHasManageOngoingCallsPermission() {
        if (!mShouldTestTelecom) {
            return;
        }
        AppOpsManager appOpsManager = mContext.getSystemService(AppOpsManager.class);
        PackageManager packageManager = mContext.getPackageManager();
        try {
            final int uid = packageManager.getApplicationInfo(CTS_TELECOM_PKG, 0).uid;
            invokeMethodWithShellPermissionsNoReturn(appOpsManager,
                    (appOpsMan) -> appOpsMan.setUidMode(AppOpsManager.OPSTR_MANAGE_ONGOING_CALLS,
                            uid, AppOpsManager.MODE_ALLOWED));
            assertTrue(mTelecomManager.hasManageOngoingCallsPermission());
            invokeMethodWithShellPermissionsNoReturn(appOpsManager,
                    (appOpsMan) -> appOpsMan.setUidMode(AppOpsManager.OPSTR_MANAGE_ONGOING_CALLS,
                            uid, AppOpsManager.opToDefaultMode(
                                    AppOpsManager.OPSTR_MANAGE_ONGOING_CALLS)));
            assertFalse(mTelecomManager.hasManageOngoingCallsPermission());
        } catch (PackageManager.NameNotFoundException ex) {
            fail("Couldn't get uid for android.telecom.cts");
        }
    }

    public void testTtyModeBroadcasts() {
        // We only expect the actual tty mode to change if there's a wired headset plugged in, so
        // don't do the test if there isn't one plugged in.
        if (!mShouldTestTelecom || !isWiredHeadsetPluggedIn()) {
            return;
        }
        LinkedBlockingQueue<Intent> ttyModeQueue = new LinkedBlockingQueue<>(1);
        BroadcastReceiver ttyModeReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (TelecomManager.ACTION_CURRENT_TTY_MODE_CHANGED.equals(intent.getAction())) {
                    ttyModeQueue.offer(intent);
                }
            }
        };
        mContext.registerReceiver(ttyModeReceiver,
                new IntentFilter(TelecomManager.ACTION_CURRENT_TTY_MODE_CHANGED));
        Intent changePreferredTtyMode =
                new Intent(TelecomManager.ACTION_TTY_PREFERRED_MODE_CHANGED);
        changePreferredTtyMode.putExtra(TelecomManager.EXTRA_TTY_PREFERRED_MODE,
                TelecomManager.TTY_MODE_FULL);

        try {
            // Hold SHELL permission identity to ensure CTS tests have READ_PRIVILEGED_PHONE_STATE
            // during delivery of ACTION_CURRENT_TTY_MODE_CHANGED.
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .adoptShellPermissionIdentity();
            mContext.sendBroadcast(changePreferredTtyMode);
            Intent intent = ttyModeQueue.poll(
                    TestUtils.WAIT_FOR_STATE_CHANGE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            assertTrue(intent.hasExtra(TelecomManager.EXTRA_CURRENT_TTY_MODE));
            assertEquals(TelecomManager.TTY_MODE_FULL,
                    intent.getIntExtra(TelecomManager.EXTRA_CURRENT_TTY_MODE, -1));
        } catch (InterruptedException e) {
            fail("interrupted");
        } finally {
            Intent revertPreferredTtyMode =
                    new Intent(TelecomManager.ACTION_TTY_PREFERRED_MODE_CHANGED);
            revertPreferredTtyMode.putExtra(TelecomManager.EXTRA_TTY_PREFERRED_MODE,
                    TelecomManager.TTY_MODE_OFF);
            mContext.sendBroadcast(revertPreferredTtyMode);
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .dropShellPermissionIdentity();
        }
    }

    public void testIsInEmergencyCall_noOngoingEmergencyCall() {
        if (!mShouldTestTelecom) {
            return;
        }

        LinkedBlockingQueue<Boolean> queue = new LinkedBlockingQueue(1);
        runWithShellPermissionIdentity(() ->
                queue.put(mTelecomManager.isInEmergencyCall()));
        try {
            boolean isInEmergencyCall = queue.poll(TestUtils.WAIT_FOR_STATE_CHANGE_TIMEOUT_MS,
                    TimeUnit.MILLISECONDS);
            assertFalse(isInEmergencyCall);
        } catch (InterruptedException e) {
            fail("Couldn't check if in emergency call.");
            e.printStackTrace();
        }
    }

    public void testIsInEmergencyCall_ongoingEmergencyCall() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }

        // Place an emergency call
        setupConnectionService(null, 0);
        setupForEmergencyCalling(TEST_EMERGENCY_NUMBER);
        Bundle extras = new Bundle();
        extras.putParcelable(TestUtils.EXTRA_PHONE_NUMBER, TEST_EMERGENCY_URI);
        placeAndVerifyCall(extras);
        verifyConnectionForOutgoingCall();
        assertIsInCall(true);
        assertIsInManagedCall(true);
        try {
            TestUtils.waitOnAllHandlers(getInstrumentation());
        } catch (Exception e) {
            fail("Failed to wait on handlers " + e);
        }

        LinkedBlockingQueue<Boolean> queue = new LinkedBlockingQueue(1);
        runWithShellPermissionIdentity(() ->
                queue.put(mTelecomManager.isInEmergencyCall()));
        try {
            boolean isInEmergencyCall = queue.poll(TestUtils.WAIT_FOR_STATE_CHANGE_TIMEOUT_MS,
                    TimeUnit.MILLISECONDS);
            assertTrue(isInEmergencyCall);
        } catch (InterruptedException e) {
            fail("Couldn't check if in emergency call.");
            e.printStackTrace();
        }
    }

    public void testCallEndpointsRegistration() {
        // TODO: grant permission after we request permission for relevant TelecomManager APIs.

        // backup existing endpoints
        Set<CallEndpoint> currentEndpoints = mTelecomManager.getCallEndpoints();
        // unregister all current endpoints
        mTelecomManager.unregisterCallEndpoints(currentEndpoints);

        // Register call endpoints set 1
        Set<CallEndpoint> endpointSet1 = new HashSet<>();
        endpointSet1.add(CALL_ENDPOINT_1);
        endpointSet1.add(CALL_ENDPOINT_2);
        mTelecomManager.registerCallEndpoints(endpointSet1);
        assertEquals(endpointSet1 , mTelecomManager.getCallEndpoints());

        // Unregister several call endpoints
        Set<CallEndpoint> endpointSet2 = new HashSet<>();
        endpointSet2.add(CALL_ENDPOINT_2);
        mTelecomManager.unregisterCallEndpoints(endpointSet2);
        Set<CallEndpoint> telecomEndpointSet = mTelecomManager.getCallEndpoints();
        assertEquals(1, telecomEndpointSet.size());
        assertTrue(telecomEndpointSet.contains(CALL_ENDPOINT_1));

        // Unregister all test endpoints
        mTelecomManager.unregisterCallEndpoints(endpointSet1);
        // restored backed endpoints
        mTelecomManager.registerCallEndpoints(currentEndpoints);
    }

    private boolean isWiredHeadsetPluggedIn() {
        AudioManager audioManager = mContext.getSystemService(AudioManager.class);
        AudioDeviceInfo[] devices = audioManager.getDevices(AudioManager.GET_DEVICES_ALL);
        boolean isPluggedIn = false;
        for (AudioDeviceInfo device : devices) {
            switch (device.getType()) {
                case AudioDeviceInfo.TYPE_WIRED_HEADPHONES:
                case AudioDeviceInfo.TYPE_WIRED_HEADSET:
                case AudioDeviceInfo.TYPE_USB_HEADSET:
                case AudioDeviceInfo.TYPE_USB_DEVICE:
                    isPluggedIn = true;
            }
            if (isPluggedIn) {
                break;
            }
        }
        return isPluggedIn;
    }

}
