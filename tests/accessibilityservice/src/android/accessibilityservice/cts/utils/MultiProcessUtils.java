/*
 * Copyright (C) 2023 The Android Open Source Project
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

package android.accessibilityservice.cts.utils;

/**
 * Utility class for providing multiprocess support. Used in AccessibilityDisplayProxyTest.
 */
public class MultiProcessUtils {
    /** Constant used in event text to verify touch exploration is updated outside the
     * instrumentation process.
     */
    public static final String TOUCH_EXPLORATION_CHANGE_EVENT_TEXT = "Touch exploration enabled: ";

    public static final String SEPARATE_PROCESS_ACTIVITY_TITLE = "Separate process activity title";
}
