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
 * limitations under the License
 */

package android.server.cts;

import com.android.tradefed.device.CollectingOutputReceiver;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;

import java.awt.Rectangle;
import java.lang.String;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import java.util.regex.Pattern;
import java.util.regex.Matcher;

import static com.android.ddmlib.Log.LogLevel.INFO;

class WindowManagerState {
    private static final String DUMPSYS_WINDOWS_APPS = "dumpsys window apps";
    private static final String DUMPSYS_WINDOWS_VISIBLE_APPS = "dumpsys window visible-apps";

    private final Pattern mWindowPattern =
            Pattern.compile("Window #(\\d+) Window\\{([0-9a-fA-F]+) u(\\d+) (.+)\\}\\:");
    private final Pattern mStartingWindowPattern =
            Pattern.compile("Window #(\\d+) Window\\{([0-9a-fA-F]+) u(\\d+) Starting (.+)\\}\\:");
    private final Pattern mExitingWindowPattern =
            Pattern.compile("Window #(\\d+) Window\\{([0-9a-fA-F]+) u(\\d+) (.+) EXITING\\}\\:");

    private final Pattern mFocusedWindowPattern =
            Pattern.compile("mCurrentFocus=Window\\{(.+) u(\\d+) (\\S+)\\}");
    private final Pattern mFocusedAppPattern =
            Pattern.compile("mFocusedApp=AppWindowToken\\{(.+) token=Token\\{(.+) "
                    + "ActivityRecord\\{(.+) u(\\d+) (\\S+) (\\S+)");

    private final Pattern mStackIdPattern = Pattern.compile("mStackId=(\\d+)");

    private final Pattern[] mExtractStackExitPatterns = { mStackIdPattern, mWindowPattern,
            mStartingWindowPattern, mExitingWindowPattern, mFocusedWindowPattern,
            mFocusedAppPattern };

    // Windows in z-order with the top most at the front of the list.
    private List<String> mWindows = new ArrayList();
    private List<String> mRawWindows = new ArrayList();
    private List<WindowStack> mStacks = new ArrayList();
    private String mFocusedWindow = null;
    private String mFocusedApp = null;
    private final LinkedList<String> mSysDump = new LinkedList();

    void computeState(ITestDevice device, boolean visibleOnly) throws DeviceNotAvailableException {
        // It is possible the system is in the middle of transition to the right state when we get
        // the dump. We try a few times to get the information we need before giving up.
        int retriesLeft = 3;
        boolean retry = false;
        String dump = null;

        do {
            if (retry) {
                CLog.logAndDisplay(INFO, "***Incomplete WM state. Retrying...");
                // Wait half a second between retries for window manager to finish transitioning...
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    CLog.logAndDisplay(INFO, e.toString());
                    // Well I guess we are not waiting...
                }
            }

            final CollectingOutputReceiver outputReceiver = new CollectingOutputReceiver();
            final String dumpsysCmd = visibleOnly ?
                    DUMPSYS_WINDOWS_VISIBLE_APPS : DUMPSYS_WINDOWS_APPS;
            device.executeShellCommand(dumpsysCmd, outputReceiver);
            dump = outputReceiver.getOutput();
            parseSysDump(dump, visibleOnly);

            retry = mWindows.isEmpty() || mFocusedWindow == null || mFocusedApp == null;
        } while (retry && retriesLeft-- > 0);

        if (retry) {
            CLog.logAndDisplay(INFO, dump);
        }

