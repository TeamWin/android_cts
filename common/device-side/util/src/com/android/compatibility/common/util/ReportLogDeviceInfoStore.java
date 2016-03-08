/*
 * Copyright (C) 2016 The Android Open Source Project
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

import android.util.JsonWriter;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class ReportLogDeviceInfoStore extends DeviceInfoStore {

    private final String mStreamName;

    public ReportLogDeviceInfoStore(File jsonFile, String streamName) throws Exception {
        mJsonFile = jsonFile;
        mStreamName = streamName;
    }

    /**
     * Creates the writer and starts the JSON Object for the metric stream.
     */
    @Override
    public void open() throws IOException {
        BufferedWriter formatWriter;
        String oldMetrics;
        if (mJsonFile.exists()) {
            BufferedReader jsonReader = new BufferedReader(new FileReader(mJsonFile));
            StringBuilder oldMetricsBuilder = new StringBuilder();
            String line;
            while ((line = jsonReader.readLine()) != null) {
                oldMetricsBuilder.append(line);
            }
            oldMetrics = oldMetricsBuilder.toString().trim();
            if (oldMetrics.charAt(oldMetrics.length() - 1) == '}') {
                oldMetrics = oldMetrics.substring(0, oldMetrics.length() - 1);
            }
            oldMetrics = oldMetrics + ",";
        } else {
            oldMetrics = "{";
        }
        mJsonFile.createNewFile();
        formatWriter = new BufferedWriter(new FileWriter(mJsonFile));
        formatWriter.write(oldMetrics + "\"" + mStreamName + "\":", 0,
            oldMetrics.length() + mStreamName.length() + 3);
        formatWriter.flush();
        formatWriter.close();
        mJsonWriter = new JsonWriter(new FileWriter(mJsonFile, true));
        mJsonWriter.beginObject();
    }

    /**
     * Closes the writer.
     */
    @Override
    public void close() throws IOException {
        mJsonWriter.endObject();
        mJsonWriter.close();
        // Close the overall JSON object
        BufferedWriter formatWriter = new BufferedWriter(new FileWriter(mJsonFile, true));
        formatWriter.write("}", 0, 1);
        formatWriter.flush();
        formatWriter.close();
    }
}
