/*
 * Copyright 2022 The Android Open Source Project
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
package com.android.app.cts.broadcasts.helper;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.IBinder;
import android.util.ArrayMap;
import android.util.Log;

import androidx.annotation.GuardedBy;

import com.android.app.cts.broadcasts.ICommandReceiver;

import java.util.ArrayList;
import java.util.List;

public class TestService extends Service {
    private static final String TAG = "TestService";

    @Override
    public IBinder onBind(Intent intent) {
        return new CommandReceiver();
    }

    private class CommandReceiver extends ICommandReceiver.Stub {
        @GuardedBy("mReceivedBroadcasts")
        private final ArrayMap<String, ArrayList<Intent>> mReceivedBroadcasts = new ArrayMap<>();

        @Override
        public void sendBroadcast(Intent intent, Bundle options) {
            TestService.this.sendBroadcast(intent, null /* receiverPermission */, options);
        }

        @Override
        public void monitorBroadcasts(IntentFilter filter, String cookie) {
            TestService.this.registerReceiver(new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    Log.d(TAG, "Received broadcast: " + intent);
                    synchronized (mReceivedBroadcasts) {
                        ArrayList<Intent> receivedBroadcasts = mReceivedBroadcasts.get(
                                cookie);
                        if (receivedBroadcasts == null) {
                            receivedBroadcasts = new ArrayList();
                            mReceivedBroadcasts.put(cookie, receivedBroadcasts);
                        }
                        receivedBroadcasts.add(intent);
                    }
                }
            }, filter);
        }

        @Override
        public List<Intent> getReceivedBroadcasts(String cookie) {
            synchronized (mReceivedBroadcasts) {
                final ArrayList<Intent> receivedBroadcasts = mReceivedBroadcasts.get(cookie);
                return receivedBroadcasts == null ? new ArrayList<>() : receivedBroadcasts;
            }
        }

        @Override
        public void clearCookie(String cookie) {
            synchronized (mReceivedBroadcasts) {
                mReceivedBroadcasts.remove(cookie);
            }
        }
    }
}

