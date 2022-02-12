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

import static android.hardware.camera2.CameraAccessException.CAMERA_DISABLED;
import static android.hardware.camera2.CameraAccessException.CAMERA_DISCONNECTED;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
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
     * Tell this activity to test camera access when it is launched. It will get the String camera
     * id to try opening from {@link #EXTRA_CAMERA_ID}, and put the test outcome in
     * {@link #EXTRA_CAMERA_RESULT} on the activity result intent. If the result was that the
     * onError callback happened, then {@link #EXTRA_CAMERA_ON_ERROR_CODE} will contain the error
     * code.
     */
    static final String ACTION_TEST_CAMERA =
            "android.virtualdevice.streamedtestapp.TEST_CAMERA";
    static final String EXTRA_CAMERA_ID = "cameraId";
    static final String EXTRA_CAMERA_RESULT = "cameraResult";
    public static final String EXTRA_CAMERA_ON_ERROR_CODE = "cameraOnErrorCode";

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
                case ACTION_TEST_CAMERA:
                    Log.d(TAG, "Testing camera");
                    testCamera();
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

    private void testCamera() {
        Intent resultData = new Intent();
        CameraManager cameraManager = getSystemService(CameraManager.class);
        String cameraId = null;
        try {
            cameraId = getIntent().getStringExtra(EXTRA_CAMERA_ID);
            Log.d(TAG, "opening camera " + cameraId);
            cameraManager.openCamera(cameraId,
                    new CameraDevice.StateCallback() {
                        @Override
                        public void onOpened(@NonNull CameraDevice camera) {
                            Log.d(TAG, "onOpened");
                        }

                        @Override
                        public void onDisconnected(@NonNull CameraDevice camera) {
                            Log.d(TAG, "onDisconnected");
                            resultData.putExtra(EXTRA_CAMERA_RESULT, "onDisconnected");
                            setResult(Activity.RESULT_OK, resultData);
                            finish();
                        }

                        @Override
                        public void onError(@NonNull CameraDevice camera, int error) {
                            Log.d(TAG, "onError " + error);
                            resultData.putExtra(EXTRA_CAMERA_RESULT, "onError");
                            resultData.putExtra(EXTRA_CAMERA_ON_ERROR_CODE, error);
                            setResult(Activity.RESULT_OK, resultData);
                            finish();
                        }
                    }, null);
        } catch (CameraAccessException e) {
            int reason = e.getReason();
            if (reason == CAMERA_DISABLED || reason == CAMERA_DISCONNECTED) {
                // ok to ignore - we should get one of the onDisconnected or onError callbacks above
                Log.d(TAG, "saw expected CameraAccessException for reason:" + reason);
            } else {
                Log.e(TAG, "got unexpected CameraAccessException with reason:" + reason, e);
            }
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "IllegalArgumentException - maybe invalid camera id? (" + cameraId + ")", e);
        }
    }
}

