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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.cts.util.WidgetTestUtils;
import android.support.test.InstrumentationRegistry;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.test.suitebuilder.annotation.SmallTest;
import android.util.AttributeSet;
import android.util.Xml;
import android.view.ViewGroup;
import android.widget.AbsoluteLayout;
import android.widget.AbsoluteLayout.LayoutParams;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class AbsoluteLayoutTest {
    private static final int DEFAULT_X      = 5;
    private static final int DEFAULT_Y      = 10;
    private static final int DEFAULT_WIDTH  = 20;
    private static final int DEFAULT_HEIGHT = 30;

    private Instrumentation mInstrumentation;
    private Activity mActivity;
    private MyAbsoluteLayout mMyAbsoluteLayout;
    private LayoutParams mAbsoluteLayoutParams;

    @Rule
    public ActivityTestRule<CtsActivity> mActivityRule
            = new ActivityTestRule<>(CtsActivity.class);

    @Before
    public void setup() {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mActivity = mActivityRule.getActivity();
        mMyAbsoluteLayout = new MyAbsoluteLayout(mActivity);
        mAbsoluteLayoutParams = new LayoutParams(DEFAULT_WIDTH, DEFAULT_HEIGHT,
                DEFAULT_X, DEFAULT_Y);
    }

    private AttributeSet getAttributeSet() throws XmlPullParserException, IOException {
        XmlPullParser parser = mActivity.getResources().getLayout(R.layout.absolute_layout);
        WidgetTestUtils.beginDocument(parser, "LinearLayout");
        return Xml.asAttributeSet(parser);
    }

    @Test
    public void testConstructor() throws XmlPullParserException, IOException {
        AttributeSet attrs = getAttributeSet();

        new AbsoluteLayout(mActivity);
        new AbsoluteLayout(mActivity, attrs);
        new AbsoluteLayout(mActivity, attrs, 0);
        new AbsoluteLayout(mActivity, null, 1);
        new AbsoluteLayout(mActivity, attrs, -1);
    }

    @Test
    public void testCheckLayoutParams() {
        assertTrue(mMyAbsoluteLayout.checkLayoutParams(mAbsoluteLayoutParams));

        ViewGroup.LayoutParams layoutParams = new ViewGroup.LayoutParams(1, 2);
        assertFalse(mMyAbsoluteLayout.checkLayoutParams(layoutParams));
        assertFalse(mMyAbsoluteLayout.checkLayoutParams(null));
    }

    @Test
    public void testGenerateLayoutParams1() throws Throwable {
        mInstrumentation.runOnMainSync(() -> mActivity.setContentView(R.layout.absolute_layout));
        mInstrumentation.waitForIdleSync();
        AbsoluteLayout layout = (AbsoluteLayout) mActivity.findViewById(R.id.absolute_view);
        LayoutParams params = (LayoutParams) layout.generateLayoutParams(getAttributeSet());

        assertNotNull(params);
        assertEquals(LayoutParams.MATCH_PARENT, params.width);
        assertEquals(LayoutParams.MATCH_PARENT, params.height);
        assertEquals(0, params.x);
        assertEquals(0, params.y);
    }

    @Test
    public void testGenerateLayoutParams2() {
        LayoutParams params =
            (LayoutParams) mMyAbsoluteLayout.generateLayoutParams(mAbsoluteLayoutParams);

        assertEquals(DEFAULT_WIDTH, params.width);
        assertEquals(DEFAULT_HEIGHT, params.height);
        assertEquals(0, params.x);
        assertEquals(0, params.y);
    }

    @Test(expected=NullPointerException.class)
    public void testGenerateLayoutParamsFromNull() {
        mMyAbsoluteLayout.generateLayoutParams((LayoutParams) null);
    }

    @Test
    public void testGenerateDefaultLayoutParams() {
        LayoutParams params = (LayoutParams) mMyAbsoluteLayout.generateDefaultLayoutParams();

        assertEquals(LayoutParams.WRAP_CONTENT, params.width);
        assertEquals(LayoutParams.WRAP_CONTENT, params.height);
        assertEquals(0, params.x);
        assertEquals(0, params.y);
    }

    private static class MyAbsoluteLayout extends AbsoluteLayout {
        public MyAbsoluteLayout(Context context) {
            super(context);
        }

        @Override
        protected boolean checkLayoutParams(ViewGroup.LayoutParams p) {
            return super.checkLayoutParams(p);
        }

        @Override
        protected ViewGroup.LayoutParams generateDefaultLayoutParams() {
            return super.generateDefaultLayoutParams();
        }

        @Override
        protected ViewGroup.LayoutParams generateLayoutParams(ViewGroup.LayoutParams p) {
            return super.generateLayoutParams(p);
        }
    }
}
