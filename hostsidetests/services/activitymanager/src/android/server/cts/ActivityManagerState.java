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
import java.lang.Integer;
import java.lang.String;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import java.util.regex.Pattern;
import java.util.regex.Matcher;

import static com.android.ddmlib.Log.LogLevel.INFO;

class ActivityManagerState {
    private static final String DUMPSYS_ACTIVITY_ACTIVITIES = "dumpsys activity activities";

    private final Pattern mStackIdPattern = Pattern.compile("Stack #(\\d+)\\:");
    private final Pattern mFocusedActivityPattern =
            Pattern.compile("mFocusedActivity\\: ActivityRecord\\{(.+) u(\\d+) (\\S+) (\\S+)\\}");
    private final Pattern mFocusedStackPattern =
            Pattern.compile("mFocusedStack=ActivityStack\\{(.+) stackId=(\\d+), (.+)");

    private final Pattern[] mExtractStackExitPatterns =
            { mStackIdPattern, mFocusedActivityPattern, mFocusedStackPattern};

    // Stacks in z-order with the top most at the front of the list.
    private final List<ActivityStack> mStacks = new ArrayList();
    private int mFocusedStackId = -1;
    private String mFocusedActivityRecord = null;
    private final List<String> mResumedActivities = new ArrayList();
    private final LinkedList<String> mSysDump = new LinkedList();

    void computeState(ITestDevice device) throws DeviceNotAvailableException {
        // It is possible the system is in the middle of transition to the right state when we get
        // the dump. We try a few times to get the information we need before giving up.
        int retriesLeft = 3;
        boolean retry = false;
        String dump = null;

        do {
            if (retry) {
                CLog.logAndDisplay(INFO, "***Incomplete AM state. Retrying...");
                // Wait half a second between retries for activity manager to finish transitioning.
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    CLog.logAndDisplay(INFO, e.toString());
                    // Well I guess we are not waiting...
                }
            }

            final CollectingOutputReceiver outputReceiver = new CollectingOutputReceiver();
            device.executeShellCommand(DUMPSYS_ACTIVITY_ACTIVITIES, outputReceiver);
            dump = outputReceiver.getOutput();
            parseSysDump(dump);

            retry = mStacks.isEmpty() || mFocusedStackId == -1 || mFocusedActivityRecord == null
                    || mResumedActivities.isEmpty();
        } while (retry && retriesLeft-- > 0);

        if (retry) {
            CLog.logAndDisplay(INFO, dump);
        }

