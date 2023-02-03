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

package android.app.stubs;

import android.app.Activity;
import android.app.StatusBarManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.Nullable;

public class MockNotesActivity extends Activity {

    private static final String TAG = MockNotesActivity.class.getSimpleName();
    private static final int REQUEST_CODE = 100;

    public static final String INFORM_ACTIVITY_RESULT_RECEIVED =
            TAG + "INFORM_ACTIVITY_RESULT_RECEIVED";
    public static final String EXTRA_ACTIVITY_RESULT_DATA = TAG + "EXTRA_ACTIVITY_RESULT_DATA";
    public static final String EXTRA_ACTIVITY_RESULT_CODE = TAG + "EXTRA_ACTIVITY_RESULT_CODE";

    public static final String INFORM_ACTIVITY_CREATED = TAG + "INFORM_ACTIVITY_CREATED";

    public static final String LAUNCH_CAPTURE_CONTENT_INTENT =
            TAG + "LAUNCH_CAPTURE_CONTENT_INTENT";
    public static final String INFORM_CAPTURE_CONTENT_INTENT_FIRED =
            TAG + "INFORM_CAPTURE_CONTENT_INTENT_FIRED";

    public static final String QUERY_SUPPORT_API = TAG + "QUERY_SUPPORT_API";
    public static final String INFORM_SUPPORT_API_RESPONSE = TAG + "INFORM_SUPPORT_API_RESPONSE";
    public static final String EXTRA_SUPPORT_API_RESPONSE = TAG + "EXTRA_SUPPORT_API_RESPONSE";

    public static final String FINISH_THIS_ACTIVITY = TAG + "FINISH_THIS_ACTIVITY";

    private final BroadcastReceiver mStartIntentActionReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            startActivityForResult(
                    new Intent(Intent.ACTION_LAUNCH_CAPTURE_CONTENT_ACTIVITY_FOR_NOTE),
                    REQUEST_CODE);
            sendBroadcast(new Intent(INFORM_CAPTURE_CONTENT_INTENT_FIRED));
        }
    };

    private final BroadcastReceiver mQuerySupportActionReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            boolean canLaunch = false;
            try {
                canLaunch = context.getSystemService(
                        StatusBarManager.class).canLaunchCaptureContentActivityForNote(
                        MockNotesActivity.this);
            } catch (Exception e) {
                Log.d(TAG, "Exception querying support API\n" + e);
            }
            sendBroadcast(
                    new Intent(INFORM_SUPPORT_API_RESPONSE).putExtra(EXTRA_SUPPORT_API_RESPONSE,
                            canLaunch));
            finish();
        }
    };

    private final BroadcastReceiver mFinishThisActivityReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            finish();
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        registerReceiver(mStartIntentActionReceiver,
                new IntentFilter(LAUNCH_CAPTURE_CONTENT_INTENT), RECEIVER_EXPORTED);
        registerReceiver(mQuerySupportActionReceiver,
                new IntentFilter(QUERY_SUPPORT_API), RECEIVER_EXPORTED);
        registerReceiver(mFinishThisActivityReceiver,
                new IntentFilter(FINISH_THIS_ACTIVITY), RECEIVER_EXPORTED);

        sendBroadcast(new Intent(INFORM_ACTIVITY_CREATED));
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(mFinishThisActivityReceiver);
        unregisterReceiver(mStartIntentActionReceiver);
        unregisterReceiver(mQuerySupportActionReceiver);
        super.onDestroy();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE) {
            sendBroadcast(new Intent(INFORM_ACTIVITY_RESULT_RECEIVED)
                    .putExtra(EXTRA_ACTIVITY_RESULT_DATA, data)
                    .putExtra(EXTRA_ACTIVITY_RESULT_CODE, resultCode));
            finish();
        }
    }
}
