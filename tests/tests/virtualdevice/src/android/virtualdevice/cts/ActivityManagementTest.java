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
import static android.Manifest.permission.WAKE_LOCK;
import static android.virtualdevice.cts.util.VirtualDeviceTestUtils.createActivityOptions;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;

import static org.junit.Assume.assumeTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import android.annotation.Nullable;
import android.app.Activity;
import android.app.PendingIntent;
import android.companion.virtual.VirtualDeviceManager;
import android.companion.virtual.VirtualDeviceManager.ActivityListener;
import android.companion.virtual.VirtualDeviceManager.VirtualDevice;
import android.companion.virtual.VirtualDeviceParams;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.display.VirtualDisplay;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ResultReceiver;
import android.platform.test.annotations.AppModeFull;
import android.virtualdevice.cts.util.EmptyActivity;
import android.virtualdevice.cts.util.FakeAssociationRule;
import android.virtualdevice.cts.util.TestAppHelper;
import android.virtualdevice.cts.util.TestAppHelper.ServiceConnectionFuture;
import android.virtualdevice.cts.util.VirtualDeviceTestUtils;
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

import java.util.concurrent.TimeUnit;

/**
 * Tests for activity management, like launching and listening to activity change events, in the
 * virtual device.
 */
@RunWith(AndroidJUnit4.class)
@AppModeFull(reason = "VirtualDeviceManager cannot be accessed by instant apps")
public class ActivityManagementTest {

    private static final VirtualDeviceParams DEFAULT_VIRTUAL_DEVICE_PARAMS =
            new VirtualDeviceParams.Builder().build();

    @Rule
    public AdoptShellPermissionsRule mAdoptShellPermissionsRule = new AdoptShellPermissionsRule(
            InstrumentationRegistry.getInstrumentation().getUiAutomation(),
            ACTIVITY_EMBEDDING,
            ADD_ALWAYS_UNLOCKED_DISPLAY,
            ADD_TRUSTED_DISPLAY,
            CREATE_VIRTUAL_DEVICE,
            WAKE_LOCK);

    @Rule
    public FakeAssociationRule mFakeAssociationRule = new FakeAssociationRule();

    private VirtualDeviceManager mVirtualDeviceManager;
    @Nullable private VirtualDevice mVirtualDevice;
    @Mock
    private VirtualDisplay.Callback mVirtualDisplayCallback;
    @Mock
    private VirtualDeviceManager.LaunchCallback mLaunchCallback;
    @Nullable private ServiceConnectionFuture<IStreamedTestApp> mServiceConnection;
    @Mock
    private OnReceiveResultListener mOnReceiveResultListener;
    private ResultReceiver mResultReceiver;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        Context context = getApplicationContext();
        assumeTrue(
                context.getPackageManager()
                        .hasSystemFeature(PackageManager.FEATURE_COMPANION_DEVICE_SETUP));

        mVirtualDeviceManager = context.getSystemService(VirtualDeviceManager.class);
        mResultReceiver = VirtualDeviceTestUtils.createResultReceiver(mOnReceiveResultListener);
    }

    @After
    public void tearDown() {
        try {
            if (mServiceConnection != null) {
                getApplicationContext().unbindService(mServiceConnection);
            }
        } catch (IllegalArgumentException e) {
            // Ignore if the service failed to bind
        }
        if (mVirtualDevice != null) {
            mVirtualDevice.close();
        }
    }

    @Test
    public void activityListener_shouldCallOnTopActivityChange() {
        Context context = getApplicationContext();
        mVirtualDevice =
                mVirtualDeviceManager.createVirtualDevice(
                        mFakeAssociationRule.getAssociationInfo().getId(),
                        DEFAULT_VIRTUAL_DEVICE_PARAMS);
        ActivityListener activityListener = mock(ActivityListener.class);
        mVirtualDevice.addActivityListener(activityListener);
        VirtualDisplay virtualDisplay = mVirtualDevice.createVirtualDisplay(
                /* width= */ 100,
                /* height= */ 100,
                /* densityDpi= */ 240,
                /* surface= */ null,
                /* flags= */ 0,
                new Handler(Looper.getMainLooper()),
                mVirtualDisplayCallback);
        EmptyActivity emptyActivity = (EmptyActivity) InstrumentationRegistry.getInstrumentation()
                .startActivitySync(
                        new Intent(context, EmptyActivity.class)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                                        | Intent.FLAG_ACTIVITY_CLEAR_TASK),
                        createActivityOptions(virtualDisplay));

        verify(activityListener).onTopActivityChanged(
                eq(virtualDisplay.getDisplay().getDisplayId()),
                eq(new ComponentName(context, EmptyActivity.class)));

        emptyActivity.finish();

        verify(activityListener, timeout(3000))
                .onDisplayEmpty(eq(virtualDisplay.getDisplay().getDisplayId()));
    }

    @Test
    public void launchPendingIntent_activityIntent_shouldLaunchActivity() throws Exception {
        PendingIntent pendingIntent = getTestAppService()
                .createActivityPendingIntent(mResultReceiver);
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
                new Handler(Looper.getMainLooper()),
                mVirtualDisplayCallback);

        mVirtualDevice.launchPendingIntent(virtualDisplay.getDisplay().getDisplayId(),
                pendingIntent, Runnable::run, mLaunchCallback);

        verify(mOnReceiveResultListener, timeout(5000)).onReceiveResult(
                eq(Activity.RESULT_OK), nullable(Bundle.class));
    }

    @Test
    public void launchPendingIntent_serviceIntent_shouldLaunchTrampolineActivity()
            throws Exception {
        PendingIntent pendingIntent = getTestAppService()
                .createServicePendingIntent(/* trampoline= */ true, mResultReceiver);
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
                new Handler(Looper.getMainLooper()),
                mVirtualDisplayCallback);

        mVirtualDevice.launchPendingIntent(
                virtualDisplay.getDisplay().getDisplayId(),
                pendingIntent, Runnable::run,
                mLaunchCallback);

        verify(mOnReceiveResultListener, timeout(5000)).onReceiveResult(
                eq(Activity.RESULT_OK), nullable(Bundle.class));
    }

    @Test
    public void launchPendingIntent_serviceIntentNoTrampoline_shouldBeNoOp()
            throws Exception {
        PendingIntent pendingIntent = getTestAppService()
                .createServicePendingIntent(/* trampoline=*/ false, mResultReceiver);
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
                new Handler(Looper.getMainLooper()),
                mVirtualDisplayCallback);

        mVirtualDevice.launchPendingIntent(
                virtualDisplay.getDisplay().getDisplayId(),
                pendingIntent,
                Runnable::run,
                mLaunchCallback);

        verify(mOnReceiveResultListener, after(5000).never()).onReceiveResult(
                eq(Activity.RESULT_OK), nullable(Bundle.class));
    }

    private IStreamedTestApp getTestAppService() throws Exception {
        mServiceConnection = TestAppHelper.createTestAppService();
        return mServiceConnection.getFuture().get(10, TimeUnit.SECONDS);
    }
}

