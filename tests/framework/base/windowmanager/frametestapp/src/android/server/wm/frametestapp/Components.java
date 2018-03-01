/*
 * Copyright (C) 2018 The Android Open Source Project
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

package android.server.wm.frametestapp;

import android.content.ComponentName;
import android.server.am.component.ComponentsBase;
import android.support.annotation.StringDef;

public class Components extends ComponentsBase {

    public static final ComponentName MOVING_CHILD_TEST_ACTIVITY =
            component(Components.class, "MovingChildTestActivity");
    public static final ComponentName DIALOG_TEST_ACTIVITY =
            component(Components.class, "DialogTestActivity");

    /**
     * Extra key and value constants of {@link android.server.wm.DialogTestActivity}.
     */
    public static class DialogTestActivity {
        public static final String DIALOG_WINDOW_NAME = "TestDialog";
        // Extra key for test case name.
        public static final String EXTRA_TEST_CASE = "test-case";
        // Value constants for {@link #EXTRA_TEST_CASE}.
        public static final String TEST_EXPLICIT_POSITION_MATCH_PARENT =
                "ExplicitPositionMatchParent";
        public static final String TEST_EXPLICIT_POSITION_MATCH_PARENT_NO_LIMITS =
                "ExplicitPositionMatchParentNoLimits";
        public static final String TEST_EXPLICIT_SIZE = "ExplicitSize";
        public static final String TEST_EXPLICIT_SIZE_BOTTOM_RIGHT_GRAVITY =
                "ExplicitSizeBottomRightGravity";
        public static final String TEST_EXPLICIT_SIZE_TOP_LEFT_GRAVITY =
                "ExplicitSizeTopLeftGravity";
        public static final String TEST_MATCH_PARENT = "MatchParent";
        public static final String TEST_MATCH_PARENT_LAYOUT_IN_OVERSCAN =
                "MatchParentLayoutInOverscan";
        public static final String TEST_NO_FOCUS = "NoFocus";
        public static final String TEST_OVER_SIZED_DIMENSIONS = "OversizedDimensions";
        public static final String TEST_OVER_SIZED_DIMENSIONS_NO_LIMITS =
                "OversizedDimensionsNoLimits";
        public static final String TEST_WITH_MARGINS = "WithMargins";
    }
}
