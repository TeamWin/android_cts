/*
 * Copyright (C) 2015 The Android Open Source Project
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

package android.security.cts;

import com.android.compatibility.common.util.PropertyUtil;

import android.platform.test.annotations.SecurityTest;
import android.test.AndroidTestCase;
import junit.framework.TestCase;

import android.content.Context;
import android.os.Build;
import android.util.Log;
import java.io.BufferedReader;
import java.io.FileReader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@SecurityTest
public class EncryptionTest extends AndroidTestCase {

    static {
        System.loadLibrary("ctssecurity_jni");
    }

    private static final int MIN_API_LEVEL = 23;

    // First API level where there are no speed exemptions.
    private static final int MIN_ALL_SPEEDS_API_LEVEL = Build.VERSION_CODES.Q;

    private static final String TAG = "EncryptionTest";

    private static native boolean deviceIsEncrypted();

    private static native boolean aesIsFast();

    private boolean isRequired() {
        // Optional before MIN_API_LEVEL
        return PropertyUtil.getFirstApiLevel() >= MIN_API_LEVEL;
    }

    private boolean isEligibleForPerformanceExemption() {
        return PropertyUtil.getFirstApiLevel() < MIN_ALL_SPEEDS_API_LEVEL;
    }

    // Note: aesIsFast() takes ~2 second to run, so it's worth rearranging
    //     test logic to delay calling this.
    private boolean isExemptByPerformance() {
        // In older API levels, we grant an exemption if AES is not fast enough.
        return (isEligibleForPerformanceExemption() && !aesIsFast());
    }

    public void testEncryption() throws Exception {
        if (!isRequired() || deviceIsEncrypted()) {
            return;
        }

        // Required if performance is sufficient or on a Q+ build.
        assertTrue("Device encryption is required", isExemptByPerformance());

        // TODO(b/111311698): If we're able to determine if the hardware
        //     has AES instructions, confirm that AES, and only AES,
        //     is in use.  If the hardware does not have AES instructions,
        //     confirm that either AES or Adiantum is in use.
    }
}
