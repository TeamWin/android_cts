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

package android.graphics.drawable.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Path;
import android.graphics.Path.Direction;
import android.graphics.RectF;
import android.graphics.Region;
import android.graphics.Region.Op;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.ColorDrawable;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;
import android.util.Log;
import android.util.PathParser;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class AdaptiveIconMaskTest {

    public static final String TAG = AdaptiveIconMaskTest.class.getSimpleName();
    public static void L(String s, Object... parts) {
        Log.d(TAG, (parts.length == 0) ? s : String.format(s, parts));
    }
    private static final double SAFEZONE_INSET = .1;
    private static final double DELTA = .01f;
    private Path mMask = new Path();
    private Path mSafeZone = new Path();
    private ColorDrawable mBlueDrawable;
    private ColorDrawable mRedDrawable;
    private AdaptiveIconDrawable mDrawable;

    @Before
    public void setup() {
        mBlueDrawable = new ColorDrawable(Color.BLUE);
        mRedDrawable = new ColorDrawable(Color.RED);
        mDrawable = new AdaptiveIconDrawable( mBlueDrawable, mRedDrawable);

        String path = Resources.getSystem().getString(com.android.internal.R.string.config_icon_mask);
        L("config_icon_mask: " + path);
        mMask = PathParser.createPathFromPathData(path);
        int sInset = (int) (SAFEZONE_INSET * AdaptiveIconDrawable.MASK_SIZE);
        mSafeZone.addCircle(AdaptiveIconDrawable.MASK_SIZE/2, AdaptiveIconDrawable.MASK_SIZE/2,
            AdaptiveIconDrawable.MASK_SIZE/2/2 - sInset, Direction.CW);
    }

    @Test
    public void testDeviceConfigMask_bounds() {
        assertNotNull(mMask);

        // Bounds should be [100 x 100]
        RectF bounds = new RectF();
        mMask.computeBounds(bounds, true);
        System.out.println("MERONG:" + bounds.toShortString());
        assertTrue("Mask top should be larger than or equal to 0", -DELTA <= bounds.top);
        assertTrue("Mask left should be larger than or equal to 0", -DELTA <= bounds.left);
        assertTrue("Mask bottom should be smaller than or equal to" +
                AdaptiveIconDrawable.MASK_SIZE,
            AdaptiveIconDrawable.MASK_SIZE + DELTA >= bounds.bottom);
        assertTrue("Mask right should be smaller than or equal to " +
                AdaptiveIconDrawable.MASK_SIZE,
            AdaptiveIconDrawable.MASK_SIZE + DELTA >= bounds.right);
    }

    @Test
    public void testDeviceConfigMask_largerThanSafezone() {
        assertNotNull(mMask);

        // MASK intersection SafeZone = SafeZone
        Region maskRegion = new Region(0, 0, (int) AdaptiveIconDrawable.MASK_SIZE,
            (int) AdaptiveIconDrawable.MASK_SIZE);
        maskRegion.setPath(mMask, maskRegion);

        Region safeZoneRegion = new Region(0, 0, (int) AdaptiveIconDrawable.MASK_SIZE,
            (int) AdaptiveIconDrawable.MASK_SIZE);
        safeZoneRegion.setPath(mSafeZone, safeZoneRegion);

        Region intersectRegion = new Region();
        boolean result = maskRegion.op(safeZoneRegion, intersectRegion, Region.Op.INTERSECT);

        // resultRegion should be exactly same as the safezone
        // Test if intersectRegion is smaller than safeZone, in which case test will fail.
        Region subtractRegion = new Region();
        result = safeZoneRegion.op(intersectRegion, subtractRegion, Op.DIFFERENCE);
        assertFalse("Mask is not respecting the safezone.", result);
    }

    @Test
    public void testDeviceConfigMask_isConvex() {
        assertNotNull(mMask);

        assertTrue("Mask is not convex", mMask.isConvex());
    }

    @Test
    public void testGetLayers() {
        assertEquals("Background layer is not the same.",
            mBlueDrawable, mDrawable.getBackground());
        assertEquals("Foreground layer is not the same.",
            mRedDrawable, mDrawable.getForeground());
    }
}
