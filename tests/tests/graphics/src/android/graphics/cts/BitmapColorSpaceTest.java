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

import android.annotation.NonNull;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ColorSpace;
import android.os.Parcel;
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

    @Test
    public void reuse() {
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inMutable = true;

        Bitmap bitmap1 = null;
        try (InputStream in = mResources.getAssets().open("green-srgb.png")) {
            bitmap1 = BitmapFactory.decodeStream(in, null, opts);
            ColorSpace cs = bitmap1.getColorSpace();
            assertNotNull(cs);
            assertSame(ColorSpace.get(ColorSpace.Named.SRGB), cs);
        } catch (IOException e) {
            fail();
        }

        try (InputStream in = mResources.getAssets().open("green-p3.png")) {
            opts.inBitmap = bitmap1;

            Bitmap bitmap2 = BitmapFactory.decodeStream(in, null, opts);
            assertSame(bitmap1, bitmap2);
            ColorSpace cs = bitmap2.getColorSpace();
            assertNotNull(cs);
            assertSame(ColorSpace.get(ColorSpace.Named.DISPLAY_P3), cs);
        } catch (IOException e) {
            fail();
        }
    }

    @Test
    public void writeColorSpace() {
        testColorSpaceMarshalling("green-srgb.png", ColorSpace.get(ColorSpace.Named.SRGB));
        testColorSpaceMarshalling("green-p3.png", ColorSpace.get(ColorSpace.Named.DISPLAY_P3));
        testColorSpaceMarshalling("prophoto-rgba16f.png",
                ColorSpace.get(ColorSpace.Named.LINEAR_EXTENDED_SRGB));

        // Special case where the color space will be null in native
        Bitmap bitmapIn = BitmapFactory.decodeResource(mResources, R.drawable.robot);
        testParcelUnparcel(bitmapIn, ColorSpace.get(ColorSpace.Named.SRGB));
    }

    private void testColorSpaceMarshalling(
            @NonNull String fileName, @NonNull ColorSpace colorSpace) {
        try (InputStream in = mResources.getAssets().open(fileName)) {
            Bitmap bitmapIn = BitmapFactory.decodeStream(in);
            testParcelUnparcel(bitmapIn, colorSpace);
        } catch (IOException e) {
            fail();
        }
    }

    private void testParcelUnparcel(Bitmap bitmapIn, ColorSpace expected) {
        ColorSpace cs = bitmapIn.getColorSpace();
        assertNotNull(cs);
        assertSame(expected, cs);

        Parcel p = Parcel.obtain();
        bitmapIn.writeToParcel(p, 0);
        p.setDataPosition(0);

        Bitmap bitmapOut = Bitmap.CREATOR.createFromParcel(p);
        cs = bitmapOut.getColorSpace();
        assertNotNull(cs);
        assertSame(expected, cs);

        p.recycle();
    }
}
