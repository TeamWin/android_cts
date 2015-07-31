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

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Load dynamic config for test cases
 */
public class DynamicConfig {
    public final static String MODULE_NAME = "module-name";

    //XML constant
    private static final String NS = null;
    private static final String DYNAMIC_CONFIG_TAG = "DynamicConfig";
    private static final String CONFIG_TAG = "Config";
    private static final String CONFIG_LIST_TAG = "ConfigList";
    private static final String ITEM_TAG = "Item";
    private static final String KEY_ATTR = "key";

    public final static String CONFIG_FOLDER_ON_DEVICE = "/sdcard/dynamic-config-files/";
    public final static String CONFIG_FOLDER_ON_HOST =
            System.getProperty("java.io.tmpdir") + "/dynamic-config-files/";


    protected Map<String, String> mDynamicParams;
    protected Map<String, List<String>> mDynamicArrayParams;

    protected void initConfigFromXml(File file) throws XmlPullParserException, IOException {
        mDynamicParams = new HashMap<>();
        mDynamicArrayParams = new HashMap<>();

        XmlPullParser parser = XmlPullParserFactory.newInstance().newPullParser();
        parser.setInput(new InputStreamReader(new FileInputStream(file)));

        parser.nextTag();
        parser.require(XmlPullParser.START_TAG, NS, DYNAMIC_CONFIG_TAG);

        while (parser.nextTag() == XmlPullParser.START_TAG) {
            if (parser.getName().equals(CONFIG_TAG)) {
                String key = parser.getAttributeValue(NS, KEY_ATTR);
                String value = parser.nextText();
                parser.require(XmlPullParser.END_TAG, NS, CONFIG_TAG);
                if (key != null && !key.isEmpty()) {
                    mDynamicParams.put(key, value);
                }
            } else {
                List<String> arrayValue = new ArrayList<>();
                parser.require(XmlPullParser.START_TAG, NS, CONFIG_LIST_TAG);
                String key = parser.getAttributeValue(NS, KEY_ATTR);
                while (parser.nextTag() == XmlPullParser.START_TAG) {
                    parser.require(XmlPullParser.START_TAG, NS, ITEM_TAG);
                    arrayValue.add(parser.nextText());
                    parser.require(XmlPullParser.END_TAG, NS, ITEM_TAG);
                }
                parser.require(XmlPullParser.END_TAG, NS, CONFIG_LIST_TAG);
                if (key != null && !key.isEmpty()) {
                    mDynamicArrayParams.put(key, arrayValue);
                }
            }
        }
        parser.require(XmlPullParser.END_TAG, NS, DYNAMIC_CONFIG_TAG);
    }

    public String getConfig(String key) {
        return mDynamicParams.get(key);
    }

    public List<String> getConfigList(String key) {
        return mDynamicArrayParams.get(key);
    }

    public static File getConfigFile(File configFolder, String moduleName) {
        return new File(configFolder, String.format("%s.dynamic", moduleName));
    }

    public static String calculateSHA1(File file) {
        MessageDigest sha1;
        byte[] buffer = new byte[1024];
        InputStream input;

        try {
            sha1 = MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            return null;
        }
        try {
            input = new FileInputStream(file);
            int length = input.read(buffer);
            while (length != -1) {
                sha1.update(buffer, 0, length);
                length = input.read(buffer);
            }
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }

        StringBuilder sb = new StringBuilder();
        for (byte b: sha1.digest()) {
            sb.append(Integer.toString((b & 0xff) + 0x100, 16).substring(1));
        }
        return sb.toString();
    }
}
