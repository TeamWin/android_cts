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
import static org.junit.Assert.assertNotNull;

import android.view.View;
import android.view.inspector.GeneratedInspectionCompanionProvider;
import android.view.inspector.InspectionCompanion;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests for {@link GeneratedInspectionCompanionProvider}
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class GeneratedInspectionCompanionProviderTest {
    private GeneratedInspectionCompanionProvider mProvider;

    @Before
    public void setup() {
        mProvider = new GeneratedInspectionCompanionProvider();
    }

    @Test
    public void testViewWorks() {
        InspectionCompanion<View> companion = mProvider.provide(View.class);
        assertNotNull(companion);
        assertEquals("android.view.View$$InspectionCompanion", companion.getClass().getName());
    }
}
