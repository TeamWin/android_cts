/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package android.slice.cts;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.app.PendingIntent.CanceledException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.app.slice.Slice;
import android.app.slice.SliceItem;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
public class SliceTest {

    public static boolean sFlag = false;

    private static final Uri BASE_URI = Uri.parse("content://android.slice.cts/");
    private final Context mContext = InstrumentationRegistry.getContext();

    @Test
    public void testProcess() {
        sFlag = false;
        Slice.bindSlice(mContext.getContentResolver(),
                BASE_URI.buildUpon().appendPath("set_flag").build());
        assertFalse(sFlag);
    }

    @Test
    public void testType() {
        assertEquals(SliceProvider.SLICE_TYPE,
                mContext.getContentResolver().getType(BASE_URI));
    }

    @Test
    public void testSliceUri() {
        Slice s = Slice.bindSlice(mContext.getContentResolver(), BASE_URI);
        assertEquals(BASE_URI, s.getUri());
    }

    @Test
    public void testSubSlice() {
        Uri uri = BASE_URI.buildUpon().appendPath("subslice").build();
        Slice s = Slice.bindSlice(mContext.getContentResolver(), uri);
        assertEquals(uri, s.getUri());
        assertEquals(1, s.getItems().size());

        SliceItem item = s.getItems().get(0);
        assertEquals(SliceItem.TYPE_SLICE, item.getType());
        // The item should start with the same Uri as the parent, but be different.
        assertTrue(item.getSlice().getUri().toString().startsWith(uri.toString()));
        assertNotEquals(uri, item.getSlice().getUri());
    }

    @Test
    public void testText() {
        Uri uri = BASE_URI.buildUpon().appendPath("text").build();
        Slice s = Slice.bindSlice(mContext.getContentResolver(), uri);
        assertEquals(uri, s.getUri());
        assertEquals(1, s.getItems().size());

        SliceItem item = s.getItems().get(0);
        assertEquals(SliceItem.TYPE_TEXT, item.getType());
        // TODO: Test spannables here.
        assertEquals("Expected text", item.getText());
    }

    @Test
    public void testIcon() {
        Uri uri = BASE_URI.buildUpon().appendPath("icon").build();
        Slice s = Slice.bindSlice(mContext.getContentResolver(), uri);
        assertEquals(uri, s.getUri());
        assertEquals(1, s.getItems().size());

        SliceItem item = s.getItems().get(0);
        assertEquals(SliceItem.TYPE_IMAGE, item.getType());
        assertEquals(Icon.createWithResource(mContext, R.drawable.size_48x48).toString(),
                item.getIcon().toString());
    }

    @Test
    public void testAction() {
        sFlag = false;
        CountDownLatch latch = new CountDownLatch(1);
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                sFlag = true;
                latch.countDown();
            }
        };
        mContext.registerReceiver(receiver,
                new IntentFilter(mContext.getPackageName() + ".action"));
        Uri uri = BASE_URI.buildUpon().appendPath("action").build();
        Slice s = Slice.bindSlice(mContext.getContentResolver(), uri);
        assertEquals(uri, s.getUri());
        assertEquals(1, s.getItems().size());

        SliceItem item = s.getItems().get(0);
        assertEquals(SliceItem.TYPE_ACTION, item.getType());
        try {
            item.getAction().send();
        } catch (CanceledException e) {
        }

        try {
            latch.await(100, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        assertTrue(sFlag);
        mContext.unregisterReceiver(receiver);
    }

    @Test
    public void testColor() {
        Uri uri = BASE_URI.buildUpon().appendPath("color").build();
        Slice s = Slice.bindSlice(mContext.getContentResolver(), uri);
        assertEquals(uri, s.getUri());
        assertEquals(1, s.getItems().size());

        SliceItem item = s.getItems().get(0);
        assertEquals(SliceItem.TYPE_COLOR, item.getType());
        assertEquals(0xff121212, item.getColor());
    }

    @Test
    public void testTimestamp() {
        Uri uri = BASE_URI.buildUpon().appendPath("timestamp").build();
        Slice s = Slice.bindSlice(mContext.getContentResolver(), uri);
        assertEquals(uri, s.getUri());
        assertEquals(1, s.getItems().size());

        SliceItem item = s.getItems().get(0);
        assertEquals(SliceItem.TYPE_TIMESTAMP, item.getType());
        assertEquals(43, item.getTimestamp());
    }

    @Test
    public void testHints() {
        // Note this tests that hints are propagated through to the client but not that any specific
        // hints have any effects.
        Uri uri = BASE_URI.buildUpon().appendPath("hints").build();
        Slice s = Slice.bindSlice(mContext.getContentResolver(), uri);
        assertEquals(uri, s.getUri());

        assertEquals(Arrays.asList(Slice.HINT_LIST), s.getHints());
        assertEquals(Arrays.asList(Slice.HINT_TITLE), s.getItems().get(0).getHints());
        assertEquals(Arrays.asList(Slice.HINT_NO_TINT, Slice.HINT_LARGE),
                s.getItems().get(1).getHints());
    }
}
