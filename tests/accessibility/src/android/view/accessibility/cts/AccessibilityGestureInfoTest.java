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
 * limitations under the License
 */

package android.view.accessibility.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import android.accessibility.cts.common.AccessibilityDumpOnFailureRule;
import android.accessibilityservice.AccessibilityGestureInfo;
import android.accessibilityservice.AccessibilityService;
import android.os.Parcel;
import android.platform.test.annotations.Presubmit;
import android.view.Display;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Class for testing {@link android.accessibilityservice.AccessibilityGestureInfo}.
 */
@Presubmit
@RunWith(AndroidJUnit4.class)
public class AccessibilityGestureInfoTest {

    @Rule
    public final AccessibilityDumpOnFailureRule mDumpOnFailureRule =
            new AccessibilityDumpOnFailureRule();

    private static final int SENT_GESTURE = AccessibilityService.GESTURE_SWIPE_DOWN;
    private static final int TARGET_DISPLAY = Display.DEFAULT_DISPLAY;

    @SmallTest
    @Test
    public void testMarshaling() {

        // Fully populate the gesture info to marshal.
        AccessibilityGestureInfo sentGestureInfo = new AccessibilityGestureInfo(
                SENT_GESTURE, TARGET_DISPLAY);

        // Marshal and unmarshal the gesture info.
        Parcel parcel = Parcel.obtain();
        sentGestureInfo.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        AccessibilityGestureInfo receivedGestureInfo =
                AccessibilityGestureInfo.CREATOR.createFromParcel(parcel);

        // Make sure all fields properly marshaled.
        assertEqualsGestureInfo(sentGestureInfo, receivedGestureInfo);

        parcel.recycle();
    }

    /**
     * Tests whether the value of Getter method is as same as the parameter of the constructor.
     *
     */
    @SmallTest
    @Test
    public void testGetterMethods() {
        AccessibilityGestureInfo actualGesture = new AccessibilityGestureInfo(SENT_GESTURE,
                TARGET_DISPLAY);

        assertEquals("getGestureId is different from parameter of constructor", SENT_GESTURE,
                actualGesture.getGestureId());
        assertEquals("getDisplayId is different from parameter of constructor", TARGET_DISPLAY,
                actualGesture.getDisplayId());
    }

    /**
     * Tests whether the gesture describes its contents consistently.
     */
    @SmallTest
    @Test
    public void testDescribeContents() {
        AccessibilityGestureInfo gesture1 = new AccessibilityGestureInfo(SENT_GESTURE,TARGET_DISPLAY);
        assertSame("accessibility gesture infos always return 0 for this method.", 0,
                gesture1.describeContents());
        AccessibilityGestureInfo gesture2 = new AccessibilityGestureInfo(
                AccessibilityService.GESTURE_SWIPE_LEFT, TARGET_DISPLAY);
        assertSame("accessibility gesture infos always return 0 for this method.", 0,
                gesture2.describeContents());
    }

    private void assertEqualsGestureInfo(AccessibilityGestureInfo sentGestureInfo,
            AccessibilityGestureInfo receivedGestureInfo) {
        assertEquals("getDisplayId has incorrectValue", sentGestureInfo.getDisplayId(),
                receivedGestureInfo.getDisplayId());
        assertEquals("getGestureId has incorrectValue", sentGestureInfo.getGestureId(),
                receivedGestureInfo.getGestureId());
    }
}
