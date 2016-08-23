/*
 * Copyright (C) 2008 The Android Open Source Project
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

package android.text.style.cts;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import android.graphics.Rasterizer;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;
import android.text.TextPaint;
import android.text.style.RasterizerSpan;

import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class RasterizerSpanTest {
    @Test
    public void testConstructor() {
        Rasterizer r = new Rasterizer();

        new RasterizerSpan(r);
        new RasterizerSpan(null);
    }

    @Test
    public void testGetRasterizer() {
        Rasterizer expected = new Rasterizer();

        RasterizerSpan rasterizerSpan = new RasterizerSpan(expected);
        assertSame(expected, rasterizerSpan.getRasterizer());

        rasterizerSpan = new RasterizerSpan(null);
        assertNull(rasterizerSpan.getRasterizer());
    }

    @Test
    public void testUpdateDrawState() {
        Rasterizer rasterizer = new Rasterizer();
        RasterizerSpan rasterizerSpan = new RasterizerSpan(rasterizer);

        TextPaint tp = new TextPaint();
        assertNull(tp.getRasterizer());

        rasterizerSpan.updateDrawState(tp);
        assertSame(rasterizer, tp.getRasterizer());
    }

    @Test(expected=NullPointerException.class)
    public void testUpdateDrawStateNull() {
        Rasterizer rasterizer = new Rasterizer();
        RasterizerSpan rasterizerSpan = new RasterizerSpan(rasterizer);

        rasterizerSpan.updateDrawState(null);
    }
}
