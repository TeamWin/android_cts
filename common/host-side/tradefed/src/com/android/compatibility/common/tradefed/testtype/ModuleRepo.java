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
import com.android.ddmlib.Log.LogLevel;
import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.config.ConfigurationFactory;
import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.config.IConfigurationFactory;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.targetprep.ITargetPreparer;
import com.android.tradefed.testtype.IAbi;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.testtype.IShardableTest;

import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/**
 * Retrieves Compatibility test module definitions from the repository.
 */
public class ModuleRepo implements IModuleRepo {

    private static final String CONFIG_EXT = ".config";

    static IModuleRepo sInstance;

    private int mShards;
    private int mModulesPerShard;
    private Set<String> mSerials = new HashSet<>();
    private Map<String, Set<String>> mDeviceTokens = new HashMap<>();
    private Map<String, Map<String, String>> mTestArgs = new HashMap<>();
    private Map<String, Map<String, String>> mModuleArgs = new HashMap<>();
    private boolean mIncludeAll;
    private Map<String, List<TestFilter>> mIncludeFilters = new HashMap<>();
    private Map<String, List<TestFilter>> mExcludeFilters = new HashMap<>();
    private IConfigurationFactory mConfigFactory = ConfigurationFactory.getInstance();

    private volatile boolean mInitialized = false;

    // Holds all the 'normal' tests waiting to be run.
    private Set<IModuleDef> mRemainingModules = new HashSet<>();
    // Holds all the 'special' tests waiting to be run. Meaning the DUT must have a specific token.
    private Set<IModuleDef> mRemainingWithTokens = new HashSet<>();