        if (mWindows.isEmpty()) {
            CLog.logAndDisplay(INFO, "No Windows found...");
        }
        if (mFocusedWindow == null) {
            CLog.logAndDisplay(INFO, "No Focused Window...");
        }
        if (mFocusedApp == null) {
            CLog.logAndDisplay(INFO, "No Focused App...");
        }
    }

    private void parseSysDump(String sysDump,boolean visibleOnly) {
        reset();

        Collections.addAll(mSysDump, sysDump.split("\\n"));

        while (!mSysDump.isEmpty()) {
            final WindowStack stack =
                    WindowStack.create(mSysDump, mStackIdPattern, mExtractStackExitPatterns);

            if (stack != null) {
                mStacks.add(stack);
                continue;
            }

            final String line = mSysDump.pop().trim();

            Matcher matcher = mWindowPattern.matcher(line);
            if (matcher.matches()) {
                CLog.logAndDisplay(INFO, line);
                final String window = matcher.group(4);

                if (visibleOnly && mWindows.isEmpty()) {
                    // This is the front window. Check to see if we are in the middle of
                    // transitioning. If we are, we want to skip dumping until window manager is
                    // done transitioning the top window.
                    matcher = mStartingWindowPattern.matcher(line);
                    if (matcher.matches()) {
                        CLog.logAndDisplay(INFO,
                                "Skipping dump due to starting window transition...");
                        return;
                    }

                    matcher = mExitingWindowPattern.matcher(line);
                    if (matcher.matches()) {
                        CLog.logAndDisplay(INFO,
                                "Skipping dump due to exiting window transition...");
                        return;
                    }
                }

                CLog.logAndDisplay(INFO, window);
                mWindows.add(window);
                mRawWindows.add(line);
                continue;
            }

            matcher = mFocusedWindowPattern.matcher(line);
            if (matcher.matches()) {
                CLog.logAndDisplay(INFO, line);
                final String focusedWindow = matcher.group(3);
                CLog.logAndDisplay(INFO, focusedWindow);
                mFocusedWindow = focusedWindow;
                continue;
            }

            matcher = mFocusedAppPattern.matcher(line);
            if (matcher.matches()) {
                CLog.logAndDisplay(INFO, line);
                final String focusedApp = matcher.group(5);
                CLog.logAndDisplay(INFO, focusedApp);
                mFocusedApp = focusedApp;
                continue;
            }
        }
    }

    void getMatchingWindowTokens(final String windowName, List<String> tokenList) {
        tokenList.clear();

        for (String line : mRawWindows) {
            if (line.contains(windowName)) {
                Matcher matcher = mWindowPattern.matcher(line);
                if (matcher.matches()) {
                    CLog.logAndDisplay(INFO, "Found activity window: " + line);
                    tokenList.add(matcher.group(2));
                }
            }
        }
    }

    String getFrontWindow() {
        return mWindows.get(0);
    }

    String getFocusedWindow() {
        return mFocusedWindow;
    }

    String getFocusedApp() {
        return mFocusedApp;
    }

    int getFrontStackId() {
        return mStacks.get(0).mStackId;
    }

    boolean containsStack(int stackId) {
        for (WindowStack stack : mStacks) {
            if (stackId == stack.mStackId) {
                return true;
            }
        }
        return false;
    }

    boolean isWindowVisible(String windowName) {
        for (String window : mWindows) {
            if (window.equals(windowName)) {
                return true;
            }
        }
        return false;
    }

    private void reset() {
        mSysDump.clear();
        mStacks.clear();
        mWindows.clear();
        mRawWindows.clear();
        mFocusedWindow = null;
        mFocusedApp = null;
    }

    static class WindowStack extends WindowContainer {

        private static final Pattern TASK_ID_PATTERN = Pattern.compile("taskId=(\\d+)");

        int mStackId;
        ArrayList<WindowTask> mTasks = new ArrayList();

        private WindowStack() {

        }

        static WindowStack create(
                LinkedList<String> dump, Pattern stackIdPattern, Pattern[] exitPatterns) {
            final String line = dump.peek().trim();

            final Matcher matcher = stackIdPattern.matcher(line);
            if (!matcher.matches()) {
                // Not a stack.
                return null;
            }
            // For the stack Id line we just read.
            dump.pop();

            final WindowStack stack = new WindowStack();
            CLog.logAndDisplay(INFO, line);
            final String stackId = matcher.group(1);
            CLog.logAndDisplay(INFO, stackId);
            stack.mStackId = Integer.parseInt(stackId);
            stack.extract(dump, exitPatterns);
            return stack;
        }

        void extract(LinkedList<String> dump, Pattern[] exitPatterns) {

            final List<Pattern> taskExitPatterns = new ArrayList();
            Collections.addAll(taskExitPatterns, exitPatterns);
            taskExitPatterns.add(TASK_ID_PATTERN);
            final Pattern[] taskExitPatternsArray =
                    taskExitPatterns.toArray(new Pattern[taskExitPatterns.size()]);

            while (!doneExtracting(dump, exitPatterns)) {
                final WindowTask task =
                        WindowTask.create(dump, TASK_ID_PATTERN, taskExitPatternsArray);

                if (task != null) {
                    mTasks.add(task);
                    continue;
                }

                final String line = dump.pop().trim();

                if (extractFullscreen(line)) {
                    continue;
                }

                if (extractBounds(line)) {
                    continue;
                }
            }
        }
    }

    static class WindowTask extends WindowContainer {
        private static final Pattern TEMP_INSET_BOUNDS_PATTERN =
                Pattern.compile("mTempInsetBounds=\\[(\\d+),(\\d+)\\]\\[(\\d+),(\\d+)\\]");

        private static final Pattern APP_TOKEN_PATTERN = Pattern.compile(
                "Activity #(\\d+) AppWindowToken\\{(\\S+) token=Token\\{(\\S+) "
                + "ActivityRecord\\{(\\S+) u(\\d+) (\\S+) t(\\d+)\\}\\}\\}");


        int mTaskId;
        Rectangle mTempInsetBounds;
        List<String> mAppTokens = new ArrayList();

        private WindowTask() {
        }

        static WindowTask create(
                LinkedList<String> dump, Pattern taskIdPattern, Pattern[] exitPatterns) {
            final String line = dump.peek().trim();

            final Matcher matcher = taskIdPattern.matcher(line);
            if (!matcher.matches()) {
                // Not a task.
                return null;
            }
            // For the task Id line we just read.
            dump.pop();

            final WindowTask task = new WindowTask();
            CLog.logAndDisplay(INFO, line);
            final String taskId = matcher.group(1);
            CLog.logAndDisplay(INFO, taskId);
            task.mTaskId = Integer.parseInt(taskId);
            task.extract(dump, exitPatterns);
            return task;
        }

        private void extract(LinkedList<String> dump, Pattern[] exitPatterns) {

            while (!doneExtracting(dump, exitPatterns)) {
                final String line = dump.pop().trim();

                if (extractFullscreen(line)) {
                    continue;
                }

                if (extractBounds(line)) {
                    continue;
                }

                Matcher matcher = TEMP_INSET_BOUNDS_PATTERN.matcher(line);
                if (matcher.matches()) {
                    CLog.logAndDisplay(INFO, line);
                    mTempInsetBounds = getBounds(matcher);
                }

                matcher = APP_TOKEN_PATTERN.matcher(line);
                if (matcher.matches()) {
                    CLog.logAndDisplay(INFO, line);
                    final String appToken = matcher.group(6);
                    CLog.logAndDisplay(INFO, appToken);
                    mAppTokens.add(appToken);
                    continue;
                }
            }
        }
    }

    static abstract class WindowContainer {
        protected static final Pattern FULLSCREEN_PATTERN = Pattern.compile("mFullscreen=(\\S+)");
        protected static final Pattern BOUNDS_PATTERN =
                Pattern.compile("mBounds=\\[(\\d+),(\\d+)\\]\\[(\\d+),(\\d+)\\]");

        protected boolean mFullscreen;
        protected Rectangle mBounds;

        static boolean doneExtracting(LinkedList<String> dump, Pattern[] exitPatterns) {
            if (dump.isEmpty()) {
                return true;
            }
            final String line = dump.peek().trim();

            for (Pattern pattern : exitPatterns) {
                if (pattern.matcher(line).matches()) {
                    return true;
                }
            }
            return false;
        }

        boolean extractFullscreen(String line) {
            final Matcher matcher = FULLSCREEN_PATTERN.matcher(line);
            if (!matcher.matches()) {
                return false;
            }
            CLog.logAndDisplay(INFO, line);
            final String fullscreen = matcher.group(1);
            CLog.logAndDisplay(INFO, fullscreen);
            mFullscreen = Boolean.valueOf(fullscreen);
            return true;
        }

        boolean extractBounds(String line) {
            final Matcher matcher = BOUNDS_PATTERN.matcher(line);
            if (!matcher.matches()) {
                return false;
            }
            CLog.logAndDisplay(INFO, line);
            mBounds = getBounds(matcher);
            return true;
        }

        static Rectangle getBounds(Matcher matcher) {
            final int left = Integer.valueOf(matcher.group(1));
            final int top = Integer.valueOf(matcher.group(2));
            final int right = Integer.valueOf(matcher.group(3));
            final int bottom = Integer.valueOf(matcher.group(4));
            final Rectangle rect = new Rectangle(left, top, right - left, bottom - top);

            CLog.logAndDisplay(INFO, rect.toString());
            return rect;
        }
    }
}
