/*
 * Copyright 2018 The Android Open Source Project
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

package android.hardware.input.cts.tests;

import static org.junit.Assume.assumeTrue;

import android.hardware.cts.R;
import android.os.Build;
import android.os.VintfRuntimeInfo;
import android.text.TextUtils;
import android.util.Pair;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class SonyDualshock4TestCase extends InputTestCase {

    public SonyDualshock4TestCase() {
        super(R.raw.sony_dualshock4_register);
    }

    // Copied from cts/tests/tests/net/src/android/net/cts/ConnectivityManagerTest.java
    private static Pair<Integer, Integer> getVersionFromString(String version) {
        // Only gets major and minor number of the version string.
        final Pattern versionPattern = Pattern.compile("^(\\d+)(\\.(\\d+))?.*");
        final Matcher m = versionPattern.matcher(version);
        if (m.matches()) {
            final int major = Integer.parseInt(m.group(1));
            final int minor = TextUtils.isEmpty(m.group(3)) ? 0 : Integer.parseInt(m.group(3));
            return new Pair<>(major, minor);
        } else {
            return new Pair<>(0, 0);
        }
    }

    // Copied from cts/tests/tests/net/src/android/net/cts/ConnectivityManagerTest.java
    public static int compareMajorMinorVersion(final String s1, final String s2) {
        final Pair<Integer, Integer> v1 = getVersionFromString(s1);
        final Pair<Integer, Integer> v2 = getVersionFromString(s2);

        if (v1.first == v2.first) {
            return Integer.compare(v1.second, v2.second);
        } else {
            return Integer.compare(v1.first, v2.first);
        }
    }

    // This test requires updates to hid-sony.c that are only available for
    // kernels 3.18+. Skip this test for kernels older than 3.18 because it is
    // too difficult to backport these changes to those older kernels.
    private static boolean isDualshock4DriverSupportedByKernel() {
        final String kVersionString = VintfRuntimeInfo.getKernelVersion();
        return compareMajorMinorVersion(kVersionString, "3.18") >= 0;
    }

    @Test
    public void testAllKeys() {
        assumeTrue(isDualshock4DriverSupportedByKernel());
        testInputEvents(R.raw.sony_dualshock4_keyeventtests);
    }

    @Test
    public void testAllMotions() {
        assumeTrue(isDualshock4DriverSupportedByKernel());
        testInputEvents(R.raw.sony_dualshock4_motioneventtests);
    }
}
