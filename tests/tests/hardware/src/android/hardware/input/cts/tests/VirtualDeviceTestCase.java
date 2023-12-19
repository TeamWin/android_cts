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

package android.hardware.input.cts.tests;

import static android.Manifest.permission.ADD_TRUSTED_DISPLAY;
import static android.Manifest.permission.CREATE_VIRTUAL_DEVICE;

import static org.junit.Assert.fail;

import android.app.ActivityOptions;
import android.companion.virtual.VirtualDeviceManager;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.hardware.input.InputManager;
import android.hardware.input.cts.virtualcreators.VirtualDeviceCreator;
import android.hardware.input.cts.virtualcreators.VirtualDisplayCreator;
import android.hardware.input.cts.virtualcreators.VirtualInputDeviceCreator;
import android.os.Bundle;
import android.server.wm.WakeUpAndUnlockRule;
import android.server.wm.WindowManagerStateHelper;
import android.virtualdevice.cts.common.FakeAssociationRule;

import androidx.annotation.Nullable;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.AdoptShellPermissionsRule;

import org.junit.Rule;
import org.junit.rules.RuleChain;

public abstract class VirtualDeviceTestCase extends InputTestCase {

    @Rule
    public RuleChain chain = RuleChain.outerRule(new WakeUpAndUnlockRule())
            .around(new AdoptShellPermissionsRule(
                    InstrumentationRegistry.getInstrumentation().getUiAutomation(),
                    CREATE_VIRTUAL_DEVICE,
                    ADD_TRUSTED_DISPLAY));

    @Rule
    public FakeAssociationRule mFakeAssociationRule = new FakeAssociationRule();

    InputManager mInputManager;
    VirtualDeviceManager.VirtualDevice mVirtualDevice;
    VirtualDisplay mVirtualDisplay;

    /** Helper class to drop permissions temporarily and restore them at the end of a test. */
    static final class DropShellPermissionsTemporarily implements AutoCloseable {
        DropShellPermissionsTemporarily() {
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .dropShellPermissionIdentity();
        }

        @Override
        public void close() {
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .adoptShellPermissionIdentity(
                            CREATE_VIRTUAL_DEVICE,
                            ADD_TRUSTED_DISPLAY);
        }
    }

    @Override
    void onBeforeLaunchActivity() {
        mVirtualDevice = VirtualDeviceCreator.createVirtualDevice(
                mFakeAssociationRule.getAssociationInfo().getId());
        mVirtualDisplay = VirtualDisplayCreator.createVirtualDisplay(mVirtualDevice,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC
                    | DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY
                    | DisplayManager.VIRTUAL_DISPLAY_FLAG_TRUSTED);
        if (mVirtualDisplay == null) {
            fail("Could not create virtual display");
        }
    }

    @Override
    void onSetUp() {
        mInputManager = mInstrumentation.getTargetContext().getSystemService(InputManager.class);
        VirtualInputDeviceCreator.prepareInputDevice(mInputManager,
                this::onSetUpVirtualInputDevice);
        // Wait for any pending transitions
        WindowManagerStateHelper windowManagerStateHelper = new WindowManagerStateHelper();
        windowManagerStateHelper.waitForAppTransitionIdleOnDisplay(mTestActivity.getDisplayId());
        mInstrumentation.getUiAutomation().syncInputTransactions();
    }

    abstract void onSetUpVirtualInputDevice();

    abstract void onTearDownVirtualInputDevice();

    @Override
    void onTearDown() {
        try {
            onTearDownVirtualInputDevice();
        } finally {
            if (mTestActivity != null) {
                mTestActivity.finish();
            }
            if (mVirtualDisplay != null) {
                mVirtualDisplay.release();
            }
            if (mVirtualDevice != null) {
                mVirtualDevice.close();
            }
        }
    }

    @Override
    @Nullable
    Bundle getActivityOptions() {
        return ActivityOptions.makeBasic()
                .setLaunchDisplayId(mVirtualDisplay.getDisplay().getDisplayId())
                .toBundle();
    }

    protected void runWithPermission(Runnable runnable, String... permissions) {
        mInstrumentation.getUiAutomation().adoptShellPermissionIdentity(permissions);
        try {
            runnable.run();
        } finally {
            // Revert the permissions needed for the test again.
            mInstrumentation.getUiAutomation().adoptShellPermissionIdentity(
                    ADD_TRUSTED_DISPLAY, CREATE_VIRTUAL_DEVICE);
        }
    }
}
