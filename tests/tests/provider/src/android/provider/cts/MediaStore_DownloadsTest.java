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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.ContentObserver;
import android.database.Cursor;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Environment;
import android.os.FileUtils;
import android.provider.MediaStore;
import android.provider.MediaStore.Downloads;
import android.provider.MediaStore.Images;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;
import android.util.Log;

import libcore.io.IoUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
public class MediaStore_DownloadsTest {
    private static final String TAG = MediaStore_DownloadsTest.class.getSimpleName();
    private static final long SCAN_TIMEOUT_MILLIS = 4000;
    private static final long NOTIFY_TIMEOUT_MILLIS = 4000;

    private Context mContext;
    private ContentResolver mContentResolver;
    private File mDownloadsDir;
    private File mPicturesDir;
    private ArrayList<Uri> mAddedUris;
    private final Uri mExternalDownloads = Downloads.EXTERNAL_CONTENT_URI;
    private CountDownLatch mCountDownLatch;
    private int mInitialDownloadsCount;

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getTargetContext();
        mContentResolver = mContext.getContentResolver();
        mDownloadsDir = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS);
        mPicturesDir = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES);
        mDownloadsDir.mkdir();
        mPicturesDir.mkdir();
        mAddedUris = new ArrayList<>();
        mInitialDownloadsCount = getInitialDownloadsCount();
    }

    @After
    public void tearDown() {
        for (Uri uri : mAddedUris) {
            mContentResolver.delete(uri, null, null);
        }
    }

    @Test
    public void testScannedDownload() throws Exception {
        final File downloadFile = new File(mDownloadsDir, "colors.txt");
        downloadFile.createNewFile();
        final String fileContents = "RED;GREEN;BLUE";
        try (final PrintWriter pw = new PrintWriter(downloadFile)) {
            pw.print(fileContents);
        }
        verifyScannedDownload(downloadFile);
    }

    @Test
    public void testScannedMediaDownload() throws Exception {
        final File downloadFile = new File(mDownloadsDir, "scenery.png");
        downloadFile.createNewFile();
        try (InputStream in = mContext.getResources().openRawResource(R.raw.scenery);
                OutputStream out = new FileOutputStream(downloadFile)) {
            FileUtils.copy(in, out);
        }
        verifyScannedDownload(downloadFile);
    }

    @Test
    public void testGetContentUri() throws Exception {
        Cursor c;
        assertNotNull(c = mContentResolver.query(Downloads.INTERNAL_CONTENT_URI,
                null, null, null, null));
        c.close();
        assertNotNull(c = mContentResolver.query(Downloads.EXTERNAL_CONTENT_URI,
                null, null, null, null));
        c.close();

        // can not accept any other volume names
        final String volume = "faveVolume";
        assertNull(mContentResolver.query(Downloads.getContentUri(volume),
                null, null, null, null));
    }

    @Test
    public void testMediaInDownloadsDir() throws Exception {
        final String displayName = "cts" + System.nanoTime();
        final Uri insertUri = insertImage(displayName, "test image",
                new File(mDownloadsDir, "scenery.jpg"), "image/jpeg", R.raw.scenery);
        final String displayName2 = "cts" + System.nanoTime();
        final Uri insertUri2 = insertImage(displayName2, "test image2",
                new File(mPicturesDir, "volantis.jpg"), "image/jpeg", R.raw.volantis);

        try (Cursor cursor = mContentResolver.query(Downloads.EXTERNAL_CONTENT_URI,
                null, "title LIKE ?1", new String[] { displayName }, null)) {
            assertEquals(1, cursor.getCount());
            cursor.moveToNext();
            assertEquals("image/jpeg",
                    cursor.getString(cursor.getColumnIndex(Images.Media.MIME_TYPE)));
        }

        assertEquals(1, mContentResolver.delete(insertUri, null, null));
        mAddedUris.remove(insertUri);
        try (Cursor cursor = mContentResolver.query(Downloads.EXTERNAL_CONTENT_URI,
                null, null, null, null)) {
            assertEquals(mInitialDownloadsCount, cursor.getCount());
        }
    }

    @Test
    public void testUpdateDownload() throws Exception {
        final String displayName = "cts" + System.nanoTime();
        final MediaStore.PendingParams params = new MediaStore.PendingParams(
                Downloads.EXTERNAL_CONTENT_URI, displayName, "video/3gp");
        final Uri downloadUri = Uri.parse("https://www.android.com/download?file=testvideo.3gp");
        params.setDownloadUri(downloadUri);

        final Uri pendingUri = MediaStore.createPending(mContext, params);
        assertNotNull(pendingUri);
        mAddedUris.add(pendingUri);
        final Uri publishUri;
        final MediaStore.PendingSession session = MediaStore.openPending(mContext, pendingUri);
        try {
            try (InputStream in = mContext.getResources().openRawResource(R.raw.testvideo);
                 OutputStream out = session.openOutputStream()) {
                android.os.FileUtils.copy(in, out);
            }
            publishUri = session.publish();
        } finally {
            IoUtils.closeQuietly(session);
        }

        final String newDisplayName = "cts" + System.nanoTime();
        final ContentValues updateValues = new ContentValues();
        updateValues.put(Downloads.DISPLAY_NAME, newDisplayName);
        assertEquals(1, mContentResolver.update(publishUri, updateValues, null, null));

        try (Cursor cursor = mContentResolver.query(Downloads.EXTERNAL_CONTENT_URI,
                null, "_display_name LIKE ?1", new String[] { newDisplayName }, null)) {
            assertEquals(1, cursor.getCount());
            cursor.moveToNext();
            assertEquals("video/3gp",
                    cursor.getString(cursor.getColumnIndex(Downloads.MIME_TYPE)));
            assertEquals(downloadUri.toString(),
                    cursor.getString(cursor.getColumnIndex(Downloads.DOWNLOAD_URI)));
        }
    }

    @Test
    public void testDeleteDownload() throws Exception {
        final String displayName = "cts" + System.nanoTime();
        final MediaStore.PendingParams params = new MediaStore.PendingParams(
                Downloads.EXTERNAL_CONTENT_URI, displayName, "video/3gp");
        final Uri downloadUri = Uri.parse("https://www.android.com/download?file=testvideo.3gp");
        params.setDownloadUri(downloadUri);

        final Uri pendingUri = MediaStore.createPending(mContext, params);
        assertNotNull(pendingUri);
        mAddedUris.add(pendingUri);
        final Uri publishUri;
        final MediaStore.PendingSession session = MediaStore.openPending(mContext, pendingUri);
        try {
            try (InputStream in = mContext.getResources().openRawResource(R.raw.testvideo);
                 OutputStream out = session.openOutputStream()) {
                android.os.FileUtils.copy(in, out);
            }
            publishUri = session.publish();
        } finally {
            IoUtils.closeQuietly(session);
        }

        assertEquals(1, mContentResolver.delete(publishUri, null, null));
        try (Cursor cursor = mContentResolver.query(Downloads.EXTERNAL_CONTENT_URI,
                null, null, null, null)) {
            assertEquals(mInitialDownloadsCount, cursor.getCount());
        }
    }

    @Test
    public void testNotifyChange() throws Exception {
        final ContentObserver observer = new ContentObserver(null) {
            @Override
            public void onChange(boolean selfChange, Uri uri) {
                super.onChange(selfChange, uri);
                mCountDownLatch.countDown();
            }
        };
        mContentResolver.registerContentObserver(Downloads.EXTERNAL_CONTENT_URI, true, observer);
        mContentResolver.registerContentObserver(MediaStore.AUTHORITY_URI, false, observer);
        final Uri volumeUri = MediaStore.AUTHORITY_URI.buildUpon()
                .appendPath(MediaStore.getVolumeName(Downloads.EXTERNAL_CONTENT_URI))
                .build();
        mContentResolver.registerContentObserver(volumeUri, false, observer);

        mCountDownLatch = new CountDownLatch(1);
        final String displayName = "cts" + System.nanoTime();
        final MediaStore.PendingParams params = new MediaStore.PendingParams(
                Downloads.EXTERNAL_CONTENT_URI, displayName, "video/3gp");
        final Uri downloadUri = Uri.parse("https://www.android.com/download?file=testvideo.3gp");
        params.setDownloadUri(downloadUri);
        final Uri pendingUri = MediaStore.createPending(mContext, params);
        assertNotNull(pendingUri);
        mAddedUris.add(pendingUri);
        final Uri publishUri;
        final MediaStore.PendingSession session = MediaStore.openPending(mContext, pendingUri);
        try {
            try (InputStream in = mContext.getResources().openRawResource(R.raw.testvideo);
                 OutputStream out = session.openOutputStream()) {
                android.os.FileUtils.copy(in, out);
            }
            publishUri = session.publish();
        } finally {
            IoUtils.closeQuietly(session);
        }
        mCountDownLatch.await(NOTIFY_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);

        mCountDownLatch = new CountDownLatch(1);
        final String newDisplayName = "cts" + System.nanoTime();
        final ContentValues updateValues = new ContentValues();
        updateValues.put(Downloads.DISPLAY_NAME, newDisplayName);
        assertEquals(1, mContentResolver.update(publishUri, updateValues, null, null));
        mCountDownLatch.await(NOTIFY_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);

        mCountDownLatch = new CountDownLatch(1);
        assertEquals(1, mContentResolver.delete(publishUri, null, null));
        mCountDownLatch.await(NOTIFY_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
    }

    private int getInitialDownloadsCount() {
        try (Cursor cursor = mContentResolver.query(Downloads.EXTERNAL_CONTENT_URI,
                null, null, null, null)) {
            return cursor.getCount();
        }
    }

    private Uri insertImage(String displayName, String description,
            File file, String mimeType, int resourceId) throws Exception {
        file.createNewFile();
        try (InputStream in = mContext.getResources().openRawResource(R.raw.scenery);
             OutputStream out = new FileOutputStream(file)) {
            FileUtils.copy(in, out);
        }

        final ContentValues values = new ContentValues();
        values.put(Images.Media.DISPLAY_NAME, displayName);
        values.put(Images.Media.TITLE, displayName);
        values.put(Images.Media.DESCRIPTION, description);
        values.put(Images.Media.DATA, file.getAbsolutePath());
        values.put(Images.Media.DATE_ADDED, System.currentTimeMillis() / 1000);
        values.put(Images.Media.DATE_MODIFIED, System.currentTimeMillis() / 1000);
        values.put(Images.Media.MIME_TYPE, mimeType);

        final Uri insertUri = mContentResolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        assertNotNull(insertUri);
        mAddedUris.add(insertUri);
        return insertUri;
    }

    private void verifyScannedDownload(File file) throws Exception {
        final Uri mediaStoreUri = scanFile(file);
        Log.e(TAG, "Scanned file " + file.getAbsolutePath() + ": " + mediaStoreUri);
        mAddedUris.add(mediaStoreUri);
        assertArrayEquals("File hashes should match for " + file + " and " + mediaStoreUri,
                hash(new FileInputStream(file)),
                hash(mContentResolver.openInputStream(mediaStoreUri)));

        // Verify the file is part of downloads collection.
        final long id = ContentUris.parseId(mediaStoreUri);
        final Cursor cursor = mContentResolver.query(mExternalDownloads,
                null, MediaStore.Downloads._ID + "=" + id, null, null);
        assertEquals(1, cursor.getCount());
    }

    private Uri scanFile(File file) throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        final Uri[] mediaStoreUris = new Uri[1];
        MediaScannerConnection.scanFile(mContext,
                new String[] {file.getAbsolutePath()},
                null /* mimeType */,
                (String path, Uri uri) -> {
                    mediaStoreUris[0] = uri;
                    latch.countDown();
                });

        latch.await(SCAN_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        assertNotNull("Failed to scan " + file.getAbsolutePath(), mediaStoreUris[0]);
        return mediaStoreUris[0];
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
