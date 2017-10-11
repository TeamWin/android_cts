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

import static android.app.WindowConfiguration.ACTIVITY_TYPE_UNDEFINED;
import static android.app.WindowConfiguration.WINDOWING_MODE_UNDEFINED;
import static android.server.am.ProtoExtractors.extract;
import static android.server.am.StateLogger.log;
import static android.server.am.StateLogger.logE;

import static org.junit.Assert.fail;

import android.content.res.Configuration;
import android.graphics.Rect;
import android.os.ParcelFileDescriptor;
import android.support.test.InstrumentationRegistry;
import android.view.nano.DisplayInfoProto;

import com.android.server.wm.proto.nano.AppTransitionProto;
import com.android.server.wm.proto.nano.AppWindowTokenProto;
import com.android.server.wm.proto.nano.ConfigurationContainerProto;
import com.android.server.wm.proto.nano.DisplayProto;
import com.android.server.wm.proto.nano.IdentifierProto;
import com.android.server.wm.proto.nano.PinnedStackControllerProto;
import com.android.server.wm.proto.nano.StackProto;
import com.android.server.wm.proto.nano.TaskProto;
import com.android.server.wm.proto.nano.WindowContainerProto;
import com.android.server.wm.proto.nano.WindowManagerServiceProto;
import com.android.server.wm.proto.nano.WindowStateAnimatorProto;
import com.android.server.wm.proto.nano.WindowStateProto;
import com.android.server.wm.proto.nano.WindowSurfaceControllerProto;
import com.android.server.wm.proto.nano.WindowTokenProto;

