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

package android.appsearch.cts;

import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;

public abstract class AppSearchHostTestBase extends BaseHostJUnit4Test {
    protected static final String TARGET_APK_A = "CtsAppSearchHostTestHelperA.apk";
    protected static final String TARGET_PKG_A = "android.appsearch.app.a";
    protected static final String TEST_CLASS_A = TARGET_PKG_A + ".AppSearchDeviceTest";
    protected static final String TARGET_APK_B = "CtsAppSearchHostTestHelperB.apk";
    protected static final String TARGET_PKG_B = "android.appsearch.app.b";
    protected static final String TEST_CLASS_B = TARGET_PKG_B + ".AppSearchDeviceTest";

    protected static final long DEFAULT_INSTRUMENTATION_TIMEOUT_MS = 600_000; // 10min

    protected void runDeviceTestAsUserInPkgA(String testMethod, int userId) throws Exception {
        runDeviceTests(getDevice(), TARGET_PKG_A, TEST_CLASS_A, testMethod, userId,
                DEFAULT_INSTRUMENTATION_TIMEOUT_MS);
    }

    protected void runDeviceTestAsUserInPkgB(String testMethod, int userId) throws Exception {
        runDeviceTests(getDevice(), TARGET_PKG_B, TEST_CLASS_B, testMethod, userId,
                DEFAULT_INSTRUMENTATION_TIMEOUT_MS);
    }
}
