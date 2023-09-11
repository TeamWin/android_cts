/*
 * Copyright (C) 2023 The Android Open Source Project
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

package android.adpf.hintsession.app;

import static android.adpf.common.ADPFHintSessionConstants.LOG_ACTUAL_DURATION_PREFIX;
import static android.adpf.common.ADPFHintSessionConstants.LOG_TARGET_DURATION_PREFFIX;
import static android.adpf.common.ADPFHintSessionConstants.LOG_TEST_APP_FAILED_PREFIX;
import static android.adpf.common.ADPFHintSessionConstants.TEST_NAME_KEY;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.PerformanceHintManager;
import android.util.Log;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

/**
 * A simple activity which logs to Logcat.
 */
public class ADPFHintSessionDeviceActivity extends Activity {
    private static final String TAG =
            android.adpf.hintsession.app.ADPFHintSessionDeviceActivity.class.getSimpleName();

    private PerformanceHintManager.Session mSession;

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        Intent intent = getIntent();
        String testName = intent.getStringExtra(
                TEST_NAME_KEY);
        if (testName == null) {
            Log.e(TAG, LOG_TEST_APP_FAILED_PREFIX + "test starts without name");
            return;
        }
        Log.i(TAG, "Test name " + testName);

        if (mSession == null) {
            PerformanceHintManager hintManager = getApplicationContext().getSystemService(
                    PerformanceHintManager.class);
            final long id = android.os.Process.myTid();
            mSession = hintManager.createHintSession(new int[]{(int) id},
                    TimeUnit.MILLISECONDS.toNanos(10));
            if (mSession == null) {
                Log.e(TAG, "Failed to create hint session");
                return;
            }
        }
        final int targetMillis = 5;
        // mSession.reportActualWorkDuration(TimeUnit.MILLISECONDS.toNanos(targetMillis));
        Log.i(TAG, LOG_TARGET_DURATION_PREFFIX + targetMillis);
        Log.i(TAG, LOG_ACTUAL_DURATION_PREFIX + Arrays.asList(1, 2, 3, 4));
    }

    @Override
    public void onStart() {
        super.onStart();
    }

}
