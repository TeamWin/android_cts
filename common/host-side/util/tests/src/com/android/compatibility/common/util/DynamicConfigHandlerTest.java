/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.compatibility.common.util;

import junit.framework.TestCase;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Unit tests for {@link DynamicConfigHandler}
 */
public class DynamicConfigHandlerTest extends TestCase {

    private static final String localConfig =
            "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
            "<DynamicConfig>\n" +
            "    <Config key=\"test-config-1\">test-config-1</Config>\n" +
            "    <Config key=\"test-config-2\">test-config-2</Config>\n" +
            "    <Config key=\"override-config-2\">test-config-3\n" +
            "    <ConfigList key=\"config-list\">\n" +
            "        <Item>config0</Item>\n" +
            "        <Item>config1</Item>\n" +
            "        <Item>config2</Item>\n" +
            "        <Item>config3</Item>\n" +
            "        <Item>config4</Item>\n" +
            "    </ConfigList>\n" +
            "    <ConfigList key=\"override-config-list-2\">\n" +
            "        <Item>A</Item>\n" +
            "        <Item>B</Item>\n" +
            "        <Item>C</Item>\n" +
            "        <Item>D</Item>\n" +
            "        <Item>E</Item>\n" +
            "    </ConfigList>\n" +
            "</DynamicConfig>\n";

    private static final String overrideJson =
            "{\n" +
            "  \"config\": [\n" +
            "    {\n" +
            "      \"key\": \"version\",\n" +
            "      \"value\": \"1.0\"\n" +
            "    },\n" +
            "    {\n" +
            "      \"key\": \"suite\",\n" +
            "      \"value\": \"CTS_V2\"\n" +
            "    },\n" +
            "    {\n" +
            "      \"key\": \"override-config-1\",\n" +
            "      \"value\": \"override-config-val-1\"\n" +
            "    },\n" +
            "    {\n" +
            "      \"key\": \"override-config-2\",\n" +
            "      \"value\": \"override-config-val-2\"\n" +
            "    }\n" +
            "  ],\n" +
            "  \"configList\": [\n" +
            "    {\n" +
            "      \"key\": \"override-config-list-1\",\n" +
            "      \"value\": [\n" +
            "        \"override-config-list-val-1-1\",\n" +
            "        \"override-config-list-val-1-2\"\n" +
            "      ]\n" +
            "    },\n" +
            "    {\n" +
            "      \"key\": \"override-config-list-2\",\n" +
            "      \"value\": [\n" +
            "        \"override-config-list-val-2-1\",\n" +
            "      ]\n" +
            "    },\n" +
            "    {\n" +
            "      \"key\": \"override-config-list-3\",\n" +
            "      \"value\": []\n" +
            "    }\n" +
            "  ]\n" +
            "}";

    public void testDynamicConfigHandler() throws Exception {
        String module = "test1";
        File localConfigFile = createFileFromStr(localConfig, module);

        File mergedFile = DynamicConfigHandler
                .getMergedDynamicConfigFile(localConfigFile, overrideJson, module);

        DynamicConfig.Params params = DynamicConfig.genParamsFromFile(mergedFile);

        assertEquals("override-config-val-1", params.mDynamicParams.get("override-config-1"));
        assertTrue(params.mDynamicArrayParams.get("override-config-list-1")
                .contains("override-config-list-val-1-1"));
        assertTrue(params.mDynamicArrayParams.get("override-config-list-1")
                .contains("override-config-list-val-1-2"));
        assertTrue(params.mDynamicArrayParams.get("override-config-list-3").size() == 0);

        assertEquals("test config 1", params.mDynamicParams.get("test-config-1"));
        assertTrue(params.mDynamicArrayParams.get("config-list").contains("Config2"));

        assertEquals("override-config-val-2", params.mDynamicParams.get("override-config-2"));
        assertEquals(1, params.mDynamicArrayParams.get("override-config-list-2").size());
        assertTrue(params.mDynamicArrayParams.get("override-config-list-2")
                .contains("override-config-list-val-2-1"));
    }


    private File createFileFromStr(String configStr, String module) throws IOException {
        File file = File.createTempFile(module, "dynamic");
        FileOutputStream stream = new FileOutputStream(file);
        stream.write(configStr.getBytes());
        stream.flush();
        return file;
    }
}
