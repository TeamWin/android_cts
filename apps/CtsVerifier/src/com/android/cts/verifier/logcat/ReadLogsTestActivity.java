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

package com.android.cts.verifier.logcat;

import static org.junit.Assert.assertTrue;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;


/**
 * A read_logs CTS Verifier test case for testing user consent for log data access.
 *
 * This test generates a per-use prompt when the requester is foreground.
 * If the requester is background, the log data access is denied.
 */
public class ReadLogsTestActivity extends PassFailButtons.Activity {

    /**
     * The name of the APP to test
     */
    private static final String TAG = "ReadLogsTestActivity";

    private static final String PERMISSION = "android.permission.READ_LOGS";

    private static final int NUM_OF_LINES = 10;

    private static Context sContext;
    private static ActivityManager sActivityManager;

    private static String sAppPackageName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        sContext = this;
        sActivityManager = sContext.getSystemService(ActivityManager.class);
        sAppPackageName = sContext.getPackageName();

        // Setup the UI.
        setContentView(R.layout.logcat_read_logs);
        setPassFailButtonClickListeners();
        setInfoResources(R.string.read_logs_text, R.string.read_logs_test_info, -1);

        // Get the run test button and attach the listener.
        Button runBtn = (Button) findViewById(R.id.run_read_logs_btn);
        runBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    // Dump the logcat most recent 10 lines before the compile command,
                    // and check if there are logs about compiling the test package.
                    File logcatFile = File.createTempFile("logcat", ".txt");
                    logcatFile.deleteOnExit();
                    ProcessBuilder pb = new ProcessBuilder(Arrays.asList("logcat", "-b", "system",
                            "-t", "10"));
                    pb.redirectOutput(logcatFile);
                    Process proc = pb.start();
                    proc.waitFor();

                    List<String> logcat = Files.readAllLines(logcatFile.toPath());
                    int numOfLines = logcat.size();
                    assertTrue("Number of lines is equal to 10",
                            numOfLines == NUM_OF_LINES);
                } catch (Exception e) {
                    Log.e(TAG, "User Consent Testing failed");
                }
            }
        });

    }
}
