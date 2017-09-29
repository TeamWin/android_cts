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

package android.server.wm;

import static android.app.WindowConfiguration.WINDOWING_MODE_SPLIT_SCREEN_PRIMARY;
import static android.server.am.StateLogger.log;

import android.server.am.ActivityManagerTestBase;
import android.server.am.WindowManagerState.WindowState;

public abstract class ParentChildTestBase extends ActivityManagerTestBase {

    private static final String COMPONENT_NAME = "android.server.FrameTestApp";

    interface ParentChildTest {

        void doTest(WindowState parent, WindowState child);
    }

    public void startTestCase(String testCase) throws Exception {
        setComponentName(COMPONENT_NAME);
        String cmd = getAmStartCmd(activityName(), intentKey(), testCase);
        executeShellCommand(cmd);
    }

    public void startTestCaseDocked(String testCase) throws Exception {
        startTestCase(testCase);
        setActivityTaskWindowingMode(activityName(), WINDOWING_MODE_SPLIT_SCREEN_PRIMARY);
    }

    abstract String intentKey();

    abstract String activityName();

    abstract void doSingleTest(ParentChildTest t) throws Exception;

    void doFullscreenTest(String testCase, ParentChildTest t) throws Exception {
        log("Running test fullscreen");
        startTestCase(testCase);
        doSingleTest(t);
        stopTestCase();
    }

    void doDockedTest(String testCase, ParentChildTest t) throws Exception {
        log("Running test docked");
        startTestCaseDocked(testCase);
        doSingleTest(t);
        stopTestCase();
    }

    void doParentChildTest(String testCase, ParentChildTest t) throws Exception {
        doFullscreenTest(testCase, t);
        doDockedTest(testCase, t);
    }
}
