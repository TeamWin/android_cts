/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.cts.storageapp;

import static com.android.cts.storageapp.Utils.TAG;
import static com.android.cts.storageapp.Utils.makeUniqueFile;
import static com.android.cts.storageapp.Utils.useFallocate;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.storage.StorageManager;
import android.system.Os;
import android.util.Log;

import java.io.File;

public class UtilsReceiver extends BroadcastReceiver {
    public static final String EXTRA_FRACTION = "fraction";
    public static final String EXTRA_BYTES = "bytes";
    public static final String EXTRA_TIME = "time";

    @Override
    public void onReceive(Context context, Intent intent) {
        final StorageManager sm = context.getSystemService(StorageManager.class);

        final double fraction = intent.getDoubleExtra(EXTRA_FRACTION, 0);
        final long quota = sm.getCacheQuotaBytes(context.getCacheDir());
        final long bytes = (long) (quota * fraction);
        final long time = intent.getLongExtra(EXTRA_TIME, System.currentTimeMillis());

        long allocated = 0;
        try {
            while (allocated < bytes) {
                final File f = makeUniqueFile(context.getCacheDir());
                final long size = 1024 * 1024;
                useFallocate(f, size);
                f.setLastModified(time);
                allocated += Os.stat(f.getAbsolutePath()).st_blocks * 512;
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to allocate cache files", e);
            setResultCode(Activity.RESULT_CANCELED);
            return;
        }

        Log.d(TAG, "Quota " + quota + ", target " + bytes + ", allocated " + allocated);

        final Bundle extras = new Bundle();
        extras.putLong(EXTRA_BYTES, allocated);
        setResultCode(Activity.RESULT_OK);
        setResultExtras(extras);
    }
}
