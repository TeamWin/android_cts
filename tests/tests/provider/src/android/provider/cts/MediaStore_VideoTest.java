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

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore.Video;
import android.provider.MediaStore.Video.VideoColumns;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.FileCopyHelper;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;

@RunWith(AndroidJUnit4.class)
public class MediaStore_VideoTest {
    private static final String TEST_VIDEO_3GP = "testVideo.3gp";

    private ArrayList<Uri> mRowsAdded;

    private Context mContext;

    private ContentResolver mContentResolver;

    private FileCopyHelper mHelper;

    @After
    public void tearDown() throws Exception {
        for (Uri row : mRowsAdded) {
            mContentResolver.delete(row, null, null);
        }
        mHelper.clear();
    }

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getTargetContext();
        mContentResolver = mContext.getContentResolver();
        mHelper = new FileCopyHelper(mContext);
        mRowsAdded = new ArrayList<Uri>();
    }

    @Test
    public void testQuery() throws Exception {
        ContentValues values = new ContentValues();
        String valueOfData = mHelper.copy(R.raw.testvideo, TEST_VIDEO_3GP);
        values.put(VideoColumns.DATA, valueOfData);

        Uri newUri = mContentResolver.insert(Video.Media.INTERNAL_CONTENT_URI, values);
        if (!Video.Media.INTERNAL_CONTENT_URI.equals(newUri)) {
            mRowsAdded.add(newUri);
        }

        Cursor c = Video.query(mContentResolver, newUri, new String[] { VideoColumns.DATA });
        assertEquals(1, c.getCount());
        c.moveToFirst();
        assertEquals(valueOfData, c.getString(c.getColumnIndex(VideoColumns.DATA)));
        c.close();
    }
}
