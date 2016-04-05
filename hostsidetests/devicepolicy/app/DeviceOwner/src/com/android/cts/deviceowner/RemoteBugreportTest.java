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
package com.android.cts.deviceowner;

import android.app.Instrumentation;
import android.app.NotificationManager;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.UiObject;
import android.support.test.uiautomator.UiObject2;
import android.support.test.uiautomator.UiObjectNotFoundException;
import android.support.test.uiautomator.UiSelector;
import android.support.test.uiautomator.Until;
import android.test.InstrumentationTestCase;

/**
 * Test class for remote bugreports.
 *
 * This class also handles making sure that the test is the device owner
 * and that it has an active admin registered, so that all tests may
 * assume these are done. The admin component can be accessed through
 * {@link BaseDeviceOwnerTest#getWho()}.
 */
public class RemoteBugreportTest extends InstrumentationTestCase {

    private static final int UI_TIMEOUT_MILLIS = 5000; //5 seconds

    private static final String MESSAGE_ONLY_ONE_MANAGED_USER_ALLOWED =
            "There should only be one user, managed by Device Owner";

    private static final String TAKING_BUG_REPORT = "Taking bug report";
    private static final String DECLINE = "DECLINE";

    private DevicePolicyManager mDevicePolicyManager;
    private Context mContext;
    private UiDevice mUiDevice;
    private ComponentName mComponentName;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        Instrumentation instrumentation = getInstrumentation();
        mContext = instrumentation.getTargetContext();
        mUiDevice = UiDevice.getInstance(instrumentation);
        mDevicePolicyManager = (DevicePolicyManager)
                mContext.getSystemService(Context.DEVICE_POLICY_SERVICE);
        BaseDeviceOwnerTest.assertDeviceOwner(mDevicePolicyManager);
        mComponentName = BaseDeviceOwnerTest.getWho();
    }

    /**
     * Test: only one remote bugreport flow can be running on the device at one time.
     */
    public void testSubsequentRemoteBugreportThrottled() {
        boolean startedSuccessfully = mDevicePolicyManager.requestBugreport(mComponentName);
        assertTrue(startedSuccessfully);

        // subsequent attempts should be throttled
        assertFalse(mDevicePolicyManager.requestBugreport(mComponentName));
        assertFalse(mDevicePolicyManager.requestBugreport(mComponentName));

        cancelRemoteBugreportFlowIfStartedSuccessfully(startedSuccessfully);
    }

    /**
     * Test: remote bugreport flow can only be started if there's one user on the device.
     */
    public void testRequestBugreportNotStartedIfMoreThanOneUserPresent() {
        boolean startedSuccessfully = false;
        try {
            startedSuccessfully = mDevicePolicyManager.requestBugreport(mComponentName);
            fail("did not throw expected SecurityException");
        } catch (SecurityException e) {
            assertEquals(e.getMessage(), MESSAGE_ONLY_ONE_MANAGED_USER_ALLOWED);
        } finally {
            cancelRemoteBugreportFlowIfStartedSuccessfully(startedSuccessfully);
        }
    }

    /**
     * Clicks on "Taking bugreport..." notification, and then DECLINE button on the consent dialog
     * to cancel the whole remote bugreport flow (including stopping the dumpstate service).
     */
    private void cancelRemoteBugreportFlowIfStartedSuccessfully(boolean startedSuccessfully) {
        if (!startedSuccessfully) {
            return;
        }

        UiObject2 bugreportNotification = findRemoteBugreportNotification();
        assertNotNull(bugreportNotification);
        bugreportNotification.click();

        // give it max 5 seconds to find the DECLINE button on the dialog
        boolean declineButtonPresent = mUiDevice.wait(
                Until.hasObject(By.text(DECLINE)), UI_TIMEOUT_MILLIS);
        assertTrue(declineButtonPresent);

        UiObject declineButton = mUiDevice.findObject(new UiSelector().text(DECLINE));
        assertNotNull(declineButton);
        try {
            declineButton.click();
        } catch (UiObjectNotFoundException e) {
            throw new IllegalStateException("Exception when clicking on 'DECLINE' button", e);
        }
    }

    /**
     * Attempts to find the remote bugreport notification scrolling down in the notification panel
     * in between 10 attempts.
     */
    private UiObject2 findRemoteBugreportNotification() {
        mUiDevice.openNotification();
        final int displayWidth = mUiDevice.getDisplayWidth();
        final int displayHeight = mUiDevice.getDisplayHeight();
        for (int i = 0; i < 10; i++) {
            UiObject2 notification = mUiDevice.wait(Until.findObject(
                    By.textStartsWith(TAKING_BUG_REPORT)), UI_TIMEOUT_MILLIS);
            if (notification != null) {
                return notification;
            } else {
                /* makes a swipe from the middle of the screen upwards to the top of the screen
                (the motion is upwards, so it scrolls downwards) half a screen, so that the
                notification is always fully visible - never cut in two pieces) */
                mUiDevice.swipe(displayWidth / 2, displayHeight / 2, displayWidth / 2,
                        /* endY= */ 0, /* steps= */ 30);
            }
        }
        return null;
    }
}
