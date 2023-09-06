/**
 * Copyright 2023 The Android Open Source Project
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

import static android.view.MotionEvent.AXIS_X;
import static android.view.MotionEvent.AXIS_Y;
import static android.view.flags.Flags.FLAG_SCROLL_FEEDBACK_API;
import static org.junit.Assume.assumeTrue;

import android.platform.test.annotations.RequiresFlagsEnabled;
import android.view.HapticScrollFeedbackProvider;
import android.view.InputDevice;
import android.view.MotionEvent;

import androidx.test.filters.SmallTest;
import androidx.test.rule.ActivityTestRule;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * There currently is no strict API requirement for the feedback behavior, so this class only tests
 * that the feedback APIs do not crash when called.
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class HapticScrollFeedbackProviderTest {
    @Rule
    public ActivityTestRule<ViewTestCtsActivity> mActivityRule =
            new ActivityTestRule<>(ViewTestCtsActivity.class);

    private HapticScrollFeedbackProvider mProvider;

    private int mTouchDeviceId = -1;

    /** Setup common for all tests. */
    @Before
    public void setup() {
        InputDevice touchDevice = getTouchScreenInputDevice();
        if (touchDevice == null) {
            return;
        }

        mTouchDeviceId = touchDevice.getId();
        mProvider = new HapticScrollFeedbackProvider(
                mActivityRule.getActivity().findViewById(R.id.scroll_view));
    }

    /** Test case checking that the MotionEvent based APIs don't crash. */
    @Test
    @RequiresFlagsEnabled(FLAG_SCROLL_FEEDBACK_API)
    public void testMotionEventApis() {
        assumeTrue(mTouchDeviceId != -1); // Don't run test if there's no touchscreen device.

        mProvider.onScrollLimit(createTouchMoveEvent(), AXIS_Y, true);
        mProvider.onScrollLimit(createTouchMoveEvent(), AXIS_Y, false);
        mProvider.onScrollLimit(createTouchMoveEvent(), AXIS_Y, true);

        mProvider.onScrollProgress(createTouchMoveEvent(), AXIS_Y, 300);
        mProvider.onScrollProgress(createTouchMoveEvent(), AXIS_X, 0);
        mProvider.onScrollProgress(createTouchMoveEvent(), AXIS_X, -900);
        mProvider.onScrollProgress(createTouchMoveEvent(), AXIS_X, -800);
        mProvider.onScrollProgress(createTouchMoveEvent(), AXIS_Y, 0);
        mProvider.onScrollProgress(createTouchMoveEvent(), AXIS_Y, -900);

        mProvider.onSnapToItem(createTouchMoveEvent(), AXIS_Y);
        mProvider.onSnapToItem(createTouchMoveEvent(), AXIS_X);
        mProvider.onSnapToItem(createTouchMoveEvent(), AXIS_Y);

        mProvider.onScrollLimit(createTouchMoveEvent(), AXIS_Y, false);
        mProvider.onScrollLimit(createTouchMoveEvent(), AXIS_Y, true);
        mProvider.onScrollLimit(createTouchMoveEvent(), AXIS_Y, false);
    }

    /** Test case checking that the non-MotionEvent based APIs don't crash. */
    @Test
    @RequiresFlagsEnabled(FLAG_SCROLL_FEEDBACK_API)
    public void testNonMotionEventBasedApis() {
        assumeTrue(mTouchDeviceId != -1); // Don't run test if there's no touchscreen device.

        mProvider.onScrollLimit(mTouchDeviceId, InputDevice.SOURCE_TOUCHSCREEN, AXIS_Y, true);
        mProvider.onScrollLimit(mTouchDeviceId, InputDevice.SOURCE_TOUCHSCREEN, AXIS_Y, false);
        mProvider.onScrollLimit(mTouchDeviceId, InputDevice.SOURCE_TOUCHSCREEN, AXIS_Y, true);

        mProvider.onScrollProgress(mTouchDeviceId, InputDevice.SOURCE_TOUCHSCREEN, AXIS_Y, 300);
        mProvider.onScrollProgress(mTouchDeviceId, InputDevice.SOURCE_TOUCHSCREEN, AXIS_X, 0);
        mProvider.onScrollProgress(mTouchDeviceId, InputDevice.SOURCE_TOUCHSCREEN, AXIS_X, -900);
        mProvider.onScrollProgress(mTouchDeviceId, InputDevice.SOURCE_TOUCHSCREEN, AXIS_X, -800);
        mProvider.onScrollProgress(mTouchDeviceId, InputDevice.SOURCE_TOUCHSCREEN, AXIS_Y, 0);
        mProvider.onScrollProgress(mTouchDeviceId, InputDevice.SOURCE_TOUCHSCREEN, AXIS_Y, -900);

        mProvider.onSnapToItem(mTouchDeviceId, InputDevice.SOURCE_TOUCHSCREEN, AXIS_Y);
        mProvider.onSnapToItem(mTouchDeviceId, InputDevice.SOURCE_TOUCHSCREEN, AXIS_X);
        mProvider.onSnapToItem(mTouchDeviceId, InputDevice.SOURCE_TOUCHSCREEN, AXIS_Y);

        mProvider.onScrollLimit(mTouchDeviceId, InputDevice.SOURCE_TOUCHSCREEN, AXIS_Y, false);
        mProvider.onScrollLimit(mTouchDeviceId, InputDevice.SOURCE_TOUCHSCREEN, AXIS_Y, true);
        mProvider.onScrollLimit(mTouchDeviceId, InputDevice.SOURCE_TOUCHSCREEN, AXIS_Y, false);
    }

    private MotionEvent createTouchMoveEvent() {
        MotionEvent.PointerProperties pointerProperties = new MotionEvent.PointerProperties();
        pointerProperties.toolType = MotionEvent.TOOL_TYPE_FINGER;
        MotionEvent.PointerCoords pointerCoords = new MotionEvent.PointerCoords();
        pointerCoords.setAxisValue(AXIS_X, 1);
        pointerCoords.setAxisValue(AXIS_Y, 1);

        return MotionEvent.obtain(
                0, /* downTime */
                0, /* eventTime */
                MotionEvent.ACTION_MOVE, /* action */
                1, /* pointerCount */
                new MotionEvent.PointerProperties[] { pointerProperties },
                new MotionEvent.PointerCoords[] { pointerCoords },
                0, /* metaState */
                0, /* buttonState */
                0f, /* xPrecision */
                0f, /* yPrecision */
                mTouchDeviceId,
                0, /* edgeFlags */
                InputDevice.SOURCE_TOUCHSCREEN,
                0 /* flags */
        );
    }

    private static InputDevice getTouchScreenInputDevice() {
        for (int deviceId : InputDevice.getDeviceIds()) {
            InputDevice device = InputDevice.getDevice(deviceId);
            if (device == null) {
                continue;
            }
            if (device.supportsSource(InputDevice.SOURCE_TOUCHSCREEN)) {
                return device;
            }
        }
        return null;
    }
}
