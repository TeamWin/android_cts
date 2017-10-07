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

package android.server.am;

import static android.app.WindowConfiguration.ACTIVITY_TYPE_HOME;
import static android.app.WindowConfiguration.ACTIVITY_TYPE_RECENTS;
import static android.app.WindowConfiguration.ACTIVITY_TYPE_STANDARD;
import static android.app.WindowConfiguration.ACTIVITY_TYPE_UNDEFINED;
import static android.app.WindowConfiguration.WINDOWING_MODE_UNDEFINED;
import static android.server.am.ProtoExtractors.extract;
import static android.server.am.StateLogger.log;
import static android.server.am.StateLogger.logE;

import android.graphics.Rect;
import android.graphics.nano.RectProto;
import android.os.ParcelFileDescriptor;
import android.support.test.InstrumentationRegistry;

import com.android.server.am.proto.nano.ActivityStackSupervisorProto;
import com.android.server.am.proto.nano.ActivityManagerServiceProto;
import com.android.server.am.proto.nano.ActivityRecordProto;
import com.android.server.am.proto.nano.ActivityStackProto;
import com.android.server.am.proto.nano.ActivityDisplayProto;
import com.android.server.am.proto.nano.KeyguardControllerProto;
import com.android.server.am.proto.nano.TaskRecordProto;
import com.android.server.wm.proto.nano.ConfigurationContainerProto;

