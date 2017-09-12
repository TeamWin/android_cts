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
package android.server.am.displayservice;

import static junit.framework.Assert.assertTrue;

import android.support.test.InstrumentationRegistry;

import com.android.compatibility.common.util.SystemUtil;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DisplayHelper {
    private static final String VIRTUAL_DISPLAY_NAME = "CtsVirtualDisplay";
    private static final String VIRTUAL_DISPLAY_SERVICE =
            "android.server.am.displayservice/.VirtualDisplayService";
    private static final Pattern mDisplayDevicePattern = Pattern.compile(
            ".*DisplayDeviceInfo\\{\"([^\"]+)\":.*, state (\\S+),.*\\}.*");

    private boolean mCreated;

    public DisplayHelper() {
    }

    public void createAndWaitForDisplay(boolean external, boolean requestShowWhenLocked)
            {
        StringBuilder command =
                new StringBuilder("am startfgservice -n " + VIRTUAL_DISPLAY_SERVICE);
        command.append(" --es command create");
        if (external) {
            command.append(" --ez external_display true");
        }
        if (requestShowWhenLocked) {
            command.append(" --ez show_content_when_locked true");
        }
        executeShellCommand(command.toString());

        waitForDisplayState(false /* default */, true /* exists */, true /* on */);
        mCreated = true;
    }

    public void turnDisplayOff() {
        executeShellCommand(
                "am start-service -n " + VIRTUAL_DISPLAY_SERVICE + " --es command off");
        waitForDisplayState(false /* default */, true /* exists */, false /* on */);
    }

    public void turnDisplayOn() {
        executeShellCommand(
                "am start-service -n " + VIRTUAL_DISPLAY_SERVICE + " --es command on");
        waitForDisplayState(false /* default */, true /* exists */, true /* on */);
    }

    public void releaseDisplay() {
        if (mCreated) {
            executeShellCommand(
                    "am start-service -n " + VIRTUAL_DISPLAY_SERVICE + " --es command destroy");
            waitForDisplayState(false /* default */, false /* exists */, true /* on */);
        }
        mCreated = false;
    }

    public static void waitForDefaultDisplayState(boolean wantOn) {
        waitForDisplayState(true /* default */, true /* exists */, wantOn);
    }

    public static boolean getDefaultDisplayState() {
        return getDisplayState(true);
    }

    private static void waitForDisplayState(boolean defaultDisplay, boolean wantExists, boolean wantOn) {
        int tries = 0;
        boolean done = false;
        do {
            if (tries > 0) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    // Oh well
                }
            }

            Boolean state = getDisplayState(defaultDisplay);
            done = (!wantExists && state == null)
                    || (wantExists && state != null && state == wantOn);

            tries++;
        } while (tries < 10 && !done);

        assertTrue(done);
    }

    private static Boolean getDisplayState(boolean defaultDisplay) {
        String dump = executeShellCommand("dumpsys display");

        boolean displayExists = false;
        boolean displayOn = false;
        for (String line : dump.split("\\n")) {
            Matcher matcher = mDisplayDevicePattern.matcher(line);
            if (matcher.matches()) {
                if ((defaultDisplay && line.contains("FLAG_DEFAULT_DISPLAY"))
                        || (!defaultDisplay && VIRTUAL_DISPLAY_NAME.equals(matcher.group(1)))) {
                    return "ON".equals(matcher.group(2));
                }
            }
        }
        return null;
    }

    private static String executeShellCommand(String command) {
        try {
            return SystemUtil
                    .runShellCommand(InstrumentationRegistry.getInstrumentation(), command);
        } catch (IOException e) {
            //bubble it up
            throw new RuntimeException(e);
        }
    }
}
