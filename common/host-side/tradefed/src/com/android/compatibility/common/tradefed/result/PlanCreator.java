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
package com.android.compatibility.common.tradefed.result;

import com.android.compatibility.common.tradefed.build.BuildHelper;
import com.android.compatibility.common.tradefed.testtype.ITestPlan;
import com.android.compatibility.common.tradefed.testtype.TestPlan;
import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.Option.Importance;

import java.io.File;
import java.util.Arrays;
import java.util.List;

/**
 * Class for creating test plans from Compatibility result XML.
 */
public class PlanCreator {

    @Option (name = "plan", shortName = 'p', description = "the name of the plan to create",
            importance=Importance.IF_UNSET)
    private String mPlanName = null;

    @Option (name = "session", shortName = 's', description = "the session id to derive from",
            importance=Importance.IF_UNSET)
    private Integer mSessionId = null;

    private List<TestStatus> mResultFilters = null;

    private IInvocationResult mResult = null;

    private File mPlanFile;

    /**
     * Create an empty {@link PlanCreator}.
     * <p/>
     * All {@link Option} fields must be populated via
     * {@link com.android.tradefed.config.ArgsOptionParser}
     */
    public PlanCreator() {
    }

    /**
     * Create a {@link PlanCreator} using the specified option values.
     */
    public PlanCreator(String planName, int session, TestStatus... result) {
        mPlanName = planName;
        mSessionId = session;
        mResultFilters = Arrays.asList(result);
    }

    /**
     * Create a test plan derived from a result.
     * <p/>
     * {@link Option} values must all be set before this is called.
     *
     * @param build
     * @return test plan
     * @throws ConfigurationException
     */
    public ITestPlan createDerivedPlan(BuildHelper build) throws ConfigurationException {
        checkFields(build);
        ITestPlan derivedPlan = new TestPlan(mPlanName);
        for (IModuleResult module : mResult.getModules()) {
            String moduleId = module.getId();
            derivedPlan.addModule(moduleId);
            for (TestStatus status : mResultFilters) {
                for (IResult result : module.getResults(status)) {
                    derivedPlan.addModuleOption(moduleId, "test", result.getName());
                }
            }
        }
        return derivedPlan;
    }

    /**
     * Check that all {@Option}s have been populated with valid values.
     * @param build
     * @throws ConfigurationException if any option has an invalid value
     */
    private void checkFields(BuildHelper build) throws ConfigurationException {
        if (mSessionId == null) {
            throw new ConfigurationException("Missing --session argument");
        }
        IInvocationResultRepo repo = new InvocationResultRepo(build.getResultsDir());
        mResult = repo.getResult(mSessionId);
        if (mResult == null) {
            throw new ConfigurationException(String.format("Could not find session with id %d",
                    mSessionId));
        }
        if (mPlanName == null) {
            throw new ConfigurationException("Missing --plan argument");
        }
        mPlanFile = new File(build.getPlansDir(), mPlanName);
        if (mPlanFile.exists()) {
            throw new ConfigurationException(String.format("Test plan %s already exists",
                    mPlanName));
        }
    }
}
