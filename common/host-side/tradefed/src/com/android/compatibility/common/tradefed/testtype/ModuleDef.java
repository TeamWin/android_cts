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

import com.android.compatibility.common.util.AbiUtils;
import com.android.tradefed.targetprep.ITargetPreparer;
import com.android.tradefed.testtype.IAbi;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.testtype.InstrumentationTest;

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
    public void addFilter(boolean include, String name) {
        for (IRemoteTest test : mTests) {
            if (test instanceof InstrumentationTest) {
                // TODO tell InstrumentationTest to include/exclude the test
            //TODO} else if (test instanceof ITestFilterReciever) {
                // TODO ((ITestFilterReciever) test).addFilter(include, name);
            } else {
                // Skip because it doesn't support this
            }
        }
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
}
