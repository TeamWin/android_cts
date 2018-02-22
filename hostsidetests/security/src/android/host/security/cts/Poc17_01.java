/**
 * Copyright (C) 2018 The Android Open Source Project
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
package android.host.security.cts;

import com.android.cts.util.SecurityTest;

@SecurityTest
public class Poc17_01 extends SecurityTestCase {
    /**
     *  b/32255299
     */
    @SecurityTest
    public void testPocCVE_2017_0386() throws Exception {
        AdbUtils.runCommandLine("logcat -c" , getDevice());
        AdbUtils.runPoc("CVE-2017-0386", getDevice(), 60);

        String logcat = AdbUtils.runCommandLine("logcat -d", getDevice());
        assertMatches("[\\s\\n\\S]*No Integer overflow[\\s\\n\\S]*", logcat);
    }
}
