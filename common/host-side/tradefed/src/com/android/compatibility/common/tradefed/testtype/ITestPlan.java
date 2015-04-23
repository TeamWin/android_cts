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

import java.util.Collection;

/**
 * Interface for accessing test plan data.
 */
public interface ITestPlan {

    /**
     * @return The name of this test plan.
     */
    String getName();

    /**
     * Gets a sorted list of module names contained in this plan.
     */
    Collection<String> getModuleNames();

    /**
     * Add a module to this test plan
     */
    void addModule(String name);

    /**
     * Adds the option to pass to the module referenced by the given name.
     */
    void addModuleOption(String name, String key, String value);

}
