/*
 * Copyright (C) 2018 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.cts.releaseparser;

import com.android.cts.releaseparser.ReleaseProto.*;
import com.google.protobuf.TextFormat;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.File;

import static org.junit.Assert.*;

/** Unit tests for {@link ApkParser} */
@RunWith(JUnit4.class)
public class ApkParserTest {
    // HelloActivity.apk's source code: android/cts/tests/tests/jni/AndroidManifest.xml
    private static final String TEST_SIMPLE_APK = "HelloActivity.apk";
    private static final String TEST_SIMPLE_APK_PB_TXT = "HelloActivity.apk.pb.txt";

    // CtsJniTestCases.apk's source code:
    // android/development/samples/HelloActivity/AndroidManifest.xml
    private static final String TEST_SO_APK = "CtsJniTestCases.apk";
    private static final String TEST_SO_APK_PB_TXT = "CtsJniTestCases.apk.pb.txt";

    /**
     * Test {@link ApkParser} with an simple APK
     *
     * @throws Exception
     */
    @Test
    public void testSimpleApk() throws Exception {
        testApkParser(TEST_SIMPLE_APK, TEST_SIMPLE_APK_PB_TXT);
    }

    /**
     * Test {@link ApkParser} with an APK with Shared Objects/Nactive Code
     *
     * @throws Exception
     */
    @Test
    public void testSoApk() throws Exception {
        testApkParser(TEST_SO_APK, TEST_SO_APK_PB_TXT);
    }

    private void testApkParser(String apkFileName, String txtProtobufFileName) throws Exception {
        File apkFile = ClassUtils.getResrouceFile(getClass(), apkFileName);
        ApkParser aParser = new ApkParser(apkFile);
        AppInfo appInfo = aParser.getAppInfo();

        AppInfo.Builder expectedAppInfoBuilder = AppInfo.newBuilder();
        TextFormat.getParser()
                .merge(
                        ClassUtils.getResrouceContentString(getClass(), txtProtobufFileName),
                        expectedAppInfoBuilder);
        assertTrue(
                String.format(
                        "ApkParser does not return the same AppInfo of %s as %s.\n%s",
                        apkFileName, txtProtobufFileName, TextFormat.printToString(appInfo)),
                appInfo.equals(expectedAppInfoBuilder.build()));
    }
}
