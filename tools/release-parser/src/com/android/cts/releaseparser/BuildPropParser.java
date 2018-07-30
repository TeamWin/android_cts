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
 * limitations under the License.
 */

package com.android.cts.releaseparser;

import com.android.cts.releaseparser.ReleaseProto.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;

public class BuildPropParser extends FileParser {
    private Entry.EntryType mType;
    private HashMap<String, String> mProp;

    public BuildPropParser(File file) {
        super(file);
    }

    @Override
    public Entry.EntryType getType() {
        if (mType == null) {
            parseFile();
        }
        return mType;
    }

    public String getBuildNumber() {
        if (mType == null) {
            parseFile();
        }
        return mProp.get("ro.build.version.incremental");
    }

    public String getVersion() {
        if (mType == null) {
            parseFile();
        }
        return mProp.get("ro.build.id");
    }

    public String getName() {
        if (mType == null) {
            parseFile();
        }
        return mProp.get("ro.product.device");
    }

    public String getFullName() {
        if (mType == null) {
            parseFile();
        }
        return mProp.get("ro.build.flavor");
    }

    private void parseFile() {
        try {
            FileReader fileReader = new FileReader(getFile());
            BufferedReader buffReader = new BufferedReader(fileReader);
            String line;
            mProp = new HashMap<>();
            while ((line = buffReader.readLine()) != null) {
                String trimLine = line.trim();
                if (!trimLine.startsWith("#")) {
                    String[] phases = trimLine.split("=");
                    if (phases.length > 1) {
                        mProp.put(phases[0], phases[1]);
                    } else {
                        mProp.put(phases[0], "");
                    }
                }
            }
            fileReader.close();
            mType = Entry.EntryType.BUILD_PROP;
        } catch (IOException e) {
            // file is not a Test Module Config
            System.err.println("BuildProp err:" + getFileName() + "\n" + e.getMessage());
            mType = super.getType();
        }
    }
}
