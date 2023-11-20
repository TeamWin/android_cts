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

import static android.Manifest.permission.ADD_ALWAYS_UNLOCKED_DISPLAY;
import static android.Manifest.permission.ADD_TRUSTED_DISPLAY;
import static android.Manifest.permission.CREATE_VIRTUAL_DEVICE;
import static android.Manifest.permission.READ_CLIPBOARD_IN_BACKGROUND;
import static android.companion.virtual.VirtualDeviceParams.DEVICE_POLICY_CUSTOM;
import static android.companion.virtual.VirtualDeviceParams.DEVICE_POLICY_DEFAULT;
import static android.companion.virtual.VirtualDeviceParams.POLICY_TYPE_CLIPBOARD;
import static android.content.pm.PackageManager.FEATURE_FREEFORM_WINDOW_MANAGEMENT;
import static android.virtualdevice.cts.common.ClipboardTestConstants.ACTION_READ;
import static android.virtualdevice.cts.common.ClipboardTestConstants.ACTION_WRITE;
import static android.virtualdevice.cts.common.ClipboardTestConstants.EXTRA_DEVICE_ID;
import static android.virtualdevice.cts.common.ClipboardTestConstants.EXTRA_HAS_CLIP_DATA;
import static android.virtualdevice.cts.common.ClipboardTestConstants.EXTRA_RESULT_RECEIVER;
import static android.virtualdevice.cts.common.ClipboardTestConstants.EXTRA_CLIP_DATA;
import static android.virtualdevice.cts.common.util.VirtualDeviceTestUtils.createActivityOptions;
import static android.virtualdevice.cts.common.util.VirtualDeviceTestUtils.createDefaultVirtualDisplayConfigBuilder;
import static android.virtualdevice.cts.common.util.VirtualDeviceTestUtils.createResultReceiver;
import static android.virtualdevice.cts.common.util.VirtualDeviceTestUtils.isVirtualDeviceManagerConfigEnabled;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeNotNull;
import static org.junit.Assume.assumeTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import android.companion.virtual.VirtualDeviceManager;
import android.companion.virtual.VirtualDeviceManager.VirtualDevice;
import android.companion.virtual.VirtualDeviceParams;
import android.companion.virtual.flags.Flags;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.hardware.display.VirtualDisplayConfig;
import android.os.Bundle;
import android.os.ResultReceiver;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.server.wm.LockScreenSession;
import android.server.wm.TouchHelper;
import android.server.wm.WindowManagerStateHelper;
import android.view.Display;
import android.virtualdevice.cts.common.FakeAssociationRule;
import android.virtualdevice.cts.common.util.VirtualDeviceTestUtils;

import androidx.annotation.Nullable;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.AdoptShellPermissionsRule;
import com.android.compatibility.common.util.FeatureUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;


/**
 * Tests for clipboard access on virtual devices.
 */
@RunWith(AndroidJUnit4.class)
@AppModeFull(reason = "VirtualDeviceManager cannot be accessed by instant apps")
public class StreamedAppClipboardTest {

    private static final ClipData CLIP_DATA = ClipData.newPlainText("label", "Hello World");

    private static final int EVENT_TIMEOUT_MS = 5000;

    private static final VirtualDisplayConfig VIRTUAL_DISPLAY_CONFIG =
            createDefaultVirtualDisplayConfigBuilder()
                    .setFlags(DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC
                            | DisplayManager.VIRTUAL_DISPLAY_FLAG_TRUSTED
                            | DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY)
                    .build();

    @Rule
    public AdoptShellPermissionsRule mAdoptShellPermissionsRule = new AdoptShellPermissionsRule(
            InstrumentationRegistry.getInstrumentation().getUiAutomation(),
            ADD_ALWAYS_UNLOCKED_DISPLAY,
            ADD_TRUSTED_DISPLAY,
            CREATE_VIRTUAL_DEVICE,
            READ_CLIPBOARD_IN_BACKGROUND);

