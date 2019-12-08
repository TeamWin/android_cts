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

package android.provider.cts.media;

import static android.provider.cts.ProviderTestUtils.containsId;
import static android.provider.cts.media.MediaStoreTest.TAG;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.provider.cts.ProviderTestUtils;
import android.provider.cts.R;
import android.text.format.DateUtils;
import android.util.Log;

import androidx.test.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class MediaStoreTrashedTest {
    private Context mContext;
    private ContentResolver mResolver;

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
        mResolver = mContext.getContentResolver();

        Log.d(TAG, "Using volume " + mVolumeName);
        mExternalImages = MediaStore.Images.Media.getContentUri(mVolumeName);
    }

    @Test
    public void testSimple() throws Exception {
        // Confirm that we have at least two images staged
        final Uri red = ProviderTestUtils.stageMedia(R.raw.scenery, mExternalImages);
        final Uri blue = ProviderTestUtils.stageMedia(R.raw.scenery, mExternalImages);

        final long redId = ContentUris.parseId(red);
        final long blueId = ContentUris.parseId(blue);

        // By default, trashed isn't visible
        MediaStore.trash(mContext, red);
        assertFalse(containsId(mExternalImages, Bundle.EMPTY, redId));
        assertTrue(containsId(mExternalImages, Bundle.EMPTY, blueId));

        // But we can force reveal it
        final Bundle extras = new Bundle();
        extras.putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_INCLUDE);
        assertTrue(containsId(mExternalImages, extras, redId));
        assertTrue(containsId(mExternalImages, extras, blueId));

        // And if we untrash it, it's visible again
        MediaStore.untrash(mContext, red);
        assertTrue(containsId(mExternalImages, Bundle.EMPTY, redId));
        assertTrue(containsId(mExternalImages, Bundle.EMPTY, blueId));

        // Trashing with giant timeout also works (it'll probably be clamped)
        MediaStore.trash(mContext, red, DateUtils.YEAR_IN_MILLIS);
        assertFalse(containsId(mExternalImages, Bundle.EMPTY, redId));
        assertTrue(containsId(mExternalImages, Bundle.EMPTY, blueId));
    }
}
