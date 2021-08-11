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

package com.android.cts.net.hostside;

import android.content.pm.PackageManager;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.Direction;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.UiObject2;
import android.support.test.uiautomator.Until;
import android.telephony.SubscriptionManager;
import android.telephony.SubscriptionPlan;
import android.util.Log;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.time.Period;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;

public class DataWarningReceiverTest extends AbstractRestrictBackgroundNetworkTestCase {

    @Before
    public void setUp() throws Exception {
        super.setUp();

        if (!isSupported()) return;

        clearSnoozeTimestamps();
        registerBroadcastReceiver();
        turnScreenOn();
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();

        if (!isSupported()) return;
    }

    @Test
    public void testSnoozeWarningNotReceived() throws Exception {
        if (!isSupported()) return;
        if (!mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
            Log.i(TAG, "Skipping because feature "
                    + PackageManager.FEATURE_TELEPHONY + " not supported");
            return;
        }
        final SubscriptionManager sm = mContext.getSystemService(SubscriptionManager.class);
        final int subId = SubscriptionManager.getDefaultDataSubscriptionId();
        if (subId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            Log.i(TAG, "Skipping because valid subId not found");
            return;
        }

        setSubPlanOwner(subId, TEST_PKG);
        final List<SubscriptionPlan> originalPlans = sm.getSubscriptionPlans(subId);
        try {
            // In NetworkPolicyManagerService class, we set the data warning bytes to 90% of
            // data limit bytes. So, create the subscription plan in such a way this data warning
            // threshold is already reached.
            final SubscriptionPlan plan = SubscriptionPlan.Builder
                    .createRecurring(ZonedDateTime.parse("2007-03-14T00:00:00.000Z"),
                            Period.ofMonths(1))
                    .setTitle("CTS")
                    .setDataLimit(1_000_000_000, SubscriptionPlan.LIMIT_BEHAVIOR_THROTTLED)
                    .setDataUsage(999_000_000, System.currentTimeMillis())
                    .build();
            sm.setSubscriptionPlans(subId, Arrays.asList(plan));
            final UiDevice uiDevice = UiDevice.getInstance(mInstrumentation);
            uiDevice.openNotification();
            try {
                final UiObject2 uiObject = UiAutomatorUtils.waitFindObject(mContext, uiDevice,
                        By.text("Data warning"));
                if (uiObject == null) {
                    Log.i(TAG, "Skipping because notification object not found");
                    return;
                }
                uiObject.wait(Until.clickable(true), 10_000L);
                uiObject.getParent().swipe(Direction.LEFT, 1.0f);
            } catch (Throwable t) {
                Log.i(TAG, "Skipping because there was an error while finding and swiping the "
                        + "notification", t);
                return;
            }
            assertSnoozeWarningNotReceived();
            uiDevice.pressHome();
        } finally {
            sm.setSubscriptionPlans(subId, originalPlans);
            setSubPlanOwner(subId, "");
        }
    }

    private void setSubPlanOwner(int subId, String packageName) throws Exception {
        executeShellCommand("cmd netpolicy set sub-plan-owner " + subId + " " + packageName);
    }

    private void clearSnoozeTimestamps() throws Exception {
        executeShellCommand("dumpsys netpolicy --unsnooze");
    }
}
