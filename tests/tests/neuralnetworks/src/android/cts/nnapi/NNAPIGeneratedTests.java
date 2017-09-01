/*
 * Copyright (C) 2017 The Android Open Source Project
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

package android.cts.nnapi;

import android.test.AndroidTestCase;
import android.util.Log;

public class NNAPIGeneratedTests extends NNAPITest {
    static {
        System.loadLibrary("nnapitest_jni");
    }

    native boolean avgPoolQuant8();
    public void testAvgPoolQuant8() {
        assertFalse(avgPoolQuant8());
    }

    native boolean conv1H3W2Same();
    public void testConv1H3W2Same() {
        assertFalse(conv1H3W2Same());
    }

    native boolean mobileNet();
    public void testMobileNet() {
        assertFalse(mobileNet());
    }
}
