/*
 * Copyright (C) 2022 The Android Open Source Project
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
public class Bug_209607944 extends SecurityTestCase {
    private static final String TEST_PKG = "android.security.cts.BUG_209607944";
    private static final String TEST_APP = "BUG-209607944.apk";
    private static final String TARGET_PERMISSION = "android.permission.CALL_PHONE";

    /**
     * b/209607944
     * Vulnerability Behaviour: obtain dangerous system permissions silently.
     */
    @Test
    @AsbSecurityTest(cveBugId = 209607944)
    public void testBug_209607944() throws Exception {
        installPackage(TEST_APP);
        /* Ensure the permission is revoked before test */
        AdbUtils.runCommandLine("pm revoke " + TEST_PKG + " " + TARGET_PERMISSION, getDevice());
        /* Launch the test app, and it would try to request the permission */
        AdbUtils.runCommandLine("am start -W -n " + TEST_PKG + "/.MainActivity", getDevice());
        /* Without user interaction, it shouldn't grant the target permission */
        final String grantState = TARGET_PERMISSION + ": granted=false";
        final String dump = AdbUtils.runCommandLine("dumpsys package " + TEST_PKG, getDevice());
        assertTrue(dump.contains(grantState));
    }
}
