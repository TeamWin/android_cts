/*
 * Copyright (C) 2022 The Android Open Source Project
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
package android.app.cts.shortfgstesthelper;

import static android.app.cts.shortfgstesthelper.ShortFgsHelper.EXTRA_MESSAGE;
import static android.app.cts.shortfgstesthelper.ShortFgsHelper.HELPER_PACKAGE;
import static android.app.cts.shortfgstesthelper.ShortFgsHelper.TAG;
import static android.app.cts.shortfgstesthelper.ShortFgsHelper.sContext;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import java.util.Objects;

/**
 * This class receives a message from the main test package and run a command.
 */
public class ShortFgsMessageReceiver extends BroadcastReceiver {
    /**
     * Send a ShortFgsMessage to this receiver.
     */
    public static void sendMessage(ShortFgsMessage m) {
        Intent i = new Intent();
        i.setComponent(new ComponentName(HELPER_PACKAGE, ShortFgsMessageReceiver.class.getName()));
        i.setAction(Intent.ACTION_VIEW);
        i.putExtra(EXTRA_MESSAGE, m);
        i.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);

        sContext.sendBroadcast(i);
        Log.i(TAG, "ShortFgsMessageReceiver.sendMessage: intent=" + i + " message=" + m);
    }

    @Override
    public void onReceive(Context context, Intent i) {
        ShortFgsMessage m = Objects.requireNonNull(
                i.getParcelableExtra(EXTRA_MESSAGE, ShortFgsMessage.class));
        Log.i(TAG, "ShortFgsMessageReceiver.onReceive: intent=" + i + " message=" + m);

        // Not used yet...
    }
}
