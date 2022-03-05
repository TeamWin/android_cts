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

package android.virtualdevice.cts;

import static android.Manifest.permission.ACTIVITY_EMBEDDING;
import static android.Manifest.permission.ADD_ALWAYS_UNLOCKED_DISPLAY;
import static android.Manifest.permission.ADD_TRUSTED_DISPLAY;
import static android.Manifest.permission.CREATE_VIRTUAL_DEVICE;
import static android.Manifest.permission.REAL_GET_TASKS;
import static android.Manifest.permission.WAKE_LOCK;
import static android.virtualdevice.cts.util.VirtualDeviceTestUtils.createActivityOptions;
import static android.virtualdevice.cts.util.VirtualDeviceTestUtils.createResultReceiver;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import android.annotation.Nullable;
import android.app.Activity;
import android.companion.virtual.VirtualDeviceManager;
import android.companion.virtual.VirtualDeviceManager.VirtualDevice;
import android.companion.virtual.VirtualDeviceParams;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.os.ResultReceiver;
import android.platform.test.annotations.AppModeFull;
import android.virtualdevice.cts.util.EmptyActivity;
import android.virtualdevice.cts.util.FakeAssociationRule;
import android.virtualdevice.cts.util.TestAppHelper;
import android.virtualdevice.cts.util.VirtualDeviceTestUtils.OnReceiveResultListener;

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
public class ActivityBlockingTest {

