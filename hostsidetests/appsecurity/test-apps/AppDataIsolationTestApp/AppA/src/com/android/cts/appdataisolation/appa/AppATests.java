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

package com.android.cts.appdataisolation.appa;

import android.content.Context;

import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.cts.appdataisolation.common.FileUtils;

import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
/*
 * This class is a helper class for AppDataIsolationTests to assert and check data stored in app.
 */
@SmallTest
public class AppATests {

    private final static String CE_DATA_FILE_NAME = "ce_data_file";
    private final static String DE_DATA_FILE_NAME = "de_data_file";

    private static final String JAVA_FILE_PERMISSION_DENIED_MSG =
            "open failed: EACCES (Permission denied)";
    private static final String JAVA_FILE_NOT_FOUND_MSG =
            "open failed: ENOENT (No such file or directory)";

    private Context mContext;
    private String mCePath;
    private String mDePath;

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();
        mCePath = mContext.getApplicationInfo().dataDir;
        mDePath = mContext.getApplicationInfo().deviceProtectedDataDir;
    }

    @Test
    public void testCreateCeDeAppData() throws IOException {
        FileUtils.assertFileDoesNotExist(mCePath, CE_DATA_FILE_NAME);
        FileUtils.assertFileDoesNotExist(mCePath, DE_DATA_FILE_NAME);
        FileUtils.assertFileDoesNotExist(mDePath, CE_DATA_FILE_NAME);
        FileUtils.assertFileDoesNotExist(mDePath, DE_DATA_FILE_NAME);

        FileUtils.touchFile(mCePath, CE_DATA_FILE_NAME);
        FileUtils.touchFile(mDePath, DE_DATA_FILE_NAME);

        FileUtils.assertFileExists(mCePath, CE_DATA_FILE_NAME);
        FileUtils.assertFileDoesNotExist(mCePath, DE_DATA_FILE_NAME);
        FileUtils.assertFileDoesNotExist(mDePath, CE_DATA_FILE_NAME);
        FileUtils.assertFileExists(mDePath, DE_DATA_FILE_NAME);
    }

    @Test
    public void testAppACeDataExists() {
        FileUtils.assertFileExists(mCePath, CE_DATA_FILE_NAME);
    }

    @Test
    public void testAppACeDataDoesNotExist() {
        FileUtils.assertFileDoesNotExist(mCePath, CE_DATA_FILE_NAME);
    }

    @Test
    public void testAppADeDataExists() {
        FileUtils.assertFileExists(mDePath, DE_DATA_FILE_NAME);
    }

    @Test
    public void testAppADeDataDoesNotExist() {
        FileUtils.assertFileDoesNotExist(mDePath, DE_DATA_FILE_NAME);
    }

    @Test
    public void testAppACurProfileDataAccessible() {
        FileUtils.assertDirIsAccessible("/data/misc/profiles/cur/0/" + mContext.getPackageName());
    }

    @Test
    public void testAppARefProfileDataNotAccessible() {
        FileUtils.assertDirIsNotAccessible("/data/misc/profiles/ref");
    }
}
