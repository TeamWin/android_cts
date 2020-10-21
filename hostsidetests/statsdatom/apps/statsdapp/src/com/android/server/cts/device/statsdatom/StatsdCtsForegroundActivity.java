/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.server.cts.device.statsdatom;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationChannelGroup;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;

/** An activity (to be run as a foreground process) which performs one of a number of actions. */
public class StatsdCtsForegroundActivity extends Activity {
    private static final String TAG = StatsdCtsForegroundActivity.class.getSimpleName();

    public static final String KEY_ACTION = "action";
    public static final String ACTION_CRASH = "action.crash";
    public static final String ACTION_SLEEP_WHILE_TOP = "action.sleep_top";
    public static final String ACTION_SHOW_NOTIFICATION = "action.show_notification";

    public static final int SLEEP_OF_ACTION_SLEEP_WHILE_TOP = 2_000;

    @Override
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);

        Intent intent = getIntent();
        if (intent == null) {
            Log.e(TAG, "Intent was null.");
            finish();
        }

        String action = intent.getStringExtra(KEY_ACTION);
        Log.i(TAG, "Starting " + action + " from foreground activity.");

        switch (action) {
            case ACTION_SHOW_NOTIFICATION:
                doShowNotification();
                break;
            case ACTION_CRASH:
                doCrash();
                break;
            case ACTION_SLEEP_WHILE_TOP:
                doSleepWhileTop(SLEEP_OF_ACTION_SLEEP_WHILE_TOP);
                break;
            default:
                Log.e(TAG, "Intent had invalid action " + action);
                finish();
        }
    }

    /** Does nothing, but asynchronously. */
    private void doSleepWhileTop(int sleepTime) {
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                AtomTests.sleep(sleepTime);
                return null;
            }

            @Override
            protected void onPostExecute(Void nothing) {
                finish();
            }
        }.execute();
    }

    private void doShowNotification() {
        final int notificationId = R.layout.activity_main;
        final String notificationChannelId = "StatsdCtsChannel";

        NotificationManager nm = getSystemService(NotificationManager.class);
        NotificationChannel channel = new NotificationChannel(notificationChannelId, "Statsd Cts",
                NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription("Statsd Cts Channel");
        nm.createNotificationChannel(channel);

        nm.notify(
                notificationId,
                new Notification.Builder(this, notificationChannelId)
                        .setSmallIcon(android.R.drawable.stat_notify_chat)
                        .setContentTitle("StatsdCts")
                        .setContentText("StatsdCts")
                        .build());
        nm.cancel(notificationId);
        finish();
    }

    @SuppressWarnings("ConstantOverflow")
    private void doCrash() {
        Log.e(TAG, "About to crash the app with 1/0 " + (long) 1 / 0);
    }
}
