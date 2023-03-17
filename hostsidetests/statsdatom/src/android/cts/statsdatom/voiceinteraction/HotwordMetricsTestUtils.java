/*
 * Copyright (C) 2023 The Android Open Source Project
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

package android.cts.statsdatom.voiceinteraction;

import static com.google.common.truth.Truth.assertWithMessage;

import com.android.tradefed.device.ITestDevice;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * An util class that provides methods for Hotword metrics logging test.
 */
public class HotwordMetricsTestUtils {

    public static final String TEST_PKG = "android.voiceinteraction.cts";
    public static final String TEST_APK = "CtsVoiceInteractionTestCases.apk";
    public static final String TEST_CLASS =
            "android.voiceinteraction.cts.HotwordDetectionServiceBasicTest";
    // HotwordDetectionServiceBasicTest usually takes around 15 secs to complete the test, we need
    // to wait for the test and logging behavior to complete, so the test here uses a longer
    // duration to avoid test flaky.
    public static final long STATSD_LOG_DEBOUNCE_MS = 25_000;

    public static int getTestAppUid(ITestDevice device) throws Exception {
        final int currentUser = device.getCurrentUser();
        final String uidLine = device.executeShellCommand(
                "cmd package list packages -U --user " + currentUser + " " + TEST_PKG);
        final Pattern pattern = Pattern.compile("package:" + TEST_PKG + " uid:(\\d+)");
        final Matcher matcher = pattern.matcher(uidLine);
        assertWithMessage("Pkg not found: " + TEST_PKG).that(matcher.find()).isTrue();
        return Integer.parseInt(matcher.group(1));
    }
}
