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
package com.android.compatibility.common.tradefed.util;

import com.android.compatibility.common.tradefed.build.CompatibilityBuildHelper;
import com.android.tradefed.build.BuildInfo;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.util.FileUtil;

import org.xmlpull.v1.XmlPullParserException;

import junit.framework.TestCase;

import java.io.File;
import java.io.IOException;

/**
 * Unit tests for {@link DynamicConfigFileReader}.
 */
public class DynamicConfigFileReaderTest extends TestCase {

    private static final String MODULE_NAME = "cts";
    private IBuildInfo mBuildInfo;
    private File mConfigFile;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mConfigFile = FileUtil.createTempFile("temp-dynamic-config", ".xml");
        mBuildInfo = new BuildInfo();
        CompatibilityBuildHelper helper = new CompatibilityBuildHelper(mBuildInfo);
        helper.addDynamicConfigFile(MODULE_NAME, mConfigFile);
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        FileUtil.deleteFile(mConfigFile);
        mBuildInfo.cleanUp();
    }

    private void writeDynamicConfigFile() throws IOException {
        String content = "<dynamicConfig>\n" +
            "  <entry key=\"media_files_url\">\n" +
            "    <value>some value</value>\n" +
            "  </entry>\n" +
            "</dynamicConfig>";
        FileUtil.writeToFile(content, mConfigFile);
    }

    /**
     * Test when the dynamic file is completely invalid.
     */
    public void testGetValueFromFile_invalidFile() throws Exception {
        try {
            DynamicConfigFileReader.getValueFromConfig(mConfigFile, "doesnotexit");
            fail("Should have thrown an exception.");
        } catch (XmlPullParserException expected) {
            // expected
        }
    }

    /**
     * Test when requesting a key not part of the dynamic file.
     */
    public void testGetValueFromFile_keyNotFound() throws Exception {
        writeDynamicConfigFile();
        String res = DynamicConfigFileReader.getValueFromConfig(mConfigFile, "doesnotexit");
        assertNull(res);
    }

    /**
     * Test when getting the value associated with the key.
     */
    public void testGetValueFromFile() throws Exception {
        writeDynamicConfigFile();
        String res = DynamicConfigFileReader.getValueFromConfig(mConfigFile, "media_files_url");
        assertEquals("some value", res);
    }

    /**
     * Test when getting the value using directly the build info and module name.
     */
    public void testGetValueFromBuild() throws Exception {
        writeDynamicConfigFile();
        String res = DynamicConfigFileReader.getValueFromConfig(
                mBuildInfo, MODULE_NAME, "media_files_url");
        assertEquals("some value", res);
    }

    /**
     * Test when trying to get a value from an unknown module.
     */
    public void testGetValueFromBuild_moduleNotFound() throws Exception {
        writeDynamicConfigFile();
        String res = DynamicConfigFileReader.getValueFromConfig(
                mBuildInfo, "NOT_A_MODULE", "media_files_url");
        assertNull(res);
    }
}
