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

package android.cts.util;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.text.Editable;
import android.text.TextUtils;

import junit.framework.Assert;

import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;

import static org.mockito.Matchers.*;

/**
 * The useful methods for widget test.
 */
public class WidgetTestUtils {
    /**
     * Assert that two bitmaps have identical content (same dimensions, same configuration,
     * same pixel content).
     *
     * @param b1 the first bitmap which needs to compare.
     * @param b2 the second bitmap which needs to compare.
     */
    public static void assertEquals(Bitmap b1, Bitmap b2) {
        if (b1 == b2) {
            return;
        }

        if (b1 == null || b2 == null) {
            Assert.fail("the bitmaps are not equal");
        }

        // b1 and b2 are all not null.
        if (b1.getWidth() != b2.getWidth() || b1.getHeight() != b2.getHeight()
            || b1.getConfig() != b2.getConfig()) {
            Assert.fail("the bitmaps are not equal");
        }

        int w = b1.getWidth();
        int h = b1.getHeight();
        int s = w * h;
        int[] pixels1 = new int[s];
        int[] pixels2 = new int[s];

        b1.getPixels(pixels1, 0, w, 0, 0, w, h);
        b2.getPixels(pixels2, 0, w, 0, 0, w, h);

        for (int i = 0; i < s; i++) {
            if (pixels1[i] != pixels2[i]) {
                Assert.fail("the bitmaps are not equal");
            }
        }
    }

    /**
     * Find beginning of the special element.
     * @param parser XmlPullParser will be parsed.
     * @param firstElementName the target element name.
     *
     * @throws XmlPullParserException if XML Pull Parser related faults occur.
     * @throws IOException if I/O-related error occur when parsing.
     */
    public static final void beginDocument(XmlPullParser parser, String firstElementName)
            throws XmlPullParserException, IOException {
        Assert.assertNotNull(parser);
        Assert.assertNotNull(firstElementName);

        int type;
        while ((type = parser.next()) != XmlPullParser.START_TAG
                && type != XmlPullParser.END_DOCUMENT) {
            ;
        }

        if (!parser.getName().equals(firstElementName)) {
            throw new XmlPullParserException("Unexpected start tag: found " + parser.getName()
                    + ", expected " + firstElementName);
        }
    }

    /**
     * Compare the expected pixels with actual, scaling for the target context density
     *
     * @throws AssertionFailedError
     */
    public static void assertScaledPixels(int expected, int actual, Context context) {
        Assert.assertEquals(expected * context.getResources().getDisplayMetrics().density,
                actual, 3);
    }

    /** Converts dips into pixels using the {@link Context}'s density. */
    public static int convertDipToPixels(Context context, int dip) {
      float density = context.getResources().getDisplayMetrics().density;
      return Math.round(density * dip);
    }

    /**
     * Retrieve a bitmap that can be used for comparison on any density
     * @param resources
     * @return the {@link Bitmap} or <code>null</code>
     */
    public static Bitmap getUnscaledBitmap(Resources resources, int resId) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inScaled = false;
        return BitmapFactory.decodeResource(resources, resId, options);
    }

    /**
     * Retrieve a dithered bitmap that can be used for comparison on any density
     * @param resources
     * @param config the preferred config for the returning bitmap
     * @return the {@link Bitmap} or <code>null</code>
     */
    public static Bitmap getUnscaledAndDitheredBitmap(Resources resources,
            int resId, Bitmap.Config config) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inDither = true;
        options.inScaled = false;
        options.inPreferredConfig = config;
        return BitmapFactory.decodeResource(resources, resId, options);
    }

    /**
     * Argument matcher for equality check of a CharSequence.
     *
     * @param expected expected CharSequence
     *
     * @return
     */
    public static CharSequence sameCharSequence(final CharSequence expected) {
        return argThat(new BaseMatcher<CharSequence>() {
            @Override
            public boolean matches(Object o) {
                if (o instanceof CharSequence) {
                    return TextUtils.equals(expected, (CharSequence) o);
                }
                return false;
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("doesn't match " + expected);
            }
        });
    }

    /**
     * Argument matcher for equality check of an Editable.
     *
     * @param expected expected Editable
     *
     * @return
     */
    public static Editable sameEditable(final Editable expected) {
        return argThat(new BaseMatcher<Editable>() {
            @Override
            public boolean matches(Object o) {
                if (o instanceof Editable) {
                    return TextUtils.equals(expected, (Editable) o);
                }
                return false;
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("doesn't match " + expected);
            }
        });
    }

}
