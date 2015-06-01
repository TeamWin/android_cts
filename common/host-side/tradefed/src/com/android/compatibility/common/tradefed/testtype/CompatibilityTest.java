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

import com.android.compatibility.common.tradefed.build.BuildHelper;
import com.android.compatibility.common.tradefed.build.CompatibilityBuildInfo;
import com.android.compatibility.common.tradefed.result.IModuleListener;
import com.android.compatibility.common.tradefed.result.ModuleListener;
import com.android.compatibility.common.tradefed.result.PlanCreator;
import com.android.compatibility.common.tradefed.result.TestStatus;
import com.android.compatibility.common.util.AbiUtils;
import com.android.compatibility.common.xml.XmlPlanHandler;
import com.android.ddmlib.Log.LogLevel;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.Option.Importance;
import com.android.tradefed.config.OptionCopier;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.targetprep.BuildError;
import com.android.tradefed.targetprep.ITargetCleaner;
import com.android.tradefed.targetprep.ITargetPreparer;
import com.android.tradefed.targetprep.TargetSetupError;
import com.android.tradefed.testtype.IAbi;
import com.android.tradefed.testtype.IAbiReceiver;
import com.android.tradefed.testtype.IBuildReceiver;
import com.android.tradefed.testtype.IDeviceTest;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.testtype.IShardableTest;
import com.android.tradefed.util.AbiFormatter;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A Test for running Compatibility Suites
 */
public class CompatibilityTest implements IDeviceTest, IShardableTest, IBuildReceiver {

    public static final String PLAN_OPTION = "plan";
    public static final String MODULE_OPTION = "module";
    public static final String CONTINUE_OPTION = "continue-session";
    public static final String RETRY_OPTION = "retry-session";
    public static final String ABI_OPTION = "abi";
    public static final String SHARD_OPTION = "shard";

    @Option(name = PLAN_OPTION,
            shortName = 'p',
            description = "the test plan to run.",
            importance = Importance.IF_UNSET)
    private String mPlanName = null;

    @Option(name = MODULE_OPTION,
            shortName = 'm',
            description = "the test module to run.",
            importance = Importance.IF_UNSET)
    private String mModuleName = null;

    @Option(name = CONTINUE_OPTION,
            shortName = 'c',
            description = "continue a previous session.",
            importance = Importance.IF_UNSET)
    private Integer mContinueSessionId = null;

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
    private BuildHelper mBuildHelper;
    private IBuildInfo mBuildInfo;
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
        mBuildHelper = new BuildHelper((CompatibilityBuildInfo) buildInfo);
        mBuildInfo = buildInfo;
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
                    throw new IllegalArgumentException(String.format("Device %s does not support %s",
                            getDevice().getSerialNumber(), mAbiName));
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
                IModuleListener moduleListener = new ModuleListener(module, listener);
                CLog.logAndDisplay(LogLevel.INFO, "Module: %s", module.getId());
                List<ITargetPreparer> preparers = module.getPreparers();
                List<IRemoteTest> tests = module.getTests();
                IAbi abi = module.getAbi();