    public static IModuleRepo getInstance() {
        if (sInstance == null) {
            sInstance = new ModuleRepo();
        }
        return sInstance;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getNumberOfShards() {
        return mShards;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getModulesPerShard() {
        return mModulesPerShard;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<String, Set<String>> getDeviceTokens() {
        return mDeviceTokens;
    }
        
    /**
     * A {@link FilenameFilter} to find all modules in a directory who match the given pattern.
     */
    public static class NameFilter implements FilenameFilter {

        private String mPattern;

        public NameFilter(String pattern) {
            mPattern = pattern;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean accept(File dir, String name) {
            return name.contains(mPattern) && name.endsWith(CONFIG_EXT);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Set<String> getSerials() {
        return mSerials;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Set<IModuleDef> getRemainingModules() {
        return mRemainingModules;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Set<IModuleDef> getRemainingWithTokens() {
        return mRemainingWithTokens;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isInitialized() {
        return mInitialized;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void initialize(int shards, File testsDir, Set<IAbi> abis, List<String> deviceTokens,
            List<String> testArgs, List<String> moduleArgs, List<String> includeFilters,
            List<String> excludeFilters) {
        mInitialized = true;
        mShards = shards;
        for (String line : deviceTokens) {
            String[] parts = line.split(":");
            if (parts.length == 2) {
                String key = parts[0];
                String value = parts[1];
                Set<String> list = mDeviceTokens.get(key);
                if (list == null) {
                    list = new HashSet<>();
                    mDeviceTokens.put(key, list);
                }
                list.add(value);
            } else {
                throw new IllegalArgumentException(
                        String.format("Could not parse device token: %s", line));
            }
        }
        putArgs(testArgs, mTestArgs);
        putArgs(moduleArgs, mModuleArgs);
        mIncludeAll = includeFilters.isEmpty();
        // Include all the inclusions
        addFilters(includeFilters, mIncludeFilters, abis);
        // Exclude all the exclusions
        addFilters(excludeFilters, mExcludeFilters, abis);

        File[] configFiles = testsDir.listFiles(new ConfigFilter());
        for (File configFile : configFiles) {
            final String name = configFile.getName().replace(CONFIG_EXT, "");
            final String[] pathArg = new String[] { configFile.getAbsolutePath() };
            try {
                // Invokes parser to process the test module config file
                // Need to generate a different config for each ABI as we cannot guarantee the
                // configs are idempotent. This however means we parse the same file multiple times
                for (IAbi abi : abis) {
                    IConfiguration config = mConfigFactory.createConfigurationFromArgs(pathArg);
                    String id = AbiUtils.createId(abi.getName(), name);
                    {
                        Map<String, String> args = new HashMap<>();
                        if (mModuleArgs.containsKey(name)) {
                            args.putAll(mModuleArgs.get(name));
                        }
                        if (mModuleArgs.containsKey(id)) {
                            args.putAll(mModuleArgs.get(id));
                        }
                        if (args != null && args.size() > 0) {
                            for (Entry<String, String> entry : args.entrySet()) {
                                config.injectOptionValue(entry.getKey(), entry.getValue());
                            }
                        }
                    }
                    List<IRemoteTest> tests = config.getTests();
                    for (IRemoteTest test : tests) {
                        String className = test.getClass().getName();
                        Map<String, String> args = new HashMap<>();
                        if (mTestArgs.containsKey(className)) {
                            args.putAll(mTestArgs.get(className));
                        }
                        if (args != null && args.size() > 0) {
                            for (Entry<String, String> entry : args.entrySet()) {
                                config.injectOptionValue(entry.getKey(), entry.getValue());
                            }
                        }
                    }
                    int testCount = tests.size();
                    for (int i = 0; i < testCount; i++) {
                        IRemoteTest test = tests.get(i);
                        if (test instanceof IShardableTest) {
                            Collection<IRemoteTest> ts = ((IShardableTest) test).split();
                            for (IRemoteTest t : ts) {
                                addModuleDef(name, abi, t, pathArg);
                            }
                        } else {
                            addModuleDef(name, abi, test, pathArg);
                        }
                    }
                }
            } catch (ConfigurationException e) {
                throw new RuntimeException(String.format("error parsing config file: %s",
                        configFile.getName()), e);
            }
        }
    }

    private static void addFilters(List<String> stringFilters,
            Map<String, List<TestFilter>> filters, Set<IAbi> abis) {
        for (String filterString : stringFilters) {
            TestFilter filter = TestFilter.createFrom(filterString);
            String abi = filter.getAbi();
            if (abi == null) {
                for (IAbi a : abis) {
                    addFilter(a.getName(), filter, filters);
                }
            } else {
                addFilter(abi, filter, filters);
            }
        }
    }

    private static void addFilter(String abi, TestFilter filter,
            Map<String, List<TestFilter>> filters) {
        getFilter(filters, AbiUtils.createId(abi, filter.getName())).add(filter);
    }

    private static List<TestFilter> getFilter(Map<String, List<TestFilter>> filters, String id) {
        List<TestFilter> fs = filters.get(id);
        if (fs == null) {
            fs = new ArrayList<>();
            filters.put(id, fs);
        }
        return fs;
    }

    private void addModuleDef(String name, IAbi abi, IRemoteTest test,
            String[] configPaths) throws ConfigurationException {
        // Invokes parser to process the test module config file
        IConfiguration config = mConfigFactory.createConfigurationFromArgs(configPaths);
        addModuleDef(new ModuleDef(name, abi, test, config.getTargetPreparers()));
    }

    private void addModuleDef(IModuleDef moduleDef) {
        String id = moduleDef.getId();
        boolean includeModule = mIncludeAll;
        for (TestFilter include : getFilter(mIncludeFilters, id)) {
            String test = include.getTest();
            if (test != null) {
                // We're including a subset of tests
                moduleDef.addIncludeFilter(test);
            }
            includeModule = true;
        }
        for (TestFilter exclude : getFilter(mExcludeFilters, id)) {
            String test = exclude.getTest();
            if (test != null) {
                // Excluding a subset of tests, so keep module but give filter
                moduleDef.addExcludeFilter(test);
            } else {
                // Excluding all tests in the module so just remove the whole thing
                includeModule = false;
            }
        }
        if (includeModule) {
            Set<String> tokens = moduleDef.getTokens();
            if (tokens == null || tokens.isEmpty()) {
                mRemainingModules.add(moduleDef);
            } else {
                mRemainingWithTokens.add(moduleDef);
            }
            float numModules = mRemainingModules.size() + mRemainingWithTokens.size();
            mModulesPerShard = (int) ((numModules / mShards) + 0.5f); // Round up
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
    public synchronized List<IModuleDef> getModules(String serial) {
        Set<String> tokens = mDeviceTokens.get(serial);
        List<IModuleDef> modules = getModulesWithTokens(tokens);
        int diff = mModulesPerShard - modules.size();
        if (diff > 0) {
            modules.addAll(getModules(diff));
        }
        mSerials.add(serial);
        if (mSerials.size() == mShards) {
            // All shards have been given their workload.
            if (!mRemainingWithTokens.isEmpty()) {
                CLog.logAndDisplay(LogLevel.INFO, "Device Tokens:");
                for (String s : mDeviceTokens.keySet()) {
                    CLog.logAndDisplay(LogLevel.INFO, "%s: %s", s, mDeviceTokens.get(s));
                }
                CLog.logAndDisplay(LogLevel.INFO, "Module Tokens:");
                for (IModuleDef module : mRemainingWithTokens) {
                    CLog.logAndDisplay(LogLevel.INFO, "%s: %s", module.getId(), module.getTokens());
                }
                throw new IllegalArgumentException("Not all modules could be scheduled.");
            }
        }
        CLog.logAndDisplay(LogLevel.INFO, "%s: %s", serial, modules);
        return modules;
    }

    /**
     * Iterates through the remaining tests that require tokens and if the device has all the
     * required tokens it will queue that module to run on that device, else the module gets put
     * back into the list.
     */
    private List<IModuleDef> getModulesWithTokens(Set<String> tokens) {
        List<IModuleDef> modules = new ArrayList<>();
        if (tokens != null) {
            Set<IModuleDef> copy = mRemainingWithTokens;
            mRemainingWithTokens = new HashSet<>();
            for (IModuleDef module : copy) {
                // If a device has all the tokens required by the module then it can run it.
                if (tokens.containsAll(module.getTokens())) {
                    modules.add(module);
                } else {
                    mRemainingWithTokens.add(module);
                }
            }
        }
        return modules;
    }

    /**
     * Returns a {@link List} of modules that do not require tokens.
     */
    private List<IModuleDef> getModules(int count) {
        int size = mRemainingModules.size();
        if (count >= size) {
            count = size;
        }
        Set<IModuleDef> copy = mRemainingModules;
        mRemainingModules = new HashSet<>();
        List<IModuleDef> modules = new ArrayList<>();
        for (IModuleDef module : copy) {
            // Give 'count' modules to this shard and then put the rest back in the queue.
            if (count > 0) {
                modules.add(module);
                count--;
            } else {
                mRemainingModules.add(module);
            }
        }
        return modules;
    }

    /**
     * @return the {@link List} of modules whose name contains the given pattern.
     */
    public static List<String> getModuleNamesMatching(File directory, String pattern) {
        String[] names = directory.list(new NameFilter(pattern));
        List<String> modules = new ArrayList<String>(names.length);
        for (String name : names) {
            int index = name.indexOf(CONFIG_EXT);
            if (index > 0) {
                modules.add(name.substring(0, index));
            }
        }
        return modules;
    }

    private static void putArgs(List<String> args, Map<String, Map<String, String>> argsMap) {
        for (String arg : args) {
            String[] parts = arg.split(":");
            String target = parts[0];
            String key = parts[1];
            String value = parts[2];
            Map<String, String> map = argsMap.get(target);
            if (map == null) {
                map = new HashMap<>();
                argsMap.put(target, map);
            }
            map.put(key, value);
        }
    }
}
