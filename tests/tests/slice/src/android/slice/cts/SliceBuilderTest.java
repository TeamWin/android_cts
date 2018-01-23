/*
 * Copyright (C) 2018 The Android Open Source Project
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.PendingIntent;
import android.app.RemoteInput;
import android.app.slice.Slice;
import android.app.slice.SliceItem;
import android.app.slice.SliceSpec;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Bundle;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;
import android.util.ArraySet;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;

@RunWith(AndroidJUnit4.class)
public class SliceBuilderTest {

    private static final Uri BASE_URI = Uri.parse("content://android.slice.cts/");
    private final Context mContext = InstrumentationRegistry.getContext();

    @Test
    public void testInt() {
        Slice s = new Slice.Builder(BASE_URI)
                .addInt(0xff121212, "subtype", Slice.HINT_TITLE)
                .build();
        assertEquals(BASE_URI, s.getUri());
        assertEquals(1, s.getItems().size());

        SliceItem item = s.getItems().get(0);
        assertEquals(SliceItem.FORMAT_INT, item.getFormat());
        assertEquals(0xff121212, item.getInt());
        assertEquals("subtype", item.getSubType());
        assertTrue(item.hasHint(Slice.HINT_TITLE));
    }

    @Test
    public void testIntList() {
        Slice s = new Slice.Builder(BASE_URI)
                .addInt(0xff121212, "subtype", Arrays.asList(Slice.HINT_TITLE))
                .build();
        assertEquals(BASE_URI, s.getUri());
        assertEquals(1, s.getItems().size());

        SliceItem item = s.getItems().get(0);
        assertEquals(SliceItem.FORMAT_INT, item.getFormat());
        assertEquals(0xff121212, item.getInt());
        assertEquals("subtype", item.getSubType());
        assertTrue(item.hasHint(Slice.HINT_TITLE));
    }

    @Test
    public void testIcon() {
        Icon i = Icon.createWithResource(mContext, R.drawable.size_48x48);
        Slice s = new Slice.Builder(BASE_URI)
                .addIcon(i, "subtype", Slice.HINT_TITLE)
                .build();
        assertEquals(BASE_URI, s.getUri());
        assertEquals(1, s.getItems().size());

        SliceItem item = s.getItems().get(0);
        assertEquals(SliceItem.FORMAT_IMAGE, item.getFormat());
        assertEquals(i, item.getIcon());
        assertEquals("subtype", item.getSubType());
        assertTrue(item.hasHint(Slice.HINT_TITLE));
    }

    @Test
    public void testIconList() {
        Icon i = Icon.createWithResource(mContext, R.drawable.size_48x48);
        Slice s = new Slice.Builder(BASE_URI)
                .addIcon(i, "subtype", Arrays.asList(Slice.HINT_TITLE))
                .build();
        assertEquals(BASE_URI, s.getUri());
        assertEquals(1, s.getItems().size());

        SliceItem item = s.getItems().get(0);
        assertEquals(SliceItem.FORMAT_IMAGE, item.getFormat());
        assertEquals(i, item.getIcon());
        assertEquals("subtype", item.getSubType());
        assertTrue(item.hasHint(Slice.HINT_TITLE));
    }

    @Test
    public void testText() {
        CharSequence i = "Some text";
        Slice s = new Slice.Builder(BASE_URI)
                .addText(i, "subtype", Slice.HINT_TITLE)
                .build();
        assertEquals(BASE_URI, s.getUri());
        assertEquals(1, s.getItems().size());

        SliceItem item = s.getItems().get(0);
        assertEquals(SliceItem.FORMAT_TEXT, item.getFormat());
        assertEquals(i, item.getText());
        assertEquals("subtype", item.getSubType());
        assertTrue(item.hasHint(Slice.HINT_TITLE));
    }

    @Test
    public void testTextList() {
        CharSequence i = "Some text";
        Slice s = new Slice.Builder(BASE_URI)
                .addText(i, "subtype", Arrays.asList(Slice.HINT_TITLE))
                .build();
        assertEquals(BASE_URI, s.getUri());
        assertEquals(1, s.getItems().size());

        SliceItem item = s.getItems().get(0);
        assertEquals(SliceItem.FORMAT_TEXT, item.getFormat());
        assertEquals(i, item.getText());
        assertEquals("subtype", item.getSubType());
        assertTrue(item.hasHint(Slice.HINT_TITLE));
    }

    @Test
    public void testTimestamp() {
        long i = 43L;
        Slice s = new Slice.Builder(BASE_URI)
                .addTimestamp(i, "subtype", Slice.HINT_TITLE)
                .build();
        assertEquals(BASE_URI, s.getUri());
        assertEquals(1, s.getItems().size());

        SliceItem item = s.getItems().get(0);
        assertEquals(SliceItem.FORMAT_TIMESTAMP, item.getFormat());
        assertEquals(i, item.getTimestamp());
        assertEquals("subtype", item.getSubType());
        assertTrue(item.hasHint(Slice.HINT_TITLE));
    }

    @Test
    public void testTimestampList() {
        long i = 43L;
        Slice s = new Slice.Builder(BASE_URI)
                .addTimestamp(i, "subtype", Arrays.asList(Slice.HINT_TITLE))
                .build();
        assertEquals(BASE_URI, s.getUri());
        assertEquals(1, s.getItems().size());

        SliceItem item = s.getItems().get(0);
        assertEquals(SliceItem.FORMAT_TIMESTAMP, item.getFormat());
        assertEquals(i, item.getTimestamp());
        assertEquals("subtype", item.getSubType());
        assertTrue(item.hasHint(Slice.HINT_TITLE));
    }

    @Test
    public void testRemoteInput() {
        RemoteInput i = new RemoteInput.Builder("key").build();
        Slice s = new Slice.Builder(BASE_URI)
                .addRemoteInput(i, "subtype", Slice.HINT_TITLE)
                .build();
        assertEquals(BASE_URI, s.getUri());
        assertEquals(1, s.getItems().size());

        SliceItem item = s.getItems().get(0);
        assertEquals(SliceItem.FORMAT_REMOTE_INPUT, item.getFormat());
        assertEquals(i, item.getRemoteInput());
        assertEquals("subtype", item.getSubType());
        assertTrue(item.hasHint(Slice.HINT_TITLE));
    }

    @Test
    public void testRemoteInputList() {
        RemoteInput i = new RemoteInput.Builder("key").build();
        Slice s = new Slice.Builder(BASE_URI)
                .addRemoteInput(i, "subtype", Arrays.asList(Slice.HINT_TITLE))
                .build();
        assertEquals(BASE_URI, s.getUri());
        assertEquals(1, s.getItems().size());

        SliceItem item = s.getItems().get(0);
        assertEquals(SliceItem.FORMAT_REMOTE_INPUT, item.getFormat());
        assertEquals(i, item.getRemoteInput());
        assertEquals("subtype", item.getSubType());
        assertTrue(item.hasHint(Slice.HINT_TITLE));
    }

    @Test
    public void testBundle() {
        Bundle i = new Bundle();
        Slice s = new Slice.Builder(BASE_URI)
                .addBundle(i, "subtype", Slice.HINT_TITLE)
                .build();
        assertEquals(BASE_URI, s.getUri());
        assertEquals(1, s.getItems().size());

        SliceItem item = s.getItems().get(0);
        assertEquals(SliceItem.FORMAT_BUNDLE, item.getFormat());
        assertEquals(i, item.getBundle());
        assertEquals("subtype", item.getSubType());
        assertTrue(item.hasHint(Slice.HINT_TITLE));
    }

    @Test
    public void testBundleList() {
        Bundle i = new Bundle();
        Slice s = new Slice.Builder(BASE_URI)
                .addBundle(i, "subtype", Arrays.asList(Slice.HINT_TITLE))
                .build();
        assertEquals(BASE_URI, s.getUri());
        assertEquals(1, s.getItems().size());

        SliceItem item = s.getItems().get(0);
        assertEquals(SliceItem.FORMAT_BUNDLE, item.getFormat());
        assertEquals(i, item.getBundle());
        assertEquals("subtype", item.getSubType());
        assertTrue(item.hasHint(Slice.HINT_TITLE));
    }

    @Test
    public void testAction() {
        PendingIntent i = PendingIntent.getActivity(mContext, 0, new Intent(), 0);
        Slice subSlice = new Slice.Builder(BASE_URI.buildUpon().appendPath("s").build()).build();
        Slice s = new Slice.Builder(BASE_URI)
                .addAction(i, subSlice)
                .build();
        assertEquals(BASE_URI, s.getUri());
        assertEquals(1, s.getItems().size());

        SliceItem item = s.getItems().get(0);
        assertEquals(SliceItem.FORMAT_ACTION, item.getFormat());
        assertEquals(i, item.getAction());
        assertEquals(null, item.getSubType());
        assertEquals(subSlice, item.getSlice());
    }

    @Test
    public void testActionSubtype() {
        PendingIntent i = PendingIntent.getActivity(mContext, 0, new Intent(), 0);
        Slice subSlice = new Slice.Builder(BASE_URI.buildUpon().appendPath("s").build()).build();
        Slice s = new Slice.Builder(BASE_URI)
                .addAction(i, subSlice, "subtype")
                .build();
        assertEquals(BASE_URI, s.getUri());
        assertEquals(1, s.getItems().size());

        SliceItem item = s.getItems().get(0);
        assertEquals(SliceItem.FORMAT_ACTION, item.getFormat());
        assertEquals(i, item.getAction());
        assertEquals("subtype", item.getSubType());
        assertEquals(subSlice, item.getSlice());
    }

    @Test
    public void testSubslice() {
        Slice subSlice = new Slice.Builder(BASE_URI.buildUpon().appendPath("s").build()).build();
        Slice s = new Slice.Builder(BASE_URI)
                .addSubSlice(subSlice)
                .build();
        assertEquals(BASE_URI, s.getUri());
        assertEquals(1, s.getItems().size());

        SliceItem item = s.getItems().get(0);
        assertEquals(SliceItem.FORMAT_SLICE, item.getFormat());
        assertEquals(null, item.getSubType());
        assertEquals(subSlice, item.getSlice());
    }

    @Test
    public void testSubsliceSubtype() {
        Slice subSlice = new Slice.Builder(BASE_URI.buildUpon().appendPath("s").build()).build();
        Slice s = new Slice.Builder(BASE_URI)
                .addSubSlice(subSlice, "subtype")
                .build();
        assertEquals(BASE_URI, s.getUri());
        assertEquals(1, s.getItems().size());

        SliceItem item = s.getItems().get(0);
        assertEquals(SliceItem.FORMAT_SLICE, item.getFormat());
        assertEquals("subtype", item.getSubType());
        assertEquals(subSlice, item.getSlice());
    }

    @Test
    public void testSpec() {
        Slice s = new Slice.Builder(BASE_URI)
                .setSpec(new SliceSpec("spec", 3))
                .build();
        assertEquals(BASE_URI, s.getUri());
        assertEquals(0, s.getItems().size());
        assertEquals(new SliceSpec("spec", 3), s.getSpec());
    }
}