import com.google.protobuf.nano.InvalidProtocolBufferNanoException;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class ActivityManagerState {

    public static final int DUMP_MODE_ACTIVITIES = 0;

    public static final String STATE_RESUMED = "RESUMED";
    public static final String STATE_PAUSED = "PAUSED";
    public static final String STATE_STOPPED = "STOPPED";
    public static final String STATE_DESTROYED = "DESTROYED";

    private static final String DUMPSYS_ACTIVITY_ACTIVITIES = "dumpsys activity --proto";

    // Stacks in z-order with the top most at the front of the list, starting with primary display.
    private final List<ActivityStack> mStacks = new ArrayList<>();
    // Stacks on all attached displays, in z-order with the top most at the front of the list.
    private final Map<Integer, List<ActivityStack>> mDisplayStacks = new HashMap<>();
    private KeyguardControllerState mKeyguardControllerState;
    private int mFocusedStackId = -1;
    private String mResumedActivityRecord = null;
    private final List<String> mResumedActivities = new ArrayList<>();

    void computeState() {
        computeState(DUMP_MODE_ACTIVITIES);
    }

    void computeState(int dumpMode) {
        // It is possible the system is in the middle of transition to the right state when we get
        // the dump. We try a few times to get the information we need before giving up.
        int retriesLeft = 3;
        boolean retry = false;
        byte[] dump = null;

        log("==============================");
        log("     ActivityManagerState     ");
        log("==============================");

        do {
            if (retry) {
                log("***Incomplete AM state. Retrying...");
                // Wait half a second between retries for activity manager to finish transitioning.
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    log(e.toString());
                    // Well I guess we are not waiting...
                }
            }

            String dumpsysCmd = "";
            switch (dumpMode) {
                case DUMP_MODE_ACTIVITIES:
                    dumpsysCmd = DUMPSYS_ACTIVITY_ACTIVITIES;
                    break;
            }

            dump = executeShellCommand(dumpsysCmd);
            try {
                parseSysDumpProto(dump);
            } catch (InvalidProtocolBufferNanoException ex) {
                throw new RuntimeException("Failed to parse dumpsys:\n"
                        + new String(dump, StandardCharsets.UTF_8), ex);
            }

            retry = mStacks.isEmpty() || mFocusedStackId == -1 || (mResumedActivityRecord == null
                    || mResumedActivities.isEmpty()) && !mKeyguardControllerState.keyguardShowing;
        } while (retry && retriesLeft-- > 0);

        if (mStacks.isEmpty()) {
            logE("No stacks found...");
        }
        if (mFocusedStackId == -1) {
            logE("No focused stack found...");
        }
        if (mResumedActivityRecord == null) {
            logE("No focused activity found...");
        }
        if (mResumedActivities.isEmpty()) {
            logE("No resumed activities found...");
        }
    }

    private byte[] executeShellCommand(String cmd) {
        try {
            ParcelFileDescriptor pfd =
                    InstrumentationRegistry.getInstrumentation().getUiAutomation()
                            .executeShellCommand(cmd);
            byte[] buf = new byte[512];
            int bytesRead;
            FileInputStream fis = new ParcelFileDescriptor.AutoCloseInputStream(pfd);
            ByteArrayOutputStream stdout = new ByteArrayOutputStream();
            while ((bytesRead = fis.read(buf)) != -1) {
                stdout.write(buf, 0, bytesRead);
            }
            fis.close();
            return stdout.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void parseSysDumpProto(byte[] sysDump) throws InvalidProtocolBufferNanoException {
        reset();

        ActivityManagerServiceProto activityManagerServiceProto =
                ActivityManagerServiceProto.parseFrom(sysDump);
        ActivityStackSupervisorProto state = activityManagerServiceProto.activities;
        for (int i = 0; i < state.displays.length; i++) {
            ActivityDisplayProto displayStack = state.displays[i];
            List<ActivityStack> activityStacks = new ArrayList<>();
            for (int j = 0; j < displayStack.stacks.length; j++) {
                ActivityStack activityStack = new ActivityStack(displayStack.stacks[j]);
                mStacks.add(activityStack);
                activityStacks.add(activityStack);
                if (activityStack.mResumedActivity != null) {
                    mResumedActivities.add(activityStack.mResumedActivity);
                }
            }
            mDisplayStacks.put(displayStack.id, activityStacks);
        }
        mKeyguardControllerState = new KeyguardControllerState(state.keyguardController);
        mFocusedStackId = state.focusedStackId;
        if (state.resumedActivity != null) {
            mResumedActivityRecord = state.resumedActivity.title;
        }
    }


    private void reset() {
        mStacks.clear();
        mFocusedStackId = -1;
        mResumedActivityRecord = null;
        mResumedActivities.clear();
        mKeyguardControllerState = null;
    }

    int getFrontStackId(int displayId) {
        return mDisplayStacks.get(displayId).get(0).mStackId;
    }

    int getFrontStackActivityType(int displayId) {
        return mDisplayStacks.get(displayId).get(0).getActivityType();
    }

    int getFrontStackWindowingMode(int displayId) {
        return mDisplayStacks.get(displayId).get(0).getWindowingMode();
    }

    int getFocusedStackId() {
        return mFocusedStackId;
    }

    int getFocusedStackActivityType() {
        final ActivityStack stack = getStackById(mFocusedStackId);
        return stack != null ? stack.getActivityType() : ACTIVITY_TYPE_UNDEFINED;
    }

    int getFocusedStackWindowingMode() {
        final ActivityStack stack = getStackById(mFocusedStackId);
        return stack != null ? stack.getWindowingMode() : WINDOWING_MODE_UNDEFINED;
    }

    String getFocusedActivity() {
        return mResumedActivityRecord;
    }

    String getResumedActivity() {
        return mResumedActivities.get(0);
    }

    int getResumedActivitiesCount() {
        return mResumedActivities.size();
    }

    public KeyguardControllerState getKeyguardControllerState() {
        return mKeyguardControllerState;
    }

    boolean containsStack(int stackId) {
        return getStackById(stackId) != null;
    }

    boolean containsStack(int windowingMode, int activityType) {
        for (ActivityStack stack : mStacks) {
            if (activityType != ACTIVITY_TYPE_UNDEFINED
                    && activityType != stack.getActivityType()) {
                continue;
            }
            if (windowingMode != WINDOWING_MODE_UNDEFINED
                    && windowingMode != stack.getWindowingMode()) {
                continue;
            }
            return true;
        }
        return false;
    }

    ActivityStack getStackById(int stackId) {
        for (ActivityStack stack : mStacks) {
            if (stackId == stack.mStackId) {
                return stack;
            }
        }
        return null;
    }

    ActivityStack getStackByActivityType(int activityType) {
        for (ActivityStack stack : mStacks) {
            if (activityType == stack.getActivityType()) {
                return stack;
            }
        }
        return null;
    }

    ActivityStack getStandardStackByWindowingMode(int windowingMode) {
        for (ActivityStack stack : mStacks) {
            if (stack.getActivityType() != ACTIVITY_TYPE_STANDARD) {
                continue;
            }
            if (stack.getWindowingMode() == windowingMode) {
                return stack;
            }
        }
        return null;
    }

    int getStackPosition(int activityType) {
        for (int i = 0; i < mStacks.size(); i++) {
            if (activityType == mStacks.get(i).getActivityType()) {
                return i;
            }
        }
        return -1;
    }

    List<ActivityStack> getStacks() {
        return new ArrayList<>(mStacks);
    }

    int getStackCount() {
        return mStacks.size();
    }

    boolean containsActivity(String activityName) {
        for (ActivityStack stack : mStacks) {
            for (ActivityTask task : stack.mTasks) {
                for (Activity activity : task.mActivities) {
                    if (activity.name.equals(activityName)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    boolean isActivityVisible(String activityName) {
        for (ActivityStack stack : mStacks) {
            for (ActivityTask task : stack.mTasks) {
                for (Activity activity : task.mActivities) {
                    if (activity.name.equals(activityName)) {
                        return activity.visible;
                    }
                }
            }
        }
        return false;
    }

    boolean containsStartedActivities() {
        for (ActivityStack stack : mStacks) {
            for (ActivityTask task : stack.mTasks) {
                for (Activity activity : task.mActivities) {
                    if (!activity.state.equals(STATE_STOPPED)
                            && !activity.state.equals(STATE_DESTROYED)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    boolean hasActivityState(String activityName, String activityState) {
        String fullName = ActivityManagerTestBase.getActivityComponentName(activityName);
        for (ActivityStack stack : mStacks) {
            for (ActivityTask task : stack.mTasks) {
                for (Activity activity : task.mActivities) {
                    if (activity.name.equals(fullName)) {
                        return activity.state.equals(activityState);
                    }
                }
            }
        }
        return false;
    }

    int getActivityProcId(String activityName) {
        for (ActivityStack stack : mStacks) {
            for (ActivityTask task : stack.mTasks) {
                for (Activity activity : task.mActivities) {
                    if (activity.name.equals(activityName)) {
                        return activity.procId;
                    }
                }
            }
        }
        return -1;
    }

    boolean isHomeActivityVisible() {
        final Activity homeActivity = getHomeActivity();
        return homeActivity != null && homeActivity.visible;
    }

    boolean isRecentsActivityVisible() {
        final Activity recentsActivity = getRecentsActivity();
        return recentsActivity != null && recentsActivity.visible;
    }

    String getHomeActivityName() {
        Activity activity = getHomeActivity();
        if (activity == null) {
            return null;
        }
        return activity.name;
    }

    ActivityTask getHomeTask() {
        final ActivityStack homeStack = getStackByActivityType(ACTIVITY_TYPE_HOME);
        if (homeStack != null && !homeStack.mTasks.isEmpty()) {
            return homeStack.mTasks.get(0);
        }
        return null;
    }

    private ActivityTask getRecentsTask() {
        final ActivityStack recentsStack = getStackByActivityType(ACTIVITY_TYPE_RECENTS);
        if (recentsStack != null && !recentsStack.mTasks.isEmpty()) {
            return recentsStack.mTasks.get(0);
        }
        return null;
    }

    private Activity getHomeActivity() {
        final ActivityTask homeTask = getHomeTask();
        return homeTask != null ? homeTask.mActivities.get(homeTask.mActivities.size() - 1) : null;
    }

    private Activity getRecentsActivity() {
        final ActivityTask recentsTask = getRecentsTask();
        return recentsTask != null ? recentsTask.mActivities.get(recentsTask.mActivities.size() - 1)
                : null;
    }

    ActivityTask getTaskByActivityName(String activityName) {
        return getTaskByActivityName(activityName, WINDOWING_MODE_UNDEFINED);
    }

    ActivityTask getTaskByActivityName(String activityName, int windowingMode) {
        String fullName = ActivityManagerTestBase.getActivityComponentName(activityName);
        for (ActivityStack stack : mStacks) {
            if (windowingMode == WINDOWING_MODE_UNDEFINED
                    || windowingMode == stack.getWindowingMode()) {
                for (ActivityTask task : stack.mTasks) {
                    for (Activity activity : task.mActivities) {
                        if (activity.name.equals(fullName)) {
                            return task;
                        }
                    }
                }
            }
        }
        return null;
    }

    static class ActivityStack extends ActivityContainer {

        int mDisplayId;
        int mStackId;
        String mResumedActivity;
        ArrayList<ActivityTask> mTasks = new ArrayList<>();

        ActivityStack(ActivityStackProto proto) {
            super(proto.configurationContainer);
            mStackId = proto.id;
            mDisplayId = proto.displayId;
            mBounds = extract(proto.bounds);
            mFullscreen = proto.fullscreen;
            for (int i = 0; i < proto.tasks.length; i++) {
                mTasks.add(new ActivityTask(proto.tasks[i]));
            }
            if (proto.resumedActivity != null) {
                mResumedActivity = proto.resumedActivity.title;
            }
        }

        /**
         * @return the bottom task in the stack.
         */
        ActivityTask getBottomTask() {
            if (!mTasks.isEmpty()) {
                // NOTE: Unlike the ActivityManager internals, we dump the state from top to bottom,
                //       so the indices are inverted
                return mTasks.get(mTasks.size() - 1);
            }
            return null;
        }

        /**
         * @return the top task in the stack.
         */
        ActivityTask getTopTask() {
            if (!mTasks.isEmpty()) {
                // NOTE: Unlike the ActivityManager internals, we dump the state from top to bottom,
                //       so the indices are inverted
                return mTasks.get(0);
            }
            return null;
        }

        List<ActivityTask> getTasks() {
            return new ArrayList<>(mTasks);
        }

        ActivityTask getTask(int taskId) {
            for (ActivityTask task : mTasks) {
                if (taskId == task.mTaskId) {
                    return task;
                }
            }
            return null;
        }
    }

    static class ActivityTask extends ActivityContainer {

        int mTaskId;
        int mStackId;
        Rect mLastNonFullscreenBounds;
        String mRealActivity;
        String mOrigActivity;
        ArrayList<Activity> mActivities = new ArrayList<>();
        int mTaskType = -1;
        int mReturnToType = -1;
        private int mResizeMode;

        ActivityTask(TaskRecordProto proto) {
            super(proto.configurationContainer);
            mTaskId = proto.id;
            mStackId = proto.stackId;
            mLastNonFullscreenBounds = extract(proto.lastNonFullscreenBounds);
            mRealActivity = proto.realActivity;
            mOrigActivity = proto.origActivity;
            mTaskType = proto.activityType;
            mReturnToType = proto.returnToType;
            mResizeMode = proto.resizeMode;
            mFullscreen = proto.fullscreen;
            mBounds = extract(proto.bounds);
            mMinWidth = proto.minWidth;
            mMinHeight = proto.minHeight;
            for (int i = 0;  i < proto.activities.length; i++) {
                mActivities.add(new Activity(proto.activities[i]));
            }
        }

        public int getResizeMode() {
            return mResizeMode;
        }

        /**
         * @return whether this task contains the given activity.
         */
        public boolean containsActivity(String activityName) {
            for (Activity activity : mActivities) {
                if (activity.name.equals(activityName)) {
                    return true;
                }
            }
            return false;
        }
    }

    static class Activity extends ActivityContainer {

        String name;
        String state;
        boolean visible;
        boolean frontOfTask;
        int procId = -1;

        Activity(ActivityRecordProto proto) {
            super(proto.configurationContainer);
            name = proto.identifier.title;
            state = proto.state;
            visible = proto.visible;
            frontOfTask = proto.frontOfTask;
            if (proto.procId != 0) {
                procId = proto.procId;
            }
        }
    }

    static abstract class ActivityContainer extends WindowManagerState.ConfigurationContainer {
        protected boolean mFullscreen;
        protected Rect mBounds;
        protected int mMinWidth = -1;
        protected int mMinHeight = -1;

        ActivityContainer(ConfigurationContainerProto proto) {
            super(proto);
        }

        Rect getBounds() {
            return mBounds;
        }

        boolean isFullscreen() {
            return mFullscreen;
        }

        int getMinWidth() {
            return mMinWidth;
        }

        int getMinHeight() {
            return mMinHeight;
        }
    }

    static class KeyguardControllerState {

        boolean keyguardShowing = false;
        boolean keyguardOccluded = false;

        KeyguardControllerState(KeyguardControllerProto proto) {
            if (proto != null) {
                keyguardShowing = proto.keyguardShowing;
                keyguardOccluded = proto.keyguardOccluded;
            }
        }
    }
}
