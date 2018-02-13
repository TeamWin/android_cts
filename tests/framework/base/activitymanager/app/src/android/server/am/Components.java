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

package android.server.am;

import android.content.ComponentName;
import android.server.am.component.ComponentsBase;

public class Components extends ComponentsBase {

    public static final ComponentName ANIMATION_TEST_ACTIVITY = component("AnimationTestActivity");
    public static final ComponentName ASSISTANT_ACTIVITY = component("AssistantActivity");
    public static final ComponentName DOCKED_ACTIVITY = component("DockedActivity");
    public static final ComponentName LAUNCHING_ACTIVITY = component("LaunchingActivity");
    public static final ComponentName LAUNCH_ASSISTANT_ACTIVITY_FROM_SESSION =
            component("LaunchAssistantActivityFromSession");
    public static final ComponentName LAUNCH_ASSISTANT_ACTIVITY_INTO_STACK  =
            component("LaunchAssistantActivityIntoAssistantStack");
    public static final ComponentName PIP_ACTIVITY = component("PipActivity");
    public static final ComponentName SPLASHSCREEN_ACTIVITY = component("SplashscreenActivity");
    public static final ComponentName TEST_ACTIVITY = component("TestActivity");
    public static final ComponentName TRANSLUCENT_ASSISTANT_ACTIVITY =
            component("TranslucentAssistantActivity");

    public static final ComponentName ASSISTANT_VOICE_INTERACTION_SERVICE =
            component("AssistantVoiceInteractionService");

    public static final ComponentName LAUNCH_BROADCAST_RECEIVER =
            component("LaunchBroadcastReceiver");
    public static final String LAUNCH_BROADCAST_ACTION =
            getPackageName() + ".LAUNCH_BROADCAST_ACTION";

    /**
     * Action and extra key constants for {@link #TEST_ACTIVITY}.
     *
     * TODO(b/73346885): These constants should be in {@link android.server.am.TestActivity} once
     * the activity is moved to test APK.
     */
    public static class TestActivity {
        // Finishes the activity
        public static final String TEST_ACTIVITY_ACTION_FINISH_SELF =
                TestActivity.class.getName() + ".finish_self";
        // Sets the fixed orientation (can be one of {@link ActivityInfo.ScreenOrientation}
        public static final String EXTRA_FIXED_ORIENTATION = "fixed_orientation";
    }

    /**
     * Extra key constants for {@link #LAUNCH_ASSISTANT_ACTIVITY_INTO_STACK} and
     * {@link #LAUNCH_ASSISTANT_ACTIVITY_FROM_SESSION}.
     *
     * TODO(b/73346885): These constants should be in {@link android.server.am.AssistantActivity}
     * once the activity is moved to test APK.
     */
    public static class AssistantActivity {
        // Launches the given activity in onResume
        public static final String EXTRA_LAUNCH_NEW_TASK = "launch_new_task";
        // Finishes this activity in onResume, this happens after EXTRA_LAUNCH_NEW_TASK
        public static final String EXTRA_FINISH_SELF = "finish_self";
        // Attempts to enter picture-in-picture in onResume
        public static final String EXTRA_ENTER_PIP = "enter_pip";
        // Display on which Assistant runs
        public static final String EXTRA_ASSISTANT_DISPLAY_ID = "assistant_display_id";
    }

    /**
     * Extra key constants for {@link #LAUNCH_ASSISTANT_ACTIVITY_INTO_STACK}.
     *
     * TODO(b/73346885): These constants should be in
     * {@link android.server.am.LaunchAssistantActivityIntoAssistantStack} once the activity is
     * moved to test APK.
     */
    public static class LaunchAssistantActivityIntoAssistantStack {
        // Launches the translucent assist activity
        public static final String EXTRA_IS_TRANSLUCENT = "is_translucent";
    }

    private static ComponentName component(String className) {
        return component(Components.class, className);
    }

    private static String getPackageName() {
        return getPackageName(Components.class);
    }
}
