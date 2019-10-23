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
 * limitations under the License.
 */

package android.server.am;

import static android.app.WindowConfiguration.ACTIVITY_TYPE_ASSISTANT;
import static android.app.WindowConfiguration.ACTIVITY_TYPE_RECENTS;
import static android.app.WindowConfiguration.ACTIVITY_TYPE_STANDARD;
import static android.app.WindowConfiguration.ACTIVITY_TYPE_UNDEFINED;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import static androidx.test.InstrumentationRegistry.getInstrumentation;

import android.content.ComponentName;
import android.os.SystemClock;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.ConditionVariable;
import android.os.Handler;
import android.os.Looper;
import android.platform.test.annotations.Presubmit;
import android.server.am.WindowManagerState.WindowState;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.WindowManager.LayoutParams;
import com.android.compatibility.common.util.CtsTouchUtils;
import android.graphics.Rect;

import com.android.compatibility.common.util.SystemUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nullable;

import androidx.test.platform.app.InstrumentationRegistry;

@Presubmit
public class ToastTest extends ActivityManagerTestBase {
    private static final String SETTING_HIDDEN_API_POLICY = "hidden_api_policy";
    private static final long TOAST_DISPLAY_TIMEOUT_MS = 8000;
    private static final long TOAST_TAP_TIMEOUT_MS = 3500;

    protected static final int[] ALL_ACTIVITY_TYPE_BUT_HOME = {
            ACTIVITY_TYPE_STANDARD, ACTIVITY_TYPE_ASSISTANT, ACTIVITY_TYPE_RECENTS,
            ACTIVITY_TYPE_UNDEFINED
    };

    private static final String ACTION_TOAST_DISPLAYED = "toast_displayed";
    private static final String ACTION_TOAST_TAP_DETECTED = "toast_tap_detected";
    private static final String APP_PACKAGE = "android.server.am";
    private static final ComponentName TOAST_RECEIVER =
            ComponentName.createRelative(APP_PACKAGE, ".ToastReceiver");

    private Context mContext;
    private ActivityManager mAm;

    /**
     * Tests can be executed as soon as the device has booted. When that happens the broadcast queue
     * is long and it takes some time to process the broadcast we just sent.
     */
    private static final long BROADCAST_DELIVERY_TIMEOUT_MS = 60000;

    @Nullable
    private String mPreviousHiddenApiPolicy;
    private Map<String, ConditionVariable> mBroadcastsReceived;

    private BroadcastReceiver mAppCommunicator = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
             getBroadcastReceivedVariable(intent.getAction()).open();
        }
    };

    @Before
    public void setUp() throws Exception {
        mContext = getInstrumentation().getContext();
        mAm = mContext.getSystemService(ActivityManager.class);

        mPreviousHiddenApiPolicy = executeShellCommand(
                "settings get global hidden_api_policy_p_apps");
        executeShellCommand("settings put global hidden_api_policy_p_apps 0");
        // Stopping just in case, to make sure reflection is allowed
        stopTestPackage(TOAST_RECEIVER);

        // These are parallel broadcasts, not affected by a busy queue
        mBroadcastsReceived = Collections.synchronizedMap(new HashMap<>());
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_TOAST_DISPLAYED);
        filter.addAction(ACTION_TOAST_TAP_DETECTED);
        mContext.registerReceiver(mAppCommunicator, filter);
    }

    @After
    public void tearDown() throws Exception {
        mContext.unregisterReceiver(mAppCommunicator);
        executeShellCommand("settings put global hidden_api_policy_p_apps " + mPreviousHiddenApiPolicy);
    }

    @Test
    public void testToastIsNotClickable() {
        Intent intent = new Intent();
        intent.setComponent(TOAST_RECEIVER);
        sendAndWaitForBroadcast(intent);
        boolean toastDisplayed = getBroadcastReceivedVariable(ACTION_TOAST_DISPLAYED).block(
                TOAST_DISPLAY_TIMEOUT_MS);
        assertTrue("Toast not displayed on time", toastDisplayed);
        WindowManagerState wmState = mAmWmState.getWmState();
        wmState.computeState();
        WindowState toastWindow = wmState.findFirstWindowWithType(LayoutParams.TYPE_TOAST);
        assertNotNull("Couldn't retrieve toast window", toastWindow);

        tapOnCenter(toastWindow.getContainingFrame(), toastWindow.getDisplayId());

        boolean toastClicked = getBroadcastReceivedVariable(ACTION_TOAST_TAP_DETECTED).block(
                TOAST_TAP_TIMEOUT_MS);
        assertFalse("Toast tap detected", toastClicked);
    }

    private void tapOnCenter(Rect bounds, int displayId) {
        final int x = bounds.left + bounds.width() / 2;
        final int y = bounds.top + bounds.height() / 2;
        long downTime = SystemClock.uptimeMillis();
        injectMotion(downTime, downTime, MotionEvent.ACTION_DOWN, x, y);
        long upTime = SystemClock.uptimeMillis();
        injectMotion(downTime, upTime, MotionEvent.ACTION_DOWN, x, y);
    }

    private static void injectMotion(long downTime, long eventTime, int action, int x, int y) {
        final MotionEvent event = MotionEvent.obtain(downTime, eventTime, action, x, y, 0);
        event.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        InstrumentationRegistry.getInstrumentation().getUiAutomation().injectInputEvent(event, true);
    }

    private void sendAndWaitForBroadcast(Intent intent) {
        assertNotEquals("Can't wait on main thread", Thread.currentThread(),
                Looper.getMainLooper().getThread());

        ConditionVariable broadcastDelivered = new ConditionVariable(false);
        mContext.sendOrderedBroadcast(
                intent,
                null,
                new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        broadcastDelivered.open();
                    }
                },
                new Handler(Looper.getMainLooper()),
                Activity.RESULT_OK,
                null,
                null);
        broadcastDelivered.block(BROADCAST_DELIVERY_TIMEOUT_MS);
    }

    private ConditionVariable getBroadcastReceivedVariable(String action) {
        return mBroadcastsReceived.computeIfAbsent(action, key -> new ConditionVariable());
    }
}
