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

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager.NameNotFoundException;

import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.cts.appdataisolation.common.FileUtils;

import org.junit.Before;
import org.junit.Test;

@SmallTest
public class AppBTests {

    private static final String APPA_PKG = "com.android.cts.appdataisolation.appa";

    private static final String JAVA_FILE_PERMISSION_DENIED_MSG =
            "open failed: EACCES (Permission denied)";
    private static final String JAVA_FILE_NOT_FOUND_MSG =
            "open failed: ENOENT (No such file or directory)";

    private Context mContext;

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();
    }

    @Test
    public void testCannotAccessAppADataDir() throws NameNotFoundException {
        ApplicationInfo applicationInfo = mContext.getPackageManager().getApplicationInfo(APPA_PKG,
                0);
        FileUtils.assertDirDoesNotExist(applicationInfo.dataDir);
        FileUtils.assertDirDoesNotExist(applicationInfo.deviceProtectedDataDir);
        FileUtils.assertDirDoesNotExist("/data/data/" + APPA_PKG);
        FileUtils.assertDirDoesNotExist("/data/misc/profiles/cur/0/" + APPA_PKG);
        FileUtils.assertDirIsNotAccessible("/data/misc/profiles/ref");
    }
}