    @Rule
    public FakeAssociationRule mFakeAssociationRule = new FakeAssociationRule();

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private VirtualDeviceManager mVirtualDeviceManager;

    private final WindowManagerStateHelper mWmState = new WindowManagerStateHelper();
    private final TouchHelper mTouchHelper =
            new TouchHelper(InstrumentationRegistry.getInstrumentation(), mWmState);

    private final ArrayList<DeviceEnvironment> mDeviceEnvironments = new ArrayList<>();

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        final PackageManager packageManager = context.getPackageManager();
        assumeTrue(isVirtualDeviceManagerConfigEnabled(context));
        assumeTrue(packageManager.hasSystemFeature(
                PackageManager.FEATURE_ACTIVITIES_ON_SECONDARY_DISPLAYS));
        // TODO(b/261155110): Re-enable tests once freeform mode is supported in Virtual Display.
        assumeFalse("Skipping test: VirtualDisplay window policy doesn't support freeform.",
                packageManager.hasSystemFeature(FEATURE_FREEFORM_WINDOW_MANAGEMENT));

        mVirtualDeviceManager = context.getSystemService(VirtualDeviceManager.class);
        assumeNotNull(mVirtualDeviceManager);
    }

    @After
    public void tearDown() {
        for (int i = 0; i < mDeviceEnvironments.size(); ++i) {
            mDeviceEnvironments.get(i).close();
        }
    }

    /** The virtual device owner has access to its device's clipboard. */
    @Test
    public void deviceOwnerCanAccessClipboard() {
        DeviceEnvironment virtualDevice = new DeviceEnvironment(mVirtualDeviceManager);
        virtualDevice.mClipboardManager.setPrimaryClip(CLIP_DATA);

        virtualDevice.verifyClipChanged();
        assertThat(virtualDevice.mClipboardManager.hasPrimaryClip()).isTrue();
        verifyClipData(virtualDevice.mClipboardManager.getPrimaryClip());
    }

    /** Activities running on the virtual device can read that device's clipboard. */
    @Test
    public void streamedAppCanReadClipboard() {
        DeviceEnvironment virtualDevice = new DeviceEnvironment(mVirtualDeviceManager);
        virtualDevice.mClipboardManager.setPrimaryClip(CLIP_DATA);

        verifyClipData(virtualDevice.readClipboardFromActivity());
    }

    /** Activities running on the virtual device can write that device's clipboard. */
    @Test
    public void streamedAppCanWriteClipboard() {
        DeviceEnvironment virtualDevice = new DeviceEnvironment(mVirtualDeviceManager);
        virtualDevice.writeClipboardFromActivity();

        virtualDevice.verifyClipChanged();
        assertThat(virtualDevice.mClipboardManager.hasPrimaryClip()).isTrue();
        verifyClipData(virtualDevice.mClipboardManager.getPrimaryClip());
    }

    /**
     * Activities running on the virtual device can read that device's clipboard even when the
     * host device is locked.
     */
    @Test
    public void streamedAppCanReadClipboard_hostDeviceIsLocked() {
        assumeTrue(FeatureUtil.hasSystemFeature(PackageManager.FEATURE_SECURE_LOCK_SCREEN));
        DeviceEnvironment virtualDevice = new DeviceEnvironment(mVirtualDeviceManager);
        virtualDevice.mClipboardManager.setPrimaryClip(CLIP_DATA);

        try (LockScreenSession session = new LockScreenSession(
                InstrumentationRegistry.getInstrumentation(), mWmState)) {
            session.setLockCredential().gotoKeyguard();

            verifyClipData(virtualDevice.readClipboardFromActivity());
        }
    }

    /**
     * Activities running on the virtual device can write that device's clipboard even when the
     * host device is locked.
     */
    @Test
    public void streamedAppCanWriteClipboard_hostDeviceIsLocked() {
        assumeTrue(FeatureUtil.hasSystemFeature(PackageManager.FEATURE_SECURE_LOCK_SCREEN));
        DeviceEnvironment virtualDevice = new DeviceEnvironment(mVirtualDeviceManager);

        try (LockScreenSession session = new LockScreenSession(
                InstrumentationRegistry.getInstrumentation(), mWmState)) {
            session.setLockCredential().gotoKeyguard();

            virtualDevice.writeClipboardFromActivity();

            virtualDevice.verifyClipChanged();
            assertThat(virtualDevice.mClipboardManager.hasPrimaryClip()).isTrue();
            verifyClipData(virtualDevice.mClipboardManager.getPrimaryClip());
        }
    }

    /** Virtual and default device's clipboards are isolated. */
    @Test
    public void clipboardsAreIsolated() {
        DeviceEnvironment defaultDevice = new DeviceEnvironment();
        DeviceEnvironment virtualDevice = new DeviceEnvironment(mVirtualDeviceManager);

        defaultDevice.mClipboardManager.setPrimaryClip(CLIP_DATA);
        assertThat(virtualDevice.mClipboardManager.hasPrimaryClip()).isFalse();

        defaultDevice.mClipboardManager.clearPrimaryClip();

        virtualDevice.mClipboardManager.setPrimaryClip(CLIP_DATA);
        assertThat(defaultDevice.mClipboardManager.hasPrimaryClip()).isFalse();
    }

    /** Activities running on the virtual device cannot read default device's clipboard. */
    @Test
    public void streamedAppCanNotReadDefaultDeviceClipboard() {
        DeviceEnvironment defaultDevice = new DeviceEnvironment();
        defaultDevice.mClipboardManager.setPrimaryClip(CLIP_DATA);

        DeviceEnvironment virtualDevice = new DeviceEnvironment(mVirtualDeviceManager);

        assertThat(virtualDevice.readClipboardFromActivity(defaultDevice.mDeviceId)).isNull();
    }

    /** Activities running on the virtual device cannot write default device's clipboard. */
    @Test
    public void streamedAppCanNotWriteDefaultDeviceClipboard() {
        DeviceEnvironment defaultDevice = new DeviceEnvironment();
        DeviceEnvironment virtualDevice = new DeviceEnvironment(mVirtualDeviceManager);

        virtualDevice.writeClipboardFromActivity(defaultDevice.mDeviceId);

        assertThat(defaultDevice.mClipboardManager.hasPrimaryClip()).isFalse();
    }


    /** Virtual and default device's clipboards are the same if the policy is custom. */
    @RequiresFlagsEnabled(Flags.FLAG_CROSS_DEVICE_CLIPBOARD)
    @Test
    public void customPolicy_clipboardsAreShared() {
        DeviceEnvironment defaultDevice = new DeviceEnvironment();
        DeviceEnvironment virtualDevice =
                new DeviceEnvironment(mVirtualDeviceManager, DEVICE_POLICY_CUSTOM);

        defaultDevice.mClipboardManager.setPrimaryClip(CLIP_DATA);
        assertThat(virtualDevice.mClipboardManager.hasPrimaryClip()).isTrue();
        verifyClipData(virtualDevice.mClipboardManager.getPrimaryClip());

        defaultDevice.mClipboardManager.clearPrimaryClip();
        assertThat(virtualDevice.mClipboardManager.hasPrimaryClip()).isFalse();

        virtualDevice.mClipboardManager.setPrimaryClip(CLIP_DATA);
        assertThat(defaultDevice.mClipboardManager.hasPrimaryClip()).isTrue();
        verifyClipData(defaultDevice.mClipboardManager.getPrimaryClip());

        virtualDevice.mClipboardManager.clearPrimaryClip();
        assertThat(defaultDevice.mClipboardManager.hasPrimaryClip()).isFalse();
    }

    /**
     * Activities running on the virtual device can read default device's clipboard if the virtual
     * device clipboard policy is custom.
     */
    @RequiresFlagsEnabled(Flags.FLAG_CROSS_DEVICE_CLIPBOARD)
    @Test
    public void customPolicy_streamedAppReadsFromDefaultDeviceClipboard() {
        DeviceEnvironment defaultDevice = new DeviceEnvironment();
        DeviceEnvironment virtualDevice =
                new DeviceEnvironment(mVirtualDeviceManager, DEVICE_POLICY_CUSTOM);

        defaultDevice.mClipboardManager.setPrimaryClip(CLIP_DATA);

        verifyClipData(virtualDevice.readClipboardFromActivity(virtualDevice.mDeviceId));
    }

    /**
     * Activities running on the virtual device can write default device's clipboard if the virtual
     * device clipboard policy is custom.
     */
    @RequiresFlagsEnabled(Flags.FLAG_CROSS_DEVICE_CLIPBOARD)
    @Test
    public void customPolicy_streamedAppWritesToDefaultDeviceClipboard() {
        DeviceEnvironment defaultDevice = new DeviceEnvironment();
        DeviceEnvironment virtualDevice =
                new DeviceEnvironment(mVirtualDeviceManager, DEVICE_POLICY_CUSTOM);

        virtualDevice.writeClipboardFromActivity(virtualDevice.mDeviceId);

        defaultDevice.verifyClipChanged();
        assertThat(defaultDevice.mClipboardManager.hasPrimaryClip()).isTrue();
        verifyClipData(defaultDevice.mClipboardManager.getPrimaryClip());
    }

    /** Activities running on the virtual device cannot read another virtual device's clipboard. */
    @Test
    public void streamedAppCanNotReadAnotherVirtualDeviceClipboard() {
        DeviceEnvironment virtualDevice = new DeviceEnvironment(mVirtualDeviceManager);
        DeviceEnvironment anotherVirtualDevice = new DeviceEnvironment(mVirtualDeviceManager);
        anotherVirtualDevice.mClipboardManager.setPrimaryClip(CLIP_DATA);

        assertThat(virtualDevice.readClipboardFromActivity(anotherVirtualDevice.mDeviceId))
                .isNull();
    }

    /** Activities running on the virtual device cannot write another virtual device's clipboard. */
    @Test
    public void streamedAppCanNotWriteAnotherVirtualDeviceClipboard() {
        DeviceEnvironment virtualDevice = new DeviceEnvironment(mVirtualDeviceManager);
        DeviceEnvironment anotherVirtualDevice = new DeviceEnvironment(mVirtualDeviceManager);

        virtualDevice.writeClipboardFromActivity(anotherVirtualDevice.mDeviceId);

        assertThat(anotherVirtualDevice.mClipboardManager.hasPrimaryClip()).isFalse();
    }

    /** Activities running on the default device cannot read virtual device's clipboard. */
    @Test
    public void defaultDeviceAppCanNotReadVirtualDeviceClipboard() {
        DeviceEnvironment defaultDevice = new DeviceEnvironment();
        DeviceEnvironment virtualDevice = new DeviceEnvironment(mVirtualDeviceManager);
        virtualDevice.mClipboardManager.setPrimaryClip(CLIP_DATA);

        assertThat(defaultDevice.readClipboardFromActivity(virtualDevice.mDeviceId)).isNull();
    }

    /** Activities running on the default device cannot write virtual device's clipboard. */
    @Test
    public void defaultDeviceAppCanNotWriteVirtualDeviceClipboard() {
        DeviceEnvironment defaultDevice = new DeviceEnvironment();
        DeviceEnvironment virtualDevice = new DeviceEnvironment(mVirtualDeviceManager);

        defaultDevice.writeClipboardFromActivity(virtualDevice.mDeviceId);

        assertThat(virtualDevice.mClipboardManager.hasPrimaryClip()).isFalse();
    }

    /**
     * Activities running on the default device can read virtual device's clipboard if the virtual
     * device clipboard policy is custom.
     */
    @RequiresFlagsEnabled(Flags.FLAG_CROSS_DEVICE_CLIPBOARD)
    @Test
    public void customPolicy_defaultDeviceAppCanReadVirtualDeviceClipboard() {
        DeviceEnvironment defaultDevice = new DeviceEnvironment();
        DeviceEnvironment virtualDevice =
                new DeviceEnvironment(mVirtualDeviceManager, DEVICE_POLICY_CUSTOM);

        virtualDevice.mClipboardManager.setPrimaryClip(CLIP_DATA);

        verifyClipData(defaultDevice.readClipboardFromActivity(virtualDevice.mDeviceId));
    }

    /**
     * Activities running on the default device can write virtual device's clipboard if the virtual
     * device clipboard policy is custom.
     */
    @RequiresFlagsEnabled(Flags.FLAG_CROSS_DEVICE_CLIPBOARD)
    @Test
    public void customPolicy_defaultDeviceAppCanWriteVirtualDeviceClipboard() {
        DeviceEnvironment defaultDevice = new DeviceEnvironment();
        DeviceEnvironment virtualDevice =
                new DeviceEnvironment(mVirtualDeviceManager, DEVICE_POLICY_CUSTOM);

        defaultDevice.writeClipboardFromActivity(virtualDevice.mDeviceId);

        virtualDevice.verifyClipChanged();
        assertThat(virtualDevice.mClipboardManager.hasPrimaryClip()).isTrue();
        verifyClipData(virtualDevice.mClipboardManager.getPrimaryClip());
    }

    private void verifyClipData(ClipData actual) {
        assertThat(actual).isNotNull();
        assertThat(actual.getItemCount()).isEqualTo(1);
        assertThat(actual.getDescription().getLabel().toString()).isEqualTo(
                CLIP_DATA.getDescription().getLabel().toString());
        assertThat(actual.getItemAt(0).getText().toString())
                .isEqualTo(CLIP_DATA.getItemAt(0).getText().toString());
    }

    /**
     * Helper wrapper of the test environment for a single device, default or virtual.
     */
    private final class DeviceEnvironment {

        private static final ComponentName CLIPBOARD_TEST_ACTIVITY =
                new ComponentName("android.virtualdevice.streamedtestapp",
                        "android.virtualdevice.streamedtestapp.ClipboardTestActivity");
        private final Context mContext;
        private ClipboardManager mClipboardManager;
        @Nullable
        private VirtualDevice mVirtualDevice;
        private final int mDisplayId;
        private final int mDeviceId;
        @Mock
        private ClipboardManager.OnPrimaryClipChangedListener mOnPrimaryClipChangedListener;
        @Mock
        private VirtualDeviceManager.ActivityListener mActivityListener;
        @Mock
        private VirtualDeviceTestUtils.OnReceiveResultListener mOnReceiveResultListener;
        private ResultReceiver mResultReceiver;

        DeviceEnvironment() {
            MockitoAnnotations.initMocks(this);
            mContext = getApplicationContext();
            mDisplayId = Display.DEFAULT_DISPLAY;
            mDeviceId = Context.DEVICE_ID_DEFAULT;
            initialize();
        }

        DeviceEnvironment(VirtualDeviceManager virtualDeviceManager) {
            this(virtualDeviceManager, DEVICE_POLICY_DEFAULT);
        }

        DeviceEnvironment(VirtualDeviceManager virtualDeviceManager,
                @VirtualDeviceParams.DevicePolicy int clipboardPolicy) {
            MockitoAnnotations.initMocks(this);
            mVirtualDevice = virtualDeviceManager.createVirtualDevice(
                    mFakeAssociationRule.getAssociationInfo().getId(),
                    new VirtualDeviceParams.Builder()
                            .setLockState(VirtualDeviceParams.LOCK_STATE_ALWAYS_UNLOCKED)
                            .setDevicePolicy(POLICY_TYPE_CLIPBOARD, clipboardPolicy)
                            .build());
            VirtualDisplay virtualDisplay = mVirtualDevice.createVirtualDisplay(
                    VIRTUAL_DISPLAY_CONFIG, null, null);
            mDisplayId = virtualDisplay.getDisplay().getDisplayId();
            mDeviceId = mVirtualDevice.getDeviceId();
            mContext = getApplicationContext().createDeviceContext(mDeviceId);
            mVirtualDevice.addActivityListener(mContext.getMainExecutor(), mActivityListener);
            mWmState.waitForWithAmState(state -> state.getDisplay(mDisplayId) != null,
                    "Waiting for new display to be created");
            initialize();
        }

        private void initialize() {
            mDeviceEnvironments.add(this);
            mClipboardManager = mContext.getSystemService(ClipboardManager.class);
            mClipboardManager.clearPrimaryClip();
            assertThat(mClipboardManager.hasPrimaryClip()).isFalse();
            mClipboardManager.addPrimaryClipChangedListener(mOnPrimaryClipChangedListener);
            mResultReceiver = createResultReceiver(mOnReceiveResultListener);
        }

        private void close() {
            mWmState.waitForActivityRemoved(CLIPBOARD_TEST_ACTIVITY);
            mClipboardManager.removePrimaryClipChangedListener(mOnPrimaryClipChangedListener);
            if (mVirtualDevice != null) {
                mVirtualDevice.close();
            }
        }

        public ClipData readClipboardFromActivity() {
            return readClipboardFromActivity(mDeviceId);
        }

        public ClipData readClipboardFromActivity(int deviceId) {
            launchTestActivity(ACTION_READ, deviceId);
            mTouchHelper.tapOnDisplayCenter(mDisplayId);

            ArgumentCaptor<Bundle> bundle = ArgumentCaptor.forClass(Bundle.class);
            verify(mOnReceiveResultListener, timeout(EVENT_TIMEOUT_MS)).onReceiveResult(anyInt(),
                    bundle.capture());
            ClipData clipData = bundle.getValue().getParcelable(EXTRA_CLIP_DATA, ClipData.class);
            if (clipData == null) {
                assertThat(bundle.getValue().getBoolean(EXTRA_HAS_CLIP_DATA, true)).isFalse();
            } else {
                assertThat(bundle.getValue().getBoolean(EXTRA_HAS_CLIP_DATA, false)).isTrue();
            }
            return clipData;
        }

        public void writeClipboardFromActivity() {
            launchTestActivity(ACTION_WRITE, mDeviceId);
        }

        public void writeClipboardFromActivity(int deviceId) {
            launchTestActivity(ACTION_WRITE, deviceId);
        }

        private void launchTestActivity(String action, int deviceId) {
            final Intent intent = new Intent(action)
                    .setComponent(CLIPBOARD_TEST_ACTIVITY)
                    .putExtra(EXTRA_RESULT_RECEIVER, mResultReceiver)
                    .putExtra(EXTRA_DEVICE_ID, deviceId)
                    .putExtra(EXTRA_CLIP_DATA, CLIP_DATA)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

            mContext.startActivity(intent, createActivityOptions(mDisplayId));
            if (mVirtualDevice != null) {
                verify(mActivityListener, timeout(EVENT_TIMEOUT_MS).atLeastOnce())
                        .onTopActivityChanged(eq(mDisplayId), eq(CLIPBOARD_TEST_ACTIVITY),
                                eq(mContext.getUserId()));
            }
        }

        private void verifyClipChanged() {
            // We use atLeastOnce for onPrimaryClipChanged because it can fire more than once as
            // text classification results on the clipboard content become available.
            verify(mOnPrimaryClipChangedListener,
                    timeout(EVENT_TIMEOUT_MS).atLeastOnce()).onPrimaryClipChanged();
        }
    }
}
