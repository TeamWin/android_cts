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

import com.android.compatibility.common.tradefed.build.CompatibilityBuildInfo;
import com.android.compatibility.common.tradefed.result.IInvocationResultRepo;
import com.android.compatibility.common.tradefed.result.InvocationResultRepo;
import com.android.compatibility.common.util.AbiUtils;
import com.android.compatibility.common.util.ICaseResult;
import com.android.compatibility.common.util.IInvocationResult;
import com.android.compatibility.common.util.IModuleResult;
import com.android.compatibility.common.util.ITestResult;
import com.android.compatibility.common.util.TestFilter;
import com.android.compatibility.common.util.TestStatus;
import com.android.ddmlib.Log.LogLevel;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.Option.Importance;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.config.OptionCopier;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.testtype.IAbi;
import com.android.tradefed.testtype.IBuildReceiver;
import com.android.tradefed.testtype.IDeviceTest;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.testtype.IShardableTest;
import com.android.tradefed.util.AbiFormatter;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * A Test for running Compatibility Suites
 */
@OptionClass(alias="compatibility-test")
public class CompatibilityTest implements IDeviceTest, IShardableTest, IBuildReceiver {

    private static final String INCLUDE_FILTER_OPTION = "include-filter";
    private static final String EXCLUDE_FILTER_OPTION = "exclude-filter";
    private static final String MODULE_OPTION = "module";
    private static final String TEST_OPTION = "test";
    public static final String RETRY_OPTION = "retry";
    private static final String ABI_OPTION = "abi";
    private static final String SHARD_OPTION = "shard";
    private static final TestStatus[] RETRY_TEST_STATUS = new TestStatus[] {
        TestStatus.FAIL,
        TestStatus.NOT_EXECUTED
    };

    @Option(name = INCLUDE_FILTER_OPTION,
            description = "the include module filters to apply.",
            importance = Importance.ALWAYS)
    private List<String> mIncludeFilters = new ArrayList<>();

    @Option(name = EXCLUDE_FILTER_OPTION,
            description = "the exclude module filters to apply.",
            importance = Importance.ALWAYS)
    private List<String> mExcludeFilters = new ArrayList<>();

    @Option(name = MODULE_OPTION,
            shortName = 'm',
            description = "the test module to run.",
            importance = Importance.IF_UNSET)
    private String mModuleName = null;

    @Option(name = TEST_OPTION,
            shortName = 't',
            description = "the test run.",
            importance = Importance.IF_UNSET)
    private String mTestName = null;

    @Option(name = RETRY_OPTION,
            shortName = 'r',
            description = "retry a previous session.",
            importance = Importance.IF_UNSET)
    private Integer mRetrySessionId = null;

    @Option(name = ABI_OPTION,
            shortName = 'a',
            description = "the abi to test.",
            importance = Importance.IF_UNSET)
    private String mAbiName = null;

    @Option(name = SHARD_OPTION,
            description = "split the modules up to run on multiple devices concurrently.")
    private int mShards = 1;

    private int mShardAssignment;
    private int mTotalShards;
    private ITestDevice mDevice;
    private CompatibilityBuildInfo mBuild;
    private List<IModuleDef> mModules = new ArrayList<>();
    private int mLastModuleIndex = 0;

    /**
     * Create a new {@link CompatibilityTest} that will run the default list of modules.
     */
    public CompatibilityTest() {
        this(0 /*shardAssignment*/, 1 /*totalShards*/);
    }

