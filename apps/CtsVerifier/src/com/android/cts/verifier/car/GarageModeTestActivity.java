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

package com.android.cts.verifier.car;

import android.app.UiModeManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;

import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;

import java.text.DateFormat;

/**
 * Tests that Garage Mode runs at the end of a drive
 */
public final class GarageModeTestActivity extends PassFailButtons.Activity {
    // Requirements from Android 10 Compatibility Definition
    // 2.5.4. Performance and Power
    // Automotive device implementations:
    //
    // [8.3/A-1-3] MUST enter Garage Mode at least once before the car is powered down.
    // [8.3/A-1-4] MUST be in Garage Mode for at least 15 minutes unless:
    //                 The battery is drained.
    //                 No idle jobs are scheduled.
    //                 The driver exits Garage Mode.
    //
    // As updated:
    // [8.3/A-1-3] MUST support Garage Mode functionality
    // [8.3/A-1-4] SHOULD be in Garage Mode for at least 15 minutes after every drive unless:
    //                 The battery is drained
    //                 No idle jobs are scheduled
    //                 The driver exits Garage Mode

    private static final String TAG = GarageModeTestActivity.class.getSimpleName();

    // The recommendation is for Garage Mode to run for 15 minutes. To allow
    // for some variation, run the test for 14 minutes and verify that it
    // ran at least 13 minutes.
    private static final int NUM_SECONDS_DURATION = 14 * 60;
    private static final long DURATION_MINIMUM_TEST_MS = (NUM_SECONDS_DURATION - 60) * 1000L;

    private TextView mStatusText;
    private boolean mJustLaunchedVerifier;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        View view = getLayoutInflater().inflate(R.layout.garage_test_main, null);
        setContentView(view);

        setInfoResources(R.string.car_garage_mode_test, R.string.car_garage_mode_test_desc, -1);
        setPassFailButtonClickListeners();
        getPassButton().setEnabled(false);
        mStatusText = findViewById(R.id.car_garage_mode_results);

        Button wifiButton = view.findViewById(R.id.car_wifi_settings);
        wifiButton.setOnClickListener(
                (buttonView) -> startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS)));

        Button monitorButton = (Button) view.findViewById(R.id.launch_garage_monitor);
        monitorButton.setOnClickListener((buttonView) -> {
            Context context = GarageModeTestActivity.this;
            SharedPreferences prefs = context.getSharedPreferences(
                    GarageModeChecker.PREFS_FILE_NAME, Context.MODE_PRIVATE);
            long now = System.currentTimeMillis();
            SharedPreferences.Editor editor = prefs.edit();
            editor.putLong(GarageModeChecker.PREFS_INITIATION, now);
            editor.putLong(GarageModeChecker.PREFS_GARAGE_MODE_START, 0);
            editor.putLong(GarageModeChecker.PREFS_GARAGE_MODE_END, 0);
            editor.putLong(GarageModeChecker.PREFS_TERMINATION, 0);
            editor.commit();

            GarageModeChecker.scheduleAnIdleJob(context, NUM_SECONDS_DURATION);
            mJustLaunchedVerifier = true;
            verifyStatus();
            Log.v(TAG, "Scheduled GarageModeChecker to run when idle");
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        verifyStatus();
    }

    private void verifyStatus() {
        Context context = GarageModeTestActivity.this;
        SharedPreferences prefs = context.getSharedPreferences(
                GarageModeChecker.PREFS_FILE_NAME, Context.MODE_PRIVATE);
        String resultsString;
        DateFormat dateTime = DateFormat.getDateTimeInstance();

        long initiateTime = prefs.getLong(GarageModeChecker.PREFS_INITIATION, 0);
        long garageModeStart = prefs.getLong(GarageModeChecker.PREFS_GARAGE_MODE_START, 0);
        long garageModeEnd = prefs.getLong(GarageModeChecker.PREFS_GARAGE_MODE_END, 0);
        long termination = prefs.getLong(GarageModeChecker.PREFS_TERMINATION, 0);

        boolean testPassed = false;
        if (initiateTime == 0) {
            resultsString = "No results are available.\n\n"
                    + "Perform the indicated steps to run the test.";
        } else if (mJustLaunchedVerifier) {
            resultsString = String.format("No results are available.\n\n"
                            + "Perform the indicated steps to run the test.\n\n"
                            + "%s -- Test was enabled",
                    dateTime.format(initiateTime));
            mJustLaunchedVerifier = false;
        } else if (garageModeStart == 0) {
            resultsString = String.format("Test failed.\n"
                            + "Garage Mode did not run.\n\n"
                            + "%s -- Test was enabled",
                    dateTime.format(initiateTime));
        } else if (garageModeEnd > (garageModeStart + DURATION_MINIMUM_TEST_MS)) {
            resultsString = String.format("Test Passed.\n"
                            + "Garage Mode ran as required and for the recommended time.\n\n"
                            + "%s -- Test was enabled\n"
                            + "%s -- Garage mode started\n"
                            + "%s -- Garage mode completed",
                    dateTime.format(initiateTime), dateTime.format(garageModeStart),
                    dateTime.format(garageModeEnd));
            testPassed = true;
        } else if (termination > 0) {
            resultsString = String.format("Test Passed.\n"
                            + "Garage Mode ran as required, "
                            + "but for less time than is recommended.\n"
                            + "The CDD recommends that Garage Mode runs for 15 minutes.\n\n"
                            + "%s -- Test was enabled\n"
                            + "%s -- Garage mode started\n"
                            + "%s -- Garage mode was terminated",
                    dateTime.format(initiateTime), dateTime.format(garageModeStart),
                    dateTime.format(termination));
            testPassed = true;
        } else {
            resultsString = "Test failed.\n\n"
                    + "Garage Mode started, but terminated unexpectedly.\n\n"
                    + dateTime.format(initiateTime) + " -- Test was enabled\n"
                    + dateTime.format(garageModeStart) + " -- Garage mode started";
        }
        mStatusText.setText(resultsString);
        getPassButton().setEnabled(testPassed);
    }
}
