/*
 * Copyright (C) 2020 The Android Open Source Project
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

import static com.android.sts.common.NativePocStatusAsserter.assertNotVulnerableExitCode;

import static org.junit.Assume.assumeNoException;

import android.platform.test.annotations.AsbSecurityTest;

import com.android.sts.common.NativePoc;
import com.android.sts.common.tradefed.testtype.NonRootSecurityTestCase;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DeviceJUnit4ClassRunner.class)
public class CVE_2019_9247 extends NonRootSecurityTestCase {

    @Test
    @AsbSecurityTest(cveBugId = 120426166)
    public void testPocCVE_2019_9247() throws Exception {
        try {
            NativePoc.builder()
                    .pocName("CVE-2019-9247")
                    .asserter(assertNotVulnerableExitCode())
                    .build()
                    .run(this);
        } catch (Exception e) {
            assumeNoException(e);
        }
    }
}
