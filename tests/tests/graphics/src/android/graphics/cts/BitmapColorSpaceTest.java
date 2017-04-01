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

import android.annotation.ColorInt;
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
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
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
    public void getPixel() {
        verifyGetPixel("green-p3.png", 0x75fb4cff, 0xff03ff00);
        verifyGetPixel("translucent-green-p3.png", 0x3a7d267f, 0x7f00ff00); // 50% translucent
    }

    private void verifyGetPixel(@NonNull String fileName,
            @ColorInt int rawColor, @ColorInt int srgbColor) {
        try (InputStream in = mResources.getAssets().open(fileName)) {
            Bitmap b = BitmapFactory.decodeStream(in);
            ColorSpace cs = b.getColorSpace();
            assertNotNull(cs);
            assertSame(ColorSpace.get(ColorSpace.Named.DISPLAY_P3), cs);

            ByteBuffer dst = ByteBuffer.allocate(b.getByteCount());
            b.copyPixelsToBuffer(dst);
            dst.rewind();
            // Stored as RGBA
            assertEquals(rawColor, dst.asIntBuffer().get());

            int srgb = b.getPixel(31, 31);
            assertEquals(srgbColor, srgb);
        } catch (IOException e) {
            fail();
        }
    }

    @Test
    public void getPixels() {
        verifyGetPixels("green-p3.png", 0xff03ff00);
        verifyGetPixels("translucent-green-p3.png", 0x7f00ff00); // 50% translucent
    }

    private void verifyGetPixels(@NonNull String fileName, @ColorInt int expected) {
        try (InputStream in = mResources.getAssets().open(fileName)) {
            Bitmap b = BitmapFactory.decodeStream(in);
            ColorSpace cs = b.getColorSpace();
            assertNotNull(cs);
            assertSame(ColorSpace.get(ColorSpace.Named.DISPLAY_P3), cs);

            int[] pixels = new int[b.getWidth() * b.getHeight()];
            b.getPixels(pixels, 0, b.getWidth(), 0, 0, b.getWidth(), b.getHeight());
            for (int pixel : pixels) {
                assertEquals(expected, pixel);
            }
        } catch (IOException e) {
            fail();
        }
    }

    @Test
    public void setPixel() {
        verifySetPixel("green-p3.png", 0xffff0000, 0xea3323ff);
        verifySetPixel("translucent-green-p3.png", 0x7fff0000, 0x7519117f);
    }

    private void verifySetPixel(@NonNull String fileName,
            @ColorInt int newColor, @ColorInt int expectedColor) {
        try (InputStream in = mResources.getAssets().open(fileName)) {
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inMutable = true;

            Bitmap b = BitmapFactory.decodeStream(in, null, opts);
            ColorSpace cs = b.getColorSpace();
            assertNotNull(cs);
            assertSame(ColorSpace.get(ColorSpace.Named.DISPLAY_P3), cs);

            b.setPixel(0, 0, newColor);

            ByteBuffer dst = ByteBuffer.allocate(b.getByteCount());
            b.copyPixelsToBuffer(dst);
            dst.rewind();
            // Stored as RGBA
            assertEquals(expectedColor, dst.asIntBuffer().get());
        } catch (IOException e) {
            fail();
        }
    }

    @Test
    public void setPixels() {
        verifySetPixels("green-p3.png", 0xffff0000, 0xea3323ff);
        verifySetPixels("translucent-green-p3.png", 0x7fff0000, 0x7519117f);
    }

    private void verifySetPixels(@NonNull String fileName,
            @ColorInt int newColor, @ColorInt int expectedColor) {
        try (InputStream in = mResources.getAssets().open(fileName)) {
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inMutable = true;

            Bitmap b = BitmapFactory.decodeStream(in, null, opts);
            ColorSpace cs = b.getColorSpace();
            assertNotNull(cs);
            assertSame(ColorSpace.get(ColorSpace.Named.DISPLAY_P3), cs);

            int[] pixels = new int[b.getWidth() * b.getHeight()];
            Arrays.fill(pixels, newColor);
            b.setPixels(pixels, 0, b.getWidth(), 0, 0, b.getWidth(), b.getHeight());

            ByteBuffer dst = ByteBuffer.allocate(b.getByteCount());
            b.copyPixelsToBuffer(dst);
            dst.rewind();

            IntBuffer buffer = dst.asIntBuffer();
            //noinspection ForLoopReplaceableByForEach
            for (int i = 0; i < pixels.length; i++) {
                // Stored as RGBA
                assertEquals(expectedColor, buffer.get());
            }
        } catch (IOException e) {
            fail();
        }
    }

    @Test
    public void writeColorSpace() {
        verifyColorSpaceMarshalling("green-srgb.png", ColorSpace.get(ColorSpace.Named.SRGB));
        verifyColorSpaceMarshalling("green-p3.png", ColorSpace.get(ColorSpace.Named.DISPLAY_P3));
        verifyColorSpaceMarshalling("prophoto-rgba16f.png",
                ColorSpace.get(ColorSpace.Named.LINEAR_EXTENDED_SRGB));

        // Special case where the color space will be null in native
        Bitmap bitmapIn = BitmapFactory.decodeResource(mResources, R.drawable.robot);
        verifyParcelUnparcel(bitmapIn, ColorSpace.get(ColorSpace.Named.SRGB));
    }

    private void verifyColorSpaceMarshalling(
            @NonNull String fileName, @NonNull ColorSpace colorSpace) {
        try (InputStream in = mResources.getAssets().open(fileName)) {
            Bitmap bitmapIn = BitmapFactory.decodeStream(in);
            verifyParcelUnparcel(bitmapIn, colorSpace);
        } catch (IOException e) {
            fail();
        }
    }

    private void verifyParcelUnparcel(Bitmap bitmapIn, ColorSpace expected) {
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

    @Test
    public void p3rgb565() {
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inPreferredConfig = Bitmap.Config.RGB_565;

        try (InputStream in = mResources.getAssets().open("green-p3.png")) {
            Bitmap b = BitmapFactory.decodeStream(in, null, opts);
            ColorSpace cs = b.getColorSpace();
            assertNotNull(cs);
            assertSame(ColorSpace.get(ColorSpace.Named.SRGB), cs);
        } catch (IOException e) {
            fail();
        }
    }

    @Test
    public void p3hardware() {
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inPreferredConfig = Bitmap.Config.HARDWARE;

        try (InputStream in = mResources.getAssets().open("green-p3.png")) {
            Bitmap b = BitmapFactory.decodeStream(in, null, opts);
            ColorSpace cs = b.getColorSpace();
            assertNotNull(cs);
            assertSame(ColorSpace.get(ColorSpace.Named.DISPLAY_P3), cs);
        } catch (IOException e) {
            fail();
        }
    }

    @Test
    public void guessSRGB() {
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inJustDecodeBounds = true;

        try (InputStream in = mResources.getAssets().open("green-srgb.png")) {
            Bitmap b = BitmapFactory.decodeStream(in, null, opts);
            ColorSpace cs = opts.outColorSpace;
            assertNull(b);
            assertNotNull(cs);
            assertSame(ColorSpace.get(ColorSpace.Named.SRGB), cs);
        } catch (IOException e) {
            fail();
        }
    }

    @Test
    public void guessProPhotoRGB() {
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inJustDecodeBounds = true;

        try (InputStream in = mResources.getAssets().open("prophoto-rgba16f.png")) {
            Bitmap b = BitmapFactory.decodeStream(in, null, opts);
            ColorSpace cs = opts.outColorSpace;
            assertNull(b);
            assertNotNull(cs);
            assertSame(ColorSpace.get(ColorSpace.Named.LINEAR_EXTENDED_SRGB), cs);
        } catch (IOException e) {
            fail();
        }
    }

    @Test
    public void guessP3() {
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inJustDecodeBounds = true;

        try (InputStream in = mResources.getAssets().open("green-p3.png")) {
            Bitmap b = BitmapFactory.decodeStream(in, null, opts);
            ColorSpace cs = opts.outColorSpace;
            assertNull(b);
            assertNotNull(cs);
            assertSame(ColorSpace.get(ColorSpace.Named.DISPLAY_P3), cs);
        } catch (IOException e) {
            fail();
        }
    }

    @Test
    public void guessUnknown() {
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inJustDecodeBounds = true;

        try (InputStream in = mResources.getAssets().open("purple-displayprofile.png")) {
            Bitmap b = BitmapFactory.decodeStream(in, null, opts);
            ColorSpace cs = opts.outColorSpace;
            assertNull(b);
            assertNotNull(cs);
            assertEquals("Unknown", cs.getName());
        } catch (IOException e) {
            fail();
        }
    }

    @Test
    public void guessCMYK() {
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inJustDecodeBounds = true;

        try (InputStream in = mResources.getAssets().open("purple-cmyk.png")) {
            Bitmap b = BitmapFactory.decodeStream(in, null, opts);
            ColorSpace cs = opts.outColorSpace;
            assertNull(b);
            assertNotNull(cs);
            assertSame(ColorSpace.get(ColorSpace.Named.SRGB), cs);
        } catch (IOException e) {
            fail();
        }
    }
}
