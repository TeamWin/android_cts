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

public class UsbDebuggingAuthWithoutUnlockTest extends TestCase {
    public void testDebugAuthWithoutUnlock() throws ClassNotFoundException {
        Class windowManager = Class.forName("com.android.internal.policy.impl" +
                                            ".PhoneWindowManager");
        try {
            windowManager.getDeclaredField("mAppsToBeHidden");
        } catch (NoSuchFieldException nsfe) {
            fail("Device is likely vulnerable to Bug - 13225149. For more information, see " +
                 "https://android.googlesource.com/platform/" +
                 "frameworks/base/+/0392442%5E%21/#F0");
        }
    }
}
