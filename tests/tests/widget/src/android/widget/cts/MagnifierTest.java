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

package android.widget.cts;

import android.app.Activity;
import android.support.test.annotation.UiThreadTest;
import android.support.test.filters.SmallTest;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
import android.widget.Magnifier;

import com.android.compatibility.common.util.PollingCheck;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests for {@link Magnifier}.
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class MagnifierTest {
    private Activity mActivity;
    private LinearLayout mMagnifierLayout;

    @Rule
    public ActivityTestRule<MagnifierCtsActivity> mActivityRule =
            new ActivityTestRule<>(MagnifierCtsActivity.class);

    @Before
    public void setup() {
        mActivity = mActivityRule.getActivity();
        PollingCheck.waitFor(mActivity::hasWindowFocus);
        mMagnifierLayout = mActivity.findViewById(R.id.magnifier_layout);
    }

    @Test
    public void testConstructor() {
        new Magnifier(new View(mActivity));
    }

    @Test(expected = NullPointerException.class)
    public void testConstructor_NPE() {
        new Magnifier(null);
    }

    @Test
    @UiThreadTest
    public void testShow() {
        View view = new View(mActivity);
        mMagnifierLayout.addView(view, new LayoutParams(200, 200));
        Magnifier magnifier = new Magnifier(view);
        // Valid coordinates.
        magnifier.show(0, 0);
        // Invalid coordinates, should both be clamped to 0.
        magnifier.show(-1, -1);
        // Valid coordinates.
        magnifier.show(10, 10);
        // Same valid coordinate as above, should skip making another copy request.
        magnifier.show(10, 10);
    }

    @Test
    @UiThreadTest
    public void testDismiss() {
        View view = new View(mActivity);
        mMagnifierLayout.addView(view, new LayoutParams(200, 200));
        Magnifier magnifier = new Magnifier(view);
        // Valid coordinates.
        magnifier.show(10, 10);
        magnifier.dismiss();
        // Should be no-op.
        magnifier.dismiss();
    }
}
