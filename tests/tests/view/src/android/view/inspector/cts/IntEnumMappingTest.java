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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;
import android.view.inspector.IntEnumMapping;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests for {@link IntEnumMapping}
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public final class IntEnumMappingTest {
    @Test
    public void testMapping() {
        IntEnumMapping mapping = new IntEnumMapping.Builder()
                .addValue("ONE", 1)
                .addValue("TWO", 2)
                .build();
        assertNull(mapping.nameOf(0));
        assertEquals("ONE", mapping.nameOf(1));
        assertEquals("TWO", mapping.nameOf(2));
    }
}
