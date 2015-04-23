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
package com.android.compatibility.common.tradefed.testtype;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Implementation of {@link ITestPlan}.
 */
public class TestPlan implements ITestPlan {

    /**
     * Map of modules found in plan, and their options
     */
    private Map<String, Map<String, List<String>>> mModuleOptionsMap;

    private final String mName;

    public TestPlan(String name) {
        this(name, new LinkedHashMap<String, Map<String, List<String>>>());
    }

    public TestPlan(String name, Map<String, Map<String, List<String>>> moduleOptions) {
        mName = name;
        mModuleOptionsMap = moduleOptions;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getName() {
        return mName;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> getModuleNames() {
        List<String> names = new ArrayList<String>(mModuleOptionsMap.keySet());
        Collections.sort(names);
        return names;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addModule(String name) {
        mModuleOptionsMap.put(name, new HashMap<String, List<String>>());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addModuleOption(String name, String key, String value) {
        Map<String, List<String>> options = mModuleOptionsMap.get(name);
        if (options != null) {
            List<String> values = options.get(key);
            if (values == null) {
                values = new ArrayList<String>();
                options.put(key, values);
            }
            values.add(value);
        } else {
            throw new IllegalArgumentException(String.format("Could not find module %s", name));
        }
    }

}
