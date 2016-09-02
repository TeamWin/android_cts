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
package com.android.cts.retaildemo;

import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertTrue;
import static junit.framework.Assert.fail;

import android.os.PowerManager;
import android.os.UserManager;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.SmallTest;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.UiObject2;
import android.support.test.uiautomator.Until;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.ArrayList;
import java.util.Random;

@SmallTest
@RunWith(JUnit4.class)
public class DemoModeUiAutomationTest {
    private static final String RETAIL_DEMO_PKG = "com.android.retaildemo";

    private static final long LAUNCH_TIMEOUT_MS = 5000; // 5 sec

    private static final long UI_TIMEOUT_MS = 4000; // 4 sec

    private static final String RESET_NOTIFICATION_TEXT = "Tap to reset device";
    private UiDevice mUiDevice;

    @Before
    public void setUp() throws Exception {
        mUiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
    }

    @Test
    public void testResetNotification() {
        startDemoSession();
        mUiDevice.openNotification();
        mUiDevice.wait(Until.hasObject(By.text(RESET_NOTIFICATION_TEXT)), UI_TIMEOUT_MS);
        UiObject2 resetNotification = mUiDevice.findObject(By.text(RESET_NOTIFICATION_TEXT));
        assertNotNull("Notification to reset demo mode not found", resetNotification);
        resetNotification.click();
    }

    @Test
    public void testUserRestrictions_inDemoUser() throws Exception {
        final UserManager um = InstrumentationRegistry.getContext().getSystemService(
                UserManager.class);
        final String[] expectedRestrictions = new String[] {
                UserManager.DISALLOW_CONFIG_WIFI,
                UserManager.DISALLOW_CONFIG_BLUETOOTH,
                UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES,
                UserManager.DISALLOW_CONFIG_MOBILE_NETWORKS,
                UserManager.DISALLOW_USB_FILE_TRANSFER,
                UserManager.DISALLOW_MODIFY_ACCOUNTS
        };
        final ArrayList<String> missingRestrictions = new ArrayList<>();
        for (String restriction : expectedRestrictions) {
            if (!um.hasUserRestriction(restriction)) {
                missingRestrictions.add(restriction);
            }
        }
        assertTrue("Restrictions should be set: " + missingRestrictions,
                missingRestrictions.isEmpty());
        assertFalse("Restriction should not be set: " + UserManager.DISALLOW_OUTGOING_CALLS,
                um.hasUserRestriction(UserManager.DISALLOW_OUTGOING_CALLS));
    }

    @Test
    public void testUserRestrictions_inSystemUser() throws Exception {
        final UserManager um = InstrumentationRegistry.getContext().getSystemService(
                UserManager.class);
        assertTrue(UserManager.DISALLOW_SAFE_BOOT + " should be set in system user.",
                um.hasUserRestriction(UserManager.DISALLOW_SAFE_BOOT));
    }

    @Test
    public void testScreenIsInteractive() throws Exception {
        final PowerManager pm = InstrumentationRegistry.getContext().getSystemService(
                PowerManager.class);
        assertTrue(pm.isInteractive());
    }

    private void startDemoSession() {
        final int h = mUiDevice.getDisplayHeight();
        final int w = mUiDevice.getDisplayWidth();
        final Random random = new Random(0);
        mUiDevice.click(random.nextInt(w), random.nextInt(h));
    }

    @Test
    public void testRetailDemoPkgLaunched() {
        if (!Boolean.TRUE.equals(mUiDevice.wait(
                Until.hasObject(By.pkg(RETAIL_DEMO_PKG).depth(0)), LAUNCH_TIMEOUT_MS))) {
            fail("Retail demo package is not started");
        }
    }
}