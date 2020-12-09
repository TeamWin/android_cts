/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.cts.splitapp;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.fail;

import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.view.Window;
import android.widget.LinearLayout;

/**
 * A helper class to retrieve theme values of Theme_Base and Theme_Feature.
 */
public class TestThemeHelper {

    public static final String THEME_FEATURE =
            "com.android.cts.splitapp.feature:style/Theme_Feature";

    private static final int COLOR_BLUE = 0xFF0000FF;
    private static final int COLOR_TEAL = 0xFF008080;
    private static final int COLOR_AQUA = 0xFF00FFFF;
    private static final int COLOR_BLUE_LT = 0xFFADD8E6;
    private static final int COLOR_TEAL_LT = 0xFFE0F0F0;
    private static final int COLOR_AQUA_LT = 0xFFE0FFFF;
    private static final int COLOR_RED = 0xFFFF0000;
    private static final int COLOR_YELLOW = 0xFFFFFF00;
    private static final int COLOR_RED_LT = 0xFFFFCCCB;
    private static final int COLOR_ORANGE_LT = 0xFFFED8B1;

    /** {@link com.android.cts.isolatedsplitapp.R.attr.customColor} */
    private final int mCustomColor;

    /** {#link android.R.attr.colorBackground} */
    private final int mColorBackground;

    /** {#link android.R.attr.navigationBarColor} */
    private final int mNavigationBarColor;

    /** {#link android.R.attr.statusBarColor} */
    private final int mStatusBarColor;

    /** {#link android.R.attr.windowBackground} */
    private final int mWindowBackground;

    public TestThemeHelper(Context context, int themeId) {
        final Resources.Theme theme = new ContextThemeWrapper(context, themeId).getTheme();
        mCustomColor = getColor(theme, R.attr.customColor);
        mColorBackground = getColor(theme, android.R.attr.colorBackground);
        mNavigationBarColor = getColor(theme, android.R.attr.navigationBarColor);
        mStatusBarColor = getColor(theme, android.R.attr.statusBarColor);
        mWindowBackground = getDrawableColor(theme, android.R.attr.windowBackground);
    }

    public void assertThemeBaseValues() {
        assertThat(mCustomColor).isEqualTo(COLOR_BLUE);
        assertThat(mNavigationBarColor).isEqualTo(COLOR_TEAL);
        assertThat(mStatusBarColor).isEqualTo(COLOR_AQUA);
        assertThat(mWindowBackground).isEqualTo(mCustomColor);
    }

    public void assertThemeBaseV23Values() {
        assertThat(mCustomColor).isEqualTo(COLOR_BLUE_LT);
        assertThat(mNavigationBarColor).isEqualTo(COLOR_TEAL_LT);
        assertThat(mStatusBarColor).isEqualTo(COLOR_AQUA_LT);
        assertThat(mWindowBackground).isEqualTo(mCustomColor);
    }

    public void assertThemeFeatureValues() {
        assertThat(mCustomColor).isEqualTo(COLOR_RED);
        assertThat(mNavigationBarColor).isEqualTo(COLOR_TEAL); // fallback to the value of parent
        assertThat(mStatusBarColor).isEqualTo(COLOR_YELLOW);
        assertThat(mWindowBackground).isEqualTo(mCustomColor);
    }

    public void assertThemeFeatureV23Values() {
        assertThat(mCustomColor).isEqualTo(COLOR_RED_LT);
        assertThat(mNavigationBarColor).isEqualTo(COLOR_ORANGE_LT);
        assertThat(mStatusBarColor).isEqualTo(COLOR_AQUA_LT); // fallback to the value of parent
        assertThat(mWindowBackground).isEqualTo(mCustomColor);
    }

    public void assertThemeApplied(Activity activity) {
        assertLayoutBGColor(activity, mCustomColor);

        final Window window = activity.getWindow();
        assertThat(window.getStatusBarColor()).isEqualTo(mStatusBarColor);
        assertThat(window.getNavigationBarColor()).isEqualTo(mNavigationBarColor);
        assertDrawableColor(window.getDecorView().getBackground(), mWindowBackground);

        assertTextViewBGColor(activity);
    }

    private int getColor(Resources.Theme theme, int resourceId) {
        final TypedArray ta = theme.obtainStyledAttributes(new int[] {resourceId});
        final int color = ta.getColor(0, 0);
        ta.recycle();
        return color;
    }

    private int getDrawableColor(Resources.Theme theme, int resourceId) {
        final TypedArray ta = theme.obtainStyledAttributes(new int[] {resourceId});
        final Drawable color = ta.getDrawable(0);
        ta.recycle();
        if (!(color instanceof ColorDrawable)) {
            fail("Can't get drawable color");
        }
        return ((ColorDrawable) color).getColor();
    }

    private void assertLayoutBGColor(Activity activity, int expected) {
        final LinearLayout layout = activity.findViewById(R.id.content);
        final Drawable background = layout.getBackground();
        assertDrawableColor(background, expected);
    }

    private void assertDrawableColor(Drawable drawable, int expected) {
        int color = 0;
        if (drawable instanceof ColorDrawable) {
            color = ((ColorDrawable) drawable).getColor();
        } else {
            fail("Can't get drawable color");
        }
        assertThat(color).isEqualTo(expected);
    }

    private void assertTextViewBGColor(Activity activity) {
        final View view = activity.findViewById(R.id.text);
        final Drawable background = view.getBackground();
        assertDrawableColor(background, mColorBackground);
    }
}
