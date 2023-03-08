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

package android.cts.gwp_asan;

import android.app.Activity;
import android.app.Application;
import android.util.Log;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

public class Utils {
    static {
        System.loadLibrary("gwp_asan_cts_library_jni");
    }

    public static final int TEST_SUCCESS = Activity.RESULT_FIRST_USER + 100;
    public static final int TEST_FAILURE = Activity.RESULT_FIRST_USER + 101;

    public static final int SERVICE_IS_GWP_ASAN_ENABLED = 42;
    public static final int SERVICE_IS_GWP_ASAN_DISABLED = 43;

    // Check that GWP-ASan is enabled by allocating a whole bunch of heap
    // pointers and making sure one of them is a GWP-ASan allocation (by
    // comparing with /proc/self/maps).
    public static native boolean isGwpAsanEnabled();
    // Check that GWP-ASan is disabled by ensuring there's no GWP-ASan mappings
    // in /proc/self/maps.
    public static boolean isGwpAsanDisabled() throws IOException {
        try (FileReader fr = new FileReader("/proc/self/maps");
                BufferedReader reader = new BufferedReader(fr)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains("GWP-ASan Guard Page")) {
                    Log.e(Application.getProcessName(),
                            "Line contained guard page: " + line);
                    return false;
                }
            }
        }
        return true;
    }
}
