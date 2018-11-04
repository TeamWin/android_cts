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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.os.FileUtils;
import android.provider.MediaStore;
import android.provider.MediaStore.MediaColumns;
import android.provider.MediaStore.PendingParams;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;

import libcore.io.IoUtils;

import com.google.common.base.Objects;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

@RunWith(AndroidJUnit4.class)
public class MediaStorePendingTest {
    private Context mContext;
    private ContentResolver mResolver;

    private Uri mExternalAudio = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
    private Uri mExternalVideo = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
    private Uri mExternalImages = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getTargetContext();
        mResolver = mContext.getContentResolver();
    }

    @After
    public void tearDown() throws Exception {
    }

    @Test
    public void testSimple_Success() throws Exception {
        final String displayName = "cts" + System.nanoTime();

        final Uri insertUri = mExternalImages;
        final MediaStore.PendingParams params = new MediaStore.PendingParams(
                insertUri, displayName, "image/png");

        final Uri pendingUri = MediaStore.createPending(mContext, params);
        final long id = ContentUris.parseId(pendingUri);

        // Verify pending status across various queries
        try (Cursor c = mResolver.query(pendingUri,
                new String[] { MediaColumns.IS_PENDING }, null, null)) {
            assertTrue(c.moveToFirst());
            assertEquals(1, c.getInt(0));
        }
        assertFalse(containsId(insertUri, id));
        assertTrue(containsId(MediaStore.setIncludePending(insertUri), id));

        // Write an image into place
        final Uri publishUri;
        final MediaStore.PendingSession session = MediaStore.openPending(mContext, pendingUri);
        try {
            try (InputStream in = mContext.getResources().openRawResource(R.raw.scenery);
                    OutputStream out = session.openOutputStream()) {
                FileUtils.copy(in, out);
            }
            publishUri = session.publish();
        } finally {
            IoUtils.closeQuietly(session);
        }

        // Verify pending status across various queries
        try (Cursor c = mResolver.query(publishUri,
                new String[] { MediaColumns.IS_PENDING }, null, null)) {
            assertTrue(c.moveToFirst());
            assertEquals(0, c.getInt(0));
        }
        assertTrue(containsId(insertUri, id));
        assertTrue(containsId(MediaStore.setIncludePending(insertUri), id));

        // Make sure our raw filename looks sane
        final File rawFile = getRawFile(publishUri);
        assertEquals(displayName + ".png", rawFile.getName());
        assertEquals(Environment.DIRECTORY_PICTURES, rawFile.getParentFile().getName());

        // Make sure file actually exists
        getRawFileHash(rawFile);
        try (InputStream in = mResolver.openInputStream(publishUri)) {
        }
    }

    @Test
    public void testSimple_Abandoned() throws Exception {
        final String displayName = "cts" + System.nanoTime();

        final Uri insertUri = mExternalImages;
        final MediaStore.PendingParams params = new MediaStore.PendingParams(
                insertUri, displayName, "image/png");

        final Uri pendingUri = MediaStore.createPending(mContext, params);
        final File pendingFile;

        final MediaStore.PendingSession session = MediaStore.openPending(mContext, pendingUri);
        try {
            try (InputStream in = mContext.getResources().openRawResource(R.raw.scenery);
                    OutputStream out = session.openOutputStream()) {
                FileUtils.copy(in, out);
            }

            // Pending file should exist
            pendingFile = getRawFile(pendingUri);
            getRawFileHash(pendingFile);

            session.abandon();
        } finally {
            IoUtils.closeQuietly(session);
        }

        // Should have no record of abandoned item
        try (Cursor c = mResolver.query(pendingUri,
                new String[] { MediaColumns.IS_PENDING }, null, null)) {
            assertFalse(c.moveToNext());
        }

        // Pending file should be gone
        try {
            getRawFileHash(pendingFile);
            fail();
        } catch (FileNotFoundException expected) {
        }
    }

    @Test
    public void testDuplicates() throws Exception {
        final String displayName = "cts" + System.nanoTime();

        final Uri insertUri = mExternalAudio;
        final MediaStore.PendingParams params1 = new MediaStore.PendingParams(
                insertUri, displayName, "audio/mpeg");
        final MediaStore.PendingParams params2 = new MediaStore.PendingParams(
                insertUri, displayName, "audio/mpeg");

        final Uri publishUri1 = execPending(params1, R.raw.testmp3);
        final Uri publishUri2 = execPending(params2, R.raw.testmp3_2);

        // Make sure both files landed with unique filenames, and that we didn't
        // cross the streams
        final File rawFile1 = getRawFile(publishUri1);
        final File rawFile2 = getRawFile(publishUri2);
        assertFalse(Objects.equal(rawFile1, rawFile2));

        assertArrayEquals(hash(mContext.getResources().openRawResource(R.raw.testmp3)),
                hash(mResolver.openInputStream(publishUri1)));
        assertArrayEquals(hash(mContext.getResources().openRawResource(R.raw.testmp3_2)),
                hash(mResolver.openInputStream(publishUri2)));
    }

    @Test
    public void testMimeTypes() throws Exception {
        final String displayName = "cts" + System.nanoTime();

        assertCreatePending(new PendingParams(mExternalAudio, displayName, "audio/ogg"));
        assertNotCreatePending(new PendingParams(mExternalAudio, displayName, "video/ogg"));
        assertNotCreatePending(new PendingParams(mExternalAudio, displayName, "image/png"));

        assertNotCreatePending(new PendingParams(mExternalVideo, displayName, "audio/ogg"));
        assertCreatePending(new PendingParams(mExternalVideo, displayName, "video/ogg"));
        assertNotCreatePending(new PendingParams(mExternalVideo, displayName, "image/png"));

        assertNotCreatePending(new PendingParams(mExternalImages, displayName, "audio/ogg"));
        assertNotCreatePending(new PendingParams(mExternalImages, displayName, "video/ogg"));
        assertCreatePending(new PendingParams(mExternalImages, displayName, "image/png"));
    }

    @Test
    public void testMimeTypes_Forced() throws Exception {
        {
            final String displayName = "cts" + System.nanoTime();
            final Uri uri = execPending(new PendingParams(mExternalImages,
                    displayName, "image/png"), R.raw.scenery);
            assertEquals(displayName + ".png", getRawFile(uri).getName());
        }
        {
            final String displayName = "cts" + System.nanoTime() + ".png";
            final Uri uri = execPending(new PendingParams(mExternalImages,
                    displayName, "image/png"), R.raw.scenery);
            assertEquals(displayName, getRawFile(uri).getName());
        }
        {
            final String displayName = "cts" + System.nanoTime() + ".jpg";
            final Uri uri = execPending(new PendingParams(mExternalImages,
                    displayName, "image/png"), R.raw.scenery);
            assertEquals(displayName + ".png", getRawFile(uri).getName());
        }
    }

    @Test
    public void testDirectories() throws Exception {
        final String displayName = "cts" + System.nanoTime();

        final Set<String> allowedAudio = new HashSet<>(
                Arrays.asList(Environment.DIRECTORY_MUSIC, Environment.DIRECTORY_RINGTONES,
                        Environment.DIRECTORY_NOTIFICATIONS, Environment.DIRECTORY_PODCASTS,
                        Environment.DIRECTORY_ALARMS));
        final Set<String> allowedVideo = new HashSet<>(
                Arrays.asList(Environment.DIRECTORY_MOVIES, Environment.DIRECTORY_DCIM));
        final Set<String> allowedImages = new HashSet<>(
                Arrays.asList(Environment.DIRECTORY_PICTURES, Environment.DIRECTORY_DCIM));

        final Set<String> everything = new HashSet<>();
        everything.addAll(allowedAudio);
        everything.addAll(allowedVideo);
        everything.addAll(allowedImages);

        {
            final PendingParams params = new PendingParams(mExternalAudio,
                    displayName, "audio/ogg");
            for (String dir : everything) {
                params.setPrimaryDirectory(dir);
                if (allowedAudio.contains(dir)) {
                    assertCreatePending(params);
                } else {
                    assertNotCreatePending(dir, params);
                }
            }
        }
        {
            final PendingParams params = new PendingParams(mExternalVideo,
                    displayName, "video/ogg");
            for (String dir : everything) {
                params.setPrimaryDirectory(dir);
                if (allowedVideo.contains(dir)) {
                    assertCreatePending(params);
                } else {
                    assertNotCreatePending(dir, params);
                }
            }
        }
        {
            final PendingParams params = new PendingParams(mExternalImages,
                    displayName, "image/png");
            for (String dir : everything) {
                params.setPrimaryDirectory(dir);
                if (allowedImages.contains(dir)) {
                    assertCreatePending(params);
                } else {
                    assertNotCreatePending(dir, params);
                }
            }
        }
    }

    @Test
    public void testDirectories_Defaults() throws Exception {
        {
            final String displayName = "cts" + System.nanoTime();
            final Uri uri = execPending(new PendingParams(mExternalImages,
                    displayName, "image/png"), R.raw.scenery);
            assertEquals(Environment.DIRECTORY_PICTURES, getRawFile(uri).getParentFile().getName());
        }
        {
            final String displayName = "cts" + System.nanoTime();
            final Uri uri = execPending(new PendingParams(mExternalAudio,
                    displayName, "audio/ogg"), R.raw.scenery);
            assertEquals(Environment.DIRECTORY_MUSIC, getRawFile(uri).getParentFile().getName());
        }
        {
            final String displayName = "cts" + System.nanoTime();
            final Uri uri = execPending(new PendingParams(mExternalVideo,
                    displayName, "video/ogg"), R.raw.scenery);
            assertEquals(Environment.DIRECTORY_MOVIES, getRawFile(uri).getParentFile().getName());
        }
    }

    @Test
    public void testDirectories_Primary() throws Exception {
        final String displayName = "cts" + System.nanoTime();
        final PendingParams params = new PendingParams(mExternalImages, displayName, "image/png");
        params.setPrimaryDirectory(Environment.DIRECTORY_DCIM);

        final Uri uri = execPending(params, R.raw.scenery);
        assertEquals(Environment.DIRECTORY_DCIM, getRawFile(uri).getParentFile().getName());

        // Verify that shady paths don't work
        params.setPrimaryDirectory("foo/bar");
        assertNotCreatePending(params);
    }

    @Test
    public void testDirectories_Secondary() throws Exception {
        final String displayName = "cts" + System.nanoTime();
        final PendingParams params = new PendingParams(mExternalImages, displayName, "image/png");
        params.setSecondaryDirectory("Kittens");

        final Uri uri = execPending(params, R.raw.scenery);
        final File rawFile = getRawFile(uri);
        assertEquals("Kittens", rawFile.getParentFile().getName());
        assertEquals(Environment.DIRECTORY_PICTURES,
                rawFile.getParentFile().getParentFile().getName());

        // Verify that shady paths don't work
        params.setSecondaryDirectory("foo/bar");
        assertNotCreatePending(params);
    }

    @Test
    public void testDirectories_PrimarySecondary() throws Exception {
        final String displayName = "cts" + System.nanoTime();
        final PendingParams params = new PendingParams(mExternalImages, displayName, "image/png");
        params.setPrimaryDirectory(Environment.DIRECTORY_DCIM);
        params.setSecondaryDirectory("Kittens");

        final Uri uri = execPending(params, R.raw.scenery);
        final File rawFile = getRawFile(uri);
        assertEquals("Kittens", rawFile.getParentFile().getName());
        assertEquals(Environment.DIRECTORY_DCIM, rawFile.getParentFile().getParentFile().getName());
    }

    private void assertCreatePending(PendingParams params) {
        MediaStore.createPending(mContext, params);
    }

    private void assertNotCreatePending(PendingParams params) {
        assertNotCreatePending(null, params);
    }

    private void assertNotCreatePending(String message, PendingParams params) {
        try {
            MediaStore.createPending(mContext, params);
            fail(message);
        } catch (Exception expected) {
        }
    }

    private Uri execPending(PendingParams params, int resId) throws Exception {
        final Uri pendingUri = MediaStore.createPending(mContext, params);
        final MediaStore.PendingSession session = MediaStore.openPending(mContext, pendingUri);
        try {
            try (InputStream in = mContext.getResources().openRawResource(resId);
                    OutputStream out = session.openOutputStream()) {
                FileUtils.copy(in, out);
            }
            return session.publish();
        } finally {
            IoUtils.closeQuietly(session);
        }
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

    private static File getRawFile(Uri uri) throws Exception {
        final String res = ProviderTestUtils.executeShellCommand(
                "content query --uri " + uri + " --projection _data",
                InstrumentationRegistry.getInstrumentation().getUiAutomation());
        final int i = res.indexOf("_data=");
        if (i >= 0) {
            return new File(res.substring(i + 6));
        } else {
            throw new FileNotFoundException("Failed to find _data for " + uri + "; found " + res);
        }
    }

    private static String getRawFileHash(File file) throws Exception {
        final String res = ProviderTestUtils.executeShellCommand(
                "sha1sum " + file.getAbsolutePath(),
                InstrumentationRegistry.getInstrumentation().getUiAutomation());
        if (Pattern.matches("[0-9a-fA-F]{40}.+", res)) {
            return res.substring(0, 40);
        } else {
            throw new FileNotFoundException("Failed to find hash for " + file + "; found " + res);
        }
    }

    private static byte[] hash(InputStream in) throws Exception {
        try (DigestInputStream digestIn = new DigestInputStream(in,
                MessageDigest.getInstance("SHA-1"));
                OutputStream out = new FileOutputStream(new File("/dev/null"))) {
            FileUtils.copy(digestIn, out);
            return digestIn.getMessageDigest().digest();
        } finally {
            IoUtils.closeQuietly(in);
        }
    }
}
