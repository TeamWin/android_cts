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
package android.content.pm.cts.shortcutmanager.throttling;

import static com.android.server.pm.shortcutmanagertest.ShortcutManagerTestUtils.list;

import static junit.framework.Assert.assertTrue;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ShortcutManager;
import android.content.pm.cts.shortcutmanager.common.Constants;
import android.os.Bundle;
import android.util.Log;

import java.util.function.BooleanSupplier;

/**
 * Throttling test case.
 *
 * If we run it as a regular instrumentation test, the process would always considered to be in the
 * foreground and will never be throttled, so we use a broadcast to communicate from the
 * main test apk.
 */
public class ShortcutManagerThrottlingTestReceiver extends BroadcastReceiver {
    private ShortcutManager mManager;

    public ShortcutManager getManager(Context context) {
        if (mManager == null) {
            mManager = context.getSystemService(ShortcutManager.class);
        }
        return mManager;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Constants.ACTION_THROTTLING_TEST.equals(intent.getAction())) {
            boolean success = false;
            String error = null;
            try {
                final String method = intent.getStringExtra(Constants.EXTRA_METHOD);
                switch (method) {
                    case Constants.TEST_SET_DYNAMIC_SHORTCUTS:
                        testSetDynamicShortcuts(context);
                        break;
                    case Constants.TEST_ADD_DYNAMIC_SHORTCUTS:
                        testAddDynamicShortcuts(context);
                        break;
                    case Constants.TEST_UPDATE_SHORTCUTS:
                        testUpdateShortcuts(context);
                        break;
                    default:
                        fail("Unknown test: " + method);
                }

                success = true;
            } catch (Throwable e) {
                error = "Test failed: " + e.getMessage() + "\n" + Log.getStackTraceString(e);
            }

            // Create the reply bundle.
            final Bundle ret = new Bundle();
            if (success) {
                ret.putBoolean("success", true);
            } else {
                ret.putString("error", error);
            }

            // Send reply
            final Intent reply = new Intent(intent.getStringExtra(Constants.EXTRA_REPLY_ACTION));
            reply.putExtras(ret);

            context.sendBroadcast(reply);
        }
    }

    private void assertThrottled(Context context, BooleanSupplier apiCall) {
        assertFalse("Throttling must be reset here", getManager(context).isRateLimitingActive());

        assertTrue("First call should succeed", apiCall.getAsBoolean());

        // App can make 10 API calls between the interval, but there's a chance that the throttling
        // gets reset within this loop, so we make 20 calls.
        boolean throttled = false;
        for (int i = 0; i < 19; i++) {
            if (!apiCall.getAsBoolean()) {
                throttled = true;
                break;
            }
        }
        assertTrue("API call not throttled", throttled);
    }

    public void testSetDynamicShortcuts(Context context) {
        assertThrottled(context, () -> getManager(context).setDynamicShortcuts(list()));
    }

    public void testAddDynamicShortcuts(Context context) {
        assertThrottled(context, () -> getManager(context).addDynamicShortcuts(list()));
    }

    public void testUpdateShortcuts(Context context) {
        assertThrottled(context, () -> getManager(context).updateShortcuts(list()));
    }
}
