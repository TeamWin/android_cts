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

import static android.Manifest.permission.ADD_ALWAYS_UNLOCKED_DISPLAY;
import static android.Manifest.permission.ADD_TRUSTED_DISPLAY;
import static android.Manifest.permission.CREATE_VIRTUAL_DEVICE;
import static android.Manifest.permission.WAKE_LOCK;
import static android.virtualdevice.cts.common.util.VirtualDeviceTestUtils.createDefaultVirtualDisplayConfigBuilder;
import static android.virtualdevice.cts.common.util.VirtualDeviceTestUtils.isVirtualDeviceManagerConfigEnabled;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeTrue;

import android.annotation.Nullable;
import android.companion.virtual.VirtualDeviceManager;
import android.companion.virtual.VirtualDeviceManager.VirtualDevice;
import android.companion.virtual.VirtualDeviceParams;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.hardware.display.VirtualDisplayConfig;
import android.platform.test.annotations.AppModeFull;
import android.view.Display;
import android.virtualdevice.cts.common.FakeAssociationRule;
import android.virtualdevice.cts.common.util.VirtualDeviceTestUtils;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.AdoptShellPermissionsRule;

import com.google.common.base.Objects;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@RunWith(AndroidJUnit4.class)
@AppModeFull(reason = " cannot be accessed by instant apps")
public class VirtualDisplayTest {
    private static final int DISPLAY_WIDTH = 640;
    private static final int DISPLAY_HEIGHT = 480;
    private static final int DISPLAY_DPI = 420;
    private static final String DISPLAY_NAME = "TestVirtualDisplay";
    private static final int TIMEOUT_MILLIS = 1000;

    private static final VirtualDeviceParams DEFAULT_VIRTUAL_DEVICE_PARAMS =
            new VirtualDeviceParams.Builder().build();
    private static final VirtualDisplayConfig DEFAULT_VIRTUAL_DISPLAY_CONFIG =
            createDefaultVirtualDisplayConfigBuilder().build();

    @Rule
    public AdoptShellPermissionsRule mAdoptShellPermissionsRule = new AdoptShellPermissionsRule(
            InstrumentationRegistry.getInstrumentation().getUiAutomation(),
            ADD_ALWAYS_UNLOCKED_DISPLAY,
            ADD_TRUSTED_DISPLAY,
            CREATE_VIRTUAL_DEVICE,
            WAKE_LOCK);

    @Rule
    public FakeAssociationRule mFakeAssociationRule = new FakeAssociationRule();

