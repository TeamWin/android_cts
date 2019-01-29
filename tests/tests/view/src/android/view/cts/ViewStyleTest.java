/*
 * Copyright (C) 2019 The Android Open Source Project
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

import static org.junit.Assert.assertEquals;

import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.MediumTest;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.support.test.uiautomator.UiDevice;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.Map;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class ViewStyleTest {

    @Rule
    public ActivityTestRule<Activity> mActivityRule =
            new ActivityTestRule<>(Activity.class, true, false);

    private static final String DISABLE_SHELL_COMMAND =
            "settings delete global debug_view_attributes_application_package";

    private static final String ENABLE_SHELL_COMMAND =
            "settings put global debug_view_attributes_application_package android.view.cts";

    private UiDevice mUiDevice;

    @Before
    public void setUp() throws Exception {
        mUiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        mUiDevice.executeShellCommand(ENABLE_SHELL_COMMAND);
        mActivityRule.launchActivity(null);
    }

    @After
    public void tearDown() throws Exception {
        mUiDevice.executeShellCommand(DISABLE_SHELL_COMMAND);
    }

    @Test
    public void testGetExplicitStyle() {
        Context context = InstrumentationRegistry.getTargetContext();
        LayoutInflater inflater = LayoutInflater.from(context);
        LinearLayout rootView = (LinearLayout) inflater.inflate(R.layout.view_style_layout, null);
        View view1 = rootView.findViewById(R.id.view1);
        assertEquals(R.style.ExplicitStyle1, view1.getExplicitStyle());

        View view2 = rootView.findViewById(R.id.view2);
        assertEquals(R.style.ExplicitStyle2, view2.getExplicitStyle());

        View view3 = rootView.findViewById(R.id.view3);
        assertEquals(Resources.ID_NULL, view3.getExplicitStyle());

        View view4 = rootView.findViewById(R.id.view4);
        assertEquals(android.R.style.TextAppearance_Material_Large, view4.getExplicitStyle());
    }

    @Test
    public void testGetAttributeResolutionStack() {
        LayoutInflater inflater = LayoutInflater.from(mActivityRule.getActivity());
        LinearLayout rootView = (LinearLayout) inflater.inflate(R.layout.view_style_layout, null);
        // View that has an explicit style ExplicitStyle1 set via style = ...
        View view1 = rootView.findViewById(R.id.view1);
        List<Integer> stackView1 = view1.getAttributeResolutionStack();
        assertEquals(3, stackView1.size());
        assertEquals(R.layout.view_style_layout, stackView1.get(0).intValue());
        assertEquals(R.style.ExplicitStyle1, stackView1.get(1).intValue());
        assertEquals(R.style.ParentOfExplicitStyle1, stackView1.get(2).intValue());

        // Button that has the default style MyButtonStyle set in ViewStyleTestTheme Activity theme
        // via android:buttonStyle
        Button button1 = rootView.findViewById(R.id.button1);
        List<Integer> stackButton1 = button1.getAttributeResolutionStack();
        assertEquals(3, stackButton1.size());
        assertEquals(R.layout.view_style_layout, stackButton1.get(0).intValue());
        assertEquals(R.style.MyButtonStyle, stackButton1.get(1).intValue());
        assertEquals(R.style.MyButtonStyleParent, stackButton1.get(2).intValue());

        // Button that has the default style MyButtonStyle set in ViewStyleTestTheme Activity theme
        // via android:buttonStyle and has an explicit style ExplicitStyle1 set via style = ...
        Button button2 = rootView.findViewById(R.id.button2);
        List<Integer> stackButton2 = button2.getAttributeResolutionStack();
        assertEquals(5, stackButton2.size());
        assertEquals(R.layout.view_style_layout, stackButton2.get(0).intValue());
        assertEquals(R.style.ExplicitStyle1, stackButton2.get(1).intValue());
        assertEquals(R.style.ParentOfExplicitStyle1, stackButton2.get(2).intValue());
        assertEquals(R.style.MyButtonStyle, stackButton2.get(3).intValue());
        assertEquals(R.style.MyButtonStyleParent, stackButton2.get(4).intValue());
    }

    @Test
    public void testGetAttributeSourceResourceMap() {
        LayoutInflater inflater = LayoutInflater.from(mActivityRule.getActivity());
        LinearLayout rootView = (LinearLayout) inflater.inflate(R.layout.view_style_layout, null);
        // View that has an explicit style ExplicitStyle1 set via style = ...
        View view1 = rootView.findViewById(R.id.view1);
        Map<Integer, Integer> attributeMapView1 = view1.getAttributeSourceResourceMap();
        assertEquals(9, attributeMapView1.size());
        assertEquals(R.style.ExplicitStyle1,
                (attributeMapView1.get(android.R.attr.padding)).intValue());
        assertEquals(R.style.ParentOfExplicitStyle1,
                (attributeMapView1.get(android.R.attr.paddingLeft)).intValue());
        assertEquals(R.layout.view_style_layout,
                (attributeMapView1.get(android.R.attr.paddingTop)).intValue());
    }
}
