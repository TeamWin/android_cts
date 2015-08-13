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

import com.android.tradefed.build.FolderBuildInfo;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.HashMap;
import java.util.Map;

/**
 * A simple {@link FolderBuildInfo} that uses a pre-existing Compatibility install.
 */
public class CompatibilityBuildInfo extends FolderBuildInfo {

    /** The root location of the extracted Compatibility package */
    private final File mDir;
    private final String mSuiteName;
    private final String mSuiteFullName;
    private final String mSuiteVersion;
    private final String mSuitePlan;
    private Map<String, String> mConfigHashes = new HashMap<>();

    /**
     * Creates a {@link CompatibilityBuildInfo} containing information about the suite and
     * invocation being run.
     * @param buildId The id of the build in which this compatibility suite was made.
     * @param suiteName The common name for the suite, often an abbreviation eg "CTS".
     * @param suiteFullName The full name for the suite, eg "Compatibility Test Suite".
     * @param suiteVersion The version string of this suite, eg "5.0.2_r8".
     * @param suitePlan The suite plan to run in this invocation, found in the jar under "config/".
     * @param rootDir the parent folder that contains the compatibility installation.
     */
    public CompatibilityBuildInfo(String buildId, String suiteName, String suiteFullName,
            String suiteVersion, String suitePlan, File rootDir) {
        super(buildId, suiteName, suitePlan);
        mSuiteName = suiteName;
        mSuiteFullName = suiteFullName;
        mSuiteVersion = suiteVersion;
        mSuitePlan = suitePlan;
        setRootDir(rootDir);
        mDir = new File(rootDir, String.format("android-%s", mSuiteName.toLowerCase()));
    }

    public String getSuiteName() {
        return mSuiteName;
    }

    public String getSuiteFullName() {
        return mSuiteFullName;
    }

    public String getSuiteVersion() {
        return mSuiteVersion;
    }

    public String getSuitePlan() {
        return mSuitePlan;
    }

    public void addDynamicConfig(String moduleName, String hash) {
        mConfigHashes.put(moduleName, hash);
    }

    public Map<String, String> getDynamicConfigHashes() {
        return mConfigHashes;
    }

    /**
     * @return a {@link File} representing the "android-<suite>" folder of the Compatibility
     * installation
     * @throws FileNotFoundException if the directory does not exist
     */
    public File getDir() throws FileNotFoundException {
        if (!mDir.exists()) {
            throw new FileNotFoundException(String.format(
                    "Compatibility install folder %s does not exist",
                    mDir.getAbsolutePath()));
        }
        return mDir;
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
