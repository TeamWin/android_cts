/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.uidmigration.cts;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Process;

public class UpdateReceiver extends BroadcastReceiver {

    private static final String CTS_TEST_PKG = "android.uidmigration.cts";
    private static final String ACTION_COUNTDOWN = "android.uidmigration.cts.ACTION_COUNTDOWN";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (!Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            return;
        }

        // Notify the tester app
        Intent i = new Intent(ACTION_COUNTDOWN);
        i.setPackage(CTS_TEST_PKG);
        i.putExtra(Intent.EXTRA_UID, Process.myUid());
        context.sendBroadcast(i);
    }
}
