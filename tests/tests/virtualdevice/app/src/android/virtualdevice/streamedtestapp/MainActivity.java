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

package android.virtualdevice.streamedtestapp;

import android.annotation.Nullable;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.os.Bundle;
import android.os.ResultReceiver;
import android.util.Log;

/**
 * Test activity to be streamed in the virtual device.
 */
public class MainActivity extends Activity {

    private static final String TAG = "StreamedTestApp";

    /**
     * Tell this activity to call the {@link #EXTRA_ACTIVITY_LAUNCHED_RECEIVER} with
     * {@link #RESULT_OK} when it is launched.
     */
    static final String ACTION_CALL_RESULT_RECEIVER =
            "android.virtualdevice.streamedtestapp.CALL_RESULT_RECEIVER";
    /**
     * Extra in the result data that contains the integer display ID when the receiver for
     * {@link #ACTION_CALL_RESULT_RECEIVER} is called.
     */
    static final String EXTRA_DISPLAY = "display";

    /**
     * Tell this activity to test clipboard when it is launched. This will attempt to read the
     * existing string in clipboard, put that in the activity result (as
     * {@link #EXTRA_CLIPBOARD_STRING}), and add the string in {@link #EXTRA_CLIPBOARD_STRING} in
     * the intent extra to the clipboard.
     */
    static final String ACTION_TEST_CLIPBOARD =
            "android.virtualdevice.streamedtestapp.TEST_CLIPBOARD";
    static final String EXTRA_ACTIVITY_LAUNCHED_RECEIVER = "activityLaunchedReceiver";
    static final String EXTRA_CLIPBOARD_STRING = "clipboardString";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        setTitle(getClass().getSimpleName());
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void onResume() {
        super.onResume();
        String action = getIntent().getAction();
        if (action != null) {
            switch (action) {
                case ACTION_CALL_RESULT_RECEIVER:
                    Log.d(TAG, "Handling intent receiver");
                    ResultReceiver resultReceiver =
                            getIntent().getParcelableExtra(EXTRA_ACTIVITY_LAUNCHED_RECEIVER);
                    Bundle result = new Bundle();
                    result.putInt(EXTRA_DISPLAY, getDisplay().getDisplayId());
                    resultReceiver.send(Activity.RESULT_OK, result);
                    finish();
                    break;
                case ACTION_TEST_CLIPBOARD:
                    Log.d(TAG, "Testing clipboard");
                    testClipboard();
                    break;
                default:
                    Log.w(TAG, "Unknown action: " + action);
            }
        }
    }

    private void testClipboard() {
        Intent resultData = new Intent();
        ClipboardManager clipboardManager = getSystemService(ClipboardManager.class);
        resultData.putExtra(EXTRA_CLIPBOARD_STRING, clipboardManager.getPrimaryClip());

        String clipboardContent = getIntent().getStringExtra(EXTRA_CLIPBOARD_STRING);
        if (clipboardContent != null) {
            clipboardManager.setPrimaryClip(
                    new ClipData(
                            "CTS clip data",
                            new String[] { "application/text" },
                            new ClipData.Item(clipboardContent)));
            Log.d(TAG, "Wrote \"" + clipboardContent + "\" to clipboard");
        } else {
            Log.w(TAG, "Clipboard content is null");
        }

        setResult(Activity.RESULT_OK, resultData);
        finish();
    }
}

