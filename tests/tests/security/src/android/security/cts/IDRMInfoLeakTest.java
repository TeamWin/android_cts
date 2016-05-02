/*
 * Copyright (C) 2016 The Android Open Source Project
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

import junit.framework.TestCase;

public class IDRMInfoLeakTest extends TestCase {
    static {
        System.loadLibrary("ctssecurity_jni");
    }

    /*
    * Information Disclosure Vulnerability in Mediaserver @CVE-2016-2419
    */
    public void testIDRMInfoLeak() throws Exception {
        assertTrue("Device is vulnerable to CVE-2016-2419", doIDRMInfoLeakTest());
    }

    /**
     * CVE-2016-2419
     * Verifies the security ulnerability - information leak of IDrm when handling
     * GET_KEY_REQUEST IPC call is fixed.
     * Returns true if the patch is applied, false otherwise
     */
    private static native boolean doIDRMInfoLeakTest();
}