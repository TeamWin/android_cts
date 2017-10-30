/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.compatibility.common.tradefed.testtype.suite;

import com.android.compatibility.common.tradefed.build.CompatibilityBuildHelper;
import com.android.compatibility.common.tradefed.testtype.ISubPlan;
import com.android.compatibility.common.tradefed.testtype.SubPlan;
import com.android.compatibility.common.tradefed.testtype.retry.RetryFactoryTest;
import com.android.compatibility.common.util.TestFilter;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.Option.Importance;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.testtype.IAbi;
import com.android.tradefed.testtype.suite.ITestSuite;
import com.android.tradefed.util.ArrayUtil;
import com.android.tradefed.util.xml.AbstractXmlParser.ParseException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

/**
 * A Test for running Compatibility Test Suite with new suite system.
 */
@OptionClass(alias = "compatibility")
public class CompatibilityTestSuite extends ITestSuite {

    public static final String INCLUDE_FILTER_OPTION = "include-filter";
    public static final String EXCLUDE_FILTER_OPTION = "exclude-filter";
    public static final String SUBPLAN_OPTION = "subplan";
    public static final String MODULE_OPTION = "module";
    public static final String TEST_OPTION = "test";
    public static final char TEST_OPTION_SHORT_NAME = 't';
    private static final String MODULE_ARG_OPTION = "module-arg";
    private static final String TEST_ARG_OPTION = "test-arg";

    // TODO: remove this option when CompatibilityTest goes away
    @Option(name = RetryFactoryTest.RETRY_OPTION,
            shortName = 'r',
            description = "Copy of --retry from CompatibilityTest to prevent using it.")
    private Integer mRetrySessionId = null;

    @Option(name = SUBPLAN_OPTION,
            description = "the subplan to run",
            importance = Importance.IF_UNSET)
    private String mSubPlan;

    @Option(name = INCLUDE_FILTER_OPTION,
            description = "the include module filters to apply.",
            importance = Importance.ALWAYS)
    private Set<String> mIncludeFilters = new HashSet<>();

    @Option(name = EXCLUDE_FILTER_OPTION,
            description = "the exclude module filters to apply.",
            importance = Importance.ALWAYS)
    private Set<String> mExcludeFilters = new HashSet<>();

    @Option(name = MODULE_OPTION,
            shortName = 'm',
            description = "the test module to run.",
            importance = Importance.IF_UNSET)
    private String mModuleName = null;

    @Option(name = TEST_OPTION,
            shortName = TEST_OPTION_SHORT_NAME,
            description = "the test to run.",
            importance = Importance.IF_UNSET)
    private String mTestName = null;

    @Option(name = MODULE_ARG_OPTION,
            description = "the arguments to pass to a module. The expected format is"
                    + "\"<module-name>:<arg-name>:[<arg-key>:=]<arg-value>\"",
            importance = Importance.ALWAYS)
    private List<String> mModuleArgs = new ArrayList<>();

    @Option(name = TEST_ARG_OPTION,
            description = "the arguments to pass to a test. The expected format is"
                    + "\"<test-class>:<arg-name>:[<arg-key>:=]<arg-value>\"",
            importance = Importance.ALWAYS)
    private List<String> mTestArgs = new ArrayList<>();

    private ModuleRepoSuite mModuleRepo = new ModuleRepoSuite();
    private CompatibilityBuildHelper mBuildHelper;

    /**
     * {@inheritDoc}
     */
    @Override
    public LinkedHashMap<String, IConfiguration> loadTests() {
        if (mRetrySessionId != null) {
            throw new IllegalArgumentException("--retry cannot be specified with cts-suite.xml. "
                    + "Use 'run cts --retry <session id>' instead.");
        }
        try {
            setupFilters();
            Set<IAbi> abis = getAbis(getDevice());
            // Initialize the repository, {@link CompatibilityBuildHelper#getTestsDir} can
            // throw a {@link FileNotFoundException}
            return mModuleRepo.loadConfigs(mBuildHelper.getTestsDir(),
                    abis, mTestArgs, mModuleArgs, mIncludeFilters, mExcludeFilters);
        } catch (DeviceNotAvailableException | FileNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setBuild(IBuildInfo buildInfo) {
        super.setBuild(buildInfo);
        mBuildHelper = new CompatibilityBuildHelper(buildInfo);
    }

    /**
     * Sets the include/exclude filters up based on if a module name was given or whether this is a
     * retry run.
     */
    void setupFilters() throws FileNotFoundException {
        if (mSubPlan != null) {
            try {
                File subPlanFile = new File(mBuildHelper.getSubPlansDir(), mSubPlan + ".xml");
                if (!subPlanFile.exists()) {
                    throw new IllegalArgumentException(
                            String.format("Could not retrieve subplan \"%s\"", mSubPlan));
                }
                InputStream subPlanInputStream = new FileInputStream(subPlanFile);
                ISubPlan subPlan = new SubPlan();
                subPlan.parse(subPlanInputStream);
                mIncludeFilters.addAll(subPlan.getIncludeFilters());
                mExcludeFilters.addAll(subPlan.getExcludeFilters());
            } catch (ParseException e) {
                throw new RuntimeException(
                        String.format("Unable to find or parse subplan %s", mSubPlan), e);
            }
        }
        if (mModuleName != null) {
            List<String> modules = ModuleRepoSuite.getModuleNamesMatching(
                    mBuildHelper.getTestsDir(), mModuleName);
            if (modules.size() == 0) {
                throw new IllegalArgumentException(
                        String.format("No modules found matching %s", mModuleName));
            } else if (modules.size() > 1) {
                throw new IllegalArgumentException(String.format(
                        "Multiple modules found matching %s:\n%s\nWhich one did you mean?\n",
                        mModuleName, ArrayUtil.join("\n", modules)));
            } else {
                String moduleName = modules.get(0);
                checkFilters(mIncludeFilters, moduleName);
                checkFilters(mExcludeFilters, moduleName);
                mIncludeFilters.add(new TestFilter(getRequestedAbi(), moduleName, mTestName)
                        .toString());
            }
        } else if (mTestName != null) {
            throw new IllegalArgumentException(
                    "Test name given without module name. Add --module <module-name>");
        }
    }

    /* Helper method designed to remove filters in a list not applicable to the given module */
    private static void checkFilters(Set<String> filters, String moduleName) {
        Set<String> cleanedFilters = new HashSet<String>();
        for (String filter : filters) {
            if (moduleName.equals(TestFilter.createFrom(filter).getName())) {
                cleanedFilters.add(filter); // Module name matches, filter passes
            }
        }
        filters.clear();
        filters.addAll(cleanedFilters);
    }

    /**
     * Sets include-filters for the compatibility test
     */
    public void setIncludeFilter(Set<String> includeFilters) {
        mIncludeFilters.addAll(includeFilters);
    }

    /**
     * Sets exclude-filters for the compatibility test
     */
    public void setExcludeFilter(Set<String> excludeFilters) {
        mExcludeFilters.addAll(excludeFilters);
    }
}
