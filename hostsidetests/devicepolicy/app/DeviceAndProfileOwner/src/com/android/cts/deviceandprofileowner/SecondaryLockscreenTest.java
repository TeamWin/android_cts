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
import static org.testng.Assert.assertThrows;

import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.os.PowerManager;
import android.os.Process;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.Until;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;

import java.util.List;

public class SecondaryLockscreenTest extends BaseDeviceAdminTest {

    private static final int UI_AUTOMATOR_WAIT_TIME_MILLIS = 5000;
    private static final String TAG = "SecondaryLockscreenTest";

    private UiDevice mUiDevice;

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();
        mUiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        runShellCommand("locksettings set-pin 1234");

        mDevicePolicyManager.clearPackagePersistentPreferredActivities(ADMIN_RECEIVER_COMPONENT,
                mContext.getPackageName());

        assertFalse(mDevicePolicyManager.isSecondaryLockscreenEnabled(Process.myUserHandle()));
        mDevicePolicyManager.setSecondaryLockscreenEnabled(ADMIN_RECEIVER_COMPONENT, true);
        assertTrue(mDevicePolicyManager.isSecondaryLockscreenEnabled(Process.myUserHandle()));
    }

    @After
    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        mDevicePolicyManager.setSecondaryLockscreenEnabled(ADMIN_RECEIVER_COMPONENT, false);
        assertFalse(mDevicePolicyManager.isSecondaryLockscreenEnabled(Process.myUserHandle()));
        runShellCommand("locksettings clear --old 1234");
    }

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

    public void testHomeButton() throws Exception {
        enterKeyguardPin();
        assertTrue("Lockscreen title not shown",
                mUiDevice.wait(Until.hasObject(By.text(SimpleKeyguardService.TITLE_LABEL)),
                        UI_AUTOMATOR_WAIT_TIME_MILLIS));

        // Verify that pressing home does not dismiss the lockscreen
        mUiDevice.pressHome();
        verifySecondaryLockscreenIsShown();
    }

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

    public void testSetSecondaryLockscreen_ineligibleAdmin_throwsSecurityException() {
        final ComponentName badAdmin = new ComponentName("com.foo.bar", ".NonProfileOwnerReceiver");
        assertThrows(SecurityException.class,
                () -> mDevicePolicyManager.setSecondaryLockscreenEnabled(badAdmin, true));
    }

    private void enterKeyguardPin() throws Exception {
        final PowerManager pm = mContext.getSystemService(PowerManager.class);
        runShellCommand("input keyevent KEYCODE_SLEEP");
        waitUntil("Device still interactive", 5,
                () -> pm != null && !pm.isInteractive());
        runShellCommand("input keyevent KEYCODE_WAKEUP");
        waitUntil("Device still not interactive", 5,
                () -> pm.isInteractive());
        runShellCommand("wm dismiss-keyguard");
        mUiDevice.wait(Until.hasObject(By.res("com.android.systemui", "pinEntry")),
                UI_AUTOMATOR_WAIT_TIME_MILLIS);
        runShellCommand("input text 1234");
        runShellCommand("input keyevent KEYCODE_ENTER");
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