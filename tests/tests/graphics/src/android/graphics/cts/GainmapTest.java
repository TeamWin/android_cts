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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.ColorSpace;
import android.graphics.Gainmap;
import android.graphics.ImageDecoder;
import android.os.Parcel;

import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayOutputStream;

import junitparams.JUnitParamsRunner;

@SmallTest
@RunWith(JUnitParamsRunner.class)
public class GainmapTest {
    private static final float EPSILON = 0.0001f;

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

    @Test
    public void testDecodeGainmap() throws Exception {
        Bitmap bitmap = ImageDecoder.decodeBitmap(
                ImageDecoder.createSource(sContext.getResources(), R.raw.gainmap),
                (decoder, info, source) -> decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE));
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
    public void testCompressA8() throws Exception {
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
}
