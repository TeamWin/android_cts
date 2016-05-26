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

package com.android.compatibility.common.tradefed.build;

import com.android.tradefed.build.IBuildInfo;

import java.util.HashMap;
import java.util.Map;

/**
 * Singleton class containing a master {@link IBuildInfo} to be persisted across sharded
 * invocations.
 */
public class MasterBuildInfo {

    private static Map<String, String> mBuild = new HashMap<String, String>();

    private MasterBuildInfo() { }

    public static Map<String, String> getBuild() {
        return mBuild;
    }

    /**
     *
     * @param buildInfo
     */
    public static void addBuildInfo(Map<String, String> buildInfo) {
        mBuild.putAll(buildInfo);
    }

    public static void clear() {
        mBuild.clear();
    }
}
