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

package android.security.cts;

import android.platform.test.annotations.SecurityTest;
import java.util.concurrent.TimeUnit;


@SecurityTest
public class Poc17_07 extends SecurityTestCase {

    /**
     * b/35443725
     **/
    @SecurityTest
    public void testPocCVE_2016_2109() throws Exception {
      assertFalse("Overallocation detected!",
          AdbUtils.runPocCheckExitCode("CVE-2016-2109",
            getDevice(), 60));
    }

    /**
     * b/35467458
     */
    @SecurityTest
    public void testPocCVE_2017_0698() throws Exception {
      assertFalse("VULNERABLE EXIT CODE FOUND", AdbUtils.runPocCheckExitCode("CVE-2017-0698",
                  getDevice(), 60));
    }
}