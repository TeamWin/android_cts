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

package android.server.wm;

import static android.server.wm.ActivityManagerState.STATE_INITIALIZING;
import static android.server.wm.ActivityManagerState.STATE_RESUMED;
import static android.server.wm.ComponentNameUtils.getActivityName;
import static android.server.wm.UiDeviceUtils.pressHomeButton;
import static android.server.wm.UiDeviceUtils.pressUnlockButton;
import static android.server.wm.UiDeviceUtils.pressWakeupButton;
import static android.server.wm.backgroundactivity.appa.Components.APP_A_BACKGROUND_ACTIVITY;
import static android.server.wm.backgroundactivity.appa.Components.APP_A_FOREGROUND_ACTIVITY;
import static android.server.wm.backgroundactivity.appa.Components.APP_A_SECOND_BACKGROUND_ACTIVITY;
import static android.server.wm.backgroundactivity.appa.Components.APP_A_SEND_PENDING_INTENT_RECEIVER;
import static android.server.wm.backgroundactivity.appa.Components.APP_A_START_ACTIVITY_RECEIVER;
import static android.server.wm.backgroundactivity.appa.Components.ForegroundActivity.LAUNCH_BACKGROUND_ACTIVITY_EXTRA;
import static android.server.wm.backgroundactivity.appa.Components.ForegroundActivity.LAUNCH_SECOND_BACKGROUND_ACTIVITY_EXTRA;
import static android.server.wm.backgroundactivity.appa.Components.ForegroundActivity.RELAUNCH_FOREGROUND_ACTIVITY_EXTRA;
import static android.server.wm.backgroundactivity.appb.Components.APP_B_FOREGROUND_ACTIVITY;

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
import android.server.wm.settings.SettingsSession;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.FlakyTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.runners.model.Statement;

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

    /** Copied from {@link Settings.Global#BACKGROUND_ACTIVITY_STARTS_ENABLED}. */
    private static final String BACKGROUND_ACTIVITY_STARTS_ENABLED =
            "background_activity_starts_enabled";

    @Rule
    public TestRule mBgActivityStartsEnabledRule = (base, description) -> new Statement() {
        @Override
        public void evaluate() throws Throwable {
            try (SettingsSession<Integer> bgActivityStartsEnabledSession = new SettingsSession<>(
                    Settings.Global.getUriFor(BACKGROUND_ACTIVITY_STARTS_ENABLED),
                    Settings.Global::getInt,
                    Settings.Global::putInt)) {
                bgActivityStartsEnabledSession.set(0 /* disable */);

                base.evaluate();
            }
        }
    };

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getContext();
        mAm = mContext.getSystemService(ActivityManager.class);
        mAtm = mContext.getSystemService(ActivityTaskManager.class);

        pressWakeupButton();
        pressUnlockButton();
        removeStacksWithActivityTypes(ALL_ACTIVITY_TYPE_BUT_HOME);

        runShellCommand("cmd deviceidle tempwhitelist -d 100000 "
                + APP_A_FOREGROUND_ACTIVITY.getPackageName());
        runShellCommand("cmd deviceidle tempwhitelist -d 100000 "
                + APP_B_FOREGROUND_ACTIVITY.getPackageName());
    }

    @After
    public void tearDown() throws Exception {
        pressHomeButton();
        mAmWmState.waitForHomeActivityVisible();
    }

    @Test
    public void testBackgroundActivityBlocked() throws Exception {
        // Start AppA background activity and blocked
        Intent intent = new Intent();
        intent.setComponent(APP_A_START_ACTIVITY_RECEIVER);
        mContext.sendBroadcast(intent);
        boolean result = waitForActivity(APP_A_BACKGROUND_ACTIVITY);
        assertFalse("Should not able to launch background activity", result);
    }

    @Test
    public void testBackgroundActivityNotBlockedWhenForegroundActivityExists() throws Exception {
        // Start AppA foreground activity
        Intent intent = new Intent();
        intent.setComponent(APP_A_FOREGROUND_ACTIVITY);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mContext.startActivity(intent);
        boolean result = waitForActivity(APP_A_FOREGROUND_ACTIVITY);
        assertTrue("Not able to start foreground activity", result);

        // Start AppA background activity successfully as there's a foreground activity
        intent = new Intent();
        intent.setComponent(APP_A_START_ACTIVITY_RECEIVER);
        mContext.sendBroadcast(intent);
        result = waitForActivity(APP_A_BACKGROUND_ACTIVITY);
        assertTrue("Not able to launch background activity", result);
    }

    @Test
    public void testActivityNotBlockedWhenForegroundActivityLaunch() throws Exception {
        // Start foreground activity, and foreground activity able to launch background activity
        // successfully
        Intent intent = new Intent();
        intent.setComponent(APP_A_FOREGROUND_ACTIVITY);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra(LAUNCH_BACKGROUND_ACTIVITY_EXTRA, true);
        mContext.startActivity(intent);
        boolean result = waitForActivity(APP_A_BACKGROUND_ACTIVITY);
        assertTrue("Not able to launch background activity", result);
    }

    @Test
    @FlakyTest(bugId = 130800326)
    public void testActivityBlockedWhenForegroundActivityRestartsItself() throws Exception {
        // Start AppA foreground activity
        Intent intent = new Intent();
        intent.setComponent(APP_A_FOREGROUND_ACTIVITY);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra(RELAUNCH_FOREGROUND_ACTIVITY_EXTRA, true);
        mContext.startActivity(intent);
        boolean result = waitForActivity(APP_A_FOREGROUND_ACTIVITY);
        assertTrue("Not able to start foreground activity", result);

        // The foreground activity will be paused but will attempt to restart itself in onPause()
        pressHomeButton();
        mAmWmState.waitForHomeActivityVisible();

        // Any activity launch will be blocked for 5s because of app switching protection.
        SystemClock.sleep(5000);

        result = waitForActivity(APP_A_FOREGROUND_ACTIVITY);
        assertFalse("Previously foreground Activity should not be able to relaunch itself", result);
    }

    @Test
    public void testSecondActivityNotBlockedWhenForegroundActivityLaunch() throws Exception {
        // Start AppA foreground activity, which will immediately launch one activity
        // and then the second.
        Intent intent = new Intent();
        intent.setComponent(APP_A_FOREGROUND_ACTIVITY);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra(LAUNCH_BACKGROUND_ACTIVITY_EXTRA, true);
        intent.putExtra(LAUNCH_SECOND_BACKGROUND_ACTIVITY_EXTRA, true);
        mContext.startActivity(intent);

        boolean result = waitForActivity(APP_A_SECOND_BACKGROUND_ACTIVITY);
        assertTrue("Not able to launch second background activity", result);

        waitAndAssertActivityState(APP_A_BACKGROUND_ACTIVITY, STATE_INITIALIZING,
                "First activity should have been created");
    }

    @Test
    public void testPendingIntentActivityBlocked() throws Exception {
        // Cannot start activity by pending intent, as both appA and appB are in background
        Intent intent = new Intent();
        intent.setComponent(APP_A_SEND_PENDING_INTENT_RECEIVER);
        mContext.sendBroadcast(intent);
        boolean result = waitForActivity(APP_A_BACKGROUND_ACTIVITY);
        assertFalse("Should not able to launch background activity", result);
    }

    @Test
    @FlakyTest(bugId = 130800326)
    public void testPendingIntentActivityNotBlocked_appAIsForeground() throws Exception {
        // Start AppA foreground activity
        Intent intent = new Intent();
        intent.setComponent(APP_A_FOREGROUND_ACTIVITY);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mContext.startActivity(intent);
        boolean result = waitForActivity(APP_A_FOREGROUND_ACTIVITY);
        assertTrue("Not able to start foreground Activity", result);

        // Send pendingIntent from AppA to AppB, and the AppB launch the pending intent to start
        // activity in App A
        intent = new Intent();
        intent.setComponent(APP_A_SEND_PENDING_INTENT_RECEIVER);
        mContext.sendBroadcast(intent);
        result = waitForActivity(APP_A_BACKGROUND_ACTIVITY);
        assertTrue("Not able to launch background activity", result);
    }

    @Test
    public void testPendingIntentBroadcastActivityNotBlocked_appBIsForeground() throws Exception {
        // Start AppB foreground activity
        Intent intent = new Intent();
        intent.setComponent(APP_B_FOREGROUND_ACTIVITY);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mContext.startActivity(intent);
        boolean result = waitForActivity(APP_B_FOREGROUND_ACTIVITY);
        assertTrue("Not able to start foreground Activity", result);

        // Send pendingIntent from AppA to AppB, and the AppB launch the pending intent to start
        // activity in App A
        intent = new Intent();
        intent.setComponent(APP_A_SEND_PENDING_INTENT_RECEIVER);
        mContext.sendBroadcast(intent);
        result = waitForActivity(APP_A_BACKGROUND_ACTIVITY);
        assertTrue("Not able to launch background activity", result);
    }

    // Return true if the activity is shown within a reasonable time.
    private boolean waitForActivity(ComponentName componentName) {
        mAmWmState.waitForActivityState(componentName, STATE_RESUMED);
        return getActivityName(componentName).equals(mAmWmState.getAmState().getFocusedActivity());
    }
}
