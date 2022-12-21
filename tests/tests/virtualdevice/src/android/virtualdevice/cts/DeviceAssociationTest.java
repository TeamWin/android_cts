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
import static android.Manifest.permission.CREATE_VIRTUAL_DEVICE;
import static android.companion.virtual.VirtualDeviceManager.DEVICE_ID_DEFAULT;
import static android.companion.virtual.VirtualDeviceManager.DEVICE_ID_INVALID;
import static android.view.Display.DEFAULT_DISPLAY;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import android.annotation.Nullable;
import android.app.ActivityThread;
import android.companion.virtual.VirtualDeviceManager;
import android.companion.virtual.VirtualDeviceParams;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.Configuration;
import android.hardware.display.DisplayManager;
import android.platform.test.annotations.AppModeFull;
import android.view.Display;
import android.virtualdevice.cts.util.FakeAssociationRule;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.AdoptShellPermissionsRule;
import com.android.compatibility.common.util.ApiTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.concurrent.Executor;
import java.util.function.IntConsumer;

@RunWith(AndroidJUnit4.class)
@ApiTest(apis = {"android.content.Context#updateDeviceId",
        "android.content.Context#isDeviceContext",
        "android.content.Context#unregisterDeviceIdChangeListener",
        "android.content.Context#registerDeviceIdChangeListener"}
)
@AppModeFull(reason = "VirtualDeviceManager cannot be accessed by instant apps")
public class DeviceAssociationTest {

    private static final VirtualDeviceParams DEFAULT_VIRTUAL_DEVICE_PARAMS =
            new VirtualDeviceParams.Builder().build();

    private Executor mTestExecutor;
    @Mock private IntConsumer mDeviceChangeListener;
    @Mock private IntConsumer mDeviceChangeListener2;
    private Display mDefaultDisplay;

    @Rule
    public AdoptShellPermissionsRule mAdoptShellPermissionsRule = new AdoptShellPermissionsRule(
            InstrumentationRegistry.getInstrumentation().getUiAutomation(),
            ACTIVITY_EMBEDDING,
            ADD_ALWAYS_UNLOCKED_DISPLAY,
            CREATE_VIRTUAL_DEVICE);

    @Rule
    public FakeAssociationRule mFakeAssociationRule = new FakeAssociationRule();

