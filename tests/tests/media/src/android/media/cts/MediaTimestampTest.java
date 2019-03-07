/*
 * Copyright 2019 The Android Open Source Project
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

import android.media.MediaTimestamp;
import android.test.AndroidTestCase;

/**
 * Tests for MediaTimestamp.
 */
public class MediaTimestampTest extends AndroidTestCase {
    public void testMediaTimestamp() {
        MediaTimestamp timestamp = new MediaTimestamp.Builder()
            .setMediaTimestamp(1000, 2000, 2.0f)
            .build();
        assertEquals(1000, timestamp.getAnchorMediaTimeUs());
        assertEquals(2000, timestamp.getAnchorSystemNanoTime());
        assertEquals(2.0f, timestamp.getMediaClockRate());
    }

    public void testMediaTimestampCopyCtor() {
        MediaTimestamp timestamp = new MediaTimestamp.Builder()
            .setMediaTimestamp(10000, 20000, 1.0f)
            .build();
        MediaTimestamp copy = new MediaTimestamp.Builder(timestamp).build();
        assertEquals(10000, copy.getAnchorMediaTimeUs());
        assertEquals(20000, copy.getAnchorSystemNanoTime());
        assertEquals(1.0f, copy.getMediaClockRate());
    }
}
