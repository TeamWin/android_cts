/*
 * Copyright (C) 2022 The Android Open Source Project
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
import static android.virtualdevice.cts.common.util.VirtualDeviceTestUtils.BLOCKED_ACTIVITY_COMPONENT;
import static android.virtualdevice.cts.common.util.VirtualDeviceTestUtils.createActivityOptions;
import static android.virtualdevice.cts.common.util.VirtualDeviceTestUtils.createResultReceiver;
import static androidx.test.core.app.ApplicationProvider.getApplicationContext;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assume.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import android.annotation.Nullable;
import android.app.Activity;
import android.app.ActivityManager;
import android.companion.virtual.VirtualDeviceManager;
import android.companion.virtual.VirtualDeviceManager.VirtualDevice;
import android.companion.virtual.VirtualDeviceParams;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.hardware.display.VirtualDisplayConfig;
import android.os.ResultReceiver;
import android.platform.test.annotations.AppModeFull;
import android.virtualdevice.cts.common.FakeAssociationRule;
import android.virtualdevice.cts.common.util.TestAppHelper;
import android.virtualdevice.cts.common.util.VirtualDeviceTestUtils;
import android.virtualdevice.cts.common.util.VirtualDeviceTestUtils.OnReceiveResultListener;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.AdoptShellPermissionsRule;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Set;

/**
 * Tests for blocking of activities that should not be shown on the virtual device.
 */
@RunWith(AndroidJUnit4.class)
@AppModeFull(reason = "VirtualDeviceManager cannot be accessed by instant apps")
public class RestrictActivityTest {
    private static final int TIMEOUT_MS = 3000;

    @Rule
    public AdoptShellPermissionsRule mAdoptShellPermissionsRule = new AdoptShellPermissionsRule(
            InstrumentationRegistry.getInstrumentation().getUiAutomation(),
            ADD_TRUSTED_DISPLAY, CREATE_VIRTUAL_DEVICE);

    @Rule public FakeAssociationRule mFakeAssociationRule = new FakeAssociationRule();

