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

import com.android.compatibility.common.tradefed.result.IModuleListener;
import com.android.compatibility.common.tradefed.result.ModuleListener;
import com.android.compatibility.common.util.AbiUtils;
import com.android.tradefed.build.IBuildInfo;
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
import com.android.tradefed.testtype.ITestFilterReceiver;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Container for Compatibility test module info.
 */
public class ModuleDef implements IModuleDef {

    private final String mId;
    private final String mName;
    private final IAbi mAbi;
    private List<IRemoteTest> mTests = null;
    private List<ITargetPreparer> mPreparers = null;
    private IBuildInfo mBuild;
    private ITestDevice mDevice;
    private List<String> mIncludeFilters = new ArrayList<>();
    private List<String> mExcludeFilters = new ArrayList<>();

    public ModuleDef(String name, IAbi abi, List<IRemoteTest> tests,
            List<ITargetPreparer> preparers) {
        mId = AbiUtils.createId(abi.getName(), name);
        mName = name;
        mAbi = abi;
        mTests = tests;
        mPreparers = preparers;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getId() {
        return mId;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getName() {
        return mName;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IAbi getAbi() {
        return mAbi;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<IRemoteTest> getTests() {
        return mTests;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<ITargetPreparer> getPreparers() {
        return mPreparers;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addIncludeFilter(String filter) {
        mIncludeFilters.add(filter);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addExcludeFilter(String filter) {
        mExcludeFilters.add(filter);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int compareTo(IModuleDef moduleDef) {
        return getName().compareTo(moduleDef.getName());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean nameMatches(Pattern pattern) {
        return pattern.matcher(mName).matches();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setBuild(IBuildInfo build) {
        mBuild = build;
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
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        IModuleListener moduleListener = new ModuleListener(this, listener);

        List<ITargetCleaner> cleaners = new ArrayList<>();
        // Setup
        for (ITargetPreparer preparer : mPreparers) {
            CLog.d("Preparer: %s", preparer.getClass().getSimpleName());
            if (preparer instanceof IAbiReceiver) {
                ((IAbiReceiver) preparer).setAbi(mAbi);
            }
            if (preparer instanceof ITargetCleaner) {
                cleaners.add((ITargetCleaner) preparer);
            }
            try {
                preparer.setUp(mDevice, mBuild);
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
        for (IRemoteTest test : mTests) {
            CLog.d("Test: %s", test.getClass().getSimpleName());
            if (test instanceof IAbiReceiver) {
                ((IAbiReceiver) test).setAbi(mAbi);
            }
            if (test instanceof IBuildReceiver) {
                ((IBuildReceiver) test).setBuild(mBuild);
            }
            if (test instanceof IDeviceTest) {
                ((IDeviceTest) test).setDevice(mDevice);
            }
            if (test instanceof ITestFilterReceiver) {
                ((ITestFilterReceiver) test).addAllIncludeFilters(mIncludeFilters);
                ((ITestFilterReceiver) test).addAllExcludeFilters(mExcludeFilters);
            }
            test.run(moduleListener);
        }
        // Tear down - in reverse order
        Collections.reverse(cleaners);
        for (ITargetCleaner cleaner : cleaners) {
            CLog.d("Cleaner: %s", cleaner.getClass().getSimpleName());
            cleaner.tearDown(mDevice, mBuild, null);
        }
    }
}
