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
import static android.virtualdevice.cts.common.util.VirtualDeviceTestUtils.createActivityOptions;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeNotNull;
import static org.junit.Assume.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;

import android.annotation.Nullable;
import android.app.Activity;
import android.app.ActivityManager;
import android.companion.virtual.VirtualDeviceManager;
import android.companion.virtual.VirtualDeviceManager.IntentInterceptorCallback;
import android.companion.virtual.VirtualDeviceManager.VirtualDevice;
import android.companion.virtual.VirtualDeviceParams;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.platform.test.annotations.AppModeFull;
import android.virtualdevice.cts.applaunch.util.EmptyActivity;
import android.virtualdevice.cts.applaunch.util.InterceptedActivity;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.concurrent.Executors;


@RunWith(AndroidJUnit4.class)
@AppModeFull(reason = "VirtualDeviceManager cannot be accessed by instant apps")
public class ActivityInterceptionTest {

    private static final int TIMEOUT_MS = 3000;

    private static final String ACTION_INTERCEPTED_RECEIVER =
            "android.virtualdevice.applaunch.util.INTERCEPTED_RECEIVER";

    private static final VirtualDeviceParams DEFAULT_VIRTUAL_DEVICE_PARAMS =
            new VirtualDeviceParams.Builder().build();

    @Rule
    public AdoptShellPermissionsRule mAdoptShellPermissionsRule = new AdoptShellPermissionsRule(
            InstrumentationRegistry.getInstrumentation().getUiAutomation(),
            ADD_TRUSTED_DISPLAY,
            CREATE_VIRTUAL_DEVICE);

    @Rule
    public FakeAssociationRule mFakeAssociationRule = new FakeAssociationRule();

    @Nullable
    private VirtualDevice mVirtualDevice;
    @Nullable
    private VirtualDisplay mVirtualDisplay;
    private VirtualDeviceManager mVirtualDeviceManager;
    private ActivityManager mActivityManager;
    private Context mContext;

