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

package android.accessibilityservice.cts;

import static android.accessibilityservice.cts.utils.AccessibilityEventFilterUtils.filterForEventType;
import static android.accessibilityservice.cts.utils.ActivityLaunchUtils.findWindowByTitle;
import static android.accessibilityservice.cts.utils.ActivityLaunchUtils.getActivityTitle;
import static android.accessibilityservice.cts.utils.ActivityLaunchUtils.launchActivityAndWaitForItToBeOnscreen;
import static android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE;
import static android.view.accessibility.AccessibilityEvent.TYPE_WINDOWS_CHANGED;

import static junit.framework.TestCase.assertTrue;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.accessibilityservice.cts.activities.AccessibilityWindowReportingActivity;
import android.app.Activity;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.support.test.InstrumentationRegistry;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.concurrent.TimeoutException;

/**
 * Tests that window changes produce the correct events and that AccessibilityWindowInfos are
 * properly populated
 */
@RunWith(AndroidJUnit4.class)
public class AccessibilityWindowReportingTest {
    private static final int TIMEOUT_ASYNC_PROCESSING = 5000;
    private static final CharSequence TOP_WINDOW_TITLE =
            "android.accessibilityservice.cts.AccessibilityWindowReportingTest.TOP_WINDOW_TITLE";

    private static Instrumentation sInstrumentation;
    private static UiAutomation sUiAutomation;
    private Activity mActivity;
    private CharSequence mActivityTitle;

    @Rule
    public ActivityTestRule<AccessibilityWindowReportingActivity> mActivityRule =
            new ActivityTestRule<>(AccessibilityWindowReportingActivity.class, false, false);

    @BeforeClass
    public static void oneTimeSetup() throws Exception {
        sInstrumentation = InstrumentationRegistry.getInstrumentation();
        sUiAutomation = sInstrumentation.getUiAutomation();
        AccessibilityServiceInfo info = sUiAutomation.getServiceInfo();
        info.flags |= AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
        sUiAutomation.setServiceInfo(info);
    }

    @AfterClass
    public static void finalTearDown() throws Exception {
        sUiAutomation.destroy();
    }

    @Before
    public void setUp() throws Exception {
        mActivity = launchActivityAndWaitForItToBeOnscreen(
                sInstrumentation, sUiAutomation, mActivityRule);
        mActivityTitle = getActivityTitle(sInstrumentation, mActivity);
    }

    @Test
    public void testUpdatedWindowTitle_generatesEventAndIsReturnedByGetTitle() {
        final String updatedTitle = "Updated Title";
        try {
            sUiAutomation.executeAndWaitForEvent(() -> sInstrumentation.runOnMainSync(
                    () -> mActivity.setTitle(updatedTitle)),
                    filterForEventType(TYPE_WINDOWS_CHANGED),
                    TIMEOUT_ASYNC_PROCESSING);
        } catch (TimeoutException exception) {
            throw new RuntimeException(
                    "Failed to get windows changed event for title update", exception);
        }
        final AccessibilityWindowInfo window = findWindowByTitle(sUiAutomation, updatedTitle);
        assertNotNull("Updated window title not reported to accessibility", window);
        window.recycle();
    }

    @Test
    public void testGetAnchorForDropDownForAutoCompleteTextView_returnsTextViewNode() {
        final AutoCompleteTextView autoCompleteTextView =
                (AutoCompleteTextView) mActivity.findViewById(R.id.autoCompleteLayout);
        final AccessibilityNodeInfo autoCompleteTextInfo = sUiAutomation.getRootInActiveWindow()
                .findAccessibilityNodeInfosByViewId(
                        "android.accessibilityservice.cts:id/autoCompleteLayout")
                .get(0);

        // For the drop-down
        final String[] COUNTRIES = new String[] {"Belgium", "France", "Italy", "Germany", "Spain"};

        try {
            sUiAutomation.executeAndWaitForEvent(() -> sInstrumentation.runOnMainSync(
                    () -> {
                        final ArrayAdapter<String> adapter = new ArrayAdapter<>(
                                mActivity, android.R.layout.simple_dropdown_item_1line, COUNTRIES);
                        autoCompleteTextView.setAdapter(adapter);
                        autoCompleteTextView.showDropDown();
                    }),
                    filterForEventType(TYPE_WINDOWS_CHANGED),
                    TIMEOUT_ASYNC_PROCESSING);
        } catch (TimeoutException exception) {
            throw new RuntimeException(
                    "Failed to get window changed event when showing dropdown", exception);
        }

        // Find the pop-up window
        boolean foundPopup = false;
        final List<AccessibilityWindowInfo> windows = sUiAutomation.getWindows();
        for (int i = 0; i < windows.size(); i++) {
            final AccessibilityWindowInfo window = windows.get(i);
            if (window.getAnchor() == null) {
                continue;
            }
            assertEquals(autoCompleteTextInfo, window.getAnchor());
            assertFalse("Found multiple pop-ups anchored to one text view", foundPopup);
            foundPopup = true;
        }
        assertTrue("Failed to find accessibility window for auto-complete pop-up", foundPopup);
    }
}