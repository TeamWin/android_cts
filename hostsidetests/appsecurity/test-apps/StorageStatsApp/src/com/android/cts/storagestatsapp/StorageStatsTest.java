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

        final File dir = Environment.getExternalStorageDirectory();
        final File downloadsDir = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS);
        downloadsDir.mkdirs();

        final File image = new File(dir, System.nanoTime() + ".jpg");
        final File video = new File(downloadsDir, System.nanoTime() + ".MP4");
        final File audio = new File(dir, System.nanoTime() + ".png.WaV");
        final File internal = new File(
                getContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES), "test.jpg");

        useWrite(image, 2 * MB_IN_BYTES);
        useWrite(video, 3 * MB_IN_BYTES);
        useWrite(audio, 5 * MB_IN_BYTES);
        useWrite(internal, 7 * MB_IN_BYTES);

        final ExternalStorageStats afterInit = stats.queryExternalStatsForUser(null, user);

        assertMostlyEquals(17 * MB_IN_BYTES, afterInit.getTotalBytes() - before.getTotalBytes());
        assertMostlyEquals(5 * MB_IN_BYTES, afterInit.getAudioBytes() - before.getAudioBytes());
        assertMostlyEquals(3 * MB_IN_BYTES, afterInit.getVideoBytes() - before.getVideoBytes());
        assertMostlyEquals(2 * MB_IN_BYTES, afterInit.getImageBytes() - before.getImageBytes());

        // Rename to ensure that stats are updated
        video.renameTo(new File(dir, System.nanoTime() + ".PnG"));

        final ExternalStorageStats afterRename = stats.queryExternalStatsForUser(null, user);

        assertMostlyEquals(17 * MB_IN_BYTES, afterRename.getTotalBytes() - before.getTotalBytes());
        assertMostlyEquals(5 * MB_IN_BYTES, afterRename.getAudioBytes() - before.getAudioBytes());
        assertMostlyEquals(0 * MB_IN_BYTES, afterRename.getVideoBytes() - before.getVideoBytes());
        assertMostlyEquals(5 * MB_IN_BYTES, afterRename.getImageBytes() - before.getImageBytes());
    }

    public void testVerifyCategory() throws Exception {
        final PackageManager pm = getContext().getPackageManager();
        final ApplicationInfo a = pm.getApplicationInfo(PKG_A, 0);
        final ApplicationInfo b = pm.getApplicationInfo(PKG_B, 0);

        assertEquals(ApplicationInfo.CATEGORY_VIDEO, a.category);
        assertEquals(ApplicationInfo.CATEGORY_UNDEFINED, b.category);
    }
}
