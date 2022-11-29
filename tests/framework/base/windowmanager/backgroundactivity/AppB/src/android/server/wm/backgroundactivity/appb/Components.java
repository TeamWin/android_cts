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

package android.server.wm.backgroundactivity.appb;

import android.content.ComponentName;
import android.server.wm.component.ComponentsBase;

public class Components extends ComponentsBase {

    public static final ComponentName APP_B_FOREGROUND_ACTIVITY =
            component(Components.class, "ForegroundActivity");
    public static final ComponentName APP_B_START_PENDING_INTENT_ACTIVITY =
            component(Components.class, "StartPendingIntentActivity");
    public static final ComponentName APP_B_START_PENDING_INTENT_RECEIVER =
            component(Components.class, "StartPendingIntentReceiver");

    /** Extra key constants for {@link #APP_B_START_PENDING_INTENT_ACTIVITY} */
    public static class StartPendingIntentActivity {
        /**
         * If present starts the pending intent with a bundle created from ActivityOptions where
         * setBackgroundActivityLaunchAllowed() was set to this value.
         */
        public static final String ALLOW_BAL_EXTRA = "ALLOW_BAL_EXTRA";

        /** Start the pending intent with a `null` bundle if no options are set. */
        public static final String USE_NULL_BUNDLE = "USE_NULL_BUNDLE";
    }

    /** Extra key constants for {@link #APP_B_START_PENDING_INTENT_RECEIVER} */
    public static class StartPendingIntentReceiver {
        public static final String PENDING_INTENT_EXTRA = "PENDING_INTENT_EXTRA";
    }
}
