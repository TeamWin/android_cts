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

import com.google.common.collect.ImmutableSet;

public class TaggingSdk30MemtagTest extends TaggingBaseTest {
    protected static final String TEST_APK = "CtsHostsideTaggingSdk30MemtagApp.apk";
    protected static final String TEST_PKG = "android.cts.tagging.sdk30memtag";
    private static final String TEST_RUNNER = "androidx.test.runner.AndroidJUnitRunner";

    private static final long NATIVE_MEMTAG_ASYNC_CHANGE_ID = 135772972;
    private static final long NATIVE_MEMTAG_SYNC_CHANGE_ID = 177438394;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        installPackage(TEST_APK, true);
    }

    @Override
    protected void tearDown() throws Exception {
        uninstallPackage(TEST_PKG, true);
        super.tearDown();
    }

    public void testAppZygoteMemtagSyncService() throws Exception {
        if (!deviceSupportsMemoryTagging) {
            return;
        }
        runDeviceCompatTest(TEST_PKG, ".TaggingTest", "testAppZygoteMemtagSyncService",
                /*enabledChanges*/ ImmutableSet.of(),
                /*disabledChanges*/ ImmutableSet.of());
    }
}
