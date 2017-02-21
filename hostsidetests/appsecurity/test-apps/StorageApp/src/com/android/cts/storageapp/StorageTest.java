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

import static com.android.cts.storageapp.Utils.CACHE_ALL;
import static com.android.cts.storageapp.Utils.CACHE_EXT;
import static com.android.cts.storageapp.Utils.CACHE_INT;
import static com.android.cts.storageapp.Utils.DATA_EXT;
import static com.android.cts.storageapp.Utils.DATA_INT;
import static com.android.cts.storageapp.Utils.MB_IN_BYTES;
import static com.android.cts.storageapp.Utils.assertMostlyEquals;
import static com.android.cts.storageapp.Utils.getSizeManual;
import static com.android.cts.storageapp.Utils.makeUniqueFile;
import static com.android.cts.storageapp.Utils.useSpace;

import android.content.Context;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.os.storage.StorageManager;
import android.test.InstrumentationTestCase;

import java.io.File;

/**
 * Client app for verifying storage behaviors.
 */
public class StorageTest extends InstrumentationTestCase {

    private Context getContext() {
        return getInstrumentation().getContext();
    }

    public void testAllocate() throws Exception {
        useSpace(getContext());
    }

    /**
     * Measure ourselves manually.
     */
    public void testVerifySpaceManual() throws Exception {
        assertMostlyEquals(DATA_INT,
                getSizeManual(getContext().getDataDir()));
        assertMostlyEquals(DATA_EXT,
                getSizeManual(getContext().getExternalCacheDir().getParentFile()));
    }

    /**
     * Measure ourselves using platform APIs.
     */
    public void testVerifySpaceApi() throws Exception {
        final StorageManager sm = getContext().getSystemService(StorageManager.class);

        final long cacheSize = sm.getCacheSizeBytes(getContext().getCacheDir());
        final long extCacheSize = sm.getCacheSizeBytes(getContext().getExternalCacheDir());
        if (cacheSize == extCacheSize) {
            assertMostlyEquals(CACHE_ALL, cacheSize);
        } else {
            assertMostlyEquals(CACHE_INT, cacheSize);
            assertMostlyEquals(CACHE_EXT, extCacheSize);
        }
    }

    public void testVerifyQuotaApi() throws Exception {
        final StorageManager sm = getContext().getSystemService(StorageManager.class);
        assertTrue("Apps must have at least 10MB quota",
                sm.getCacheQuotaBytes(getContext().getCacheDir()) > 10 * MB_IN_BYTES);
    }

    public void testVerifyAllocateApi() throws Exception {
        final StorageManager sm = getContext().getSystemService(StorageManager.class);

        final File filesDir = getContext().getFilesDir();
        assertTrue("Apps must be able to allocate internal space",
                sm.getAllocatableBytes(filesDir, 0) > 10 * MB_IN_BYTES);
        final File extDir = Environment.getExternalStorageDirectory();
        assertTrue("Apps must be able to allocate external space",
                sm.getAllocatableBytes(extDir, 0) > 10 * MB_IN_BYTES);

        // Should always be able to allocate 1MB indirectly
        sm.allocateBytes(filesDir, 1 * MB_IN_BYTES, 0);

        // Should always be able to allocate 1MB directly
        final File filesFile = makeUniqueFile(filesDir);
        assertEquals(0L, filesFile.length());
        try (ParcelFileDescriptor pfd = ParcelFileDescriptor.open(filesFile,
                ParcelFileDescriptor.parseMode("rwt"))) {
            sm.allocateBytes(pfd.getFileDescriptor(), 1 * MB_IN_BYTES, 0);
        }
        assertEquals(1 * MB_IN_BYTES, filesFile.length());
    }

    public void testBehaviorNormal() throws Exception {
        final StorageManager sm = getContext().getSystemService(StorageManager.class);

        final File dir = makeUniqueFile(getContext().getCacheDir());
        dir.mkdir();
        assertFalse(sm.isCacheBehaviorAtomic(dir));
        assertFalse(sm.isCacheBehaviorTombstone(dir));

        // TODO: verify that files are purged normally
    }

    public void testBehaviorAtomic() throws Exception {
        final StorageManager sm = getContext().getSystemService(StorageManager.class);

        final File dir = makeUniqueFile(getContext().getCacheDir());
        dir.mkdir();
        sm.setCacheBehaviorAtomic(dir, true);
        assertTrue(sm.isCacheBehaviorAtomic(dir));

        // TODO: verify that directory is purged atomically
    }

    public void testBehaviorTombstone() throws Exception {
        final StorageManager sm = getContext().getSystemService(StorageManager.class);

        final File dir = makeUniqueFile(getContext().getCacheDir());
        dir.mkdir();
        sm.setCacheBehaviorTombstone(dir, true);
        assertTrue(sm.isCacheBehaviorTombstone(dir));

        // TODO: verify that directory is purged with tombstones
    }
}
