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

import static android.adpf.common.ADPFHintSessionConstants.TEST_NAME_KEY;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.PerformanceHintManager;
import android.util.Log;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * A simple activity to create and use hint session APIs.
 */
public class ADPFHintSessionDeviceActivity extends Activity {
    public static class Result {
        // if the activity runs successfully
        boolean mIsSuccess = true;
        // the error message if it fails to run
        String mErrMsg;
    }

    private static final String TAG =
            android.adpf.hintsession.app.ADPFHintSessionDeviceActivity.class.getSimpleName();

    private PerformanceHintManager.Session mSession;

    private final Map<String, String> mMetrics = new HashMap<>();

    private final Result mResult = new Result();

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        Intent intent = getIntent();
        String testName = intent.getStringExtra(
                TEST_NAME_KEY);
        if (testName == null) {
            synchronized (mResult) {
                mResult.mIsSuccess = false;
                mResult.mErrMsg = "test starts without name";
            }
            return;
        }

        if (mSession == null) {
            PerformanceHintManager hintManager = getApplicationContext().getSystemService(
                    PerformanceHintManager.class);
            long preferredRate = hintManager.getPreferredUpdateRateNanos();
            synchronized (mMetrics) {
                mMetrics.put("isHintSessionSupported", preferredRate < 0 ? "false" : "true");
                mMetrics.put("preferredRate", String.valueOf(preferredRate));
            }
            if (hintManager.getPreferredUpdateRateNanos() < 0) {
                Log.i(TAG, "Skipping the test as the hint session is not supported");
                return;
            }
            final long id = android.os.Process.myTid();
            mSession = hintManager.createHintSession(new int[]{(int) id},
                    TimeUnit.MILLISECONDS.toNanos(10));
            if (mSession == null) {
                synchronized (mResult) {
                    mResult.mIsSuccess = false;
                    mResult.mErrMsg = "failed to create hint session";
                }
                return;
            }
        }
        final long targetMillis = 10;
        mSession.updateTargetWorkDuration(TimeUnit.MILLISECONDS.toNanos(targetMillis));
        final long actualMillis = 5;
        mSession.reportActualWorkDuration(TimeUnit.MILLISECONDS.toNanos(actualMillis));
    }

    @Override
    public void onStart() {
        super.onStart();
    }

    /**
     * Gets the hint session test metrics.
     */
    public Map<String, String> getMetrics() {
        synchronized (mMetrics) {
            return new HashMap<>(mMetrics);
        }
    }

    /**
     * Gets the hint session test result.
     */
    public Result getResult() {
        Result res = new Result();
        synchronized (mResult) {
            res.mIsSuccess = mResult.mIsSuccess;
            res.mErrMsg = mResult.mErrMsg;
        }
        return res;
    }

}
