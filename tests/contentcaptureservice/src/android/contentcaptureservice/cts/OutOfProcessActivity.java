/*
 * Copyright (C) 2019 The Android Open Source Project
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
package android.contentcaptureservice.cts;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import java.io.File;
import java.io.IOException;

/**
 * Activity that is running out of the CTS test process.
 *
 * <p>As such, it uses files to keep track of lifecycle events.
 */
public class OutOfProcessActivity extends Activity {

    private static final String TAG = OutOfProcessActivity.class.getSimpleName();

    public static final ComponentName COMPONENT_NAME = new ComponentName(Helper.MY_PACKAGE,
            OutOfProcessActivity.class.getName());

    @Override
    protected void onStart() {
        Log.i(TAG, "onStart()");
        super.onStart();
        try {
            if (!getStartedMarker(this).createNewFile()) {
                Log.e(TAG, "cannot write started file");
            }
        } catch (IOException e) {
            Log.e(TAG, "cannot write started file: " + e);
        }
    }

    @Override
    protected void onStop() {
        Log.i(TAG, "onStop()");
        super.onStop();

        try {
            if (!getStoppedMarker(this).createNewFile()) {
                Log.e(TAG, "could not write stopped marker");
            } else {
                Log.v(TAG, "wrote stopped marker");
            }
        } catch (IOException e) {
            Log.e(TAG, "could write stopped marker: " + e);
        }
    }

    /**
     * Gets the file that signals that the activity has entered {@link Activity#onStop()}.
     *
     * @param context Context of the app
     * @return The marker file that is written onStop()
     */
    @NonNull public static File getStoppedMarker(@NonNull Context context) {
        return new File(context.getFilesDir(), "stopped");
    }

    /**
     * Gets the file that signals that the activity has entered {@link Activity#onStart()}.
     *
     * @param context Context of the app
     * @return The marker file that is written onStart()
     */
    @NonNull public static File getStartedMarker(@NonNull Context context) {
        return new File(context.getFilesDir(), "started");
    }
}
