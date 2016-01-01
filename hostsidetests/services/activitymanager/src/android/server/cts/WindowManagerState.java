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

import com.android.ddmlib.Log.LogLevel;
import com.android.tradefed.device.CollectingOutputReceiver;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;

import java.lang.String;
import java.util.ArrayList;
import java.util.List;

import java.util.regex.Pattern;
import java.util.regex.Matcher;

class WindowManagerState {
    private static final String DUMPSYS_WINDOWS_VISIBLE_APPS = "dumpsys window visible-apps";

    private final Pattern mWindowPattern =
            Pattern.compile("Window #(\\d+) Window\\{(.+) u(\\d+) (.+)\\}\\:");
    private final Pattern mStartingWindowPattern =
            Pattern.compile("Window #(\\d+) Window\\{(.+) u(\\d+) Starting (.+)\\}\\:");
    private final Pattern mExitingWindowPattern =
            Pattern.compile("Window #(\\d+) Window\\{(.+) u(\\d+) (.+) EXITING\\}\\:");

    private final Pattern mFocusedWindowPattern =
            Pattern.compile("mCurrentFocus=Window\\{(.+) u(\\d+) (\\S+)\\}");
    private final Pattern mFocusedAppPattern =
            Pattern.compile("mFocusedApp=AppWindowToken\\{(.+) token=Token\\{(.+) "
                    + "ActivityRecord\\{(.+) u(\\d+) (\\S+) (\\S+)");

    // Windows in z-order with the top most at the front of the list.
    private List<String> mWindows = new ArrayList<>();
    private String mFocusedWindow = null;
    private String mFocusedApp = null;

    void processVisibleAppWindows(ITestDevice device) throws DeviceNotAvailableException {
        // It is possible the system is in the middle of transition to the right state when we get
        // the dump. We try a few times to get the information we need before giving up.
        int retriesLeft = 3;
        boolean retry = false;
        String dump = null;

        do {
            if (retry) {
                CLog.logAndDisplay(LogLevel.INFO, "***Incomplete WM state. Retrying...");
                // Wait half a second between retries for window manager to finish transitioning...
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    CLog.logAndDisplay(LogLevel.INFO, e.toString());
                    // Well I guess we are not waiting...
                }
            }

            final CollectingOutputReceiver outputReceiver = new CollectingOutputReceiver();
            device.executeShellCommand(DUMPSYS_WINDOWS_VISIBLE_APPS, outputReceiver);
            dump = outputReceiver.getOutput();
            parseSysDump(dump);

            retry = mWindows.isEmpty() || mFocusedWindow == null || mFocusedApp == null;
        } while (retry && retriesLeft-- > 0);

        if (retry) {
            CLog.logAndDisplay(LogLevel.INFO, dump);
        }

        if (mWindows.isEmpty()) {
            CLog.logAndDisplay(LogLevel.INFO, "No Windows found...");
        }
        if (mFocusedWindow == null) {
            CLog.logAndDisplay(LogLevel.INFO, "No Focused Window...");
        }
        if (mFocusedApp == null) {
            CLog.logAndDisplay(LogLevel.INFO, "No Focused App...");
        }
    }

    private void parseSysDump(String sysDump) {
        mWindows.clear();
        mFocusedWindow = null;
        mFocusedApp = null;

        for (String line : sysDump.split("\\n")) {
            line = line.trim();

            Matcher matcher = mWindowPattern.matcher(line);
            if (matcher.matches()) {
                CLog.logAndDisplay(LogLevel.INFO, line);
                final String window = matcher.group(4);

                if (mWindows.isEmpty()) {
                    // This is the front window. Check to see if we are in the middle of
                    // transitioning. If we are, we want to skip dumping until window manager is
                    // done transitioning the top window.
                    matcher = mStartingWindowPattern.matcher(line);
                    if (matcher.matches()) {
                        CLog.logAndDisplay(LogLevel.INFO,
                                "Skipping dump due to starting window transition...");
                        return;
                    }

                    matcher = mExitingWindowPattern.matcher(line);
                    if (matcher.matches()) {
                        CLog.logAndDisplay(LogLevel.INFO,
                                "Skipping dump due to exiting window transition...");
                        return;
                    }
                }

                CLog.logAndDisplay(LogLevel.INFO, window);
                mWindows.add(window);
                continue;
            }

            matcher = mFocusedWindowPattern.matcher(line);
            if (matcher.matches()) {
                CLog.logAndDisplay(LogLevel.INFO, line);
                final String focusedWindow = matcher.group(3);
                CLog.logAndDisplay(LogLevel.INFO, focusedWindow);
                mFocusedWindow = focusedWindow;
                continue;
            }

            matcher = mFocusedAppPattern.matcher(line);
            if (matcher.matches()) {
                CLog.logAndDisplay(LogLevel.INFO, line);
                final String focusedApp = matcher.group(5);
                CLog.logAndDisplay(LogLevel.INFO, focusedApp);
                mFocusedApp = focusedApp;
                continue;
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
}
