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
package android.transition.cts;

import android.support.test.rule.ActivityTestRule;
import android.test.suitebuilder.annotation.SmallTest;
import android.transition.Slide;
import android.transition.TransitionManager;
import android.view.Gravity;
import android.view.View;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

@RunWith(Parameterized.class)
public class SlideBadEdgeTest {
    private final int mBadEdge;
    private final String mEdgeName;

    @Parameters(name = "Slide edge({1})")
    public static Iterable<Object[]> data() {
        Object[][] badGravity = {
                { Gravity.AXIS_CLIP, "AXIS_CLIP" },
                { Gravity.AXIS_PULL_AFTER, "AXIS_PULL_AFTER" },
                { Gravity.AXIS_PULL_BEFORE, "AXIS_PULL_BEFORE" },
                { Gravity.AXIS_SPECIFIED, "AXIS_SPECIFIED" },
                { Gravity.AXIS_Y_SHIFT, "AXIS_Y_SHIFT" },
                { Gravity.AXIS_X_SHIFT, "AXIS_X_SHIFT" },
                { Gravity.CENTER, "CENTER" },
                { Gravity.CLIP_VERTICAL, "CLIP_VERTICAL" },
                { Gravity.CLIP_HORIZONTAL, "CLIP_HORIZONTAL" },
                { Gravity.CENTER_VERTICAL, "CENTER_VERTICAL" },
                { Gravity.CENTER_HORIZONTAL, "CENTER_HORIZONTAL" },
                { Gravity.DISPLAY_CLIP_VERTICAL, "DISPLAY_CLIP_VERTICAL" },
                { Gravity.DISPLAY_CLIP_HORIZONTAL, "DISPLAY_CLIP_HORIZONTAL" },
                { Gravity.FILL_VERTICAL, "FILL_VERTICAL" },
                { Gravity.FILL, "FILL" },
                { Gravity.FILL_HORIZONTAL, "FILL_HORIZONTAL" },
                { Gravity.HORIZONTAL_GRAVITY_MASK, "HORIZONTAL_GRAVITY_MASK" },
                { Gravity.NO_GRAVITY, "NO_GRAVITY" },
                { Gravity.RELATIVE_LAYOUT_DIRECTION, "RELATIVE_LAYOUT_DIRECTION" },
                { Gravity.RELATIVE_HORIZONTAL_GRAVITY_MASK, "RELATIVE_HORIZONTAL_GRAVITY_MASK" },
                { Gravity.VERTICAL_GRAVITY_MASK, "VERTICAL_GRAVITY_MASK" },
        };
        return Arrays.asList(badGravity);
    }

    public SlideBadEdgeTest(int badEdge, String edgeName) {
        mBadEdge = badEdge;
        mEdgeName = edgeName;
    }

    @Test
    @SmallTest
    public void testBadSide() {
        try {
            Slide slide = new Slide(mBadEdge);
            fail("Should not be able to set slide edge to " + mEdgeName);
        } catch (IllegalArgumentException e) {
            // expected
        }

        try {
            Slide slide = new Slide();
            slide.setSlideEdge(mBadEdge);
            fail("Should not be able to set slide edge to " + mEdgeName);
        } catch (IllegalArgumentException e) {
            // expected
        }
    }
}

