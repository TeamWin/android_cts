/*
 * Copyright (C) 2024 The Android Open Source Project
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

package android.app.cts.testcomponentcaller;

import static android.app.cts.testcomponentcaller.Constants.ACTION_ID;
import static android.app.cts.testcomponentcaller.Constants.EXTRA_CHECK_CONTENT_URI_PERMISSION_RESULT;
import static android.app.cts.testcomponentcaller.Constants.EXTRA_ILLEGAL_ARG_EXCEPTION_CAUGHT;
import static android.app.cts.testcomponentcaller.Constants.MODE_FLAGS_TO_CHECK;
import static android.app.cts.testcomponentcaller.Constants.EXTRA_SECURITY_EXCEPTION_CAUGHT;
import static android.app.cts.testcomponentcaller.Constants.INVALID_PERMISSION_RESULT;
import static android.app.cts.testcomponentcaller.Constants.NONE_PROVIDED_USE_HELPER_APP_URI_LOCATION_ID;
import static android.app.cts.testcomponentcaller.Constants.SEND_TEST_BROADCAST_ACTION_ID;
import static android.app.cts.testcomponentcaller.Constants.START_TEST_ACTIVITY_ACTION_ID;
import static android.app.cts.testcomponentcaller.Constants.TEST_ACTIVITY;
import static android.app.cts.testcomponentcaller.Constants.TEST_RECEIVER;
import static android.app.cts.testcomponentcaller.Constants.TEST_RECEIVER_ACTION;
import static android.app.cts.testcomponentcaller.Constants.URI_IN_CLIP_DATA_LOCATION_ID;
import static android.app.cts.testcomponentcaller.Constants.URI_IN_DATA_LOCATION_ID;
import static android.app.cts.testcomponentcaller.Constants.URI_LOCATION_ID;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

public class TestActivity extends Activity {
    private static final String TAG = "componentcaller";
    @Override
    public void onStart() {
        super.onStart();
        Intent intent = getIntent();
        int uriLocationId = intent.getIntExtra(URI_LOCATION_ID, -1);
        int actionId = intent.getIntExtra(ACTION_ID, -1);
        int modeFlags = intent.getIntExtra(MODE_FLAGS_TO_CHECK, -1);

        Uri uri = getUriToCheck(uriLocationId);
        Log.i(TAG, uri.toString());
        switch (actionId) {
            case START_TEST_ACTIVITY_ACTION_ID -> startTestActivity(uri, modeFlags);
            case SEND_TEST_BROADCAST_ACTION_ID -> sendTestBroadcast(uri, modeFlags);
            default -> throw new RuntimeException("Invalid action ID");
        }
    }

    private void startTestActivity(Uri uri, int modeFlags) {
        Intent intent = new Intent();
        intent.setComponent(TEST_ACTIVITY);
        intent.setData(uri);
        intent.putExtra(MODE_FLAGS_TO_CHECK, modeFlags);
        intent.setFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK | modeFlags);
        startActivity(intent);
        finish();
    }

    private void sendTestBroadcast(Uri uri, int modeFlags) {
        boolean securityExceptionCaught = false;
        boolean illegalArgExceptionCaught = false;
        int permissionResult = INVALID_PERMISSION_RESULT;

        try {
            if (uri != null) {
                permissionResult = getInitialCaller().checkContentUriPermission(uri, modeFlags);
            } else {
                throw new RuntimeException("Test URI is null");
            }
        } catch (Exception e) {
            if (e instanceof SecurityException) {
                securityExceptionCaught = true;
            } else if (e instanceof IllegalArgumentException) {
                illegalArgExceptionCaught = true;
            }
        }

        Intent intent = new Intent();
        intent.setComponent(TEST_RECEIVER);
        intent.setAction(TEST_RECEIVER_ACTION);

        intent.putExtra(EXTRA_SECURITY_EXCEPTION_CAUGHT, securityExceptionCaught);
        intent.putExtra(EXTRA_ILLEGAL_ARG_EXCEPTION_CAUGHT, illegalArgExceptionCaught);
        intent.putExtra(EXTRA_CHECK_CONTENT_URI_PERMISSION_RESULT, permissionResult);

        sendBroadcast(intent);
        finish();
    }

    private Uri getUriToCheck(int uriLocationId) {
        Intent intent = getIntent();
        return switch (uriLocationId) {
            case URI_IN_DATA_LOCATION_ID -> intent.getData();
            case URI_IN_CLIP_DATA_LOCATION_ID -> getClipDataUri(intent);
            case NONE_PROVIDED_USE_HELPER_APP_URI_LOCATION_ID -> TestProvider.CONTENT_URI;
            default -> throw new RuntimeException("Invalid URI location ID");
        };
    }

    private Uri getClipDataUri(Intent intent) {
        ClipData clip = intent.getClipData();
        if (clip == null || clip.getItemCount() == 0) {
            throw new RuntimeException("Testing clip data, but it wasn't provided");
        }
        return intent.getClipData().getItemAt(0).getUri();
    }
}
