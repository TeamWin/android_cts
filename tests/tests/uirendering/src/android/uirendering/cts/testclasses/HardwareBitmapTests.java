/*
 * Copyright (C) 2016 The Android Open Source Project
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

package android.uirendering.cts.testclasses;

import android.graphics.BitmapRegionDecoder;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.uirendering.cts.R;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.support.test.filters.MediumTest;
import android.uirendering.cts.bitmapcomparers.ExactComparer;
import android.uirendering.cts.bitmapcomparers.MSSIMComparer;
import android.uirendering.cts.bitmapverifiers.GoldenImageVerifier;
import android.uirendering.cts.testinfrastructure.ActivityTestBase;

import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;

@MediumTest
public class HardwareBitmapTests extends ActivityTestBase {

    @Test
    public void testDecodeResource() {
        createTest().addCanvasClient((canvas, width, height) -> {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inPreferredConfig = Bitmap.Config.HARDWARE;
            Bitmap hardwareBitmap = BitmapFactory.decodeResource(
                    getActivity().getResources(), R.drawable.robot, options);
            canvas.drawBitmap(hardwareBitmap, 0, 0, new Paint());
        }, true).runWithVerifier(new GoldenImageVerifier(getActivity(),
                R.drawable.golden_robot, new ExactComparer()));
    }

    @Test
    public void testBitmapRegionDecode() throws IOException {
        InputStream inputStream = getActivity().getResources().openRawResource(R.drawable.robot);
        BitmapRegionDecoder decoder = BitmapRegionDecoder.newInstance(inputStream, false);
        createTest().addCanvasClient((canvas, width, height) -> {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inPreferredConfig = Bitmap.Config.HARDWARE;
            Bitmap hardwareBitmap = decoder.decodeRegion(new Rect(10, 15, 34, 39), options);
            canvas.drawBitmap(hardwareBitmap, 0, 0, new Paint());
        }, true).runWithVerifier(new GoldenImageVerifier(getActivity(),
                R.drawable.golden_headless_robot, new ExactComparer()));
    }

    @Test
    public void testBitmapConfigFromRGB565() {
        testBitmapCopy(R.drawable.robot, Bitmap.Config.RGB_565, Bitmap.Config.HARDWARE);
    }

    @Test
    public void testBitmapConfigFromARGB8888() {
        testBitmapCopy(R.drawable.robot, Bitmap.Config.ARGB_8888, Bitmap.Config.HARDWARE);
    }

    @Test
    public void testBitmapConfigFromA8() {
        Bitmap b = Bitmap.createBitmap(32, 32, Bitmap.Config.ALPHA_8);
        // we do not support conversion from A8
        Assert.assertNull(b.copy(Bitmap.Config.HARDWARE, false));
    }

    @Test
    public void testBitmapConfigFromIndex8() {
        testBitmapCopy(R.drawable.index_8, null, Bitmap.Config.HARDWARE);
    }

    @Test
    public void testBitmapConfigFromHardwareToHardware() {
        testBitmapCopy(R.drawable.robot, Bitmap.Config.HARDWARE, Bitmap.Config.HARDWARE);
    }

    @Test
    public void testBitmapConfigFromHardwareToARGB8888() {
        testBitmapCopy(R.drawable.robot, Bitmap.Config.HARDWARE, Bitmap.Config.ARGB_8888);
    }

    private void testBitmapCopy(int id, Bitmap.Config from, Bitmap.Config to) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inScaled = false;
        options.inPreferredConfig = from;
        Bitmap bitmap = BitmapFactory.decodeResource(getActivity().getResources(), id, options);
        Assert.assertEquals(from, bitmap.getConfig());

        createTest().addCanvasClient((canvas, width, height) -> {
            canvas.drawColor(Color.CYAN);
            canvas.drawBitmap(bitmap, 0, 0, null);
        }, true).addCanvasClient((canvas, width, height) -> {
            canvas.drawColor(Color.CYAN);
            Bitmap copy = bitmap.copy(to, false);
            Assert.assertNotNull(copy);
            Assert.assertEquals(to, copy.getConfig());
            canvas.drawBitmap(copy, 0, 0, null);
        }, true).runWithComparer(new MSSIMComparer(0.99));
    }
}