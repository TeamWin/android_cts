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

package android.widget.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import android.app.Activity;
import android.graphics.drawable.Icon;
import android.util.AttributeSet;
import android.util.Xml;
import android.view.View;
import android.widget.AnalogClock;

import androidx.test.filters.SmallTest;
import androidx.test.rule.ActivityTestRule;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.xmlpull.v1.XmlPullParser;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class AnalogClockTest {
    private static final String TIME_ZONE_NEW_YORK = "America/New_York";
    private static final String TIME_ZONE_LOS_ANGELES = "America/Los_Angeles";

    private AttributeSet mAttrSet;
    private Activity mActivity;
    private AnalogClock mClock;
    private AnalogClock mClockWithAttrs;

    @Rule
    public ActivityTestRule<FrameLayoutCtsActivity> mActivityRule =
            new ActivityTestRule<>(FrameLayoutCtsActivity.class);

    @Before
    public void setup() throws Exception {
        mActivity = mActivityRule.getActivity();
        XmlPullParser parser = mActivity.getResources().getXml(R.layout.analogclock_layout);
        mAttrSet = Xml.asAttributeSet(parser);

        View layout = mActivity.getLayoutInflater().inflate(R.layout.analogclock_layout, null);
        mClock = layout.findViewById(R.id.clock);
        mClockWithAttrs = layout.findViewById(R.id.clock_with_attrs);
    }

    @Test
    public void testConstructor() {
        new AnalogClock(mActivity);
        new AnalogClock(mActivity, mAttrSet);
        new AnalogClock(mActivity, mAttrSet, 0);
    }

    @Test(expected = NullPointerException.class)
    public void testConstructorWithNullContext1() {
        new AnalogClock(null);
    }

    @Test(expected = NullPointerException.class)
    public void testConstructorWithNullContext2() {
        new AnalogClock(null, null);
    }

    @Test(expected = NullPointerException.class)
    public void testConstructorWithNullContext3() {
        new AnalogClock(null, null, -1);
    }

    @Test
    public void testSetDial() {
        Icon icon = Icon.createWithResource(mActivity, R.drawable.magenta_fill);
        mClock.setDial(icon);
        mClockWithAttrs.setDial(icon);
    }

    @Test(expected = NullPointerException.class)
    public void testSetDialWithNull() {
        mClock.setDial(null);
    }

    @Test
    public void testSetHourHand() {
        Icon icon = Icon.createWithResource(mActivity, R.drawable.magenta_fill);
        mClock.setHourHand(icon);
        mClockWithAttrs.setHourHand(icon);
    }

    @Test(expected = NullPointerException.class)
    public void testSetHourHandWithNull() {
        mClock.setHourHand(null);
    }

    @Test
    public void testSetMinuteHand() {
        Icon icon = Icon.createWithResource(mActivity, R.drawable.magenta_fill);
        mClock.setMinuteHand(icon);
        mClockWithAttrs.setMinuteHand(icon);
    }

    @Test(expected = NullPointerException.class)
    public void testSetMinuteHandWithNull() {
        mClock.setMinuteHand(null);
    }

    @Test
    public void testSetSecondHand() {
        Icon icon = Icon.createWithResource(mActivity, R.drawable.magenta_fill);
        mClock.setSecondHand(icon);
        mClockWithAttrs.setSecondHand(icon);
    }

    @Test
    public void testSetSecondHandWithNull() {
        mClock.setSecondHand(null);
        mClockWithAttrs.setSecondHand(null);
    }

    @Test
    public void testTimeZone() {
        assertNull(mClock.getTimeZone());
        assertEquals(TIME_ZONE_NEW_YORK, mClockWithAttrs.getTimeZone());

        mClock.setTimeZone(TIME_ZONE_NEW_YORK);
        assertEquals(TIME_ZONE_NEW_YORK, mClock.getTimeZone());

        mClock.setTimeZone(TIME_ZONE_LOS_ANGELES);
        assertEquals(TIME_ZONE_LOS_ANGELES, mClock.getTimeZone());

        mClock.setTimeZone("Some/Invalid_time_zone");
        assertNull(mClock.getTimeZone());

        mClock.setTimeZone(TIME_ZONE_NEW_YORK);
        assertEquals(TIME_ZONE_NEW_YORK, mClock.getTimeZone());

        mClock.setTimeZone(null);
        assertNull(mClock.getTimeZone());
    }
}