    @Mock
    private IntentInterceptorCallback mInterceptor;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mContext = getApplicationContext();
        final PackageManager packageManager = mContext.getPackageManager();
        assumeTrue(packageManager.hasSystemFeature(
                PackageManager.FEATURE_ACTIVITIES_ON_SECONDARY_DISPLAYS));
        mVirtualDeviceManager = mContext.getSystemService(VirtualDeviceManager.class);
        assumeNotNull(mVirtualDeviceManager);
        mActivityManager = mContext.getSystemService(ActivityManager.class);
        mVirtualDevice =
                mVirtualDeviceManager.createVirtualDevice(
                        mFakeAssociationRule.getAssociationInfo().getId(),
                        DEFAULT_VIRTUAL_DEVICE_PARAMS);
        mVirtualDisplay = mVirtualDevice.createVirtualDisplay(
                VirtualDeviceTestUtils.createDefaultVirtualDisplayConfigBuilder()
                        .setFlags(DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY
                                | DisplayManager.VIRTUAL_DISPLAY_FLAG_TRUSTED
                                | DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC)
                        .build(),
                null, null);
    }

    @After
    public void tearDown() {
        if (mVirtualDevice != null) {
            mVirtualDevice.close();
        }
    }

    @Test
    public void noInterceptorRegistered_activityShouldLaunch() {
        EmptyActivity emptyActivity = launchEmptyActivityOnVirtualDisplay();
        EmptyActivity.Callback callback = mock(EmptyActivity.Callback.class);
        emptyActivity.setCallback(callback);

        int requestCode = 1;
        emptyActivity.startActivityForResult(createInterceptedIntent(), requestCode,
                createActivityOptions(mVirtualDisplay));

        verify(callback, timeout(TIMEOUT_MS)).onActivityResult(
                eq(requestCode), eq(Activity.RESULT_OK), any());
        verifyZeroInteractions(mInterceptor);
    }

    @Test
    public void interceptorRegistered_intentIsIntercepted() {
        IntentFilter intentFilter = new IntentFilter(ACTION_INTERCEPTED_RECEIVER);
        mVirtualDevice.registerIntentInterceptor(intentFilter, Executors.newSingleThreadExecutor(),
                mInterceptor);

        EmptyActivity emptyActivity = launchEmptyActivityOnVirtualDisplay();

        // Send intent that is intercepted
        Intent interceptedIntent = createInterceptedIntent();
        assertThat(mActivityManager.isActivityStartAllowedOnDisplay(
                emptyActivity, mVirtualDisplay.getDisplay().getDisplayId(), interceptedIntent))
                .isTrue();
        emptyActivity.startActivity(interceptedIntent, createActivityOptions(mVirtualDisplay));
        Intent capturedIntent = captureInterceptedIntent(mInterceptor);

        assertThat(capturedIntent).isNotNull();
        assertThat(capturedIntent.getAction()).isEqualTo(interceptedIntent.getAction());
        assertThat(capturedIntent.getData()).isEqualTo(interceptedIntent.getData());
        assertThat(capturedIntent.getExtras()).isNull();

        // Unregister interceptor and verify intent is not intercepted
        mVirtualDevice.unregisterIntentInterceptor(mInterceptor);
        emptyActivity.startActivity(interceptedIntent, createActivityOptions(mVirtualDisplay));
        verifyNoMoreInteractions(mInterceptor);
    }

    @Test
    public void interceptorRegistered_intentIsIntercepted_fromDefaultDisplay() {
        IntentFilter intentFilter = new IntentFilter(ACTION_INTERCEPTED_RECEIVER);
        mVirtualDevice.registerIntentInterceptor(intentFilter, Executors.newSingleThreadExecutor(),
                mInterceptor);

        // Send intent that is intercepted
        Intent interceptedIntent = createInterceptedIntent()
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        assertThat(mActivityManager.isActivityStartAllowedOnDisplay(
                mContext, mVirtualDisplay.getDisplay().getDisplayId(), interceptedIntent))
                .isTrue();
        mContext.startActivity(interceptedIntent, createActivityOptions(mVirtualDisplay));
        Intent capturedIntent = captureInterceptedIntent(mInterceptor);

        assertThat(capturedIntent).isNotNull();
        assertThat(capturedIntent.getAction()).isEqualTo(interceptedIntent.getAction());
        assertThat(capturedIntent.getData()).isEqualTo(interceptedIntent.getData());
        assertThat(capturedIntent.getExtras()).isNull();

        // Unregister interceptor and verify intent is not intercepted
        mVirtualDevice.unregisterIntentInterceptor(mInterceptor);
        mContext.startActivity(interceptedIntent, createActivityOptions(mVirtualDisplay));
        verifyNoMoreInteractions(mInterceptor);
    }

    @Test
    public void multipleInterceptorsRegistered_intentIsIntercepted() {
        IntentInterceptorCallback interceptorOther = mock(IntentInterceptorCallback.class);
        IntentFilter intentFilter = new IntentFilter(ACTION_INTERCEPTED_RECEIVER);
        mVirtualDevice.registerIntentInterceptor(intentFilter, Executors.newSingleThreadExecutor(),
                mInterceptor);
        mVirtualDevice.registerIntentInterceptor(intentFilter, Executors.newSingleThreadExecutor(),
                interceptorOther);

        EmptyActivity emptyActivity = launchEmptyActivityOnVirtualDisplay();

        Intent interceptedIntent = createInterceptedIntent();
        emptyActivity.startActivity(interceptedIntent, createActivityOptions(mVirtualDisplay));

        verify(mInterceptor, timeout(TIMEOUT_MS).times(1)).onIntentIntercepted(any());
        verify(interceptorOther, timeout(TIMEOUT_MS).times(1)).onIntentIntercepted(any());

        // Unregister first interceptor and verify intent is intercepted by other
        reset(mInterceptor);
        reset(interceptorOther);
        mVirtualDevice.unregisterIntentInterceptor(mInterceptor);

        emptyActivity.startActivity(interceptedIntent, createActivityOptions(mVirtualDisplay));

        verifyZeroInteractions(mInterceptor);
        Intent capturedIntent = captureInterceptedIntent(interceptorOther);

        assertThat(capturedIntent).isNotNull();
        assertThat(capturedIntent.getAction()).isEqualTo(interceptedIntent.getAction());
        assertThat(capturedIntent.getData()).isEqualTo(interceptedIntent.getData());
        assertThat(capturedIntent.getExtras()).isNull();

        mVirtualDevice.unregisterIntentInterceptor(interceptorOther);
    }

    @Test
    public void noMatchingInterceptor_activityShouldLaunch() {
        mInterceptor = mock(IntentInterceptorCallback.class);
        IntentFilter intentFilter = new IntentFilter("ACTION_OTHER");
        mVirtualDevice.registerIntentInterceptor(intentFilter, Executors.newSingleThreadExecutor(),
                mInterceptor);

        EmptyActivity emptyActivity = launchEmptyActivityOnVirtualDisplay();
        EmptyActivity.Callback callback = mock(EmptyActivity.Callback.class);
        emptyActivity.setCallback(callback);

        // Send intent that is intercepted
        Intent allowedIntent = createInterceptedIntent();
        int requestCode = 1;
        emptyActivity.startActivityForResult(
                allowedIntent,
                requestCode,
                createActivityOptions(mVirtualDisplay));

        verify(callback, timeout(TIMEOUT_MS)).onActivityResult(
                eq(requestCode), eq(Activity.RESULT_OK), any());

        verifyZeroInteractions(mInterceptor);
        mVirtualDevice.unregisterIntentInterceptor(mInterceptor);
    }

    @Test
    public void differentInterceptorsRegistered_oneIntercepted() {
        // setup expected intent interceptor
        IntentFilter intentFilter = new IntentFilter(ACTION_INTERCEPTED_RECEIVER);
        mVirtualDevice.registerIntentInterceptor(intentFilter, Executors.newSingleThreadExecutor(),
                mInterceptor);

        // setup other intent interceptor
        IntentInterceptorCallback interceptorOther = mock(IntentInterceptorCallback.class);
        IntentFilter intentFilterOther = new IntentFilter("ACTION_OTHER");
        mVirtualDevice.registerIntentInterceptor(intentFilterOther,
                Executors.newSingleThreadExecutor(), interceptorOther);

        EmptyActivity emptyActivity = launchEmptyActivityOnVirtualDisplay();

        // Send intent that is intercepted
        Intent interceptedIntent = createInterceptedIntent();
        emptyActivity.startActivity(interceptedIntent, createActivityOptions(mVirtualDisplay));

        Intent capturedIntent = captureInterceptedIntent(mInterceptor);

        assertThat(capturedIntent).isNotNull();
        assertThat(capturedIntent.getAction()).isEqualTo(interceptedIntent.getAction());
        assertThat(capturedIntent.getData()).isEqualTo(interceptedIntent.getData());
        assertThat(capturedIntent.getExtras()).isNull();

        verifyZeroInteractions(interceptorOther);
        mVirtualDevice.unregisterIntentInterceptor(mInterceptor);
        mVirtualDevice.unregisterIntentInterceptor(interceptorOther);
    }

    @Test
    public void registerIntentInterceptor_nullArguments_shouldThrow() {
        assertThrows(NullPointerException.class,
                () -> mVirtualDevice.registerIntentInterceptor(null,
                        Executors.newSingleThreadExecutor(), mInterceptor));

        assertThrows(NullPointerException.class,
                () -> mVirtualDevice.registerIntentInterceptor(new IntentFilter(),
                        null, mInterceptor));

        assertThrows(NullPointerException.class,
                () -> mVirtualDevice.registerIntentInterceptor(new IntentFilter(),
                        Executors.newSingleThreadExecutor(), null));
    }

    @Test
    public void unregisterIntentInterceptor_nullArguments_shouldThrow() {
        assertThrows(NullPointerException.class,
                () -> mVirtualDevice.unregisterIntentInterceptor(null));
    }

    private static Intent createInterceptedIntent() {
        return new Intent(ACTION_INTERCEPTED_RECEIVER)
                .setClass(getApplicationContext(), InterceptedActivity.class)
                .putExtra("TEST", "extra");
    }

    private EmptyActivity launchEmptyActivityOnVirtualDisplay() {
        Intent intent = new Intent(Intent.ACTION_MAIN)
                .setClass(mContext, EmptyActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return (EmptyActivity) InstrumentationRegistry.getInstrumentation()
                .startActivitySync(intent, createActivityOptions(mVirtualDisplay));
    }

    private Intent captureInterceptedIntent(IntentInterceptorCallback interceptor) {
        ArgumentCaptor<Intent> captor = ArgumentCaptor.forClass(Intent.class);
        verify(interceptor, timeout(TIMEOUT_MS).times(1)).onIntentIntercepted(captor.capture());
        return captor.getValue();
    }
}
