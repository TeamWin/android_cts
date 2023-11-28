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

package android.virtualdevice.cts.applaunch;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeTrue;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import android.companion.virtual.VirtualDeviceManager.VirtualDevice;
import android.companion.virtual.VirtualDeviceParams;
import android.companion.virtual.flags.Flags;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.inputmethodservice.InputMethodService;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.view.Display;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.virtualdevice.cts.applaunch.AppComponents.EmptyActivity;
import android.virtualdevice.cts.common.VirtualDeviceRule;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.FeatureUtil;
import com.android.compatibility.common.util.SystemUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Tests for IME behavior on virtual devices.
 */
@RunWith(AndroidJUnit4.class)
@AppModeFull(reason = "VirtualDeviceManager cannot be accessed by instant apps")
@RequiresFlagsEnabled(Flags.FLAG_VDM_CUSTOM_IME)
public class VirtualDeviceImeTest {

    private static final long TIMEOUT_MILLIS = TimeUnit.SECONDS.toMillis(3);

    @Rule
    public VirtualDeviceRule mRule = VirtualDeviceRule.createDefault();

    @Rule
    public MockitoRule mMockitoRule = MockitoJUnit.rule();

    private final Context mContext = getInstrumentation().getContext();
    private final InputMethodManager mInputMethodManager =
            mContext.getSystemService(InputMethodManager.class);

    private int mVirtualDisplayId;

    @Mock
    private Consumer<Integer> mDefaultDeviceImeListener;
    @Mock
    private Consumer<Integer> mVirtualDeviceImeListener;


    @Before
    public void setUp() throws Exception {
        assumeTrue(FeatureUtil.hasSystemFeature(PackageManager.FEATURE_INPUT_METHODS));

        DefaultDeviceTestIme.sShowInputOnDisplayRequestListener = mDefaultDeviceImeListener;
        VirtualDeviceTestIme.sShowInputOnDisplayRequestListener = mVirtualDeviceImeListener;

        enableTestIme(DefaultDeviceTestIme.class, /* makeDefault= */ true);
        enableTestIme(VirtualDeviceTestIme.class, /* makeDefault= */ false);
    }

    @After
    public void tearDown() throws Exception {
        SystemUtil.runShellCommandOrThrow("ime reset");
    }

    private <T extends InputMethodService> String enableTestIme(
            Class<T> imeClass, boolean makeDefault) {
        final String imeId =
                new ComponentName(mContext, imeClass.getName()).flattenToShortString();
        SystemUtil.runShellCommandOrThrow("ime enable " + imeId);
        if (makeDefault) {
            SystemUtil.runShellCommandOrThrow("ime set " + imeId);
            assertThat(mInputMethodManager.getCurrentInputMethodInfo().getId()).isEqualTo(imeId);
        }
        return imeId;
    }

    /** The virtualDeviceOnly attribute is propagated to InputMethodInfo. */
    @ApiTest(apis = {"android.R.attr#isVirtualDeviceOnly"})
    @Test
    public void virtualDeviceOnlyIme_reflectedInInputMethodInfo() {
        final InputMethodInfo virtualDeviceImi =
                getInputMethodInfo(VirtualDeviceTestIme.class.getName());

        assertThat(virtualDeviceImi).isNotNull();
        assertThat(virtualDeviceImi.isVirtualDeviceOnly()).isTrue();

        final InputMethodInfo defaultDeviceImi =
                getInputMethodInfo(DefaultDeviceTestIme.class.getName());

        assertThat(defaultDeviceImi).isNotNull();
        assertThat(defaultDeviceImi.isVirtualDeviceOnly()).isFalse();
    }

