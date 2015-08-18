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

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;
import org.xmlpull.v1.XmlSerializer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DynamicConfigHandler {

    private static final String LOG_TAG = DynamicConfigHandler.class.getSimpleName();

    // xml constant
    private static final String NS = null; //representing null namespace
    private static final String ENCODING = "UTF-8";

    public static File getMergedDynamicConfigFile(File localConfigFile, String apfeConfigJson,
            String moduleName) throws IOException, XmlPullParserException, JSONException {

        DynamicConfig.Params localConfig = DynamicConfig.genParamsFromFile(localConfigFile);
        DynamicConfig.Params apfeOverride = parseJsonToParam(apfeConfigJson);

        localConfig.mDynamicParams.putAll(apfeOverride.mDynamicParams);
        localConfig.mDynamicArrayParams.putAll(apfeOverride.mDynamicArrayParams);

        File mergedConfigFile = storeMergedConfigFile(localConfig, moduleName);
        return mergedConfigFile;
    }

    private static DynamicConfig.Params parseJsonToParam(String apfeConfigJson)
            throws IOException, JSONException {
        if (apfeConfigJson == null) return new DynamicConfig.Params();

        Map<String, String> configMap = new HashMap<>();
        Map<String, List<String>> configListMap = new HashMap<>();

        JSONObject rootObj  = new JSONObject(new JSONTokener(apfeConfigJson));
        if (rootObj.has("config")) {
            JSONArray configArr = rootObj.getJSONArray("config");
            for (int i = 0; i < configArr.length(); i++) {
                JSONObject config = configArr.getJSONObject(i);
                configMap.put(config.getString("key"), config.getString("value"));
            }
        }
        if (rootObj.has("configList")) {
            JSONArray configListArr = rootObj.getJSONArray("configList");
            for (int i = 0; i < configListArr.length(); i++) {
                JSONObject configList = configListArr.getJSONObject(i);
                String key = configList.getString("key");
                List<String> values = new ArrayList<>();
                JSONArray configListValuesArr = configList.getJSONArray("value");
                for (int j = 0; j < configListValuesArr.length(); j++) {
                    values.add(configListValuesArr.getString(j));
                }
                configListMap.put(key, values);
            }
        }

        DynamicConfig.Params param = new DynamicConfig.Params();
        param.mDynamicParams = configMap;
        param.mDynamicArrayParams = configListMap;
        return param;
    }

    private static File storeMergedConfigFile(DynamicConfig.Params p, String moduleName)
            throws XmlPullParserException, IOException {
        XmlSerializer serializer = XmlPullParserFactory.newInstance().newSerializer();

        File parentFolder = new File(DynamicConfig.CONFIG_FOLDER_ON_HOST);
        if (!parentFolder.exists()) parentFolder.mkdir();
        File folder = new File(DynamicConfig.MERGED_CONFIG_FILE_FOLDER);
        if (!folder.exists()) folder.mkdir();
        File mergedConfigFile = new File(folder, moduleName+".dynamic");
        OutputStream stream = new FileOutputStream(mergedConfigFile);
        serializer.setOutput(stream, ENCODING);
        serializer.setFeature("http://xmlpull.org/v1/doc/features.html#indent-output", true);
        serializer.startDocument(ENCODING, false);

        serializer.startTag(NS, DynamicConfig.DYNAMIC_CONFIG_TAG);
        for (String key : p.mDynamicParams.keySet()) {
            serializer.startTag(NS, DynamicConfig.CONFIG_TAG);
            serializer.attribute(NS, DynamicConfig.KEY_ATTR, key);
            serializer.text(p.mDynamicParams.get(key));
            serializer.endTag(NS, DynamicConfig.CONFIG_TAG);
        }
        for (String key : p.mDynamicArrayParams.keySet()) {
            serializer.startTag(NS, DynamicConfig.CONFIG_LIST_TAG);
            serializer.attribute(NS, DynamicConfig.KEY_ATTR, key);
            for (String item: p.mDynamicArrayParams.get(key)) {
                serializer.startTag(NS, DynamicConfig.ITEM_TAG);
                serializer.text(item);
                serializer.endTag(NS, DynamicConfig.ITEM_TAG);
            }
            serializer.endTag(NS, DynamicConfig.CONFIG_LIST_TAG);
        }
        serializer.endTag(NS, DynamicConfig.DYNAMIC_CONFIG_TAG);
        serializer.endDocument();
        return mergedConfigFile;
    }
}
