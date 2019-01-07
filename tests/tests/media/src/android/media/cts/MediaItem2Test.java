/*
 * Copyright 2018 The Android Open Source Project
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

package android.media.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import android.media.MediaItem2;
import android.media.MediaMetadata;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests {@link android.media.MediaItem2}.
 */
@RunWith(AndroidJUnit4.class)
@SmallTest
public class MediaItem2Test {

    @Test
    public void testBuilder() {
        MediaMetadata meta = createMetadata("test", 1000);
        long startPosition = 100;
        long endPosition = 200;

        MediaItem2.Builder builder = new MediaItem2.Builder()
                .setMetadata(meta).setStartPosition(startPosition).setEndPosition(endPosition);
        MediaItem2 item = builder.build();

        assertEquals(meta, item.getMetadata());
        assertEquals(startPosition, item.getStartPosition());
        assertEquals(endPosition, item.getEndPosition());
    }

    @Test
    public void testBuilder_illegal_end_position() {
        MediaMetadata meta = createMetadata("test", 1000);
        long startPosition = 100;
        long endPosition = 2000;

        MediaItem2.Builder builder = new MediaItem2.Builder()
                .setMetadata(meta).setStartPosition(startPosition).setEndPosition(endPosition);
        try {
            MediaItem2 item = builder.build();
            fail();
        } catch (IllegalArgumentException e) {
            // Expected IllegalArgumentException
        }
    }

    @Test
    public void testBuilder_illegal_start_end_position() {
        MediaMetadata meta = createMetadata("test", 1000);
        long startPosition = 200;
        long endPosition = 100;

        MediaItem2.Builder builder = new MediaItem2.Builder()
                .setMetadata(meta).setStartPosition(startPosition).setEndPosition(endPosition);
        try {
            MediaItem2 item = builder.build();
            fail();
        } catch (IllegalArgumentException e) {
            // Expected IllegalArgumentException
        }
    }

    @Test
    public void testSetMetadata() {
        long startPosition = 100;
        long endPosition = 200;

        MediaItem2.Builder builder = new MediaItem2.Builder()
                .setStartPosition(startPosition).setEndPosition(endPosition);
        MediaItem2 item = builder.build();

        // Set metadata when item's metadata is null
        MediaMetadata meta = createMetadata("test", 1000);
        item.setMetadata(meta);
        assertEquals(meta, item.getMetadata());

        // Set metadata with the same Media Id
        MediaMetadata meta2 = createMetadata("test", 1000);
        item.setMetadata(meta2);
        assertEquals(meta2, item.getMetadata());

        // Set metadata with different Media Id
        MediaMetadata meta3 = createMetadata("test-other", 1000);
        item.setMetadata(meta3);
        // metadata shouldn't be changed
        assertEquals(meta2, item.getMetadata());
    }

    private MediaMetadata createMetadata(String id, long duration) {
        MediaMetadata.Builder builder = new MediaMetadata.Builder();
        builder.putString(MediaMetadata.METADATA_KEY_MEDIA_ID, id);
        builder.putLong(MediaMetadata.METADATA_KEY_DURATION, duration);
        return builder.build();
    }
}

