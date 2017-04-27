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
 * limitations under the License
 */

package android.app.cts;

import android.app.WallpaperColors;
import android.graphics.Color;
import android.os.Debug;
import android.os.Parcel;
import android.support.test.runner.AndroidJUnit4;
import android.util.Pair;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;

@RunWith(AndroidJUnit4.class)
public class WallpaperColorsTest {

    @Test
    public void getWallpaperColorsTest() {
        ArrayList<Pair<Color, Integer>> colorList = new ArrayList<>();
        colorList.add(new Pair<>(Color.valueOf(Color.WHITE), 5));
        colorList.add(new Pair<>(Color.valueOf(Color.BLACK), 5));

        WallpaperColors colors = new WallpaperColors(colorList);
        Assert.assertSame(colors.getColors(), colorList);
    }

    @Test
    public void supportsDarkTextOverrideTest() {
        ArrayList<Pair<Color, Integer>> colorList = new ArrayList<>();
        colorList.add(new Pair<>(Color.valueOf(Color.BLACK), 5));

        // Black should not support dark text!
        WallpaperColors colors = new WallpaperColors(colorList);
        Assert.assertFalse(colors.supportsDarkText());

        // Override it
        colors = new WallpaperColors(colorList, true);
        Assert.assertTrue(colors.supportsDarkText());
    }

    @Test
    public void equalsTest() {
        ArrayList<Pair<Color, Integer>> list1 = new ArrayList<>();
        list1.add(new Pair<>(Color.valueOf(Color.BLACK), 5));
        WallpaperColors colors1 = new WallpaperColors(list1);

        ArrayList<Pair<Color, Integer>> list2 = new ArrayList<>();
        list2.add(new Pair<>(Color.valueOf(Color.WHITE), 1));
        WallpaperColors colors2 = new WallpaperColors(list2);

        // Different list
        Assert.assertNotEquals(colors1, null);
        Assert.assertNotEquals(colors1, colors2);

        // List with same values
        ArrayList<Pair<Color, Integer>> list3 = new ArrayList<>();
        list3.add(new Pair<>(Color.valueOf(Color.BLACK), 5));
        WallpaperColors colors3 = new WallpaperColors(list3);
        Assert.assertEquals(colors1, colors3);
        Assert.assertEquals(colors1.hashCode(), colors3.hashCode());

        // same values but different overrides
        WallpaperColors colors4 = new WallpaperColors(list1, true);
        WallpaperColors colors5 = new WallpaperColors(list1, false);
        Assert.assertNotEquals(colors4, colors5);
    }

    @Test
    public void parcelTest() {
        ArrayList<Pair<Color, Integer>> colorList = new ArrayList<>();
        colorList.add(new Pair<>(Color.valueOf(Color.WHITE), 5));
        colorList.add(new Pair<>(Color.valueOf(Color.BLACK), 3));
        WallpaperColors wallpaperColors = new WallpaperColors(colorList);

        Parcel parcel = Parcel.obtain();
        wallpaperColors.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        WallpaperColors newColors = new WallpaperColors(parcel);
        Assert.assertEquals(wallpaperColors, newColors);
        Assert.assertEquals(parcel.dataPosition(), parcel.dataSize());
        parcel.recycle();
    }

}
