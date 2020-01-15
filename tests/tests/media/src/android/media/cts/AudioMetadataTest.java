/*
 * Copyright (C) 2020 The Android Open Source Project
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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.testng.Assert.assertThrows;

import android.media.AudioMetadata;
import androidx.test.runner.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;

@NonMediaMainlineTest
@RunWith(AndroidJUnit4.class)
public class AudioMetadataTest {

    // Trial keys to test with.
    private static final AudioMetadata.Key<Integer>
        KEY_INTEGER = AudioMetadata.createKey("integer", Integer.class);
    private static final AudioMetadata.Key<Number>
        KEY_NUMBER = AudioMetadata.createKey("number", Number.class);
    private static final AudioMetadata.Key<String>
        KEY_STRING = AudioMetadata.createKey("string", String.class);

    @Test
    public void testKey() throws Exception {
        assertEquals("integer", KEY_INTEGER.getName());
        assertEquals("number", KEY_NUMBER.getName());
        assertEquals("string", KEY_STRING.getName());

        assertEquals(Integer.class, KEY_INTEGER.getValueClass());
        assertEquals(Number.class, KEY_NUMBER.getValueClass());
        assertEquals(String.class, KEY_STRING.getValueClass());
    }

    @Test
    public void testMap() throws Exception {
        final AudioMetadata.Map audioMetadata = AudioMetadata.createMap();

        int ivalue;
        String svalue;

        audioMetadata.set(KEY_INTEGER, 10);
        ivalue = audioMetadata.get(KEY_INTEGER);
        assertEquals(10, ivalue);

        // Because the get is typed, the following cannot compile.
        // audioMetadata.set(KEY_INTEGER, "abc");
        // String svalue = audioMetadata.get(KEY_INTEGER);

        assertEquals(1, audioMetadata.size());

        audioMetadata.set(KEY_STRING, "abc");
        svalue = audioMetadata.get(KEY_STRING);
        assertEquals("abc", svalue);

        // Because the set is typed, the following cannot compile
        // audioMetadata.set(KEY_STRING, 10);
        // ivalue = audioMetadata.get(KEY_STRING);

        assertEquals(2, audioMetadata.size());
        assertTrue(audioMetadata.containsKey(KEY_STRING));
        // We should be able to remove the string
        svalue = audioMetadata.remove(KEY_STRING);
        assertEquals("abc", svalue);

        assertEquals(1, audioMetadata.size());
        assertFalse(audioMetadata.containsKey(KEY_STRING));
        assertTrue(audioMetadata.containsKey(KEY_INTEGER));

        // Try a generic Number.
        Number nvalue;
        audioMetadata.set(KEY_NUMBER, 2.125f);
        nvalue = audioMetadata.get(KEY_NUMBER);
        assertEquals(2.125f, nvalue.floatValue(), 0.f);

        // Verify we handle null properly.
        assertThrows(NullPointerException.class,
            () -> { audioMetadata.get(null); }
        );
        assertThrows(NullPointerException.class,
            () -> { audioMetadata.set(null, 1); }
        );
        assertThrows(NullPointerException.class,
            () -> { audioMetadata.set(KEY_NUMBER, null); }
        );
    }
}
