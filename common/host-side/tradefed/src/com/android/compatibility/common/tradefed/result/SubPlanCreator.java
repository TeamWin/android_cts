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
package com.android.compatibility.common.tradefed.result;

import com.android.compatibility.common.tradefed.build.CompatibilityBuildHelper;
import com.android.compatibility.common.tradefed.testtype.CompatibilityTest;
import com.android.compatibility.common.tradefed.testtype.ISubPlan;
import com.android.compatibility.common.tradefed.testtype.SubPlan;
import com.android.compatibility.common.tradefed.util.OptionHelper;
import com.android.compatibility.common.util.IInvocationResult;
import com.android.compatibility.common.util.ResultHandler;
import com.android.compatibility.common.util.TestFilter;
import com.android.ddmlib.Log.LogLevel;
import com.android.tradefed.config.ArgsOptionParser;
import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.Option.Importance;
import com.android.tradefed.log.LogUtil.CLog;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Class for creating subplans from compatibility result XML.
 */
public class SubPlanCreator {

    private static final String PASSED = "passed";
    private static final String FAILED = "failed";
    private static final String NOT_EXECUTED = "not_executed";

    @Option (name = "name", shortName = 'n', description = "the name of the subplan to create",
            importance=Importance.IF_UNSET)
    private String mSubPlanName = null;

    @Option (name = "session", shortName = 's', description = "the session id to derive from",
            importance=Importance.IF_UNSET)
    private Integer mSessionId = null;

    @Option (name = "result", shortName = 'r',
            description = "the result type to include. One of passed, failed, not_executed."
            + " Option may be repeated",
            importance=Importance.IF_UNSET)
    private Set<String> mResultFilterStrings = new HashSet<String>();

    @Option(name = CompatibilityTest.INCLUDE_FILTER_OPTION,
            description = "the include module filters to apply.",
            importance = Importance.NEVER)
    private List<String> mIncludeFilters = new ArrayList<>();

    @Option(name = CompatibilityTest.EXCLUDE_FILTER_OPTION,
            description = "the exclude module filters to apply.",
            importance = Importance.NEVER)
    private List<String> mExcludeFilters = new ArrayList<>();

    @Option(name = CompatibilityTest.MODULE_OPTION, shortName = 'm',
            description = "the test module to run.",
            importance = Importance.NEVER)
    private String mModuleName = null;

    @Option(name = CompatibilityTest.TEST_OPTION, shortName = 't',
            description = "the test to run.",
            importance = Importance.NEVER)
    private String mTestName = null;

    @Option(name = CompatibilityTest.ABI_OPTION, shortName = 'a',
            description = "the abi to test.",
            importance = Importance.IF_UNSET)
    private String mAbiName = null;

    File mSubPlanFile = null;
    IInvocationResult mResult = null;

    /**
     * Create an empty {@link SubPlanCreator}.
     * <p/>
     * All {@link Option} fields must be populated via
     * {@link com.android.tradefed.config.ArgsOptionParser}
     */
    public SubPlanCreator() {}

    /**
     * Create a {@link SubPlanCreator} using the specified option values.
     */
    public SubPlanCreator(String name, int session, Collection<String> resultFilterStrings) {
        mSubPlanName = name;
        mSessionId = session;
        mResultFilterStrings.addAll(resultFilterStrings);
    }

    /**
     * Create and serialize a subplan derived from a result.
     * <p/>
     * {@link Option} values must all be set before this is called.
     * @return serialized subplan file.
     * @throws ConfigurationException
     */
    public File createAndSerializeSubPlan(CompatibilityBuildHelper buildHelper)
            throws ConfigurationException {
        ISubPlan subPlan = createSubPlan(buildHelper);
        if (subPlan != null) {
            try {
                subPlan.serialize(new BufferedOutputStream(new FileOutputStream(mSubPlanFile)));
                CLog.logAndDisplay(LogLevel.INFO, "Created subplan \"%s\" at %s",
                        mSubPlanName, mSubPlanFile.getAbsolutePath());
                return mSubPlanFile;
            } catch (IOException e) {
                CLog.e("Failed to create plan file %s", mSubPlanFile.getAbsolutePath());
                CLog.e(e);
            }
        }
        return null;
    }