import com.google.protobuf.nano.InvalidProtocolBufferNanoException;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class WindowManagerState {

    public static final String TRANSIT_ACTIVITY_OPEN = "TRANSIT_ACTIVITY_OPEN";
    public static final String TRANSIT_ACTIVITY_CLOSE = "TRANSIT_ACTIVITY_CLOSE";
    public static final String TRANSIT_TASK_OPEN = "TRANSIT_TASK_OPEN";
    public static final String TRANSIT_TASK_CLOSE = "TRANSIT_TASK_CLOSE";

    public static final String TRANSIT_WALLPAPER_OPEN = "TRANSIT_WALLPAPER_OPEN";
    public static final String TRANSIT_WALLPAPER_CLOSE = "TRANSIT_WALLPAPER_CLOSE";
    public static final String TRANSIT_WALLPAPER_INTRA_OPEN = "TRANSIT_WALLPAPER_INTRA_OPEN";
    public static final String TRANSIT_WALLPAPER_INTRA_CLOSE = "TRANSIT_WALLPAPER_INTRA_CLOSE";

    public static final String TRANSIT_KEYGUARD_GOING_AWAY = "TRANSIT_KEYGUARD_GOING_AWAY";
    public static final String TRANSIT_KEYGUARD_GOING_AWAY_ON_WALLPAPER =
            "TRANSIT_KEYGUARD_GOING_AWAY_ON_WALLPAPER";
    public static final String TRANSIT_KEYGUARD_OCCLUDE = "TRANSIT_KEYGUARD_OCCLUDE";
    public static final String TRANSIT_KEYGUARD_UNOCCLUDE = "TRANSIT_KEYGUARD_UNOCCLUDE";

    public static final String APP_STATE_IDLE = "APP_STATE_IDLE";

    private static final String DUMPSYS_WINDOW = "dumpsys window -a --proto";

    private static final String STARTING_WINDOW_PREFIX = "Starting ";
    private static final String DEBUGGER_WINDOW_PREFIX = "Waiting For Debugger: ";

    // Windows in z-order with the top most at the front of the list.
    private List<WindowState> mWindowStates = new ArrayList();
    // Stacks in z-order with the top most at the front of the list, starting with primary display.
    private final List<WindowStack> mStacks = new ArrayList();
    // Stacks on all attached displays, in z-order with the top most at the front of the list.
    private final Map<Integer, List<WindowStack>> mDisplayStacks
            = new HashMap<>();
    private List<Display> mDisplays = new ArrayList();
    private String mFocusedWindow = null;
    private String mFocusedApp = null;
    private String mLastTransition = null;
    private String mAppTransitionState = null;
    private String mInputMethodWindowAppToken = null;
    private Rect mStableBounds = new Rect();
    private Rect mDefaultPinnedStackBounds = new Rect();
    private Rect mPinnedStackMovementBounds = new Rect();
    private final LinkedList<String> mSysDump = new LinkedList();
    private int mRotation;
    private int mLastOrientation;
    private boolean mDisplayFrozen;
    private boolean mIsDockedStackMinimized;

    public void computeState() {
        // It is possible the system is in the middle of transition to the right state when we get
        // the dump. We try a few times to get the information we need before giving up.
        int retriesLeft = 3;
        boolean retry = false;
        byte[] dump = null;

        log("==============================");
        log("      WindowManagerState      ");
        log("==============================");
        do {
            if (retry) {
                log("***Incomplete WM state. Retrying...");
                // Wait half a second between retries for window manager to finish transitioning...
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    log(e.toString());
                    // Well I guess we are not waiting...
                }
            }

            dump = executeShellCommand(DUMPSYS_WINDOW);
            try {
                parseSysDumpProto(dump);
            } catch (InvalidProtocolBufferNanoException ex) {
                throw new RuntimeException("Failed to parse dumpsys:\n"
                        + new String(dump, StandardCharsets.UTF_8), ex);
            }

            retry = mWindowStates.isEmpty() || mFocusedApp == null;
        } while (retry && retriesLeft-- > 0);

        if (mWindowStates.isEmpty()) {
            logE("No Windows found...");
        }
        if (mFocusedWindow == null) {
            logE("No Focused Window...");
        }
        if (mFocusedApp == null) {
            logE("No Focused App...");
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
        WindowManagerServiceProto state = WindowManagerServiceProto.parseFrom(sysDump);
        List<WindowState> allWindows = new ArrayList<>();
        Map<String, WindowState> windowMap = new HashMap<>();
        if (state.focusedWindow != null) {
            mFocusedWindow = state.focusedWindow.title;
        }
        mFocusedApp = state.focusedApp;
        for (int i = 0; i < state.rootWindowContainer.displays.length; i++) {
            DisplayProto displayProto = state.rootWindowContainer.displays[i];
            final Display display = new Display(displayProto);
            mDisplays.add(display);
            allWindows.addAll(display.getWindows());
            List<WindowStack> stacks = new ArrayList<>();
            for (int j = 0; j < displayProto.stacks.length; j++) {
                StackProto stackProto = displayProto.stacks[j];
                final WindowStack stack = new WindowStack(stackProto);
                mStacks.add(stack);
                stacks.add(stack);
                allWindows.addAll(stack.getWindows());
            }
            mDisplayStacks.put(display.mDisplayId, stacks);

            // use properties from the default display only
            if (display.getDisplayId() == ActivityAndWindowManagersState.DEFAULT_DISPLAY_ID) {
                if (displayProto.dockedStackDividerController != null) {
                    mIsDockedStackMinimized =
                            displayProto.dockedStackDividerController.minimizedDock;
                }
                PinnedStackControllerProto pinnedStackProto = displayProto.pinnedStackController;
                if (pinnedStackProto != null) {
                    mDefaultPinnedStackBounds = extract(pinnedStackProto.defaultBounds);
                    mPinnedStackMovementBounds = extract(pinnedStackProto.movementBounds);
                }
            }
        }
        for (WindowState w : allWindows) {
            windowMap.put(w.getToken(), w);
        }
        for (int i = 0; i < state.rootWindowContainer.windows.length; i++) {
            IdentifierProto identifierProto = state.rootWindowContainer.windows[i];
            String hash_code = Integer.toHexString(identifierProto.hashCode);
            mWindowStates.add(windowMap.get(hash_code));
        }
        if (state.policy != null) {
            mStableBounds = extract(state.policy.stableBounds);
        }
        if (state.inputMethodWindow != null) {
            mInputMethodWindowAppToken = Integer.toHexString(state.inputMethodWindow.hashCode);
        }
        mDisplayFrozen = state.displayFrozen;
        mRotation = state.rotation;
        mLastOrientation = state.lastOrientation;
        AppTransitionProto appTransitionProto = state.appTransition;
        int appState = 0;
        int lastTransition = 0;
        if (appTransitionProto != null) {
            appState = appTransitionProto.appTransitionState;
            lastTransition = appTransitionProto.lastUsedAppTransition;
        }
        mAppTransitionState = appStateToString(appState);
        mLastTransition = appTransitionToString(lastTransition);
    }

    static String appStateToString(int appState) {
        switch (appState) {
            case AppTransitionProto.APP_STATE_IDLE:
                return "APP_STATE_IDLE";
            case AppTransitionProto.APP_STATE_READY:
                return "APP_STATE_READY";
            case AppTransitionProto.APP_STATE_RUNNING:
                return "APP_STATE_RUNNING";
            case AppTransitionProto.APP_STATE_TIMEOUT:
                return "APP_STATE_TIMEOUT";
            default:
                fail("Invalid AppTransitionState");
                return null;
        }
    }

    static String appTransitionToString(int transition) {
        switch (transition) {
            case AppTransitionProto.TRANSIT_UNSET: {
                return "TRANSIT_UNSET";
            }
            case AppTransitionProto.TRANSIT_NONE: {
                return "TRANSIT_NONE";
            }
            case AppTransitionProto.TRANSIT_ACTIVITY_OPEN: {
                return TRANSIT_ACTIVITY_OPEN;
            }
            case AppTransitionProto.TRANSIT_ACTIVITY_CLOSE: {
                return TRANSIT_ACTIVITY_CLOSE;
            }
            case AppTransitionProto.TRANSIT_TASK_OPEN: {
                return TRANSIT_TASK_OPEN;
            }
            case AppTransitionProto.TRANSIT_TASK_CLOSE: {
                return TRANSIT_TASK_CLOSE;
            }
            case AppTransitionProto.TRANSIT_TASK_TO_FRONT: {
                return "TRANSIT_TASK_TO_FRONT";
            }
            case AppTransitionProto.TRANSIT_TASK_TO_BACK: {
                return "TRANSIT_TASK_TO_BACK";
            }
            case AppTransitionProto.TRANSIT_WALLPAPER_CLOSE: {
                return TRANSIT_WALLPAPER_CLOSE;
            }
            case AppTransitionProto.TRANSIT_WALLPAPER_OPEN: {
                return TRANSIT_WALLPAPER_OPEN;
            }
            case AppTransitionProto.TRANSIT_WALLPAPER_INTRA_OPEN: {
                return TRANSIT_WALLPAPER_INTRA_OPEN;
            }
            case AppTransitionProto.TRANSIT_WALLPAPER_INTRA_CLOSE: {
                return TRANSIT_WALLPAPER_INTRA_CLOSE;
            }
            case AppTransitionProto.TRANSIT_TASK_OPEN_BEHIND: {
                return "TRANSIT_TASK_OPEN_BEHIND";
            }
            case AppTransitionProto.TRANSIT_ACTIVITY_RELAUNCH: {
                return "TRANSIT_ACTIVITY_RELAUNCH";
            }
            case AppTransitionProto.TRANSIT_DOCK_TASK_FROM_RECENTS: {
                return "TRANSIT_DOCK_TASK_FROM_RECENTS";
            }
            case AppTransitionProto.TRANSIT_KEYGUARD_GOING_AWAY: {
                return TRANSIT_KEYGUARD_GOING_AWAY;
            }
            case AppTransitionProto.TRANSIT_KEYGUARD_GOING_AWAY_ON_WALLPAPER: {
                return TRANSIT_KEYGUARD_GOING_AWAY_ON_WALLPAPER;
            }
            case AppTransitionProto.TRANSIT_KEYGUARD_OCCLUDE: {
                return TRANSIT_KEYGUARD_OCCLUDE;
            }
            case AppTransitionProto.TRANSIT_KEYGUARD_UNOCCLUDE: {
                return TRANSIT_KEYGUARD_UNOCCLUDE;
            }
            default: {
                fail("Invalid lastUsedAppTransition");
                return null;
            }
        }
    }

    void getMatchingWindowTokens(final String windowName, List<String> tokenList) {
        tokenList.clear();

        for (WindowState ws : mWindowStates) {
            if (windowName.equals(ws.getName())) {
                tokenList.add(ws.getToken());
            }
        }
    }

    public void getMatchingVisibleWindowState(final String windowName, List<WindowState> windowList) {
        windowList.clear();
        for (WindowState ws : mWindowStates) {
            if (ws.isShown() && windowName.equals(ws.getName())) {
                windowList.add(ws);
            }
        }
    }

    public void getPrefixMatchingVisibleWindowState(final String windowName,
            List<WindowState> windowList) {
        windowList.clear();
        for (WindowState ws : mWindowStates) {
            if (ws.isShown() && ws.getName().startsWith(windowName)) {
                windowList.add(ws);
            }
        }
    }

    public WindowState getWindowByPackageName(String packageName, int windowType) {
        for (WindowState ws : mWindowStates) {
            final String name = ws.getName();
            if (name == null || !name.contains(packageName)) {
                continue;
            }
            if (windowType != ws.getType()) {
                continue;
            }
            return ws;
        }

        return null;
    }

    public void getWindowsByPackageName(String packageName, List<Integer> restrictToTypeList,
            List<WindowState> outWindowList) {
        outWindowList.clear();
        for (WindowState ws : mWindowStates) {
            final String name = ws.getName();
            if (name == null || !name.contains(packageName)) {
                continue;
            }
            if (restrictToTypeList != null && !restrictToTypeList.contains(ws.getType())) {
                continue;
            }
            outWindowList.add(ws);
        }
    }

    public void sortWindowsByLayer(List<WindowState> windows) {
        windows.sort(Comparator.comparingInt(WindowState::getLayer));
    }

    WindowState getWindowStateForAppToken(String appToken) {
        for (WindowState ws : mWindowStates) {
            if (ws.getToken().equals(appToken)) {
                return ws;
            }
        }
        return null;
    }

    Display getDisplay(int displayId) {
        for (Display display : mDisplays) {
            if (displayId == display.getDisplayId()) {
                return display;
            }
        }
        return null;
    }

    String getFrontWindow() {
        if (mWindowStates == null || mWindowStates.isEmpty()) {
            return null;
        }
        return mWindowStates.get(0).getName();
    }

    public String getFocusedWindow() {
        return mFocusedWindow;
    }

    String getFocusedApp() {
        return mFocusedApp;
    }

    String getLastTransition() {
        return mLastTransition;
    }

    String getAppTransitionState() {
        return mAppTransitionState;
    }

    int getFrontStackId(int displayId) {
        return mDisplayStacks.get(displayId).get(0).mStackId;
    }

    int getFrontStackActivityType(int displayId) {
        return mDisplayStacks.get(displayId).get(0).getActivityType();
    }

    public int getRotation() {
        return mRotation;
    }

    int getLastOrientation() {
        return mLastOrientation;
    }

    boolean containsStack(int stackId) {
        for (WindowStack stack : mStacks) {
            if (stackId == stack.mStackId) {
                return true;
            }
        }
        return false;
    }

    boolean containsStack(int windowingMode, int activityType) {
        for (WindowStack stack : mStacks) {
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

    /**
     * Check if there exists a window record with matching windowName.
     */
    boolean containsWindow(String windowName) {
        for (WindowState window : mWindowStates) {
            if (window.getName().equals(windowName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if at least one window which matches provided window name is visible.
     */
    boolean isWindowVisible(String windowName) {
        for (WindowState window : mWindowStates) {
            if (window.getName().equals(windowName)) {
                if (window.isShown()) {
                    return true;
                }
            }
        }
        return false;
    }

    boolean allWindowsVisible(String windowName) {
        boolean allVisible = false;
        for (WindowState window : mWindowStates) {
            if (window.getName().equals(windowName)) {
                if (!window.isShown()) {
                    log("[VISIBLE] not visible" + windowName);
                    return false;
                }
                log("[VISIBLE] visible" + windowName);
                allVisible = true;
            }
        }
        return allVisible;
    }

    WindowStack getStack(int stackId) {
        for (WindowStack stack : mStacks) {
            if (stackId == stack.mStackId) {
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

    WindowState getInputMethodWindowState() {
        return getWindowStateForAppToken(mInputMethodWindowAppToken);
    }

    Rect getStableBounds() {
        return mStableBounds;
    }

    Rect getDefaultPinnedStackBounds() {
        return mDefaultPinnedStackBounds;
    }

    Rect getPinnedStackMomentBounds() {
        return mPinnedStackMovementBounds;
    }

    WindowState findFirstWindowWithType(int type) {
        for (WindowState window : mWindowStates) {
            if (window.getType() == type) {
                return window;
            }
        }
        return null;
    }

    public boolean isDisplayFrozen() {
        return mDisplayFrozen;
    }

    public boolean isDockedStackMinimized() {
        return mIsDockedStackMinimized;
    }

    private void reset() {
        mSysDump.clear();
        mStacks.clear();
        mDisplays.clear();
        mWindowStates.clear();
        mFocusedWindow = null;
        mFocusedApp = null;
        mInputMethodWindowAppToken = null;
        mIsDockedStackMinimized = false;
    }

    class WindowStack extends WindowContainer {

        int mStackId;
        ArrayList<WindowTask> mTasks = new ArrayList();
        boolean mWindowAnimationBackgroundSurfaceShowing;

        WindowStack(StackProto proto) {
            super(proto.windowContainer);
            mStackId = proto.id;
            mFullscreen = proto.fillsParent;
            mBounds = extract(proto.bounds);
            for (int i = 0; i < proto.tasks.length; i++) {
                TaskProto taskProto = proto.tasks[i];
                WindowTask task = new WindowTask(taskProto);
                mTasks.add(task);
                mSubWindows.addAll(task.getWindows());
            }
            mWindowAnimationBackgroundSurfaceShowing = proto.animationBackgroundSurfaceIsDimming;
        }

        WindowTask getTask(int taskId) {
            for (WindowTask task : mTasks) {
                if (taskId == task.mTaskId) {
                    return task;
                }
            }
            return null;
        }

        boolean isWindowAnimationBackgroundSurfaceShowing() {
            return mWindowAnimationBackgroundSurfaceShowing;
        }
    }

    class WindowTask extends WindowContainer {

        int mTaskId;
        Rect mTempInsetBounds;
        List<String> mAppTokens = new ArrayList();

        WindowTask(TaskProto proto) {
            super(proto.windowContainer);
            mTaskId = proto.id;
            mFullscreen = proto.fillsParent;
            mBounds = extract(proto.bounds);
            for (int i = 0; i < proto.appWindowTokens.length; i++) {
                AppWindowTokenProto appWindowTokenProto = proto.appWindowTokens[i];
                mAppTokens.add(appWindowTokenProto.name);
                WindowTokenProto windowTokenProto = appWindowTokenProto.windowToken;
                for (int j = 0; j < windowTokenProto.windows.length; j++) {
                    WindowStateProto windowProto = windowTokenProto.windows[j];
                    WindowState window = new WindowState(windowProto);
                    mSubWindows.add(window);
                    mSubWindows.addAll(window.getWindows());
                }
            }
            mTempInsetBounds = extract(proto.tempInsetBounds);
        }
    }

    static class ConfigurationContainer {
        final Configuration mOverrideConfiguration = new Configuration();
        final Configuration mFullConfiguration = new Configuration();
        final Configuration mMergedOverrideConfiguration = new Configuration();

        ConfigurationContainer(ConfigurationContainerProto proto) {
            if (proto == null) {
                return;
            }
            mOverrideConfiguration.setTo(extract(proto.overrideConfiguration));
            mFullConfiguration.setTo(extract(proto.fullConfiguration));
            mMergedOverrideConfiguration.setTo(extract(proto.mergedOverrideConfiguration));
        }

        int getWindowingMode() {
            if (mFullConfiguration == null) {
                return WINDOWING_MODE_UNDEFINED;
            }
            return mFullConfiguration.windowConfiguration.getWindowingMode();
        }

        int getActivityType() {
            if (mFullConfiguration == null) {
                return ACTIVITY_TYPE_UNDEFINED;
            }
            return mFullConfiguration.windowConfiguration.getActivityType();
        }
    }

    abstract class WindowContainer extends ConfigurationContainer {

        protected boolean mFullscreen;
        protected Rect mBounds;
        protected int mOrientation;
        protected List<WindowState> mSubWindows = new ArrayList<>();

        WindowContainer(WindowContainerProto proto) {
            super(proto.configurationContainer);
            mOrientation = proto.orientation;
        }

        Rect getBounds() {
            return mBounds;
        }

        boolean isFullscreen() {
            return mFullscreen;
        }

        List<WindowState> getWindows() {
            return mSubWindows;
        }
    }

    class Display extends WindowContainer {

        private final int mDisplayId;
        private Rect mDisplayRect = new Rect();
        private Rect mAppRect = new Rect();
        private int mDpi;

        public Display(DisplayProto proto) {
            super(proto.windowContainer);
            mDisplayId = proto.id;
            for (int i = 0; i < proto.aboveAppWindows.length; i++) {
                addWindowsFromTokenProto(proto.aboveAppWindows[i]);
            }
            for (int i = 0; i < proto.belowAppWindows.length; i++) {
                addWindowsFromTokenProto(proto.belowAppWindows[i]);
            }
            for (int i = 0; i < proto.imeWindows.length; i++) {
                addWindowsFromTokenProto(proto.imeWindows[i]);
            }
            mDpi = proto.dpi;
            DisplayInfoProto infoProto = proto.displayInfo;
            if (infoProto != null) {
                mDisplayRect.set(0, 0, infoProto.logicalWidth, infoProto.logicalHeight);
                mAppRect.set(0, 0, infoProto.logicalWidth, infoProto.logicalHeight);
            }
        }

        private void addWindowsFromTokenProto(WindowTokenProto proto) {
            for (int j = 0; j < proto.windows.length; j++) {
                WindowStateProto windowProto = proto.windows[j];
                WindowState childWindow = new WindowState(windowProto);
                mSubWindows.add(childWindow);
                mSubWindows.addAll(childWindow.getWindows());
            }
        }

        int getDisplayId() {
            return mDisplayId;
        }

        int getDpi() {
            return mDpi;
        }

        Rect getDisplayRect() {
            return mDisplayRect;
        }

        Rect getAppRect() {
            return mAppRect;
        }

        @Override
        public String toString() {
            return "Display #" + mDisplayId + ": mDisplayRect=" + mDisplayRect
                    + " mAppRect=" + mAppRect;
        }
    }

    public class WindowState extends WindowContainer {

        private static final int WINDOW_TYPE_NORMAL = 0;
        private static final int WINDOW_TYPE_STARTING = 1;
        private static final int WINDOW_TYPE_EXITING = 2;
        private static final int WINDOW_TYPE_DEBUGGER = 3;

        private String mName;
        private final String mAppToken;
        private final int mWindowType;
        private int mType = 0;
        private int mDisplayId;
        private int mStackId;
        private int mLayer;
        private boolean mShown;
        private Rect mContainingFrame = new Rect();
        private Rect mParentFrame = new Rect();
        private Rect mContentFrame = new Rect();
        private Rect mFrame = new Rect();
        private Rect mSurfaceInsets = new Rect();
        private Rect mContentInsets = new Rect();
        private Rect mGivenContentInsets = new Rect();
        private Rect mCrop = new Rect();

        WindowState(WindowStateProto proto) {
            super(proto.windowContainer);
            IdentifierProto identifierProto = proto.identifier;
            mName = identifierProto.title;
            mAppToken = Integer.toHexString(identifierProto.hashCode);
            mDisplayId = proto.displayId;
            mStackId = proto.stackId;
            if (proto.attributes != null) {
                mType = proto.attributes.type;
            }
            WindowStateAnimatorProto animatorProto = proto.animator;
            if (animatorProto != null) {
                if (animatorProto.surface != null) {
                    WindowSurfaceControllerProto surfaceProto = animatorProto.surface;
                    mShown = surfaceProto.shown;
                    mLayer = surfaceProto.layer;
                }
                mCrop = extract(animatorProto.lastClipRect);
            }
            mGivenContentInsets = extract(proto.givenContentInsets);
            mFrame = extract(proto.frame);
            mContainingFrame = extract(proto.containingFrame);
            mParentFrame = extract(proto.parentFrame);
            mContentFrame = extract(proto.contentFrame);
            mContentInsets = extract(proto.contentInsets);
            mSurfaceInsets = extract(proto.surfaceInsets);
            if (mName.startsWith(STARTING_WINDOW_PREFIX)) {
                mWindowType = WINDOW_TYPE_STARTING;
                // Existing code depends on the prefix being removed
                mName = mName.substring(STARTING_WINDOW_PREFIX.length());
            } else if (proto.animatingExit) {
                mWindowType = WINDOW_TYPE_EXITING;
            } else if (mName.startsWith(DEBUGGER_WINDOW_PREFIX)) {
                mWindowType = WINDOW_TYPE_STARTING;
                mName = mName.substring(DEBUGGER_WINDOW_PREFIX.length());
            } else {
                mWindowType = 0;
            }
            for (int i = 0; i < proto.childWindows.length; i++) {
                WindowStateProto childProto = proto.childWindows[i];
                WindowState childWindow = new WindowState(childProto);
                mSubWindows.add(childWindow);
                mSubWindows.addAll(childWindow.getWindows());
            }
        }

        public String getName() {
            return mName;
        }

        String getToken() {
            return mAppToken;
        }

        boolean isStartingWindow() {
            return mWindowType == WINDOW_TYPE_STARTING;
        }

        boolean isExitingWindow() {
            return mWindowType == WINDOW_TYPE_EXITING;
        }

        boolean isDebuggerWindow() {
            return mWindowType == WINDOW_TYPE_DEBUGGER;
        }

        int getDisplayId() {
            return mDisplayId;
        }

        int getStackId() {
            return mStackId;
        }

        public int getLayer() {
            return mLayer;
        }

        Rect getContainingFrame() {
            return mContainingFrame;
        }

        public Rect getFrame() {
            return mFrame;
        }

        Rect getSurfaceInsets() {
            return mSurfaceInsets;
        }

        Rect getContentInsets() {
            return mContentInsets;
        }

        Rect getGivenContentInsets() {
            return mGivenContentInsets;
        }

        public Rect getContentFrame() {
            return mContentFrame;
        }

        Rect getParentFrame() {
            return mParentFrame;
        }

        Rect getCrop() {
            return mCrop;
        }

        boolean isShown() {
            return mShown;
        }

        public int getType() {
            return mType;
        }

        private String getWindowTypeSuffix(int windowType) {
            switch (windowType) {
                case WINDOW_TYPE_STARTING:
                    return " STARTING";
                case WINDOW_TYPE_EXITING:
                    return " EXITING";
                case WINDOW_TYPE_DEBUGGER:
                    return " DEBUGGER";
                default:
                    break;
            }
            return "";
        }

        @Override
        public String toString() {
            return "WindowState: {" + mAppToken + " " + mName
                    + getWindowTypeSuffix(mWindowType) + "}" + " type=" + mType
                    + " cf=" + mContainingFrame + " pf=" + mParentFrame;
        }
    }
}
