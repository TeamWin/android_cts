/**
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

import android.platform.test.annotations.SecurityTest;

import org.junit.Test;
import org.junit.runner.RunWith;

import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import static org.junit.Assume.assumeTrue;
import static org.junit.Assert.*;

@RunWith(DeviceJUnit4ClassRunner.class)
public class CVE_2020_0462 extends SecurityTestCase {

    /**
     * b/169161709
     */
    @Test
    @SecurityTest(minPatchLevel = "2020-12")
    public void testPocCVE_2020_0462() throws Exception {
        assumeTrue(containsDriver(getDevice(),
                "/sys/devices/system/cpu/vulnerabilities/spec_store_bypass"));
        String spec_store_bypass = AdbUtils.runCommandLine(
                "cat /sys/devices/system/cpu/vulnerabilities/spec_store_bypass", getDevice());
        assertFalse(spec_store_bypass.startsWith("Vulnerable"));
        assertTrue(spec_store_bypass.startsWith("Not affected") ||
                spec_store_bypass.startsWith("Mitigation"));
    }
}
