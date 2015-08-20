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

import com.android.tradefed.build.IFolderBuildInfo;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.HashMap;
import java.util.Map;

/**
 * A simple helper that stores and retrieves information from a {@link IFolderBuildInfo}.
 */
public class CompatibilityBuildHelper {

    private static final String ROOT_DIR = "CTS_ROOT_DIR";
    private static final String SUITE_NAME = "CTS_SUITE_NAME";
    private static final String SUITE_FULL_NAME = "CTS_SUITE_FULL_NAME";
    private static final String SUITE_VERSION = "CTS_SUITE_VERSION";
    private static final String CONFIG_PATH_PREFIX = "DYNAMIC_CONFIG_FILE:";
    private static final String DYNAMIC_CONFIG_OVERRIDE_URL = "DYNAMIC_CONFIG_OVERRIDE_URL";

    private final IFolderBuildInfo mBuildInfo;
    private boolean mInitialized = false;

    /**
     * Creates a {@link CompatibilityBuildHelper} wrapping the given {@link IFolderBuildInfo}.
     */
    public CompatibilityBuildHelper(IFolderBuildInfo buildInfo) {
        mBuildInfo = buildInfo;
    }

    /**
     * Initializes the {@link IFolderBuildInfo} from the manifest.
     */
    public void init() {
        if (mInitialized) {
            return;
        }
        mInitialized = true;
        Package pkg = Package.getPackage("com.android.compatibility.tradefed.command");
        String suiteFullName = pkg.getSpecificationTitle();
        String suiteName = pkg.getSpecificationVendor();
        String suiteVersion = pkg.getSpecificationVersion();
        String buildId = pkg.getImplementationVersion();
        mBuildInfo.setBuildId(buildId);
        mBuildInfo.addBuildAttribute(SUITE_NAME, suiteName);
        mBuildInfo.addBuildAttribute(SUITE_FULL_NAME, suiteFullName);
        mBuildInfo.addBuildAttribute(SUITE_VERSION, suiteVersion);
        String mRootDirPath = System.getProperty(String.format("%s_ROOT", suiteName));
        if (mRootDirPath == null || mRootDirPath.equals("")) {
            throw new IllegalArgumentException(
                    String.format("Missing install path property %s_ROOT", suiteName));
        }
        File rootDir = new File(mRootDirPath);
        mBuildInfo.addBuildAttribute(ROOT_DIR, rootDir.getAbsolutePath());
        mBuildInfo.setRootDir(rootDir);
    }

    /**
     * Initializes the {@link IFolderBuildInfo} from the manifest.
     */
    public void init(String dynamicConfigOverrideUrl) {
        init();
        if (dynamicConfigOverrideUrl != null) {
            mBuildInfo.addBuildAttribute(DYNAMIC_CONFIG_OVERRIDE_URL,
                    dynamicConfigOverrideUrl.replace("{suite-name}", getSuiteName()));
        }

    }

    public String getBuildId() {
        return mBuildInfo.getBuildId();
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
