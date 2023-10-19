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

import static android.Manifest.permission.CREATE_VIRTUAL_DEVICE;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeNotNull;
import static org.junit.Assume.assumeTrue;

import android.annotation.Nullable;
import android.app.KeyguardManager;
import android.companion.virtual.VirtualDeviceManager;
import android.companion.virtual.VirtualDeviceParams;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.platform.test.annotations.AppModeFull;
import android.server.wm.LockScreenSession;
import android.server.wm.WindowManagerStateHelper;
import android.virtualdevice.cts.common.FakeAssociationRule;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.AdoptShellPermissionsRule;
import com.android.compatibility.common.util.FeatureUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockitoAnnotations;

/** Tests to verify that virtual device contexts always report insecure / unlocked device. */
@RunWith(AndroidJUnit4.class)
@AppModeFull(reason = "VirtualDeviceManager cannot be accessed by instant apps")
public class VirtualDeviceKeyguardTest {

    @Rule
    public AdoptShellPermissionsRule mAdoptShellPermissionsRule = new AdoptShellPermissionsRule(
            InstrumentationRegistry.getInstrumentation().getUiAutomation(),
            CREATE_VIRTUAL_DEVICE);

    @Rule
    public FakeAssociationRule mFakeAssociationRule = new FakeAssociationRule();

    @Nullable
    private VirtualDeviceManager.VirtualDevice mVirtualDevice;
    private KeyguardManager mDefaultDeviceKeyguardManager;
    private KeyguardManager mVirtualDeviceKeyguardManager;

    private final WindowManagerStateHelper mWmState = new WindowManagerStateHelper();

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        assumeTrue(FeatureUtil.hasSystemFeature(PackageManager.FEATURE_COMPANION_DEVICE_SETUP));

        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        mDefaultDeviceKeyguardManager = context.getSystemService(KeyguardManager.class);

        VirtualDeviceManager virtualDeviceManager =
                context.getSystemService(VirtualDeviceManager.class);
        assumeNotNull(virtualDeviceManager);

        mVirtualDevice = virtualDeviceManager.createVirtualDevice(
                mFakeAssociationRule.getAssociationInfo().getId(),
                new VirtualDeviceParams.Builder().build());
        Context deviceContext = InstrumentationRegistry.getInstrumentation().getContext()
                .createDeviceContext(mVirtualDevice.getDeviceId());
        mVirtualDeviceKeyguardManager = deviceContext.getSystemService(KeyguardManager.class);
    }

    @After
    public void tearDown() {
        if (mVirtualDevice != null) {
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .adoptShellPermissionIdentity(CREATE_VIRTUAL_DEVICE);
            mVirtualDevice.close();
        }
    }

    @Test
    public void deviceIsNotSecure() {
        assumeTrue(supportsLockScreen());

        try (LockScreenSession session = new LockScreenSession(
                InstrumentationRegistry.getInstrumentation(), mWmState)) {
            session.disableLockScreen();

            assertThat(mDefaultDeviceKeyguardManager.isDeviceSecure()).isFalse();
            assertThat(mDefaultDeviceKeyguardManager.isDeviceLocked()).isFalse();

            assertThat(mVirtualDeviceKeyguardManager.isDeviceSecure()).isFalse();
            assertThat(mVirtualDeviceKeyguardManager.isDeviceLocked()).isFalse();
        }
    }

    @Test
    public void deviceIsSecureAndLocked() {
        assumeTrue(supportsSecureLock());

        try (LockScreenSession session = new LockScreenSession(
                InstrumentationRegistry.getInstrumentation(), mWmState)) {
            session.setLockCredential().gotoKeyguard();

            assertThat(mDefaultDeviceKeyguardManager.isDeviceSecure()).isTrue();
            assertThat(mDefaultDeviceKeyguardManager.isDeviceLocked()).isTrue();

            assertThat(mVirtualDeviceKeyguardManager.isDeviceSecure()).isFalse();
            assertThat(mVirtualDeviceKeyguardManager.isDeviceLocked()).isFalse();
        }
    }

    @Test
    public void deviceIsSecureNotLocked() {
        assumeTrue(supportsSecureLock());

        try (LockScreenSession session = new LockScreenSession(
                InstrumentationRegistry.getInstrumentation(), mWmState)) {
            session.setLockCredential().gotoKeyguard();
            mWmState.assertKeyguardShowingAndNotOccluded();
            session.unlockDevice().enterAndConfirmLockCredential();
            mWmState.waitAndAssertKeyguardGone();

            assertThat(mDefaultDeviceKeyguardManager.isDeviceSecure()).isTrue();
            assertThat(mDefaultDeviceKeyguardManager.isDeviceLocked()).isFalse();

            assertThat(mVirtualDeviceKeyguardManager.isDeviceSecure()).isFalse();
            assertThat(mVirtualDeviceKeyguardManager.isDeviceLocked()).isFalse();
        }
    }

    protected boolean supportsLockScreen() {
        return supportsInsecureLock() || supportsSecureLock();
    }

    /** Whether or not the device supports pin/pattern/password lock. */
    protected boolean supportsSecureLock() {
        return FeatureUtil.hasSystemFeature(PackageManager.FEATURE_SECURE_LOCK_SCREEN);
    }

    /** Whether or not the device supports "swipe" lock. */
    protected boolean supportsInsecureLock() {
        return !FeatureUtil.hasAnySystemFeature(
                PackageManager.FEATURE_LEANBACK, PackageManager.FEATURE_WATCH,
                PackageManager.FEATURE_EMBEDDED, PackageManager.FEATURE_AUTOMOTIVE)
                && getSupportsInsecureLockScreen();
    }

    private boolean getSupportsInsecureLockScreen() {
        boolean insecure;
        try {
            insecure = InstrumentationRegistry.getInstrumentation().getContext().getResources()
                    .getBoolean(Resources.getSystem().getIdentifier(
                            "config_supportsInsecureLockScreen", "bool", "android"));
        } catch (Resources.NotFoundException e) {
            insecure = true;
        }
        return insecure;
    }
}
