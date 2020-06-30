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

package com.android.cts.install.lib;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageInstaller;
import android.os.SystemClock;
import android.util.Log;
import android.util.SparseArray;

import androidx.test.InstrumentationRegistry;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Helper for making IntentSenders whose results are sent back to the test
 * app.
 */
public class LocalIntentSender extends BroadcastReceiver {
    private static final String TAG = "cts.install.lib";
    private static final String EXTRA_REQUEST_ID = LocalIntentSender.class.getName() + ".ID";
    // Access to this member must be synchronized because it is used by multiple threads
    private static final SparseArray<BlockingQueue<Intent>> sResults = new SparseArray<>();

    // Generate a unique id to ensure each LocalIntentSender gets its own results.
    private final int mRequestId = (int) SystemClock.elapsedRealtime();

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.i(TAG, "Received intent " + prettyPrint(intent));
        int id = intent.getIntExtra(EXTRA_REQUEST_ID, 0);
        BlockingQueue<Intent> queue = getQueue(id);
        // queue will be null if this broadcast comes from the session staged in previous tests
        if (queue != null) {
            queue.add(intent);
        }
    }

    /**
     * Get a LocalIntentSender.
     */
    public IntentSender getIntentSender() {
        addQueue(mRequestId);
        Context context = InstrumentationRegistry.getContext();
        Intent intent = new Intent(context, LocalIntentSender.class);
        intent.putExtra(EXTRA_REQUEST_ID, mRequestId);
        PendingIntent pending = PendingIntent.getBroadcast(context, mRequestId, intent, 0);
        return pending.getIntentSender();
    }

    /**
     * Returns and remove the most early Intent received by this LocalIntentSender.
     */
    public Intent getResult() throws InterruptedException {
        Intent intent = getQueue(mRequestId).take();
        Log.i(TAG, "Taking intent " + prettyPrint(intent));
        return intent;
    }

    /**
     * Returns the most recent Intent sent by a LocalIntentSender.
     * TODO(b/136260017): To be removed when all callers are cleaned up.
     */
    public static Intent getIntentSenderResult() throws InterruptedException {
        return null;
    }

    private static BlockingQueue<Intent> getQueue(int requestId) {
        synchronized (sResults) {
            return sResults.get(requestId);
        }
    }

    private static void addQueue(int requestId) {
        synchronized (sResults) {
            sResults.append(requestId, new LinkedBlockingQueue<>());
        }
    }

    private static String prettyPrint(Intent intent) {
        int sessionId = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1);
        int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS,
                PackageInstaller.STATUS_FAILURE);
        int id = intent.getIntExtra(EXTRA_REQUEST_ID, 0);
        String message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
        return String.format("%s: {\n"
                + "requestId = %d\n"
                + "sessionId = %d\n"
                + "status = %d\n"
                + "message = %s\n"
                + "}", intent, id, sessionId, status, message);
    }
}
