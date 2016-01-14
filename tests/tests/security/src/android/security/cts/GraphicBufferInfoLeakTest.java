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

public class GraphicBufferInfoLeakTest extends TestCase {

    static {
        System.loadLibrary("ctssecurity_jni");
    }

    /**
     * Check that IGraphicBufferConsumer::attachBuffer does not leak info in error case
     */
    public void test_attachBufferInfoLeak() throws Exception {
        int slot = native_test_attachBufferInfoLeak();
        assertTrue(String.format("Leaked slot 0x%08X", slot), slot == -1);
    }

    /**
     * Check that IGraphicBufferProducer::queueBuffer does not leak info in error case
     */
    public void test_queueBufferInfoLeak() throws Exception {
        int data = native_test_queueBufferInfoLeak();
        assertTrue(String.format("Leaked buffer data 0x%08X", data), data == 0);
    }

    private static native int native_test_attachBufferInfoLeak();
    private static native int native_test_queueBufferInfoLeak();
}