                List<ITargetCleaner> cleaners = new ArrayList<>();
                // Setup
                for (ITargetPreparer preparer : preparers) {
                    CLog.d("Preparer: %s", preparer.getClass().getSimpleName());
                    if (preparer instanceof IAbiReceiver) {
                        ((IAbiReceiver) preparer).setAbi(abi);
                    }
                    if (preparer instanceof ITargetCleaner) {
                        cleaners.add((ITargetCleaner) preparer);
                    }
                    try {
                        preparer.setUp(getDevice(), mBuildInfo);
                    } catch (BuildError e) {
                        // This should only happen for flashing new build
                        CLog.e("Unexpected BuildError from preparer: %s",
                            preparer.getClass().getCanonicalName());
                    } catch (TargetSetupError e) {
                        // log preparer class then rethrow & let caller handle
                        CLog.e("TargetSetupError in preparer: %s",
                            preparer.getClass().getCanonicalName());
                        throw new RuntimeException(e);
                    }
                }
                // Run tests
                for (IRemoteTest test : tests) {
                    CLog.d("Test: %s", test.getClass().getSimpleName());
                    if (test instanceof IBuildReceiver) {
                        ((IBuildReceiver) test).setBuild(mBuildInfo);
                    }
                    if (test instanceof IDeviceTest) {
                        ((IDeviceTest) test).setDevice(getDevice());
                    }
                    if (test instanceof IAbiReceiver) {
                        ((IAbiReceiver) test).setAbi(abi);
                    }
                    test.run(moduleListener);
                }
                // Tear down - in reverse order
                Collections.reverse(cleaners);
                for (ITargetCleaner cleaner : cleaners) {
                    CLog.d("Cleaner: %s", cleaner.getClass().getSimpleName());
                    cleaner.tearDown(getDevice(), mBuildInfo, null);
                }
                // Track of the last complete test package index for resume
                mLastModuleIndex = i;
            }
        } catch (DeviceNotAvailableException e) {
            // Pass up
            throw e;
        } catch (RuntimeException e) {
            CLog.logAndDisplay(LogLevel.ERROR, "Exception: %s", e.getMessage());
        } catch (Error e) {
            CLog.logAndDisplay(LogLevel.ERROR, "Error: %s", e.getMessage());
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
        // Collect ALL tests
        IModuleRepo testRepo = new ModuleRepo(mBuildHelper.getTestsDir(), abis);
        List<IModuleDef> modules = new ArrayList<>(getModules(testRepo));
        // Note: run() relies on the fact that the list is reliably sorted for sharding purposes
        Collections.sort(modules);
        // Filter by shard
        int numTestmodules = modules.size();
        int totalShards = Math.min(mTotalShards, numTestmodules);

        mModules.clear();
        for (int i = mShardAssignment; i < numTestmodules; i += totalShards) {
            mModules.add(modules.get(i));
        }
    }

    /**
     * @return the {@link Set} of {@link IModuleDef}s to run
     */
    private Set<IModuleDef> getModules(IModuleRepo testRepo) {
        // use LinkedHashSet to have predictable iteration order
        Set<IModuleDef> moduleDefs = new LinkedHashSet<>();
        if (mPlanName != null) {
            CLog.i("Executing test plan %s", mPlanName);
            File planFile = new File(mBuildHelper.getPlansDir(), mPlanName);
            ITestPlan plan = XmlPlanHandler.parsePlan(mPlanName, planFile);
            if (plan == null) {
                throw new IllegalArgumentException(String.format(
                        "Could not find plan %s. Use 'list plans' to see available plans.",
                        mPlanName));
            }
            Map<String, List<IModuleDef>> modules = testRepo.getModulesByName();
            for (String test : plan.getModuleNames()) {
                if (!modules.containsKey(test)) {
                    CLog.e("Could not find test %s referenced in plan %s", test, mPlanName);
                } else {
                    moduleDefs.addAll(modules.get(test));
                }
            }
        } else if (mModuleName != null){
            CLog.i("Executing test module %s", mModuleName);
            Map<String, List<IModuleDef>> modules = testRepo.getModulesByName();
            if (!modules.containsKey(mModuleName)) {
                throw new IllegalArgumentException(String.format(
                        "Could not find module %s. Use 'list modules' to see available modules.",
                        mModuleName));
            }
            moduleDefs.addAll(modules.get(mModuleName));
        } else if (mContinueSessionId != null) {
            // create an in-memory derived plan that contains the notExecuted tests from previous
            // session use timestamp as plan name so it will hopefully be unique
            String uniquePlanName = Long.toString(System.currentTimeMillis());
            ITestPlan plan;
            try {
                plan = new PlanCreator(uniquePlanName, mContinueSessionId,
                        TestStatus.NOT_EXECUTED).createDerivedPlan(mBuildHelper);
                Map<String, List<IModuleDef>> modules = testRepo.getModulesByName();
                for (String test : plan.getModuleNames()) {
                    if (!modules.containsKey(test)) {
                        CLog.e("Could not find test %s", test);
                    } else {
                        moduleDefs.addAll(modules.get(test));
                    }
                }
            } catch (ConfigurationException e) {
                throw new IllegalStateException(String.format(
                        "Could not load session %s. Use 'list results' to see available sessions.",
                        mContinueSessionId));
            }
        } else if (mRetrySessionId != null) {
            // create an in-memory derived plan that contains the failed tests from previous
            // session use timestamp as plan name so it will hopefully be unique
            String uniquePlanName = Long.toString(System.currentTimeMillis());
            ITestPlan plan;
            try {
                plan = new PlanCreator(uniquePlanName, mRetrySessionId,
                        TestStatus.FAIL, TestStatus.NOT_EXECUTED).createDerivedPlan(mBuildHelper);
                Map<String, List<IModuleDef>> modules = testRepo.getModulesByName();
                for (String test : plan.getModuleNames()) {
                    if (!modules.containsKey(test)) {
                        CLog.e("Could not find test %s", test);
                    } else {
                        moduleDefs.addAll(modules.get(test));
                    }
                }
            } catch (ConfigurationException e) {
                throw new IllegalStateException(String.format(
                        "Could not load session %s. Use 'list results' to see available sessions.",
                        mRetrySessionId));
            }
        } else {
            throw new IllegalStateException(
                    "Nothing to do. Use 'list plans' to see available plans, 'list modules' to see "
                    + "available modules, and 'list results' to see available sessions to re-run.");
        }
        return moduleDefs;
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
