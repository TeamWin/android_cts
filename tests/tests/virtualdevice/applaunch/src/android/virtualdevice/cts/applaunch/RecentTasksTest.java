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

import static android.Manifest.permission.ADD_TRUSTED_DISPLAY;
import static android.Manifest.permission.CREATE_VIRTUAL_DEVICE;
import static android.companion.virtual.VirtualDeviceParams.DEVICE_POLICY_CUSTOM;
import static android.companion.virtual.VirtualDeviceParams.DEVICE_POLICY_DEFAULT;
import static android.companion.virtual.VirtualDeviceParams.POLICY_TYPE_RECENTS;
import static android.virtualdevice.cts.common.util.VirtualDeviceTestUtils.createActivityOptions;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeNotNull;
import static org.junit.Assume.assumeTrue;

import android.app.ActivityManager;
import android.companion.virtual.VirtualDeviceManager;
import android.companion.virtual.VirtualDeviceManager.VirtualDevice;
import android.companion.virtual.VirtualDeviceParams;
import android.companion.virtual.flags.Flags;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.platform.test.annotations.AppModeFull;
import android.virtualdevice.cts.applaunch.util.EmptyActivity;
import android.virtualdevice.cts.common.FakeAssociationRule;
import android.virtualdevice.cts.common.util.VirtualDeviceTestUtils;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.AdoptShellPermissionsRule;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@AppModeFull(reason = "VirtualDeviceManager cannot be accessed by instant apps")
public class RecentTasksTest {

    @Rule
    public AdoptShellPermissionsRule mAdoptShellPermissionsRule = new AdoptShellPermissionsRule(
            InstrumentationRegistry.getInstrumentation().getUiAutomation(),
            ADD_TRUSTED_DISPLAY,
            CREATE_VIRTUAL_DEVICE);

    @Rule
    public FakeAssociationRule mFakeAssociationRule = new FakeAssociationRule();

    private VirtualDeviceManager mVirtualDeviceManager;
    private DisplayManager mDisplayManager;
    private ActivityManager mActivityManager;
    private VirtualDeviceTestUtils.DisplayListenerForTest mDisplayListener;

    @Before
    public void setUp() {
        Context context = getApplicationContext();
        final PackageManager packageManager = context.getPackageManager();
        assumeTrue(packageManager.hasSystemFeature(
                PackageManager.FEATURE_ACTIVITIES_ON_SECONDARY_DISPLAYS));
        mVirtualDeviceManager = context.getSystemService(VirtualDeviceManager.class);
        assumeNotNull(mVirtualDeviceManager);
        mActivityManager = context.getSystemService(ActivityManager.class);
        mDisplayManager = context.getSystemService(DisplayManager.class);
        mDisplayListener = new VirtualDeviceTestUtils.DisplayListenerForTest();
        mDisplayManager.registerDisplayListener(mDisplayListener, /*handler=*/null);
    }

    @After
    public void tearDown() {
        if (mDisplayManager != null && mDisplayListener != null) {
            mDisplayManager.unregisterDisplayListener(mDisplayListener);
        }
    }

    @Test
    public void activityLaunchedOnVdmWithDefaultRecentPolicy_includedInRecents() {
        EmptyActivity activity;
        int taskId;
        try (VirtualDevice virtualDevice = createVirtualDeviceWithRecentsPolicy(
                DEVICE_POLICY_DEFAULT)) {
            VirtualDisplay virtualDisplay = createVirtualDisplay(virtualDevice);
            assertThat(mDisplayListener.waitForOnDisplayAddedCallback()).isTrue();
            activity = launchTestActivity(virtualDisplay);
            taskId = activity.getTaskId();
            assertThat(isTaskIncludedInRecents(taskId)).isTrue();

            if (Flags.dynamicPolicy()) {
                virtualDevice.setDevicePolicy(POLICY_TYPE_RECENTS, DEVICE_POLICY_CUSTOM);
                assertThat(isTaskIncludedInRecents(taskId)).isFalse();
                virtualDevice.setDevicePolicy(POLICY_TYPE_RECENTS, DEVICE_POLICY_DEFAULT);
                assertThat(isTaskIncludedInRecents(taskId)).isTrue();
            }
        }

        // Make sure the policy is respected even after the device / display are gone.
        assertThat(mDisplayListener.waitForOnDisplayRemovedCallback()).isTrue();
        assertThat(isTaskIncludedInRecents(taskId)).isTrue();
    }

    @Test
    public void activityLaunchedOnVdmWithCustomRecentPolicy_excludedFromRecents() {
        EmptyActivity activity;
        int taskId;
        try (VirtualDevice virtualDevice = createVirtualDeviceWithRecentsPolicy(
                DEVICE_POLICY_CUSTOM)) {
            VirtualDisplay virtualDisplay = createVirtualDisplay(virtualDevice);
            assertThat(mDisplayListener.waitForOnDisplayAddedCallback()).isTrue();
            activity = launchTestActivity(virtualDisplay);
            taskId = activity.getTaskId();
            assertThat(isTaskIncludedInRecents(taskId)).isFalse();

            if (Flags.dynamicPolicy()) {
                virtualDevice.setDevicePolicy(POLICY_TYPE_RECENTS, DEVICE_POLICY_DEFAULT);
                assertThat(isTaskIncludedInRecents(taskId)).isTrue();
                virtualDevice.setDevicePolicy(POLICY_TYPE_RECENTS, DEVICE_POLICY_CUSTOM);
                assertThat(isTaskIncludedInRecents(taskId)).isFalse();
            }
        }

        // Make sure the policy is respected even after the device / display are gone.
        assertThat(mDisplayListener.waitForOnDisplayRemovedCallback()).isTrue();
        assertThat(isTaskIncludedInRecents(taskId)).isFalse();
    }

    private VirtualDevice createVirtualDeviceWithRecentsPolicy(
            @VirtualDeviceParams.DevicePolicy int recentsPolicy) {
        return mVirtualDeviceManager.createVirtualDevice(
                mFakeAssociationRule.getAssociationInfo().getId(),
                new VirtualDeviceParams.Builder().setDevicePolicy(POLICY_TYPE_RECENTS,
                        recentsPolicy).build());
    }

    private EmptyActivity launchTestActivity(VirtualDisplay virtualDisplay) {
        return (EmptyActivity) InstrumentationRegistry.getInstrumentation()
                .startActivitySync(
                        new Intent(getApplicationContext(), EmptyActivity.class)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                                        | Intent.FLAG_ACTIVITY_CLEAR_TASK),
                        createActivityOptions(virtualDisplay));
    }

    private static VirtualDisplay createVirtualDisplay(VirtualDevice virtualDevice) {
        return virtualDevice.createVirtualDisplay(
                VirtualDeviceTestUtils.createDefaultVirtualDisplayConfigBuilder()
                        .setFlags(DisplayManager.VIRTUAL_DISPLAY_FLAG_TRUSTED
                                | DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY
                                | DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC)
                        .build(),
                /*executor=*/null, /*callback=*/null);
    }

    private boolean isTaskIncludedInRecents(int taskId) {
        return mActivityManager.getRecentTasks(Integer.MAX_VALUE, /*flags=*/0)
                .stream().anyMatch(task -> task.taskId == taskId);
    }
}
