/*
 * Copyright (C) 2016 The Android Open Source Project
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

package android.server.am;

import static org.junit.Assert.assertTrue;

import android.os.Build;

import org.junit.After;
import org.junit.Test;

/**
 * Ensure that compatibility dialog is shown when launching an application with
 * an unsupported smallest width.
 *
 * Build: mmma -j32 cts/hostsidetests/services
 * Run: cts/tests/framework/base/activitymanager/util/run-test CtsActivityManagerDeviceTestCases android.server.am.DisplaySizeTest
 */
public class DisplaySizeTest extends ActivityManagerTestBase {
    private static final String DENSITY_PROP_DEVICE = "ro.sf.lcd_density";
    private static final String DENSITY_PROP_EMULATOR = "qemu.sf.lcd_density";

    private static final String AM_START_COMMAND = "am start -n %s/%s.%s";
    private static final String AM_FORCE_STOP = "am force-stop %s";

    private static final int ACTIVITY_TIMEOUT_MILLIS = 1000;
    private static final int WINDOW_TIMEOUT_MILLIS = 1000;

    @After
    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        resetDensity();

        // Ensure app process is stopped.
        forceStopPackage("android.server.am.displaysize");
        forceStopPackage("android.server.am");
    }

    @Test
    public void testCompatibilityDialog() throws Exception {
        // Launch some other app (not to perform density change on launcher).
        startActivity("android.server.am", "TestActivity");
        verifyWindowDisplayed("TestActivity", ACTIVITY_TIMEOUT_MILLIS);

        setUnsupportedDensity();

        // Launch target app.
        startActivity("android.server.am.displaysize", "SmallestWidthActivity");
        verifyWindowDisplayed("SmallestWidthActivity", ACTIVITY_TIMEOUT_MILLIS);
        verifyWindowDisplayed("UnsupportedDisplaySizeDialog", WINDOW_TIMEOUT_MILLIS);
    }

    @Test
    public void testCompatibilityDialogWhenFocused() throws Exception {
        startActivity("android.server.am.displaysize", "SmallestWidthActivity");
        verifyWindowDisplayed("SmallestWidthActivity", ACTIVITY_TIMEOUT_MILLIS);

        setUnsupportedDensity();

        verifyWindowDisplayed("UnsupportedDisplaySizeDialog", WINDOW_TIMEOUT_MILLIS);
    }

    @Test
    public void testCompatibilityDialogAfterReturn() throws Exception {
        // Launch target app.
        startActivity("android.server.am.displaysize", "SmallestWidthActivity");
        verifyWindowDisplayed("SmallestWidthActivity", ACTIVITY_TIMEOUT_MILLIS);
        // Launch another activity.
        startOtherActivityOnTop("android.server.am.displaysize", "SmallestWidthActivity");
        verifyWindowDisplayed("TestActivity", ACTIVITY_TIMEOUT_MILLIS);

        setUnsupportedDensity();

        // Go back.
        executeShellCommand("input keyevent 4");

        verifyWindowDisplayed("SmallestWidthActivity", ACTIVITY_TIMEOUT_MILLIS);
        verifyWindowDisplayed("UnsupportedDisplaySizeDialog", WINDOW_TIMEOUT_MILLIS);
    }

    private void setUnsupportedDensity() {
        // Set device to 0.85 zoom. It doesn't matter that we're zooming out
        // since the feature verifies that we're in a non-default density.
        final int stableDensity = getStableDensity();
        final int targetDensity = (int) (stableDensity * 0.85);
        setDensity(targetDensity);
    }

    private int getStableDensity() {
        final String densityProp;
        if (Build.getSerial().startsWith("emulator-")) {
            densityProp = DENSITY_PROP_EMULATOR;
        } else {
            densityProp = DENSITY_PROP_DEVICE;
        }

        return Integer.parseInt(executeShellCommand("getprop " + densityProp).trim());
    }

    private void setDensity(int targetDensity) {
        executeShellCommand("wm density " + targetDensity);

        // Verify that the density is changed.
        final String output = executeShellCommand("wm density");
        final boolean success = output.contains("Override density: " + targetDensity);

        assertTrue("Failed to set density to " + targetDensity, success);
    }

    private void resetDensity() {
        executeShellCommand("wm density reset");
    }

    private void forceStopPackage(String packageName) {
        final String forceStopCmd = String.format(AM_FORCE_STOP, packageName);
        executeShellCommand(forceStopCmd);
    }

    private void startActivity(String packageName, String activityName){
        executeShellCommand(getStartCommand(packageName, activityName));
    }

    private void startOtherActivityOnTop(String packageName, String activityName) {
        final String startCmd = getStartCommand(packageName, activityName)
                + " -f 0x20000000 --ez launch_another_activity true";
        executeShellCommand(startCmd);
    }

    private String getStartCommand(String packageName, String activityName) {
        return String.format(AM_START_COMMAND, packageName, packageName, activityName);
    }

    private void verifyWindowDisplayed(String windowName, long timeoutMillis) {
        boolean success = false;

        // Verify that compatibility dialog is shown within 1000ms.
        final long timeoutTimeMillis = System.currentTimeMillis() + timeoutMillis;
        while (!success && System.currentTimeMillis() < timeoutTimeMillis) {
            final String output = executeShellCommand("dumpsys window");
            success = output.contains(windowName);
        }

        assertTrue(windowName + " was not displayed", success);
    }
}
