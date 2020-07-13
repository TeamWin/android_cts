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
package android.vendor.wm.utils;

import static android.app.WindowConfiguration.WINDOWING_MODE_FREEFORM;
import static android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN;
import static android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN_OR_SPLIT_SCREEN_SECONDARY;
import static android.app.WindowConfiguration.WINDOWING_MODE_PINNED;
import static android.app.WindowConfiguration.WINDOWING_MODE_SPLIT_SCREEN_PRIMARY;
import static android.app.WindowConfiguration.WINDOWING_MODE_SPLIT_SCREEN_SECONDARY;
import static android.app.WindowConfiguration.WINDOWING_MODE_UNDEFINED;

import android.content.Context;

/**
 * Utility class to simplify checking the currently configured windowing mode
 */
public class WindowConfigurationUtils {
    private static final int[] ANDROID_WINDOWING_MODES = {WINDOWING_MODE_UNDEFINED,
            WINDOWING_MODE_FULLSCREEN,
            WINDOWING_MODE_PINNED,
            WINDOWING_MODE_SPLIT_SCREEN_PRIMARY,
            WINDOWING_MODE_SPLIT_SCREEN_SECONDARY,
            WINDOWING_MODE_FULLSCREEN_OR_SPLIT_SCREEN_SECONDARY,
            WINDOWING_MODE_FREEFORM
    };

    /**
    * Returns true when the current Android Windowing mode
    * is not one of the default windowing modes.
    * @param context current windowing context
    * @return        boolean if in vendor windowing mode
    */
    public static boolean isInVendorWindowingMode(Context context) {
        int windowingMode = context.getResources().getConfiguration().windowConfiguration
                .getWindowingMode();

        for (int androidMode : ANDROID_WINDOWING_MODES) {
            if (windowingMode == androidMode) {
                return false;
            }
        }

        return true;
    }
}
