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

package android.view.inspector.cts;

import static org.junit.Assert.assertArrayEquals;

import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;
import android.view.inspector.IntFlagMapping;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests for {@link IntFlagMapping}
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public final class IntFlagMappingTest {
    @Test
    public void testNonExclusiveFlags() {
        IntFlagMapping mapping = new IntFlagMapping.Builder()
                .addFlag("ONE", 1)
                .addFlag("TWO", 2)
                .build();

        assertArrayEquals(new String[0], mapping.namesOf(0));
        assertArrayEquals(new String[] {"ONE"}, mapping.namesOf(1));
        assertArrayEquals(new String[] {"TWO"}, mapping.namesOf(2));
        assertArrayEquals(new String[] {"ONE", "TWO"}, mapping.namesOf(3));
        assertArrayEquals(new String[0], mapping.namesOf(4));
    }

    @Test
    public void testMutuallyExclusiveFlags() {
        IntFlagMapping mapping = new IntFlagMapping.Builder()
                .addFlag("ONE", 1, 3)
                .addFlag("TWO", 2, 3)
                .build();


        assertArrayEquals(new String[0], mapping.namesOf(0));
        assertArrayEquals(new String[] {"ONE"}, mapping.namesOf(1));
        assertArrayEquals(new String[] {"TWO"}, mapping.namesOf(2));
        assertArrayEquals(new String[0], mapping.namesOf(3));
        assertArrayEquals(new String[0], mapping.namesOf(4));
    }

    @Test
    public void testMixedFlags() {
        IntFlagMapping mapping = new IntFlagMapping.Builder()
                .addFlag("ONE", 1, 3)
                .addFlag("TWO", 2, 3)
                .addFlag("FOUR", 4)
                .build();


        assertArrayEquals(new String[0], mapping.namesOf(0));
        assertArrayEquals(new String[] {"ONE"}, mapping.namesOf(1));
        assertArrayEquals(new String[] {"TWO"}, mapping.namesOf(2));
        assertArrayEquals(new String[0], mapping.namesOf(3));
        assertArrayEquals(new String[] {"FOUR"}, mapping.namesOf(4));
        assertArrayEquals(new String[] {"ONE", "FOUR"}, mapping.namesOf(5));
        assertArrayEquals(new String[] {"TWO", "FOUR"}, mapping.namesOf(6));
        assertArrayEquals(new String[] {"FOUR"}, mapping.namesOf(7));
    }
}
