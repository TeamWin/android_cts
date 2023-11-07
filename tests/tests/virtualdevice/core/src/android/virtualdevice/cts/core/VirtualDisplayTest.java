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
import static android.Manifest.permission.CAPTURE_VIDEO_OUTPUT;
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
import android.companion.virtual.flags.Flags;
import android.compat.testing.PlatformCompatChangeRule;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.hardware.display.VirtualDisplayConfig;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.RequiresFlagsDisabled;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.view.Display;
import android.virtualdevice.cts.common.FakeAssociationRule;
import android.virtualdevice.cts.common.util.VirtualDeviceTestUtils;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.AdoptShellPermissionsRule;

import com.google.common.base.Objects;

import libcore.junit.util.compat.CoreCompatChangeRule.DisableCompatChanges;
import libcore.junit.util.compat.CoreCompatChangeRule.EnableCompatChanges;

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
    private static final int TIMEOUT_MILLIS = 1000;

    private static final long MAKE_VIRTUAL_DISPLAY_FLAGS_CONSISTENT_WITH_DISPLAY_MANAGER =
            294837146L;

    private static final VirtualDeviceParams DEFAULT_VIRTUAL_DEVICE_PARAMS =
            new VirtualDeviceParams.Builder().build();
    private static final VirtualDisplayConfig DEFAULT_VIRTUAL_DISPLAY_CONFIG =
            createDefaultVirtualDisplayConfigBuilder().build();

    /** Helper class to drop permissions temporarily and restore them at the end of a test. */
    private static final class DropShellPermissionsTemporarily implements AutoCloseable {
        DropShellPermissionsTemporarily() {
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .dropShellPermissionIdentity();
        }

        @Override
        public void close() {
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .adoptShellPermissionIdentity(
                            ADD_ALWAYS_UNLOCKED_DISPLAY,
                            ADD_TRUSTED_DISPLAY,
                            CAPTURE_VIDEO_OUTPUT,
                            CREATE_VIRTUAL_DEVICE,
                            WAKE_LOCK);
        }
    }

    @Rule
    public AdoptShellPermissionsRule mAdoptShellPermissionsRule = new AdoptShellPermissionsRule(
            InstrumentationRegistry.getInstrumentation().getUiAutomation(),
            ADD_ALWAYS_UNLOCKED_DISPLAY,
            ADD_TRUSTED_DISPLAY,
            CAPTURE_VIDEO_OUTPUT,
            CREATE_VIRTUAL_DEVICE,
            WAKE_LOCK);

    @Rule
    public FakeAssociationRule mFakeAssociationRule = new FakeAssociationRule();

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Rule
    public PlatformCompatChangeRule mCompatChangeRule = new PlatformCompatChangeRule();

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
        mVirtualDevice = createVirtualDevice(DEFAULT_VIRTUAL_DEVICE_PARAMS);

        VirtualDisplay virtualDisplay = createVirtualDisplayForVirtualDevice();
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
        mVirtualDevice = createVirtualDevice(DEFAULT_VIRTUAL_DEVICE_PARAMS);

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

    @EnableCompatChanges({MAKE_VIRTUAL_DISPLAY_FLAGS_CONSISTENT_WITH_DISPLAY_MANAGER})
    @Test
    public void createVirtualDisplay_defaultVirtualDisplayFlags() {
        mVirtualDevice = createVirtualDevice(DEFAULT_VIRTUAL_DEVICE_PARAMS);

        VirtualDisplay virtualDisplay = createVirtualDisplayForVirtualDevice();
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

    @DisableCompatChanges({MAKE_VIRTUAL_DISPLAY_FLAGS_CONSISTENT_WITH_DISPLAY_MANAGER})
    @Test
    public void createVirtualDisplay_defaultVirtualDisplayFlags_compat() {
        mVirtualDevice =
                mVirtualDeviceManager.createVirtualDevice(
                        mFakeAssociationRule.getAssociationInfo().getId(),
                        DEFAULT_VIRTUAL_DEVICE_PARAMS);

        VirtualDisplay virtualDisplay = mVirtualDevice.createVirtualDisplay(
                DEFAULT_VIRTUAL_DISPLAY_CONFIG, Runnable::run, mVirtualDisplayCallback);
        assertThat(mDisplayListener.waitForOnDisplayAddedCallback()).isTrue();

        Display display = virtualDisplay.getDisplay();
        assertThat(display.isValid()).isTrue();
        assertThat(display.getFlags()).isEqualTo(Display.FLAG_ROTATES_WITH_CONTENT
                | Display.FLAG_TOUCH_FEEDBACK_DISABLED);
        assertThat(display.getRemoveMode()).isEqualTo(Display.REMOVE_MODE_DESTROY_CONTENT);
        assertThat(mDisplayListener.getObservedAddedDisplays()).containsExactly(
                display.getDisplayId());
    }

    /**
     * Tests that a virtual device is not allowed create a virtual display with just
     * VIRTUAL_DISPLAY_FLAG_PUBLIC flag if screen mirroring is disabled, as DisplayManagerService
     * tries to create an auto-mirror display by default for public virtual displays.
     */
    @RequiresFlagsDisabled(Flags.FLAG_INTERACTIVE_SCREEN_MIRROR)
    @EnableCompatChanges({MAKE_VIRTUAL_DISPLAY_FLAGS_CONSISTENT_WITH_DISPLAY_MANAGER})
    @Test
    public void createVirtualDisplay_public_throwsException() {
        mVirtualDevice = createVirtualDevice(DEFAULT_VIRTUAL_DEVICE_PARAMS);

        // Try creating public display without CAPTURE_VIDEO_OUTPUT permission.
        try (DropShellPermissionsTemporarily ignored = new DropShellPermissionsTemporarily()) {
            assertThrows(SecurityException.class,
                    () -> createVirtualDisplayForVirtualDevice(
                            DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC));
        }
    }

    /**
     * Tests that a virtual device is not allowed create a virtual display with
     * VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR flag if screen mirroring is disabled.
     */
    @RequiresFlagsDisabled(Flags.FLAG_INTERACTIVE_SCREEN_MIRROR)
    @EnableCompatChanges({MAKE_VIRTUAL_DISPLAY_FLAGS_CONSISTENT_WITH_DISPLAY_MANAGER})
    @Test
    public void createVirtualDisplay_autoMirror_throwsException() {
        mVirtualDevice = createVirtualDevice(DEFAULT_VIRTUAL_DEVICE_PARAMS);

        // Try creating auto-mirror display without CAPTURE_VIDEO_OUTPUT permission.
        try (DropShellPermissionsTemporarily ignored = new DropShellPermissionsTemporarily()) {
            assertThrows(SecurityException.class,
                    () -> createVirtualDisplayForVirtualDevice(
                            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR));
        }
    }

    /**
     * Tests that a virtual device is not allowed create a virtual display with
     * VIRTUAL_DISPLAY_FLAG_PUBLIC or VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR flags if screen mirroring is
     * disabled.
     */
    @RequiresFlagsDisabled(Flags.FLAG_INTERACTIVE_SCREEN_MIRROR)
    @EnableCompatChanges({MAKE_VIRTUAL_DISPLAY_FLAGS_CONSISTENT_WITH_DISPLAY_MANAGER})
    @Test
    public void createVirtualDisplay_publicAutoMirror_throwsException() {
        mVirtualDevice = createVirtualDevice(DEFAULT_VIRTUAL_DEVICE_PARAMS);

        // Try creating public auto-mirror display without CAPTURE_VIDEO_OUTPUT permission.
        try (DropShellPermissionsTemporarily ignored = new DropShellPermissionsTemporarily()) {
            assertThrows(SecurityException.class,
                    () -> createVirtualDisplayForVirtualDevice(
                            DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC
                                    | DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR));
        }
    }

    @RequiresFlagsEnabled(Flags.FLAG_INTERACTIVE_SCREEN_MIRROR)
    @EnableCompatChanges({MAKE_VIRTUAL_DISPLAY_FLAGS_CONSISTENT_WITH_DISPLAY_MANAGER})
    @Test
    public void createVirtualDisplay_public_createsMirrorDisplay() {
        mVirtualDevice = createVirtualDevice(DEFAULT_VIRTUAL_DEVICE_PARAMS);

        VirtualDisplay virtualDisplay = null;
        // Try creating public display without CAPTURE_VIDEO_OUTPUT permission.
        try (DropShellPermissionsTemporarily ignored = new DropShellPermissionsTemporarily()) {
            virtualDisplay = createVirtualDisplayForVirtualDevice(
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC);
        }

        assertThat(virtualDisplay).isNotNull();
        Display display = virtualDisplay.getDisplay();
        assertThat(display.isValid()).isTrue();
        assertThat(mVirtualDeviceManager.isVirtualDeviceOwnedMirrorDisplay(display.getDisplayId()))
                .isTrue();
    }

    @RequiresFlagsEnabled(Flags.FLAG_INTERACTIVE_SCREEN_MIRROR)
    @EnableCompatChanges({MAKE_VIRTUAL_DISPLAY_FLAGS_CONSISTENT_WITH_DISPLAY_MANAGER})
    @Test
    public void createVirtualDisplay_autoMirror_createsMirrorDisplay() {
        mVirtualDevice = createVirtualDevice(DEFAULT_VIRTUAL_DEVICE_PARAMS);

        VirtualDisplay virtualDisplay = null;
        // Try creating auto-mirror display without CAPTURE_VIDEO_OUTPUT permission.
        try (DropShellPermissionsTemporarily ignored = new DropShellPermissionsTemporarily()) {
            virtualDisplay = createVirtualDisplayForVirtualDevice(
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR);
        }

        assertThat(virtualDisplay).isNotNull();
        Display display = virtualDisplay.getDisplay();
        assertThat(display.isValid()).isTrue();
        assertThat(mVirtualDeviceManager.isVirtualDeviceOwnedMirrorDisplay(display.getDisplayId()))
                .isTrue();
    }

    @RequiresFlagsEnabled(Flags.FLAG_INTERACTIVE_SCREEN_MIRROR)
    @EnableCompatChanges({MAKE_VIRTUAL_DISPLAY_FLAGS_CONSISTENT_WITH_DISPLAY_MANAGER})
    @Test
    public void createVirtualDisplay_publicAutoMirror_createsMirrorDisplay() {
        mVirtualDevice = createVirtualDevice(DEFAULT_VIRTUAL_DEVICE_PARAMS);

        VirtualDisplay virtualDisplay = null;
        // Try creating public auto-mirror display without CAPTURE_VIDEO_OUTPUT permission.
        try (DropShellPermissionsTemporarily ignored = new DropShellPermissionsTemporarily()) {
            virtualDisplay = createVirtualDisplayForVirtualDevice(
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC
                            | DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR);
        }

        assertThat(virtualDisplay).isNotNull();
        Display display = virtualDisplay.getDisplay();
        assertThat(display.isValid()).isTrue();
        assertThat(mVirtualDeviceManager.isVirtualDeviceOwnedMirrorDisplay(display.getDisplayId()))
                .isTrue();
    }

    @RequiresFlagsEnabled(Flags.FLAG_INTERACTIVE_SCREEN_MIRROR)
    @EnableCompatChanges({MAKE_VIRTUAL_DISPLAY_FLAGS_CONSISTENT_WITH_DISPLAY_MANAGER})
    @Test
    public void createVirtualDisplay_ownContentOnly_doesNotCreateMirrorDisplay() {
        VirtualDisplay virtualDisplay = createVirtualDeviceAndDisplay(
                DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY,
                DEFAULT_VIRTUAL_DEVICE_PARAMS);

        assertThat(virtualDisplay).isNotNull();
        Display display = virtualDisplay.getDisplay();
        assertThat(display.isValid()).isTrue();
        assertThat(mVirtualDeviceManager.isVirtualDeviceOwnedMirrorDisplay(display.getDisplayId()))
                .isFalse();
    }

    @RequiresFlagsEnabled(Flags.FLAG_INTERACTIVE_SCREEN_MIRROR)
    @EnableCompatChanges({MAKE_VIRTUAL_DISPLAY_FLAGS_CONSISTENT_WITH_DISPLAY_MANAGER})
    @Test
    public void createVirtualDisplay_autoMirrorAndOwnContentOnly_doesNotCreateMirrorDisplay() {
        VirtualDisplay virtualDisplay = createVirtualDeviceAndDisplay(
                DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY
                        | DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                DEFAULT_VIRTUAL_DEVICE_PARAMS);

        assertThat(virtualDisplay).isNotNull();
        Display display = virtualDisplay.getDisplay();
        assertThat(display.isValid()).isTrue();
        assertThat(mVirtualDeviceManager.isVirtualDeviceOwnedMirrorDisplay(display.getDisplayId()))
                .isFalse();
    }

    @RequiresFlagsEnabled(Flags.FLAG_INTERACTIVE_SCREEN_MIRROR)
    @EnableCompatChanges({MAKE_VIRTUAL_DISPLAY_FLAGS_CONSISTENT_WITH_DISPLAY_MANAGER})
    @Test
    public void createVirtualDisplay_autoMirror_flagAlwaysUnlockedNotSet() {
        VirtualDeviceParams params = new VirtualDeviceParams.Builder()
                .setLockState(VirtualDeviceParams.LOCK_STATE_ALWAYS_UNLOCKED)
                .build();
        VirtualDisplay virtualDisplay = createVirtualDeviceAndDisplay(
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, params);

        assertThat(virtualDisplay).isNotNull();
        Display display = virtualDisplay.getDisplay();
        assertThat(display.getFlags() & Display.FLAG_ALWAYS_UNLOCKED).isEqualTo(0);
    }

    @RequiresFlagsEnabled(Flags.FLAG_INTERACTIVE_SCREEN_MIRROR)
    @EnableCompatChanges({MAKE_VIRTUAL_DISPLAY_FLAGS_CONSISTENT_WITH_DISPLAY_MANAGER})
    @Test
    public void createVirtualDisplay_public_flagAlwaysUnlockedNotSet() {
        VirtualDeviceParams params = new VirtualDeviceParams.Builder()
                .setLockState(VirtualDeviceParams.LOCK_STATE_ALWAYS_UNLOCKED)
                .build();
        VirtualDisplay virtualDisplay = createVirtualDeviceAndDisplay(
                DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC, params);

        assertThat(virtualDisplay).isNotNull();
        Display display = virtualDisplay.getDisplay();
        assertThat(display.getFlags() & Display.FLAG_ALWAYS_UNLOCKED).isEqualTo(0);
    }

    @RequiresFlagsEnabled(Flags.FLAG_INTERACTIVE_SCREEN_MIRROR)
    @EnableCompatChanges({MAKE_VIRTUAL_DISPLAY_FLAGS_CONSISTENT_WITH_DISPLAY_MANAGER})
    @Test
    public void createVirtualDisplay_autoMirror_flagPresentationNotSet() {
        VirtualDisplay virtualDisplay = createVirtualDeviceAndDisplay(
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR
                        | DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION,
                DEFAULT_VIRTUAL_DEVICE_PARAMS);

        assertThat(virtualDisplay).isNotNull();
        Display display = virtualDisplay.getDisplay();
        assertThat(display.getFlags() & Display.FLAG_PRESENTATION).isEqualTo(0);
    }

    @RequiresFlagsEnabled(Flags.FLAG_INTERACTIVE_SCREEN_MIRROR)
    @EnableCompatChanges({MAKE_VIRTUAL_DISPLAY_FLAGS_CONSISTENT_WITH_DISPLAY_MANAGER})
    @Test
    public void createVirtualDisplay_public_flagPresentationNotSet() {
        VirtualDisplay virtualDisplay = createVirtualDeviceAndDisplay(
                DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC
                        | DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION,
                DEFAULT_VIRTUAL_DEVICE_PARAMS);

        assertThat(virtualDisplay).isNotNull();
        Display display = virtualDisplay.getDisplay();
        assertThat(display.getFlags() & Display.FLAG_PRESENTATION).isEqualTo(0);
    }

    @RequiresFlagsEnabled(Flags.FLAG_INTERACTIVE_SCREEN_MIRROR)
    @Test
    public void isVirtualDeviceOwnedMirrorDisplay_invalidDisplay_returnsFalse() {
        assertThat(mVirtualDeviceManager.isVirtualDeviceOwnedMirrorDisplay(Display.INVALID_DISPLAY))
                .isFalse();
    }

    @RequiresFlagsEnabled(Flags.FLAG_INTERACTIVE_SCREEN_MIRROR)
    @Test
    public void isVirtualDeviceOwnedMirrorDisplay_defaultDisplay_returnsFalse() {
        assertThat(mVirtualDeviceManager.isVirtualDeviceOwnedMirrorDisplay(Display.DEFAULT_DISPLAY))
                .isFalse();
    }

    @RequiresFlagsEnabled(Flags.FLAG_INTERACTIVE_SCREEN_MIRROR)
    @Test
    public void isVirtualDeviceOwnedMirrorDisplay_unownedAutoMirrorDisplay_returnsFalse() {
        VirtualDisplay virtualDisplay = createUnownedVirtualDisplay(
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR);

        Display display = virtualDisplay.getDisplay();
        assertThat(mVirtualDeviceManager.isVirtualDeviceOwnedMirrorDisplay(display.getDisplayId()))
                .isFalse();
    }

    @RequiresFlagsEnabled(Flags.FLAG_INTERACTIVE_SCREEN_MIRROR)
    @Test
    public void isVirtualDeviceOwnedMirrorDisplay_unownedPublicDisplay_returnsFalse() {
        VirtualDisplay virtualDisplay = createUnownedVirtualDisplay(
                DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC);

        Display display = virtualDisplay.getDisplay();
        assertThat(mVirtualDeviceManager.isVirtualDeviceOwnedMirrorDisplay(display.getDisplayId()))
                .isFalse();
    }

    @RequiresFlagsEnabled(Flags.FLAG_INTERACTIVE_SCREEN_MIRROR)
    @Test
    public void isVirtualDeviceOwnedMirrorDisplay_unownedPublicAutoMirrorDisplay_returnsFalse() {
        VirtualDisplay virtualDisplay = createUnownedVirtualDisplay(
                DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC
                        | DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR);

        Display display = virtualDisplay.getDisplay();
        assertThat(mVirtualDeviceManager.isVirtualDeviceOwnedMirrorDisplay(display.getDisplayId()))
                .isFalse();
    }

    @RequiresFlagsEnabled(Flags.FLAG_INTERACTIVE_SCREEN_MIRROR)
    @Test
    public void isVirtualDeviceOwnedMirrorDisplay_unownedDisplay_returnsFalse() {
        VirtualDisplay virtualDisplay = createUnownedVirtualDisplay();

        Display display = virtualDisplay.getDisplay();
        assertThat(mVirtualDeviceManager.isVirtualDeviceOwnedMirrorDisplay(display.getDisplayId()))
                .isFalse();
    }

    @Test
    public void createVirtualDisplay_trustedDisplay_shouldSpecifyOwnFocusFlag() {
        mVirtualDevice = createVirtualDevice(
                new VirtualDeviceParams.Builder()
                    .setDevicePolicy(VirtualDeviceParams.POLICY_TYPE_RECENTS,
                            VirtualDeviceParams.DEVICE_POLICY_CUSTOM)
                    .build());

        VirtualDisplay virtualDisplay = createVirtualDisplayForVirtualDevice(
                DisplayManager.VIRTUAL_DISPLAY_FLAG_TRUSTED);
        assertThat(mDisplayListener.waitForOnDisplayAddedCallback()).isTrue();

        assertThat(virtualDisplay).isNotNull();
        Display display = virtualDisplay.getDisplay();
        assertThat(display.isValid()).isTrue();
        int displayFlags = display.getFlags();
        assertWithMessage(
                String.format(
                        "Virtual display flags (0x%x) should contain FLAG_TRUSTED",
                        displayFlags))
                .that(displayFlags & Display.FLAG_TRUSTED)
                .isEqualTo(Display.FLAG_TRUSTED);
        assertWithMessage(
                String.format(
                        "Virtual display flags (0x%x) should contain FLAG_OWN_FOCUS",
                        displayFlags))
                .that(displayFlags & Display.FLAG_OWN_FOCUS)
                .isEqualTo(Display.FLAG_OWN_FOCUS);
        assertThat(mDisplayListener.getObservedAddedDisplays()).containsExactly(
                display.getDisplayId());
    }

    @Test
    public void createVirtualDisplay_alwaysUnlocked_shouldSpecifyAlwaysUnlockedFlag() {
        mVirtualDevice = createVirtualDevice(
                new VirtualDeviceParams.Builder()
                        .setLockState(VirtualDeviceParams.LOCK_STATE_ALWAYS_UNLOCKED)
                        .build());

        VirtualDisplay virtualDisplay = createVirtualDisplayForVirtualDevice();
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
                .isEqualTo(Display.FLAG_ALWAYS_UNLOCKED);
        assertThat(mDisplayListener.getObservedAddedDisplays()).containsExactly(
                display.getDisplayId());
    }

    @Test
    public void createVirtualDisplay_nullExecutorAndCallback_shouldSucceed() {
        mVirtualDevice = createVirtualDevice(DEFAULT_VIRTUAL_DEVICE_PARAMS);

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
        mVirtualDevice = createVirtualDevice(DEFAULT_VIRTUAL_DEVICE_PARAMS);

        assertThrows(NullPointerException.class,
                () -> mVirtualDevice.createVirtualDisplay(
                        DEFAULT_VIRTUAL_DISPLAY_CONFIG, /*executor=*/ null,
                        mVirtualDisplayCallback));
    }

    @Test
    public void virtualDisplay_createAndRemoveSeveralDisplays() {
        mVirtualDevice = createVirtualDevice(DEFAULT_VIRTUAL_DEVICE_PARAMS);
        ArrayList<VirtualDisplay> displays = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            displays.add(createVirtualDisplayForVirtualDevice());
        }

        // Releasing several displays in quick succession should not cause deadlock
        displays.forEach(VirtualDisplay::release);

        assertThat(mDisplayListener.waitForOnDisplayRemovedCallback(/*numDisplays=*/5)).isTrue();
        assertThat(mDisplayListener.getObservedRemovedDisplays()).containsExactlyElementsIn(
                getDisplayIds(displays));
    }

    @Test
    public void virtualDisplay_releasedWhenDeviceIsClosed() {
        mVirtualDevice = createVirtualDevice(DEFAULT_VIRTUAL_DEVICE_PARAMS);
        ArrayList<VirtualDisplay> displays = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            displays.add(createVirtualDisplayForVirtualDevice());
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
        mVirtualDevice = createVirtualDevice(DEFAULT_VIRTUAL_DEVICE_PARAMS);
        ArrayList<VirtualDisplay> displays = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            displays.add(createVirtualDisplayForVirtualDevice());
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
        mVirtualDevice = createVirtualDevice(DEFAULT_VIRTUAL_DEVICE_PARAMS);
        VirtualDisplay virtualDisplay = createVirtualDisplayForVirtualDevice();

        assertThat(mVirtualDeviceManager.getDeviceIdForDisplayId(
                virtualDisplay.getDisplay().getDisplayId())).isEqualTo(
                mVirtualDevice.getDeviceId());
    }

    @Test
    public void getDeviceIdForDisplayId_returnsDefaultIdForReleasedDisplay() {
        mVirtualDevice = createVirtualDevice(DEFAULT_VIRTUAL_DEVICE_PARAMS);
        VirtualDisplay virtualDisplay = createVirtualDisplayForVirtualDevice();
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
        mVirtualDevice = createVirtualDevice(DEFAULT_VIRTUAL_DEVICE_PARAMS);
        VirtualDisplay virtualDisplay = createVirtualDisplayForVirtualDevice();

        mVirtualDevice.close();
        assertThat(mDisplayListener.waitForOnDisplayRemovedCallback()).isTrue();

        // Check whether display associated with virtual device is valid.
        Display display = virtualDisplay.getDisplay();
        assertThat(display.isValid()).isFalse();
    }

    private VirtualDisplay createVirtualDeviceAndDisplay(int displayFlags,
            VirtualDeviceParams deviceParams) {
        mVirtualDevice = createVirtualDevice(deviceParams);
        return createVirtualDisplayForVirtualDevice(displayFlags);
    }

    private VirtualDevice createVirtualDevice(VirtualDeviceParams deviceParams) {
        return mVirtualDeviceManager.createVirtualDevice(
                mFakeAssociationRule.getAssociationInfo().getId(),
                deviceParams);
    }

    private VirtualDisplay createVirtualDisplayForVirtualDevice() {
        return mVirtualDevice.createVirtualDisplay(DEFAULT_VIRTUAL_DISPLAY_CONFIG, Runnable::run,
                mVirtualDisplayCallback);
    }

    private VirtualDisplay createVirtualDisplayForVirtualDevice(int displayFlags) {
        VirtualDisplayConfig config = createDefaultVirtualDisplayConfigBuilder()
                .setFlags(displayFlags)
                .build();
        return mVirtualDevice.createVirtualDisplay(config, Runnable::run, mVirtualDisplayCallback);
    }

    private VirtualDisplay createUnownedVirtualDisplay() {
        VirtualDisplayConfig config = createDefaultVirtualDisplayConfigBuilder().build();
        return mDisplayManager.createVirtualDisplay(config);
    }

    private VirtualDisplay createUnownedVirtualDisplay(int displayFlags) {
        VirtualDisplayConfig config = createDefaultVirtualDisplayConfigBuilder()
                .setFlags(displayFlags)
                .build();
        return mDisplayManager.createVirtualDisplay(config);
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
