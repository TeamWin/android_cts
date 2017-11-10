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

import static android.server.am.StateLogger.log;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class KeyguardTestBase extends ActivityManagerTestBase {

    protected void assertOnDismissSucceededInLogcat(String logSeparator) throws Exception {
        assertInLogcat("KeyguardDismissLoggerCallback", "onDismissSucceeded", logSeparator);
    }

    protected void assertOnDismissCancelledInLogcat(String logSeparator) throws Exception {
        assertInLogcat("KeyguardDismissLoggerCallback", "onDismissCancelled", logSeparator);
    }

    protected void assertOnDismissErrorInLogcat(String logSeparator) throws Exception {
        assertInLogcat("KeyguardDismissLoggerCallback", "onDismissError", logSeparator);
    }

    private void assertInLogcat(String activityName, String entry, String logSeparator)
            throws Exception {
        final Pattern pattern = Pattern.compile("(.+)" + entry);
        int tries = 0;
        while (tries < 5) {
            final String[] lines = getDeviceLogsForComponent(activityName, logSeparator);
            log("Looking at logcat");
            for (int i = lines.length - 1; i >= 0; i--) {
                final String line = lines[i].trim();
                log(line);
                Matcher matcher = pattern.matcher(line);
                if (matcher.matches()) {
                    return;
                }
            }
            tries++;
            Thread.sleep(500);
        }
        fail("Not in logcat: " + entry);
    }
}
