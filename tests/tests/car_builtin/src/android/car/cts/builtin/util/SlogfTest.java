/*
 * Copyright (C) 2021 The Android Open Source Project
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
 * limitations under the License.
 */

package android.car.cts.builtin.util;

import static android.car.cts.builtin.util.SlogfTest.Level.ERROR;
import static android.car.cts.builtin.util.SlogfTest.Level.VERBOSE;

import android.car.builtin.util.Slogf;

import com.android.compatibility.common.util.SystemUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public final class SlogfTest {
    private static final String TAG = SlogfTest.class.getSimpleName();
    private static final int TIMEOUT_MS = 60_000;
    // wait time for waiting to make sure msg is not logged. Should not be a high value as tests
    // waits for this much time.
    private static final int NOT_LOGGED_WAIT_TIME_MS = 5_000;
    private static final String LOGCAT_LINE_FORMAT = "%s %s: %s";

    enum Level {
        VERBOSE("V"), ERROR("E");

        private String mValue;

        public String getValue() {
            return mValue;
        }

        Level(String v) {
            mValue = v;
        }
    }

    @Before
    public void setup() {
        setLogLevel(VERBOSE);
        clearLog();
    }

    @After
    public void reset() {
        setLogLevel(VERBOSE);
        clearLog();
    }

    @Test
    public void testV_msg1() {
        Slogf.v(TAG, "This is the message to be checked.");

        assertLogcatMessage(VERBOSE, "This is the message to be checked.");
    }

    @Test
    public void testV_msg2() {
        Slogf.v(TAG, "This is the message to be checked.",
                new Throwable("Exception message to be checked."));

        assertLogcatMessage(VERBOSE, "This is the message to be checked.");
        assertLogcatMessage(VERBOSE, "java.lang.Throwable: Exception message to be checked.");
    }

    @Test
    public void testV_noMsg() throws Exception {
        setLogLevel(ERROR);

        Slogf.v(TAG, "This message should not exists.");

        assertNoLogcatMessage(VERBOSE, "This message should not exists.");
    }

    private void clearLog() {
        LogcatHelper.clearLog();
    }

    private void setLogLevel(Level level) {
        SystemUtil.runShellCommand("setprop log.tag.SlogfTest " + level.getValue());
    }

    private void assertNoLogcatMessage(Level level, String msg) throws Exception {
        String match = String.format(LOGCAT_LINE_FORMAT, level.getValue(), TAG, msg);
        LogcatHelper.assertNoLogcatMessage(match, NOT_LOGGED_WAIT_TIME_MS);
    }

    private void assertLogcatMessage(Level level, String msg) {
        String match = String.format(LOGCAT_LINE_FORMAT, level.getValue(), TAG, msg);
        LogcatHelper.assertLogcatMessage(match, TIMEOUT_MS);
    }
}
