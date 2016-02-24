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

public class MediaCodecListTest extends TestCase {

    static {
        System.loadLibrary("ctssecurity_jni");
    }

    public void testMediaCodecList() throws Exception {
        assertTrue("Device is vulnerable to CVE-2015-6620. "
                        + "Please apply the security patch at "
                        + "https://android.googlesource.com/"
                        + "platform%2Fframeworks%2Fav/"
                        + "+/77c185d5499d6174e7a97b3e1512994d3a803151",
                doCodecInfoTest());
    }

    /**
     * ANDROID-24445127 / CVE-2015-6620
     *
     * Returns true if the device is patched against the getCodecInfo().
     *
     * More information on this vulnreability is available at
     * https://android.googlesource.com/platform%2Fframeworks%2Fav
     * /a+/77c185d5499d6174e7a97b3e1512994d3a803151/
     */
    private static native boolean doCodecInfoTest();
}