    private VirtualDeviceManager mVirtualDeviceManager;
    private DisplayManager mDisplayManager;
    private VirtualDeviceTestUtils.DisplayListenerForTest mDisplayListener;
    @Nullable
    private VirtualDevice mVirtualDevice;
    @Mock
    private VirtualDisplay.Callback mVirtualDisplayCallback;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        Context context = getApplicationContext();
        final PackageManager packageManager = context.getPackageManager();
        assumeTrue(isVirtualDeviceManagerConfigEnabled(context));
        assumeTrue(packageManager.hasSystemFeature(
                PackageManager.FEATURE_ACTIVITIES_ON_SECONDARY_DISPLAYS));
        mVirtualDeviceManager = context.getSystemService(VirtualDeviceManager.class);
        mDisplayManager = context.getSystemService(DisplayManager.class);
        mDisplayListener = new VirtualDeviceTestUtils.DisplayListenerForTest();
        mDisplayManager.registerDisplayListener(mDisplayListener, /*handler=*/null);
    }

    @After
    public void tearDown() {
        if (mVirtualDevice != null) {
            mVirtualDevice.close();
        }
        if (mDisplayManager != null && mDisplayListener != null) {
            mDisplayManager.unregisterDisplayListener(mDisplayListener);
        }
    }

    @Test
    public void createVirtualDisplay_shouldSucceed() {
        mVirtualDevice =
                mVirtualDeviceManager.createVirtualDevice(
                        mFakeAssociationRule.getAssociationInfo().getId(),
                        DEFAULT_VIRTUAL_DEVICE_PARAMS);

        VirtualDisplay virtualDisplay = mVirtualDevice.createVirtualDisplay(
                DEFAULT_VIRTUAL_DISPLAY_CONFIG, Runnable::run, mVirtualDisplayCallback);
        assertThat(mDisplayListener.waitForOnDisplayAddedCallback()).isTrue();

        assertThat(virtualDisplay).isNotNull();
        Display display = virtualDisplay.getDisplay();
        assertThat(display.isValid()).isTrue();
        assertThat(display.getWidth()).isEqualTo(DEFAULT_VIRTUAL_DISPLAY_CONFIG.getWidth());
        assertThat(display.getHeight()).isEqualTo(DEFAULT_VIRTUAL_DISPLAY_CONFIG.getHeight());
        assertThat(mDisplayListener.getObservedAddedDisplays()).containsExactly(
                Integer.valueOf(display.getDisplayId()));
    }

    @Test
    public void createVirtualDisplay_deprecatedOverload_shouldSucceed() {
        mVirtualDevice =
                mVirtualDeviceManager.createVirtualDevice(
                        mFakeAssociationRule.getAssociationInfo().getId(),
                        DEFAULT_VIRTUAL_DEVICE_PARAMS);

        VirtualDisplay virtualDisplay = mVirtualDevice.createVirtualDisplay(
                /* width= */ DISPLAY_WIDTH,
                /* height= */ DISPLAY_HEIGHT,
                /* densityDpi= */ DISPLAY_DPI,
                /* surface= */ null,
                /* flags= */ 0,
                Runnable::run,
                mVirtualDisplayCallback);
        assertThat(mDisplayListener.waitForOnDisplayAddedCallback()).isTrue();

        assertThat(virtualDisplay).isNotNull();
        Display display = virtualDisplay.getDisplay();
        assertThat(display.isValid()).isTrue();
        assertThat(display.getWidth()).isEqualTo(DISPLAY_WIDTH);
        assertThat(display.getHeight()).isEqualTo(DISPLAY_HEIGHT);
        assertThat(mDisplayListener.getObservedAddedDisplays()).containsExactly(
                Integer.valueOf(display.getDisplayId()));
    }

    @Test
    public void createVirtualDisplay_defaultVirtualDisplayFlags() {
        mVirtualDevice =
                mVirtualDeviceManager.createVirtualDevice(
                        mFakeAssociationRule.getAssociationInfo().getId(),
                        DEFAULT_VIRTUAL_DEVICE_PARAMS);

        VirtualDisplay virtualDisplay = mVirtualDevice.createVirtualDisplay(
                DEFAULT_VIRTUAL_DISPLAY_CONFIG, Runnable::run, mVirtualDisplayCallback);
        assertThat(mDisplayListener.waitForOnDisplayAddedCallback()).isTrue();

        Display display = virtualDisplay.getDisplay();
        assertThat(display.isValid()).isTrue();
        assertThat(display.getFlags()).isEqualTo(
                Display.FLAG_PRIVATE | Display.FLAG_TOUCH_FEEDBACK_DISABLED);
        // Private displays always destroy their content on removal
        assertThat(display.getRemoveMode()).isEqualTo(Display.REMOVE_MODE_DESTROY_CONTENT);
        assertThat(mDisplayListener.getObservedAddedDisplays()).containsExactly(
                display.getDisplayId());
    }

    @Test
    public void createVirtualDisplay_autoMirrorIsDefaultForPublicDisplays() {
        mVirtualDevice =
                mVirtualDeviceManager.createVirtualDevice(
                        mFakeAssociationRule.getAssociationInfo().getId(),
                        DEFAULT_VIRTUAL_DEVICE_PARAMS);

        VirtualDisplayConfig config = createDefaultVirtualDisplayConfigBuilder()
                .setFlags(DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC)
                .build();

        // TODO(b/286849916): Assert that the auto-mirror display is created.
        assertThrows(SecurityException.class,
                () -> mVirtualDevice.createVirtualDisplay(
                        config, Runnable::run, mVirtualDisplayCallback));
    }

    @Test
    public void createVirtualDisplay_trustedDisplay_shouldSpecifyOwnFocusFlag() {
        mVirtualDevice =
                mVirtualDeviceManager.createVirtualDevice(
                        mFakeAssociationRule.getAssociationInfo().getId(),
                        new VirtualDeviceParams.Builder()
                                .setDevicePolicy(VirtualDeviceParams.POLICY_TYPE_RECENTS,
                                        VirtualDeviceParams.DEVICE_POLICY_CUSTOM)
                                .build());

        VirtualDisplayConfig config = createDefaultVirtualDisplayConfigBuilder()
                .setFlags(DisplayManager.VIRTUAL_DISPLAY_FLAG_TRUSTED)
                .build();
        VirtualDisplay virtualDisplay = mVirtualDevice.createVirtualDisplay(
                config, Runnable::run, mVirtualDisplayCallback);
        assertThat(mDisplayListener.waitForOnDisplayAddedCallback()).isTrue();

        assertThat(virtualDisplay).isNotNull();
        Display display = virtualDisplay.getDisplay();
        assertThat(display.isValid()).isTrue();
        assertThat(display.getFlags()).isEqualTo(Display.FLAG_TOUCH_FEEDBACK_DISABLED
                | Display.FLAG_TRUSTED
                | Display.FLAG_PRIVATE
                | Display.FLAG_OWN_FOCUS);
        assertThat(mDisplayListener.getObservedAddedDisplays()).containsExactly(
                display.getDisplayId());
    }

    @Test
    public void createVirtualDisplay_alwaysUnlocked_shouldSpecifyAlwaysUnlockedFlag() {
        mVirtualDevice =
                mVirtualDeviceManager.createVirtualDevice(
                        mFakeAssociationRule.getAssociationInfo().getId(),
                        new VirtualDeviceParams.Builder()
                                .setLockState(VirtualDeviceParams.LOCK_STATE_ALWAYS_UNLOCKED)
                                .build());

        VirtualDisplay virtualDisplay = mVirtualDevice.createVirtualDisplay(
                DEFAULT_VIRTUAL_DISPLAY_CONFIG, Runnable::run, mVirtualDisplayCallback);
        assertThat(mDisplayListener.waitForOnDisplayAddedCallback()).isTrue();

        assertThat(virtualDisplay).isNotNull();
        Display display = virtualDisplay.getDisplay();
        assertThat(display.isValid()).isTrue();
        int displayFlags = display.getFlags();
        assertWithMessage(
                String.format(
                        "Virtual display flags (0x%x) should contain FLAG_ALWAYS_UNLOCKED",
                        displayFlags))
                .that(displayFlags & Display.FLAG_ALWAYS_UNLOCKED)
                .isNotEqualTo(0);
        assertThat(mDisplayListener.getObservedAddedDisplays()).containsExactly(
                display.getDisplayId());
    }

    @Test
    public void createVirtualDisplay_nullExecutorAndCallback_shouldSucceed() {
        mVirtualDevice =
                mVirtualDeviceManager.createVirtualDevice(
                        mFakeAssociationRule.getAssociationInfo().getId(),
                        DEFAULT_VIRTUAL_DEVICE_PARAMS);
        VirtualDisplay virtualDisplay = mVirtualDevice.createVirtualDisplay(
                DEFAULT_VIRTUAL_DISPLAY_CONFIG, /* executor= */ null, /* callback= */ null);
        assertThat(mDisplayListener.waitForOnDisplayAddedCallback()).isTrue();

        assertThat(virtualDisplay).isNotNull();
        Display display = virtualDisplay.getDisplay();
        assertThat(display.isValid()).isTrue();
        assertThat(mDisplayListener.getObservedAddedDisplays()).containsExactly(
                Integer.valueOf(display.getDisplayId()));
    }

    @Test
    public void createVirtualDisplay_nullExecutorButNonNullCallback_shouldThrow() {
        mVirtualDevice =
                mVirtualDeviceManager.createVirtualDevice(
                        mFakeAssociationRule.getAssociationInfo().getId(),
                        DEFAULT_VIRTUAL_DEVICE_PARAMS);

        assertThrows(NullPointerException.class,
                () -> mVirtualDevice.createVirtualDisplay(
                        DEFAULT_VIRTUAL_DISPLAY_CONFIG, /*executor=*/ null,
                        mVirtualDisplayCallback));
    }

    @Test
    public void virtualDisplay_createAndRemoveSeveralDisplays() {
        mVirtualDevice =
                mVirtualDeviceManager.createVirtualDevice(
                        mFakeAssociationRule.getAssociationInfo().getId(),
                        DEFAULT_VIRTUAL_DEVICE_PARAMS);
        ArrayList<VirtualDisplay> displays = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            displays.add(mVirtualDevice.createVirtualDisplay(
                    DEFAULT_VIRTUAL_DISPLAY_CONFIG, Runnable::run, mVirtualDisplayCallback));
        }

        // Releasing several displays in quick succession should not cause deadlock
        displays.forEach(VirtualDisplay::release);

        assertThat(mDisplayListener.waitForOnDisplayRemovedCallback(/*numDisplays=*/5)).isTrue();
        assertThat(mDisplayListener.getObservedRemovedDisplays()).containsExactlyElementsIn(
                getDisplayIds(displays));
    }

    @Test
    public void virtualDisplay_releasedWhenDeviceIsClosed() {
        mVirtualDevice =
                mVirtualDeviceManager.createVirtualDevice(
                        mFakeAssociationRule.getAssociationInfo().getId(),
                        DEFAULT_VIRTUAL_DEVICE_PARAMS);
        ArrayList<VirtualDisplay> displays = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            displays.add(mVirtualDevice.createVirtualDisplay(
                    DEFAULT_VIRTUAL_DISPLAY_CONFIG, Runnable::run, mVirtualDisplayCallback));
        }

        // Closing the virtual device should automatically release displays.
        mVirtualDevice.close();

        assertThat(mDisplayListener.waitForOnDisplayRemovedCallback(/*numDisplays=*/5)).isTrue();
        assertThat(mDisplayListener.getObservedRemovedDisplays()).containsExactlyElementsIn(
                getDisplayIds(displays));
        displays.forEach(display -> assertThat(display.getDisplay().isValid()).isFalse());
    }

    @Test
    public void virtualDisplay_releaseDisplaysAndCloseDevice() {
        mVirtualDevice =
                mVirtualDeviceManager.createVirtualDevice(
                        mFakeAssociationRule.getAssociationInfo().getId(),
                        DEFAULT_VIRTUAL_DEVICE_PARAMS);
        ArrayList<VirtualDisplay> displays = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            displays.add(mVirtualDevice.createVirtualDisplay(
                    DEFAULT_VIRTUAL_DISPLAY_CONFIG, Runnable::run, mVirtualDisplayCallback));
        }

        // Releasing and closing the device should result in each display being released only once.
        displays.forEach(VirtualDisplay::release);
        mVirtualDevice.close();

        assertThat(mDisplayListener.waitForOnDisplayRemovedCallback(/*numDisplays=*/5)).isTrue();
        assertThat(mDisplayListener.getObservedRemovedDisplays()).containsExactlyElementsIn(
                getDisplayIds(displays));
    }

    @Test
    public void getDeviceIdForDisplayId_returnsCorrectId() {
        mVirtualDevice =
                mVirtualDeviceManager.createVirtualDevice(
                        mFakeAssociationRule.getAssociationInfo().getId(),
                        DEFAULT_VIRTUAL_DEVICE_PARAMS);
        VirtualDisplay virtualDisplay = mVirtualDevice.createVirtualDisplay(
                DEFAULT_VIRTUAL_DISPLAY_CONFIG, Runnable::run, mVirtualDisplayCallback);

        assertThat(mVirtualDeviceManager.getDeviceIdForDisplayId(
                virtualDisplay.getDisplay().getDisplayId())).isEqualTo(
                mVirtualDevice.getDeviceId());
    }

    @Test
    public void getDeviceIdForDisplayId_returnsDefaultIdForReleasedDisplay() {
        mVirtualDevice =
                mVirtualDeviceManager.createVirtualDevice(
                        mFakeAssociationRule.getAssociationInfo().getId(),
                        DEFAULT_VIRTUAL_DEVICE_PARAMS);
        VirtualDisplay virtualDisplay = mVirtualDevice.createVirtualDisplay(
                DEFAULT_VIRTUAL_DISPLAY_CONFIG, Runnable::run, mVirtualDisplayCallback);
        virtualDisplay.release();
        assertThat(mDisplayListener.waitForOnDisplayRemovedCallback()).isTrue();

        // When virtual display is released, the callback from display manager service to VDM
        // is dispatched asynchronously and after display listener callbacks are dispatched.
        // Therefore, even receiving onDisplayRemoved callback from display manager doesn't
        // guarantee that the display removal was already processed by VDM.
        // To test the proper removal but avoid flakiness, the assertion below polls for result
        // until it returns DEVICE_ID_DEFAULT (meaning the  display was properly disposed of).
        // In case the display is not released within the polling timeout, the pollForResult returns
        // last value from getDeviceIdForDisplayId call and the test will fail.
        assertThat(pollForResult(() -> mVirtualDeviceManager.getDeviceIdForDisplayId(
                        virtualDisplay.getDisplay().getDisplayId()),
                Context.DEVICE_ID_DEFAULT)).isEqualTo(
                Context.DEVICE_ID_DEFAULT);
    }

    @Test
    public void createAndRelease_isInvalidForReleasedDisplay() {
        mVirtualDevice =
                mVirtualDeviceManager.createVirtualDevice(
                        mFakeAssociationRule.getAssociationInfo().getId(),
                        DEFAULT_VIRTUAL_DEVICE_PARAMS);
        VirtualDisplay virtualDisplay = mVirtualDevice.createVirtualDisplay(
                DEFAULT_VIRTUAL_DISPLAY_CONFIG, Runnable::run, mVirtualDisplayCallback);

        mVirtualDevice.close();
        assertThat(mDisplayListener.waitForOnDisplayRemovedCallback()).isTrue();

        // Check whether display associated with virtual device is valid.
        Display display = virtualDisplay.getDisplay();
        assertThat(display.isValid()).isFalse();
    }

    private static List<Integer> getDisplayIds(Collection<VirtualDisplay> displays) {
        return displays.stream().map((display) -> display.getDisplay().getDisplayId()).collect(
                Collectors.toList());
    }

    /**
     * Polls for result of supplier until the returned value equals expected
     * value, or the timeout expires.
     *
     * @param supplier       Supplier to poll from
     * @param expectedResult expected result, when this result is returned, polling stops.
     * @param <T>            return type of supplier
     * @return last return value from supplier.
     */
    private static <T> T pollForResult(Supplier<T> supplier, T expectedResult) {
        final long pollingStartedTime = System.currentTimeMillis();
        T lastResult = supplier.get();
        while (!Objects.equal(lastResult, expectedResult)
                && (System.currentTimeMillis() - pollingStartedTime < TIMEOUT_MILLIS)) {
            try {
                Thread.sleep(1);
            } catch (InterruptedException ie) {
                continue;
            }
            lastResult = supplier.get();
        }
        return lastResult;
    }
}
