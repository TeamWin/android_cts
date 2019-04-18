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

package android.server.wm.backgroundactivity.appa;

import static android.server.wm.backgroundactivity.appa.Components.APP_A_BACKGROUND_ACTIVITY;
import static android.server.wm.backgroundactivity.appb.Components.APP_B_START_PENDING_INTENT_RECEIVER;
import static android.server.wm.backgroundactivity.appb.Components.StartPendingIntentReceiver.PENDING_INTENT_EXTRA;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Receive broadcast command to create a pendingIntent and send it to AppB.
 */
public class SendPendingIntentReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent notUsed) {

        // Create a pendingIntent to launch appA's BackgroundActivity
        Intent newIntent = new Intent();
        newIntent.setComponent(APP_A_BACKGROUND_ACTIVITY);
        newIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0,
                newIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        // Send the pendingIntent to appB
        Intent intent = new Intent();
        intent.setComponent(APP_B_START_PENDING_INTENT_RECEIVER);
        intent.putExtra(PENDING_INTENT_EXTRA, pendingIntent);
        context.sendBroadcast(intent);
    }
}
