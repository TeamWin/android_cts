/*
 * Copyright (C) 2023 The Android Open Source Project
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

package android.virtualdevice.cts;

import static android.Manifest.permission.ACTIVITY_EMBEDDING;
import static android.Manifest.permission.ADD_ALWAYS_UNLOCKED_DISPLAY;
import static android.Manifest.permission.ADD_TRUSTED_DISPLAY;
import static android.Manifest.permission.CREATE_VIRTUAL_DEVICE;
import static android.Manifest.permission.REAL_GET_TASKS;
import static android.Manifest.permission.WAKE_LOCK;
import static android.app.PendingIntent.FLAG_IMMUTABLE;
import static android.app.PendingIntent.FLAG_UPDATE_CURRENT;
import static android.companion.virtual.VirtualDeviceParams.DEVICE_POLICY_CUSTOM;
import static android.companion.virtual.VirtualDeviceParams.DEVICE_POLICY_DEFAULT;
import static android.companion.virtual.VirtualDeviceParams.POLICY_TYPE_RECENTS;
import static android.virtualdevice.cts.common.util.TestAppHelper.ACTION_CHECK_RECENT_TASKS_PRESENCE;
import static android.virtualdevice.cts.common.util.TestAppHelper.EXTRA_ACTIVITY_INCLUDED_IN_RECENT_TASKS;
import static android.virtualdevice.cts.common.util.TestAppHelper.EXTRA_ACTIVITY_LAUNCHED_RECEIVER;
import static android.virtualdevice.cts.common.util.TestAppHelper.MAIN_ACTIVITY_COMPONENT;
import static android.virtualdevice.cts.common.util.VirtualDeviceTestUtils.createResultReceiver;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeNotNull;
import static org.junit.Assume.assumeTrue;

import android.app.Activity;
import android.app.PendingIntent;
import android.companion.virtual.VirtualDeviceManager;
import android.companion.virtual.VirtualDeviceManager.VirtualDevice;
import android.companion.virtual.VirtualDeviceParams;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.hardware.display.VirtualDisplayConfig;
import android.os.Bundle;
import android.platform.test.annotations.AppModeFull;
import android.virtualdevice.cts.common.FakeAssociationRule;
import android.virtualdevice.cts.common.util.VirtualDeviceTestUtils;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.AdoptShellPermissionsRule;

import com.google.common.util.concurrent.SettableFuture;
import com.google.common.util.concurrent.Uninterruptibles;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@RunWith(AndroidJUnit4.class)
@AppModeFull(reason = "VirtualDeviceManager cannot be accessed by instant apps")
public class RecentTasksTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final VirtualDisplayConfig VIRTUAL_DISPLAY_CONFIG =
            new VirtualDisplayConfig.Builder("testDisplay", 100, 100, 100).setFlags(
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_TRUSTED).build();

    @Rule
    public AdoptShellPermissionsRule mAdoptShellPermissionsRule = new AdoptShellPermissionsRule(
            InstrumentationRegistry.getInstrumentation().getUiAutomation(),
            ACTIVITY_EMBEDDING,
            ADD_ALWAYS_UNLOCKED_DISPLAY,
            ADD_TRUSTED_DISPLAY,
            CREATE_VIRTUAL_DEVICE,
            REAL_GET_TASKS,
            WAKE_LOCK);

    @Rule
    public FakeAssociationRule mFakeAssociationRule = new FakeAssociationRule();

    private VirtualDeviceManager mVirtualDeviceManager;

    @Before
    public void setUp() {
        Context context = getApplicationContext();
        final PackageManager packageManager = context.getPackageManager();
        assumeTrue(packageManager.hasSystemFeature(PackageManager.FEATURE_COMPANION_DEVICE_SETUP));
        assumeTrue(packageManager.hasSystemFeature(
                PackageManager.FEATURE_ACTIVITIES_ON_SECONDARY_DISPLAYS));
        mVirtualDeviceManager = context.getSystemService(VirtualDeviceManager.class);
        assumeNotNull(mVirtualDeviceManager);
    }

    @Test
    public void activityLaunchedOnVdmWithDefaultRecentPolicy_includedInRecents() throws Exception {
        try (VirtualDevice virtualDevice = createVirtualDeviceWithRecentsPolicy(
                DEVICE_POLICY_DEFAULT)) {
            VirtualDisplay virtualDisplay = virtualDevice.createVirtualDisplay(
                    VIRTUAL_DISPLAY_CONFIG, /*executor=*/null, /*callback=*/null);

            Bundle activityResult = launchTestActivity(virtualDevice, virtualDisplay);

            assertThat(activityResult.getBoolean(EXTRA_ACTIVITY_INCLUDED_IN_RECENT_TASKS)).isTrue();
        }
    }

    @Test
    public void activityLaunchedOnVdmWithCustomRecentPolicy_excludedFromRecents() throws Exception {
        try (VirtualDevice virtualDevice = createVirtualDeviceWithRecentsPolicy(
                DEVICE_POLICY_CUSTOM)) {
            VirtualDisplay virtualDisplay = virtualDevice.createVirtualDisplay(
                    VIRTUAL_DISPLAY_CONFIG, /*executor=*/null, /*callback=*/null);
            launchTestActivity(virtualDevice, virtualDisplay);

            Bundle activityResult = launchTestActivity(virtualDevice, virtualDisplay);

            assertThat(
                    activityResult.getBoolean(EXTRA_ACTIVITY_INCLUDED_IN_RECENT_TASKS)).isFalse();
        }
    }

    private VirtualDevice createVirtualDeviceWithRecentsPolicy(
            @VirtualDeviceParams.DevicePolicy int recentsPolicy) {
        return mVirtualDeviceManager.createVirtualDevice(
                mFakeAssociationRule.getAssociationInfo().getId(),
                new VirtualDeviceParams.Builder().setDevicePolicy(POLICY_TYPE_RECENTS,
                        recentsPolicy).build());
    }

    private Bundle launchTestActivity(VirtualDevice virtualDevice, VirtualDisplay virtualDisplay)
            throws ExecutionException, TimeoutException {
        ActivityResultCallback activityResultCallback = new ActivityResultCallback();

        Intent intent =
                new Intent(ACTION_CHECK_RECENT_TASKS_PRESENCE)
                        .setComponent(MAIN_ACTIVITY_COMPONENT)
                        .putExtra(EXTRA_ACTIVITY_LAUNCHED_RECEIVER,
                                createResultReceiver(activityResultCallback)).setFlags(
                                Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        virtualDevice.launchPendingIntent(virtualDisplay.getDisplay().getDisplayId(),
                PendingIntent.getActivity(getApplicationContext(),
                        MAIN_ACTIVITY_COMPONENT.hashCode(), intent,
                        FLAG_IMMUTABLE | FLAG_UPDATE_CURRENT),
                Runnable::run, value -> {
                });

        return activityResultCallback.waitForActivityResult();
    }

    private static class ActivityResultCallback implements
            VirtualDeviceTestUtils.OnReceiveResultListener {

        private final SettableFuture<Bundle> mResultBundle = SettableFuture.create();

        public Bundle waitForActivityResult() throws ExecutionException, TimeoutException {
            return Uninterruptibles.getUninterruptibly(mResultBundle, TIMEOUT.getSeconds(),
                    TimeUnit.SECONDS);
        }

        @Override
        public void onReceiveResult(int resultCode, Bundle resultData) {
            if (resultCode != Activity.RESULT_OK) {
                mResultBundle.setException(new IllegalStateException(
                        "Test activity returned unexpected result code: " + resultCode));
            } else {
                mResultBundle.set(resultData);
            }
        }
    }

}
