/*
 * Copyright (C) 2018 The Android Open Source Project
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

package android.provider.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.provider.MediaStore.MediaColumns;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;
import android.text.format.DateUtils;
import android.util.Log;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class MediaStoreTrashTest {
    private static final String TAG = "MediaStoreTrashTest";

    private Context mContext;
    private ContentResolver mResolver;

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getTargetContext();
        mResolver = mContext.getContentResolver();
    }

    @Test
    public void testTrashUntrash() throws Exception {
        final Uri insertUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        final Uri uri = ProviderTestUtils.stageMedia(R.raw.volantis, insertUri);
        final long id = ContentUris.parseId(uri);

        // Default is not trashed, and no expiration date
        try (Cursor c = mResolver.query(uri,
                new String[] { MediaColumns.IS_TRASHED, MediaColumns.DATE_EXPIRES }, null, null)) {
            assertTrue(c.moveToFirst());
            assertEquals(0, c.getInt(0));
            assertTrue(c.isNull(1));
        }
        assertTrue(containsId(insertUri, id));
        assertTrue(containsId(MediaStore.setIncludeTrashed(insertUri), id));

        // Trash should expire roughly 2 days from now
        MediaStore.trash(mContext, uri);
        try (Cursor c = mResolver.query(uri,
                new String[] { MediaColumns.IS_TRASHED, MediaColumns.DATE_EXPIRES }, null, null)) {
            assertTrue(c.moveToFirst());
            assertEquals(1, c.getInt(0));
            assertMostlyEquals((System.currentTimeMillis() + (2 * DateUtils.DAY_IN_MILLIS)) / 1000,
                    c.getLong(1), 30);
        }
        assertFalse(containsId(insertUri, id));
        assertTrue(containsId(MediaStore.setIncludeTrashed(insertUri), id));

        // Untrash should bring us back
        MediaStore.untrash(mContext, uri);
        try (Cursor c = mResolver.query(uri,
                new String[] { MediaColumns.IS_TRASHED, MediaColumns.DATE_EXPIRES }, null, null)) {
            assertTrue(c.moveToFirst());
            assertEquals(0, c.getInt(0));
            assertTrue(c.isNull(1));
        }
        assertTrue(containsId(insertUri, id));
        assertTrue(containsId(MediaStore.setIncludeTrashed(insertUri), id));
    }

    @Test
    public void testTrashExecutes() throws Exception {
        final Uri insertUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        final Uri uri = ProviderTestUtils.stageMedia(R.raw.volantis, insertUri);

        MediaStore.trash(mContext, uri, 1);

        // Force idle maintenance to run
        ProviderTestUtils.executeShellCommand(
                "cmd jobscheduler run -f com.android.providers.media -200",
                InstrumentationRegistry.getInstrumentation().getUiAutomation());

        // Wait around for item to be deleted
        final long timeout = SystemClock.elapsedRealtime() + DateUtils.MINUTE_IN_MILLIS;
        while (SystemClock.elapsedRealtime() < timeout) {
            try (Cursor c = mResolver.query(uri, null, null, null)) {
                Log.v(TAG, "Count " + c.getCount());
                if (c.getCount() == 0) {
                    return;
                }
            }
            SystemClock.sleep(500);
        }

        fail("Timed out waiting for job to delete trashed item");
    }

    private boolean containsId(Uri uri, long id) {
        try (Cursor c = mResolver.query(uri,
                new String[] { MediaColumns._ID }, null, null)) {
            while (c.moveToNext()) {
                if (c.getLong(0) == id) return true;
            }
        }
        return false;
    }

    private static void assertMostlyEquals(long expected, long actual, long delta) {
        if (Math.abs(expected - actual) > delta) {
            fail("Expected roughly " + expected + " but was " + actual);
        }
    }
}
