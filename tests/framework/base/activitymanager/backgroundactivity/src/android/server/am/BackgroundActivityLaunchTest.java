/*
 * Copyright (C) 2019 The Android Open Source Project
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

package android.server.am;

import static android.server.am.ActivityManagerState.STATE_RESUMED;
import static android.server.am.UiDeviceUtils.pressHomeButton;
import static android.server.am.UiDeviceUtils.pressUnlockButton;
import static android.server.am.UiDeviceUtils.pressWakeupButton;

import static com.android.compatibility.common.util.SystemUtil.runShellCommand;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.ActivityManager;
import android.app.ActivityTaskManager;
import android.content.ComponentName;
import android.content.Intent;
import android.os.SystemClock;
import android.platform.test.annotations.Presubmit;
import android.provider.Settings;

import androidx.test.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * This class covers all test cases for starting/blocking background activities.
 * As instrumentation tests started by shell are whitelisted to allow starting background activity,
 * tests can't be done in this app alone.
 * Hence, there are 2 extra apps, appA and appB. This class will send commands to appA/appB, for
 * example, send a broadcast to appA and ask it to start a background activity, and we will monitor
 * the result and see if it starts an activity successfully.
 */
@Presubmit
public class BackgroundActivityLaunchTest extends ActivityManagerTestBase {

    private static final int ACTIVITY_FOCUS_TIMEOUT_MS = 3000;

    private static final String APP_A_PACKAGE_NAME = "android.server.am.cts.backgroundactivity.appa";
    private static final String APP_B_PACKAGE_NAME = "android.server.am.cts.backgroundactivity.appb";

    private static final ComponentName APP_A_START_ACTIVITY_RECEIVER_COMPONENT = new ComponentName(
            APP_A_PACKAGE_NAME, "android.server.am.StartBackgroundActivityReceiver");
    private static final ComponentName APP_A_BACKGROUND_ACTIVITY_COMPONENT = new ComponentName(
            APP_A_PACKAGE_NAME, "android.server.am.BackgroundActivity");
    private static final ComponentName APP_A_FOREGROUND_ACTIVITY_COMPONENT = new ComponentName(
            APP_A_PACKAGE_NAME, "android.server.am.ForegroundActivity");
    private static final ComponentName APP_A_SEND_PENDING_INTENT_RECEIVER_COMPONENT =
            new ComponentName(APP_A_PACKAGE_NAME, "android.server.am.SendPendingIntentReceiver");
    private static final ComponentName APP_B_FOREGROUND_ACTIVITY_COMPONENT = new ComponentName(
            APP_B_PACKAGE_NAME, "android.server.am.ForegroundActivity");
    private static final String LAUNCH_BACKGROUND_ACTIVITY_EXTRA =
            "LAUNCH_BACKGROUND_ACTIVITY_EXTRA";
    private static final String LAUNCH_BACKGROUND_ACTIVITY_STARTS_ENABLED =
            "background_activity_starts_enabled";
    private static final int DEFAULT_LAUNCH_BG_ACTIVITY_STARTS_SETTING = -1;

    private final ActivityManagerState mAmState = new ActivityManagerState();
    private int mOriginalBgActivityStartsSetting;

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getContext();
        mAm = mContext.getSystemService(ActivityManager.class);
        mAtm = mContext.getSystemService(ActivityTaskManager.class);
        mOriginalBgActivityStartsSetting = Settings.Global.getInt(mContext.getContentResolver(),
                LAUNCH_BACKGROUND_ACTIVITY_STARTS_ENABLED,
                DEFAULT_LAUNCH_BG_ACTIVITY_STARTS_SETTING);
        runShellCommand("settings put global " + LAUNCH_BACKGROUND_ACTIVITY_STARTS_ENABLED + " 0");

        pressWakeupButton();
        pressUnlockButton();
        removeStacksWithActivityTypes(ALL_ACTIVITY_TYPE_BUT_HOME);