        if (mStacks.isEmpty()) {
            CLog.logAndDisplay(INFO, "No stacks found...");
        }
        if (mFocusedStackId == -1) {
            CLog.logAndDisplay(INFO, "No focused stack found...");
        }
        if (mFocusedActivityRecord == null) {
            CLog.logAndDisplay(INFO, "No focused activity found...");
        }
        if (mResumedActivities.isEmpty()) {
            CLog.logAndDisplay(INFO, "No resumed activities found...");
        }
    }

    private void parseSysDump(String sysDump) {
        reset();

        Collections.addAll(mSysDump, sysDump.split("\\n"));

        while (!mSysDump.isEmpty()) {
            final ActivityStack stack =
                    ActivityStack.create(mSysDump, mStackIdPattern, mExtractStackExitPatterns);

            if (stack != null) {
                mStacks.add(stack);
                if (stack.mResumedActivity != null) {
                    mResumedActivities.add(stack.mResumedActivity);
                }
                continue;
            }

            final String line = mSysDump.pop().trim();

            Matcher matcher = mFocusedStackPattern.matcher(line);
            if (matcher.matches()) {
                CLog.logAndDisplay(INFO, line);
                final String stackId = matcher.group(2);
                CLog.logAndDisplay(INFO, stackId);
                mFocusedStackId = Integer.parseInt(stackId);
                continue;
            }

            matcher = mFocusedActivityPattern.matcher(line);
            if (matcher.matches()) {
                CLog.logAndDisplay(INFO, line);
                mFocusedActivityRecord = matcher.group(3);
                CLog.logAndDisplay(INFO, mFocusedActivityRecord);
                continue;
            }
        }
    }

    private void reset() {
        mStacks.clear();
        mFocusedStackId = -1;
        mFocusedActivityRecord = null;
        mResumedActivities.clear();
        mSysDump.clear();
    }

    int getFrontStackId() {
        return mStacks.get(0).mStackId;
    }

    int getFocusedStackId() {
        return mFocusedStackId;
    }

    String getFocusedActivity() {
        return mFocusedActivityRecord;
    }

    String getResumedActivity() {
        return mResumedActivities.get(0);
    }

    int getResumedActivitiesCount() {
        return mResumedActivities.size();
    }

    boolean containsStack(int stackId) {
        for (ActivityStack stack : mStacks) {
            if (stackId == stack.mStackId) {
                return true;
            }
        }
        return false;
    }

    List<ActivityStack> getStacks() {
        return new ArrayList(mStacks);
    }

    int getStackCount() {
        return mStacks.size();
    }

    static class ActivityStack extends ActivityContainer {

        private static final Pattern TASK_ID_PATTERN = Pattern.compile("Task id #(\\d+)");
        private static final Pattern RESUMED_ACTIVITY_PATTERN = Pattern.compile(
                "mResumedActivity\\: ActivityRecord\\{(.+) u(\\d+) (\\S+) (\\S+)\\}");

        int mStackId;
        String mResumedActivity;
        ArrayList<ActivityTask> mTasks = new ArrayList();

        private ActivityStack() {
        }

        static ActivityStack create(
                LinkedList<String> dump, Pattern stackIdPattern, Pattern[] exitPatterns) {
            final String line = dump.peek().trim();

            final Matcher matcher = stackIdPattern.matcher(line);
            if (!matcher.matches()) {
                // Not a stack.
                return null;
            }
            // For the stack Id line we just read.
            dump.pop();

            final ActivityStack stack = new ActivityStack();
            CLog.logAndDisplay(INFO, line);
            final String stackId = matcher.group(1);
            CLog.logAndDisplay(INFO, stackId);
            stack.mStackId = Integer.parseInt(stackId);
            stack.extract(dump, exitPatterns);
            return stack;
        }

        private void extract(LinkedList<String> dump, Pattern[] exitPatterns) {

            final List<Pattern> taskExitPatterns = new ArrayList();
            Collections.addAll(taskExitPatterns, exitPatterns);
            taskExitPatterns.add(TASK_ID_PATTERN);
            taskExitPatterns.add(RESUMED_ACTIVITY_PATTERN);
            final Pattern[] taskExitPatternsArray =
                    taskExitPatterns.toArray(new Pattern[taskExitPatterns.size()]);

            while (!doneExtracting(dump, exitPatterns)) {
                final ActivityTask task =
                        ActivityTask.create(dump, TASK_ID_PATTERN, taskExitPatternsArray);

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

                Matcher matcher = RESUMED_ACTIVITY_PATTERN.matcher(line);
                if (matcher.matches()) {
                    CLog.logAndDisplay(INFO, line);
                    mResumedActivity = matcher.group(3);
                    CLog.logAndDisplay(INFO, mResumedActivity);
                    continue;
                }
            }
        }

        List<ActivityTask> getTasks() {
            return new ArrayList(mTasks);
        }
    }

    static class ActivityTask extends ActivityContainer {
        private static final Pattern TASK_RECORD_PATTERN = Pattern.compile(
                "\\* TaskRecord\\{(\\S+) #(\\d+) A=(\\S+) U=(\\d+) StackId=(\\d+) sz=(\\d+)\\}");

        private static final Pattern LAST_NON_FULLSCREEN_BOUNDS_PATTERN = Pattern.compile(
                "mLastNonFullscreenBounds=Rect\\((\\d+), (\\d+) - (\\d+), (\\d+)\\)");

        private static final Pattern ORIG_ACTIVITY_PATTERN = Pattern.compile("origActivity=(\\S+)");
        private static final Pattern REAL_ACTIVITY_PATTERN = Pattern.compile("realActivity=(\\S+)");

        int mTaskId;
        int mStackId;
        Rectangle mLastNonFullscreenBounds;
        String mRealActivity;
        String mOrigActivity;

        private ActivityTask() {
        }

        static ActivityTask create(
                LinkedList<String> dump, Pattern taskIdPattern, Pattern[] exitPatterns) {
            final String line = dump.peek().trim();

            final Matcher matcher = taskIdPattern.matcher(line);
            if (!matcher.matches()) {
                // Not a task.
                return null;
            }
            // For the task Id line we just read.
            dump.pop();

            final ActivityTask task = new ActivityTask();
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

                Matcher matcher = TASK_RECORD_PATTERN.matcher(line);
                if (matcher.matches()) {
                    CLog.logAndDisplay(INFO, line);
                    final String stackId = matcher.group(5);
                    mStackId = Integer.valueOf(stackId);
                    CLog.logAndDisplay(INFO, stackId);
                    continue;
                }

                matcher = LAST_NON_FULLSCREEN_BOUNDS_PATTERN.matcher(line);
                if (matcher.matches()) {
                    CLog.logAndDisplay(INFO, line);
                    mLastNonFullscreenBounds = getBounds(matcher);
                }

                matcher = REAL_ACTIVITY_PATTERN.matcher(line);
                if (matcher.matches()) {
                    if (mRealActivity == null) {
                        CLog.logAndDisplay(INFO, line);
                        mRealActivity = matcher.group(1);
                        CLog.logAndDisplay(INFO, mRealActivity);
                    }
                    continue;
                }

                matcher = ORIG_ACTIVITY_PATTERN.matcher(line);
                if (matcher.matches()) {
                    if (mOrigActivity == null) {
                        CLog.logAndDisplay(INFO, line);
                        mOrigActivity = matcher.group(1);
                        CLog.logAndDisplay(INFO, mOrigActivity);
                    }
                    continue;
                }
            }
        }
    }

    static abstract class ActivityContainer {
        protected static final Pattern FULLSCREEN_PATTERN = Pattern.compile("mFullscreen=(\\S+)");
        protected static final Pattern BOUNDS_PATTERN =
                Pattern.compile("mBounds=Rect\\((\\d+), (\\d+) - (\\d+), (\\d+)\\)");

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
