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

package com.android.cts.tagging;

import android.compat.cts.CompatChangeGatingTestCase;

import com.google.common.collect.ImmutableSet;

public class TaggingDefaultTest extends CompatChangeGatingTestCase {

    protected static final String TEST_APK = "CtsHostsideTaggingNoneApp.apk";
    protected static final String TEST_PKG = "android.cts.tagging.none";

    private static final long NATIVE_HEAP_POINTER_TAGGING_CHANGE_ID = 135754954;
    private static final long NATIVE_MEMORY_TAGGING_CHANGE_ID = 135772972;

    private boolean supportsMemoryTagging;

    @Override
    protected void setUp() throws Exception {
        installPackage(TEST_APK, true);
        supportsMemoryTagging = !runCommand("grep 'Features.* mte' /proc/cpuinfo").isEmpty();
    }

    public void testHeapTaggingCompatFeatureEnabled() throws Exception {
        if (supportsMemoryTagging) {
            return;
        }
        runDeviceCompatTest(TEST_PKG, ".TaggingTest", "testHeapTaggingEnabled",
                /*enabledChanges*/ImmutableSet.of(NATIVE_HEAP_POINTER_TAGGING_CHANGE_ID),
                /*disabledChanges*/ ImmutableSet.of());
    }

    public void testHeapTaggingCompatFeatureDisabled() throws Exception {
        if (supportsMemoryTagging) {
            return;
        }
        runDeviceCompatTest(TEST_PKG, ".TaggingTest", "testHeapTaggingDisabled",
                /*enabledChanges*/ImmutableSet.of(),
                /*disabledChanges*/ ImmutableSet.of(NATIVE_HEAP_POINTER_TAGGING_CHANGE_ID));
    }

    public void testMemoryTagChecksCompatFeatureEnabled() throws Exception {
        if (!supportsMemoryTagging) {
            return;
        }
        runDeviceCompatTest(TEST_PKG, ".TaggingTest", "testMemoryTagChecksEnabled",
                /*enabledChanges*/ ImmutableSet.of(NATIVE_MEMORY_TAGGING_CHANGE_ID),
                /*disabledChanges*/ImmutableSet.of());
    }

    public void testMemoryTagChecksCompatFeatureDisabled() throws Exception {
        if (!supportsMemoryTagging) {
            return;
        }
        runDeviceCompatTest(TEST_PKG, ".TaggingTest", "testMemoryTagChecksDisabled",
                /*enabledChanges*/ImmutableSet.of(),
                /*disabledChanges*/ ImmutableSet.of(NATIVE_MEMORY_TAGGING_CHANGE_ID));
    }
}
