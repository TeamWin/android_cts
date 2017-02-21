/*
 * Copyright (C) 2017 The Android Open Source Project
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

package android.preference2.cts;

import android.app.Activity;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.SystemClock;
import android.support.test.InstrumentationRegistry;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.UiObject;
import android.support.test.uiautomator.UiObjectNotFoundException;
import android.support.test.uiautomator.UiSelector;

import com.android.compatibility.common.util.SystemUtil;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Collection of helper utils for preferences testing.
 */
public class TestUtils {

    final UiDevice device;
    private final Instrumentation mInstrumentation;
    private final UiAutomation mAutomation;

    TestUtils() {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        device = UiDevice.getInstance(mInstrumentation);
        mAutomation = mInstrumentation.getUiAutomation();
    }

    Bitmap takeScreenshot() {
        Bitmap bt = mAutomation.takeScreenshot();
        // Crop-out the top bar where current time is displayed since any time change would
        // introduce flakiness (we are cutting 5% of the screen height).
        int yToCut = bt.getHeight() / 20;
        // Crop the right side for scrollbar which might or might not be visible.
        int xToCut = bt.getWidth() / 20;
        bt = Bitmap.createBitmap(
                bt, 0, yToCut, bt.getWidth() - xToCut, bt.getHeight() - yToCut);
        return bt;
    }

    void tapOnViewWithText(String text) {
        UiObject obj = device.findObject(new UiSelector().textMatches(text));
        try {
            obj.click();
        } catch (UiObjectNotFoundException e) {
            throw new AssertionError("View with text '" + text + "' was not found!", e);
        }
        device.waitForIdle();
    }

    boolean isTextShown(String text) {
        UiObject obj = device.findObject(new UiSelector().textMatches(text));
        if (obj.exists()) {
            return true;
        }
        return obj.waitForExists(1000);
    }

    boolean isTextHidden(String text) {
        UiObject obj = device.findObject(new UiSelector().textMatches(text));
        if (!obj.exists()) {
            return true;
        }
        return obj.waitUntilGone(1000);
    }

    void getMultiWindowFocus(Context context) {
        // Get window focus (otherwise back press would close multi-window instead of firing to the
        // Activity and also the automator would fail to find objects on the screen.
        // We want to click slightly below status bar in the 1/3 of width of the screen.
        int x = device.getDisplayWidth() / 3;
        int resourceId =
                context.getResources().getIdentifier("status_bar_height", "dimen", "android");
        int statusBarHeight =
                (resourceId > 0) ? context.getResources().getDimensionPixelSize(resourceId) : 0;
        device.click(x, 2 * statusBarHeight);
    }

    // Multi-window helpers taken from ActivityManagerDockedStackTests.java

    void enterMultiWindow(Activity activity)  {
        try {
            int id = getActivityTaskId(activity);
            runShellCommand("am stack move-task " + id + " 3 true");
        } catch (IOException e) {
            throw new RuntimeException("Failed to get activity task id!", e);
        }
        SystemClock.sleep(2000);
    }

    void leaveMultiWindow(Activity activity) {
        try {
            int id = getActivityTaskId(activity);
            runShellCommand("am stack move-task "+ id +" 1 true");
        } catch (IOException e) {
            throw new RuntimeException("Failed to get activity task id!", e);
        }
        SystemClock.sleep(2000);
    }

    private int getActivityTaskId(Activity activity) throws IOException {
        // Taken from ActivityManagerTestBase.java
        final String output = runShellCommand("am stack list");
        final Pattern activityPattern =
                Pattern.compile("(.*) " + getWindowName(activity) + " (.*)");
        for (String line : output.split("\\n")) {
            Matcher matcher = activityPattern.matcher(line);
            if (matcher.matches()) {
                for (String word : line.split("\\s+")) {
                    if (word.startsWith("taskId")) {
                        final String withColon = word.split("=")[1];
                        return Integer.parseInt(withColon.substring(0, withColon.length() - 1));
                    }
                }
            }
        }
        return -1;
    }

    private String getWindowName(Activity activity) {
        String componentName = activity.getPackageName();
        String baseWindowName = componentName + "/" + componentName + ".";
        return baseWindowName + activity.getClass().getSimpleName();
    }

    private String runShellCommand(String cmd) {
        try {
            return SystemUtil.runShellCommand(mInstrumentation, cmd);
        } catch (IOException e) {
            throw new RuntimeException("Failed to run command: " + cmd, e);
        }
    }
}
