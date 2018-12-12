/*
 * Copyright (C) 2009 The Android Open Source Project
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
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.app.usage.StorageStatsManager;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.media.MediaScanner;
import android.net.Uri;
import android.os.Environment;
import android.os.storage.StorageManager;
import android.provider.MediaStore;
import android.provider.MediaStore.MediaColumns;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;

import libcore.util.HexEncoding;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@RunWith(AndroidJUnit4.class)
public class MediaStoreTest {
    private static final String TEST_VOLUME_NAME = "volume_for_cts";

    private static final long SIZE_DELTA = 32_000;

    private static final String[] PROJECTION = new String[] { MediaStore.MEDIA_SCANNER_VOLUME };

    private Uri mScannerUri;

    private String mVolumnBackup;

    private ContentResolver mContentResolver;

    private Context getContext() {
        return InstrumentationRegistry.getTargetContext();
    }

    @Before
    public void setUp() throws Exception {
        mScannerUri = MediaStore.getMediaScannerUri();
        mContentResolver = getContext().getContentResolver();

        Cursor c = mContentResolver.query(mScannerUri, PROJECTION, null, null, null);
        if (c != null) {
            c.moveToFirst();
            mVolumnBackup = c.getString(0);
            c.close();
        }
    }

    @After
    public void tearDown() throws Exception {
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .dropShellPermissionIdentity();

        // restore initial values
        if (mVolumnBackup != null) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.MEDIA_SCANNER_VOLUME, mVolumnBackup);
            mContentResolver.insert(mScannerUri, values);
        }
    }

    @Test
    public void testGetMediaScannerUri() {
        ContentValues values = new ContentValues();
        String selection = MediaStore.MEDIA_SCANNER_VOLUME + "=?";
        String[] selectionArgs = new String[] { TEST_VOLUME_NAME };

        // assert there is no item with name TEST_VOLUME_NAME
        assertNull(mContentResolver.query(mScannerUri, PROJECTION,
                selection, selectionArgs, null));

        // insert
        values.put(MediaStore.MEDIA_SCANNER_VOLUME, TEST_VOLUME_NAME);
        assertEquals(MediaStore.getMediaScannerUri(),
                mContentResolver.insert(mScannerUri, values));

        // query
        Cursor c = mContentResolver.query(mScannerUri, PROJECTION,
                selection, selectionArgs, null);
        assertEquals(1, c.getCount());
        c.moveToFirst();
        assertEquals(TEST_VOLUME_NAME, c.getString(0));
        c.close();

        // delete
        assertEquals(1, mContentResolver.delete(mScannerUri, null, null));
        assertNull(mContentResolver.query(mScannerUri, PROJECTION, null, null, null));
    }

    @Test
    public void testGetVersion() {
        // Could be a version string or null...just check it doesn't blow up.
        MediaStore.getVersion(getContext());
    }

    @Test
    public void testGetAllVolumeNames() {
        Set<String> volumeNames = MediaStore.getAllVolumeNames(getContext());

        // At very least should contain these two volumes
        assertTrue(volumeNames.contains("internal"));
        assertTrue(volumeNames.contains("external"));
    }

    @Test
    public void testContributedMedia() throws Exception {
        // STOPSHIP: remove this once isolated storage is always enabled
        Assume.assumeTrue(StorageManager.hasIsolatedStorage());

        InstrumentationRegistry.getInstrumentation().getUiAutomation().adoptShellPermissionIdentity(
                android.Manifest.permission.CLEAR_APP_USER_DATA,
                android.Manifest.permission.PACKAGE_USAGE_STATS);

        // Measure usage before
        final long beforePackage = getExternalPackageSize();
        final long beforeTotal = getExternalTotalSize();
        final long beforeContributed = MediaStore.getContributedMediaSize(getContext(),
                getContext().getPackageName(), android.os.Process.myUserHandle());

        final long stageSize;
        try (AssetFileDescriptor fd = getContext().getResources()
                .openRawResourceFd(R.raw.volantis)) {
            stageSize = fd.getLength();
        }

        // Create media both inside and outside sandbox
        final Uri inside;
        final Uri outside;
        final File file = new File(getContext().getExternalMediaDirs()[0],
                "cts" + System.nanoTime());
        ProviderTestUtils.stageFile(R.raw.volantis, file);
        try (MediaScanner scanner = new MediaScanner(getContext(), "external")) {
            inside = scanner.scanSingleFile(file.getAbsolutePath(), "image/jpeg");
        }
        outside = ProviderTestUtils.stageMedia(R.raw.volantis,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI);

        {
            final HashSet<Long> visible = getVisibleIds(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            assertTrue(visible.contains(ContentUris.parseId(inside)));
            assertTrue(visible.contains(ContentUris.parseId(outside)));

            final long afterPackage = getExternalPackageSize();
            final long afterTotal = getExternalTotalSize();
            final long afterContributed = MediaStore.getContributedMediaSize(getContext(),
                    getContext().getPackageName(), android.os.Process.myUserHandle());

            assertMostlyEquals(beforePackage + stageSize, afterPackage, SIZE_DELTA);
            assertMostlyEquals(beforeTotal + stageSize + stageSize, afterTotal, SIZE_DELTA);
            assertMostlyEquals(beforeContributed + stageSize, afterContributed, SIZE_DELTA);
        }

        // Delete only contributed items
        MediaStore.deleteContributedMedia(getContext(), getContext().getPackageName(),
                android.os.Process.myUserHandle());
        {
            final HashSet<Long> visible = getVisibleIds(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            assertTrue(visible.contains(ContentUris.parseId(inside)));
            assertFalse(visible.contains(ContentUris.parseId(outside)));

            final long afterPackage = getExternalPackageSize();
            final long afterTotal = getExternalTotalSize();
            final long afterContributed = MediaStore.getContributedMediaSize(getContext(),
                    getContext().getPackageName(), android.os.Process.myUserHandle());

            assertMostlyEquals(beforePackage + stageSize, afterPackage, SIZE_DELTA);
            assertMostlyEquals(beforeTotal + stageSize, afterTotal, SIZE_DELTA);
            assertMostlyEquals(beforeContributed, afterContributed, SIZE_DELTA);
        }
    }

    @Test
    public void testHash() throws Exception {
        final ContentResolver resolver = getContext().getContentResolver();

        final Uri uri = ProviderTestUtils.stageMedia(R.raw.volantis,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI);

        final String expected = Arrays
                .toString(HexEncoding.decode("dd41258ce8d306163f3b727603cb064be81973db"));

        // We can force hash to be generated by requesting canonicalization
        resolver.canonicalize(uri);
        try (Cursor c = resolver.query(uri, new String[] { MediaColumns.HASH }, null, null)) {
            assertTrue(c.moveToFirst());
            assertEquals(expected, Arrays.toString(c.getBlob(0)));
        }

        // Make sure that editing image results in a different hash
        try (OutputStream out = resolver.openOutputStream(uri)) {
            out.write(42);
        }
        try (Cursor c = resolver.query(uri, new String[] { MediaColumns.HASH }, null, null)) {
            assertTrue(c.moveToFirst());
            assertNotEquals(expected, Arrays.toString(c.getBlob(0)));
        }
    }

    private long getExternalPackageSize() throws Exception {
        final StorageManager storage = getContext().getSystemService(StorageManager.class);
        final StorageStatsManager stats = getContext().getSystemService(StorageStatsManager.class);

        final UUID externalUuid = storage.getUuidForPath(Environment.getExternalStorageDirectory());
        return stats.queryStatsForPackage(externalUuid, getContext().getPackageName(),
                android.os.Process.myUserHandle()).getDataBytes();
    }

    private long getExternalTotalSize() throws Exception {
        final StorageManager storage = getContext().getSystemService(StorageManager.class);
        final StorageStatsManager stats = getContext().getSystemService(StorageStatsManager.class);

        final UUID externalUuid = storage.getUuidForPath(Environment.getExternalStorageDirectory());
        return stats.queryExternalStatsForUser(externalUuid, android.os.Process.myUserHandle())
                .getTotalBytes();
    }

    private HashSet<Long> getVisibleIds(Uri collectionUri) {
        final HashSet<Long> res = new HashSet<>();
        try (Cursor c = mContentResolver.query(collectionUri,
                new String[] { MediaColumns._ID }, null, null)) {
            while (c.moveToNext()) {
                res.add(c.getLong(0));
            }
        }
        return res;
    }

    private static void assertMostlyEquals(long expected, long actual, long delta) {
        if (Math.abs(expected - actual) > delta) {
            fail("Expected roughly " + expected + " but was " + actual);
        }
    }
}
