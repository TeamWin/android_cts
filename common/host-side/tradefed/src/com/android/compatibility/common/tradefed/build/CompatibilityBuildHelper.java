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
package com.android.compatibility.common.tradefed.build;

import com.android.compatibility.SuiteInfo;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.build.IFolderBuildInfo;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.HashMap;
import java.util.Map;

/**
 * A simple helper that stores and retrieves information from a {@link IBuildInfo}.
 */
public class CompatibilityBuildHelper {

    private static final String ROOT_DIR = "ROOT_DIR";
    private static final String SUITE_BUILD = "SUITE_BUILD";
    private static final String SUITE_NAME = "SUITE_NAME";
    private static final String SUITE_FULL_NAME = "SUITE_FULL_NAME";
    private static final String SUITE_VERSION = "SUITE_VERSION";
    private static final String SUITE_PLAN = "SUITE_PLAN";
    private static final String RESULT_DIR = "RESULT_DIR";
    private static final String CONFIG_PATH_PREFIX = "DYNAMIC_CONFIG_FILE:";
    private static final String DYNAMIC_CONFIG_OVERRIDE_URL = "DYNAMIC_CONFIG_OVERRIDE_URL";
    private final IBuildInfo mBuildInfo;
    private boolean mInitialized = false;

    /**
     * Creates a {@link CompatibilityBuildHelper} wrapping the given {@link IBuildInfo}.
     */
    public CompatibilityBuildHelper(IBuildInfo buildInfo) {
        mBuildInfo = buildInfo;
    }

    /**
     * Initializes the {@link IBuildInfo} from the manifest.
     */
    public void init(String suitePlan, String dynamicConfigUrl) {
        if (mInitialized) {
            return;
        }
        mInitialized = true;
        mBuildInfo.addBuildAttribute(SUITE_BUILD, SuiteInfo.BUILD_NUMBER);
        mBuildInfo.addBuildAttribute(SUITE_NAME, SuiteInfo.NAME);
        mBuildInfo.addBuildAttribute(SUITE_FULL_NAME, SuiteInfo.FULLNAME);
        mBuildInfo.addBuildAttribute(SUITE_VERSION, SuiteInfo.VERSION);
        mBuildInfo.addBuildAttribute(SUITE_PLAN, suitePlan);
        String rootDirPath = null;
        if (mBuildInfo instanceof IFolderBuildInfo) {
            File rootDir = ((IFolderBuildInfo) mBuildInfo).getRootDir();
            if (rootDir != null) {
                rootDirPath = rootDir.getAbsolutePath();
            }
        }
        rootDirPath = System.getProperty(String.format("%s_ROOT", SuiteInfo.NAME), rootDirPath);
        if (rootDirPath == null || rootDirPath.trim().equals("")) {
            throw new IllegalArgumentException(
                    String.format("Missing install path property %s_ROOT", SuiteInfo.NAME));
        }
        File rootDir = new File(rootDirPath);
        if (!rootDir.exists()) {
            throw new IllegalArgumentException(
                    String.format("Root directory doesn't exist %s", rootDir.getAbsolutePath()));
        }
        mBuildInfo.addBuildAttribute(ROOT_DIR, rootDir.getAbsolutePath());
        if (dynamicConfigUrl != null && !dynamicConfigUrl.isEmpty()) {
            mBuildInfo.addBuildAttribute(DYNAMIC_CONFIG_OVERRIDE_URL,
                    dynamicConfigUrl.replace("{suite-name}", getSuiteName()));
        }
    }

    public String getSuiteBuild() {
        return mBuildInfo.getBuildAttributes().get(SUITE_BUILD);
    }

    public String getSuiteName() {
        return mBuildInfo.getBuildAttributes().get(SUITE_NAME);
    }

    public String getSuiteFullName() {
        return mBuildInfo.getBuildAttributes().get(SUITE_FULL_NAME);
    }

    public String getSuiteVersion() {
        return mBuildInfo.getBuildAttributes().get(SUITE_VERSION);
    }

    public String getSuitePlan() {
        return mBuildInfo.getBuildAttributes().get(SUITE_PLAN);
    }

    public String getDynamicConfigUrl() {
        return mBuildInfo.getBuildAttributes().get(DYNAMIC_CONFIG_OVERRIDE_URL);
    }

    public void addDynamicConfigFile(String moduleName, File configFile) {
        mBuildInfo.addBuildAttribute(CONFIG_PATH_PREFIX + moduleName, configFile.getAbsolutePath());
    }

    public Map<String, File> getDynamicConfigFiles() {
        Map<String, File> configMap = new HashMap<>();
        for (String key : mBuildInfo.getBuildAttributes().keySet()) {
            if (key.startsWith(CONFIG_PATH_PREFIX)) {
                configMap.put(key.substring(CONFIG_PATH_PREFIX.length()),
                        new File(mBuildInfo.getBuildAttributes().get(key)));
            }
        }
        return configMap;
    }

    /**
     * @return a {@link File} representing the directory holding the Compatibility installation
     * @throws FileNotFoundException if the directory does not exist
     */
    public File getRootDir() throws FileNotFoundException {
        File dir = new File(mBuildInfo.getBuildAttributes().get(ROOT_DIR));
        if (!dir.exists()) {
            throw new FileNotFoundException(String.format(
                    "Compatibility root directory %s does not exist",
                    dir.getAbsolutePath()));
        }
        return dir;
    }

    /**
     * @return a {@link File} representing the "android-<suite>" folder of the Compatibility
     * installation
     * @throws FileNotFoundException if the directory does not exist
     */
    public File getDir() throws FileNotFoundException {
        File dir = new File(getRootDir(), String.format("android-%s", getSuiteName().toLowerCase()));
        if (!dir.exists()) {
            throw new FileNotFoundException(String.format(
                    "Compatibility install folder %s does not exist",
                    dir.getAbsolutePath()));
        }
        return dir;
    }

    /**
     * @return a {@link File} representing the results directory.
     * @throws FileNotFoundException if the directory structure is not valid.
     */
    public File getResultsDir() throws FileNotFoundException {
        return new File(getDir(), "results");
    }

    /**
     * Sets the name of the current invocation's result directory.
     */
    public void setResultDir(String resultDir) {
        mBuildInfo.addBuildAttribute(RESULT_DIR, resultDir);
    }

    /**
     * @return a {@link File} representing the result directory of the current invocation.
     * @throws FileNotFoundException if the directory structure is not valid.
     */
    public File getResultDir() throws FileNotFoundException {
        return new File(getResultsDir(), mBuildInfo.getBuildAttributes().get(RESULT_DIR));
    }

    /**
     * @return a {@link File} representing the directory to store result logs.
     * @throws FileNotFoundException if the directory structure is not valid.
     */
    public File getLogsDir() throws FileNotFoundException {
        return new File(getDir(), "logs");
    }

    /**
     * @return a {@link File} representing the test modules directory.
     * @throws FileNotFoundException if the directory structure is not valid.
     */
    public File getTestsDir() throws FileNotFoundException {
        File testsDir = new File(getDir(), "testcases");
        if (!testsDir.exists()) {
            throw new FileNotFoundException(String.format(
                    "Compatibility tests folder %s does not exist",
                    testsDir.getAbsolutePath()));
        }
        return testsDir;
    }

}
