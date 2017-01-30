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
package com.android.server.cts.procstatshelper;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.util.Log;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class FlashingActivity extends Activity {
    private static final String TAG = "FlashingActivity";

    private static Semaphore mGate = new Semaphore(1);

    @Override
    protected void onResume() {
        Log.i(TAG, "onResume");
        super.onResume();

        new Handler().postDelayed(() -> finish(), 100);
    }

    @Override
    protected void onDestroy() {
        Log.i(TAG, "onDestroy");
        super.onDestroy();

        mGate.release();
    }

    public static void flash(Context context) {
        Log.i(TAG, "flash");
        try {
            mGate.acquire();
            final Intent intent = new Intent()
                    .setComponent(new ComponentName(context, FlashingActivity.class))
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                            | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            context.startActivity(intent);

            if (!mGate.tryAcquire(10, TimeUnit.SECONDS)) {
                throw new RuntimeException("Activity didn't start.");
            }
            mGate.release();

        } catch (InterruptedException e) {
            Log.e(TAG, "InterruptedException", e);
            throw new RuntimeException(e);
        }
    }
}