        runShellCommand("cmd deviceidle tempwhitelist -d 100000 " + APP_A_PACKAGE_NAME);
        runShellCommand("cmd deviceidle tempwhitelist -d 100000 " + APP_B_PACKAGE_NAME);
    }

    @After
    public void tearDown() throws Exception {
        if (mOriginalBgActivityStartsSetting == DEFAULT_LAUNCH_BG_ACTIVITY_STARTS_SETTING) {
            runShellCommand("settings delete global " + LAUNCH_BACKGROUND_ACTIVITY_STARTS_ENABLED);
        } else {
            runShellCommand("settings put global " + LAUNCH_BACKGROUND_ACTIVITY_STARTS_ENABLED + " "
                    + mOriginalBgActivityStartsSetting);
        }
        pressHomeButton();
        mAmWmState.waitForHomeActivityVisible();
    }

    @Test
    public void testBackgroundActivityBlocked() throws Exception {
        // Start AppA background activity and blocked
        Intent intent = new Intent();
        intent.setComponent(APP_A_START_ACTIVITY_RECEIVER_COMPONENT);
        mContext.sendBroadcast(intent);
        boolean result = waitForActivity(ACTIVITY_FOCUS_TIMEOUT_MS,
                APP_A_BACKGROUND_ACTIVITY_COMPONENT);
        assertFalse("Should not able to launch background activity", result);
    }

    @Test
    public void testBackgroundActivityNotBlockedWhenForegroundActivityExists() throws Exception {
        // Start AppA foreground activity
        Intent intent = new Intent();
        intent.setComponent(APP_A_FOREGROUND_ACTIVITY_COMPONENT);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mContext.startActivity(intent);
        boolean result = waitForActivity(ACTIVITY_FOCUS_TIMEOUT_MS,
                APP_A_FOREGROUND_ACTIVITY_COMPONENT);
        assertTrue("Not able to start foreground Activity", result);

        // Start AppA background activity successfully as there's a foreground activity
        intent = new Intent();
        intent.setComponent(APP_A_START_ACTIVITY_RECEIVER_COMPONENT);
        mContext.sendBroadcast(intent);
        result = waitForActivity(ACTIVITY_FOCUS_TIMEOUT_MS, APP_A_BACKGROUND_ACTIVITY_COMPONENT);
        assertTrue("Not able to launch background activity", result);
    }

    @Test
    public void testActivityNotBlockedwhenForegroundActivityLaunch() throws Exception {
        // Start foreground activity, and foreground activity able to launch background activity
        // successfully
        Intent intent = new Intent();
        intent.setComponent(APP_A_FOREGROUND_ACTIVITY_COMPONENT);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra(LAUNCH_BACKGROUND_ACTIVITY_EXTRA, true);
        mContext.startActivity(intent);
        boolean result = waitForActivity(ACTIVITY_FOCUS_TIMEOUT_MS,
                APP_A_BACKGROUND_ACTIVITY_COMPONENT);
        assertTrue("Not able to launch background activity", result);
    }

    @Test
    public void testPendingIntentActivityBlocked() throws Exception {
        // Cannot start activity by pending intent, as both appA and appB are in background
        Intent intent = new Intent();
        intent.setComponent(APP_A_SEND_PENDING_INTENT_RECEIVER_COMPONENT);
        mContext.sendBroadcast(intent);
        boolean result = waitForActivity(ACTIVITY_FOCUS_TIMEOUT_MS,
                APP_A_BACKGROUND_ACTIVITY_COMPONENT);
        assertFalse("Should not able to launch background activity", result);
    }

    @Test
    public void testPendingIntentActivityNotBlocked_appAIsForeground() throws Exception {
        // Start AppA foreground activity
        Intent intent = new Intent();
        intent.setComponent(APP_A_FOREGROUND_ACTIVITY_COMPONENT);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mContext.startActivity(intent);
        boolean result = waitForActivity(ACTIVITY_FOCUS_TIMEOUT_MS,
                APP_A_FOREGROUND_ACTIVITY_COMPONENT);
        assertTrue("Not able to start foreground Activity", result);

        // Send pendingIntent from AppA to AppB, and the AppB launch the pending intent to start
        // activity in App A
        intent = new Intent();
        intent.setComponent(APP_A_SEND_PENDING_INTENT_RECEIVER_COMPONENT);
        mContext.sendBroadcast(intent);
        result = waitForActivity(ACTIVITY_FOCUS_TIMEOUT_MS, APP_A_BACKGROUND_ACTIVITY_COMPONENT);
        assertTrue("Not able to launch background activity", result);
    }

    @Test
    public void testPendingIntentBroadcastActivityNotBlocked_appBIsForeground() throws Exception {
        // Start AppB foreground activity
        Intent intent = new Intent();
        intent.setComponent(APP_B_FOREGROUND_ACTIVITY_COMPONENT);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mContext.startActivity(intent);
        boolean result = waitForActivity(ACTIVITY_FOCUS_TIMEOUT_MS,
                APP_B_FOREGROUND_ACTIVITY_COMPONENT);
        assertTrue("Not able to start foreground Activity", result);

        // Send pendingIntent from AppA to AppB, and the AppB launch the pending intent to start
        // activity in App A
        intent = new Intent();
        intent.setComponent(APP_A_SEND_PENDING_INTENT_RECEIVER_COMPONENT);
        mContext.sendBroadcast(intent);
        result = waitForActivity(ACTIVITY_FOCUS_TIMEOUT_MS, APP_A_BACKGROUND_ACTIVITY_COMPONENT);
        assertTrue("Not able to launch background activity", result);
    }

    // Return true if the activity is shown before timeout
    private boolean waitForActivity(int timeoutMs, ComponentName componentName) {
        mAmWmState.waitForActivityState(componentName, STATE_RESUMED);
        return componentName.flattenToString().equals(mAmWmState.getAmState().getFocusedActivity());
    }
}