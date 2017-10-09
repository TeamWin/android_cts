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
package android.graphics.cts;

import android.content.res.Resources;
import android.graphics.Color;
import android.test.AndroidTestCase;
import android.util.TypedValue;

import java.util.Arrays;
import java.util.List;

public class ColorTest extends AndroidTestCase {

    public void testResourceColor() {
        int colors [][] = {
                { 0xff000000, android.R.color.black },
                { 0xffaaaaaa, android.R.color.darker_gray },
                { 0xff00ddff, android.R.color.holo_blue_bright },
                { 0xff0099cc, android.R.color.holo_blue_dark },
                { 0xff33b5e5, android.R.color.holo_blue_light },
                { 0xff669900, android.R.color.holo_green_dark },
                { 0xff99cc00, android.R.color.holo_green_light },
                { 0xffff8800, android.R.color.holo_orange_dark },
                { 0xffffbb33, android.R.color.holo_orange_light },
                { 0xffaa66cc, android.R.color.holo_purple },
                { 0xffcc0000, android.R.color.holo_red_dark },
                { 0xffff4444, android.R.color.holo_red_light },
                { 0x00000000, android.R.color.transparent },
                { 0xffffffff, android.R.color.white },
        };

        Resources resources = mContext.getResources();
        for (int[] pair : colors) {
            final int resourceId = pair[1];
            final int expectedColor = pair[0];

            // validate color from getColor
            int observedColor = resources.getColor(resourceId);
            assertEquals("Color = " + Integer.toHexString(observedColor) + ", "
                            + Integer.toHexString(expectedColor) + " expected",
                    expectedColor,
                    observedColor);

            // validate color from getValue
            TypedValue value = new TypedValue();
            resources.getValue(resourceId, value, true);
            assertEquals("Color should be expected value", expectedColor, value.data);

            // colors should be raw ints
            assertTrue("Type should be int", value.type >= TypedValue.TYPE_FIRST_INT
                    && value.type <= TypedValue.TYPE_LAST_INT);
        }
    }

    public void testAlpha() {
        assertEquals(0xff, Color.alpha(Color.RED));
        assertEquals(0xff, Color.alpha(Color.YELLOW));
        new Color();
    }

    public void testArgb(){
        assertEquals(Color.RED, Color.argb(0xff, 0xff, 0x00, 0x00));
        assertEquals(Color.YELLOW, Color.argb(0xff, 0xff, 0xff, 0x00));
    }

    public void testBlue(){
        assertEquals(0x00, Color.blue(Color.RED));
        assertEquals(0x00, Color.blue(Color.YELLOW));
    }

    public void testGreen(){
        assertEquals(0x00, Color.green(Color.RED));
        assertEquals(0xff, Color.green(Color.GREEN));
    }

    public void testHSVToColor1(){
        //abnormal case: hsv length less than 3
        try{
            float[] hsv = new float[2];
            Color.HSVToColor(hsv);
            fail("shouldn't come to here");
        }catch(RuntimeException e){
            //expected
        }

        float[] hsv = new float[3];
        Color.colorToHSV(Color.RED, hsv);
        assertEquals(Color.RED, Color.HSVToColor(hsv));
    }

    public void testHSVToColor2(){
        //abnormal case: hsv length less than 3
        try{
            float[] hsv = new float[2];
            Color.HSVToColor(hsv);
            fail("shouldn't come to here");
        }catch(RuntimeException e){
            //expected
        }

        float[] hsv = new float[3];
        Color.colorToHSV(Color.RED, hsv);
        assertEquals(Color.RED, Color.HSVToColor(0xff, hsv));
    }

    public void testParseColor(){
        //abnormal case: colorString starts with '#' but length is neither 7 nor 9
        try{
            Color.parseColor("#ff00ff0");
            fail("should come to here");
        }catch(IllegalArgumentException e){
            //expected
        }

        assertEquals(Color.RED, Color.parseColor("#ff0000"));
        assertEquals(Color.RED, Color.parseColor("#ffff0000"));

        //abnormal case: colorString doesn't start with '#' and is unknown color
        try{
            Color.parseColor("hello");
            fail("should come to here");
        }catch(IllegalArgumentException e){
            //expected
        }

        assertEquals(Color.BLACK, Color.parseColor("black"));
        assertEquals(Color.DKGRAY, Color.parseColor("darkgray"));
        assertEquals(Color.GRAY, Color.parseColor("gray"));
        assertEquals(Color.LTGRAY, Color.parseColor("lightgray"));
        assertEquals(Color.WHITE, Color.parseColor("white"));
        assertEquals(Color.RED, Color.parseColor("red"));
        assertEquals(Color.GREEN, Color.parseColor("green"));
        assertEquals(Color.BLUE, Color.parseColor("blue"));
        assertEquals(Color.YELLOW, Color.parseColor("yellow"));
        assertEquals(Color.CYAN, Color.parseColor("cyan"));
        assertEquals(Color.MAGENTA, Color.parseColor("magenta"));
    }

    public void testRed(){
        assertEquals(0xff, Color.red(Color.RED));
        assertEquals(0xff, Color.red(Color.YELLOW));
    }

    public void testRgb(){
        assertEquals(Color.RED, Color.rgb(0xff, 0x00, 0x00));
        assertEquals(Color.YELLOW, Color.rgb(0xff, 0xff, 0x00));
    }

    public void testRGBToHSV(){
        //abnormal case: hsv length less than 3
        try{
            float[] hsv = new float[2];
            Color.RGBToHSV(0xff, 0x00, 0x00, hsv);
            fail("shouldn't come to here");
        }catch(RuntimeException e){
            //expected
        }

        float[] hsv = new float[3];
        Color.RGBToHSV(0xff, 0x00, 0x00, hsv);
        assertEquals(Color.RED, Color.HSVToColor(hsv));
    }
}