    private VirtualDeviceManager mVirtualDeviceManager;
    @Nullable
    private VirtualDeviceManager.VirtualDevice mVirtualDevice;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        Context context = getApplicationContext();
        mVirtualDeviceManager = context.getSystemService(VirtualDeviceManager.class);
        mTestExecutor = context.getMainExecutor();
        final DisplayManager dm = getApplicationContext().getSystemService(DisplayManager.class);
        mDefaultDisplay = dm.getDisplay(DEFAULT_DISPLAY);
    }

    @After
    public void tearDown() {
        if (mVirtualDevice != null) {
            mVirtualDevice.close();
        }
    }

    @Test
    @ApiTest(apis = {"android.content.Context#registerDeviceIdChangeListener"})
    public void registerDeviceIdChangeListener_nullListener_throwsException() {
        Context context = getApplicationContext();

        assertThrows(
                NullPointerException.class,
                () -> context.registerDeviceIdChangeListener(mTestExecutor, null));
    }

    @Test
    @ApiTest(apis = {"android.content.Context#registerDeviceIdChangeListener"})
    public void registerDeviceIdChangeListener_nullExecutor_throwsException() {
        Context context = getApplicationContext();

        assertThrows(
                NullPointerException.class,
                () -> context.registerDeviceIdChangeListener(null, mDeviceChangeListener));
    }

    @Test
    @ApiTest(apis = {"android.content.Context#unregisterDeviceIdChangeListener"})
    public void registerDeviceIdChangeListener_registerTwice_throwsException() {
        Context context = getApplicationContext();

        context.registerDeviceIdChangeListener(mTestExecutor, mDeviceChangeListener);

        assertThrows(
                IllegalArgumentException.class,
                () -> context.registerDeviceIdChangeListener(mTestExecutor, mDeviceChangeListener));
    }

    @Test
    @ApiTest(apis = {"android.content.Context#unregisterDeviceIdChangeListener"})
    public void unregisterDeviceChangedListener_nullListener_throwsException() {
        Context context = getApplicationContext();

        assertThrows(
                NullPointerException.class,
                () -> context.unregisterDeviceIdChangeListener(null));
    }

    @Test
    @ApiTest(apis = {"android.content.Context#unregisterDeviceIdChangeListener"})
    public void unregisterDeviceChangedListener_succeeds() {
        Context context = getApplicationContext();
        context.registerDeviceIdChangeListener(mTestExecutor, mDeviceChangeListener);
        int virtualDeviceId = createVirtualDevice();

        context.updateDeviceId(virtualDeviceId);

        assertThat(context.getDeviceId()).isEqualTo(virtualDeviceId);
        verify(mDeviceChangeListener).accept(virtualDeviceId);


        // DeviceId of listener is not updated after unregistering.
        context.unregisterDeviceIdChangeListener(mDeviceChangeListener);
        context.updateDeviceId(DEVICE_ID_DEFAULT);

        assertThat(context.getDeviceId()).isEqualTo(DEVICE_ID_DEFAULT);
        verifyNoMoreInteractions(mDeviceChangeListener);
    }

    @Test
    @ApiTest(apis = {"android.content.Context#isDeviceContext"})
    public void isDeviceContext_appContext_returnsFalse() {
        final Context appContext = getApplicationContext();

        assertThat(appContext.isDeviceContext()).isFalse();
    }

    @Test
    @ApiTest(apis = {"android.content.Context#isDeviceContext"})
    public void isDeviceContext_systemContext_returnsTrue() {
        final Context systemContext =
                ActivityThread.currentActivityThread().getSystemContext();

        assertThat(systemContext.isDeviceContext()).isTrue();
    }

    @Test
    @ApiTest(apis = {"android.content.Context#isDeviceContext"})
    public void isDeviceContext_systemUiContext_returnsTrue() {
        final Context systemUiContext =
                ActivityThread.currentActivityThread().getSystemUiContext();

        assertThat(systemUiContext.isDeviceContext()).isTrue();
    }

    @Test
    @ApiTest(apis = {"android.content.Context#isDeviceContext"})
    public void isDeviceContext_displayContext_returnsTrue() {
        final Context displayContext =
                getApplicationContext().createDisplayContext(mDefaultDisplay);

        assertThat(displayContext.isDeviceContext()).isTrue();
    }

    @Test
    @ApiTest(apis = {"android.content.Context#isDeviceContext"})
    public void isDeviceContext_ContextWrapper_isTrueIfBaseIsDeviceContext() {
        Context appContext = getApplicationContext();
        int virtualDeviceId = createVirtualDevice();
        Context deviceContext = appContext.createDeviceContext(virtualDeviceId);
        ContextWrapper wrapper = new ContextWrapper(appContext);

        assertFalse(wrapper.isDeviceContext());
        assertThat(wrapper.getDeviceId()).isEqualTo(DEVICE_ID_DEFAULT);

        wrapper = new ContextWrapper(deviceContext);

        assertTrue(wrapper.isDeviceContext());
        assertThat(wrapper.getDeviceId()).isEqualTo(virtualDeviceId);
    }

    @Test
    @ApiTest(apis = {"android.content.Context#isDeviceContext"})
    public void isDeviceContext_derivedContext_returnsTrue() {
        Context appContext = getApplicationContext();
        int virtualDeviceId = createVirtualDevice();
        Context deviceContext = appContext.createDeviceContext(virtualDeviceId);

        Context derivedContext = deviceContext.createConfigurationContext(Configuration.EMPTY);

        assertTrue(derivedContext.isDeviceContext());
        assertThat(derivedContext.getDeviceId()).isEqualTo(virtualDeviceId);
    }

    @Test
    @ApiTest(apis = {"android.content.Context#isDeviceContext"})
    public void isDeviceContext_deviceContext_returnsTrue() {
        final Context appContext = getApplicationContext();
        Context deviceContext = appContext.createDeviceContext(DEVICE_ID_DEFAULT);

        assertTrue(deviceContext.isDeviceContext());
    }

    @Test
    @ApiTest(apis = {"android.content.Context#updateDeviceId"})
    public void updateDeviceId_invalidDeviceId_shouldThrowIllegalArgumentException() {
        Context context = getApplicationContext();

        assertThrows(
                IllegalArgumentException.class,
                () -> context.updateDeviceId(DEVICE_ID_INVALID));
    }

    @Test
    @ApiTest(apis = {"android.content.Context#updateDeviceId"})
    public void updateDeviceId_missingDeviceId_shouldThrowIllegalArgumentException() {
        int virtualDeviceId = createVirtualDevice();
        Context context = getApplicationContext();

        assertThrows(
                IllegalArgumentException.class,
                () -> context.updateDeviceId(virtualDeviceId + 1));
    }

    @Test
    @ApiTest(apis = {"android.content.Context#updateDeviceId"})
    public void updateDeviceId_defaultDeviceId() {
        Context context = getApplicationContext();

        context.updateDeviceId(DEVICE_ID_DEFAULT);

        assertThat(context.getDeviceId()).isEqualTo(DEVICE_ID_DEFAULT);
    }

    @Test
    @ApiTest(apis = {"android.content.Context#updateDeviceId"})
    public void updateDeviceId_validVirtualDeviceId() {
        int virtualDeviceId = createVirtualDevice();
        Context context = getApplicationContext();

        context.updateDeviceId(virtualDeviceId);

        assertThat(context.getDeviceId()).isEqualTo(virtualDeviceId);
    }

    @Test
    @ApiTest(apis = {"android.content.Context#updateDeviceId"})
    public void updateDeviceId_convertToDeviceContext_overridesValue() {
        int virtualDeviceId = createVirtualDevice();
        Context context = getApplicationContext();

        context.updateDeviceId(virtualDeviceId);
        assertThat(context.getDeviceId()).isEqualTo(virtualDeviceId);

        Context defaultDeviceContext = context.createDeviceContext(DEVICE_ID_DEFAULT);

        assertThat(defaultDeviceContext.getDeviceId()).isEqualTo(DEVICE_ID_DEFAULT);
    }

    @Test
    @ApiTest(apis = {"android.content.Context#updateDeviceId"})
    public void updateDeviceId_onDeviceContext_throwsException() {
        int virtualDeviceId = createVirtualDevice();
        Context context = getApplicationContext();
        Context deviceContext = context.createDeviceContext(virtualDeviceId);
        deviceContext.registerDeviceIdChangeListener(mTestExecutor, mDeviceChangeListener);

        assertThrows(
                UnsupportedOperationException.class,
                () -> deviceContext.updateDeviceId(DEVICE_ID_DEFAULT));
    }

    @Test
    @ApiTest(apis = {"android.content.Context#updateDeviceId"})
    public void updateDeviceId_notifiesListeners() {
        int virtualDeviceId = createVirtualDevice();
        Context context = getApplicationContext();
        context.registerDeviceIdChangeListener(mTestExecutor, mDeviceChangeListener);
        context.registerDeviceIdChangeListener(mTestExecutor, mDeviceChangeListener2);

        context.updateDeviceId(virtualDeviceId);

        assertThat(context.getDeviceId()).isEqualTo(virtualDeviceId);
        verify(mDeviceChangeListener, times(1)).accept(virtualDeviceId);
        verify(mDeviceChangeListener2, times(1)).accept(virtualDeviceId);
    }

    // Create a virtual device and return its id.
    private int createVirtualDevice() {
        mVirtualDevice = mVirtualDeviceManager.createVirtualDevice(
                mFakeAssociationRule.getAssociationInfo().getId(),
                DEFAULT_VIRTUAL_DEVICE_PARAMS);
        return mVirtualDevice.getDeviceId();
    }
}
