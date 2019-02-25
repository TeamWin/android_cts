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

package android.view.inspector.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import android.view.inspector.IntEnumMapping;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests for {@link IntEnumMapping}.
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class IntEnumMappingTest {
    @Test
    public void testSimpleValue() {
        IntEnumMapping mapping = new IntEnumMapping.Builder()
                .addValue("ZERO", 0)
                .build();
        assertEquals("ZERO", mapping.get(0));
    }

    @Test
    public void testMissingValue() {
        IntEnumMapping mapping = new IntEnumMapping.Builder()
                .addValue("ZERO", 0)
                .build();
        assertNull(mapping.get(1));
    }

    @Test
    public void testBuilderReuse() {
        IntEnumMapping.Builder builder = new IntEnumMapping.Builder();

        builder.addValue("ONE", 1);
        IntEnumMapping mapping1 = builder.build();

        assertEquals("ONE", mapping1.get(1));

        builder.addValue("TWO", 2);
        IntEnumMapping mapping2 = builder.build();

        assertEquals("ONE", mapping2.get(1));
        assertEquals("TWO", mapping2.get(2));
        assertNull(mapping1.get(2));
    }

    @Test(expected = NullPointerException.class)
    public void testNullName() {
        new IntEnumMapping.Builder().addValue(null, 0);
    }
}
