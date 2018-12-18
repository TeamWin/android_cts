/*
 * Copyright (C) 2012 The Android Open Source Project
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

import static android.provider.cts.ProviderTestUtils.assertExists;
import static android.provider.cts.ProviderTestUtils.assertNotExists;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore.Files;
import android.provider.MediaStore.Video.Media;
import android.provider.MediaStore.Video.Thumbnails;
import android.provider.MediaStore.Video.VideoColumns;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;
import android.util.Log;

import com.android.compatibility.common.util.MediaUtils;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;

@RunWith(AndroidJUnit4.class)
public class MediaStore_Video_ThumbnailsTest {
    private static final String TAG = "MediaStore_Video_ThumbnailsTest";

    private Context mContext;
    private ContentResolver mResolver;

    private boolean hasCodec() {
        return MediaUtils.hasCodecForResourceAndDomain(
                mContext, R.raw.testthumbvideo, "video/");
    }

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getTargetContext();
        mResolver = mContext.getContentResolver();
    }

    @Test
    public void testGetContentUri() {
        Uri internalUri = Thumbnails.getContentUri(MediaStoreAudioTestHelper.INTERNAL_VOLUME_NAME);
        Uri externalUri = Thumbnails.getContentUri(MediaStoreAudioTestHelper.EXTERNAL_VOLUME_NAME);
        assertEquals(Thumbnails.INTERNAL_CONTENT_URI, internalUri);
        assertEquals(Thumbnails.EXTERNAL_CONTENT_URI, externalUri);
    }

    @Test
    public void testGetThumbnail() throws Exception {
        // Insert a video into the provider.
        Uri videoUri = insertVideo();
        long videoId = ContentUris.parseId(videoUri);
        assertTrue(videoId != -1);
        assertEquals(ContentUris.withAppendedId(Media.EXTERNAL_CONTENT_URI, videoId),
                videoUri);

        // Get the current thumbnail count for future comparison.
        int count = getThumbnailCount(Thumbnails.EXTERNAL_CONTENT_URI);

        // Don't run the test if the codec isn't supported.
        if (!hasCodec()) {
            // Calling getThumbnail should not generate a new thumbnail.
            assertNull(Thumbnails.getThumbnail(mResolver, videoId, Thumbnails.MINI_KIND, null));
            Log.i(TAG, "SKIPPING testGetThumbnail(): codec not supported");
            return;
        }

        // Calling getThumbnail should generate a new thumbnail.
        assertNotNull(Thumbnails.getThumbnail(mResolver, videoId, Thumbnails.MINI_KIND, null));
        assertNotNull(Thumbnails.getThumbnail(mResolver, videoId, Thumbnails.MICRO_KIND, null));

        // Check that an additional thumbnails have been registered.
        int count2 = getThumbnailCount(Thumbnails.EXTERNAL_CONTENT_URI);
        assertTrue(count2 > count);

        Cursor c = mResolver.query(Thumbnails.EXTERNAL_CONTENT_URI,
                new String[] { Thumbnails._ID, Thumbnails.DATA, Thumbnails.VIDEO_ID },
                null, null, null);

        if (c.moveToLast()) {
            long vid = c.getLong(2);
            assertEquals(videoId, vid);
            String path = c.getString(1);
            assertExists("thumbnail file does not exist", path);
            long id = c.getLong(0);
            mResolver.delete(ContentUris.withAppendedId(Thumbnails.EXTERNAL_CONTENT_URI, id),
                    null, null);
            assertNotExists("thumbnail file should no longer exist", path);
        }
        c.close();

        assertEquals(1, mResolver.delete(videoUri, null, null));
    }

    @Test
    public void testThumbnailGenerationAndCleanup() throws Exception {

        if (!hasCodec()) {
            // we don't support video, so no need to run the test
            Log.i(TAG, "SKIPPING testThumbnailGenerationAndCleanup(): codec not supported");
            return;
        }

        // insert a video
        Uri uri = insertVideo();

        // request thumbnail creation
        Thumbnails.getThumbnail(mResolver, Long.valueOf(uri.getLastPathSegment()),
                Thumbnails.MINI_KIND, null /* options */);

        // query the thumbnail
        Cursor c = mResolver.query(
                Thumbnails.EXTERNAL_CONTENT_URI,
                new String [] {Thumbnails.DATA},
                "video_id=?",
                new String[] {uri.getLastPathSegment()},
                null /* sort */
                );
        assertTrue("couldn't find thumbnail", c.moveToNext());
        String path = c.getString(0);
        c.close();
        assertExists("thumbnail does not exist", path);

        // delete the source video and check that the thumbnail is gone too
        mResolver.delete(uri, null /* where clause */, null /* where args */);
        assertNotExists("thumbnail still exists after source file delete", path);

        // insert again
        uri = insertVideo();

        // request thumbnail creation
        Thumbnails.getThumbnail(mResolver, Long.valueOf(uri.getLastPathSegment()),
                Thumbnails.MINI_KIND, null);

        // query its thumbnail again
        c = mResolver.query(
                Thumbnails.EXTERNAL_CONTENT_URI,
                new String [] {Thumbnails.DATA},
                "video_id=?",
                new String[] {uri.getLastPathSegment()},
                null /* sort */
                );
        assertTrue("couldn't find thumbnail", c.moveToNext());
        path = c.getString(0);
        c.close();
        assertExists("thumbnail does not exist", path);

        // update the media type
        ContentValues values = new ContentValues();
        values.put("media_type", 0);
        assertEquals("unexpected number of updated rows",
                1, mResolver.update(uri, values, null /* where */, null /* where args */));

        // video was marked as regular file in the database, which should have deleted its thumbnail

        // query its thumbnail again
        c = mResolver.query(
                Thumbnails.EXTERNAL_CONTENT_URI,
                new String [] {Thumbnails.DATA},
                "video_id=?",
                new String[] {uri.getLastPathSegment()},
                null /* sort */
                );
        if (c != null) {
            assertFalse("thumbnail entry exists for non-thumbnail file", c.moveToNext());
            c.close();
        }
        assertNotExists("thumbnail remains after source file type change", path);

        // check source no longer exists as video
        c = mResolver.query(uri,
                null /* projection */, null /* where */, null /* where args */, null /* sort */);
        assertFalse("source entry should be gone", c.moveToNext());
        c.close();

        // check source still exists as file
        Uri fileUri = ContentUris.withAppendedId(
                Files.getContentUri("external"),
                Long.valueOf(uri.getLastPathSegment()));
        c = mResolver.query(fileUri,
                null /* projection */, null /* where */, null /* where args */, null /* sort */);
        assertTrue("source entry should be gone", c.moveToNext());
        String sourcePath = c.getString(c.getColumnIndex("_data"));
        c.close();

        // clean up
        mResolver.delete(fileUri, null /* where */, null /* where args */);
        new File(sourcePath).delete();
    }

    private Uri insertVideo() throws IOException {
        File file = new File(Environment.getExternalStorageDirectory(), "testVideo.3gp");
        // clean up any potential left over entries from a previous aborted run
        mResolver.delete(Media.EXTERNAL_CONTENT_URI,
                "_data=?", new String[] { file.getAbsolutePath() });
        file.delete();

        ProviderTestUtils.stageFile(R.raw.testthumbvideo, file);

        ContentValues values = new ContentValues();
        values.put(VideoColumns.DATA, file.getAbsolutePath());
        return mResolver.insert(Media.EXTERNAL_CONTENT_URI, values);
    }

    private int getThumbnailCount(Uri uri) {
        Cursor cursor = mResolver.query(uri, null, null, null, null);
        try {
            return cursor.getCount();
        } finally {
            cursor.close();
        }
    }
}
