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

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.Option;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.testtype.IBuildReceiver;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.testtype.IShardableTest;

import junit.framework.Assert;

import java.util.ArrayList;
import java.util.Collection;

public class ShardableTestStub implements IRemoteTest, IShardableTest, IBuildReceiver {

    @Option(name = "module")
    String mModule;
    @Option(name = "foo")
    String mFoo;
    @Option(name = "blah")
    String mBlah;

    public IBuildInfo mBuildInfo = null;

    Collection<IRemoteTest> mShards;

    /**
     * {@inheritDoc}
     */
    @Override
    public void setBuild(IBuildInfo buildInfo) {
        mBuildInfo = buildInfo;
    }

    /**
     * {@inheritdoc}
     */
    @Override
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        // Do nothing
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Collection<IRemoteTest> split() {
        Assert.assertNotNull(mBuildInfo);

        mShards = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            mShards.add(new ShardableTestStub());
        }
        return mShards;
    }
}
