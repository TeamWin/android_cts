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
 * limitations under the License.
 */

package com.android.compatibility.common.util;

import junit.framework.TestCase;

import org.xmlpull.v1.XmlPullParserException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Unit tests for {@link DynamicConfig}
 */
public class DynamicConfigTest extends TestCase {
    private static final String correctConfig =
            "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
            "<DynamicConfig>\n" +
            "    <Config key=\"test-config-1\">test config 1</Config>\n" +
            "    <Config key=\"test-config-2\">testconfig2</Config>\n" +
            "    <ConfigList key=\"config-list\">\n" +
            "        <Item>config0</Item>\n" +
            "        <Item>config1</Item>\n" +
            "        <Item>config2</Item>\n" +
            "        <Item>config3</Item>\n" +
            "        <Item>config4</Item>\n" +
            "    </ConfigList>\n" +
            "    <ConfigList key=\"config-list-2\">\n" +
            "        <Item>A</Item>\n" +
            "        <Item>B</Item>\n" +
            "        <Item>C</Item>\n" +
            "        <Item>D</Item>\n" +
            "        <Item>E</Item>\n" +
            "    </ConfigList>\n" +
            "</DynamicConfig>\n";

    private static final String configWrongNodeName =
            "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
            "<DynamicCsonfig>\n" +  //The node name DynamicConfig is intentionally mistyped
            "    <Config key=\"test-config-1\">test config 1</Config>\n" +
            "    <Config key=\"test-config-2\">testconfig2</Config>\n" +
            "    <ConfigList key=\"config-list\">\n" +
            "        <Item>Nevermore</Item>\n" +
            "        <Item>Puck</Item>\n" +
            "        <Item>Zeus</Item>\n" +
            "        <Item>Earth Shaker</Item>\n" +
            "        <Item>Vengeful Spirit</Item>\n" +
            "    </ConfigList>\n" +
            "    <ConfigList key=\"config-list-2\">\n" +
            "        <Item>A</Item>\n" +
            "        <Item>B</Item>\n" +
            "        <Item>C</Item>\n" +
            "        <Item>D</Item>\n" +
            "        <Item>E</Item>\n" +
            "    </ConfigList>\n" +
            "</DynamicConfig>\n";

    public void testCorrectConfig() throws Exception {
        DynamicConfig config = new DynamicConfig();
        File file = createFileFromStr(correctConfig);
        config.initConfigFromXml(file);

        assertEquals("Wrong Config", config.getConfig("test-config-1"), "test config 1");
        assertEquals("Wrong Config", config.getConfig("test-config-2"), "testconfig2");
        assertEquals("Wrong Config List", config.getConfigList("config-list").get(0), "config0");
        assertEquals("Wrong Config List", config.getConfigList("config-list").get(2), "config2");
        assertEquals("Wrong Config List", config.getConfigList("config-list-2").get(2), "C");
    }

    public void testConfigWithWrongNodeName() throws Exception {
        DynamicConfig config = new DynamicConfig();
        File file = createFileFromStr(configWrongNodeName);

        try {
            config.initConfigFromXml(file);
            fail("Cannot detect error when config file has wrong node name");
        } catch (XmlPullParserException e) {
            //expected
        }
    }

    private File createFileFromStr(String configStr) throws IOException {
        File file = File.createTempFile("test", "dynamic");
        FileOutputStream stream = new FileOutputStream(file);
        stream.write(configStr.getBytes());
        stream.flush();
        return file;
    }
}
