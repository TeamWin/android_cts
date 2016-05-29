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
 * limitations under the License.
 */

package com.android.compatibility.common.tradefed.result;

import com.android.ddmlib.Log;
import com.android.ddmlib.Log.LogLevel;
import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.config.OptionCopier;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.IShardableListener;
import com.android.tradefed.result.StubTestInvocationListener;
import com.android.tradefed.util.TimeUtil;

import java.util.Map;

/**
 * Write test progress to the test console.
 */
public class ConsoleReporter extends StubTestInvocationListener implements IShardableListener {

    private static final String UNKNOWN_DEVICE = "unknown_device";

    @Option(name = "quiet-output", description = "Mute display of test results.")
    private boolean mQuietOutput = false;

    private String mDeviceSerial = UNKNOWN_DEVICE;
    private boolean mTestFailed;
    private String mModuleId;

    /**
     * {@inheritDoc}
     */
    @Override
    public void invocationStarted(IBuildInfo buildInfo) {
        if (buildInfo == null) {
            CLog.w("buildInfo should not be null");
            return;
        }

        mDeviceSerial = buildInfo.getDeviceSerial().replace("%", "%%");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void testRunStarted(String id, int numTests) {
        mModuleId = id;
        log("Starting %s with %d test%s", id, numTests, (numTests > 1) ? "s" : "");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void testStarted(TestIdentifier test) {
        mTestFailed = false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void testFailed(TestIdentifier test, String trace) {
        log("%s fail: %s", test, trace);
        mTestFailed = true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void testIgnored(TestIdentifier test) {
        log("%s ignore", test);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void testAssumptionFailure(TestIdentifier test, String trace) {
        log("%s failed assumption: %s", test, trace);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void testEnded(TestIdentifier test, Map<String, String> testMetrics) {
        if (!mTestFailed) {
            log("%s pass", test);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void testRunFailed(String errorMessage) {
        log(errorMessage);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void testRunStopped(long elapsedTime) {
        log("%s stopped (%s)", mModuleId, TimeUtil.formatElapsedTime(elapsedTime));
    }

    /**
     * Print out to the console or log silently when mQuietOutput is true.
     */
    private void log(String format, Object... args) {
        // Escape any "%" signs in the device serial.
        format = String.format("[%s] %s", mDeviceSerial, format);

        if (mQuietOutput) {
            CLog.i(format, args);
        } else {
            CLog.logAndDisplay(LogLevel.INFO, format, args);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IShardableListener clone() {
        ConsoleReporter clone = new ConsoleReporter();
        OptionCopier.copyOptionsNoThrow(this, clone);
        return clone;
    }
}
