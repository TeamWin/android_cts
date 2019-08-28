/**
 * Copyright (C) 2019 The Android Open Source Project
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

import android.platform.test.annotations.SecurityTest;

@SecurityTest
public class Poc19_02 extends SecurityTestCase {

    /**
     * b/70857947
     */
    @SecurityTest(minPatchLevel = "2019-02")
    public void testPocCVE_2018_6367() throws Exception {
        AdbUtils.runPocAssertNoCrashes("CVE-2018-6267", getDevice(), "mediaserver");
    }

    /**
     * b/80198474
     */
    @SecurityTest(minPatchLevel = "2019-02")
    public void testPocCVE_2018_6271() throws Exception {
        AdbUtils.runPocAssertNoCrashes("CVE-2018-6271", getDevice(), "mediaserver");
    }
}
