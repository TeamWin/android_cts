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
package android.server.am;

import static android.server.am.ComponentNameUtils.getActivityName;
import static android.server.am.StateLogger.logAlways;
import static android.server.am.displayservice.Components.VIRTUAL_DISPLAY_SERVICE;
import static android.server.am.displayservice.Components.VirtualDisplayService.COMMAND_CREATE;
import static android.server.am.displayservice.Components.VirtualDisplayService.COMMAND_DESTROY;
import static android.server.am.displayservice.Components.VirtualDisplayService.COMMAND_OFF;
import static android.server.am.displayservice.Components.VirtualDisplayService.COMMAND_ON;
import static android.server.am.displayservice.Components.VirtualDisplayService.EXTRA_COMMAND;
import static android.server.am.displayservice.Components.VirtualDisplayService.EXTRA_SHOW_CONTENT_WHEN_LOCKED;
import static android.server.am.displayservice.Components.VirtualDisplayService.VIRTUAL_DISPLAY_NAME;
import static android.support.test.InstrumentationRegistry.getInstrumentation;

import static org.junit.Assert.fail;

import android.os.SystemClock;
import android.support.annotation.Nullable;

import com.android.compatibility.common.util.SystemUtil;

import java.io.IOException;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Helper class to create virtual display with communicating with VirtualDisplayService in
 * CtsDisplayServiceApp.
 */
class VirtualDisplayHelper {
    private static final Pattern DISPLAY_DEVICE_PATTERN = Pattern.compile(
            ".*DisplayDeviceInfo\\{\"([^\"]+)\":.*, state (\\S+),.*\\}.*");

    private boolean mCreated;

    void createAndWaitForDisplay(boolean requestShowWhenLocked) {
        StringBuilder command = new StringBuilder("am startfgservice -n "
                + getActivityName(VIRTUAL_DISPLAY_SERVICE));
        command.append(" --es " + EXTRA_COMMAND + " " + COMMAND_CREATE);
        if (requestShowWhenLocked) {
            command.append(" --ez " + EXTRA_SHOW_CONTENT_WHEN_LOCKED + " true");
        }
        executeShellCommand(command.toString());

        waitForDisplayState(false /* default */, true /* on */);
        mCreated = true;
    }

    void turnDisplayOff() {
        executeShellCommand("am start-service -n " + getActivityName(VIRTUAL_DISPLAY_SERVICE)
                + " --es " + EXTRA_COMMAND + " " + COMMAND_OFF);
        waitForDisplayState(false /* default */, false /* on */);
    }

    void turnDisplayOn() {
        executeShellCommand("am start-service -n " + getActivityName(VIRTUAL_DISPLAY_SERVICE)
                        + " --es " + EXTRA_COMMAND + " " + COMMAND_ON);
        waitForDisplayState(false /* default */, true /* on */);
    }

    void releaseDisplay() {
        if (mCreated) {
            executeShellCommand("am start-service -n " + getActivityName(VIRTUAL_DISPLAY_SERVICE)
                            + " --es " + EXTRA_COMMAND + " " + COMMAND_DESTROY);
            waitForDisplayCondition(false /* defaultDisplay */, Objects::isNull,
                    "Waiting for virtual display destroy");
        }
        mCreated = false;
    }

    static void waitForDefaultDisplayState(boolean wantOn) {
        waitForDisplayState(true /* default */, wantOn);
    }

    private static void waitForDisplayState(boolean defaultDisplay, boolean wantOn) {
        waitForDisplayCondition(defaultDisplay, state -> state != null && state == wantOn,
                "Waiting for " + (defaultDisplay ? "default" : "virtual") + " display "
                        + (wantOn ? "on" : "off"));
    }

    private static void waitForDisplayCondition(boolean defaultDisplay,
            Predicate<Boolean> condition, String message) {
        for (int retry = 1; retry <= 10; retry++) {
            if (condition.test(getDisplayState(defaultDisplay))) {
                return;
            }
            logAlways(message + "... retry=" + retry);
            SystemClock.sleep(500);
        }
        fail(message + " failed");
    }

    @Nullable
    private static Boolean getDisplayState(boolean defaultDisplay) {
        final String dump = executeShellCommand("dumpsys display");
        final Predicate<Matcher> displayNameMatcher = defaultDisplay
                ? m -> m.group(0).contains("FLAG_DEFAULT_DISPLAY")
                : m -> m.group(1).equals(VIRTUAL_DISPLAY_NAME);
        for (final String line : dump.split("\\n")) {
            final Matcher matcher = DISPLAY_DEVICE_PATTERN.matcher(line);
            if (matcher.matches() && displayNameMatcher.test(matcher)) {
                return "ON".equals(matcher.group(2));
            }
        }
        return null;
    }

    private static String executeShellCommand(String command) {
        try {
            return SystemUtil.runShellCommand(getInstrumentation(), command);
        } catch (IOException e) {
            //bubble it up
            throw new RuntimeException(e);
        }
    }
}
