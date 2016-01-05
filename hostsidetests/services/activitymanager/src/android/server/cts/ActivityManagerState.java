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

import java.lang.Integer;
import java.lang.String;
import java.util.ArrayList;
import java.util.List;

import java.util.regex.Pattern;
import java.util.regex.Matcher;

class ActivityManagerState {
    private static final String DUMPSYS_ACTIVITY_ACTIVITIES = "dumpsys activity activities";

    private final Pattern mStackIdPattern = Pattern.compile("Stack #(\\d+)\\:");
    private final Pattern mFocusedActivityPattern =
            Pattern.compile("mFocusedActivity\\: ActivityRecord\\{(.+) u(\\d+) (\\S+) (\\S+)\\}");
    private final Pattern mFocusedStackPattern =
            Pattern.compile("mFocusedStack=ActivityStack\\{(.+) stackId=(\\d+), (.+)");
    private final Pattern mResumedActivityPattern =
            Pattern.compile("mResumedActivity\\: ActivityRecord\\{(.+) u(\\d+) (\\S+) (\\S+)\\}");

    // Stack ids in z-order with the top most at the front of the list.
    private final List<Integer> mStackIds = new ArrayList<>();
    private int mFocusedStackId = -1;
    private String mFocusedActivityRecord = null;
    private final List<String> mResumedActivities = new ArrayList();

    void processActivities(ITestDevice device) throws DeviceNotAvailableException {
        // It is possible the system is in the middle of transition to the right state when we get
        // the dump. We try a few times to get the information we need before giving up.
        int retriesLeft = 3;
        boolean retry = false;
        String dump = null;

        do {
            if (retry) {
                CLog.logAndDisplay(LogLevel.INFO, "***Incomplete AM state. Retrying...");
                // Wait half a second between retries for activity manager to finish transitioning.
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    CLog.logAndDisplay(LogLevel.INFO, e.toString());
                    // Well I guess we are not waiting...
                }
            }

            final CollectingOutputReceiver outputReceiver = new CollectingOutputReceiver();
            device.executeShellCommand(DUMPSYS_ACTIVITY_ACTIVITIES, outputReceiver);
            dump = outputReceiver.getOutput();
            parseSysDump(dump);

            retry = mStackIds.isEmpty() || mFocusedStackId == -1 || mFocusedActivityRecord == null
                    || mResumedActivities.isEmpty();
        } while (retry && retriesLeft-- > 0);

        if (retry) {
            CLog.logAndDisplay(LogLevel.INFO, dump);
        }

        if (mStackIds.isEmpty()) {
            CLog.logAndDisplay(LogLevel.INFO, "No stacks found...");
        }
        if (mFocusedStackId == -1) {
            CLog.logAndDisplay(LogLevel.INFO, "No focused stack found...");
        }
        if (mFocusedActivityRecord == null) {
            CLog.logAndDisplay(LogLevel.INFO, "No focused activity found...");
        }
        if (mResumedActivities.isEmpty()) {
            CLog.logAndDisplay(LogLevel.INFO, "No resumed activities found...");
        }
    }

    private void parseSysDump(String sysDump) {
        mStackIds.clear();
        mFocusedStackId = -1;
        mFocusedActivityRecord = null;
        mResumedActivities.clear();

        for (String line : sysDump.split("\\n")) {
            line = line.trim();

            Matcher matcher = mStackIdPattern.matcher(line);
            if (matcher.matches()) {
                CLog.logAndDisplay(LogLevel.INFO, line);
                final String stackId = matcher.group(1);
                CLog.logAndDisplay(LogLevel.INFO, stackId);
                mStackIds.add(Integer.parseInt(stackId));
                continue;
            }

            matcher = mFocusedStackPattern.matcher(line);
            if (matcher.matches()) {
                CLog.logAndDisplay(LogLevel.INFO, line);
                final String stackId = matcher.group(2);
                CLog.logAndDisplay(LogLevel.INFO, stackId);
                mFocusedStackId = Integer.parseInt(stackId);
                continue;
            }

            matcher = mFocusedActivityPattern.matcher(line);
            if (matcher.matches()) {
                CLog.logAndDisplay(LogLevel.INFO, line);
                mFocusedActivityRecord = matcher.group(3);
                CLog.logAndDisplay(LogLevel.INFO, mFocusedActivityRecord);
                continue;
            }

            matcher = mResumedActivityPattern.matcher(line);
            if (matcher.matches()) {
                CLog.logAndDisplay(LogLevel.INFO, line);
                final String resumedActivity = matcher.group(3);
                CLog.logAndDisplay(LogLevel.INFO, resumedActivity);
                mResumedActivities.add(resumedActivity);
                continue;
            }
        }
    }

    int getFrontStackId() {
        return mStackIds.get(0);
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
        return mStackIds.contains(stackId);
    }

    int getStackCount() {
        return mStackIds.size();
    }
}
