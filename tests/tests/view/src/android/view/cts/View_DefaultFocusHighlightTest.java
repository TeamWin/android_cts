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

package android.view.cts;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.support.test.annotation.UiThreadTest;
import android.support.test.filters.MediumTest;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class View_DefaultFocusHighlightTest {

    @Rule
    public ActivityTestRule<DefaultFocusHighlightCtsActivity> mActivityRule =
            new ActivityTestRule<>(DefaultFocusHighlightCtsActivity.class);

    @UiThreadTest
    @Test
    public void testSettersAndGetters() {
        Activity activity = mActivityRule.getActivity();

        View view = activity.findViewById(R.id.view);
        ListView listView = (ListView) activity.findViewById(R.id.listview);
        EditText editText = (EditText) activity.findViewById(R.id.edittext);
        Button button = (Button) activity.findViewById(R.id.button);
        LinearLayout linearLayout = (LinearLayout) activity.findViewById(R.id.linearlayout);

        assertTrue(view.getDefaultFocusHighlightEnabled());
        assertFalse(listView.getDefaultFocusHighlightEnabled());
        assertFalse(editText.getDefaultFocusHighlightEnabled());
        assertTrue(button.getDefaultFocusHighlightEnabled());
        assertTrue(linearLayout.getDefaultFocusHighlightEnabled());

        view.setDefaultFocusHighlightEnabled(false);
        listView.setDefaultFocusHighlightEnabled(true);
        editText.setDefaultFocusHighlightEnabled(true);
        button.setDefaultFocusHighlightEnabled(false);
        linearLayout.setDefaultFocusHighlightEnabled(false);

        assertFalse(view.getDefaultFocusHighlightEnabled());
        assertTrue(listView.getDefaultFocusHighlightEnabled());
        assertTrue(editText.getDefaultFocusHighlightEnabled());
        assertFalse(button.getDefaultFocusHighlightEnabled());
        assertFalse(linearLayout.getDefaultFocusHighlightEnabled());
    }

    @UiThreadTest
    @Test
    public void testInflating() {
        Activity activity = mActivityRule.getActivity();

        View view = activity.findViewById(R.id.view_to_inflate);
        ListView listView = (ListView) activity.findViewById(R.id.listview_to_inflate);
        EditText editText = (EditText) activity.findViewById(R.id.edittext_to_inflate);
        Button button = (Button) activity.findViewById(R.id.button_to_inflate);
        LinearLayout linearLayout = (LinearLayout) activity.findViewById(
                R.id.linearlayout_to_inflate);

        assertFalse(view.getDefaultFocusHighlightEnabled());
        assertTrue(listView.getDefaultFocusHighlightEnabled());
        assertTrue(editText.getDefaultFocusHighlightEnabled());
        assertFalse(button.getDefaultFocusHighlightEnabled());
        assertFalse(linearLayout.getDefaultFocusHighlightEnabled());
    }
}
