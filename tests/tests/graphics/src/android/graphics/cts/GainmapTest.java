/*
 * Copyright (C) 2023 The Android Open Source Project
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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Color;
import android.graphics.ColorSpace;
import android.graphics.Gainmap;
import android.graphics.ImageDecoder;
import android.graphics.Rect;
import android.os.Parcel;

import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

import junitparams.JUnitParamsRunner;

@SmallTest
@RunWith(JUnitParamsRunner.class)
public class GainmapTest {
    private static final float EPSILON = 0.0001f;
    private static final int TILE_SIZE = 256;

    private static Context sContext;

    static final Bitmap sScalingRedA8;
    static final Bitmap sScalingRed8888;

    static {
        sScalingRedA8 = Bitmap.createBitmap(new int[] {
                Color.RED,
                Color.RED,
                Color.RED,
                Color.RED
        }, 4, 1, Bitmap.Config.ARGB_8888);
        sScalingRedA8.setGainmap(new Gainmap(Bitmap.createBitmap(new int[] {
                0x00000000,
                0x40000000,
                0x80000000,
                0xFF000000
        }, 4, 1, Bitmap.Config.ALPHA_8)));

        sScalingRed8888 = Bitmap.createBitmap(new int[] {
                Color.RED,
                Color.RED,
                Color.RED,
                Color.RED
        }, 4, 1, Bitmap.Config.ARGB_8888);
        sScalingRed8888.setGainmap(new Gainmap(Bitmap.createBitmap(new int[] {
                0xFF000000,
                0xFF404040,
                0xFF808080,
                0xFFFFFFFF
        }, 4, 1, Bitmap.Config.ARGB_8888)));
    }

    @BeforeClass
    public static void setupClass() {
        sContext = InstrumentationRegistry.getInstrumentation().getContext();
    }

    private static void assertAllAre(float expected, float[] value) {
        assertEquals(3, value.length);
        for (int i = 0; i < value.length; i++) {
            assertEquals("value[" + i + "] didn't match " + expected, expected, value[i], EPSILON);
        }
    }

    private static void assertAre(float r, float g, float b, float[] value) {
        assertEquals(3, value.length);
        assertEquals(r, value[0], EPSILON);
        assertEquals(g, value[1], EPSILON);
        assertEquals(b, value[2], EPSILON);
    }

    private void checkGainmap(Bitmap bitmap) throws Exception {
        assertNotNull(bitmap);
        assertTrue("Missing gainmap", bitmap.hasGainmap());
        assertEquals(Bitmap.Config.ARGB_8888, bitmap.getConfig());
        assertEquals(ColorSpace.Named.SRGB.ordinal(), bitmap.getColorSpace().getId());
        Gainmap gainmap = bitmap.getGainmap();
        assertNotNull(gainmap);
        Bitmap gainmapData = gainmap.getGainmapContents();
        assertNotNull(gainmapData);
        assertEquals(Bitmap.Config.ARGB_8888, gainmapData.getConfig());

        assertAllAre(0.f, gainmap.getEpsilonSdr());
        assertAllAre(0.f, gainmap.getEpsilonHdr());
        assertAllAre(1.f, gainmap.getGamma());
        assertEquals(1.f, gainmap.getMinDisplayRatioForHdrTransition(), EPSILON);

        assertAllAre(4f, gainmap.getRatioMax());
        assertAllAre(1.0f, gainmap.getRatioMin());
        assertEquals(5f, gainmap.getDisplayRatioForFullHdr(), EPSILON);
    }

    @Test
    public void testDecodeGainmap() throws Exception {
        Bitmap bitmap = ImageDecoder.decodeBitmap(
                ImageDecoder.createSource(sContext.getResources(), R.raw.gainmap),
                (decoder, info, source) -> decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE));
        checkGainmap(bitmap);
    }

    @Test
    public void testDecodeGainmapBitmapFactory() throws Exception {
        Bitmap bitmap = BitmapFactory.decodeResource(sContext.getResources(), R.raw.gainmap);
        checkGainmap(bitmap);
    }

    @Test
    public void testDecodeGainmapBitmapFactoryReuse() throws Exception {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inMutable = true;
        options.inDensity = 160;
        options.inTargetDensity = 160;

        Bitmap bitmap = BitmapFactory.decodeResource(sContext.getResources(), R.raw.gainmap,
                options);
        checkGainmap(bitmap);
        options.inBitmap = bitmap;
        assertSame(bitmap, BitmapFactory.decodeResource(
                sContext.getResources(), R.drawable.baseline_jpeg, options));
        assertEquals(1280, bitmap.getWidth());
        assertEquals(960, bitmap.getHeight());
        assertFalse(bitmap.hasGainmap());
        assertNull(bitmap.getGainmap());
        assertSame(bitmap, BitmapFactory.decodeResource(
                sContext.getResources(), R.raw.gainmap, options));
        checkGainmap(bitmap);
    }

    @Test
    public void testDecodeGainmapBitmapRegionDecoder() throws Exception {
        InputStream is = sContext.getResources().openRawResource(R.raw.gainmap);
        BitmapRegionDecoder decoder = BitmapRegionDecoder.newInstance(is);
        Bitmap region = decoder.decodeRegion(new Rect(0, 0, TILE_SIZE, TILE_SIZE), null);
        checkGainmap(region);
    }

    @Test
    public void testDecodeGainmapBitmapRegionDecoderReuse() throws Exception {
        InputStream is = sContext.getResources().openRawResource(R.raw.gainmap);
        BitmapRegionDecoder decoder = BitmapRegionDecoder.newInstance(is);
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inMutable = true;
        options.inDensity = 160;
        options.inTargetDensity = 160;
        Bitmap region = decoder.decodeRegion(new Rect(0, 0, TILE_SIZE, TILE_SIZE),
                options);
        checkGainmap(region);
        Bitmap previousGainmap = region.getGainmap().getGainmapContents();
        options.inBitmap = region;

        is = sContext.getResources().openRawResource(R.drawable.baseline_jpeg);
        BitmapRegionDecoder secondDecoder = BitmapRegionDecoder.newInstance(is);
        assertSame(region, secondDecoder.decodeRegion(new Rect(0, 0, TILE_SIZE, TILE_SIZE),
                options));
        assertFalse(region.hasGainmap());
        assertNull(region.getGainmap());

        assertSame(region, decoder.decodeRegion(new Rect(0, 0, TILE_SIZE, TILE_SIZE),
                options));
        checkGainmap(region);
        assertNotSame(previousGainmap, region.getGainmap().getGainmapContents());
    }

    @Test
    public void testDecodeGainmapBitmapRegionDecoderReusePastBounds() throws Exception {
        InputStream is = sContext.getResources().openRawResource(R.raw.gainmap);
        BitmapRegionDecoder decoder = BitmapRegionDecoder.newInstance(is);
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inMutable = true;
        options.inDensity = 160;
        options.inTargetDensity = 160;
        int offsetX = decoder.getWidth() - (TILE_SIZE / 2);
        int offsetY = decoder.getHeight() - (TILE_SIZE / 4);
        Bitmap region = decoder.decodeRegion(new Rect(offsetX, offsetY, offsetX + TILE_SIZE,
                        offsetY + TILE_SIZE), options);
        checkGainmap(region);
        Bitmap gainmap = region.getGainmap().getGainmapContents();
        // Since there's no re-use bitmap, the resulting bitmap size will be the size of the rect
        // that overlaps with the image. 1/2 of the X and 3/4ths of the Y are out of bounds
        assertEquals(TILE_SIZE / 2, region.getWidth());
        assertEquals(TILE_SIZE / 4, region.getHeight());
        // The test image has a 1:1 ratio between base & gainmap
        assertEquals(region.getWidth(), gainmap.getWidth());
        assertEquals(region.getHeight(), gainmap.getHeight());

        options.inBitmap = Bitmap.createBitmap(TILE_SIZE, TILE_SIZE, Bitmap.Config.ARGB_8888);
        region = decoder.decodeRegion(new Rect(offsetX, offsetY, offsetX + TILE_SIZE,
                offsetY + TILE_SIZE), options);
        gainmap = region.getGainmap().getGainmapContents();
        // Although 1/2 the X and 3/4ths the Y are out of bounds, because there's a re-use
        // bitmap the resulting decode must exactly match the size given
        assertEquals(TILE_SIZE, region.getWidth());
        assertEquals(TILE_SIZE, region.getHeight());
        // The test image has a 1:1 ratio between base & gainmap
        assertEquals(region.getWidth(), gainmap.getWidth());
        assertEquals(region.getHeight(), gainmap.getHeight());
    }

    @Test
    public void testDecodeGainmapBitmapRegionDecoderReuseCropped() throws Exception {
        InputStream is = sContext.getResources().openRawResource(R.raw.gainmap);
        BitmapRegionDecoder decoder = BitmapRegionDecoder.newInstance(is);
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inMutable = true;
        options.inDensity = 160;
        options.inTargetDensity = 160;
        options.inBitmap = Bitmap.createBitmap(TILE_SIZE / 2, TILE_SIZE / 2,
                Bitmap.Config.ARGB_8888);
        Bitmap region = decoder.decodeRegion(new Rect(0, 0, TILE_SIZE, TILE_SIZE),
                options);
        checkGainmap(region);
        Bitmap gainmap = region.getGainmap().getGainmapContents();
        // Although the rect was entirely in-bounds of the image, the inBitmap is 1/2th the
        // the specified width/height so make sure the gainmap matches
        assertEquals(TILE_SIZE / 2, region.getWidth());
        assertEquals(TILE_SIZE / 2, region.getHeight());
        // The test image has a 1:1 ratio between base & gainmap
        assertEquals(region.getWidth(), gainmap.getWidth());
        assertEquals(region.getHeight(), gainmap.getHeight());
    }

    @Test
    public void testDefaults() {
        Gainmap gainmap = new Gainmap(Bitmap.createBitmap(10, 10, Bitmap.Config.ALPHA_8));
        assertAllAre(1.0f, gainmap.getRatioMin());
        assertAllAre(2.f, gainmap.getRatioMax());
        assertAllAre(1.f, gainmap.getGamma());
        assertAllAre(0.f, gainmap.getEpsilonSdr());
        assertAllAre(0.f, gainmap.getEpsilonHdr());
        assertEquals(1.f, gainmap.getMinDisplayRatioForHdrTransition(), EPSILON);
        assertEquals(2.f, gainmap.getDisplayRatioForFullHdr(), EPSILON);
    }

    @Test
    public void testSetGet() {
        Gainmap gainmap = new Gainmap(Bitmap.createBitmap(10, 10, Bitmap.Config.ALPHA_8));
        gainmap.setDisplayRatioForFullHdr(5f);
        gainmap.setMinDisplayRatioForHdrTransition(3f);
        gainmap.setGamma(1.1f, 1.2f, 1.3f);
        gainmap.setRatioMin(2.1f, 2.2f, 2.3f);
        gainmap.setRatioMax(3.1f, 3.2f, 3.3f);
        gainmap.setEpsilonSdr(0.1f, 0.2f, 0.3f);
        gainmap.setEpsilonHdr(0.01f, 0.02f, 0.03f);

        assertEquals(5f, gainmap.getDisplayRatioForFullHdr(), EPSILON);
        assertEquals(3f, gainmap.getMinDisplayRatioForHdrTransition(), EPSILON);
        assertAre(1.1f, 1.2f, 1.3f, gainmap.getGamma());
        assertAre(2.1f, 2.2f, 2.3f, gainmap.getRatioMin());
        assertAre(3.1f, 3.2f, 3.3f, gainmap.getRatioMax());
        assertAre(0.1f, 0.2f, 0.3f, gainmap.getEpsilonSdr());
        assertAre(0.01f, 0.02f, 0.03f, gainmap.getEpsilonHdr());
    }

    @Test
    public void testWriteToParcel() throws Exception {
        Bitmap bitmap = ImageDecoder.decodeBitmap(
                ImageDecoder.createSource(sContext.getResources(), R.raw.gainmap),
                (decoder, info, source) -> decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE));
        assertNotNull(bitmap);

        Gainmap gainmap = bitmap.getGainmap();
        assertNotNull(gainmap);
        Bitmap gainmapData = gainmap.getGainmapContents();
        assertNotNull(gainmapData);

        Parcel p = Parcel.obtain();
        gainmap.writeToParcel(p, 0);
        p.setDataPosition(0);

        Gainmap unparceledGainmap = Gainmap.CREATOR.createFromParcel(p);
        assertNotNull(unparceledGainmap);
        Bitmap unparceledGainmapData = unparceledGainmap.getGainmapContents();
        assertNotNull(unparceledGainmapData);

        assertTrue(gainmapData.sameAs(unparceledGainmapData));
        assertEquals(gainmapData.getConfig(), unparceledGainmapData.getConfig());
        assertEquals(gainmapData.getColorSpace(), unparceledGainmapData.getColorSpace());

        assertArrayEquals(gainmap.getEpsilonSdr(), unparceledGainmap.getEpsilonSdr(), 0f);
        assertArrayEquals(gainmap.getEpsilonHdr(), unparceledGainmap.getEpsilonHdr(), 0f);
        assertArrayEquals(gainmap.getGamma(), unparceledGainmap.getGamma(), 0f);
        assertEquals(gainmap.getMinDisplayRatioForHdrTransition(),
                unparceledGainmap.getMinDisplayRatioForHdrTransition(), 0f);

        assertArrayEquals(gainmap.getRatioMax(), unparceledGainmap.getRatioMax(), 0f);
        assertArrayEquals(gainmap.getRatioMin(), unparceledGainmap.getRatioMin(), 0f);
        assertEquals(gainmap.getDisplayRatioForFullHdr(),
                unparceledGainmap.getDisplayRatioForFullHdr(), 0f);
        p.recycle();
    }

    @Test
    public void testCompress8888() throws Exception {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        assertTrue(sScalingRed8888.compress(Bitmap.CompressFormat.JPEG, 100, stream));
        byte[] data = stream.toByteArray();
        Bitmap result = ImageDecoder.decodeBitmap(
                ImageDecoder.createSource(data), (decoder, info, src) -> {
                decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
            });
        assertTrue(result.hasGainmap());
        Bitmap gainmapImage = result.getGainmap().getGainmapContents();
        assertEquals(Bitmap.Config.ARGB_8888, gainmapImage.getConfig());
        Bitmap sourceImage = sScalingRed8888.getGainmap().getGainmapContents();
        for (int x = 0; x < 4; x++) {
            Color expected = sourceImage.getColor(x, 0);
            Color got = gainmapImage.getColor(x, 0);
            assertArrayEquals("Differed at x=" + x,
                    expected.getComponents(), got.getComponents(), 0.05f);
        }
    }

    @Test
    public void testCompressA8ByImageDecoder() throws Exception {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        assertTrue(sScalingRedA8.compress(Bitmap.CompressFormat.JPEG, 100, stream));
        byte[] data = stream.toByteArray();
        Bitmap result = ImageDecoder.decodeBitmap(
                ImageDecoder.createSource(data), (decoder, info, src) -> {
                decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
            });
        assertTrue(result.hasGainmap());
        Bitmap gainmapImage = result.getGainmap().getGainmapContents();
        assertEquals(Bitmap.Config.ALPHA_8, gainmapImage.getConfig());
        Bitmap sourceImage = sScalingRedA8.getGainmap().getGainmapContents();
        for (int x = 0; x < 4; x++) {
            Color expected = sourceImage.getColor(x, 0);
            Color got = gainmapImage.getColor(x, 0);
            assertArrayEquals("Differed at x=" + x,
                    expected.getComponents(), got.getComponents(), 0.05f);
        }
    }

    @Test
    public void testCompressA8ByBitmapRegionDecoder() throws Exception {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        assertTrue(sScalingRedA8.compress(Bitmap.CompressFormat.JPEG, 100, stream));
        byte[] data = stream.toByteArray();
        BitmapRegionDecoder decoder = BitmapRegionDecoder.newInstance(data, 0, data.length);
        Bitmap region = decoder.decodeRegion(new Rect(0, 0, 4, 1), null);
        assertTrue(region.hasGainmap());
        Bitmap gainmapImage = region.getGainmap().getGainmapContents();
        assertEquals(Bitmap.Config.ALPHA_8, gainmapImage.getConfig());
        Bitmap sourceImage = sScalingRedA8.getGainmap().getGainmapContents();
        for (int x = 0; x < 4; x++) {
            Color expected = sourceImage.getColor(x, 0);
            Color got = gainmapImage.getColor(x, 0);
            assertArrayEquals("Differed at x=" + x,
                    expected.getComponents(), got.getComponents(), 0.05f);
        }
    }

    @Test
    public void testCompressA8ByBitmapFactory() throws Exception {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        assertTrue(sScalingRedA8.compress(Bitmap.CompressFormat.JPEG, 100, stream));
        byte[] data = stream.toByteArray();
        Bitmap result = BitmapFactory.decodeByteArray(data, 0, data.length);
        assertTrue(result.hasGainmap());
        Bitmap gainmapImage = result.getGainmap().getGainmapContents();
        assertEquals(Bitmap.Config.ALPHA_8, gainmapImage.getConfig());
        Bitmap sourceImage = sScalingRedA8.getGainmap().getGainmapContents();
        for (int x = 0; x < 4; x++) {
            Color expected = sourceImage.getColor(x, 0);
            Color got = gainmapImage.getColor(x, 0);
            assertArrayEquals("Differed at x=" + x,
                    expected.getComponents(), got.getComponents(), 0.05f);
        }
    }

    @Test
    public void testHardwareGainmapCopy() throws Exception {
        Bitmap bitmap = ImageDecoder.decodeBitmap(
                ImageDecoder.createSource(sContext.getResources(), R.raw.gainmap),
                (decoder, info, source) -> decoder.setAllocator(ImageDecoder.ALLOCATOR_HARDWARE));
        assertNotNull(bitmap);
        assertTrue("Missing gainmap", bitmap.hasGainmap());
        assertEquals(Bitmap.Config.HARDWARE, bitmap.getConfig());

        Gainmap gainmap = bitmap.getGainmap();
        assertNotNull(gainmap);
        Bitmap gainmapData = gainmap.getGainmapContents();
        assertNotNull(gainmapData);
        assertEquals(Bitmap.Config.HARDWARE, gainmapData.getConfig());
    }

    @Test
    public void testCopyPreservesGainmap() throws Exception {
        Bitmap bitmap = ImageDecoder.decodeBitmap(
                ImageDecoder.createSource(sContext.getResources(), R.raw.gainmap),
                (decoder, info, source) -> decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE));
        assertNotNull(bitmap);
        assertTrue("Missing gainmap", bitmap.hasGainmap());

        Bitmap copy = bitmap.copy(Bitmap.Config.ARGB_8888, true);
        assertNotNull(copy);
        assertTrue("Missing gainmap", copy.hasGainmap());
    }
}
