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
package android.content.pm.cts.shortcutmanager;


import static android.content.pm.cts.shortcutmanager.common.Constants.INLINE_REPLY_REMOTE_INPUT_CAPTION;

import static com.android.server.pm.shortcutmanagertest.ShortcutManagerTestUtils.resetThrottling;
import static com.android.server.pm.shortcutmanagertest.ShortcutManagerTestUtils.retryUntil;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.cts.shortcutmanager.common.Constants;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.UiObject2;
import android.support.test.uiautomator.Until;
import android.test.suitebuilder.annotation.SmallTest;
import android.view.KeyEvent;

import java.util.concurrent.atomic.AtomicReference;

/**
 * The actual test is implemented in the CtsShortcutManagerThrottlingTest module.
 * This class uses broadcast receivers to communicate with it, because if we just used an
 * instrumentation test, the target process would never been throttled.
 */
@SmallTest
public class ShortcutManagerThrottlingTest extends ShortcutManagerCtsTestsBase {

    private static final int UI_TIMEOUT = 5000;

    private void callTest(String method) {

        final AtomicReference<Intent> ret = new AtomicReference<>();

        // Register the reply receiver

        // Use a random reply action every time.
        final String replyAction = Constants.ACTION_THROTTLING_REPLY + sRandom.nextLong();
        final IntentFilter filter = new IntentFilter(replyAction);

        final BroadcastReceiver r = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                ret.set(intent);
            }
        };

        getTestContext().registerReceiver(r, filter);

        try {
            // Send the request broadcast.

            final Intent i = new Intent(Constants.ACTION_THROTTLING_TEST);
            i.putExtra(Constants.EXTRA_METHOD, method);
            i.putExtra(Constants.EXTRA_REPLY_ACTION, replyAction);
            i.setComponent(ComponentName.unflattenFromString(
                    "android.content.pm.cts.shortcutmanager.throttling/"
                            + ".ShortcutManagerThrottlingTestReceiver"
                    ));
            getTestContext().sendBroadcast(i);

            // Wait for the response.
            retryUntil(() -> ret.get() != null, "Didn't receiver result broadcast");

            if (ret.get().getExtras().getBoolean("success")) {
                return;
            }
            fail(ret.get().getExtras().getString("error"));
        } finally {
            getTestContext().unregisterReceiver(r);
        }
    }

    public void testThrottling() throws InterruptedException {
        resetThrottling(getInstrumentation());

        callTest(Constants.TEST_SET_DYNAMIC_SHORTCUTS);

        // --------------------------------------
        resetThrottling(getInstrumentation());

        callTest(Constants.TEST_ADD_DYNAMIC_SHORTCUTS);

        // --------------------------------------
        resetThrottling(getInstrumentation());

        callTest(Constants.TEST_UPDATE_SHORTCUTS);

        // --------------------------------------
        resetThrottling(getInstrumentation());

        callTest(Constants.TEST_BG_SERVICE_THROTTLED);

        // --------------------------------------
        resetThrottling(getInstrumentation());

        callTest(Constants.TEST_ACTIVITY_UNTHROTTLED);

        // --------------------------------------
        resetThrottling(getInstrumentation());

        callTest(Constants.TEST_FG_SERVICE_UNTHROTTLED);
    }

    public void testInlineReply() throws Exception {
        resetThrottling(getInstrumentation());

        clearNotifications();

        callTest(Constants.TEST_INLINE_REPLY_SHOW);

        performInlineReply();

        callTest(Constants.TEST_INLINE_REPLY_CHECK);
    }

    private void clearNotifications() throws InterruptedException {
        final UiDevice ud = UiDevice.getInstance(getInstrumentation());
        ud.openNotification();

        final UiObject2 clearAll = ud.wait(Until.findObject(By.text("CLEAR ALL")), UI_TIMEOUT);

        // Just skip if not found.
        if (clearAll != null) {
            clearAll.clear();
            Thread.sleep(1000);
        }
        ud.pressHome();
        Thread.sleep(1000);
    }

    private void performInlineReply() throws InterruptedException {
        final UiDevice ud = UiDevice.getInstance(getInstrumentation());

        Thread.sleep(1000);
        ud.openNotification();

        ud.wait(Until.findObject(By.text(INLINE_REPLY_REMOTE_INPUT_CAPTION)), UI_TIMEOUT).click();

        Thread.sleep(1000);

        ud.pressKeyCode(KeyEvent.KEYCODE_A);
        ud.pressKeyCode(KeyEvent.KEYCODE_B);
        ud.pressKeyCode(KeyEvent.KEYCODE_C);
        ud.pressEnter();

        Thread.sleep(1000);
        ud.pressHome();
    }
}