    private VirtualDeviceManager mVirtualDeviceManager;
    private ActivityManager mActivityManager;
    @Nullable private VirtualDevice mVirtualDevice;
    @Mock private VirtualDisplay.Callback mVirtualDisplayCallback;
    @Mock private OnReceiveResultListener mOnReceiveResultListener;
    @Mock private VirtualDeviceManager.ActivityListener mActivityListener;
    private ResultReceiver mResultReceiver;
    private DisplayManager mDisplayManager;
    private Context mContext;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mContext = getApplicationContext();
        final PackageManager packageManager = mContext.getPackageManager();
        assumeTrue(packageManager.hasSystemFeature(PackageManager.FEATURE_COMPANION_DEVICE_SETUP));
        assumeTrue(packageManager.hasSystemFeature(
                PackageManager.FEATURE_ACTIVITIES_ON_SECONDARY_DISPLAYS));
        mVirtualDeviceManager = mContext.getSystemService(VirtualDeviceManager.class);
        mActivityManager = mContext.getSystemService(ActivityManager.class);
        mDisplayManager = mContext.getSystemService(DisplayManager.class);
        mResultReceiver = createResultReceiver(mOnReceiveResultListener);
    }

    @After
    public void tearDown() {
        if (mVirtualDevice != null) {
            mVirtualDevice.close();
        }
    }

    @Test
    public void restrictedActivity_nonRestrictedActivityOnNonRestrictedDevice_shouldSucceed() {
        VirtualDisplay virtualDisplay = createVirtualDisplay(/* displayCategories= */ null);
        int virtualDisplayId = virtualDisplay.getDisplay().getDisplayId();
        Intent intent = TestAppHelper.createActivityLaunchedReceiverIntent(mResultReceiver)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

        assertThat(mActivityManager.isActivityStartAllowedOnDisplay(
                mContext, virtualDisplayId, intent)).isTrue();
        mContext.startActivity(intent, createActivityOptions(virtualDisplay));
        verify(mOnReceiveResultListener, timeout(TIMEOUT_MS)).onReceiveResult(
                eq(Activity.RESULT_OK),
                argThat(result -> result.getInt(TestAppHelper.EXTRA_DISPLAY) == virtualDisplayId));
    }

    @Test
    public void restrictedActivity_restrictedActivityOnNonRestrictedDevice_shouldFail() {
        VirtualDisplay virtualDisplay = createVirtualDisplay(/* displayCategories= */ null);

        Intent intent = createRestrictedAutomotiveIntent();
        assertThat(mActivityManager.isActivityStartAllowedOnDisplay(
                mContext, virtualDisplay.getDisplay().getDisplayId(), intent)).isFalse();
        mContext.startActivity(intent, createActivityOptions(virtualDisplay));

        verify(mActivityListener, timeout(TIMEOUT_MS)).onTopActivityChanged(
                eq(virtualDisplay.getDisplay().getDisplayId()), eq(BLOCKED_ACTIVITY_COMPONENT),
                anyInt());
        verify(mOnReceiveResultListener, never()).onReceiveResult(anyInt(), any());
    }

    @Test
    public void restrictedActivity_nonRestrictedActivityOnRestrictedDevice_shouldFail() {
        VirtualDisplay virtualDisplay = createVirtualDisplay(Set.of("automotive"));
        Intent intent =
                TestAppHelper.createActivityLaunchedReceiverIntent(mResultReceiver)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

        assertThat(mActivityManager.isActivityStartAllowedOnDisplay(
                mContext, virtualDisplay.getDisplay().getDisplayId(), intent)).isFalse();
        mContext.startActivity(intent, createActivityOptions(virtualDisplay));

        verify(mActivityListener, timeout(TIMEOUT_MS)).onTopActivityChanged(
                eq(virtualDisplay.getDisplay().getDisplayId()), eq(BLOCKED_ACTIVITY_COMPONENT),
                anyInt());
        verify(mOnReceiveResultListener, never()).onReceiveResult(anyInt(), any());
    }

    @Test
    public void restrictedActivity_differentCategory_shouldFail() {
        VirtualDisplay virtualDisplay = createVirtualDisplay(Set.of("abc"));
        Intent intent = createRestrictedAutomotiveIntent();
        assertThat(mActivityManager.isActivityStartAllowedOnDisplay(
                mContext, virtualDisplay.getDisplay().getDisplayId(), intent)).isFalse();
        mContext.startActivity(intent, createActivityOptions(virtualDisplay));

        verify(mActivityListener, timeout(TIMEOUT_MS)).onTopActivityChanged(
                eq(virtualDisplay.getDisplay().getDisplayId()), eq(BLOCKED_ACTIVITY_COMPONENT),
                anyInt());
        verify(mOnReceiveResultListener, never()).onReceiveResult(anyInt(), any());
    }

    @Test
    public void restrictedActivity_containCategories_shouldSucceed() {
        VirtualDisplay virtualDisplay = createVirtualDisplay(Set.of("automotive"));
        int virtualDisplayId = virtualDisplay.getDisplay().getDisplayId();
        Intent intent = createRestrictedAutomotiveIntent();
        assertThat(mActivityManager.isActivityStartAllowedOnDisplay(
                mContext, virtualDisplayId, intent)).isTrue();
        mContext.startActivity(intent, createActivityOptions(virtualDisplay));

        verify(mOnReceiveResultListener, timeout(TIMEOUT_MS)).onReceiveResult(
                eq(Activity.RESULT_OK),
                argThat(result -> result.getInt(TestAppHelper.EXTRA_DISPLAY) == virtualDisplayId));
    }

    @Test
    public void restrictedActivity_noGwpc_shouldFail() {
        VirtualDisplay virtualDisplay = mDisplayManager.createVirtualDisplay(
                VirtualDeviceTestUtils.createDefaultVirtualDisplayConfigBuilder().setFlags(
                        DisplayManager.VIRTUAL_DISPLAY_FLAG_TRUSTED).build());

        Intent intent = createRestrictedAutomotiveIntent();
        assertThat(mActivityManager.isActivityStartAllowedOnDisplay(
                mContext, virtualDisplay.getDisplay().getDisplayId(), intent)).isFalse();
        mContext.startActivity(intent, createActivityOptions(virtualDisplay));

        verify(mOnReceiveResultListener, never()).onReceiveResult(anyInt(), any());
    }

    private Intent createRestrictedAutomotiveIntent() {
        return TestAppHelper.createRestrictActivityIntent(mResultReceiver)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
    }

    private VirtualDisplay createVirtualDisplay(@Nullable Set<String> displayCategories) {
        mVirtualDevice =
                mVirtualDeviceManager.createVirtualDevice(
                        mFakeAssociationRule.getAssociationInfo().getId(),
                        new VirtualDeviceParams.Builder().build());
        mVirtualDevice.addActivityListener(mContext.getMainExecutor(), mActivityListener);
        VirtualDisplayConfig.Builder builder =
                VirtualDeviceTestUtils.createDefaultVirtualDisplayConfigBuilder()
                        .setFlags(DisplayManager.VIRTUAL_DISPLAY_FLAG_TRUSTED);
        if (displayCategories != null) {
            builder = builder.setDisplayCategories(displayCategories);
        }
        return mVirtualDevice.createVirtualDisplay(
                builder.build(), Runnable::run, mVirtualDisplayCallback);
    }
}