    /**
     * Create a new {@link CompatibilityTest} that will run a sublist of modules.
     */
    public CompatibilityTest(int shardAssignment, int totalShards) {
        if (shardAssignment < 0) {
            throw new IllegalArgumentException(
                "shardAssignment cannot be negative. found:" + shardAssignment);
        }
        if (totalShards < 1) {
            throw new IllegalArgumentException(
                "shardAssignment must be at least 1. found:" + totalShards);
        }
        mShardAssignment = shardAssignment;
        mTotalShards = totalShards;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ITestDevice getDevice() {
        return mDevice;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setDevice(ITestDevice device) {
        mDevice = device;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setBuild(IBuildInfo buildInfo) {
        mBuild = (CompatibilityBuildInfo) buildInfo;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        try {
            Set<IAbi> abiSet = getAbis();
            if (abiSet == null || abiSet.isEmpty()) {
                if (mAbiName == null) {
                    throw new IllegalArgumentException("Could not get device's ABIs");
                } else {
                    throw new IllegalArgumentException(String.format("Device %s does't support %s",
                            mDevice.getSerialNumber(), mAbiName));
                }
            }
            CLog.logAndDisplay(LogLevel.INFO, "ABIs: %s", abiSet);
            setupTestModules(abiSet);

            // Always collect the device info, even for resumed runs, since test will likely be
            // running on a different device
            //collectDeviceInfo(getDevice(), mBuildHelper, listener);

            int moduleCount = mModules.size();
            CLog.logAndDisplay(LogLevel.INFO, "Start test run of %d module%s", moduleCount,
                    (moduleCount > 1) ? "s" : "");

            for (int i = mLastModuleIndex; i < moduleCount; i++) {
                IModuleDef module = mModules.get(i);
                module.setBuild(mBuild);
                module.setDevice(mDevice);
                module.run(listener);
                // Track of the last complete test package index for resume
                mLastModuleIndex = i;
            }
        } catch (DeviceNotAvailableException e) {
            // Pass up
            throw e;
        } catch (RuntimeException e) {
            CLog.logAndDisplay(LogLevel.ERROR, "Exception: %s", e.getMessage());
            CLog.e(e);
        } catch (Error e) {
            CLog.logAndDisplay(LogLevel.ERROR, "Error: %s", e.getMessage());
            CLog.e(e);
        }
    }

    /**
     * Set {@code mModules} to the list of test modules to run.
     * @param abis
     */
    private void setupTestModules(Set<IAbi> abis) {
        if (!mModules.isEmpty()) {
            CLog.d("Resume tests using existing module list");
            return;
        }
        if (mRetrySessionId != null) {
            // We're retrying so clear the filters
            mIncludeFilters.clear();
            mExcludeFilters.clear();
            mModuleName = null;
            mTestName = null;
            // Load the invocation result
            IInvocationResultRepo repo;
            IInvocationResult result = null;
            try {
                repo = new InvocationResultRepo(mBuild.getResultsDir());
                result = repo.getResult(mRetrySessionId);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
            if (result == null) {
                throw new IllegalArgumentException(String.format(
                        "Could not find session with id %d", mRetrySessionId));
            }
            // Append each test that failed or was not executed to the filters
            for (IModuleResult module : result.getModules()) {
                for (ICaseResult cr : module.getResults()) {
                    for (TestStatus status : RETRY_TEST_STATUS) {
                        for (ITestResult r : cr.getResults(status)) {
                            // Create the filter for the test to be run.
                            mIncludeFilters.add(new TestFilter(module.getAbi(), module.getName(),
                                    r.getFullName()).toString());
                        }
                    }
                }
            }
        }
        if (mModuleName != null) {
            mIncludeFilters.clear();
            mIncludeFilters.add(new TestFilter(mAbiName, mModuleName, mTestName).toString());
            if (mTestName != null) {
                // We're filtering it down to the lowest level, no need to give excludes
                mExcludeFilters.clear();
            } else {
                // If we dont specify a test name, we only want to run this module with any
                // exclusions defined by the plan as long as they dont exclude the whole module.
                List<String> excludeFilters = new ArrayList<>();
                for (String excludeFilter : mExcludeFilters) {
                    TestFilter filter = TestFilter.createFrom(excludeFilter);
                    String name = filter.getName();
                    // Add the filter if it applies to this module, and it has a test name
                    if ((mModuleName.equals(name) || mModuleName.matches(name) ||
                            name.matches(mModuleName)) && filter.getTest() != null) {
                        excludeFilters.add(excludeFilter);
                    }
                }
                mExcludeFilters = excludeFilters;
            }
        }
        // Collect ALL tests
        IModuleRepo testRepo = new ModuleRepo(mBuild, abis);
        List<IModuleDef> modules = testRepo.getModules(mIncludeFilters, mExcludeFilters);
        // Filter by shard
        int numTestmodules = modules.size();
        int totalShards = Math.min(mTotalShards, numTestmodules);

        mModules.clear();
        for (int i = mShardAssignment; i < numTestmodules; i += totalShards) {
            mModules.add(modules.get(i));
        }
    }

    /**
     * Gets the set of ABIs supported by both Compatibility and the device under test
     * @return The set of ABIs to run the tests on
     * @throws DeviceNotAvailableException
     */
    Set<IAbi> getAbis() throws DeviceNotAvailableException {
        Set<IAbi> abis = new HashSet<>();
        for (String abi : AbiFormatter.getSupportedAbis(mDevice, "")) {
            // Only test against ABIs supported by Compatibility, and if the --abi option was given,
            // it must match.
            if (AbiUtils.isAbiSupportedByCompatibility(abi)
                    && (mAbiName == null || mAbiName.equals(abi))) {
                abis.add(new Abi(abi, AbiUtils.getBitness(abi)));
            }
        }
        return abis;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Collection<IRemoteTest> split() {
        if (mShards <= 1) {
            return null;
        }

        List<IRemoteTest> shardQueue = new LinkedList<>();
        for (int shardAssignment = 0; shardAssignment < mShards; shardAssignment++) {
            CompatibilityTest test = new CompatibilityTest(shardAssignment, mShards /* total */);
            OptionCopier.copyOptionsNoThrow(this, test);
            // Set the shard count because the copy option on the previous line copies
            // over the mShard value
            test.mShards = 0;
            shardQueue.add(test);
        }

        return shardQueue;
    }

}
