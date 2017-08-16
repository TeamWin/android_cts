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

import static android.server.cts.ActivityAndWindowManagersState.DEFAULT_DISPLAY_ID;
import static android.server.cts.StateLogger.log;
import static android.server.cts.StateLogger.logE;

import android.graphics.RectProto;
import com.android.server.wm.proto.AppWindowTokenProto;
import com.android.server.wm.proto.AppTransitionProto;
import com.android.server.wm.proto.DisplayProto;
import com.android.server.wm.proto.IdentifierProto;
import com.android.server.wm.proto.PinnedStackControllerProto;
import com.android.server.wm.proto.StackProto;
import com.android.server.wm.proto.TaskProto;
import com.android.server.wm.proto.WindowManagerServiceProto;
import com.android.server.wm.proto.WindowStateAnimatorProto;
import com.android.server.wm.proto.WindowStateProto;
import com.android.server.wm.proto.WindowSurfaceControllerProto;
import com.android.server.wm.proto.WindowTokenProto;
import android.view.DisplayInfoProto;
import com.android.tradefed.device.CollectingByteOutputReceiver;
import com.android.tradefed.device.ITestDevice;

import com.google.protobuf.InvalidProtocolBufferException;

import java.awt.Rectangle;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
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
    private Rectangle mStableBounds = new Rectangle();
    private Rectangle mDefaultPinnedStackBounds = new Rectangle();
    private Rectangle mPinnedStackMovementBounds = new Rectangle();
    private final LinkedList<String> mSysDump = new LinkedList();
    private int mRotation;
    private int mLastOrientation;
    private boolean mDisplayFrozen;
    private boolean mIsDockedStackMinimized;

    void computeState(ITestDevice device) throws Exception {
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

            final CollectingByteOutputReceiver outputReceiver = new CollectingByteOutputReceiver();
            device.executeShellCommand(DUMPSYS_WINDOW, outputReceiver);
            dump = outputReceiver.getOutput();
            try {
                parseSysDumpProto(dump);
            } catch (InvalidProtocolBufferException ex) {
                System.out.println(new String(dump, StandardCharsets.UTF_8));
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

    private void parseSysDumpProto(byte[] sysDump) throws Exception {
        reset();
        List<WindowState> allWindows = new ArrayList<>();
        Map<String, WindowState> windowMap = new HashMap<>();
        WindowManagerServiceProto state = WindowManagerServiceProto.parser().parseFrom(sysDump);
        if (state.hasFocusedWindow()) {
            mFocusedWindow = state.getFocusedWindow().getTitle();
        }
        mFocusedApp = state.getFocusedApp();
        for (int i = 0; i < state.getDisplaysCount(); i++) {
            DisplayProto displayProto = state.getDisplays(i);
            final Display display = new Display(displayProto);
            mDisplays.add(display);
            allWindows.addAll(display.getWindows());
            List<WindowStack> stacks = new ArrayList<>();
            for (int j = 0; j < displayProto.getStacksCount(); j++) {
                StackProto stackProto = displayProto.getStacks(j);
                final WindowStack stack = new WindowStack(stackProto);
                mStacks.add(stack);
                stacks.add(stack);
                allWindows.addAll(stack.getWindows());
            }
            mDisplayStacks.put(display.mDisplayId, stacks);

            // use properties from the default display only
            if (display.getDisplayId() == DEFAULT_DISPLAY_ID) {
                mIsDockedStackMinimized = displayProto.getDockedStackDividerController().getMinimizedDock();
                PinnedStackControllerProto pinnedStackProto = displayProto.getPinnedStackController();
                mDefaultPinnedStackBounds = WindowContainer.extractBounds(pinnedStackProto.getDefaultBounds());
                mPinnedStackMovementBounds = WindowContainer.extractBounds(pinnedStackProto.getMovementBounds());
            }
        }
        for (WindowState w : allWindows) {
            windowMap.put(w.getToken(), w);
        }
        for (int i = 0; i < state.getWindowsCount(); i++) {
            IdentifierProto identifierProto = state.getWindows(i);
            String hash_code = Integer.toHexString(identifierProto.getHashCode());
            mWindowStates.add(windowMap.get(hash_code));
        }
        mStableBounds = WindowContainer.extractBounds(state.getPolicy().getStableBounds());
        mInputMethodWindowAppToken = Integer.toHexString(state.getInputMethodWindow().getHashCode());
        mDisplayFrozen = state.getDisplayFrozen();
        mRotation = state.getRotation();
        mLastOrientation = state.getLastOrientation();
        AppTransitionProto appTransitionProto = state.getAppTransition();
        mAppTransitionState = appTransitionProto.getAppTransitionState().name();
        mLastTransition = appTransitionProto.getLastUsedAppTransition().name();
    }

    void getMatchingWindowTokens(final String windowName, List<String> tokenList) {
        tokenList.clear();

        for (WindowState ws : mWindowStates) {
            if (windowName.equals(ws.getName())) {
                tokenList.add(ws.getToken());
            }
        }
    }

    void getMatchingVisibleWindowState(final String windowName, List<WindowState> windowList) {
        windowList.clear();
        for (WindowState ws : mWindowStates) {
            if (ws.isShown() && windowName.equals(ws.getName())) {
                windowList.add(ws);
            }
        }
    }

    void getPrefixMatchingVisibleWindowState(final String windowName, List<WindowState> windowList) {
        windowList.clear();
        for (WindowState ws : mWindowStates) {
            if (ws.isShown() && ws.getName().startsWith(windowName)) {
                windowList.add(ws);
            }
        }
    }

    WindowState getWindowByPackageName(String packageName, int windowType) {
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

    void getWindowsByPackageName(String packageName, List<Integer> restrictToTypeList,
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

    void sortWindowsByLayer(List<WindowState> windows) {
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

    String getFocusedWindow() {
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

    /** Check if there exists a window record with matching windowName. */
    boolean containsWindow(String windowName) {
        for (WindowState window : mWindowStates) {
            if (window.getName().equals(windowName)) {
                return true;
            }
        }
        return false;
    }

    /** Check if at least one window which matches provided window name is visible. */
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


    int getStackPosition(int stackId) {
        for (int i = 0; i < mStacks.size(); i++) {
            if (stackId == mStacks.get(i).mStackId) {
                return i;
            }
        }
        return -1;
    }

    WindowState getInputMethodWindowState() {
        return getWindowStateForAppToken(mInputMethodWindowAppToken);
    }

    Rectangle getStableBounds() {
        return mStableBounds;
    }

    Rectangle getDefaultPinnedStackBounds() {
        return mDefaultPinnedStackBounds;
    }

    Rectangle getPinnedStackMomentBounds() {
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
    }

    static class WindowStack extends WindowContainer {

        int mStackId;
        ArrayList<WindowTask> mTasks = new ArrayList();
        boolean mWindowAnimationBackgroundSurfaceShowing;

        WindowStack(StackProto proto) {
            mStackId = proto.getId();
            mFullscreen = proto.getFillsParent();
            if (proto.hasBounds()) {
                mBounds = extractBounds(proto.getBounds());
            }
            for (int i = 0; i < proto.getTasksCount(); i++) {
                TaskProto taskProto = proto.getTasks(i);
                WindowTask task = new WindowTask(taskProto);
                mTasks.add(task);
                mSubWindows.addAll(task.getWindows());
            }
            mWindowAnimationBackgroundSurfaceShowing = proto.getAnimationBackgroundSurfaceIsDimming();
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

    static class WindowTask extends WindowContainer {

        int mTaskId;
        Rectangle mTempInsetBounds;
        List<String> mAppTokens = new ArrayList();

        WindowTask(TaskProto proto) {
            mTaskId = proto.getId();
            mFullscreen = proto.getFillsParent();
            if (proto.hasBounds()) {
                mBounds = extractBounds(proto.getBounds());
            }
            for (int i = 0; i < proto.getAppWindowTokensCount(); i++) {
                AppWindowTokenProto appWindowTokenProto = proto.getAppWindowTokens(i);
                mAppTokens.add(appWindowTokenProto.getName());
                WindowTokenProto windowTokenProto = appWindowTokenProto.getWindowToken();
                for (int j = 0; j < windowTokenProto.getWindowsCount(); j++) {
                    WindowStateProto windowProto = windowTokenProto.getWindows(j);
                    WindowState window = new WindowState(windowProto);
                    mSubWindows.add(window);
                    mSubWindows.addAll(window.getWindows());
                }
            }
            mTempInsetBounds = extractBounds(proto.getTempInsetBounds());
        }
    }

    static abstract class WindowContainer {

        protected boolean mFullscreen;
        protected Rectangle mBounds;
        protected List<WindowState> mSubWindows = new ArrayList<>();

        static Rectangle extractBounds(RectProto rectProto) {
            final int left = rectProto.getLeft();
            final int top = rectProto.getTop();
            final int right = rectProto.getRight();
            final int bottom = rectProto.getBottom();
            final Rectangle rect = new Rectangle(left, top, right - left, bottom - top);
            return rect;
        }

        Rectangle getBounds() {
            return mBounds;
        }

        boolean isFullscreen() {
            return mFullscreen;
        }

        List<WindowState> getWindows() {
            return mSubWindows;
        }
    }

    static class Display extends WindowContainer {

        private final int mDisplayId;
        private Rectangle mDisplayRect = new Rectangle();
        private Rectangle mAppRect = new Rectangle();
        private int mDpi;

        public Display(DisplayProto proto) {
            mDisplayId = proto.getId();
            for (int i = 0; i < proto.getAboveAppWindowsCount(); i++) {
                addWindowsFromTokenProto(proto.getAboveAppWindows(i));
            }
            for (int i = 0; i < proto.getBelowAppWindowsCount(); i++) {
                addWindowsFromTokenProto(proto.getBelowAppWindows(i));
            }
            for (int i = 0; i < proto.getImeWindowsCount(); i++) {
                addWindowsFromTokenProto(proto.getImeWindows(i));
            }
            mDpi = proto.getDpi();
            DisplayInfoProto infoProto = proto.getDisplayInfo();
            mDisplayRect.setBounds(0, 0, infoProto.getLogicalWidth(), infoProto.getLogicalHeight());
            mAppRect.setBounds(0, 0, infoProto.getAppWidth(), infoProto.getAppHeight());
        }

        private void addWindowsFromTokenProto(WindowTokenProto proto) {
            for (int j = 0; j < proto.getWindowsCount(); j++) {
                WindowStateProto windowProto = proto.getWindows(j);
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

        Rectangle getDisplayRect() {
            return mDisplayRect;
        }

        Rectangle getAppRect() {
            return mAppRect;
        }

        @Override
        public String toString() {
            return "Display #" + mDisplayId + ": mDisplayRect=" + mDisplayRect
                    + " mAppRect=" + mAppRect;
        }
    }

    public static class WindowState extends WindowContainer {
        private static final String TAG = "[WindowState] ";

        public static final int TYPE_WALLPAPER = 2013;

        private static final int WINDOW_TYPE_NORMAL   = 0;
        private static final int WINDOW_TYPE_STARTING = 1;
        private static final int WINDOW_TYPE_EXITING  = 2;
        private static final int WINDOW_TYPE_DEBUGGER = 3;

        private String mName;
        private final String mAppToken;
        private final int mWindowType;
        private int mType;
        private int mDisplayId;
        private int mStackId;
        private int mLayer;
        private boolean mShown;
        private Rectangle mContainingFrame = new Rectangle();
        private Rectangle mParentFrame = new Rectangle();
        private Rectangle mContentFrame = new Rectangle();
        private Rectangle mFrame = new Rectangle();
        private Rectangle mSurfaceInsets = new Rectangle();
        private Rectangle mContentInsets = new Rectangle();
        private Rectangle mGivenContentInsets = new Rectangle();
        private Rectangle mCrop = new Rectangle();

        WindowState (WindowStateProto proto) {
            IdentifierProto identifierProto = proto.getIdentifier();
            mName = identifierProto.getTitle();
            mAppToken = Integer.toHexString(identifierProto.getHashCode());
            mDisplayId = proto.getDisplayId();
            mStackId = proto.getStackId();
            mType = proto.getAttributes().getType();
            WindowStateAnimatorProto animatorProto = proto.getAnimator();
            if (animatorProto.hasSurface()) {
                WindowSurfaceControllerProto surfaceProto = animatorProto.getSurface();
                mShown = surfaceProto.getShown();
                mLayer = surfaceProto.getLayer();
            }
            mGivenContentInsets = extractBounds(proto.getGivenContentInsets());
            mFrame = extractBounds(proto.getFrame());
            mContainingFrame = extractBounds(proto.getContainingFrame());
            mParentFrame = extractBounds(proto.getParentFrame());
            mContentFrame = extractBounds(proto.getContentFrame());
            mContentInsets = extractBounds(proto.getContentInsets());
            mSurfaceInsets = extractBounds(proto.getSurfaceInsets());
            mCrop = extractBounds(animatorProto.getLastClipRect());
            if (mName.startsWith(STARTING_WINDOW_PREFIX)) {
                mWindowType = WINDOW_TYPE_STARTING;
                // Existing code depends on the prefix being removed
                mName = mName.substring(STARTING_WINDOW_PREFIX.length());
            } else if (proto.getAnimatingExit()) {
                mWindowType = WINDOW_TYPE_EXITING;
            } else if (mName.startsWith(DEBUGGER_WINDOW_PREFIX)) {
                mWindowType = WINDOW_TYPE_STARTING;
                mName = mName.substring(DEBUGGER_WINDOW_PREFIX.length());
            } else {
                mWindowType = 0;
            }
            for (int i = 0; i < proto.getChildWindowsCount(); i++) {
                WindowStateProto childProto = proto.getChildWindows(i);
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

        int getLayer() {
            return mLayer;
        }

        Rectangle getContainingFrame() {
            return mContainingFrame;
        }

        Rectangle getFrame() {
            return mFrame;
        }

        Rectangle getSurfaceInsets() {
            return mSurfaceInsets;
        }

        Rectangle getContentInsets() {
            return mContentInsets;
        }

        Rectangle getGivenContentInsets() {
            return mGivenContentInsets;
        }

        Rectangle getContentFrame() {
            return mContentFrame;
        }

        Rectangle getParentFrame() {
            return mParentFrame;
        }

        Rectangle getCrop() {
            return mCrop;
        }

        boolean isShown() {
            return mShown;
        }

        int getType() {
            return mType;
        }

        private static String getWindowTypeSuffix(int windowType) {
            switch (windowType) {
            case WINDOW_TYPE_STARTING: return " STARTING";
            case WINDOW_TYPE_EXITING: return " EXITING";
            case WINDOW_TYPE_DEBUGGER: return " DEBUGGER";
            default: break;
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
