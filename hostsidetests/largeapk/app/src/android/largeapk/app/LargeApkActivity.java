/*
 * Copyright (C) 2016 The Android Open Source Project
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

package android.largeapk.app;

import android.app.Activity;
import android.content.res.AssetFileDescriptor;
import android.os.Bundle;
import android.util.Log;

import java.io.IOException;

/**
 * A simple activity
 */
public class LargeApkActivity extends Activity {

    private static final String TEST_STRING = "Hello Large APK";

    private static final String ASSET_FILE = "zerofile.png";

    private static final long ASSET_SIZE = 200 * 1024 * 1024;

    private static final String TAG = LargeApkActivity.class.getSimpleName();

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        long length = 0;
        try {
            AssetFileDescriptor fd = getResources().openRawResourceFd(R.raw.zerofile);
            length = fd.getLength();
            fd.close();
        } catch (IOException ex) {
            Log.e(TAG, "Could not open file " + ASSET_FILE + " got exception:" + ex);
        }
        // success case: verify asset length
        if (length == ASSET_SIZE) {
            Log.i(TAG, TEST_STRING);
        } else {
            Log.e(TAG, "Wrong asset size:" + length);
        }
    }
}
