/*
 * Copyright (C) 2019 The Android Open Source Project
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
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.MediaStore.MediaColumns;
import android.util.Log;
import android.util.Pair;

import androidx.test.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import java.io.File;

@RunWith(Parameterized.class)
public class MediaStorePlacementTest {
    static final String TAG = "MediaStorePlacementTest";

    private Context mContext;
    private ContentResolver mContentResolver;

    private Uri mExternalImages;

    @Parameter(0)
    public String mVolumeName;

    @Parameters
    public static Iterable<? extends Object> data() {
        return ProviderTestUtils.getSharedVolumeNames();
    }

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getTargetContext();
        mContentResolver = mContext.getContentResolver();

        Log.d(TAG, "Using volume " + mVolumeName);
        mExternalImages = MediaStore.Images.Media.getContentUri(mVolumeName);
    }

    @Test
    public void testDefault() throws Exception {
        final Uri uri = ProviderTestUtils.stageMedia(R.drawable.scenery,
                mExternalImages, "image/jpeg");

        // By default placed under "Pictures" with sane name
        final File before = ProviderTestUtils.getRelativeFile(uri);
        assertTrue(before.getName().startsWith("cts"));
        assertTrue(before.getName().endsWith("jpg"));
        assertEquals("Pictures", before.getParent());
    }

    @Test
    public void testIgnored() throws Exception {
        final Uri uri = ProviderTestUtils.stageMedia(R.drawable.scenery,
                mExternalImages, "image/jpeg");

        {
            final ContentValues values = new ContentValues();
            values.put(MediaColumns.SIZE, 0);
            assertEquals(0, mContentResolver.update(uri, values, null, null));
        }

        // Make sure shady paths can't be passed in
        for (String column : new String[] {
                MediaColumns.DISPLAY_NAME,
                MediaColumns.PRIMARY_DIRECTORY,
                MediaColumns.SECONDARY_DIRECTORY
        }) {
            final ContentValues values = new ContentValues();
            values.put(column, "path/to/file");
            try {
                mContentResolver.update(uri, values, null, null);
                fail();
            } catch (IllegalArgumentException expected) {
            }
        }
    }

    @Test
    public void testDisplayName_SameMime() throws Exception {
        final Uri uri = ProviderTestUtils.stageMedia(R.drawable.scenery,
                mExternalImages, "image/jpeg");

        // Movement within same MIME type is okay
        final File before = ProviderTestUtils.getRelativeFile(uri);
        final String name = "CTS" +  System.nanoTime() + ".JPEG";
        assertTrue(updatePlacement(uri, Pair.create(MediaColumns.DISPLAY_NAME, name)));

        final File after = ProviderTestUtils.getRelativeFile(uri);
        assertEquals(before.getParent(), after.getParent());
        assertEquals(name, after.getName());
    }

    @Test
    public void testDisplayName_DifferentMime() throws Exception {
        final Uri uri = ProviderTestUtils.stageMedia(R.drawable.scenery,
                mExternalImages, "image/jpeg");

        final File before = ProviderTestUtils.getRelativeFile(uri);
        assertTrue(before.getName().endsWith(".jpg"));

        // Movement across MIME types is not okay; verify that original MIME
        // type remains intact
        final String name = "cts" +  System.nanoTime() + ".png";
        assertTrue(updatePlacement(uri, Pair.create(MediaColumns.DISPLAY_NAME, name)));

        final File after = ProviderTestUtils.getRelativeFile(uri);
        assertTrue(after.getName().startsWith(name));
        assertTrue(after.getName().endsWith(".jpg"));
    }

    @Test
    public void testDirectory_Valid() throws Exception {
        final Uri uri = ProviderTestUtils.stageMedia(R.drawable.scenery,
                mExternalImages, "image/jpeg");

        final File before = ProviderTestUtils.getRelativeFile(uri);
        assertEquals("Pictures", before.getParent());

        {
            assertTrue(updatePlacement(uri,
                    Pair.create(MediaColumns.PRIMARY_DIRECTORY, null)));
            final File after = ProviderTestUtils.getRelativeFile(uri);
            assertEquals("Pictures", after.getParent());
        }
        {
            assertTrue(updatePlacement(uri,
                    Pair.create(MediaColumns.PRIMARY_DIRECTORY, Environment.DIRECTORY_DCIM),
                    Pair.create(MediaColumns.SECONDARY_DIRECTORY, "Vacation")));
            final File after = ProviderTestUtils.getRelativeFile(uri);
            assertEquals("DCIM/Vacation", after.getParent());
        }
        {
            assertTrue(updatePlacement(uri,
                    Pair.create(MediaColumns.SECONDARY_DIRECTORY, "Misc")));
            final File after = ProviderTestUtils.getRelativeFile(uri);
            assertEquals("DCIM/Misc", after.getParent());
        }
        {
            assertTrue(updatePlacement(uri,
                    Pair.create(MediaColumns.PRIMARY_DIRECTORY, Environment.DIRECTORY_PICTURES)));
            final File after = ProviderTestUtils.getRelativeFile(uri);
            assertEquals("Pictures/Misc", after.getParent());
        }
        {
            assertTrue(updatePlacement(uri,
                    Pair.create(MediaColumns.SECONDARY_DIRECTORY, null)));
            final File after = ProviderTestUtils.getRelativeFile(uri);
            assertEquals("Pictures", after.getParent());
        }
    }

    @Test
    public void testDirectory_Invalid() throws Exception {
        final Uri uri = ProviderTestUtils.stageMedia(R.drawable.scenery,
                mExternalImages, "image/jpeg");

        assertFalse(updatePlacement(uri,
                Pair.create(MediaColumns.PRIMARY_DIRECTORY, "Random")));
        assertFalse(updatePlacement(uri,
                Pair.create(MediaColumns.PRIMARY_DIRECTORY, Environment.DIRECTORY_ALARMS)));
    }

    private boolean updatePlacement(Uri uri, Pair<?, ?>... args) throws Exception {
        final ContentValues values = new ContentValues();
        for (Pair<?, ?> arg : args) {
            if (arg.second != null) {
                values.put(String.valueOf(arg.first), String.valueOf(arg.second));
            } else {
                values.putNull(String.valueOf(arg.first));
            }
        }
        try {
            return (mContentResolver.update(uri, values, null, null) == 1);
        } catch (Exception tolerated) {
            return false;
        }
    }
}
