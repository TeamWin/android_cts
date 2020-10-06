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

package com.android.bionic_app;

import junit.framework.TestCase;

import com.android.compatibility.common.util.CpuFeatures;

public class BionicAppTest extends TestCase {
  static {
    System.loadLibrary("bionic_app_jni");
  }

  private native String progname();

  public void test__progname() {
    if (CpuFeatures.isNativeBridgedCpu()) {
        // Android 11 is lacking r.android.com/1446695 to support
        // conventional __progname overriding for native bridge.
        // We are going to support this in the next Android versions (b/167968941).
        return;
    }
    // https://issuetracker.google.com/152893281
    assertEquals("com.android.bionic_app", progname());
  }
}
