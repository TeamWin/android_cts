/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.suspendapps.cts;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.util.Log;

import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;

/**
 * Wrapper around {@link BroadcastReceiver} to listen for communication from the test app.
 */
public class AppCommunicationReceiver extends BroadcastReceiver {
    private static final String TAG = AppCommunicationReceiver.class.getSimpleName();
    private Context mContext;
    private boolean mRegistered;
    private SynchronousQueue<Intent> mIntentQueue = new SynchronousQueue<>();

    AppCommunicationReceiver(Context context) {
        this.mContext = context;
    }

    void register(Handler handler, String... actions) {
        mRegistered = true;
        final IntentFilter intentFilter = new IntentFilter();
        for (String action : actions) {
            intentFilter.addAction(action);
        }
        mContext.registerReceiver(this, intentFilter, null, handler);
    }

    void unregister() {
        if (mRegistered) {
            mRegistered = false;
            mContext.unregisterReceiver(this);
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "AppCommunicationReceiver#onReceive: " + intent.getAction());
        try {
            mIntentQueue.offer(intent, 5, TimeUnit.SECONDS);
        } catch (InterruptedException ie) {
            throw new RuntimeException("Receiver thread interrupted", ie);
        }
    }

    Intent pollForIntent(long secondsToWait) {
        if (!mRegistered) {
            throw new IllegalStateException("Receiver not registered");
        }
        final Intent intent;
        try {
            intent = mIntentQueue.poll(secondsToWait, TimeUnit.SECONDS);
        } catch (InterruptedException ie) {
            throw new RuntimeException("Interrupted while waiting for app broadcast", ie);
        }
        return intent;
    }

    void drainPendingBroadcasts() {
        while (pollForIntent(5) != null) {
            // Repeat until no incoming intent.
        }
    }
}
