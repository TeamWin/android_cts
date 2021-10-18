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

package android.security.cts;

import static org.junit.Assert.assertTrue;
import android.platform.test.annotations.AsbSecurityTest;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DeviceJUnit4ClassRunner.class)
public class CVE_2021_0922 extends SecurityTestCase {

    /**
     * b/195630721
     */
    @AsbSecurityTest(cveBugId = 195630721)
    @Test
    public void testPocCVE_2021_0922() throws Exception {
        String packageName = "com.android.managedprovisioning";
        String queryStr = "dumpsys package " + packageName;
        String permissions = AdbUtils.runCommandLine(queryStr, getDevice());

        // MANAGE_APP_OPS_MODES permission must be enforced for
        // package com.android.managedprovisioning
        assertTrue(permissions.contains("android.permission.MANAGE_APP_OPS_MODES"));
    }
}