    /**
     * Create a subplan derived from a result.
     * <p/>
     * {@link Option} values must all be set before this is called.
     *
     * @param build
     * @return subplan
     * @throws ConfigurationException
     */
    public ISubPlan createSubPlan(CompatibilityBuildHelper buildHelper)
            throws ConfigurationException {
        setupFields(buildHelper);
        ISubPlan subPlan = new SubPlan();
        // At least one of the following three is true
        boolean notExecuted = mResultFilterStrings.contains(NOT_EXECUTED);
        boolean passed = mResultFilterStrings.contains(PASSED);
        boolean failed = mResultFilterStrings.contains(FAILED);
        if (notExecuted) {
            // if including not_executed tests, include filters from previous session to
            // track which tests must run
            subPlan.addAllIncludeFilters(new HashSet<String>(mIncludeFilters));
            subPlan.addAllExcludeFilters(new HashSet<String>(mExcludeFilters));
            if (mModuleName != null) {
                subPlan.addIncludeFilter(
                        new TestFilter(mAbiName, mModuleName, mTestName).toString());
            }
            if (!passed) {
                subPlan.excludePassed(mResult);
            }
            if (!failed) {
                subPlan.excludeFailed(mResult);
            }
        } else {
            // if only including executed tests, add each filter explicitly without filters from
            // previous session
            if (passed) {
                subPlan.includePassed(mResult);
            }
            if (failed) {
                subPlan.includeFailed(mResult);
            }
        }

        return subPlan;
    }

    /**
     * Ensure that all {@Option}s and fields are populated with valid values.
     * @param buildHelper
     * @throws ConfigurationException if any option has an invalid value
     */
    private void setupFields(CompatibilityBuildHelper buildHelper) throws ConfigurationException {
        if (mSessionId == null) {
            throw new ConfigurationException("Missing --session argument");
        }
        try {
            mResult = ResultHandler.findResult(buildHelper.getResultsDir(), mSessionId);
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
        if (mResult == null) {
            throw new IllegalArgumentException(String.format(
                    "Could not find session with id %d", mSessionId));
        }

        String retryCommandLineArgs = mResult.getCommandLineArgs();
        if (retryCommandLineArgs != null) {
            try {
                // parse the command-line string from the result file and set options
                ArgsOptionParser parser = new ArgsOptionParser(this);
                parser.parse(OptionHelper.getValidCliArgs(retryCommandLineArgs, this));
            } catch (ConfigurationException e) {
                throw new RuntimeException(e);
            }
        }

        if (mResultFilterStrings.isEmpty()) {
            // add all valid values, include all tests of all statuses
            mResultFilterStrings.addAll(
                    new HashSet<String>(Arrays.asList(PASSED, FAILED, NOT_EXECUTED)));
        }
        // validate all test status values
        for (String filterString : mResultFilterStrings) {
            if (!filterString.equals(PASSED)
                    && !filterString.equals(FAILED)
                    && !filterString.equals(NOT_EXECUTED)) {
                throw new ConfigurationException(String.format("result filter string %s invalid",
                        filterString));
            }
        }

        if (mSubPlanName == null) {
            mSubPlanName = createPlanName();
        }
        try {
            mSubPlanFile = new File(buildHelper.getSubPlansDir(), mSubPlanName + ".xml");
            if (mSubPlanFile.exists()) {
                throw new ConfigurationException(String.format("Subplan %s already exists",
                        mSubPlanName));
            }
        } catch (IOException e) {
            throw new RuntimeException("Could not find subplans directory");
        }
    }

    private String createPlanName() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.join("_", mResultFilterStrings));
        sb.append(Integer.toString(mSessionId));
        return sb.toString();
    }
}
