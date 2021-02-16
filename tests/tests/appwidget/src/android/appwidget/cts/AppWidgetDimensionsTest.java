/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.appwidget.cts;

import static androidx.test.InstrumentationRegistry.getInstrumentation;
import static org.junit.Assert.assertTrue;

import android.content.res.Resources;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
class AppWidgetDimensionsTest {

    @Test
    public void containerRadius_shouldBePositive() {
        Resources resources = getInstrumentation().getTargetContext().getResources();
        assertTrue(resources.getDimension(android.R.dimen.system_app_widget_background_radius) >= 0);
    }

    @Test
    public void innerRadius_shouldBePositive() {
        Resources resources = getInstrumentation().getTargetContext().getResources();
        assertTrue(resources.getDimension(android.R.dimen.system_app_widget_inner_radius) >= 0);
    }

    @Test
    public void internalPadding_shouldBePositive() {
        Resources resources = getInstrumentation().getTargetContext().getResources();
        assertTrue(resources.getDimension(android.R.dimen.system_app_widget_internal_padding) >= 0);
    }
}
