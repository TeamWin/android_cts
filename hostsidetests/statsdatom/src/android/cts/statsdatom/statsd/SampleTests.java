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

package android.cts.statsdatom.statsd;

import static com.google.common.truth.Truth.assertThat;

import android.cts.statsdatom.lib.TestLibrary;
import com.android.tradefed.testtype.DeviceTestCase;

public final class SampleTests extends DeviceTestCase {
    public void testFibonacciImplementation() throws Exception {
        assertThat(TestLibrary.fib(1)).isEqualTo(1);
        assertThat(TestLibrary.fib(2)).isEqualTo(1);
        assertThat(TestLibrary.fib(3)).isEqualTo(2);
        assertThat(TestLibrary.fib(4)).isEqualTo(3);
        assertThat(TestLibrary.fib(5)).isEqualTo(5);
    }
}
