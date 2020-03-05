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

package com.android.cts.appdataisolation.appb;

import static com.android.cts.appdataisolation.common.FileUtils.assertDirDoesNotExist;
import static com.android.cts.appdataisolation.common.FileUtils.assertDirIsAccessible;
import static com.android.cts.appdataisolation.common.FileUtils.assertDirIsNotAccessible;
import static com.android.cts.appdataisolation.common.FileUtils.assertFileIsAccessible;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager.NameNotFoundException;

import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

@SmallTest
public class AppBTests {

    private static final String APPA_PKG = "com.android.cts.appdataisolation.appa";

    private Context mContext;

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();
    }

    @Test
    public void testCannotAccessAppADataDir() throws NameNotFoundException {
        ApplicationInfo applicationInfo = mContext.getPackageManager().getApplicationInfo(APPA_PKG,
                0);
        assertDirDoesNotExist(applicationInfo.dataDir);
        assertDirDoesNotExist(applicationInfo.deviceProtectedDataDir);
        assertDirDoesNotExist("/data/data/" + APPA_PKG);
        assertDirDoesNotExist("/data/misc/profiles/cur/0/" + APPA_PKG);
        assertDirIsNotAccessible("/data/misc/profiles/ref");
    }

    @Test
    public void testCanAccessAppADataDir() throws NameNotFoundException, IOException {
        ApplicationInfo applicationInfo = mContext.getPackageManager().getApplicationInfo(APPA_PKG,
                0);
        assertDirIsAccessible(applicationInfo.dataDir);
        assertDirIsAccessible(applicationInfo.deviceProtectedDataDir);
        assertDirIsAccessible("/data/data/" + APPA_PKG);
        assertFileIsAccessible("/data/misc/profiles/cur/0/" + APPA_PKG + "/primary.prof");
        assertDirIsNotAccessible("/data/misc/profiles/ref");
    }
}
