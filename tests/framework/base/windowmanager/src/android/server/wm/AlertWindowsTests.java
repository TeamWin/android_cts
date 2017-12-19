/*
 * Copyright (C) 2017 The Android Open Source Project
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
 * limitations under the License
 */

package android.server.wm;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.ComponentName;
import android.platform.test.annotations.Presubmit;
import android.server.am.ActivityManagerTestBase;
import android.server.am.WaitForValidActivityState;
import android.server.am.WindowManagerState;

import org.junit.After;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Build: mmma -j32 cts/tests/framework/base
 * Run: cts/tests/framework/base/activitymanager/util/run-test CtsWindowManagerDeviceTestCases android.server.wm.AlertWindowsTests
 */
@Presubmit
public class AlertWindowsTests extends ActivityManagerTestBase {

    private static final ComponentName TEST_ACTIVITY = ComponentName.createRelative(
            "android.server.wm.alertwindowapp", ".AlertWindowTestActivity");
    private static final ComponentName SDK25_TEST_ACTIVITY = ComponentName.createRelative(
            "android.server.wm.alertwindowappsdk25", ".AlertWindowTestActivitySdk25");

    // From WindowManager.java
    private static final int TYPE_BASE_APPLICATION      = 1;
    private static final int FIRST_SYSTEM_WINDOW        = 2000;

    private static final int TYPE_PHONE                 = FIRST_SYSTEM_WINDOW + 2;
    private static final int TYPE_SYSTEM_ALERT          = FIRST_SYSTEM_WINDOW + 3;
    private static final int TYPE_SYSTEM_OVERLAY        = FIRST_SYSTEM_WINDOW + 6;
    private static final int TYPE_PRIORITY_PHONE        = FIRST_SYSTEM_WINDOW + 7;
    private static final int TYPE_SYSTEM_ERROR          = FIRST_SYSTEM_WINDOW + 10;
    private static final int TYPE_APPLICATION_OVERLAY   = FIRST_SYSTEM_WINDOW + 38;

    private static final int TYPE_STATUS_BAR            = FIRST_SYSTEM_WINDOW;
    private static final int TYPE_INPUT_METHOD          = FIRST_SYSTEM_WINDOW + 11;
    private static final int TYPE_NAVIGATION_BAR        = FIRST_SYSTEM_WINDOW + 19;

    private final List<Integer> mAlertWindowTypes = Arrays.asList(
            TYPE_PHONE,
            TYPE_PRIORITY_PHONE,
            TYPE_SYSTEM_ALERT,
            TYPE_SYSTEM_ERROR,
            TYPE_SYSTEM_OVERLAY,
            TYPE_APPLICATION_OVERLAY);
    private final List<Integer> mSystemWindowTypes = Arrays.asList(
            TYPE_STATUS_BAR,
            TYPE_INPUT_METHOD,
            TYPE_NAVIGATION_BAR);

    @After
    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        setAlertWindowPermission(TEST_ACTIVITY, false);
        setAlertWindowPermission(SDK25_TEST_ACTIVITY, false);
        stopTestPackage(TEST_ACTIVITY);
        stopTestPackage(SDK25_TEST_ACTIVITY);
    }

    @Test
    public void testAlertWindowAllowed() throws Exception {
        runAlertWindowTest(TEST_ACTIVITY, true /* hasAlertWindowPermission */,
                true /* atLeastO */);
    }

    @Test
    public void testAlertWindowDisallowed() throws Exception {
        runAlertWindowTest(TEST_ACTIVITY, false /* hasAlertWindowPermission */,
                true /* atLeastO */);
    }

    @Test
    public void testAlertWindowAllowedSdk25() throws Exception {
        runAlertWindowTest(SDK25_TEST_ACTIVITY, true /* hasAlertWindowPermission */,
                false /* atLeastO */);
    }

    @Test
    public void testAlertWindowDisallowedSdk25() throws Exception {
        runAlertWindowTest(SDK25_TEST_ACTIVITY, false /* hasAlertWindowPermission */,
                false /* atLeastO */);
    }

    private void runAlertWindowTest(final ComponentName activityName,
            final boolean hasAlertWindowPermission, final boolean atLeastO) throws Exception {
        setAlertWindowPermission(activityName, hasAlertWindowPermission);

        executeShellCommand(getAmStartCmd(activityName));
        mAmWmState.computeState(new WaitForValidActivityState(activityName));
        mAmWmState.assertVisibility(activityName, true);

        assertAlertWindows(activityName, hasAlertWindowPermission, atLeastO);
    }

    private void assertAlertWindows(final ComponentName activityName,
            final boolean hasAlertWindowPermission, final boolean atLeastO) {
        final String packageName = activityName.getPackageName();
        final WindowManagerState wMState = mAmWmState.getWmState();

        final ArrayList<WindowManagerState.WindowState> alertWindows = new ArrayList<>();
        wMState.getWindowsByPackageName(packageName, mAlertWindowTypes, alertWindows);

        if (!hasAlertWindowPermission) {
            assertTrue("Should be empty alertWindows=" + alertWindows, alertWindows.isEmpty());
            return;
        }

        if (atLeastO) {
            // Assert that only TYPE_APPLICATION_OVERLAY was created.
            for (WindowManagerState.WindowState win : alertWindows) {
                assertTrue("Can't create win=" + win + " on SDK O or greater",
                        win.getType() == TYPE_APPLICATION_OVERLAY);
            }
        }

        final WindowManagerState.WindowState mainAppWindow =
                wMState.getWindowByPackageName(packageName, TYPE_BASE_APPLICATION);

        assertNotNull(mainAppWindow);

        final WindowManagerState.WindowState lowestAlertWindow = alertWindows.get(0);
        final WindowManagerState.WindowState highestAlertWindow =
                alertWindows.get(alertWindows.size() - 1);

        // Assert that the alert windows have higher z-order than the main app window
        assertTrue("lowestAlertWindow=" + lowestAlertWindow + " less than mainAppWindow="
                + mainAppWindow, lowestAlertWindow.getZOrder() > mainAppWindow.getZOrder());

        // Assert that legacy alert windows have a lower z-order than the new alert window layer.
        final WindowManagerState.WindowState appOverlayWindow =
                wMState.getWindowByPackageName(packageName, TYPE_APPLICATION_OVERLAY);
        if (appOverlayWindow != null && highestAlertWindow != appOverlayWindow) {
            assertTrue("highestAlertWindow=" + highestAlertWindow
                            + " greater than appOverlayWindow=" + appOverlayWindow,
                    highestAlertWindow.getZOrder() < appOverlayWindow.getZOrder());
        }

        // Assert that alert windows are below key system windows.
        final ArrayList<WindowManagerState.WindowState> systemWindows = new ArrayList<>();
        wMState.getWindowsByPackageName(packageName, mSystemWindowTypes, systemWindows);
        if (!systemWindows.isEmpty()) {
            final WindowManagerState.WindowState lowestSystemWindow = alertWindows.get(0);
            assertTrue("highestAlertWindow=" + highestAlertWindow
                            + " greater than lowestSystemWindow=" + lowestSystemWindow,
                    highestAlertWindow.getZOrder() < lowestSystemWindow.getZOrder());
        }
    }

    private void setAlertWindowPermission(final ComponentName activityName, final boolean allow) {
        final String packageName = activityName.getPackageName();
        executeShellCommand("appops set " + packageName + " android:system_alert_window "
                + (allow ? "allow" : "deny"));
    }
}
