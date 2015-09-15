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
import com.android.compatibility.common.util.TestFilter;
import com.android.tradefed.config.Configuration;
import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.config.ConfigurationFactory;
import com.android.tradefed.config.IConfigurationFactory;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.targetprep.ITargetPreparer;
import com.android.tradefed.testtype.IAbi;
import com.android.tradefed.testtype.IRemoteTest;

import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Retrieves Compatibility test module definitions from the repository.
 */
public class ModuleRepo implements IModuleRepo {

    private static final String CONFIG_EXT = ".config";

    /** mapping of module id to definition */
    private final Map<String, IModuleDef> mModules;
    private final Set<IAbi> mAbis;

    /**
     * Creates a {@link ModuleRepo}, initialized from provided build
     */
    public ModuleRepo(File testsDir, Set<IAbi> abis) {
        this(new HashMap<String, IModuleDef>(), abis);
        parse(testsDir);
    }

    /**
     * Creates a {@link ModuleRepo}, initialized with the given modules
     */
    public ModuleRepo(Map<String, IModuleDef> modules, Set<IAbi> abis) {
        mModules = modules;
        mAbis = abis;
    }

    /**
     * Builds mTestMap based on directory contents
     */
    private void parse(File dir) {
        File[] configFiles = dir.listFiles(new ConfigFilter());
        IConfigurationFactory configFactory = ConfigurationFactory.getInstance();
        for (File configFile : configFiles) {
            try {
                // Invokes parser to process the test module config file
                // Need to generate a different config for each ABI as we cannot guarantee the
                // configs are idempotent. This however means we parse the same file multiple times.
                for (IAbi abi : mAbis) {
                    Configuration config = (Configuration) configFactory.createConfigurationFromArgs(
                            new String[]{configFile.getAbsolutePath()});
                    String name = configFile.getName().replace(CONFIG_EXT, "");
                    List<IRemoteTest> tests = config.getTests();
                    List<ITargetPreparer> preparers = config.getTargetPreparers();
                    IModuleDef def = new ModuleDef(name, abi, tests, preparers);
                    mModules.put(AbiUtils.createId(abi.getName(), name), def);
                }
            } catch (ConfigurationException e) {
                throw new RuntimeException(String.format("error parsing config file: %s",
                        configFile.getName()), e);
            }
        }
    }

    /**
     * A {@link FilenameFilter} to find all the config files in a directory.
     */
    public static class ConfigFilter implements FilenameFilter {

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean accept(File dir, String name) {
            return name.endsWith(CONFIG_EXT);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IModuleDef getModule(String id) {
        return mModules.get(id);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<String, IModuleDef> getModules() {
        return mModules;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<String, List<IModuleDef>> getModulesByName() {
        Map<String, List<IModuleDef>> modules = new HashMap<>();
        for (IModuleDef moduleDef : mModules.values()) {
            String name = moduleDef.getName();
            List<IModuleDef> defs = modules.get(name);
            if (defs == null) {
                defs = new ArrayList<IModuleDef>();
                modules.put(name, defs);
            }
            defs.add(moduleDef);
        }
        return modules;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> getModuleIds() {
        List<String> ids = new ArrayList<>(mModules.keySet());
        Collections.sort(ids);
        return ids;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> getModuleNames() {
        Set<String> names = new HashSet<>();
        for (IModuleDef moduleDef : mModules.values()) {
            names.add(moduleDef.getName());
        }
        List<String> namesList = new ArrayList<>(names);
        Collections.sort(namesList);
        return namesList;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Set<String> getModulesMatching(String regex) {
        Set<String> names = new HashSet<>();
        Pattern pattern = Pattern.compile(regex);
        for (IModuleDef moduleDef : mModules.values()) {
            if (moduleDef.nameMatches(pattern)) {
                names.add(moduleDef.getName());
            }
        }
        return names;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<IModuleDef> getModules(List<String> includeFilters, List<String> excludeFilters) {
        Map<String, IModuleDef> moduleDefs = new HashMap<>();
        Set<String> ids;
        // Include all the inclusions
        for (String filterString : includeFilters) {
            TestFilter filter = TestFilter.createFrom(filterString);
            String test = filter.getTest();
            ids = getAllIds(filter);
            for (String id : ids) {
                IModuleDef module = getModule(id);
                if (test != null) {
                    // We're including a subset of tests
                    module.addIncludeFilter(test);
                }
                moduleDefs.put(id, module);
            }
        }
        // Exclude all the exclusions
        for (String filterString : excludeFilters) {
            TestFilter filter = TestFilter.createFrom(filterString);
            String test = filter.getTest();
            ids = getAllIds(filter);
            // Iterate through all IDs
            for (String id : ids) {
                IModuleDef module = moduleDefs.get(id);
                if (module != null) {
                    if (test != null) {
                        // Excluding a subset of tests, so keep module but give filter
                        module.addExcludeFilter(test);
                    } else {
                        // Excluding all tests in the module so just remove the whole thing
                        moduleDefs.remove(id);
                    }
                }
            }
        }
        if (moduleDefs.isEmpty()) {
            throw new IllegalStateException("Nothing to do. Use 'list modules' to see available"
                    + " modules, and 'list results' to see available sessions to retry.");
        }
        // Note: run() relies on the fact that the list is reliably sorted for sharding purposes
        List<IModuleDef> sortedModuleDefs = new ArrayList<>(moduleDefs.values());
        Collections.sort(sortedModuleDefs);
        return sortedModuleDefs;
    }

    /**
     * Returns all IDs matching the given filter.
     */
    private Set<String> getAllIds(TestFilter filter) {
        String abi = filter.getAbi();
        String name = filter.getName();
        Set<String> filteredNames = getModulesMatching(name);
        if (filteredNames.isEmpty()) {
            throw new IllegalArgumentException(String.format(
                    "Not modules matching %s. Use 'list modules' to see available modules.",
                    filter.getName()));
        }
        Set<String> ids = new HashSet<>();
        for (String module : filteredNames) {
            if (abi != null) {
                ids.add(AbiUtils.createId(abi, module));
            } else {
                // ABI not specified, test on all ABIs
                for (IAbi a : mAbis) ids.add(AbiUtils.createId(a.getName(), module));
            }
        }
        return ids;
    }
}
