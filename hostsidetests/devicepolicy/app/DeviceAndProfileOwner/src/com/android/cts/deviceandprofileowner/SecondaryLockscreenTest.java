/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.cts.deviceandprofileowner;

import static com.android.compatibility.common.util.TestUtils.waitUntil;
import static com.android.cts.deviceandprofileowner.BaseDeviceAdminTest.ADMIN_RECEIVER_COMPONENT;

import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;

import static org.junit.Assume.assumeTrue;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.PowerManager;
import android.os.Process;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.Until;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

@RunWith(AndroidJUnit4.class)
public class SecondaryLockscreenTest {

    private static final int UI_AUTOMATOR_WAIT_TIME_MILLIS = 10000;
    private static final String TAG = "SecondaryLockscreenTest";

    private Context mContext;
    private DevicePolicyManager mDevicePolicyManager;
    private UiDevice mUiDevice;

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getContext();
        mDevicePolicyManager = mContext.getSystemService(DevicePolicyManager.class);
        mUiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());

        assumeTrue(
                "Device does not support secure lock",
                mContext.getPackageManager().hasSystemFeature(
                        PackageManager.FEATURE_SECURE_LOCK_SCREEN));

        mUiDevice.executeShellCommand("locksettings set-disabled false");
        mUiDevice.executeShellCommand("locksettings set-pin 1234");

        mDevicePolicyManager.clearPackagePersistentPreferredActivities(ADMIN_RECEIVER_COMPONENT,
                mContext.getPackageName());

        assertFalse(mDevicePolicyManager.isSecondaryLockscreenEnabled(Process.myUserHandle()));
        mDevicePolicyManager.setSecondaryLockscreenEnabled(ADMIN_RECEIVER_COMPONENT, true);
        assertTrue(mDevicePolicyManager.isSecondaryLockscreenEnabled(Process.myUserHandle()));
    }

    @After
    public void tearDown() throws Exception {
        mDevicePolicyManager.setSecondaryLockscreenEnabled(ADMIN_RECEIVER_COMPONENT, false);
        assertFalse(mDevicePolicyManager.isSecondaryLockscreenEnabled(Process.myUserHandle()));
        mUiDevice.executeShellCommand("locksettings clear --old 1234");
        mUiDevice.executeShellCommand("locksettings set-disabled true");
    }

    @Test
    public void testSetSecondaryLockscreenEnabled() throws Exception {
        enterKeyguardPin();
        assertTrue("Lockscreen title not shown",
                mUiDevice.wait(Until.hasObject(By.text(SimpleKeyguardService.TITLE_LABEL)),
                        UI_AUTOMATOR_WAIT_TIME_MILLIS));

        mDevicePolicyManager.setSecondaryLockscreenEnabled(ADMIN_RECEIVER_COMPONENT, false);

        // Verify that the lockscreen is dismissed after disabling the feature
        assertFalse(mDevicePolicyManager.isSecondaryLockscreenEnabled(Process.myUserHandle()));
        verifyHomeLauncherIsShown();
    }

    @Test
    public void testHomeButton() throws Exception {
        enterKeyguardPin();
        assertTrue("Lockscreen title not shown",
                mUiDevice.wait(Until.hasObject(By.text(SimpleKeyguardService.TITLE_LABEL)),
                        UI_AUTOMATOR_WAIT_TIME_MILLIS));

        // Verify that pressing home does not dismiss the lockscreen
        mUiDevice.pressHome();
        verifySecondaryLockscreenIsShown();
    }

    @Test
    public void testDismiss() throws Exception {
        enterKeyguardPin();
        assertTrue("Lockscreen title not shown",
                mUiDevice.wait(Until.hasObject(By.text(SimpleKeyguardService.TITLE_LABEL)),
                        UI_AUTOMATOR_WAIT_TIME_MILLIS));

        mUiDevice.findObject(By.text(SimpleKeyguardService.DISMISS_BUTTON_LABEL)).click();
        verifyHomeLauncherIsShown();

        // Verify that the feature is not disabled after dismissal
        enterKeyguardPin();
        assertTrue(mUiDevice.wait(Until.hasObject(By.text(SimpleKeyguardService.TITLE_LABEL)),
                UI_AUTOMATOR_WAIT_TIME_MILLIS));
        verifySecondaryLockscreenIsShown();
    }

    @Test(expected = SecurityException.class)
    public void testSetSecondaryLockscreen_ineligibleAdmin_throwsSecurityException() {
        final ComponentName badAdmin = new ComponentName("com.foo.bar", ".NonProfileOwnerReceiver");
        mDevicePolicyManager.setSecondaryLockscreenEnabled(badAdmin, true);
    }

    private void enterKeyguardPin() throws Exception {
        final PowerManager pm = mContext.getSystemService(PowerManager.class);
        mUiDevice.executeShellCommand("input keyevent KEYCODE_SLEEP");
        waitUntil("Device still interactive", 5, () -> pm != null && !pm.isInteractive());
        mUiDevice.executeShellCommand("input keyevent KEYCODE_WAKEUP");
        waitUntil("Device still not interactive", 5, () -> pm.isInteractive());
        mUiDevice.executeShellCommand("wm dismiss-keyguard");
        mUiDevice.wait(
                Until.hasObject(By.res("com.android.systemui", "pinEntry")),
                        UI_AUTOMATOR_WAIT_TIME_MILLIS);
        mUiDevice.executeShellCommand("input text 1234");
        mUiDevice.executeShellCommand("input keyevent KEYCODE_ENTER");
    }

    private void verifyHomeLauncherIsShown() {
        String launcherPackageName = getLauncherPackageName();
        assertTrue("Lockscreen title is unexpectedly shown",
                mUiDevice.wait(Until.gone(By.text(SimpleKeyguardService.TITLE_LABEL)),
                        UI_AUTOMATOR_WAIT_TIME_MILLIS));
        assertTrue(String.format("Launcher (%s) is not shown", launcherPackageName),
                mUiDevice.wait(Until.hasObject(By.pkg(launcherPackageName)),
                        UI_AUTOMATOR_WAIT_TIME_MILLIS));
    }

    private void verifySecondaryLockscreenIsShown() {
        String launcherPackageName = getLauncherPackageName();
        assertTrue("Lockscreen title is unexpectedly not shown",
                mUiDevice.wait(Until.hasObject(By.text(SimpleKeyguardService.TITLE_LABEL)),
                        UI_AUTOMATOR_WAIT_TIME_MILLIS));
        assertTrue(String.format("Launcher (%s) is unexpectedly shown", launcherPackageName),
                mUiDevice.wait(Until.gone(By.pkg(launcherPackageName)),
                        UI_AUTOMATOR_WAIT_TIME_MILLIS));
    }

    private String getLauncherPackageName() {
        Intent homeIntent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        List<ResolveInfo> resolveInfos = mContext.getPackageManager().queryIntentActivities(
                homeIntent, 0);
        StringBuilder sb = new StringBuilder();
        for (ResolveInfo resolveInfo : resolveInfos) {
            sb.append(resolveInfo.activityInfo.packageName).append("/").append(
                    resolveInfo.activityInfo.name).append(", ");
        }
        return resolveInfos.isEmpty() ? null : resolveInfos.get(0).activityInfo.packageName;
    }
}
