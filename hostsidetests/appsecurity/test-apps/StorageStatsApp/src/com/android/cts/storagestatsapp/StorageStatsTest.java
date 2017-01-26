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

package com.android.cts.storagestatsapp;

import static com.android.cts.storageapp.Utils.CACHE_ALL;
import static com.android.cts.storageapp.Utils.DATA_ALL;
import static com.android.cts.storageapp.Utils.MB_IN_BYTES;
import static com.android.cts.storageapp.Utils.assertAtLeast;
import static com.android.cts.storageapp.Utils.assertMostlyEquals;
import static com.android.cts.storageapp.Utils.useSpace;
import static com.android.cts.storageapp.Utils.useWrite;

import android.app.usage.ExternalStorageStats;
import android.app.usage.StorageStats;
import android.app.usage.StorageStatsManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Environment;
import android.os.UserHandle;
import android.test.InstrumentationTestCase;

import java.io.File;

/**
 * Tests to verify {@link StorageStatsManager} behavior.
 */
public class StorageStatsTest extends InstrumentationTestCase {

    private static final String PKG_A = "com.android.cts.storageapp_a";
    private static final String PKG_B = "com.android.cts.storageapp_b";

    private Context getContext() {
        return getInstrumentation().getContext();
    }

    public void testVerifySummary() throws Exception {
        final StorageStatsManager stats = getContext().getSystemService(StorageStatsManager.class);

        assertAtLeast(Environment.getDataDirectory().getTotalSpace(), stats.getTotalBytes(null));
        assertAtLeast(Environment.getDataDirectory().getFreeSpace(), stats.getFreeBytes(null));
    }

    public void testVerifyStats() throws Exception {
        final StorageStatsManager stats = getContext().getSystemService(StorageStatsManager.class);
        final int uid = android.os.Process.myUid();
        final UserHandle user = UserHandle.getUserHandleForUid(uid);

        final StorageStats beforeApp = stats.queryStatsForUid(null, uid);
        final StorageStats beforeUser = stats.queryStatsForUser(null, user);

        useSpace(getContext());

        final StorageStats afterApp = stats.queryStatsForUid(null, uid);
        final StorageStats afterUser = stats.queryStatsForUser(null, user);

        final long deltaData = DATA_ALL;
        assertMostlyEquals(deltaData, afterApp.getDataBytes() - beforeApp.getDataBytes());
        assertMostlyEquals(deltaData, afterUser.getDataBytes() - beforeUser.getDataBytes());

        final long deltaCache = CACHE_ALL;
        assertMostlyEquals(deltaCache, afterApp.getCacheBytes() - beforeApp.getCacheBytes());
        assertMostlyEquals(deltaCache, afterUser.getCacheBytes() - beforeUser.getCacheBytes());
    }

    public void testVerifyStatsMultiple() throws Exception {
        final PackageManager pm = getContext().getPackageManager();
        final StorageStatsManager stats = getContext().getSystemService(StorageStatsManager.class);

        final ApplicationInfo a = pm.getApplicationInfo(PKG_A, 0);
        final ApplicationInfo b = pm.getApplicationInfo(PKG_B, 0);

        final StorageStats as = stats.queryStatsForUid(null, a.uid);
        final StorageStats bs = stats.queryStatsForUid(null, b.uid);

        assertMostlyEquals(DATA_ALL * 2, as.getDataBytes());
        assertMostlyEquals(CACHE_ALL * 2, as.getCacheBytes());

        assertMostlyEquals(DATA_ALL, bs.getDataBytes());
        assertMostlyEquals(CACHE_ALL, bs.getCacheBytes());
    }

    public void testVerifyStatsExternal() throws Exception {
        final StorageStatsManager stats = getContext().getSystemService(StorageStatsManager.class);
        final int uid = android.os.Process.myUid();
        final UserHandle user = UserHandle.getUserHandleForUid(uid);

        final ExternalStorageStats before = stats.queryExternalStatsForUser(null, user);

        useWrite(new File(Environment.getExternalStorageDirectory(), System.nanoTime() + ".jpg"),
                2 * MB_IN_BYTES);
        useWrite(new File(Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_DOWNLOADS), System.nanoTime() + ".MP4"),
                3 * MB_IN_BYTES);
        useWrite(new File(Environment.getExternalStorageDirectory(), System.nanoTime() + ".png.WaV"),
                5 * MB_IN_BYTES);

        final ExternalStorageStats after = stats.queryExternalStatsForUser(null, user);

        assertMostlyEquals((2 + 3 + 5) * MB_IN_BYTES,
                after.getTotalBytes() - before.getTotalBytes());

        assertMostlyEquals(5 * MB_IN_BYTES, after.getAudioBytes() - before.getAudioBytes());
        assertMostlyEquals(3 * MB_IN_BYTES, after.getVideoBytes() - before.getVideoBytes());
        assertMostlyEquals(2 * MB_IN_BYTES, after.getImageBytes() - before.getImageBytes());
    }
}