    /** The default IME is used on virtual devices when there's no custom IME component. */
    @Test
    public void noCustomImeComponent_defaultImeShouldBeOnVirtualDisplay() {
        createVirtualDeviceAndDisplay(/* imeComponent= */ Optional.empty());

        showSoftInputOnDisplay(Display.DEFAULT_DISPLAY);
        verify(mDefaultDeviceImeListener, timeout(TIMEOUT_MILLIS)).accept(Display.DEFAULT_DISPLAY);

        showSoftInputOnDisplay(mVirtualDisplayId);
        verify(mDefaultDeviceImeListener, timeout(TIMEOUT_MILLIS)).accept(mVirtualDisplayId);
    }

    /** The default IME is used on virtual devices when the custom IME component is {@code null}. */
    @ApiTest(apis = {
            "android.companion.virtual.VirtualDeviceParams.Builder#setInputMethodComponent"})
    @Test
    public void nullCustomImeComponent_defaultImeShouldBeOnVirtualDisplay() {
        createVirtualDeviceAndDisplay(/* imeComponent= */ Optional.ofNullable(null));

        showSoftInputOnDisplay(Display.DEFAULT_DISPLAY);
        verify(mDefaultDeviceImeListener, timeout(TIMEOUT_MILLIS)).accept(Display.DEFAULT_DISPLAY);

        showSoftInputOnDisplay(mVirtualDisplayId);
        verify(mDefaultDeviceImeListener, timeout(TIMEOUT_MILLIS)).accept(mVirtualDisplayId);
    }

    private InputMethodInfo getInputMethodInfo(String className) {
        final String imeId = new ComponentName(mContext, className).flattenToShortString();
        return mInputMethodManager.getInputMethodList().stream()
                .filter(imi -> imi.getId().equals(imeId)).findFirst().orElse(null);
    }

    private void showSoftInputOnDisplay(int displayId) {
        EmptyActivity activity = mRule.startActivityOnDisplaySync(displayId, EmptyActivity.class);

        InputMethodManager activityInputMethodManager =
                activity.getSystemService(InputMethodManager.class);

        getInstrumentation().runOnMainSync(
                () -> activityInputMethodManager.showSoftInput(
                        activity.getWindow().getDecorView(), 0));
        getInstrumentation().waitForIdleSync();
    }

    private void createVirtualDeviceAndDisplay(Optional<ComponentName> imeComponent) {
        VirtualDeviceParams.Builder builder = new VirtualDeviceParams.Builder();
        imeComponent.ifPresent(componentName -> builder.setInputMethodComponent(componentName));
        VirtualDevice virtualDevice = mRule.createManagedVirtualDevice(builder.build());

        VirtualDisplay virtualDisplay = mRule.createManagedVirtualDisplayWithFlags(virtualDevice,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC
                        | DisplayManager.VIRTUAL_DISPLAY_FLAG_TRUSTED
                        | DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY);
        mVirtualDisplayId = virtualDisplay.getDisplay().getDisplayId();
    }


    /**
     * Simple IME implementation forwarding the show input requests to a listener along with a
     * display id.
     */
    public static class DefaultDeviceTestIme extends InputMethodService {

        static Consumer<Integer> sShowInputOnDisplayRequestListener = null;

        @Override
        public boolean onShowInputRequested(int flags, boolean configChange) {
            if (sShowInputOnDisplayRequestListener != null) {
                sShowInputOnDisplayRequestListener.accept(
                        getWindow().getWindow().getDecorView().getDisplay().getDisplayId());
            }
            return true;
        }
    }

    /**
     * Simple IME implementation forwarding the show input requests to a listener along with a
     * display id.
     */
    public static class VirtualDeviceTestIme extends InputMethodService {

        static Consumer<Integer> sShowInputOnDisplayRequestListener = null;

        @Override
        public boolean onShowInputRequested(int flags, boolean configChange) {
            if (sShowInputOnDisplayRequestListener != null) {
                sShowInputOnDisplayRequestListener.accept(
                        getWindow().getWindow().getDecorView().getDisplay().getDisplayId());
            }
            return true;
        }
    }
}
