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

import java.io.File;
import java.io.FileNotFoundException;

/**
 * Helper class for retrieving files from the Compatibility installation.
 */
public class BuildHelper {

    /** The root location of the extracted Compatibility package */
    private final File mRootDir;
    private final File mDir;
    private String mBuildId;
    private String mSuiteName;
    private String mSuiteFullName;
    private String mSuiteVersion;

    /**
     * Creates a {@link BuildHelper}.
     * @param buildId
     * @param suiteName
     * @param suiteFullName
     * @param suiteVersion
     * @param rootDir the parent folder that contains the compatibility installation.
     * @throws IllegalArgumentException if provided directory does not contain a valid Compatibility
     * installation.
     */
    public BuildHelper(String buildId, String suiteName, String suiteFullName, String suiteVersion,
            File rootDir) {
        mBuildId = buildId;
        mSuiteName = suiteName;
        mSuiteFullName = suiteFullName;
        mSuiteVersion = suiteVersion;
        mRootDir = rootDir;
        mDir = new File(mRootDir, String.format("android-%s", mSuiteName.toLowerCase()));
        try {
            validateStructure();
        } catch (FileNotFoundException e) {
            throw new IllegalArgumentException("Invalid build provided.", e);
        }
    }

    /**
     * Creates a {@link BuildHelper}.
     * @param build
     * @throws IllegalArgumentException if provided directory does not contain a valid Compatibility
     * installation.
     */
    public BuildHelper(CompatibilityBuildInfo build) {
        this(build.getBuildId(), build.getSuiteName(), build.getSuiteFullName(),
                build.getSuiteVersion(), build.getRootDir());
    }

    /**
     * @return a {@link File} representing the parent folder of the Compatibility installation
     */
    public File getRootDir() {
        return mRootDir;
    }

    /**
     * @return a {@link File} representing the "android-<suite>" folder of the Compatibility
     * installation
     */
    public File getDir() {
        return mDir;
    }

    private File getRepositoryDir() {
        return new File(getDir(), "repository");
    }

    /**
     * @return a {@link File} representing the results directory.
     */
    public File getResultsDir() {
        return new File(getRepositoryDir(), "results");
    }

    /**
     * @return a {@link File} representing the directory to store result logs.
     */
    public File getLogsDir() {
        return new File(getRepositoryDir(), "logs");
    }

    /**
     * @return a {@link File} representing the test modules directory
     */
    public File getTestsDir() {
        return new File(getRepositoryDir(), "testcases");
    }

    /**
     * @return a {@link File} representing the test plan directory
     */
    public File getPlansDir() {
        return new File(getRepositoryDir(), "plans");
    }

    /**
     * Check the validity of the Compatibility build file system structure.
     * @throws FileNotFoundException if any major directories are missing
     */
    public void validateStructure() throws FileNotFoundException {
        if (!getDir().exists()) {
            throw new FileNotFoundException(String.format(
                    "Compatibility install folder %s does not exist",
                    getDir().getAbsolutePath()));
        }
        if (!getTestsDir().exists()) {
            throw new FileNotFoundException(String.format(
                    "Compatibility tests folder %s does not exist",
                    getTestsDir().getAbsolutePath()));
        }
        if (!getPlansDir().exists()) {
            throw new FileNotFoundException(String.format(
                    "Compatibility plans folder %s does not exist",
                    getPlansDir().getAbsolutePath()));
        }
    }

    public String getBuildId() {
        return mBuildId;
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

}