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

import com.android.compatibility.common.util.AbiUtils;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Interface for accessing tests from the Compatibility repository.
 */
public interface IModuleRepo {

    /**
     * Get a {@link IModuleDef} given an id.
     *
     * @param id the id of the module (created by {@link AbiUtils#createId(String, String)})
     */
    IModuleDef getModule(String id);

    /**
     * @return a {@link Map} of all modules in repo.
     */
    Map<String, IModuleDef> getModules();

    /**
     * @return a sorted {@link List} of {@link IModuleDef}s given the filters.
     */
    List<IModuleDef> getModules(List<String> includeFilters, List<String> excludeFilters);

    /**
     * @return a {@link Map} of all module in repo keyed by name.
     */
    Map<String, List<IModuleDef>> getModulesByName();

    /**
     * @return a sorted {@link List} of module names.
     */
    List<String> getModuleNames();

    /**
     * @return a {@link Set} of modules names that match the given regular expression.
     */
    Set<String> getModulesMatching(String regex);

    /**
     * @return a sorted {@link List} of module ids.
     */
    List<String> getModuleIds();
}
