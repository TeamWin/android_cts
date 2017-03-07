/*
 * Copyright (C) 2017 The Android Open Source Project
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

package android.graphics.cts;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ColorSpace;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.io.InputStream;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class BitmapColorSpaceTest {
    private Resources mResources;

    @Before
    public void setup() {
        mResources = InstrumentationRegistry.getTargetContext().getResources();
    }

    @Test
    public void sRGB() {
        Bitmap b = BitmapFactory.decodeResource(mResources, R.drawable.robot);
        ColorSpace cs = b.getColorSpace();
        assertNotNull(cs);
        assertSame(ColorSpace.get(ColorSpace.Named.SRGB), cs);
    }

    @Test
    public void p3() {
        try (InputStream in = mResources.getAssets().open("green-p3.png")) {
            Bitmap b = BitmapFactory.decodeStream(in);
            ColorSpace cs = b.getColorSpace();
            assertNotNull(cs);
            assertSame(ColorSpace.get(ColorSpace.Named.DISPLAY_P3), cs);
        } catch (IOException e) {
            fail();
        }
    }

    @Test
    public void extendedSRGB() {
        try (InputStream in = mResources.getAssets().open("prophoto-rgba16f.png")) {
            Bitmap b = BitmapFactory.decodeStream(in);
            ColorSpace cs = b.getColorSpace();
            assertNotNull(cs);
            assertSame(ColorSpace.get(ColorSpace.Named.LINEAR_EXTENDED_SRGB), cs);
        } catch (IOException e) {
            fail();
        }
    }

    @Test
    public void reconfigure() {
        try (InputStream in = mResources.getAssets().open("green-p3.png")) {
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inMutable = true;

            Bitmap b = BitmapFactory.decodeStream(in, null, opts);
            ColorSpace cs = b.getColorSpace();
            assertNotNull(cs);
            assertSame(ColorSpace.get(ColorSpace.Named.DISPLAY_P3), cs);

            b.reconfigure(b.getWidth() / 2, b.getHeight() / 2, Bitmap.Config.RGBA_F16);
            cs = b.getColorSpace();
            assertNotNull(cs);
            assertSame(ColorSpace.get(ColorSpace.Named.LINEAR_EXTENDED_SRGB), cs);

            b.reconfigure(b.getWidth(), b.getHeight(), Bitmap.Config.ARGB_8888);
            cs = b.getColorSpace();
            assertNotNull(cs);
            assertSame(ColorSpace.get(ColorSpace.Named.DISPLAY_P3), cs);
        } catch (IOException e) {
            fail();
        }
    }
}
