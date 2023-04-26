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
import static android.virtualdevice.cts.util.TestAppHelper.MAIN_ACTIVITY_COMPONENT;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeNotNull;
import static org.junit.Assume.assumeTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import static java.lang.Integer.MAX_VALUE;

import android.app.ActivityManager;
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
import android.platform.test.annotations.AppModeFull;
import android.virtualdevice.cts.common.FakeAssociationRule;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.AdoptShellPermissionsRule;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.function.IntConsumer;

@RunWith(AndroidJUnit4.class)
@AppModeFull(reason = "VirtualDeviceManager cannot be accessed by instant apps")
public class RecentTasksTest {
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
    private ActivityManager mActivityManager;
    @Mock
    private IntConsumer mLaunchCompleteListener;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        Context context = getApplicationContext();
        final PackageManager packageManager = context.getPackageManager();
        assumeTrue(packageManager.hasSystemFeature(PackageManager.FEATURE_COMPANION_DEVICE_SETUP));
        assumeTrue(packageManager.hasSystemFeature(
                PackageManager.FEATURE_ACTIVITIES_ON_SECONDARY_DISPLAYS));
        mVirtualDeviceManager = context.getSystemService(VirtualDeviceManager.class);
        assumeNotNull(mVirtualDeviceManager);
        mActivityManager = context.getSystemService(ActivityManager.class);
    }

    @Test
    public void activityLaunchedOnVdmWithDefaultRecentPolicy_includedInRecents() {
        try (VirtualDevice virtualDevice = createVirtualDeviceWithRecentsPolicy(
                DEVICE_POLICY_DEFAULT)) {
            VirtualDisplay virtualDisplay = virtualDevice.createVirtualDisplay(
                    VIRTUAL_DISPLAY_CONFIG, /*executor=*/null, /*callback=*/null);

            launchTestActivity(virtualDevice, virtualDisplay);

            assertThat(
                    mActivityManager.getRecentTasks(MAX_VALUE, /*flags=*/0).stream().anyMatch(
                            RecentTasksTest::hasTestActivityComponentIntent)).isTrue();
        }
    }

    @Test
    public void activityLaunchedOnVdmWithCustomRecentPolicy_excludedFromRecents() {
        try (VirtualDevice virtualDevice = createVirtualDeviceWithRecentsPolicy(
                DEVICE_POLICY_CUSTOM)) {
            VirtualDisplay virtualDisplay = virtualDevice.createVirtualDisplay(
                    VIRTUAL_DISPLAY_CONFIG, /*executor=*/null, /*callback=*/null);

            launchTestActivity(virtualDevice, virtualDisplay);

            assertThat(
                    mActivityManager.getRecentTasks(MAX_VALUE, /*flags=*/0).stream().anyMatch(
                            RecentTasksTest::hasTestActivityComponentIntent)).isFalse();
        }
    }

    private void launchTestActivity(VirtualDevice virtualDevice, VirtualDisplay virtualDisplay) {
        Intent intent = new Intent()
                .setComponent(MAIN_ACTIVITY_COMPONENT)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        virtualDevice.launchPendingIntent(virtualDisplay.getDisplay().getDisplayId(),
                PendingIntent.getActivity(getApplicationContext(),
                        MAIN_ACTIVITY_COMPONENT.hashCode(), intent,
                        FLAG_IMMUTABLE | FLAG_UPDATE_CURRENT),
                Runnable::run, mLaunchCompleteListener);

        verify(mLaunchCompleteListener, timeout(1000)).accept(
                eq(VirtualDeviceManager.LAUNCH_SUCCESS));
    }

    private static boolean hasTestActivityComponentIntent(
            ActivityManager.RecentTaskInfo recentTaskInfo) {
        return recentTaskInfo.baseIntent != null && MAIN_ACTIVITY_COMPONENT.equals(
                recentTaskInfo.baseIntent.getComponent());
    }

    private VirtualDevice createVirtualDeviceWithRecentsPolicy(
            @VirtualDeviceParams.DevicePolicy int recentsPolicy) {
        return mVirtualDeviceManager.createVirtualDevice(
                mFakeAssociationRule.getAssociationInfo().getId(),
                new VirtualDeviceParams.Builder().setDevicePolicy(POLICY_TYPE_RECENTS,
                        recentsPolicy).build());
    }
}