    private static final VirtualDeviceParams DEFAULT_VIRTUAL_DEVICE_PARAMS =
            new VirtualDeviceParams.Builder().build();

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
    @Nullable private VirtualDevice mVirtualDevice;
    @Mock
    private VirtualDisplay.Callback mVirtualDisplayCallback;
    @Mock
    private OnReceiveResultListener mOnReceiveResultListener;
    private ResultReceiver mResultReceiver;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        Context context = getApplicationContext();
        mVirtualDeviceManager = context.getSystemService(VirtualDeviceManager.class);
        mResultReceiver = createResultReceiver(mOnReceiveResultListener);
    }

    @After
    public void tearDown() {
        if (mVirtualDevice != null) {
            mVirtualDevice.close();
        }
    }

    @Test
    public void nonTrustedDisplay_startNonEmbeddableActivity_shouldThrowSecurityException() {
        mVirtualDevice =
                mVirtualDeviceManager.createVirtualDevice(
                        mFakeAssociationRule.getAssociationInfo().getId(),
                        DEFAULT_VIRTUAL_DEVICE_PARAMS);
        VirtualDisplay virtualDisplay = mVirtualDevice.createVirtualDisplay(
                /* width= */ 100,
                /* height= */ 100,
                /* densityDpi= */ 240,
                /* surface= */ null,
                /* flags= */ 0,
                Runnable::run,
                mVirtualDisplayCallback);

        Intent intent = TestAppHelper.createNoEmbedIntent()
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        assertThrows(SecurityException.class, () ->
                InstrumentationRegistry.getInstrumentation().getTargetContext()
                        .startActivity(intent, createActivityOptions(virtualDisplay)));
    }

    @Test
    public void cannotDisplayOnRemoteActivity_shouldBeBlockedFromLaunching() {
        mVirtualDevice =
                mVirtualDeviceManager.createVirtualDevice(
                        mFakeAssociationRule.getAssociationInfo().getId(),
                        DEFAULT_VIRTUAL_DEVICE_PARAMS);
        VirtualDisplay virtualDisplay = mVirtualDevice.createVirtualDisplay(
                /* width= */ 100,
                /* height= */ 100,
                /* densityDpi= */ 240,
                /* surface= */ null,
                /* flags= */ 0,
                Runnable::run,
                mVirtualDisplayCallback);

        EmptyActivity emptyActivity = (EmptyActivity) InstrumentationRegistry.getInstrumentation()
                .startActivitySync(
                        new Intent(getApplicationContext(), EmptyActivity.class)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                                        | Intent.FLAG_ACTIVITY_CLEAR_TASK),
                        createActivityOptions(virtualDisplay));

        emptyActivity.startActivity(
                TestAppHelper.createCannotDisplayOnRemoteIntent(mResultReceiver));

        verify(mOnReceiveResultListener, after(3000).never()).onReceiveResult(
                eq(Activity.RESULT_OK),
                argThat(result ->
                        result.getInt(TestAppHelper.EXTRA_DISPLAY)
                                == virtualDisplay.getDisplay().getDisplayId()));
    }

    @Test
    public void trustedDisplay_startNonEmbeddableActivity_shouldSucceed() {
        mVirtualDevice =
                mVirtualDeviceManager.createVirtualDevice(
                        mFakeAssociationRule.getAssociationInfo().getId(),
                        DEFAULT_VIRTUAL_DEVICE_PARAMS);
        VirtualDisplay virtualDisplay = mVirtualDevice.createVirtualDisplay(
                /* width= */ 100,
                /* height= */ 100,
                /* densityDpi= */ 240,
                /* surface= */ null,
                /* flags= */ DisplayManager.VIRTUAL_DISPLAY_FLAG_TRUSTED,
                Runnable::run,
                mVirtualDisplayCallback);

        Intent intent = TestAppHelper.createActivityLaunchedReceiverIntent(mResultReceiver)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        InstrumentationRegistry.getInstrumentation().getTargetContext()
                .startActivity(intent, createActivityOptions(virtualDisplay));

        verify(mOnReceiveResultListener, timeout(3000)).onReceiveResult(
                eq(Activity.RESULT_OK),
                argThat(result ->
                        result.getInt(TestAppHelper.EXTRA_DISPLAY)
                                == virtualDisplay.getDisplay().getDisplayId()));
    }

    @Test
    public void setAllowedActivities_shouldBlockNonAllowedActivities() {
        Context context = getApplicationContext();
        ComponentName emptyActivityComponentName = new ComponentName(context, EmptyActivity.class);
        mVirtualDevice =
                mVirtualDeviceManager.createVirtualDevice(
                        mFakeAssociationRule.getAssociationInfo().getId(),
                        new VirtualDeviceParams.Builder()
                                .setAllowedActivities(Set.of(emptyActivityComponentName))
                                .build());
        VirtualDisplay virtualDisplay = mVirtualDevice.createVirtualDisplay(
                /* width= */ 100,
                /* height= */ 100,
                /* densityDpi= */ 240,
                /* surface= */ null,
                /* flags= */ 0,
                Runnable::run,
                mVirtualDisplayCallback);

        EmptyActivity emptyActivity = (EmptyActivity) InstrumentationRegistry.getInstrumentation()
                .startActivitySync(
                        new Intent().setComponent(emptyActivityComponentName)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                                        | Intent.FLAG_ACTIVITY_CLEAR_TASK),
                        createActivityOptions(virtualDisplay));

        emptyActivity.startActivity(
                TestAppHelper.createActivityLaunchedReceiverIntent(mResultReceiver));

        verify(mOnReceiveResultListener, after(3000).never()).onReceiveResult(
                eq(Activity.RESULT_OK),
                argThat(result ->
                        result.getInt(TestAppHelper.EXTRA_DISPLAY)
                                == virtualDisplay.getDisplay().getDisplayId()));
    }

    @Test
    public void setBlockedActivities_shouldBlockActivityFromLaunching() {
        Context context = getApplicationContext();
        ComponentName emptyActivityComponentName = new ComponentName(context, EmptyActivity.class);
        mVirtualDevice =
                mVirtualDeviceManager.createVirtualDevice(
                        mFakeAssociationRule.getAssociationInfo().getId(),
                        new VirtualDeviceParams.Builder()
                                .setBlockedActivities(Set.of(TestAppHelper.MAIN_ACTIVITY_COMPONENT))
                                .build());
        VirtualDisplay virtualDisplay = mVirtualDevice.createVirtualDisplay(
                /* width= */ 100,
                /* height= */ 100,
                /* densityDpi= */ 240,
                /* surface= */ null,
                /* flags= */ 0,
                Runnable::run,
                mVirtualDisplayCallback);

        EmptyActivity emptyActivity = (EmptyActivity) InstrumentationRegistry.getInstrumentation()
                .startActivitySync(
                        new Intent().setComponent(emptyActivityComponentName)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                                        | Intent.FLAG_ACTIVITY_CLEAR_TASK),
                        createActivityOptions(virtualDisplay));

        EmptyActivity.Callback callback = mock(EmptyActivity.Callback.class);
        emptyActivity.setCallback(callback);
        final int requestCode = 1;
        emptyActivity.startActivityForResult(
                TestAppHelper.createActivityLaunchedReceiverIntent(mResultReceiver), requestCode);

        verify(mOnReceiveResultListener, after(3000).never()).onReceiveResult(
                eq(Activity.RESULT_OK),
                argThat(result ->
                        result.getInt(TestAppHelper.EXTRA_DISPLAY)
                                == virtualDisplay.getDisplay().getDisplayId()));
    }
}

